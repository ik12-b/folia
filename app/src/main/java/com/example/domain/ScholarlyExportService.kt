package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.model.BlockRegionEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentMetadataEntity
import com.example.data.model.DocumentPartEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object ScholarlyExportService {

    data class PageExportData(
        val part: DocumentPartEntity,
        val blocks: List<BlockRegionEntity>,
        val linesWithTranscriptions: List<Pair<LineSegmentEntity, LineTranscriptionEntity?>>
    )

    data class PdfExportResult(
        val file: File,
        val pageCount: Int,
        val totalLines: Int,
        val totalTranscribedLines: Int,
        val fileSizeBytes: Long
    )

    /**
     * Renders a publication-grade PDF document featuring:
     * 1. High-resolution manuscript folio images as the document base.
     * 2. PP-OCRv5 DBNet bounding box & baseline overlays.
     * 3. Full scholarly transcribed text overlaid directly on each line.
     * 4. Comprehensive philological metadata header, line numbering badges, and critical apparatus footer.
     */
    fun exportToManuscriptOverlayPdf(
        context: Context,
        document: DocumentEntity,
        metadata: DocumentMetadataEntity?,
        pages: List<PageExportData>,
        renderBoundingBoxes: Boolean = true,
        renderTranscribedOverlay: Boolean = true,
        fontSizeSp: Float = 12f
    ): PdfExportResult {
        val pdfDoc = PdfDocument()

        val pageWidth = 792 // Standard 11x8.5 pt ratio
        val pageHeight = 1120
        val headerHeight = 65
        val footerHeight = 40
        val contentMargin = 20

        val manuscriptRect = RectF(
            contentMargin.toFloat(),
            (headerHeight + 10).toFloat(),
            (pageWidth - contentMargin).toFloat(),
            (pageHeight - footerHeight - 10).toFloat()
        )

        // Paints
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Dark Slate
            style = Paint.Style.FILL
        }

        val headerTitlePaint = Paint().apply {
            color = Color.parseColor("#F59E0B") // Amber / Gold
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerMetaPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val footerTextPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val textBgPaint = Paint().apply {
            color = Color.parseColor("#FDF8EC") // Parchment highlight box
            style = Paint.Style.FILL
            alpha = 220
        }

        val textBorderPaint = Paint().apply {
            color = Color.parseColor("#D97706") // Gold border
            style = Paint.Style.STROKE
            strokeWidth = 1f
            alpha = 180
        }

        val transcribedTextPaint = Paint().apply {
            color = Color.parseColor("#1E1B18") // Deep manuscript ink
            textSize = fontSizeSp * 1.05f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT // RTL Arabic default
            isAntiAlias = true
        }

        val badgeCirclePaint = Paint().apply {
            color = Color.parseColor("#D97706")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val badgeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val baselinePaint = Paint().apply {
            color = Color.parseColor("#E11D48") // Rose/Red line
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            alpha = 150
            isAntiAlias = true
        }

        val boxFillPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
            alpha = 30
        }

        val boxStrokePaint = Paint().apply {
            color = Color.parseColor("#0284C7")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            alpha = 130
        }

        var totalLinesCount = 0
        var totalTranscribedCount = 0

        pages.forEachIndexed { pageIdx, pageData ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // 1. Draw Page Canvas Base
            canvas.drawColor(Color.parseColor("#F8F4EB")) // Parchment backdrop

            // 2. Draw Scholarly Header Bar
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight.toFloat(), headerBgPaint)

            // Header Title & Folio Number
            val pageTitle = "${document.title} — Lembar ${pageData.part.pageNumber}"
            canvas.drawText(pageTitle, contentMargin.toFloat() + 5f, 26f, headerTitlePaint)

            // Header Philological Metadata Line
            val metaParts = mutableListOf<String>()
            if (metadata != null) {
                if (metadata.author.isNotBlank()) metaParts.add("Pengarang: ${metadata.author}")
                if (metadata.shelfmark.isNotBlank()) metaParts.add("Kode: ${metadata.shelfmark}")
                if (metadata.dateText.isNotBlank()) metaParts.add("Tarikh: ${metadata.dateText}")
                if (metadata.scriptType.isNotBlank()) metaParts.add("Khat: ${metadata.scriptType}")
            }
            val metaText = if (metaParts.isNotEmpty()) metaParts.joinToString("  •  ") else "Model HTR: PP-OCRv5 • Arah: ${document.readDirection}"
            canvas.drawText(metaText, contentMargin.toFloat() + 5f, 48f, headerMetaPaint)

            // 3. Draw High-Res Manuscript Image
            val bitmap = loadBitmapForExport(context, pageData.part.imageResName, pageData.part.imageUri)
            if (bitmap != null) {
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(bitmap, srcRect, manuscriptRect, null)
            } else {
                // Realistic parchment fallback texture
                val texturePaint = Paint().apply {
                    color = Color.parseColor("#F1E4CE")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(manuscriptRect, texturePaint)
            }

            // Outer decorative frame around manuscript
            val framePaint = Paint().apply {
                color = Color.parseColor("#B45309")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                alpha = 180
            }
            canvas.drawRect(manuscriptRect, framePaint)

            // 4. Draw Lines, Bounding Boxes & Transcribed Text Overlay
            val canvasW = manuscriptRect.width()
            val canvasH = manuscriptRect.height()
            val canvasLeft = manuscriptRect.left
            val canvasTop = manuscriptRect.top

            pageData.linesWithTranscriptions.forEach { (line, transcription) ->
                totalLinesCount++
                val hasText = transcription != null && transcription.text.isNotBlank()
                if (hasText) totalTranscribedCount++

                val baselinePts = parsePointList(line.baselinePointsJson)
                val boxPts = if (line.maskPolygonJson.isNotBlank()) {
                    parsePointList(line.maskPolygonJson)
                } else {
                    if (baselinePts.size >= 2) {
                        val p1 = baselinePts.first()
                        val p2 = baselinePts.last()
                        val left = min(p1.x, p2.x)
                        val right = max(p1.x, p2.x)
                        val top = min(p1.y, p2.y) - 0.022f
                        val bottom = max(p1.y, p2.y) + 0.012f
                        listOf(
                            android.graphics.PointF(left, top),
                            android.graphics.PointF(right, top),
                            android.graphics.PointF(right, bottom),
                            android.graphics.PointF(left, bottom)
                        )
                    } else emptyList()
                }

                // Compute bounding box coordinates on PDF canvas
                val allX = (boxPts.map { it.x } + baselinePts.map { it.x })
                val allY = (boxPts.map { it.y } + baselinePts.map { it.y })
                val minX = (allX.minOrNull() ?: 0.15f) * canvasW + canvasLeft
                val maxX = (allX.maxOrNull() ?: 0.85f) * canvasW + canvasLeft
                val minY = (allY.minOrNull() ?: 0.1f) * canvasH + canvasTop
                val maxY = (allY.maxOrNull() ?: 0.9f) * canvasH + canvasTop
                val avgY = if (baselinePts.isNotEmpty()) {
                    baselinePts.map { it.y }.average().toFloat() * canvasH + canvasTop
                } else (minY + maxY) / 2f

                // Draw DBNet Bounding Box
                if (renderBoundingBoxes && boxPts.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(boxPts[0].x * canvasW + canvasLeft, boxPts[0].y * canvasH + canvasTop)
                        for (i in 1 until boxPts.size) {
                            lineTo(boxPts[i].x * canvasW + canvasLeft, boxPts[i].y * canvasH + canvasTop)
                        }
                        close()
                    }
                    canvas.drawPath(path, boxFillPaint)
                    canvas.drawPath(path, boxStrokePaint)
                }

                // Draw Baseline Vector
                if (renderBoundingBoxes && baselinePts.size >= 2) {
                    val bPath = Path().apply {
                        moveTo(baselinePts[0].x * canvasW + canvasLeft, baselinePts[0].y * canvasH + canvasTop)
                        for (i in 1 until baselinePts.size) {
                            lineTo(baselinePts[i].x * canvasW + canvasLeft, baselinePts[i].y * canvasH + canvasTop)
                        }
                    }
                    canvas.drawPath(bPath, baselinePaint)
                }

                // Draw Line Index Badge
                val badgeX = (maxX + 14f).coerceAtMost(manuscriptRect.right - 8f)
                val badgeY = avgY
                canvas.drawCircle(badgeX, badgeY, 7f, badgeCirclePaint)
                canvas.drawText("${line.orderIndex}", badgeX, badgeY + 2.8f, badgeTextPaint)

                // 5. Draw Transcribed Text Overlaid directly on the Line
                if (renderTranscribedOverlay && hasText && transcription != null) {
                    val textStr = transcription.text
                    val textWidth = transcribedTextPaint.measureText(textStr)
                    val boxH = max(18f, maxY - minY + 6f)
                    val textRectLeft = (maxX - textWidth - 12f).coerceAtLeast(minX - 6f).coerceAtLeast(manuscriptRect.left + 6f)
                    val textRectRight = (maxX + 6f).coerceAtMost(manuscriptRect.right - 20f)
                    val textRectTop = avgY - (boxH / 2f)
                    val textRectBottom = avgY + (boxH / 2f)

                    val textPill = RectF(textRectLeft, textRectTop, textRectRight, textRectBottom)
                    canvas.drawRoundRect(textPill, 4f, 4f, textBgPaint)
                    canvas.drawRoundRect(textPill, 4f, 4f, textBorderPaint)

                    // Draw text in RTL format (right aligned within the pill)
                    val textDrawX = textRectRight - 6f
                    val textDrawY = avgY + (fontSizeSp * 0.35f)
                    canvas.drawText(textStr, textDrawX, textDrawY, transcribedTextPaint)
                }
            }

            // 6. Draw Scholarly Footer
            val footerY = pageHeight - 16f
            val footerText = "Folia Manuscript HTR Platform  •  Halaman ${pageData.part.pageNumber} dari ${pages.size}  •  ${pageData.linesWithTranscriptions.size} Baris Terdeteksi"
            canvas.drawText(footerText, pageWidth / 2f, footerY, footerTextPaint)

            pdfDoc.finishPage(page)
        }

        // Write to Cache and Storage File
        val safeDocTitle = document.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
        val exportFile = File(context.cacheDir, "Folia_${safeDocTitle}_Transkripsi.pdf")
        FileOutputStream(exportFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        return PdfExportResult(
            file = exportFile,
            pageCount = pages.size,
            totalLines = totalLinesCount,
            totalTranscribedLines = totalTranscribedCount,
            fileSizeBytes = exportFile.length()
        )
    }

    private fun loadBitmapForExport(context: Context, imageResName: String?, imageUri: String?): Bitmap? {
        return try {
            if (!imageUri.isNullOrEmpty()) {
                val uri = Uri.parse(imageUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val resName = when (imageResName) {
                    "manuscript_p2" -> "manuscript_p2"
                    else -> "manuscript_p1"
                }
                val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                if (resId != 0) {
                    BitmapFactory.decodeResource(context.resources, resId)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePointList(pointsJson: String): List<android.graphics.PointF> {
        if (pointsJson.isBlank()) return emptyList()
        return try {
            pointsJson.split(";").mapNotNull { pairStr ->
                val parts = pairStr.split(",")
                if (parts.size == 2) {
                    val x = parts[0].trim().toFloatOrNull()
                    val y = parts[1].trim().toFloatOrNull()
                    if (x != null && y != null) android.graphics.PointF(x, y) else null
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun exportToPageXml(
        document: DocumentEntity,
        metadata: DocumentMetadataEntity?,
        pages: List<PageExportData>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15\"\n")
        sb.append("       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("       xsi:schemaLocation=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15 http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15/pagecontent.xsd\">\n")
        sb.append("  <Metadata>\n")
        sb.append("    <Creator>Folia Manuscript HTR Platform (KitabHTR-Tiny)</Creator>\n")
        sb.append("    <Created>${System.currentTimeMillis()}</Created>\n")
        if (metadata != null) {
            sb.append("    <Comments>Author: ${metadata.author}; Date: ${metadata.dateText}; Repository: ${metadata.repository}; Shelfmark: ${metadata.shelfmark}</Comments>\n")
        }
        sb.append("  </Metadata>\n")

        for (pageData in pages) {
            val part = pageData.part
            sb.append("  <Page imageFilename=\"${part.imageResName ?: "page_${part.pageNumber}.jpg"}\" imageWidth=\"${part.width}\" imageHeight=\"${part.height}\">\n")
            sb.append("    <ReadingOrder>\n")
            sb.append("      <OrderedGroup id=\"ro_${part.id}\">\n")
            pageData.blocks.forEachIndexed { i, block ->
                sb.append("        <RegionRefIndexed index=\"$i\" regionRef=\"reg_${block.id}\"/>\n")
            }
            sb.append("      </OrderedGroup>\n")
            sb.append("    </ReadingOrder>\n")

            // Blocks and their lines
            for (block in pageData.blocks) {
                val blockType = when (block.typology) {
                    "Heading" -> "heading"
                    "Marginalia" -> "marginalia"
                    "Footnote" -> "footnote"
                    else -> "paragraph"
                }
                val blockPoints = parsePointsToPageXmlCoords(block.polygonPointsJson, part.width, part.height)
                sb.append("    <TextRegion id=\"reg_${block.id}\" type=\"$blockType\" custom=\"structure {type:${block.typology};}\">\n")
                sb.append("      <Coords points=\"$blockPoints\"/>\n")

                val blockLines = pageData.linesWithTranscriptions.filter { it.first.blockId == block.id }
                for ((line, transcription) in blockLines) {
                    val baselineCoords = parsePointsToPageXmlCoords(line.baselinePointsJson, part.width, part.height)
                    sb.append("      <TextLine id=\"line_${line.id}\" custom=\"order:${line.orderIndex};\">\n")
                    sb.append("        <Coords points=\"$baselineCoords\"/>\n")
                    sb.append("        <Baseline points=\"$baselineCoords\"/>\n")
                    if (transcription != null && transcription.text.isNotEmpty()) {
                        sb.append("        <TextEquiv index=\"0\" confidence=\"${transcription.avgConfidence}\">\n")
                        sb.append("          <Unicode><![CDATA[${transcription.text}]]></Unicode>\n")
                        sb.append("        </TextEquiv>\n")
                    }
                    sb.append("      </TextLine>\n")
                }
                sb.append("    </TextRegion>\n")
            }
            sb.append("  </Page>\n")
        }
        sb.append("</PcGts>")
        return sb.toString()
    }

    fun exportToAltoXml(
        document: DocumentEntity,
        metadata: DocumentMetadataEntity?,
        pages: List<PageExportData>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<alto xmlns=\"http://www.loc.gov/standards/alto/ns-v4#\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.loc.gov/standards/alto/ns-v4# http://www.loc.gov/standards/alto/v4/alto-4-2.xsd\">\n")
        sb.append("  <Description>\n")
        sb.append("    <MeasurementUnit>pixel</MeasurementUnit>\n")
        sb.append("    <sourceImageInformation>\n")
        sb.append("      <fileName>${document.title}</fileName>\n")
        sb.append("    </sourceImageInformation>\n")
        sb.append("    <OCRProcessing ID=\"OCR_0\">\n")
        sb.append("      <ocrProcessingStep>\n")
        sb.append("        <processingSoftware>\n")
        sb.append("          <softwareName>Folia Scholarly HTR</softwareName>\n")
        sb.append("          <softwareVersion>1.0</softwareVersion>\n")
        sb.append("        </processingSoftware>\n")
        sb.append("      </ocrProcessingStep>\n")
        sb.append("    </OCRProcessing>\n")
        sb.append("  </Description>\n")
        sb.append("  <Layout>\n")

        for (pageData in pages) {
            val part = pageData.part
            sb.append("    <Page ID=\"PAGE_${part.pageNumber}\" PHYSICAL_IMG_NR=\"${part.pageNumber}\" WIDTH=\"${part.width}\" HEIGHT=\"${part.height}\">\n")
            sb.append("      <PrintSpace ID=\"PS_${part.id}\" HPOS=\"0\" VPOS=\"0\" WIDTH=\"${part.width}\" HEIGHT=\"${part.height}\">\n")

            for (block in pageData.blocks) {
                sb.append("        <TextBlock ID=\"TB_${block.id}\" HPOS=\"200\" VPOS=\"200\" WIDTH=\"800\" HEIGHT=\"1200\">\n")
                val blockLines = pageData.linesWithTranscriptions.filter { it.first.blockId == block.id }
                for ((line, transcription) in blockLines) {
                    val lineText = transcription?.text ?: ""
                    sb.append("          <TextLine ID=\"TL_${line.id}\">\n")
                    sb.append("            <String ID=\"STR_${line.id}\" CONTENT=\"${escapeXml(lineText)}\" WC=\"${transcription?.avgConfidence ?: 0.95f}\"/>\n")
                    sb.append("          </TextLine>\n")
                }
                sb.append("        </TextBlock>\n")
            }

            sb.append("      </PrintSpace>\n")
            sb.append("    </Page>\n")
        }
        sb.append("  </Layout>\n")
        sb.append("</alto>")
        return sb.toString()
    }

    fun exportToPlainText(
        document: DocumentEntity,
        pages: List<PageExportData>
    ): String {
        val sb = StringBuilder()
        sb.append("=== ${document.title} ===\n\n")
        for (pageData in pages) {
            sb.append("--- [صفحة / Halaman ${pageData.part.pageNumber}] ---\n\n")
            for (block in pageData.blocks) {
                sb.append("[${block.typology}: ${block.label.ifEmpty { block.typology }}]\n")
                val blockLines = pageData.linesWithTranscriptions.filter { it.first.blockId == block.id }
                for ((_, transcription) in blockLines) {
                    if (transcription != null && transcription.text.isNotBlank()) {
                        sb.append("${transcription.text}\n")
                    }
                }
                sb.append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun exportToKaggleTrainingJson(
        document: DocumentEntity,
        pages: List<PageExportData>
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"dataset_name\": \"Folia_${document.id}_KitabHTR\",\n")
        sb.append("  \"document_title\": \"${document.title.replace("\"", "\\\"")}\",\n")
        sb.append("  \"script\": \"${document.mainScript}\",\n")
        sb.append("  \"direction\": \"${document.readDirection}\",\n")
        sb.append("  \"samples\": [\n")

        val allLines = mutableListOf<String>()
        for (pageData in pages) {
            for ((line, transcription) in pageData.linesWithTranscriptions) {
                if (transcription != null && transcription.text.isNotBlank()) {
                    val jsonSample = """    {
      "page_number": ${pageData.part.pageNumber},
      "image_file": "${pageData.part.imageResName ?: "page_${pageData.part.pageNumber}.jpg"}",
      "line_id": ${line.id},
      "typology": "${line.typology}",
      "baseline_points": "${line.baselinePointsJson}",
      "ground_truth_text": "${transcription.text.replace("\"", "\\\"")}",
      "confidence": ${transcription.avgConfidence}
    }"""
                    allLines.add(jsonSample)
                }
            }
        }
        sb.append(allLines.joinToString(",\n"))
        sb.append("\n  ]\n}")
        return sb.toString()
    }

    private fun parsePointsToPageXmlCoords(pointsJson: String, width: Int, height: Int): String {
        if (pointsJson.isBlank()) return "0,0"
        return pointsJson.split(";")
            .mapNotNull { pointStr ->
                val parts = pointStr.split(",")
                if (parts.size == 2) {
                    val normX = parts[0].toFloatOrNull() ?: 0f
                    val normY = parts[1].toFloatOrNull() ?: 0f
                    val px = (normX * width).toInt()
                    val py = (normY * height).toInt()
                    "$px,$py"
                } else null
            }
            .joinToString(" ")
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
