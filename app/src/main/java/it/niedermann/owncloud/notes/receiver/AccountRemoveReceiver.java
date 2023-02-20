/*
 * Copyright MURENA SAS 2023
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

package it.niedermann.owncloud.notes.receiver;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import trikita.log.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nextcloud.android.sso.helper.SingleAccountHelper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.niedermann.owncloud.notes.persistence.NotesRepository;
import it.niedermann.owncloud.notes.persistence.entity.Account;

public class AccountRemoveReceiver extends BroadcastReceiver {

    private static final String TAG = AccountRemoveReceiver.class.getSimpleName();
    private static final String[] SUPPORTED_ACCOUNT_TYPES = {"e.foundation.webdav.eelo"};
    private static final String ACTION_ACCOUNT_REMOVE = "android.accounts.action.ACCOUNT_REMOVED";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_ACCOUNT_REMOVE.equals(intent.getAction())) {
            return;
        }

        var accountName = getAccountName(intent);

        if (accountName == null) {
            return;
        }

        var repository = NotesRepository.getInstance(context.getApplicationContext());
        deleteAccount(context, repository, accountName);
    }

    @Nullable
    private String getAccountName(Intent intent) {
        if (!isSupportedAccountType(intent)) {
            return null;
        }

        var accountName = intent.getExtras().getString(AccountManager.KEY_ACCOUNT_NAME);

        if (accountName == null || accountName.trim().isEmpty()) {
            return null;
        }

        return accountName;

    }

    private boolean isSupportedAccountType(Intent intent) {
        var accountTpe = intent.getExtras().getString(AccountManager.KEY_ACCOUNT_TYPE);
        return Arrays.asList(SUPPORTED_ACCOUNT_TYPES)
                .contains(accountTpe);
    }

    private void deleteAccount(@NonNull Context context, @NonNull NotesRepository repository, @NonNull String accountName) {
        executor.submit(() -> {
            final var accounts = repository.getAccounts();

            for (int i = 0; i < accounts.size(); i++) {
                if (accounts.get(i).getAccountName().equals(accountName)) {
                    updateCurrentAccount(context, accounts, accountName, i);
                    repository.deleteAccount(accounts.get(i));
                    break;
                }
            }
        });
    }

    private void updateCurrentAccount(@NonNull Context context, @NonNull List<Account> accounts, @NonNull String toBeDeleteAccountName, int currentIndex) {
        if (!isCurrentAccount(context, toBeDeleteAccountName)) {
            return;
        }

        if (currentIndex > 0) {
            selectAccount(context, accounts.get(currentIndex - 1));
        } else if (accounts.size() > 1) {
            selectAccount(context, accounts.get(currentIndex + 1));
        } else {
            selectAccount(context, null);
        }
    }

    private boolean isCurrentAccount(@NonNull Context context, @NonNull String accountName) {
        try {
            var selectedAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(context);

            if (selectedAccount == null) {
                return false;
            }

            return accountName.equals(selectedAccount.name);
        } catch (Exception e) {
            Log.e(TAG, "Currently selected account not found:", e);
        }

        return false;
    }

    private void selectAccount(@NonNull Context context, @Nullable Account account) {
        SingleAccountHelper.setCurrentAccount(context, (account == null) ? null : account.getAccountName());
    }
}