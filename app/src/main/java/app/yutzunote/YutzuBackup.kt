package app.yutzunote

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import it.niedermann.owncloud.notes.persistence.NotesRepository
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Copias de seguridad ZIP con fecha, restauración e importación desde carpetas. */
object YutzuBackup {
    private const val MANIFEST = "yutzunote.json"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private data class Item(val title: String, val category: String, val content: String, val favorite: Boolean, val modified: Long)

    data class ImportResult(val created: Int, val skipped: Int) {
        fun summary() = "Importadas $created notas" + if (skipped > 0) " · $skipped ya existían" else ""
    }

    fun backupDir(c: Context) = File(YutzuPrefs.baseDir(c), YutzuPrefs.DIR_BACKUPS)

    /** Crea YutzuNote-AAAAMMDD-HHMMSS.zip con todas las notas (.md por carpetas + yutzunote.json) y poda las antiguas. */
    @Synchronized
    fun createBackup(context: Context): File {
        val ctx = context.applicationContext
        if (!YutzuPrefs.hasFileAccess()) error("Falta el permiso de acceso a todos los archivos")
        val account = NoteFiles.localAccount(ctx) ?: error("No hay cuenta local de notas")
        val notes = NotesRepository.getInstance(ctx).getNotesByAccountId(account.id)
        val dir = backupDir(ctx).apply { mkdirs() }
        val name = "YutzuNote-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".zip"
        val tmp = File(dir, "$name.part")
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            val used = HashSet<String>()
            for (n in notes) {
                val path = NoteFiles.uniquePath(NoteFiles.basePathFor(n), n.id, used)
                zip.putNextEntry(ZipEntry(path).apply { n.modified?.timeInMillis?.let { time = it } })
                zip.write(n.content.toByteArray())
                zip.closeEntry()
            }
            val items = notes.map { Item(it.title, it.category, it.content, it.favorite == true, it.modified?.timeInMillis ?: 0) }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(gson.toJson(items).toByteArray())
            zip.closeEntry()
        }
        val out = File(dir, name)
        tmp.renameTo(out)
        YutzuPrefs.setLastBackup(ctx, System.currentTimeMillis())
        prune(ctx)
        return out
    }

    private fun prune(ctx: Context) {
        val keep = YutzuPrefs.backupKeep(ctx)
        backupDir(ctx).listFiles { f -> f.name.startsWith("YutzuNote-") && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }?.drop(keep)?.forEach { it.delete() }
    }

    /** Copia automática si está activada y la última tiene más de 24 h. */
    fun autoBackupIfDue(ctx: Context): File? {
        if (!YutzuPrefs.isAutoBackup(ctx) || !YutzuPrefs.hasFileAccess()) return null
        if (System.currentTimeMillis() - YutzuPrefs.lastBackup(ctx) < 24L * 3600_000) return null
        return runCatching { createBackup(ctx) }.getOrNull()
    }

    /** Restaura un ZIP (de YutzuNote o cualquier ZIP con .md/.txt). No duplica notas idénticas. */
    fun restoreZip(context: Context, input: InputStream): ImportResult {
        val ctx = context.applicationContext
        val texts = LinkedHashMap<String, Pair<String, Long>>()
        var manifest: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val bytes = zip.readBytes()
                val name = e.name.replace('\\', '/')
                if (name == MANIFEST) manifest = String(bytes)
                else if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) in NoteFiles.NOTE_EXTENSIONS &&
                    name.split('/').none { it.startsWith(".") || it == "__MACOSX" })
                    texts[name] = String(bytes) to (if (e.time > 0) e.time else System.currentTimeMillis())
            }
        }
        val items: List<Item> = manifest?.let {
            runCatching { gson.fromJson<List<Item>>(it, object : TypeToken<List<Item>>() {}.type) }.getOrNull()
        } ?: texts.map { (path, v) ->
            Item("", NoteFiles.categoryFromRelativeDir(path.substringBeforeLast('/', "")), v.first, false, v.second)
        }
        return importItems(ctx, items)
    }

    /**
     * Importa todos los .md/.txt de una carpeta (subcarpetas = categorías).
     * Sirve para traer las notas exportadas desde la app "Notas" de /e/OS (Ajustes → Exportar notas).
     */
    fun importFolder(context: Context, dir: File): ImportResult {
        val ctx = context.applicationContext
        if (!dir.isDirectory) error("No es una carpeta: ${dir.path}")
        val items = dir.walkTopDown().onEnter { !it.name.startsWith(".") || it == dir }
            .filter { NoteFiles.isNoteFile(it) }
            .map { f ->
                val rel = f.relativeTo(dir).parent
                Item("", NoteFiles.categoryFromRelativeDir(rel), f.readText(), false, f.lastModified())
            }.toList()
        return importItems(ctx, items)
    }

    private fun importItems(ctx: Context, items: List<Item>): ImportResult {
        val account = NoteFiles.localAccount(ctx) ?: error("No hay cuenta local de notas")
        val existing: MutableSet<String> = NotesRepository.getInstance(ctx).getNotesByAccountId(account.id)
            .map { key(it.content, it.category) }.toHashSet()
        var created = 0
        var skipped = 0
        for (it in items) {
            if (it.content.isBlank() || !existing.add(key(it.content, it.category))) { skipped++; continue }
            NoteFiles.createNote(ctx, account, it.content, it.category, it.modified.takeIf { m -> m > 0 } ?: System.currentTimeMillis(),
                it.favorite, it.title.takeIf { t -> t.isNotBlank() })
            created++
        }
        if (created > 0) YutzuSync.requestSync(ctx)
        return ImportResult(created, skipped)
    }

    private fun key(content: String, category: String) = NoteFiles.sha1(content.trim()) + "|" + category
}
