/*
 * Copyright (C) 2026 e Foundation
 *
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
package foundation.e.notes.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import it.niedermann.owncloud.notes.persistence.NotesDatabase
import it.niedermann.owncloud.notes.shared.util.AccountSyncUtil

class NotesBackupAgent : BackupAgent() {

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        // Full Auto Backup handles the file payload.
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        // Full Auto Backup handles the file payload.
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()

        runCatching {
            val accountDao = NotesDatabase
                .getInstance(applicationContext)
                .getAccountDao()

            // Non-local account such as murena account will be auto logged-in
            val removedAccounts = accountDao.getAccounts()
                .filterNot { AccountSyncUtil.isLocalAccount(applicationContext, it) }
                .onEach(accountDao::deleteAccount)

            Log.i(TAG, "Restore cleanup finished. Removed non-local accounts: ${removedAccounts.size}")
        }.onFailure {
            Log.e(TAG, "Failed to clean up restored accounts", it)
        }
    }

    companion object {
        private const val TAG = "NotesBackupAgent"
    }
}
