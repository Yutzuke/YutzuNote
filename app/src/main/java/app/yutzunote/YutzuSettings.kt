package app.yutzunote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File

/** Engancha la sección "YutzuNote" de la pantalla de Ajustes. */
class YutzuSettings private constructor(private val fragment: PreferenceFragmentCompat) {

    private val ctx: Context get() = fragment.requireContext()
    private lateinit var pickSyncFolder: ActivityResultLauncher<Uri?>
    private lateinit var pickImportFolder: ActivityResultLauncher<Uri?>
    private lateinit var pickZip: ActivityResultLauncher<Array<String>>

    companion object {
        /** Llamar desde onCreatePreferences(). */
        @JvmStatic
        fun attach(fragment: PreferenceFragmentCompat): YutzuSettings = YutzuSettings(fragment).also { it.setup() }

        /** Convierte el Uri de árbol de SAF en una ruta real (memoria interna o microSD). */
        fun treeUriToPath(uri: Uri): String? {
            val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
            val volume = docId.substringBefore(':')
            val rel = docId.substringAfter(':', "")
            val base = if (volume.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory().path else "/storage/$volume"
            return if (rel.isEmpty()) base else "$base/$rel"
        }
    }

    private fun setup() {
        pickSyncFolder = fragment.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val path = uri?.let(::treeUriToPath) ?: return@registerForActivityResult
            val changed = YutzuPrefs.folder(ctx)?.path != path
            YutzuPrefs.setFolder(ctx, path)
            if (changed) FolderSync.resetMap(ctx)
            refreshSummaries()
            if (!YutzuPrefs.isEnabled(ctx)) {
                YutzuPrefs.sp(ctx).edit().putBoolean(YutzuPrefs.KEY_ENABLED, true).apply()
                fragment.findPreference<androidx.preference.TwoStatePreference>(YutzuPrefs.KEY_ENABLED)?.isChecked = true
            }
            ensurePermission { syncNow() }
        }
        pickImportFolder = fragment.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val path = uri?.let(::treeUriToPath) ?: return@registerForActivityResult
            ensurePermission {
                run("Importando…", { YutzuBackup.importFolder(ctx, File(path)) }) { it.summary() }
            }
        }
        pickZip = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val app = ctx.applicationContext
            run("Restaurando…", {
                app.contentResolver.openInputStream(uri)!!.use { YutzuBackup.restoreZip(app, it) }
            }) { it.summary() }
        }

        pref("yutzu_folder")?.setOnPreferenceClickListener {
            pickSyncFolder.launch(null); true
        }
        fragment.findPreference<Preference>(YutzuPrefs.KEY_ENABLED)?.setOnPreferenceChangeListener { _, value ->
            if (value == true) {
                if (YutzuPrefs.folder(ctx) == null) { pickSyncFolder.launch(null) }
                else ensurePermission { fragment.view?.post { syncNow() } }
            }
            true
        }
        pref("yutzu_sync_now")?.setOnPreferenceClickListener {
            ensurePermission { syncNow() }; true
        }
        pref("yutzu_backup_now")?.setOnPreferenceClickListener {
            ensurePermission {
                run("Creando copia…", { YutzuBackup.createBackup(ctx.applicationContext) }) { "Copia guardada:\n${it.path}" }
            }; true
        }
        pref("yutzu_restore_zip")?.setOnPreferenceClickListener {
            pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")); true
        }
        pref("yutzu_import_folder")?.setOnPreferenceClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Importar notas desde carpeta")
                .setMessage("Elige una carpeta con archivos .md o .txt. Las subcarpetas se convierten en categorías y no se duplican notas idénticas.\n\n" +
                        "Para traer las notas de la app «Notas» de /e/OS: en esa app ve a Ajustes → Exportar notas, y aquí elige la carpeta Notes/Local de la memoria interna.")
                .setPositiveButton("Elegir carpeta") { _, _ -> pickImportFolder.launch(null) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        pref("yutzu_permission")?.setOnPreferenceClickListener { openPermissionSettings(); true }
        refreshSummaries()
    }

    fun refreshSummaries() {
        pref("yutzu_folder")?.summary = YutzuPrefs.folder(ctx)?.path ?: "Toca para elegir (p. ej. una carpeta en la microSD)"
        val last = YutzuPrefs.lastSync(ctx)
        pref("yutzu_sync_now")?.summary = when {
            last == 0L -> "Nunca"
            YutzuPrefs.lastError(ctx) != null -> "⚠ " + YutzuPrefs.lastError(ctx)
            else -> "Última: " + DateUtils.getRelativeTimeSpanString(last)
        }
        val lb = YutzuPrefs.lastBackup(ctx)
        pref("yutzu_backup_now")?.summary = "En " + YutzuBackup.backupDir(ctx).path +
                (if (lb > 0) "\nÚltima: " + DateUtils.getRelativeTimeSpanString(lb) else "")
        pref("yutzu_permission")?.let {
            it.isVisible = !YutzuPrefs.hasFileAccess()
        }
    }

    private fun syncNow() = run("Sincronizando…", { FolderSync.sync(ctx.applicationContext, true) }) { it.summary() }

    private fun pref(key: String): Preference? = fragment.findPreference(key)

    private fun ensurePermission(then: () -> Unit) {
        if (YutzuPrefs.hasFileAccess()) { then(); return }
        AlertDialog.Builder(ctx)
            .setTitle("Permiso necesario")
            .setMessage("YutzuNote necesita «Acceso a todos los archivos» para escribir tus notas en la microSD. Actívalo en la siguiente pantalla y vuelve.")
            .setPositiveButton("Abrir ajustes") { _, _ -> openPermissionSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openPermissionSettings() {
        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
        runCatching { fragment.startActivity(i) }.onFailure {
            fragment.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun <T> run(progress: String, block: () -> T, message: (T) -> String) {
        val app = ctx.applicationContext
        Toast.makeText(app, progress, Toast.LENGTH_SHORT).show()
        YutzuSync.runAsync(app, block) { r ->
            if (fragment.isAdded) refreshSummaries()
            val text = r.fold(message) { "Error: " + (it.message ?: it.javaClass.simpleName) }
            if (fragment.isAdded) AlertDialog.Builder(fragment.requireContext()).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
            else Toast.makeText(app, text, Toast.LENGTH_LONG).show()
        }
    }
}
