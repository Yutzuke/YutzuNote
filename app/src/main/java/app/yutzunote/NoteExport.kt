package app.yutzunote

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import it.niedermann.owncloud.notes.persistence.entity.Note
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Exporta una nota suelta a TXT o PDF dentro de <carpeta>/Exportados y permite compartirla. */
object NoteExport {
    enum class Format(val ext: String, val mime: String) { TXT("txt", "text/plain"), PDF("pdf", "application/pdf") }

    fun exportDir(c: Context) = File(YutzuPrefs.baseDir(c), YutzuPrefs.DIR_EXPORTS)

    fun export(context: Context, note: Note, format: Format): File {
        if (!YutzuPrefs.hasFileAccess()) error("Falta el permiso de acceso a todos los archivos")
        val dir = exportDir(context).apply { mkdirs() }
        val out = File(dir, NoteFiles.sanitize(note.title) + "." + format.ext)
        when (format) {
            Format.TXT -> out.writeText(note.content)
            Format.PDF -> writePdf(note, out)
        }
        return out
    }

    /** Exporta en segundo plano, avisa de dónde quedó el archivo y abre el menú de compartir. */
    @JvmStatic
    fun exportAndShare(context: Context, note: Note, format: Format) {
        val app = context.applicationContext
        YutzuSync.runAsync(app, { export(app, note, format) }) { r ->
            r.onSuccess { file ->
                android.widget.Toast.makeText(app, "Guardado en ${file.path}", android.widget.Toast.LENGTH_LONG).show()
                runCatching { share(app, file, format) }
            }.onFailure {
                android.widget.Toast.makeText(app, "Error al exportar: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun share(context: Context, file: File, format: Format) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".yutzu.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(format.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // A4 en puntos PDF (1/72")
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 50f

    private fun writePdf(note: Note, out: File) {
        val width = (PAGE_W - 2 * MARGIN).toInt()
        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val metaPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF777777.toInt() }
        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f }

        val titleLayout = layout(note.title, titlePaint, width)
        val meta = listOfNotNull(
            note.category.takeIf { it.isNotBlank() },
            note.modified?.time?.let { DateFormat.getDateTimeInstance().format(it) } ?: DateFormat.getDateTimeInstance().format(Date())
        ).joinToString("  ·  ")
        val metaLayout = layout(meta, metaPaint, width)
        val body = layout(stripTitleLine(note), bodyPaint, width)

        val doc = PdfDocument()
        try {
            var line = 0
            var pageNo = 1
            while (true) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                val c = page.canvas
                var y = MARGIN
                if (pageNo == 1) {
                    c.save(); c.translate(MARGIN, y); titleLayout.draw(c); c.restore(); y += titleLayout.height + 4
                    c.save(); c.translate(MARGIN, y); metaLayout.draw(c); c.restore(); y += metaLayout.height + 14
                }
                val available = PAGE_H - MARGIN - y
                val startTop = if (line < body.lineCount) body.getLineTop(line) else 0
                var end = line
                while (end < body.lineCount && body.getLineBottom(end) - startTop <= available) end++
                if (end == line && line < body.lineCount) end = line + 1 // línea más alta que la página
                if (line < body.lineCount) {
                    val endBottom = body.getLineBottom(end - 1)
                    c.save()
                    c.translate(MARGIN, y - startTop)
                    c.clipRect(0f, startTop.toFloat(), width.toFloat(), endBottom.toFloat())
                    body.draw(c)
                    c.restore()
                }
                // número de página
                c.drawText("$pageNo", PAGE_W / 2f, PAGE_H - MARGIN / 2, metaPaint)
                doc.finishPage(page)
                line = end
                if (line >= body.lineCount) break
                pageNo++
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Si la primera línea es el título (habitual en notas markdown), no lo repetimos en el cuerpo. */
    private fun stripTitleLine(note: Note): String {
        val lines = note.content.lines()
        val first = lines.firstOrNull()?.trimStart('#', ' ')?.trim().orEmpty()
        return if (first.isNotEmpty() && first == note.title.trim()) lines.drop(1).joinToString("\n").trimStart('\n') else note.content
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
}
