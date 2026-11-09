package app.yutzunote

import android.content.Context
import it.niedermann.owncloud.notes.persistence.NotesDatabase
import it.niedermann.owncloud.notes.persistence.NotesRepository
import it.niedermann.owncloud.notes.persistence.entity.Account
import it.niedermann.owncloud.notes.persistence.entity.Note
import it.niedermann.owncloud.notes.shared.model.DBStatus
import it.niedermann.owncloud.notes.shared.util.AccountSyncUtil
import it.niedermann.owncloud.notes.shared.util.NoteUtil
import java.io.File
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale

/** Utilidades comunes: nombres de archivo, hashes y alta/edición de notas en la cuenta local. */
object NoteFiles {
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    val NOTE_EXTENSIONS = setOf("md", "txt", "markdown")
    private const val MAX_NAME = 80

    fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    fun sanitize(name: String): String {
        var s = name.replace(ILLEGAL, "_").trim().trim('.').trim()
        if (s.length > MAX_NAME) s = s.substring(0, MAX_NAME).trim()
        return s.ifEmpty { "Sin título" }
    }

    /** Ruta relativa deseada (categoría → subcarpetas, título → nombre). Sin extensión. */
    fun basePathFor(note: Note): String {
        val dirs = note.category.split('/').map { it.trim() }.filter { it.isNotEmpty() }.map(::sanitize)
        val name = sanitize(note.title.ifBlank { NoteUtil.generateNoteTitle(note.content) })
        return (dirs + name).joinToString("/")
    }

    /** Devuelve una ruta "base.md" única (sin distinguir mayúsculas: la microSD es FAT/exFAT). */
    fun uniquePath(base: String, id: Long, used: MutableSet<String>): String {
        var candidate = "$base.md"
        if (!used.add(candidate.lowercase(Locale.ROOT))) {
            candidate = "$base ~$id.md"
            used.add(candidate.lowercase(Locale.ROOT))
        }
        return candidate
    }

    fun categoryFromRelativeDir(relDir: String?): String =
        relDir.orEmpty().split('/', File.separatorChar).filter { it.isNotBlank() }.joinToString("/")

    fun isNoteFile(f: File) = f.isFile && f.extension.lowercase(Locale.ROOT) in NOTE_EXTENSIONS

    /** Cuenta local ("Local") donde viven las notas sin nube. Si no hay, la primera cuenta. */
    fun localAccount(context: Context): Account? {
        val accounts = NotesRepository.getInstance(context).accounts ?: return null
        return accounts.firstOrNull { runCatching { AccountSyncUtil.isLocalAccount(context, it) }.getOrDefault(false) }
            ?: accounts.firstOrNull { it.accountName.equals("Local", ignoreCase = true) }
    }

    fun createNote(context: Context, account: Account, content: String, category: String, modified: Long, favorite: Boolean = false, title: String? = null): Note {
        val cal = Calendar.getInstance().apply { timeInMillis = modified }
        val t = title?.takeIf { it.isNotBlank() } ?: NoteUtil.generateNonEmptyNoteTitle(content, context)
        val note = Note(null, cal, t, content, category, favorite, null)
        return NotesRepository.getInstance(context).addNote(account.id, note)
    }

    fun updateNoteContent(context: Context, old: Note, content: String, modified: Long, category: String = old.category): Note {
        val title = NoteUtil.generateNonEmptyNoteTitle(content, context)
        val cal = Calendar.getInstance().apply { timeInMillis = modified }
        val n = Note(old.id, old.remoteId, cal, title, content, category, old.favorite, old.eTag,
            DBStatus.VOID, old.accountId, NoteUtil.generateNoteExcerpt(content, title), old.scrollY)
        NotesDatabase.getInstance(context).noteDao.updateNote(n)
        return n
    }

    fun setCategory(context: Context, note: Note, category: String) {
        NotesDatabase.getInstance(context).noteDao.updateCategory(note.id, category)
    }
}
