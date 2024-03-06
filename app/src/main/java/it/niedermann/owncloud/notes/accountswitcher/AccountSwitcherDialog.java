package it.niedermann.owncloud.notes.accountswitcher;

import static it.niedermann.owncloud.notes.branding.BrandingUtil.applyBrandToLayerDrawable;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.stream.Collectors;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.branding.BrandedDialogFragment;
import it.niedermann.owncloud.notes.databinding.DialogAccountSwitcherBinding;
import it.niedermann.owncloud.notes.manageaccounts.ManageAccountsActivity;
import it.niedermann.owncloud.notes.persistence.NotesRepository;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.shared.util.AccountSyncUtil;
import it.niedermann.owncloud.notes.shared.util.DisplayUtils;

/**
 * Displays all available {@link Account} entries and provides basic operations for them, like adding or switching
 */
public class AccountSwitcherDialog extends BrandedDialogFragment {

    private static final String KEY_CURRENT_ACCOUNT_ID = "current_account_id";

    private NotesRepository repo;
    private DialogAccountSwitcherBinding binding;
    private AccountSwitcherListener accountSwitcherListener;
    private long currentAccountId;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AccountSwitcherListener) {
            this.accountSwitcherListener = (AccountSwitcherListener) context;
        } else {
            throw new ClassCastException("Caller must implement " + AccountSwitcherListener.class.getSimpleName());
        }

        final var args = getArguments();

        if (args == null || !args.containsKey(KEY_CURRENT_ACCOUNT_ID)) {
            throw new IllegalArgumentException("Please provide at least " + KEY_CURRENT_ACCOUNT_ID);
        } else {
            this.currentAccountId = args.getLong(KEY_CURRENT_ACCOUNT_ID);
        }

        repo = NotesRepository.getInstance(requireContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogAccountSwitcherBinding.inflate(requireActivity().getLayoutInflater());

        final var account$ = repo.getAccountById$(currentAccountId);
        account$.observe(requireActivity(), (currentLocalAccount) -> {
            account$.removeObservers(requireActivity());

            binding.accountName.setText(currentLocalAccount.getDisplayName());
            DisplayUtils.setHost(currentLocalAccount, binding.accountHost);
            Glide.with(requireContext())
                    .load(DisplayUtils.getAvatarUrl(currentLocalAccount))
                    .error(R.drawable.ic_account_circle_grey_32dp)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.currentAccountItemAvatar);
            binding.accountLayout.setOnClickListener((v) -> dismiss());

            final var adapter = new AccountSwitcherAdapter((localAccount -> {
                accountSwitcherListener.onAccountChosen(localAccount);
                dismiss();
            }));
            binding.accountsList.setAdapter(adapter);
            final var localAccounts$ = repo.getAccounts$();
            localAccounts$.observe(requireActivity(), (localAccounts) -> {
                localAccounts$.removeObservers(requireActivity());

                setAdapterList(currentLocalAccount, adapter, localAccounts);
                setAddLocalAccountButtonVisibility(localAccounts);
            });
        });

        binding.addAccount.setOnClickListener((v) -> {
            accountSwitcherListener.onAddAccountButtonClick();
            dismiss();
        });

        binding.manageAccounts.setOnClickListener((v) -> {
            requireActivity().startActivity(new Intent(requireContext(), ManageAccountsActivity.class));
            dismiss();
        });

        binding.addLocalAccount.setOnClickListener(view -> {
            accountSwitcherListener.onAddLocalAccountButtonClick();
            dismiss();
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    private void setAddLocalAccountButtonVisibility(List<Account> localAccounts) {
        final boolean isDeviceLocalAccountPresent = localAccounts.stream()
                .anyMatch(account -> AccountSyncUtil.isLocalAccount(requireContext(), account));
        binding.addLocalAccount.setVisibility(isDeviceLocalAccountPresent ? View.GONE : View.VISIBLE);
    }

    private void setAdapterList(Account currentLocalAccount, AccountSwitcherAdapter adapter, List<Account> localAccounts) {
        final List<Account> adapterAccounts = localAccounts.stream()
                .filter(account -> account.getId() != currentLocalAccount.getId())
                .collect(Collectors.toList());
        adapter.setLocalAccounts(adapterAccounts);
    }

    public static DialogFragment newInstance(long currentAccountId) {
        final var dialog = new AccountSwitcherDialog();

        final var args = new Bundle();
        args.putLong(KEY_CURRENT_ACCOUNT_ID, currentAccountId);
        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        applyBrandToLayerDrawable((LayerDrawable) binding.check.getDrawable(), R.id.area, mainColor);
    }
}
