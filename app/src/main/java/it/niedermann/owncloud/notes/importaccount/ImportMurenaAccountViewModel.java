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

package it.niedermann.owncloud.notes.importaccount;

import android.accounts.Account;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import java.util.List;
import java.util.Objects;

import it.niedermann.owncloud.notes.shared.util.AccountSyncUtil;

public class ImportMurenaAccountViewModel extends AndroidViewModel {

    private Account account;

    public ImportMurenaAccountViewModel(@NonNull Application application) {
        super(application);
        retrieveAccount();
    }

    private void retrieveAccount() {
        List<Account> accounts = AccountSyncUtil.getMurenaAccounts(getApplication());

        if (accounts.isEmpty()) {
            return;
        }

        Account murenaAccount = accounts.get(0); // retrieve the first account, as we have policy that one murena account per device

        if (AccountSyncUtil.isSyncEnable(murenaAccount)) {
            account = murenaAccount;
        }
    }

    public boolean isMurenaAccountPresent() {
        return account != null;
    }

    public Account getMurenaAccount() {
        return account;
    }

    public boolean shouldLoadMurenaAccount(List<it.niedermann.owncloud.notes.persistence.entity.Account> noteAccounts) {
        if (!isMurenaAccountPresent()) {
            return false;
        }

        return noteAccounts.stream()
                .filter(Objects::nonNull)
                .noneMatch(noteAccount -> noteAccount.getAccountName().equals(account.name));
    }
}
