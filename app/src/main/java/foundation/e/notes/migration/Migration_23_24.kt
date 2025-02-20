/*
 * Copyright e Foundation 2025
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

package foundation.e.notes.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_23_24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `Note`
            SET status = 'LOCAL_EDITED'
            WHERE (status IS NULL OR status = '')
              AND accountId IN (
                  SELECT id FROM `Account`
                  WHERE userName != 'Local'
                    AND url IS NOT NULL
                    AND url != ''
              );
            """.trimIndent()
        )
    }
}
