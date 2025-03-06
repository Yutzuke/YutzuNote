/*
 * Copyright MURENA SAS 2024
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.niedermann.owncloud.notes.shared.account;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.niedermann.owncloud.notes.persistence.ApiProvider;
import it.niedermann.owncloud.notes.persistence.NotesRepository;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.shared.model.Capabilities;
import it.niedermann.owncloud.notes.shared.model.IResponseCallback;
import trikita.log.Log;

public class LocalAccountViewModel extends AndroidViewModel {

    private static final String ACCOUNT_NAME_LOCAL = "Local";
    private static final String TAG = LocalAccountViewModel.class.getSimpleName();
    @NonNull
    private final NotesRepository notesRepository;

    @NonNull
    private final ExecutorService executor;

    public LocalAccountViewModel(@NonNull Application application) {
        super(application);
        notesRepository = NotesRepository.getInstance(application);
        executor = Executors.newSingleThreadExecutor();
    }

    private static boolean isExistingLocalAccount(@NonNull SingleSignOnAccount ssoAccount) {
        return !ssoAccount.name.isEmpty() && !ssoAccount.userId.isEmpty() && !ssoAccount.type.isEmpty();
    }

    public void logInToLocalAccount(Context context, @NonNull IResponseCallback<LocalAccountBundle> callback) {
        executor.submit(() -> {
            try {
                Account account = getExistingAccount();
                if (account != null) {
                    handleExistingAccount(context, account, callback);
                    return;
                }
            } catch (NextcloudFilesAppAccountNotFoundException accountNotFoundException) {
                Log.e(TAG, "Failed to get SingleSignOn account: " + accountNotFoundException);
            }
            addNewAccount(callback);
        });
    }

    private Account getExistingAccount() {
        return notesRepository.getAccountByName(ACCOUNT_NAME_LOCAL);
    }

    private void addNewAccount(@NonNull IResponseCallback<LocalAccountBundle> callback) {
        SingleSignOnAccount singleSignOnAccount = createSingleSignOnAccount();
        try {
            final var capabilities = new Capabilities();
            final String displayName = singleSignOnAccount.name;

            notesRepository.addAccount(singleSignOnAccount.url, singleSignOnAccount.userId,
                    singleSignOnAccount.name, capabilities, displayName, new IResponseCallback<>() {
                        @Override
                        public void onSuccess(Account account) {
                            final var bundle = new LocalAccountBundle(singleSignOnAccount, account, capabilities);
                            Log.d(TAG, "onSuccess: added local account, name: " + singleSignOnAccount.name);
                            callback.onSuccess(bundle);
                        }

                        @Override
                        public void onError(@NonNull Throwable throwable) {
                            Log.e(TAG, "onError: couldn't add local account, error: " + throwable);
                            callback.onError(throwable);
                        }
                    });
        } catch (Throwable throwable) {
            ApiProvider.getInstance().invalidateAPICache(singleSignOnAccount);
            Log.e(TAG, throwable);
            callback.onError(throwable);
        }
    }

    private void handleExistingAccount(Context context, @NonNull Account account, IResponseCallback<LocalAccountBundle> callback)
            throws NextcloudFilesAppAccountNotFoundException {
        final SingleSignOnAccount ssoAccount = AccountImporter.getSingleSignOnAccount(context, ACCOUNT_NAME_LOCAL);
        if (isExistingLocalAccount(ssoAccount)) {
            Log.i(TAG, "Local account already exists, reusing it; account name: " + ssoAccount.name);
            final Capabilities capabilities = new Capabilities();
            final LocalAccountBundle bundle = new LocalAccountBundle(ssoAccount, account, capabilities);
            callback.onSuccess(bundle);
        }
    }

    @NonNull
    private SingleSignOnAccount createSingleSignOnAccount() {
        SingleSignOnAccount singleSignOnAccount = AccountImporter.pickLocalAccount(getApplication());
        Log.i(TAG, "New local account created; account name: " + singleSignOnAccount.name);
        return singleSignOnAccount;
    }
}
