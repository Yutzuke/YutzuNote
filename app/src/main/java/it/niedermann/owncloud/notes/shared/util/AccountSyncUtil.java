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

package it.niedermann.owncloud.notes.shared.util;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import trikita.log.Log;

import it.niedermann.owncloud.notes.persistence.entity.Account;

public class AccountSyncUtil {

    private static final String TAG = AccountSyncUtil.class.getSimpleName();
    private static final String murena_account_type = "e.foundation.webdav.eelo";
    private static final String notes_content_authority = "foundation.e.notes.android.providers.AppContentProvider";

    private AccountSyncUtil() {
        throw new UnsupportedOperationException("Do not instantiate this util class.");
    }

    private static android.accounts.Account[] getMurenaAccountsOnDevice(AccountManager accountManager) {
        return accountManager.getAccountsByType(murena_account_type);
    }

    private static boolean isSyncEnable(android.accounts.Account account) {
        return ContentResolver.getMasterSyncAutomatically() && ContentResolver.getSyncAutomatically(account, notes_content_authority);
    }

    public static boolean isSyncEnable(Context context, Account account) {
        AccountManager accountManager = AccountManager.get(context);

        try {
            android.accounts.Account[] murenaAccounts = getMurenaAccountsOnDevice(accountManager);

            for (android.accounts.Account murenaAccount : murenaAccounts) {
                if (account.getAccountName().equals(murenaAccount.name)) {
                    return isSyncEnable(murenaAccount);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to load murena accounts", e);
        }

        return true;
    }

}
