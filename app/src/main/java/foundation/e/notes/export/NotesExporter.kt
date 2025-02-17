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
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

import it.niedermann.owncloud.notes.R
import it.niedermann.owncloud.notes.persistence.NotesRepository
import it.niedermann.owncloud.notes.persistence.entity.Note

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesExporter {

    private var handler: Handler = Handler(Looper.getMainLooper())

    fun export(context: Context, accountIds: List<Long>) {
        val notesRepo = NotesRepository.getInstance(context)
        val accounts = notesRepo.accounts.filter { it.id in accountIds }

        if (accounts.isEmpty()) {
            handler.post {
                Toast.makeText(
                    context, context.getString(R.string.export_empty_accounts),
                    Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d(TAG, "Exporting notes for ${accounts.size} accounts")

        accounts.forEach { account ->
            val notes = notesRepo.getNotesByAccountId(account.id)
            if (notes.isNotEmpty()) {
                exportNotesForAccount(notes, account.userName)
            }
        }

        handler.post {
            Toast.makeText(
                context, context.getString(R.string.export_finished_message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun exportNotesForAccount(notes: List<Note>, accountUserName: String) {
        Log.d(TAG, "Exporting ${notes.size} notes for account: $accountUserName")

        notes.forEach { note ->
            val notesDir = getNotesDirectory(accountUserName, note.category)
            ensureDirectoryExists(notesDir)
            val fileName = "${note.title}-${note.modified?.time?.let { getCurrentTimestamp(it) }}"
            writeNoteToFile(notesDir, fileName, note.content)
        }
    }

    private fun getNotesDirectory(userName: String, category: String): File {
        val combinedDir = if (category.isNotEmpty()) "/${category}" else ""
        return File(Environment.getExternalStorageDirectory(), "$EXPORT_DIR/$userName$combinedDir")
    }

    private fun ensureDirectoryExists(directory: File) {
        if (!directory.exists() && directory.mkdirs()) {
            Log.d(TAG, "Created directory: ${directory.absolutePath}")
        }
    }

    private fun writeNoteToFile(directory: File, title: String, content: String) {
        var sanitizedTitle = sanitizeFileName(title)
        if (sanitizedTitle.isBlank()) {
            sanitizedTitle = "Untitled-${getCurrentTimestamp(Date())}"
            Log.w(TAG, "Renaming note with a random title")
        }

        val noteFile = File(directory, "$sanitizedTitle.md")
        try {
            noteFile.writeText(content)
            Log.d(TAG, "Written file: ${noteFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write file: ${noteFile.absolutePath}", e)
        }
    }

    private fun getCurrentTimestamp(date: Date): String {
        return SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault()).format(date)
    }

    private fun sanitizeFileName(name: String): String {
        return name.trim().replace(Regex("[/:*?\"<>|]"), "_")
    }

    companion object {
        private const val TAG = "NotesExporter"
        private const val EXPORT_DIR = "Notes"
        private const val DATE_TIME_FORMAT = "yyyyMMdd-HHmmss"
    }
}
