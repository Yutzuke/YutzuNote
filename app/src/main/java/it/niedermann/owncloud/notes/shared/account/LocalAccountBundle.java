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

import com.nextcloud.android.sso.model.SingleSignOnAccount;

import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.shared.model.Capabilities;

public class LocalAccountBundle {
    private final SingleSignOnAccount singleSignOnAccount;
    private final Account account;
    private final Capabilities capabilities;

    public LocalAccountBundle(SingleSignOnAccount singleSignOnAccount, Account account, Capabilities capabilities) {
        this.singleSignOnAccount = singleSignOnAccount;
        this.account = account;
        this.capabilities = capabilities;
    }

    public SingleSignOnAccount getSingleSignOnAccount() {
        return singleSignOnAccount;
    }

    public Account getAccount() {
        return account;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }
}
