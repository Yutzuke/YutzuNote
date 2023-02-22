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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import it.niedermann.owncloud.notes.shared.constant.MurenaAccountConstants;
import trikita.log.Log;

import it.niedermann.owncloud.notes.persistence.entity.Account;

public final class AccountSyncUtil {

    private static final String TAG = AccountSyncUtil.class.getSimpleName();

    private AccountSyncUtil() {
        throw new UnsupportedOperationException("Do not instantiate this util class.");
    }

    public static boolean isSyncEnable(android.accounts.Account account) {
        return ContentResolver.getMasterSyncAutomatically() && ContentResolver.getSyncAutomatically(account, MurenaAccountConstants.NOTES_CONTENT_AUTHORITY);
    }

    public static boolean isSyncEnable(Context context, Account account) {
        try {
            List<android.accounts.Account> murenaAccounts = getMurenaAccounts(context);

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

    public static List<android.accounts.Account> getMurenaAccounts(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        return Arrays.stream(accountManager.getAccountsByType(MurenaAccountConstants.MURENA_ACCOUNT_TYPE))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
