package com.example.evinspector.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.example.evinspector.domain.ChargerStatus
import com.example.evinspector.domain.SessionReport
import com.example.evinspector.domain.TimeFormats
import com.example.evinspector.domain.summary
import java.io.File
import java.io.FileOutputStream

private const val PAGE_WIDTH = 595 // A4 at 72 dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN - 10f

private const val DETAIL_INDENT = 12f
private const val PHOTO_GAP = 10f

/**
 * Renders the human-readable report with the platform [PdfDocument] (no third-party dependency):
 * summary, a colour overview grid that mirrors the dashboard, then a failure breakdown with the
 * damage descriptions and photos, and finally the chargers that were not fully inspected.
 */
class PdfReportWriter {

    private fun text(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private val title = text(20f, bold = true)
    private val heading = text(14f, bold = true)
    private val subheading = text(12f, bold = true)
    private val body = text(11f)
    private val bodyBold = text(11f, bold = true)
    private val warning = text(11f, bold = true, color = Color.rgb(198, 40, 40))
    private val small = text(9f, color = Color.DKGRAY)
    private val tileLabel = text(9f, bold = true)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun write(report: SessionReport, target: File) {
        val doc = PdfDocument()
        try {
            val pager = Pager(doc, small)
            pager.newPage()
            drawSummary(pager, report)
            drawOverview(pager, report)
            drawFailures(pager, report)
            drawNotFullyInspected(pager, report)
            pager.close()
            FileOutputStream(target).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // ------------------------------------------------------------------ sections

    private fun drawSummary(pager: Pager, report: SessionReport) {
        val s = report.summary()
        paragraph(pager, "EV Charger Inspection Report", title, after = 10f)
        paragraph(pager, "Inspector: ${report.inspectorId}", body)
        paragraph(pager, "Session started: ${TimeFormats.human(report.startedAtMillis, report.zone)}", body)
        paragraph(pager, "Report generated: ${TimeFormats.human(report.generatedAtMillis, report.zone)}", body, after = 8f)
        paragraph(
            pager,
            "Completion: ${s.inspected} of ${s.total} chargers inspected (${s.percentInspected}%)",
            bodyBold,
        )
        paragraph(
            pager,
            "All clear: ${s.clear}    With issues: ${s.issues}    Partially checked: ${s.partial}    Not checked: ${s.unchecked}",
            body,
        )
        paragraph(pager, "Failed components: ${s.failedComponents}", body, after = 6f)
        val incomplete = s.partial + s.unchecked
        if (incomplete > 0) {
            paragraph(
                pager,
                "INCOMPLETE: $incomplete charger(s) were not fully inspected (listed at the end of this report).",
                warning,
            )
        }
        pager.y += 10f
    }

    private fun drawOverview(pager: Pager, report: SessionReport) {
        sectionHeading(pager, "Charger overview")

        // Legend
        pager.ensure(20f)
        var x = MARGIN
        val legend = listOf(
            ChargerStatus.UNCHECKED to "Not checked",
            ChargerStatus.PARTIAL to "Partial",
            ChargerStatus.CLEAR to "All clear",
            ChargerStatus.ISSUES to "Issues",
        )
        for ((status, name) in legend) {
            fill.color = colorFor(status)
            pager.canvas.drawRect(x, pager.y, x + 10f, pager.y + 10f, fill)
            pager.canvas.drawText(name, x + 14f, pager.y + 9f, small)
            x += 14f + small.measureText(name) + 16f
        }
        pager.y += 20f

        // Grid
        val tileW = 44f
        val tileH = 28f
        val gap = 4f
        val perRow = ((CONTENT_WIDTH + gap) / (tileW + gap)).toInt().coerceAtLeast(1)
        for (row in report.chargers.chunked(perRow)) {
            pager.ensure(tileH + gap)
            row.forEachIndexed { i, charger ->
                val left = MARGIN + i * (tileW + gap)
                fill.color = colorFor(charger.status)
                pager.canvas.drawRoundRect(RectF(left, pager.y, left + tileW, pager.y + tileH), 4f, 4f, fill)
                tileLabel.color = if (charger.status == ChargerStatus.PARTIAL) Color.BLACK else Color.WHITE
                val labelWidth = tileLabel.measureText(charger.label)
                pager.canvas.drawText(charger.label, left + (tileW - labelWidth) / 2f, pager.y + tileH / 2f + 3.5f, tileLabel)
            }
            pager.y += tileH + gap
        }
        pager.y += 10f
    }

    private fun drawFailures(pager: Pager, report: SessionReport) {
        sectionHeading(pager, "Failure details")
        val affected = report.chargers.filter { it.failedFindings.isNotEmpty() }
        if (affected.isEmpty()) {
            paragraph(pager, "No failures were recorded.", body, after = 8f)
            return
        }
        for (charger in affected) {
            pager.ensure(60f) // keep a charger heading together with its first finding
            val whenText = charger.inspectedAtMillis
                ?.let { "inspected ${TimeFormats.human(it, report.zone)}" }
                ?: "not saved as complete"
            paragraph(pager, "${charger.label}  -  $whenText", subheading, after = 3f)
            for (finding in charger.failedFindings) {
                val description = finding.note.ifBlank { "(no description provided)" }
                paragraph(pager, "${finding.component.label}: $description", body, indent = DETAIL_INDENT, after = 4f)
                drawPhotos(pager, finding.photoPaths)
            }
            pager.y += 8f
        }
    }

    private fun drawNotFullyInspected(pager: Pager, report: SessionReport) {
        val notChecked = report.chargers.filter { it.status == ChargerStatus.UNCHECKED }.map { it.label }
        val partial = report.chargers.filter { it.status == ChargerStatus.PARTIAL }.map { it.label }
        if (notChecked.isEmpty() && partial.isEmpty()) return

        sectionHeading(pager, "Not fully inspected")
        if (notChecked.isNotEmpty()) {
            paragraph(pager, "Not checked (${notChecked.size}): ${notChecked.joinToString(", ")}", body, after = 6f)
        }
        if (partial.isNotEmpty()) {
            paragraph(
                pager,
                "Partially checked, not saved as complete (${partial.size}): ${partial.joinToString(", ")}",
                body,
                after = 6f,
            )
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun drawPhotos(pager: Pager, paths: List<String>) {
        if (paths.isEmpty()) return
        val columnWidth = (CONTENT_WIDTH - DETAIL_INDENT - PHOTO_GAP) / 2f
        for (pair in paths.chunked(2)) {
            val bitmaps: List<Bitmap?> = pair.map { decode(it) }
            val heights = bitmaps.map { b -> if (b == null) 14f else columnWidth * b.height / b.width }
            val rowHeight = heights.maxOrNull() ?: 0f
            pager.ensure(rowHeight + 6f)
            pair.indices.forEach { i ->
                val left = MARGIN + DETAIL_INDENT + i * (columnWidth + PHOTO_GAP)
                val bitmap = bitmaps[i]
                if (bitmap != null) {
                    pager.canvas.drawBitmap(bitmap, null, RectF(left, pager.y, left + columnWidth, pager.y + heights[i]), photoPaint)
                    bitmap.recycle()
                } else {
                    pager.canvas.drawText("[photo unavailable]", left, pager.y + 10f, small)
                }
            }
            pager.y += rowHeight + 6f
        }
    }

    private fun decode(path: String): Bitmap? =
        try {
            BitmapFactory.decodeFile(path)
        } catch (e: OutOfMemoryError) {
            null
        }

    private fun sectionHeading(pager: Pager, text: String) {
        pager.ensure(50f) // never leave a heading stranded at the bottom of a page
        pager.y += 4f
        paragraph(pager, text, heading, after = 6f)
    }

    /** Draws wrapped text at the cursor, starting a new page first if it would not fit. */
    private fun paragraph(
        pager: Pager,
        text: String,
        paint: TextPaint,
        indent: Float = 0f,
        after: Float = 3f,
    ) {
        val width = (CONTENT_WIDTH - indent).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
        pager.ensure(layout.height.toFloat())
        pager.canvas.save()
        pager.canvas.translate(MARGIN + indent, pager.y)
        layout.draw(pager.canvas)
        pager.canvas.restore()
        pager.y += layout.height + after
    }

    private fun colorFor(status: ChargerStatus): Int = when (status) {
        ChargerStatus.UNCHECKED -> Color.rgb(117, 117, 117)
        ChargerStatus.PARTIAL -> Color.rgb(249, 168, 37)
        ChargerStatus.CLEAR -> Color.rgb(46, 125, 50)
        ChargerStatus.ISSUES -> Color.rgb(198, 40, 40)
    }

    /** Tracks the current page and a vertical cursor; adds a page-number footer when a page closes. */
    private class Pager(private val doc: PdfDocument, private val footerPaint: TextPaint) {
        private var page: PdfDocument.Page? = null
        var pageNumber = 0
            private set
        lateinit var canvas: Canvas
            private set
        var y = MARGIN

        fun newPage() {
            closeCurrent()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val p = doc.startPage(info)
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        fun ensure(height: Float) {
            if (y + height > CONTENT_BOTTOM) newPage()
        }

        fun close() = closeCurrent()

        private fun closeCurrent() {
            val p = page ?: return
            val label = "Page $pageNumber"
            p.canvas.drawText(label, (PAGE_WIDTH - footerPaint.measureText(label)) / 2f, PAGE_HEIGHT - 22f, footerPaint)
            doc.finishPage(p)
            page = null
        }
    }
}
