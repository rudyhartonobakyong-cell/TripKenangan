package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
import com.example.data.Trip
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class PdfPageSize(
    val label: String,
    val description: String,
    val widthMm: Float,
    val heightMm: Float
) {
    A4_PORTRAIT(
        label = "A4 Portrait",
        description = "210 × 297 mm",
        widthMm = 210f,
        heightMm = 297f
    ),
    SQUARE_20X20(
        label = "Persegi 20×20 cm",
        description = "200 × 200 mm",
        widthMm = 200f,
        heightMm = 200f
    );

    val widthPt: Int get() = (widthMm * PdfExporter.MM_TO_PT).roundToInt()
    val heightPt: Int get() = (heightMm * PdfExporter.MM_TO_PT).roundToInt()
}

enum class PdfGridLayout(
    val label: String,
    val columns: Int,
    val rows: Int
) {
    GRID_2X3(label = "2 × 3", columns = 2, rows = 3),
    GRID_2X2(label = "2 × 2", columns = 2, rows = 2),
    GRID_3X3(label = "3 × 3", columns = 3, rows = 3);

    val photosPerPage: Int get() = columns * rows
}

enum class PdfExportScope(val label: String) {
    THIS_VISIT("Kunjungan ini"),
    ALL("Semua"),
    PERSONAL_ONLY("Pribadi saja"),
    SPECIFIC_GROUP("Grup tertentu")
}

data class PdfExportOptions(
    val scope: PdfExportScope = PdfExportScope.ALL,
    val selectedVisitId: String? = null,
    val selectedGroupUuid: String? = null,
    val useDateRange: Boolean = false,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val gridLayout: PdfGridLayout = PdfGridLayout.GRID_2X3,
    val pageSize: PdfPageSize = PdfPageSize.A4_PORTRAIT,
    val printTimestamp: Boolean = true,
    val includeCover: Boolean = false
)

data class PdfAlbumPhoto(
    val foto: Foto,
    val takenAtMillis: Long
)

data class PdfVisitPhotoSection(
    val kunjungan: Kunjungan,
    val photos: List<PdfAlbumPhoto>,
    val photoPages: List<List<PdfAlbumPhoto>>
)

data class PdfPhotoAlbumSpec(
    val options: PdfExportOptions,
    val coverTitle: String,
    val coverDateLabel: String,
    val suggestedFileName: String,
    val visitSections: List<PdfVisitPhotoSection>,
    val totalPhotos: Int,
    val totalPages: Int
)

data class PdfExportProgress(
    val processedPhotos: Int,
    val totalPhotos: Int,
    val currentPage: Int
)

data class ExportedPdfResult(
    val cacheFile: File,
    val contentUri: Uri,
    val fileName: String,
    val savedLocationNote: String,
    val pageCount: Int,
    val photoCount: Int
)

data class PdfCellBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class PdfGridMetrics(
    val pageWidthPt: Int,
    val pageHeightPt: Int,
    val marginPt: Float,
    val gapPt: Float,
    val cellSizePt: Float,
    val startXPt: Float,
    val startYPt: Float
) {
    fun cellBounds(indexOnPage: Int, columns: Int): PdfCellBounds {
        val col = indexOnPage % columns
        val row = indexOnPage / columns
        val left = startXPt + col * (cellSizePt + gapPt)
        val top = startYPt + row * (cellSizePt + gapPt)
        return PdfCellBounds(left = left, top = top, right = left + cellSizePt, bottom = top + cellSizePt)
    }

    fun cellRect(indexOnPage: Int, columns: Int): RectF {
        return cellBounds(indexOnPage, columns).toRectF()
    }
}

object PdfExporter {

    const val MM_TO_PT = 72f / 25.4f
    const val MARGIN_MM = 12f
    const val PHOTO_GAP_MM = 6f
    const val MIN_SQUARE_PX = 1200
    const val MAX_SQUARE_PX = 2000
    const val JPEG_QUALITY = 90

    private const val PDF_EXPORT_DIR = "pdf_exports"

    fun computeGridMetrics(pageSize: PdfPageSize, grid: PdfGridLayout): PdfGridMetrics {
        val pageW = pageSize.widthPt
        val pageH = pageSize.heightPt
        val marginPt = MARGIN_MM * MM_TO_PT
        val gapPt = PHOTO_GAP_MM * MM_TO_PT

        val contentW = pageW - (2f * marginPt)
        val contentH = pageH - (2f * marginPt)
        val maxCellW = (contentW - gapPt * (grid.columns - 1)) / grid.columns.toFloat()
        val maxCellH = (contentH - gapPt * (grid.rows - 1)) / grid.rows.toFloat()
        val cellSizePt = minOf(maxCellW, maxCellH).coerceAtLeast(10f)

        val totalGridW = cellSizePt * grid.columns + gapPt * (grid.columns - 1)
        val totalGridH = cellSizePt * grid.rows + gapPt * (grid.rows - 1)
        val startXPt = marginPt + (contentW - totalGridW) / 2f
        val startYPt = marginPt + (contentH - totalGridH) / 2f

        return PdfGridMetrics(
            pageWidthPt = pageW,
            pageHeightPt = pageH,
            marginPt = marginPt,
            gapPt = gapPt,
            cellSizePt = cellSizePt,
            startXPt = startXPt,
            startYPt = startYPt
        )
    }

    fun buildPhotoAlbumSpec(
        options: PdfExportOptions,
        allTrips: List<Trip>,
        allKunjungan: List<Kunjungan>,
        allFotos: List<Foto>,
        resolveTakenTimeMillis: (Foto) -> Long = { resolvePhotoTakenMillis(it) }
    ): PdfPhotoAlbumSpec {
        val tripById = allTrips.associateBy { it.id }

        val scopeFilteredVisits = allKunjungan.filter { kunjungan ->
            when (options.scope) {
                PdfExportScope.THIS_VISIT -> kunjungan.id == options.selectedVisitId
                PdfExportScope.ALL -> true
                PdfExportScope.PERSONAL_ONLY -> kunjungan.tripId == Trip.PERSONAL_TRIP_ID
                PdfExportScope.SPECIFIC_GROUP -> {
                    options.selectedGroupUuid != null && kunjungan.tripId == options.selectedGroupUuid
                }
            }
        }

        val normalizedRange = if (options.useDateRange) {
            val rawStart = options.startEpochDay
            val rawEnd = options.endEpochDay
            if (rawStart != null && rawEnd != null) {
                minOf(rawStart, rawEnd) to maxOf(rawStart, rawEnd)
            } else {
                rawStart to rawEnd
            }
        } else {
            null to null
        }

        val dateFilteredVisits = scopeFilteredVisits.filter { kunjungan ->
            if (!options.useDateRange) {
                true
            } else {
                val afterStart = normalizedRange.first == null || kunjungan.visitDateEpochDay >= normalizedRange.first!!
                val beforeEnd = normalizedRange.second == null || kunjungan.visitDateEpochDay <= normalizedRange.second!!
                afterStart && beforeEnd
            }
        }

        val sortedVisits = dateFilteredVisits.sortedWith(
            compareBy<Kunjungan> { it.visitDateEpochDay }
                .thenBy { it.visitHour }
                .thenBy { it.visitMinute }
                .thenBy { it.createdAtMillis }
        )

        // Exclude videos completely; only include photos grouped by kunjunganId
        val photosByVisitId = allFotos
            .filter { !it.isVideo }
            .groupBy { it.kunjunganId }

        val perPage = options.gridLayout.photosPerPage
        val visitSections = sortedVisits.mapNotNull { visit ->
            val rawPhotos = photosByVisitId[visit.id].orEmpty()
            if (rawPhotos.isEmpty()) return@mapNotNull null

            val albumPhotos = rawPhotos
                .map { foto -> PdfAlbumPhoto(foto = foto, takenAtMillis = resolveTakenTimeMillis(foto)) }
                .sortedWith(
                    compareBy<PdfAlbumPhoto> { it.takenAtMillis }
                        .thenBy { it.foto.createdAtMillis }
                        .thenBy { it.foto.id }
                )

            val chunks = albumPhotos.chunked(perPage)
            PdfVisitPhotoSection(
                kunjungan = visit,
                photos = albumPhotos,
                photoPages = chunks
            )
        }

        val totalPhotos = visitSections.sumOf { it.photos.size }
        val photoPageCount = visitSections.sumOf { it.photoPages.size }
        val totalPages = if (totalPhotos == 0) {
            0
        } else {
            photoPageCount + if (options.includeCover) 1 else 0
        }

        val distinctDays = visitSections.map { it.kunjungan.visitDateEpochDay }.distinct()
        val coverDateLabel = when {
            distinctDays.isEmpty() -> ""
            distinctDays.size == 1 -> DateUtils.formatIndonesianFullDate(distinctDays.first())
            else -> "${DateUtils.formatIndonesianShortDate(distinctDays.min())} – ${DateUtils.formatIndonesianShortDate(distinctDays.max())}"
        }

        val coverTitle = when (options.scope) {
            PdfExportScope.THIS_VISIT -> {
                visitSections.firstOrNull()?.kunjungan?.locationName
                    ?: allKunjungan.find { it.id == options.selectedVisitId }?.locationName
                    ?: "Album Foto"
            }
            PdfExportScope.ALL -> "Album Foto TripBuddy"
            PdfExportScope.PERSONAL_ONLY -> "Album Foto Pribadi"
            PdfExportScope.SPECIFIC_GROUP -> {
                tripById[options.selectedGroupUuid]?.name ?: "Album Foto Grup"
            }
        }

        val fileSlug = when (options.scope) {
            PdfExportScope.THIS_VISIT -> "Album_${coverTitle}"
            PdfExportScope.ALL -> "Album_Semua"
            PdfExportScope.PERSONAL_ONLY -> "Album_Pribadi"
            PdfExportScope.SPECIFIC_GROUP -> "Album_${coverTitle}"
        }

        return PdfPhotoAlbumSpec(
            options = options,
            coverTitle = coverTitle,
            coverDateLabel = coverDateLabel,
            suggestedFileName = buildPdfFileName(fileSlug),
            visitSections = visitSections,
            totalPhotos = totalPhotos,
            totalPages = totalPages
        )
    }

    fun buildPdfFileName(rawTitle: String, timestampMillis: Long = System.currentTimeMillis()): String {
        val slug = rawTitle
            .trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "TripBuddy_Album" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestampMillis))
        return "TripBuddy_${slug}_$stamp.pdf"
    }

    fun exportPhotoAlbumToPdf(
        context: Context,
        spec: PdfPhotoAlbumSpec,
        onProgress: (PdfExportProgress) -> Unit = {}
    ): Result<ExportedPdfResult> = runCatching {
        if (spec.totalPhotos == 0 || spec.visitSections.isEmpty()) {
            throw IllegalStateException("Tidak ada foto dalam cakupan yang dipilih.")
        }

        val metrics = computeGridMetrics(spec.options.pageSize, spec.options.gridLayout)
        val pdfDocument = PdfDocument()
        try {
            var pageNumber = 0
            var processedPhotos = 0

            if (spec.options.includeCover) {
                pageNumber += 1
                drawMinimalCoverPage(
                    pdfDocument = pdfDocument,
                    pageNumber = pageNumber,
                    metrics = metrics,
                    spec = spec
                )
            }

            onProgress(
                PdfExportProgress(
                    processedPhotos = 0,
                    totalPhotos = spec.totalPhotos,
                    currentPage = pageNumber.coerceAtLeast(1)
                )
            )

            spec.visitSections.forEach { section ->
                section.photoPages.forEach { pagePhotos ->
                    pageNumber += 1
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        metrics.pageWidthPt,
                        metrics.pageHeightPt,
                        pageNumber
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    try {
                        val canvas = page.canvas
                        canvas.drawColor(Color.WHITE)

                        pagePhotos.forEachIndexed { slotIndex, albumPhoto ->
                            val cellRect = metrics.cellRect(slotIndex, spec.options.gridLayout.columns)
                            drawSquarePhotoSlot(
                                canvas = canvas,
                                albumPhoto = albumPhoto,
                                cellRect = cellRect,
                                printTimestamp = spec.options.printTimestamp
                            )
                            processedPhotos += 1
                            onProgress(
                                PdfExportProgress(
                                    processedPhotos = processedPhotos,
                                    totalPhotos = spec.totalPhotos,
                                    currentPage = pageNumber
                                )
                            )
                        }
                    } finally {
                        pdfDocument.finishPage(page)
                    }
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
                pageCount = pageNumber,
                photoCount = spec.totalPhotos
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

    private fun drawMinimalCoverPage(
        pdfDocument: PdfDocument,
        pageNumber: Int,
        metrics: PdfGridMetrics,
        spec: PdfPhotoAlbumSpec
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(
            metrics.pageWidthPt,
            metrics.pageHeightPt,
            pageNumber
        ).create()
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val contentW = (metrics.pageWidthPt - 2f * metrics.marginPt).toInt().coerceAtLeast(100)
            val centerY = metrics.pageHeightPt * 0.44f

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titleLayout = StaticLayout.Builder.obtain(
                spec.coverTitle,
                0,
                spec.coverTitle.length,
                titlePaint,
                contentW
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(4f, 1.0f)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            val titleTop = centerY - titleLayout.height / 2f
            canvas.save()
            canvas.translate(metrics.marginPt, titleTop)
            titleLayout.draw(canvas)
            canvas.restore()

            val ruleY = titleTop + titleLayout.height + 16f
            val ruleHalf = 36f
            val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1.5f
            }
            canvas.drawLine(
                metrics.pageWidthPt / 2f - ruleHalf,
                ruleY,
                metrics.pageWidthPt / 2f + ruleHalf,
                ruleY,
                rulePaint
            )

            var nextY = ruleY + 24f
            if (spec.coverDateLabel.isNotBlank()) {
                val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#475569")
                    textSize = 12.5f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(spec.coverDateLabel, metrics.pageWidthPt / 2f, nextY, datePaint)
                nextY += 18f
            }

            val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 11f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "${spec.totalPhotos} Foto • ${spec.visitSections.size} Kunjungan",
                metrics.pageWidthPt / 2f,
                nextY,
                countPaint
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawSquarePhotoSlot(
        canvas: Canvas,
        albumPhoto: PdfAlbumPhoto,
        cellRect: RectF,
        printTimestamp: Boolean
    ) {
        val bitmap = decodeHighResSquareJpegBitmap(albumPhoto.foto.filePath)
        if (bitmap != null) {
            try {
                val src = Rect(0, 0, bitmap.width, bitmap.height)
                val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bitmap, src, cellRect, bitmapPaint)
            } finally {
                bitmap.recycle()
            }
        } else {
            val missingPaint = Paint().apply {
                color = Color.parseColor("#F1F5F9")
            }
            canvas.drawRect(cellRect, missingPaint)
        }

        if (printTimestamp) {
            drawPhotoTimestamp(
                canvas = canvas,
                cellRect = cellRect,
                timestampText = DateUtils.formatPhotoTimestamp(albumPhoto.takenAtMillis)
            )
        }
    }

    private fun drawPhotoTimestamp(
        canvas: Canvas,
        cellRect: RectF,
        timestampText: String
    ) {
        val textSize = (cellRect.width() * 0.035f).coerceIn(6.2f, 9.2f)
        val pad = (cellRect.width() * 0.032f).coerceIn(5f, 9f)
        val x = cellRect.right - pad
        val y = cellRect.bottom - pad

        val shadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 0, 0, 0)
            this.textSize = textSize
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(timestampText, x + 0.7f, y + 0.7f, shadowPaint)
        canvas.drawText(timestampText, x - 0.35f, y + 0.45f, shadowPaint)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setShadowLayer(1.8f, 0.5f, 0.5f, Color.argb(190, 0, 0, 0))
        }
        canvas.drawText(timestampText, x, y, textPaint)
    }

    /**
     * Decodes original photo at high resolution, centers-crops 1:1 without distortion,
     * scales between [MIN_SQUARE_PX] (1200 px) and [MAX_SQUARE_PX] (2000 px) per side,
     * compresses to JPEG quality 90, and immediately recycles intermediate bitmaps.
     */
    private fun decodeHighResSquareJpegBitmap(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        var raw: Bitmap? = null
        var square: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            if (origW <= 0 || origH <= 0) return null

            val minOrig = minOf(origW, origH)
            val maxOrig = maxOf(origW, origH)
            var inSampleSize = 1
            while ((minOrig / (inSampleSize * 2)) >= MIN_SQUARE_PX) {
                inSampleSize *= 2
            }
            if ((maxOrig / inSampleSize) > 4500 && (minOrig / (inSampleSize * 2)) >= MIN_SQUARE_PX) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            raw = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

            val exifOrientation = try {
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val cropSide = minOf(raw.width, raw.height).coerceAtLeast(1)
            val cropX = ((raw.width - cropSide) / 2).coerceAtLeast(0)
            val cropY = ((raw.height - cropSide) / 2).coerceAtLeast(0)
            val targetSide = cropSide.coerceIn(MIN_SQUARE_PX, MAX_SQUARE_PX)
            val scale = targetSide.toFloat() / cropSide.toFloat()

            val matrix = exifMatrixForOrientation(exifOrientation).apply {
                postScale(scale, scale)
            }

            square = Bitmap.createBitmap(raw, cropX, cropY, cropSide, cropSide, matrix, true)
            if (square !== raw) {
                raw.recycle()
                raw = null
            }

            val jpegStream = ByteArrayOutputStream()
            square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpegStream)
            val jpegBytes = jpegStream.toByteArray()
            square.recycle()
            square = null

            val finalOpts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, finalOpts)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } finally {
            if (square != null && !square.isRecycled) {
                square.recycle()
            }
            if (raw != null && !raw.isRecycled) {
                raw.recycle()
            }
        }
    }

    private fun exifMatrixForOrientation(orientation: Int): Matrix {
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
    }

    fun resolvePhotoTakenMillis(foto: Foto): Long {
        val file = File(foto.filePath)
        if (file.exists() && file.length() > 0L) {
            val exifMillis = readExifDateTimeMillis(file.absolutePath)
            if (exifMillis != null && exifMillis > 0L) return exifMillis
        }
        if (foto.createdAtMillis > 0L) return foto.createdAtMillis
        val lastMod = if (file.exists()) file.lastModified() else 0L
        return if (lastMod > 0L) lastMod else System.currentTimeMillis()
    }

    private fun readExifDateTimeMillis(path: String): Long? {
        return try {
            val exif = ExifInterface(path)
            val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return null
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            parser.parse(rawDate.trim())?.time
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
