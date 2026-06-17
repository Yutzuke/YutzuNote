package it.niedermann.owncloud.notes.importaccount;

import android.accounts.NetworkErrorException;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountPermissionNotGrantedException;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotSupportedException;
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException;
import com.nextcloud.android.sso.exceptions.UnknownErrorException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import com.nextcloud.android.sso.ui.UiExceptionManager;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.branding.BrandingUtil;
import it.niedermann.owncloud.notes.databinding.ActivityImportAccountBinding;
import it.niedermann.owncloud.notes.exception.ExceptionDialogFragment;
import it.niedermann.owncloud.notes.exception.ExceptionHandler;
import it.niedermann.owncloud.notes.main.MainActivity;
import it.niedermann.owncloud.notes.persistence.ApiProvider;
import it.niedermann.owncloud.notes.persistence.CapabilitiesClient;
import it.niedermann.owncloud.notes.persistence.SyncWorker;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.shared.account.ImportMurenaAccountViewModel;
import it.niedermann.owncloud.notes.shared.account.LocalAccountBundle;
import it.niedermann.owncloud.notes.shared.account.LocalAccountViewModel;
import it.niedermann.owncloud.notes.shared.constant.MurenaAccountConstants;
import it.niedermann.owncloud.notes.shared.model.IResponseCallback;
import it.niedermann.owncloud.notes.shared.util.AccountSyncUtil;
import trikita.log.Log;

public class ImportAccountActivity extends AppCompatActivity {

    private static final String TAG = ImportAccountActivity.class.getSimpleName();
    public static final int REQUEST_CODE_IMPORT_ACCOUNT = 1;

    private static final String ACCOUNT_MANAGER_APP_PASSWORD_ACTION =
            "foundation.e.accountmanager.ui.setup.AppPasswordActivity";
    private static final String EXTRA_ACCOUNT_NAME = "accountName";
    private static final String EXTRA_ACCOUNT_TYPE = "accountType";

    private static final String STATE_PENDING_APP_PASSWORD_ACCOUNT = "pendingAppPasswordAccount";
    private static final String STATE_APP_PASSWORD_FLOW_LAUNCHED = "appPasswordFlowLaunched";
    private static final String STATE_USER_INITIATED_IMPORT = "userInitiatedImport";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ImportAccountViewModel importAccountViewModel;
    private ImportMurenaAccountViewModel importMurenaAccountViewModel;

    private LocalAccountViewModel localAccountViewModel;

    private ActivityImportAccountBinding binding;

    private SingleSignOnAccount pendingAppPasswordAccount;
    // One 2FA launch per import — prevents a relaunch loop when the user cancels.
    private boolean appPasswordFlowLaunched;
    // Auto-pick on open must not silently launch the 2FA browser — only "Add" taps do.
    private boolean userInitiatedImport;
    private ActivityResultLauncher<Intent> appPasswordLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler(this));

        if (savedInstanceState != null) {
            pendingAppPasswordAccount = (SingleSignOnAccount)
                    savedInstanceState.getSerializable(STATE_PENDING_APP_PASSWORD_ACCOUNT);
            appPasswordFlowLaunched = savedInstanceState.getBoolean(STATE_APP_PASSWORD_FLOW_LAUNCHED);
            userInitiatedImport = savedInstanceState.getBoolean(STATE_USER_INITIATED_IMPORT);
        }

        binding = ActivityImportAccountBinding.inflate(getLayoutInflater());
        importAccountViewModel = new ViewModelProvider(this).get(ImportAccountViewModel.class);
        importMurenaAccountViewModel = new ViewModelProvider(this).get(ImportMurenaAccountViewModel.class);
        localAccountViewModel = new ViewModelProvider(this).get(LocalAccountViewModel.class);

        setContentView(binding.getRoot());

        appPasswordLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    final var account = pendingAppPasswordAccount;
                    pendingAppPasswordAccount = null;
                    if (account != null) {
                        ApiProvider.getInstance().invalidateAPICache(account);
                        importAccount(account);
                    }
                });

        binding.welcomeText.setText(getString(R.string.welcome_text, getString(R.string.app_name)));
        handleAddButtonClickListener();
        handleLocalAccount();

        handleAutoMurenaAccountSetup();
    }

    private void handleAddButtonClickListener() {
        binding.addButton.setOnClickListener((v) -> {
            userInitiatedImport = true;
            setAddButtonVisibility(false);
            binding.status.setVisibility(View.GONE);
            if (importMurenaAccountViewModel.isMurenaAccountPresent()) {
                pickMurenaAccount();
                return;
            }
            try {
                AccountImporter.pickNewAccount(this);
            } catch (NextcloudFilesAppNotInstalledException e) {
                UiExceptionManager.showDialogForException(this, e);
                Log.w(TAG, "=============================================================");
                Log.w(TAG, "Nextcloud app is not installed. Cannot choose account");
                Log.e(TAG, e.getMessage());
            } catch (AndroidGetAccountsPermissionNotGranted e) {
                setAddButtonVisibility(true);
                AccountImporter.requestAndroidAccountPermissionsAndPickAccount(this);
            }
        });
    }

    private void setAddButtonVisibility(boolean enabled) {
        int visibility = enabled ? View.VISIBLE : View.INVISIBLE;

        binding.addButton.setEnabled(enabled);
        binding.addButton.setVisibility(visibility);

        binding.addLocalButton.setEnabled(enabled);
        binding.addLocalButton.setVisibility(visibility);

        binding.textOr.setVisibility(visibility);
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onSupportNavigateUp();
        setResult(RESULT_CANCELED);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, ImportAccountActivity.this, ssoAccount -> {
                appPasswordFlowLaunched = false;
                importAccount(ssoAccount);
            });
        } catch (AccountImportCancelledException e) {
            restoreCleanState();
            Log.i(TAG, "Account import has been canceled.");
        }
    }

    private void importAccount(@NonNull SingleSignOnAccount ssoAccount) {
        runOnUiThread(() -> binding.progressCircular.setVisibility(View.VISIBLE));

        executor.submit(() -> {
            if (isExistingAccount(ssoAccount)) {
                finishActivity(false);
                return;
            }

            SingleAccountHelper.setCurrentAccount(getApplicationContext(), ssoAccount.name);
            Log.i(TAG, "Added account: " + "name:" + ssoAccount.name + ", " + ssoAccount.url + ", userId" + ssoAccount.userId);
            try {
                Log.i(TAG, "Loading capabilities for " + ssoAccount.name);
                final var capabilities = CapabilitiesClient.getCapabilities(getApplicationContext(), ssoAccount, null, ApiProvider.getInstance());
                final String displayName = CapabilitiesClient.getDisplayName(getApplicationContext(), ssoAccount, ApiProvider.getInstance());
                final var status$ = importAccountViewModel.addAccount(ssoAccount.url, ssoAccount.userId, ssoAccount.name, capabilities, displayName, new IResponseCallback<>() {

                    /**
                     * Update syncing when adding account
                     * https://github.com/stefan-niedermann/nextcloud-deck/issues/531
                     * @param account the account to add
                     */
                    @Override
                    public void onSuccess(Account account) {
                        runOnUiThread(() -> {
                            Log.i(TAG, capabilities.toString());
                            BrandingUtil.saveBrandColors(ImportAccountActivity.this, capabilities.getColor(), capabilities.getTextColor());
                            finishActivity(false);
                        });
                        SyncWorker.update(ImportAccountActivity.this, PreferenceManager.getDefaultSharedPreferences(ImportAccountActivity.this)
                                .getBoolean(getString(R.string.pref_key_background_sync), true));
                    }

                    @Override
                    public void onError(@NonNull Throwable t) {
                        runOnUiThread(() -> {
                            restoreCleanState();
                            ExceptionDialogFragment.newInstance(t).show(getSupportFragmentManager(), ExceptionDialogFragment.class.getSimpleName());
                        });
                    }
                });
                runOnUiThread(() -> status$.observe(ImportAccountActivity.this, (status) -> {
                    binding.progressText.setVisibility(View.VISIBLE);
                    Log.v(TAG, "Status: " + status.count + " of " + status.total);
                    if (status.count > 0) {
                        binding.progressCircular.setIndeterminate(false);
                    }
                    binding.progressText.setText(getString(R.string.progress_import, status.count + 1, status.total));
                    binding.progressCircular.setProgress(status.count);
                    binding.progressCircular.setMax(status.total);
                }));
            } catch (Throwable t) {
                t.printStackTrace();
                ApiProvider.getInstance().invalidateAPICache(ssoAccount);

                if (t instanceof NextcloudHttpRequestFailedException
                        && ((NextcloudHttpRequestFailedException) t).getStatusCode() == HttpURLConnection.HTTP_SEE_OTHER
                        && userInitiatedImport
                        && !appPasswordFlowLaunched) {
                    appPasswordFlowLaunched = true;
                    pendingAppPasswordAccount = ssoAccount;
                    SingleAccountHelper.setCurrentAccount(this, null);
                    runOnUiThread(() -> {
                        restoreCleanState();
                        launchAppPasswordFlow(ssoAccount);
                    });
                    return;
                }

                SingleAccountHelper.setCurrentAccount(this, null);
                runOnUiThread(() -> {
                    restoreCleanState();
                    if (t instanceof NextcloudHttpRequestFailedException && ((NextcloudHttpRequestFailedException) t).getStatusCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                        binding.status.setText(R.string.error_maintenance_mode);
                        binding.status.setVisibility(View.VISIBLE);
                    } else if (t instanceof NetworkErrorException) {
                        binding.status.setText(getString(R.string.error_sync, getString(R.string.error_no_network)));
                        binding.status.setVisibility(View.VISIBLE);
                    } else if (t instanceof UnknownErrorException && t.getMessage() != null && t.getMessage().contains("No address associated with hostname")) {
                        // https://github.com/stefan-niedermann/nextcloud-notes/issues/1014
                        binding.status.setText(R.string.you_have_to_be_connected_to_the_internet_in_order_to_add_an_account);
                        binding.status.setVisibility(View.VISIBLE);
                    } else if (t instanceof NextcloudHttpRequestFailedException && ((NextcloudHttpRequestFailedException) t).getStatusCode() == HttpURLConnection.HTTP_SEE_OTHER) {
                        // 2FA flow already attempted (e.g. cancelled) — show guidance instead of relaunching.
                        binding.status.setText(R.string.error_two_factor_app_password);
                        binding.status.setVisibility(View.VISIBLE);
                    } else {
                        ExceptionDialogFragment.newInstance(t).show(getSupportFragmentManager(), ExceptionDialogFragment.class.getSimpleName());
                    }
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_PENDING_APP_PASSWORD_ACCOUNT, pendingAppPasswordAccount);
        outState.putBoolean(STATE_APP_PASSWORD_FLOW_LAUNCHED, appPasswordFlowLaunched);
        outState.putBoolean(STATE_USER_INITIATED_IMPORT, userInitiatedImport);
    }

    private void launchAppPasswordFlow(@NonNull SingleSignOnAccount ssoAccount) {
        if (!hasValidAccountManagerSignature()) {
            Log.e(TAG, "AccountManager signature mismatch; refusing app-password launch");
            binding.status.setText(R.string.error_two_factor_app_password);
            binding.status.setVisibility(View.VISIBLE);
            return;
        }
        final var intent = new Intent(ACCOUNT_MANAGER_APP_PASSWORD_ACTION)
                .setPackage(MurenaAccountConstants.ACCOUNT_MANAGER_PACKAGE)
                .putExtra(EXTRA_ACCOUNT_NAME, ssoAccount.name)
                .putExtra(EXTRA_ACCOUNT_TYPE, MurenaAccountConstants.MURENA_ACCOUNT_TYPE);
        try {
            appPasswordLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "AccountManager 2FA app-password activity not available: " + e.getMessage());
            binding.status.setText(R.string.error_two_factor_app_password);
            binding.status.setVisibility(View.VISIBLE);
        }
    }

    private boolean hasValidAccountManagerSignature() {
        return getPackageManager().checkSignatures(
                MurenaAccountConstants.ACCOUNT_MANAGER_PACKAGE,
                getPackageName()
        ) == PackageManager.SIGNATURE_MATCH;
    }

    private boolean isExistingAccount(SingleSignOnAccount ssoAccount) {
        return importAccountViewModel.isExistingAccount(ssoAccount);
    }

    private void finishActivity(boolean isLocal) {
        setResult(RESULT_OK);
        Intent intent = new Intent(ImportAccountActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(AccountSyncUtil.LOCAL, isLocal);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private void restoreCleanState() {
        runOnUiThread(() -> {
            setAddButtonVisibility(true);
            binding.progressCircular.setVisibility(View.GONE);
            binding.progressText.setVisibility(View.GONE);
        });
    }

    private void handleAutoMurenaAccountSetup() {
        if (importMurenaAccountViewModel.isMurenaAccountPresent()) {
            setAddButtonVisibility(false);
            binding.status.setVisibility(View.GONE);

            pickMurenaAccount();
        }
    }

    private void pickMurenaAccount() {
        try {
            AccountImporter.pickAccount(this, importMurenaAccountViewModel.getMurenaAccount());
        } catch (NextcloudFilesAppNotInstalledException |
                 NextcloudFilesAppNotSupportedException e) {
            UiExceptionManager.showDialogForException(this, e);
            Log.w(TAG, "=============================================================");
            Log.w(TAG, "Nextcloud app is not installed. Cannot choose account");
            e.printStackTrace();
        } catch (AndroidGetAccountsPermissionNotGranted |
                 NextcloudFilesAppAccountPermissionNotGrantedException e) {
            setAddButtonVisibility(true);
            AccountImporter.requestAndroidAccountPermissionsAndPickAccount(this);
        }
    }


    private void handleLocalAccount() {
        binding.addLocalButton.setOnClickListener(view -> {
            localAccountViewModel.logInToLocalAccount(getApplicationContext(), getAddLocalAccountCallback());
        });
    }

    @NonNull
    private IResponseCallback<LocalAccountBundle> getAddLocalAccountCallback() {
        return new IResponseCallback<>() {
            @Override
            public void onSuccess(@NonNull LocalAccountBundle result) {
                onSuccessfullyAddedLocalAccount(result);
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                errorOnAddingNewAccount(throwable);
            }
        };
    }

    private void errorOnAddingNewAccount(@NonNull Throwable throwable) {
        runOnUiThread(() -> {
            restoreCleanState();
            ExceptionDialogFragment.newInstance(throwable).show(getSupportFragmentManager(), ExceptionDialogFragment.class.getSimpleName());
        });
    }

    private void onSuccessfullyAddedLocalAccount(@NonNull LocalAccountBundle result) {
        runOnUiThread(() -> {
            SingleAccountHelper.setCurrentAccount(getApplicationContext(), result.getSingleSignOnAccount().name);

            BrandingUtil.saveBrandColors(ImportAccountActivity.this, result.getCapabilities().getColor(), result.getCapabilities().getTextColor());
            finishActivity(true);
        });
    }
}
