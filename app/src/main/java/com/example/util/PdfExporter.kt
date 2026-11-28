package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.ui.VisitMemoryItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfVisitPage(
    val locationName: String,
    val dateEpochDay: Long,
    val visitHour: Int,
    val visitMinute: Int,
    val description: String,
    val mediaItems: List<Foto>
) {
    val photoCount: Int get() = mediaItems.count { !it.isVideo }
    val videoCount: Int get() = mediaItems.count { it.isVideo }
}

data class PdfExportSpec(
    val coverTitle: String,
    val coverDateLabel: String,
    val coverSubtitle: String,
    val suggestedFileName: String,
    val pages: List<PdfVisitPage>
) {
    val totalPhotos: Int get() = pages.sumOf { it.photoCount }
    val totalVideos: Int get() = pages.sumOf { it.videoCount }
}

data class ExportedPdfResult(
    val cacheFile: File,
    val contentUri: Uri,
    val fileName: String,
    val savedLocationNote: String,
    val pageCount: Int
)

object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 42f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2)
    private const val PDF_EXPORT_DIR = "pdf_exports"
    private const val MAX_MEDIA_PER_PAGE = 4

    fun buildSingleVisitPdfSpec(detail: VisitMemoryItem): PdfExportSpec {
        val k = detail.kunjungan
        val desc = k.memoryNotes.ifBlank { k.planNotes }.trim()
        val page = PdfVisitPage(
            locationName = k.locationName,
            dateEpochDay = k.visitDateEpochDay,
            visitHour = k.visitHour,
            visitMinute = k.visitMinute,
            description = desc,
            mediaItems = detail.fotos.sortedBy { it.createdAtMillis }
        )
        return PdfExportSpec(
            coverTitle = k.locationName,
            coverDateLabel = DateUtils.formatIndonesianFullDate(k.visitDateEpochDay),
            coverSubtitle = "Kenangan Kunjungan • ${detail.trip.name}",
            suggestedFileName = buildPdfFileName("Kenangan_${k.locationName}"),
            pages = listOf(page)
        )
    }

    fun buildGroupPdfSpec(
        groupTitle: String,
        kunjunganList: List<Kunjungan>,
        fotosByVisitId: Map<String, List<Foto>>,
        fallbackDateLabel: String = ""
    ): PdfExportSpec {
        val sortedVisits = kunjunganList.sortedWith(
            compareBy<Kunjungan> { it.visitDateEpochDay }
                .thenBy { it.visitHour }
                .thenBy { it.visitMinute }
                .thenBy { it.createdAtMillis }
        )
        val distinctDays = sortedVisits.map { it.visitDateEpochDay }.distinct()
        val dateRangeLabel = when {
            distinctDays.isEmpty() -> fallbackDateLabel.ifBlank { "Belum ada tanggal kunjungan" }
            distinctDays.size == 1 -> DateUtils.formatIndonesianFullDate(distinctDays.first())
            else -> "${DateUtils.formatIndonesianShortDate(distinctDays.min())} – ${DateUtils.formatIndonesianShortDate(distinctDays.max())}"
        }
        val pages = sortedVisits.map { k ->
            PdfVisitPage(
                locationName = k.locationName,
                dateEpochDay = k.visitDateEpochDay,
                visitHour = k.visitHour,
                visitMinute = k.visitMinute,
                description = k.memoryNotes.ifBlank { k.planNotes }.trim(),
                mediaItems = fotosByVisitId[k.id].orEmpty().sortedBy { it.createdAtMillis }
            )
        }
        return PdfExportSpec(
            coverTitle = groupTitle.ifBlank { "Album Kenangan Grup" },
            coverDateLabel = dateRangeLabel,
            coverSubtitle = "Album Kenangan Grup TripBuddy",
            suggestedFileName = buildPdfFileName("Grup_${groupTitle}"),
            pages = pages
        )
    }

    fun buildPdfFileName(rawTitle: String, timestampMillis: Long = System.currentTimeMillis()): String {
        val slug = rawTitle
            .trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "TripBuddy" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestampMillis))
        return "TripBuddy_${slug}_$stamp.pdf"
    }

    fun exportSpecToPdf(context: Context, spec: PdfExportSpec): Result<ExportedPdfResult> = runCatching {
        val pdfDocument = PdfDocument()
        try {
            drawCoverPage(pdfDocument, spec)
            if (spec.pages.isEmpty()) {
                drawEmptyGroupNoticePage(pdfDocument, spec.coverTitle)
            } else {
                spec.pages.forEachIndexed { index, visitPage ->
                    drawVisitPage(
                        context = context,
                        pdfDocument = pdfDocument,
                        pageNumber = index + 2,
                        visitIndex = index + 1,
                        totalVisits = spec.pages.size,
                        visitPage = visitPage
                    )
                }
            }

            val cacheDir = File(context.cacheDir, PDF_EXPORT_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val cacheFile = File(cacheDir, spec.suggestedFileName)
            cacheFile.outputStream().use { out ->
                pdfDocument.writeTo(out)
            }

            val savedNote = saveCopyToDownloadsOrAppFiles(context, cacheFile, spec.suggestedFileName)
            val contentUri = PhotoStorageHelper.getUriForFile(context, cacheFile)

            ExportedPdfResult(
                cacheFile = cacheFile,
                contentUri = contentUri,
                fileName = spec.suggestedFileName,
                savedLocationNote = savedNote,
                pageCount = 1 + maxOf(1, spec.pages.size)
            )
        } finally {
            pdfDocument.close()
        }
    }

    fun copyFileToUri(context: Context, sourceFile: File, destUri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(destUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Tidak dapat menulis file ke lokasi tujuan.")
    }

    private fun drawCoverPage(pdfDocument: PdfDocument, spec: PdfExportSpec) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        val accentPaint = Paint().apply { color = Color.parseColor("#0284C7") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 16f, accentPaint)

        val heroBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F9FF")
        }
        val heroRect = RectF(MARGIN_H, 68f, PAGE_WIDTH - MARGIN_H, 420f)
        canvas.drawRoundRect(heroRect, 20f, 20f, heroBoxPaint)

        val heroBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BAE6FD")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(heroRect, 20f, 20f, heroBorderPaint)

        val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0369A1")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("TRIPBUDDY • KENANGAN PERJALANAN", MARGIN_H + 24f, 114f, eyebrowPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder.obtain(
            spec.coverTitle,
            0,
            spec.coverTitle.length,
            titlePaint,
            (CONTENT_WIDTH - 48f).toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.0f)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(MARGIN_H + 24f, 140f)
        titleLayout.draw(canvas)
        canvas.restore()

        val dateY = 140f + titleLayout.height + 22f
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dateLayout = StaticLayout.Builder.obtain(
            spec.coverDateLabel,
            0,
            spec.coverDateLabel.length,
            datePaint,
            (CONTENT_WIDTH - 48f).toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .build()

        canvas.save()
        canvas.translate(MARGIN_H + 24f, dateY)
        dateLayout.draw(canvas)
        canvas.restore()

        val subtitleY = dateY + dateLayout.height + 12f
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 12f
        }
        canvas.drawText(spec.coverSubtitle, MARGIN_H + 24f, subtitleY + 12f, subtitlePaint)

        val statsText = buildString {
            append("${spec.pages.size} Kunjungan")
            append(" • ${spec.totalPhotos} Foto")
            if (spec.totalVideos > 0) {
                append(" • ${spec.totalVideos} Video")
            }
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F2FE")
        }
        val pillRect = RectF(MARGIN_H + 24f, 354f, PAGE_WIDTH - MARGIN_H - 24f, 396f)
        canvas.drawRoundRect(pillRect, 12f, 12f, pillPaint)

        val statsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0369A1")
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(statsText, MARGIN_H + 40f, 380f, statsPaint)

        // Itinerary index summary on cover page
        if (spec.pages.isNotEmpty()) {
            val listHeadingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Daftar Kunjungan", MARGIN_H, 464f, listHeadingPaint)

            val itemPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 11f
            }
            var y = 488f
            val maxPreview = 11
            spec.pages.take(maxPreview).forEachIndexed { idx, item ->
                val line = "${idx + 1}. ${item.locationName} (${DateUtils.formatIndonesianShortDate(item.dateEpochDay)} • ${DateUtils.formatTime(item.visitHour, item.visitMinute)} WIB)"
                val ellipsized = TextUtils.ellipsize(line, itemPaint, CONTENT_WIDTH, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsized, MARGIN_H, y, itemPaint)
                y += 24f
            }
            if (spec.pages.size > maxPreview) {
                val morePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#64748B")
                    textSize = 10.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                }
                canvas.drawText("...dan ${spec.pages.size - maxPreview} kunjungan lainnya", MARGIN_H, y, morePaint)
            }
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 10f
        }
        canvas.drawText("Dibuat dengan TripBuddy", MARGIN_H, PAGE_HEIGHT - 32f, footerPaint)

        pdfDocument.finishPage(page)
    }

    private fun drawEmptyGroupNoticePage(pdfDocument: PdfDocument, groupTitle: String) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 13f
        }
        canvas.drawText(
            "Belum ada lokasi kunjungan di \"$groupTitle\".",
            MARGIN_H,
            120f,
            textPaint
        )
        pdfDocument.finishPage(page)
    }

    private fun drawVisitPage(
        context: Context,
        pdfDocument: PdfDocument,
        pageNumber: Int,
        visitIndex: Int,
        totalVisits: Int,
        visitPage: PdfVisitPage
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        // Top subtle bar
        val topStripPaint = Paint().apply { color = Color.parseColor("#0284C7") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, topStripPaint)

        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dateAndTime = "${DateUtils.formatIndonesianFullDate(visitPage.dateEpochDay)} • ${DateUtils.formatTime(visitPage.visitHour, visitPage.visitMinute)} WIB"
        canvas.drawText("Kunjungan $visitIndex dari $totalVisits", MARGIN_H, 42f, badgePaint)

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 10.5f
        }
        canvas.drawText(dateAndTime, MARGIN_H, 58f, datePaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder.obtain(
            visitPage.locationName,
            0,
            visitPage.locationName.length,
            titlePaint,
            CONTENT_WIDTH.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1.0f)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(MARGIN_H, 74f)
        titleLayout.draw(canvas)
        canvas.restore()

        val dividerY = 74f + titleLayout.height + 12f
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN_H, dividerY, PAGE_WIDTH - MARGIN_H, dividerY, dividerPaint)

        val mediaStartY = dividerY + 16f
        val mediaAreaBottomY = drawVisitMediaGrid(
            context = context,
            canvas = canvas,
            mediaItems = visitPage.mediaItems,
            topY = mediaStartY
        )

        var descTopY = mediaAreaBottomY + 18f
        if (visitPage.mediaItems.size > MAX_MEDIA_PER_PAGE) {
            val extraCount = visitPage.mediaItems.size - MAX_MEDIA_PER_PAGE
            val extraPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText("+$extraCount media lainnya tersimpan di aplikasi", MARGIN_H, descTopY, extraPaint)
            descTopY += 18f
        }

        val descHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Deskripsi Perjalanan", MARGIN_H, descTopY, descHeaderPaint)

        val bodyTopY = descTopY + 14f
        val maxBodyHeight = (PAGE_HEIGHT - 54f - bodyTopY).coerceAtLeast(48f)
        val descText = visitPage.description.ifBlank {
            "Belum ada deskripsi perjalanan untuk kunjungan ini."
        }
        val descPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (visitPage.description.isBlank()) Color.parseColor("#64748B") else Color.parseColor("#1E293B")
            textSize = 11.5f
        }
        val approxMaxLines = (maxBodyHeight / 17f).toInt().coerceAtLeast(3)
        val descLayout = StaticLayout.Builder.obtain(
            descText,
            0,
            descText.length,
            descPaint,
            CONTENT_WIDTH.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3.5f, 1.0f)
            .setMaxLines(approxMaxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(MARGIN_H, bodyTopY)
        descLayout.draw(canvas)
        canvas.restore()

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 9.5f
        }
        canvas.drawText("TripBuddy • Halaman $pageNumber", MARGIN_H, PAGE_HEIGHT - 28f, footerPaint)

        pdfDocument.finishPage(page)
    }

    private fun drawVisitMediaGrid(
        context: Context,
        canvas: Canvas,
        mediaItems: List<Foto>,
        topY: Float
    ): Float {
        if (mediaItems.isEmpty()) {
            val boxRect = RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 150f)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(boxRect, 14f, 14f, fillPaint)
            canvas.drawRoundRect(boxRect, 14f, 14f, strokePaint)

            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 11.5f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "Tidak ada foto atau video untuk kunjungan ini",
                PAGE_WIDTH / 2f,
                topY + 80f,
                emptyPaint
            )
            return boxRect.bottom
        }

        val shown = mediaItems.take(MAX_MEDIA_PER_PAGE)
        val gap = 10f
        return when (shown.size) {
            1 -> {
                val rect = RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 270f)
                drawSingleMediaSlot(context, canvas, shown[0], rect)
                rect.bottom
            }
            2 -> {
                val cellWidth = (CONTENT_WIDTH - gap) / 2f
                val cellHeight = 220f
                val r1 = RectF(MARGIN_H, topY, MARGIN_H + cellWidth, topY + cellHeight)
                val r2 = RectF(r1.right + gap, topY, PAGE_WIDTH - MARGIN_H, topY + cellHeight)
                drawSingleMediaSlot(context, canvas, shown[0], r1)
                drawSingleMediaSlot(context, canvas, shown[1], r2)
                r1.bottom
            }
            else -> {
                val cellWidth = (CONTENT_WIDTH - gap) / 2f
                val cellHeight = 162f
                val rects = listOf(
                    RectF(MARGIN_H, topY, MARGIN_H + cellWidth, topY + cellHeight),
                    RectF(MARGIN_H + cellWidth + gap, topY, PAGE_WIDTH - MARGIN_H, topY + cellHeight),
                    RectF(MARGIN_H, topY + cellHeight + gap, MARGIN_H + cellWidth, topY + cellHeight * 2 + gap),
                    RectF(MARGIN_H + cellWidth + gap, topY + cellHeight + gap, PAGE_WIDTH - MARGIN_H, topY + cellHeight * 2 + gap)
                )
                shown.forEachIndexed { idx, media ->
                    drawSingleMediaSlot(context, canvas, media, rects[idx])
                }
                if (shown.size == 3) rects[2].bottom else rects[3].bottom
            }
        }
    }

    private fun drawSingleMediaSlot(
        context: Context,
        canvas: Canvas,
        media: Foto,
        rect: RectF
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0") }
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)

        val thumbPath = PhotoStorageHelper.resolveDisplayThumbnailPath(context, media)
        val bitmap = thumbPath?.let {
            decodeSampledRotatedBitmap(
                path = it,
                reqWidth = (rect.width() * 2).toInt().coerceAtLeast(320),
                reqHeight = (rect.height() * 2).toInt().coerceAtLeast(320)
            )
        }

        if (bitmap != null) {
            try {
                val clipPath = Path().apply {
                    addRoundRect(rect, 12f, 12f, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(clipPath)

                // Center-crop scale
                val scale = maxOf(
                    rect.width() / bitmap.width.toFloat(),
                    rect.height() / bitmap.height.toFloat()
                )
                val scaledW = bitmap.width * scale
                val scaledH = bitmap.height * scale
                val left = rect.centerX() - scaledW / 2f
                val top = rect.centerY() - scaledH / 2f
                val dst = RectF(left, top, left + scaledW, top + scaledH)
                val src = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            } finally {
                bitmap.recycle()
            }
        } else {
            val missingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
            val label = if (media.isVideo) "Thumbnail video tidak tersedia" else "Foto tidak ditemukan"
            canvas.drawText(label, rect.centerX(), rect.centerY(), missingPaint)
        }

        if (media.isVideo) {
            val badgeRect = RectF(rect.left + 8f, rect.bottom - 26f, rect.left + 98f, rect.bottom - 8f)
            val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 15, 23, 42)
            }
            canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBg)

            val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("▶ VIDEO", badgeRect.left + 8f, badgeRect.bottom - 5.5f, badgeTextPaint)
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
    }

    private fun decodeSampledRotatedBitmap(
        path: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var inSampleSize = 1
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.RGB_565
            }
            val raw = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

            val rotationDegrees = try {
                val exif = ExifInterface(path)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) {
                0f
            }

            if (rotationDegrees == 0f) {
                raw
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { rotated ->
                    if (rotated !== raw) raw.recycle()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCopyToDownloadsOrAppFiles(
        context: Context,
        sourceFile: File,
        fileName: String
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TripBuddy")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val copied = resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                        true
                    } ?: false
                    if (copied) {
                        val finishValues = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(uri, finishValues, null, null)
                        return "Tersimpan di folder Download/TripBuddy/$fileName"
                    } else {
                        resolver.delete(uri, null, null)
                    }
                }
            } catch (_: Exception) {
                // Fall back to app-private files directory
            }
        }

        val appExportDir = File(context.filesDir, PDF_EXPORT_DIR)
        if (!appExportDir.exists()) {
            appExportDir.mkdirs()
        }
        val destFile = File(appExportDir, fileName)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { out -> input.copyTo(out) }
        }
        return "Tersimpan di penyimpanan aplikasi ($fileName)"
    }
}
