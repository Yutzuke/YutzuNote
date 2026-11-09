package app.yutzunote

import android.content.Context
import android.util.Log
import it.niedermann.owncloud.notes.persistence.NotesRepository
import it.niedermann.owncloud.notes.persistence.entity.Note
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sincronización bidireccional entre las notas de la cuenta local y una carpeta (p. ej. en la microSD).
 *
 * - Cada nota es un archivo .md; la categoría es la subcarpeta y el título el nombre del archivo.
 * - Los cambios hechos en la app se escriben al instante en la carpeta.
 * - Los archivos editados o creados fuera de la app (PC, otro editor) se importan.
 * - Si una nota cambió en los dos lados, gana la versión más reciente y la otra se conserva
 *   como nota aparte marcada "(conflicto)". Nunca se pierde texto.
 * - Las notas borradas en la app se mueven a la carpeta .papelera en lugar de borrarse.
 */
object FolderSync {
    private const val TAG = "YutzuFolderSync"
    private const val MAP_PREFS = "yutzunote_map"

    data class Result(val exported: Int = 0, val imported: Int = 0, val updated: Int = 0, val trashed: Int = 0, val error: String? = null) {
        fun summary(): String = error ?: "Escritas $exported · nuevas $imported · actualizadas $updated · a papelera $trashed"
    }

    private data class Entry(var path: String, var hash: String, var mtime: Long)

    @Synchronized
    fun sync(context: Context, importExternal: Boolean = true): Result {
        val ctx = context.applicationContext
        if (!YutzuPrefs.isEnabled(ctx)) return Result(error = "Sincronización con carpeta desactivada")
        val root = YutzuPrefs.folder(ctx) ?: return Result(error = "No has elegido carpeta")
        if (!YutzuPrefs.hasFileAccess()) return fail(ctx, "Falta el permiso de acceso a todos los archivos")
        if (!root.exists() && !root.mkdirs()) return fail(ctx, "No se puede crear ${root.path} (¿microSD retirada?)")
        val account = NoteFiles.localAccount(ctx) ?: return fail(ctx, "No hay cuenta local de notas")

        return try {
            val r = doSync(ctx, root, account.id, importExternal)
            YutzuPrefs.setLastSync(ctx, System.currentTimeMillis(), null)
            Log.i(TAG, r.summary())
            r
        } catch (t: Throwable) {
            Log.e(TAG, "Fallo de sincronización", t)
            fail(ctx, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun fail(ctx: Context, msg: String): Result {
        YutzuPrefs.setLastSync(ctx, System.currentTimeMillis(), msg)
        return Result(error = msg)
    }

    private fun doSync(ctx: Context, root: File, accountId: Long, importExternal: Boolean): Result {
        val repo = NotesRepository.getInstance(ctx)
        val account = repo.getAccountById(accountId)
        val map = loadMap(ctx)
        val byId = LinkedHashMap<Long, Note>()
        repo.getNotesByAccountId(accountId).forEach { byId[it.id] = it }
        var imported = 0
        var updated = 0

        // ---------- 1. Importar cambios externos ----------
        if (importExternal) {
            val pathToId = map.entries.associate { it.value.path.lowercase(Locale.ROOT) to it.key }
            val files = walk(root)
            val seen = files.map { relative(root, it).lowercase(Locale.ROOT) }.toSet()
            // Archivos mapeados que ya no están en su sitio (posible movimiento a otra carpeta)
            val missing = map.filter { (id, e) -> byId.containsKey(id) && e.path.lowercase(Locale.ROOT) !in seen }
                .entries.groupBy({ it.value.hash }, { it.key }).mapValues { it.value.toMutableList() }
            val contentIndex = byId.values.groupBy { NoteFiles.sha1(it.content) + "|" + it.category }

            for (file in files) {
                val rel = relative(root, file)
                val id = pathToId[rel.lowercase(Locale.ROOT)]
                if (id != null) {
                    val entry = map[id] ?: continue
                    val note = byId[id] ?: continue
                    val mtime = file.lastModified()
                    if (mtime == entry.mtime) continue
                    val text = file.readText()
                    val h = NoteFiles.sha1(text)
                    if (h == entry.hash) { entry.mtime = mtime; continue }

                    val changedInApp = NoteFiles.sha1(note.content) != entry.hash
                    val appTime = note.modified?.timeInMillis ?: 0L
                    if (changedInApp && appTime > mtime) {
                        // La app es más reciente: guardamos la versión externa como nota de conflicto.
                        NoteFiles.createNote(ctx, account, text, note.category, mtime,
                            title = note.title + " (conflicto ${stamp(mtime)})")
                        imported++
                    } else {
                        if (changedInApp) {
                            NoteFiles.createNote(ctx, account, note.content, note.category, appTime,
                                title = note.title + " (conflicto ${stamp(appTime)})")
                            imported++
                        }
                        byId[id] = NoteFiles.updateNoteContent(ctx, note, text, mtime)
                        updated++
                    }
                    entry.hash = h
                    entry.mtime = mtime
                } else {
                    val text = file.readText()
                    val h = NoteFiles.sha1(text)
                    val category = NoteFiles.categoryFromRelativeDir(File(rel).parent)
                    val movedId = missing[h]?.removeFirstOrNull()
                    if (movedId != null) {
                        // Mismo contenido que una nota cuyo archivo desapareció: es un movimiento de carpeta.
                        val note = byId.getValue(movedId)
                        if (note.category != category) {
                            NoteFiles.setCategory(ctx, note, category)
                            note.category = category
                            updated++
                        }
                        map[movedId] = Entry(rel, h, file.lastModified())
                        continue
                    }
                    val twin = contentIndex[h + "|" + category]?.firstOrNull { tw -> map[tw.id]?.path?.let { File(root, it).exists() } != true }
                    if (twin != null) {
                        // Ya existe una nota idéntica sin archivo (p. ej. tras reinstalar): solo enlazar.
                        map[twin.id] = Entry(rel, h, file.lastModified())
                        continue
                    }
                    val note = NoteFiles.createNote(ctx, account, text, category, file.lastModified())
                    byId[note.id] = note
                    map[note.id] = Entry(rel, h, file.lastModified())
                    imported++
                }
            }
        }

        // ---------- 2. Exportar notas de la app ----------
        var exported = 0
        val used = HashSet<String>()
        // Primero las que ya tienen archivo, para que conserven su nombre ante colisiones.
        val ordered = byId.values.sortedWith(compareBy({ map[it.id] == null }, { it.id }))
        for (note in ordered) {
            val desired = NoteFiles.uniquePath(NoteFiles.basePathFor(note), note.id, used)
            val target = File(root, desired)
            val entry = map[note.id]
            val h = NoteFiles.sha1(note.content)
            if (entry != null && entry.path != desired) {
                val old = File(root, entry.path)
                if (old.exists()) {
                    target.parentFile?.mkdirs()
                    if (!old.renameTo(target)) { old.copyTo(target, overwrite = true); old.delete() }
                    pruneEmptyDirs(root, old.parentFile)
                }
            }
            if (entry == null || entry.hash != h || !target.exists()) {
                target.parentFile?.mkdirs()
                target.writeText(note.content)
                note.modified?.timeInMillis?.let { target.setLastModified(it) }
                exported++
            }
            map[note.id] = Entry(desired, h, target.lastModified())
        }

        // ---------- 3. Notas borradas → papelera ----------
        var trashed = 0
        val gone = map.keys.filter { it !in byId }
        for (id in gone) {
            val e = map.remove(id) ?: continue
            val f = File(root, e.path)
            if (f.exists()) {
                val dest = File(root, "${YutzuPrefs.DIR_TRASH}/${stamp(System.currentTimeMillis())} ${f.name}")
                dest.parentFile?.mkdirs()
                if (!f.renameTo(dest)) { f.copyTo(dest, overwrite = true); f.delete() }
                pruneEmptyDirs(root, f.parentFile)
                trashed++
            }
        }

        saveMap(ctx, map)
        return Result(exported, imported, updated, trashed)
    }

    // ---------- utilidades ----------

    fun walk(root: File): List<File> = root.walkTopDown()
        .onEnter { dir -> dir == root || !(dir.name.startsWith(".") || (dir.parentFile == root && dir.name in setOf(YutzuPrefs.DIR_BACKUPS, YutzuPrefs.DIR_EXPORTS))) }
        .filter { NoteFiles.isNoteFile(it) && !it.name.startsWith(".") }
        .toList()

    private fun relative(root: File, f: File) = f.relativeTo(root).path.replace(File.separatorChar, '/')

    private fun pruneEmptyDirs(root: File, start: File?) {
        var d = start
        while (d != null && d != root && d.isDirectory && (d.list()?.isEmpty() == true)) {
            d.delete(); d = d.parentFile
        }
    }

    private fun stamp(t: Long) = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(Date(t))

    private fun loadMap(ctx: Context): MutableMap<Long, Entry> {
        val out = HashMap<Long, Entry>()
        ctx.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val parts = (v as? String)?.split('\u0000') ?: return@forEach
            val id = k.toLongOrNull() ?: return@forEach
            if (parts.size == 3) out[id] = Entry(parts[0], parts[1], parts[2].toLongOrNull() ?: 0)
        }
        return out
    }

    private fun saveMap(ctx: Context, map: Map<Long, Entry>) {
        val ed = ctx.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE).edit().clear()
        map.forEach { (id, e) -> ed.putString(id.toString(), "${e.path}\u0000${e.hash}\u0000${e.mtime}") }
        ed.apply()
    }

    /** Olvida qué archivo corresponde a cada nota (al cambiar de carpeta). */
    fun resetMap(ctx: Context) = ctx.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
