package com.example.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.ViralGrowthKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedArtifact(
    val file: File,
    val uri: Uri,
    val mimeType: String,
    val chooserTitle: String
)

object GrowthKitExporter {

    private const val EXPORTS_DIR = "exports"
    private const val MAX_CACHED_EXPORTS = 12

    // Studio Palette (ARGB ints matching ui.theme.Color)
    private const val COLOR_OBSIDIAN = 0xFF0B0D14.toInt()
    private const val COLOR_SURFACE_DARK = 0xFF131622.toInt()
    private const val COLOR_CARD_DARK = 0xFF1B2032.toInt()
    private const val COLOR_OUTLINE_DARK = 0xFF2D3550.toInt()
    private const val COLOR_TEXT_PRIMARY_DARK = 0xFFF4F6FA.toInt()
    private const val COLOR_TEXT_MUTED_DARK = 0xFFA3ACC2.toInt()

    private const val COLOR_CORAL = 0xFFFF3D5A.toInt()
    private const val COLOR_MINT = 0xFF00D6A3.toInt()
    private const val COLOR_GOLD = 0xFFFFB800.toInt()

    private const val COLOR_PAPER = 0xFFFFFFFF.toInt()
    private const val COLOR_LIGHT_CARD = 0xFFF4F5F8.toInt()
    private const val COLOR_LIGHT_OUTLINE = 0xFFDCDFE8.toInt()
    private const val COLOR_TEXT_DARK = 0xFF131520.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF5A6178.toInt()

    suspend fun exportKitToPdf(context: Context, kit: ViralGrowthKit): ExportedArtifact =
        withContext(Dispatchers.IO) {
            val file = generatePdfFile(context, kit)
            val uri = getShareUri(context, file)
            ExportedArtifact(
                file = file,
                uri = uri,
                mimeType = "application/pdf",
                chooserTitle = "Share Viral Growth Kit PDF"
            )
        }

    suspend fun exportKitToShareImage(context: Context, kit: ViralGrowthKit): ExportedArtifact =
        withContext(Dispatchers.IO) {
            val file = generateShareImageFile(context, kit)
            val uri = getShareUri(context, file)
            ExportedArtifact(
                file = file,
                uri = uri,
                mimeType = "image/png",
                chooserTitle = "Share Viral Growth Card"
            )
        }

    fun launchShareSheet(context: Context, artifact: ExportedArtifact, kit: ViralGrowthKit): Boolean {
        val shareText = buildString {
            append("${kit.title.ifBlank { "Viral Growth Kit" }} · Score ${kit.viralScore}/100")
            append(" (${kit.platform} · ${kit.language})")
            if (kit.curiosityHook.isNotBlank()) {
                append("\n\nTop Hook: \"${kit.curiosityHook}\"")
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, artifact.uri)
            putExtra(Intent.EXTRA_SUBJECT, "${kit.title.ifBlank { "Viral Growth Kit" }} — ViralKit Strategy")
            putExtra(Intent.EXTRA_TEXT, shareText)
            clipData = ClipData.newRawUri(artifact.chooserTitle, artifact.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, artifact.chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return runCatching {
            context.startActivity(chooser)
        }.isSuccess
    }

    fun getShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: IllegalArgumentException) {
            Uri.Builder()
                .scheme("content")
                .authority(authority)
                .appendPath("shared_exports")
                .appendPath(file.name)
                .build()
        }
    }

    fun sanitizeFileName(rawTitle: String): String {
        val cleaned = rawTitle
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(36)
        return cleaned.ifBlank { "viralkit_strategy" }
    }

    private fun prepareExportFile(context: Context, prefix: String, extension: String): File {
        val dir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        pruneOldExports(dir)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${prefix}_$stamp.$extension")
    }

    private fun pruneOldExports(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.size > MAX_CACHED_EXPORTS) {
            files.drop(MAX_CACHED_EXPORTS).forEach { runCatching { it.delete() } }
        }
    }

    private fun scoreAccentColor(score: Long): Int = when {
        score >= 80L -> COLOR_MINT
        score >= 60L -> COLOR_GOLD
        else -> COLOR_CORAL
    }

    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int = Int.MAX_VALUE,
        lineSpacingMultiplier: Float = 1.18f
    ): StaticLayout {
        val safeWidth = width.coerceAtLeast(16)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // PDF Document Export (A4: 595 x 842 pt, multi-page paginated)
    // -------------------------------------------------------------------------

    private interface PdfPageSession {
        fun nextCanvas(): Canvas
        fun finishPage()
    }

    fun generatePdfFile(context: Context, kit: ViralGrowthKit): File {
        val file = prepareExportFile(context, "${sanitizeFileName(kit.title)}_kit", "pdf")
        val pageWidth = 595
        val pageHeight = 842

        val primaryResult = runCatching {
            val document = PdfDocument()
            try {
                var pageNumber = 0
                var activePage: PdfDocument.Page? = null
                val session = object : PdfPageSession {
                    override fun nextCanvas(): Canvas {
                        pageNumber++
                        val created = document.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        )
                        activePage = created
                        return created.canvas
                    }

                    override fun finishPage() {
                        activePage?.let { document.finishPage(it) }
                        activePage = null
                    }
                }
                drawKitPdfPages(kit, pageWidth, pageHeight, session)
                FileOutputStream(file).use { out ->
                    document.writeTo(out)
                }
            } finally {
                runCatching { document.close() }
            }
        }

        if (primaryResult.isFailure) {
            // Fallback for headless JVM/Robolectric or devices missing native libpdfium runtime
            generateFallbackRasterPdf(file, kit, pageWidth, pageHeight)
        }

        return file
    }

    private fun generateFallbackRasterPdf(
        file: File,
        kit: ViralGrowthKit,
        pageWidth: Int,
        pageHeight: Int
    ) {
        val scale = 2
        val pageJpegs = mutableListOf<ByteArray>()
        var currentBitmap: Bitmap? = null

        val session = object : PdfPageSession {
            override fun nextCanvas(): Canvas {
                val bmp = Bitmap.createBitmap(
                    pageWidth * scale,
                    pageHeight * scale,
                    Bitmap.Config.ARGB_8888
                )
                currentBitmap = bmp
                return Canvas(bmp).apply { scale(scale.toFloat(), scale.toFloat()) }
            }

            override fun finishPage() {
                val bmp = currentBitmap ?: return
                currentBitmap = null
                try {
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                    pageJpegs.add(baos.toByteArray())
                } finally {
                    bmp.recycle()
                }
            }
        }

        try {
            drawKitPdfPages(kit, pageWidth, pageHeight, session)
            writeJpegPagesAsPdf14(file, pageJpegs, pageWidth, pageHeight, pageWidth * scale, pageHeight * scale)
        } finally {
            currentBitmap?.recycle()
        }
    }

    private fun writeJpegPagesAsPdf14(
        file: File,
        pageJpegs: List<ByteArray>,
        pageWidthPt: Int,
        pageHeightPt: Int,
        pixelWidth: Int,
        pixelHeight: Int
    ) {
        val out = java.io.ByteArrayOutputStream()
        fun writeAscii(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        val offsets = mutableListOf<Int>()
        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        val pageCount = pageJpegs.size.coerceAtLeast(1)
        val pageObjIds = (0 until pageCount).map { 3 + it * 3 }

        // 1: Catalog
        offsets.add(out.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // 2: Pages
        offsets.add(out.size())
        val kidsRef = pageObjIds.joinToString(" ") { "$it 0 R" }
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [ $kidsRef ] /Count $pageCount >>\nendobj\n")

        // Each page uses 3 objects: Page, Content stream, Image XObject
        pageJpegs.forEachIndexed { idx, jpegBytes ->
            val pageObjId = 3 + idx * 3
            val contentObjId = pageObjId + 1
            val imageObjId = pageObjId + 2
            val imgName = "Im${idx + 1}"

            offsets.add(out.size())
            writeAscii(
                "$pageObjId 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidthPt $pageHeightPt] " +
                    "/Resources << /XObject << /$imgName $imageObjId 0 R >> >> " +
                    "/Contents $contentObjId 0 R >>\nendobj\n"
            )

            val contentCmd = "q\n$pageWidthPt 0 0 $pageHeightPt 0 0 cm\n/$imgName Do\nQ\n"
            val contentBytes = contentCmd.toByteArray(Charsets.US_ASCII)
            offsets.add(out.size())
            writeAscii("$contentObjId 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
            out.write(contentBytes)
            writeAscii("\nendstream\nendobj\n")

            offsets.add(out.size())
            writeAscii(
                "$imageObjId 0 obj\n" +
                    "<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                    "/Length ${jpegBytes.size} >>\nstream\n"
            )
            out.write(jpegBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = out.size()
        val totalObjects = offsets.size + 1
        writeAscii("xref\n0 $totalObjects\n")
        writeAscii("0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size $totalObjects /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        FileOutputStream(file).use { fos ->
            fos.write(out.toByteArray())
        }
    }

    private fun drawKitPdfPages(
        kit: ViralGrowthKit,
        pageWidth: Int,
        pageHeight: Int,
        session: PdfPageSession
    ) {
        val margin = 36f
        val contentWidth = (pageWidth - margin * 2).toInt()
        val footerTop = pageHeight - 38f

        var pageNumber = 1
        var canvas = session.nextCanvas()

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY_DARK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED_DARK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val sectionHeadingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DARK
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CORAL
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DARK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 9f
        }

        fun drawFooter(targetCanvas: Canvas, currentPage: Int) {
            strokePaint.color = COLOR_LIGHT_OUTLINE
            targetCanvas.drawLine(margin, footerTop - 8f, pageWidth - margin, footerTop - 8f, strokePaint)
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
            targetCanvas.drawText("ViralKit · AI Retention & Viral Growth Dossier · $dateStr", margin, footerTop + 8f, mutedPaint)
            val pageStr = "Page $currentPage"
            val pageStrW = mutedPaint.measureText(pageStr)
            targetCanvas.drawText(pageStr, pageWidth - margin - pageStrW, footerTop + 8f, mutedPaint)
        }

        fun startNextPage(): Float {
            drawFooter(canvas, pageNumber)
            session.finishPage()
            pageNumber++
            canvas = session.nextCanvas()
            bgPaint.color = COLOR_PAPER
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            // Continuation micro-header
            bgPaint.color = COLOR_OBSIDIAN
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 38f, bgPaint)
            val contPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY_DARK
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("VIRALKIT STRATEGY · ${kit.title.take(48)}", margin, 24f, contPaint)
            return 58f
        }

        fun ensureSpace(currentY: Float, requiredHeight: Float): Float {
            return if (currentY + requiredHeight > footerTop - 16f) {
                startNextPage()
            } else {
                currentY
            }
        }

        // Page 1 background
        bgPaint.color = COLOR_PAPER
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        // Hero Header Banner
        val headerHeight = 112f
        bgPaint.color = COLOR_OBSIDIAN
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, bgPaint)

        val accent = scoreAccentColor(kit.viralScore)
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("VIRALKIT · VIRAL GROWTH KIT", margin, 26f, badgePaint)

        val safeTitle = kit.title.ifBlank { "Viral Growth Kit" }
        val titleLayout = createStaticLayout(safeTitle, titlePaint, contentWidth - 96, maxLines = 2)
        drawLayout(canvas, titleLayout, margin, 36f)

        val metaText = "${kit.platform} · ${kit.language} · ${kit.modelUsed}" +
            if (kit.mediaLabel.isNotBlank()) " · ${kit.mediaLabel}" else ""
        val metaLayout = createStaticLayout(metaText, subtitlePaint, contentWidth - 96, maxLines = 1)
        drawLayout(canvas, metaLayout, margin, 86f)

        // Score Circle in Header
        val cx = pageWidth - margin - 34f
        val cy = headerHeight / 2f
        val radius = 28f
        strokePaint.color = COLOR_OUTLINE_DARK
        strokePaint.strokeWidth = 5f
        canvas.drawCircle(cx, cy, radius, strokePaint)

        strokePaint.color = accent
        strokePaint.strokeCap = Paint.Cap.ROUND
        val scoreRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = (kit.viralScore.coerceIn(0L, 100L) / 100f) * 360f
        canvas.drawArc(scoreRect, -90f, sweep, false, strokePaint)
        strokePaint.strokeWidth = 1f

        val scoreNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY_DARK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val scoreSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED_DARK
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${kit.viralScore}", cx, cy + 4f, scoreNumPaint)
        canvas.drawText("/100", cx, cy + 14f, scoreSubPaint)

        var y = headerHeight + 22f

        fun drawSectionBox(label: String, text: String, accentColor: Int) {
            if (text.isBlank()) return
            val pad = 12f
            val innerW = (contentWidth - pad * 2).toInt()
            val textLayout = createStaticLayout(text, bodyPaint, innerW)
            val boxH = 26f + textLayout.height + pad
            y = ensureSpace(y, boxH + 10f)

            bgPaint.color = COLOR_LIGHT_CARD
            val rect = RectF(margin, y, pageWidth - margin, y + boxH)
            canvas.drawRoundRect(rect, 10f, 10f, bgPaint)

            // Left accent bar
            bgPaint.color = accentColor
            canvas.drawRoundRect(RectF(margin, y, margin + 4f, y + boxH), 2f, 2f, bgPaint)

            labelPaint.color = accentColor
            canvas.drawText(label, margin + pad + 4f, y + 17f, labelPaint)
            drawLayout(canvas, textLayout, margin + pad + 4f, y + 25f)
            y += boxH + 10f
        }

        // 1. Diagnosis & First 30s Strategy
        drawSectionBox("RETENTION DIAGNOSIS", kit.diagnosisCritique, accent)
        drawSectionBox("FIRST 30-SECOND ENGAGEMENT STRATEGY", kit.first30sStrategy, COLOR_GOLD)

        // 2. High-Retention Hooks
        y = ensureSpace(y, 44f)
        canvas.drawText("High-Retention Hooks (First 3 Seconds)", margin, y + 12f, sectionHeadingPaint)
        y += 22f

        drawSectionBox("CURIOSITY / MYSTERY HOOK", kit.curiosityHook, COLOR_CORAL)
        drawSectionBox("CONTRARIAN / SHOCKING HOOK", kit.contrarianHook, COLOR_GOLD)
        drawSectionBox("DIRECT VALUE / PROBLEM-SOLUTION HOOK", kit.directValueHook, COLOR_MINT)

        // 3. Pacing Segments
        if (kit.pacingSegments.isNotEmpty()) {
            y = ensureSpace(y, 50f)
            canvas.drawText("Timestamped B-Roll & Visual Pacing Guide", margin, y + 12f, sectionHeadingPaint)
            y += 22f

            kit.pacingSegments.forEach { seg ->
                val segText = buildString {
                    if (seg.bRollSuggestion.isNotBlank()) append("Visual/B-Roll: ${seg.bRollSuggestion}")
                    if (seg.onScreenText.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("On-Screen Text: \"${seg.onScreenText}\"")
                    }
                    if (seg.cutAndSfx.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("Cut & SFX: ${seg.cutAndSfx}")
                    }
                }
                drawSectionBox(seg.timestamp.ifBlank { "Segment" }.uppercase(Locale.getDefault()), segText, COLOR_OBSIDIAN)
            }
        }

        // 4. Captions, Hashtags & YouTube Metadata
        y = ensureSpace(y, 50f)
        canvas.drawText("Captions, Hashtags & YouTube Shorts SEO", margin, y + 12f, sectionHeadingPaint)
        y += 22f

        val igFull = buildString {
            append(kit.instagramCaption)
            if (kit.hashtags.isNotEmpty()) {
                append("\n\n")
                append(kit.hashtags.joinToString(" "))
            }
        }
        drawSectionBox("INSTAGRAM REEL CAPTION & HASHTAGS", igFull, COLOR_CORAL)

        val ytFull = buildString {
            if (kit.youtubeTitle.isNotBlank()) append("Title: ${kit.youtubeTitle}")
            if (kit.youtubeDescription.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(kit.youtubeDescription)
            }
        }
        drawSectionBox("YOUTUBE SHORTS TITLE & DESCRIPTION", ytFull, COLOR_MINT)

        // 5. Thumbnail Ideas & Trending Topics
        if (kit.thumbnailIdeas.isNotEmpty()) {
            drawSectionBox(
                "THUMBNAIL TEXT OVERLAY IDEAS",
                kit.thumbnailIdeas.mapIndexed { idx, idea -> "${idx + 1}. $idea" }.joinToString("\n"),
                COLOR_GOLD
            )
        }
        if (kit.trendingTopics.isNotEmpty()) {
            drawSectionBox(
                "TRENDING INDUSTRY TOPICS (GOOGLE SEARCH GROUNDED)",
                kit.trendingTopics.joinToString("  ·  "),
                COLOR_MINT
            )
        }

        drawFooter(canvas, pageNumber)
        session.finishPage()
    }

    // -------------------------------------------------------------------------
    // Social Share Image Card Export (1080 x 1350 PNG, 4:5 Portrait)
    // -------------------------------------------------------------------------

    fun generateShareImageFile(context: Context, kit: ViralGrowthKit): File {
        val file = prepareExportFile(context, "${sanitizeFileName(kit.title)}_card", "png")
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        // 1. Studio Dark Gradient Background + Radial Glow
        bgPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(COLOR_OBSIDIAN, COLOR_SURFACE_DARK, 0xFF0E1324.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

        val accent = scoreAccentColor(kit.viralScore)
        bgPaint.shader = RadialGradient(
            width * 0.85f, height * 0.14f, 460f,
            intArrayOf((accent and 0x00FFFFFF) or 0x33000000, 0x00000000),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

        val margin = 56f
        val contentWidth = (width - margin * 2).toInt()

        // 2. Top Brand Row
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CORAL
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("VIRALKIT", margin, 82f, brandPaint)

        val pillText = "${kit.platform} · ${kit.language}"
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY_DARK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pillW = pillTextPaint.measureText(pillText) + 40f
        val pillRect = RectF(width - margin - pillW, 48f, width - margin, 96f)
        bgPaint.color = COLOR_CARD_DARK
        canvas.drawRoundRect(pillRect, 24f, 24f, bgPaint)
        strokePaint.color = COLOR_OUTLINE_DARK
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(pillRect, 24f, 24f, strokePaint)
        canvas.drawText(pillText, pillRect.left + 20f, 80f, pillTextPaint)

        // 3. Hero Score & Title Card
        val heroTop = 128f
        val heroHeight = 290f
        val heroRect = RectF(margin, heroTop, width - margin, heroTop + heroHeight)
        bgPaint.color = COLOR_CARD_DARK
        canvas.drawRoundRect(heroRect, 32f, 32f, bgPaint)
        strokePaint.color = (accent and 0x00FFFFFF) or 0x66000000
        strokePaint.strokeWidth = 2.5f
        canvas.drawRoundRect(heroRect, 32f, 32f, strokePaint)

        // Score Ring
        val ringCx = margin + 124f
        val ringCy = heroTop + heroHeight / 2f
        val ringR = 84f
        strokePaint.color = COLOR_OUTLINE_DARK
        strokePaint.strokeWidth = 14f
        canvas.drawCircle(ringCx, ringCy, ringR, strokePaint)

        strokePaint.color = accent
        strokePaint.strokeCap = Paint.Cap.ROUND
        val sweep = (kit.viralScore.coerceIn(0L, 100L) / 100f) * 360f
        canvas.drawArc(
            RectF(ringCx - ringR, ringCy - ringR, ringCx + ringR, ringCy + ringR),
            -90f,
            sweep,
            false,
            strokePaint
        )

        val bigScorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 60f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val scoreCaptionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED_DARK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${kit.viralScore}", ringCx, ringCy + 14f, bigScorePaint)
        canvas.drawText("VIRAL SCORE", ringCx, ringCy + 42f, scoreCaptionPaint)

        // Title & Diagnosis on right side of hero card
        val heroTextX = margin + 244f
        val heroTextW = (width - margin - 32f - heroTextX).toInt()
        val heroTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY_DARK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heroDiagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED_DARK
            textSize = 24f
        }
        val titleLayout = createStaticLayout(kit.title.ifBlank { "Viral Growth Kit" }, heroTitlePaint, heroTextW, maxLines = 2)
        drawLayout(canvas, titleLayout, heroTextX, heroTop + 34f)
        val diagY = heroTop + 48f + titleLayout.height
        val diagLayout = createStaticLayout(kit.diagnosisCritique, heroDiagPaint, heroTextW, maxLines = 4)
        drawLayout(canvas, diagLayout, heroTextX, diagY)

        // 4. Hooks Section
        var cursorY = heroTop + heroHeight + 40f
        val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED_DARK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("SCROLL-STOPPING HOOKS (FIRST 3 SECONDS)", margin, cursorY, sectionTitlePaint)
        cursorY += 20f

        val hookLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val hookBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY_DARK
            textSize = 25f
        }

        val hooks = listOf(
            Triple("CURIOSITY HOOK", kit.curiosityHook, COLOR_CORAL),
            Triple("CONTRARIAN HOOK", kit.contrarianHook, COLOR_GOLD),
            Triple("DIRECT VALUE HOOK", kit.directValueHook, COLOR_MINT)
        )

        hooks.forEach { (label, hookText, hookAccent) ->
            val cardPad = 24f
            val layout = createStaticLayout(
                hookText.ifBlank { "Hook ready inside ViralKit" },
                hookBodyPaint,
                (contentWidth - cardPad * 2 - 12f).toInt(),
                maxLines = 3
            )
            val cardH = 44f + layout.height + cardPad
            val rect = RectF(margin, cursorY, width - margin, cursorY + cardH)
            bgPaint.color = COLOR_CARD_DARK
            canvas.drawRoundRect(rect, 22f, 22f, bgPaint)

            bgPaint.color = hookAccent
            canvas.drawRoundRect(RectF(margin, cursorY, margin + 8f, cursorY + cardH), 4f, 4f, bgPaint)

            hookLabelPaint.color = hookAccent
            canvas.drawText(label, margin + cardPad + 4f, cursorY + 32f, hookLabelPaint)
            drawLayout(canvas, layout, margin + cardPad + 4f, cursorY + 46f)
            cursorY += cardH + 18f
        }

        // 5. Thumbnail Ideas & First 30s Strategy Card (remaining space above footer)
        val footerY = height - 64f
        val availableH = footerY - cursorY - 22f
        if (availableH >= 150f) {
            val bottomCardRect = RectF(margin, cursorY, width - margin, footerY - 22f)
            bgPaint.color = COLOR_SURFACE_DARK
            canvas.drawRoundRect(bottomCardRect, 24f, 24f, bgPaint)
            strokePaint.color = COLOR_OUTLINE_DARK
            strokePaint.strokeWidth = 2f
            canvas.drawRoundRect(bottomCardRect, 24f, 24f, strokePaint)

            var innerY = cursorY + 34f
            hookLabelPaint.color = COLOR_GOLD
            canvas.drawText("FIRST 30s RETENTION STRATEGY", margin + 26f, innerY, hookLabelPaint)
            innerY += 16f

            val stratPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY_DARK
                textSize = 23f
            }
            val stratLayout = createStaticLayout(
                kit.first30sStrategy.ifBlank { "Deliver value immediately after the 3-second pattern interrupt." },
                stratPaint,
                contentWidth - 52,
                maxLines = 3
            )
            drawLayout(canvas, stratLayout, margin + 26f, innerY)
            innerY += stratLayout.height + 24f

            val tagsLine = when {
                kit.thumbnailIdeas.isNotEmpty() ->
                    "Thumbnails: " + kit.thumbnailIdeas.take(3).joinToString("  •  ")
                kit.hashtags.isNotEmpty() ->
                    kit.hashtags.take(5).joinToString("  ")
                else -> ""
            }
            if (tagsLine.isNotBlank() && innerY + 30f < footerY - 30f) {
                val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_MINT
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val tagLayout = createStaticLayout(tagsLine, tagPaint, contentWidth - 52, maxLines = 2)
                drawLayout(canvas, tagLayout, margin + 26f, innerY)
            }
        }

        // 6. Footer Watermark
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED_DARK
            textSize = 21f
        }
        canvas.drawText("Generated with ViralKit · AI Retention Studio", margin, height - 26f, footerPaint)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }

        return file
    }
}
