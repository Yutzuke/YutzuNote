package app.yutzunote

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.preference.PreferenceManager
import java.io.File

/** Ajustes propios de YutzuNote. Los interruptores viven en las preferencias por defecto (pantalla de Ajustes). */
object YutzuPrefs {
    const val KEY_ENABLED = "yutzu_enabled"
    const val KEY_FOLDER = "yutzu_folder"
    const val KEY_AUTO_BACKUP = "yutzu_auto_backup"
    const val KEY_BACKUP_KEEP = "yutzu_backup_keep"
    private const val KEY_LAST_BACKUP = "yutzu_last_backup"
    private const val KEY_LAST_SYNC = "yutzu_last_sync"
    private const val KEY_LAST_ERROR = "yutzu_last_error"

    const val DIR_BACKUPS = "Backups"
    const val DIR_EXPORTS = "Exportados"
    const val DIR_TRASH = ".papelera"

    fun sp(c: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(c)

    fun isEnabled(c: Context) = sp(c).getBoolean(KEY_ENABLED, false)
    fun folder(c: Context): File? = sp(c).getString(KEY_FOLDER, null)?.let { File(it) }
    fun setFolder(c: Context, path: String) = sp(c).edit().putString(KEY_FOLDER, path).apply()

    fun isAutoBackup(c: Context) = sp(c).getBoolean(KEY_AUTO_BACKUP, true)
    fun backupKeep(c: Context) = sp(c).getString(KEY_BACKUP_KEEP, "10")?.toIntOrNull()?.coerceIn(1, 100) ?: 10

    fun lastBackup(c: Context) = sp(c).getLong(KEY_LAST_BACKUP, 0)
    fun setLastBackup(c: Context, v: Long) = sp(c).edit().putLong(KEY_LAST_BACKUP, v).apply()

    fun setLastSync(c: Context, time: Long, error: String?) =
        sp(c).edit().putLong(KEY_LAST_SYNC, time).putString(KEY_LAST_ERROR, error).apply()

    fun lastSync(c: Context) = sp(c).getLong(KEY_LAST_SYNC, 0)
    fun lastError(c: Context): String? = sp(c).getString(KEY_LAST_ERROR, null)

    /** Carpeta base para copias y exportaciones: la carpeta elegida o Documents/YutzuNote como respaldo. */
    fun baseDir(c: Context): File =
        folder(c) ?: File(Environment.getExternalStorageDirectory(), "Documents/YutzuNote")

    fun hasFileAccess(): Boolean = Environment.isExternalStorageManager()
}
