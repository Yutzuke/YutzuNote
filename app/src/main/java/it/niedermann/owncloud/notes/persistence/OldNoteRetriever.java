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

package it.niedermann.owncloud.notes.persistence;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import it.niedermann.owncloud.notes.shared.model.DBStatus;

/**
 * This is a helper class which loads all notes from old table.
 * Because of massive version jump, not-sync notes are not migrated to latest `Note` table from old `NOTES` table.
 * This helper class load the notes from the `NOTES` table for migration.
 *
 * <a href="https://gitlab.e.foundation/e/os/backlog/-/issues/1147">Check for more details</a>
 */
public final class OldNoteRetriever {

    private static final String OLD_NOTES_TABLE_NAME = "NOTES";

    private OldNoteRetriever() {
    }

    @NonNull
    public static List<OldNote> getNotesFromOldTable(@NonNull RoomDatabase database) {
       final SupportSQLiteDatabase supportSQLiteDatabase = database.getOpenHelper().getWritableDatabase();

        if (!isOldNoteTableExists(supportSQLiteDatabase)) { // old notes table can not be existed for fresh install
            return Collections.emptyList();
        }

        return retrieveOldNotes(supportSQLiteDatabase);
    }

    @NonNull
    private static List<OldNote> retrieveOldNotes(@NonNull SupportSQLiteDatabase supportSQLiteDatabase) {
       final Cursor oldNotesCursor = supportSQLiteDatabase.query("SELECT * FROM " + OLD_NOTES_TABLE_NAME);
        List<OldNote> notes = new ArrayList<>();

        while (oldNotesCursor.moveToNext()) {
            notes.add(getOldNoteFromCursor(oldNotesCursor));
        }

        oldNotesCursor.close();
        return notes;
    }

    private static boolean isOldNoteTableExists(@NonNull SupportSQLiteDatabase supportSQLiteDatabase) {
       final Cursor tableExistCursor = supportSQLiteDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='" + OLD_NOTES_TABLE_NAME + "'");
       final boolean tableExists = tableExistCursor.moveToNext();
        tableExistCursor.close();
        return tableExists;
    }

    @NonNull
    private static OldNote getOldNoteFromCursor(@NonNull Cursor cursor) {
        final Calendar modified = Calendar.getInstance();
        modified.setTimeInMillis(cursor.getLong(4) * 1000);
        return new OldNote(cursor.getLong(0), cursor.getLong(1), modified, cursor.getString(3), cursor.getString(5), cursor.getInt(6) > 0, cursor.getString(7), cursor.getString(8), DBStatus.parse(cursor.getString(2)));
    }
}
