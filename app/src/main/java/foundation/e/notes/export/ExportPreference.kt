/*
 * Copyright (C) 2025 e Foundation
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

package foundation.e.notes.export

import android.content.Context
import android.util.AttributeSet

import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference

import it.niedermann.owncloud.notes.R
import it.niedermann.owncloud.notes.persistence.NotesRepository
import it.niedermann.owncloud.notes.persistence.entity.Account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {
    private val selectedValues = mutableSetOf<String>()
    private var accounts: List<Account> = emptyList()
    private var alertDialog: AlertDialog? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val notesRepo = NotesRepository.getInstance(context)
            val fetchedAccounts = notesRepo.accounts ?: emptyList()

            withContext(Dispatchers.Main) {
                accounts = fetchedAccounts
            }
        }
        dialogLayoutResource = R.layout.dialog_export_notes
    }

    override fun onClick() {
        loadSavedAccounts()
        showDialog()
    }

    /**
     * Loads previously saved selected accounts.
     * If nothing was selected before, selects all by default.
     */
    private fun loadSavedAccounts() {
        val savedValues = getPersistedStringSet(emptySet())

        if (savedValues.isEmpty()) {
            selectedValues.addAll(accounts.map { it.id.toString() })
        } else {
            selectedValues.addAll(savedValues)
        }
    }

    private fun showDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.settings_export_notes_title))

        val accountNames = accounts.map { it.accountName }.toTypedArray()
        val checkedItems = BooleanArray(accounts.size) {
            selectedValues.contains(accounts[it].id.toString())
        }

        builder.setMultiChoiceItems(accountNames, checkedItems) { _, index, isChecked ->
            val id = accounts[index].id.toString()
            if (isChecked) {
                selectedValues.add(id)
            } else {
                selectedValues.remove(id)
            }
        }

        builder.setPositiveButton(R.string.settings_export) { _, _ -> saveSelection() }
        builder.setNegativeButton(android.R.string.cancel, null)

        alertDialog = builder.create().apply {
            setOnDismissListener { alertDialog = null }
            show()
        }
    }

    /**
     * Saves the selected accounts and triggers the export.
     */
    private fun saveSelection() {
        persistStringSet(selectedValues)

        CoroutineScope(Dispatchers.IO).launch {
            val accountIds = selectedValues.map { it.toLong() }
            NotesExporter().export(context, accountIds)
        }
    }

    override fun onDetached() {
        super.onDetached()
        alertDialog?.takeIf { it.isShowing }?.dismiss()
        alertDialog = null
    }
}
