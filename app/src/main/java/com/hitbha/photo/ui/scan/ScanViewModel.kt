package com.hitbha.photo.ui.scan

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hitbha.photo.utils.FileUtils
import com.hitbha.photo.utils.OfficeUtils
import com.hitbha.photo.utils.ShareUtils
import com.hitbha.photo.utils.TableExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OutputFormat { PDF, ID_SCAN, DOCUMENT, EXCEL }

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    data class CapturedMedia(val uri: Uri, val isVideo: Boolean, val dateAdded: Long)

    private val _pageBitmaps = mutableListOf<Bitmap>()
    private val _pagesLiveData = MutableLiveData<List<Bitmap>>(emptyList())
    val pagesLiveData: LiveData<List<Bitmap>> = _pagesLiveData

    private val _selectedFormat = MutableLiveData(OutputFormat.PDF)
    val selectedFormat: LiveData<OutputFormat> = _selectedFormat

    private val _processing = MutableLiveData(false)
    val processing: LiveData<Boolean> = _processing

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _saveResult = MutableLiveData<Pair<Int, Int>?>()
    val saveResult: LiveData<Pair<Int, Int>?> = _saveResult

    private val _shareData = MutableLiveData<ShareData?>()
    val shareData: LiveData<ShareData?> = _shareData

    data class ShareData(val uri: Uri?, val mimeType: String, val failRes: Int)

    var pendingCameraUri: Uri? = null
    var captureMarkerTime = 0L
    var captureMarkerDateAdded = 0L

    val pageBitmaps: List<Bitmap> get() = _pageBitmaps.toList()

    fun clearToastMessage() { _toastMessage.value = null }
    fun clearSaveResult() { _saveResult.value = null }
    fun clearShareData() { _shareData.value = null }

    fun setFormat(format: OutputFormat) {
        _selectedFormat.value = format
        if (format == OutputFormat.ID_SCAN && _pageBitmaps.size > 2) {
            _pageBitmaps.subList(0, _pageBitmaps.size - 2).clear()
            _pagesLiveData.value = _pageBitmaps.toList()
        }
    }

    fun addPage(bitmap: Bitmap) {
        val format = _selectedFormat.value ?: OutputFormat.PDF
        if (format == OutputFormat.ID_SCAN && _pageBitmaps.size >= 2) {
            _pageBitmaps.removeAt(0)
        }
        _pageBitmaps.add(bitmap)
        _pagesLiveData.value = _pageBitmaps.toList()
    }

    fun removePage(index: Int) {
        if (index in _pageBitmaps.indices) {
            _pageBitmaps.removeAt(index)
            _pagesLiveData.value = _pageBitmaps.toList()
        }
    }

    fun hasPages(): Boolean = _pageBitmaps.isNotEmpty()

    fun buildPdfPagePreview(bitmap: Bitmap): Bitmap {
        val pageWidthPt = ShareUtils.PaperSize.A4.widthPt
        val pageHeightPt = ShareUtils.PaperSize.A4.heightPt
        val sf = 2f
        val renderW = (pageWidthPt * sf).toInt()
        val renderH = (pageHeightPt * sf).toInt()
        val out = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val margin = 36f * sf
        val maxW = renderW - 2 * margin
        val maxH = renderH - 2 * margin
        val scale = (maxW / bitmap.width.toFloat())
            .coerceAtMost(maxH / bitmap.height.toFloat())
            .coerceAtMost(1f)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val left = (renderW - drawW) / 2f
        val top = (renderH - drawH) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, null, Rect(left.toInt(), top.toInt(), (left + drawW).toInt(), (top + drawH).toInt()), paint)
        return out
    }

    data class IdPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

    fun idCardPlacements(bitmaps: List<Bitmap>, pageW: Int, pageH: Int): List<IdPlacement> {
        val margin = 36f
        val gap = 20f
        val availW = pageW - 2 * margin
        val availH = pageH - 2 * margin
        if (bitmaps.size <= 1) {
            val bmp = bitmaps.first()
            val scale = (availW / bmp.width.toFloat()).coerceAtMost(availH / bmp.height.toFloat()).coerceAtMost(1f)
            val w = bmp.width * scale; val h = bmp.height * scale
            return listOf(IdPlacement((pageW - w) / 2f, (pageH - h) / 2f, w, h))
        }
        val halfH = (availH - gap) / 2f
        val result = mutableListOf<IdPlacement>()
        var y = margin
        for (bmp in bitmaps.take(2)) {
            val scale = (availW / bmp.width.toFloat()).coerceAtMost(halfH / bmp.height.toFloat()).coerceAtMost(1f)
            val w = bmp.width * scale; val h = bmp.height * scale
            result.add(IdPlacement((pageW - w) / 2f, y, w, h))
            y += h + gap
        }
        return result
    }

    fun buildIdCardPagePreview(bitmaps: List<Bitmap>): Bitmap {
        val pageWidthPt = ShareUtils.PaperSize.A4.widthPt
        val pageHeightPt = ShareUtils.PaperSize.A4.heightPt
        val sf = 2f
        val renderW = (pageWidthPt * sf).toInt()
        val renderH = (pageHeightPt * sf).toInt()
        val out = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        idCardPlacements(bitmaps, pageWidthPt, pageHeightPt).forEachIndexed { index, p ->
            canvas.drawBitmap(bitmaps[index], null,
                Rect((p.left * sf).toInt(), (p.top * sf).toInt(), ((p.left + p.width) * sf).toInt(), ((p.top + p.height) * sf).toInt()), paint)
        }
        return out
    }

    fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            val ctx = getApplication<Application>()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > 1080 && sampleSize < 32) { sampleSize *= 2 }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }) }
        } catch (t: Throwable) { null }
    }

    fun extractVideoFrame(uri: Uri): Bitmap? {
        val ctx = getApplication<Application>()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ctx, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) { null } finally { runCatching { retriever.release() } }
    }

    suspend fun findNewlyCapturedMedia(): List<CapturedMedia> {
        val ctx = getApplication<Application>()
        repeat(4) {
            val media = queryNewestIn(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false) +
                queryNewestIn(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            if (media.isNotEmpty()) return media
            delay(700)
        }
        return emptyList()
    }

    private fun queryNewestIn(ctx: Application, collection: Uri, isVideo: Boolean): List<CapturedMedia> {
        val seen = HashSet<Uri>()
        val result = mutableListOf<CapturedMedia>()
        val byTaken = queryMediaSince(ctx, collection, isVideo, MediaStore.MediaColumns.DATE_TAKEN, captureMarkerTime)
        val byAdded = queryMediaSince(ctx, collection, isVideo, MediaStore.MediaColumns.DATE_ADDED, captureMarkerDateAdded)
        (byTaken + byAdded).forEach { media -> if (seen.add(media.uri)) result.add(media) }
        return result.sortedBy { it.dateAdded }
    }

    private fun queryMediaSince(ctx: Application, collection: Uri, isVideo: Boolean, column: String, value: Long): List<CapturedMedia> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val result = mutableListOf<CapturedMedia>()
        ctx.contentResolver.query(collection, projection, "$column >= ?", arrayOf(value.toString()),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                result.add(CapturedMedia(ContentUris.withAppendedId(collection, cursor.getLong(idCol)), isVideo, cursor.getLong(dateCol)))
            }
        }
        return result
    }

    fun queryMaxMediaDateAdded(): Long {
        val ctx = getApplication<Application>()
        var max = 0L
        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).forEach { collection ->
            ctx.contentResolver.query(collection, arrayOf("MAX(${MediaStore.MediaColumns.DATE_ADDED})"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) { val value = cursor.getLong(0); if (value > max) max = value }
            }
        }
        return max
    }

    fun saveOutput() {
        if (_pageBitmaps.isEmpty()) return
        _processing.value = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val format = _selectedFormat.value ?: OutputFormat.PDF
            val (uri, successRes, failRes) = when (format) {
                OutputFormat.PDF -> {
                    val uri = withContext(Dispatchers.IO) { FileUtils.savePdfToGallery(ctx, _pageBitmaps.toList(), paperSize = ShareUtils.PaperSize.A4) }
                    Triple(uri, com.hitbha.photo.R.string.pdf_saved, com.hitbha.photo.R.string.failed_pdf)
                }
                OutputFormat.ID_SCAN -> {
                    val preview = withContext(Dispatchers.IO) { buildIdCardPagePreview(_pageBitmaps) }
                    val uri = withContext(Dispatchers.IO) { FileUtils.savePdfToGallery(ctx, listOf(preview),
                        paperSize = ShareUtils.PaperSize.A4, bitmapDpi = 144f) }
                    Triple(uri, com.hitbha.photo.R.string.pdf_saved, com.hitbha.photo.R.string.failed_pdf)
                }
                OutputFormat.DOCUMENT -> {
                    val uri = withContext(Dispatchers.IO) { OfficeUtils.saveDocxToGallery(ctx, _pageBitmaps.toList()) }
                    Triple(uri, com.hitbha.photo.R.string.docx_saved, com.hitbha.photo.R.string.failed_docx)
                }
                OutputFormat.EXCEL -> saveExcelOutput()
            }
            _processing.value = false
            _saveResult.value = Pair(successRes, failRes)
        }
    }

    private suspend fun saveExcelOutput(): Triple<Uri?, Int, Int> {
        val ctx = getApplication<Application>()
        return try {
            val tables = withContext(Dispatchers.IO) { TableExtractor.extractTables(_pageBitmaps.toList()) }
            val uri = withContext(Dispatchers.IO) { OfficeUtils.saveXlsxPagesToGallery(ctx, _pageBitmaps.toList(), tables) }
            Triple(uri, com.hitbha.photo.R.string.xlsx_saved, com.hitbha.photo.R.string.failed_xlsx)
        } catch (e: Exception) {
            val uri = withContext(Dispatchers.IO) { OfficeUtils.saveXlsxPagesToGallery(ctx, _pageBitmaps.toList(), emptyList()) }
            Triple(uri, com.hitbha.photo.R.string.xlsx_saved, com.hitbha.photo.R.string.failed_xlsx)
        }
    }

    fun shareOutput() {
        if (_pageBitmaps.isEmpty()) return
        _processing.value = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val format = _selectedFormat.value ?: OutputFormat.PDF
            val (fileUri, mimeType, failRes) = withContext(Dispatchers.IO) {
                when (format) {
                    OutputFormat.PDF -> {
                        val uri = ShareUtils.createShareFile(ctx, _pageBitmaps.toList(), ShareUtils.Format.PDF, "scan", paperSize = ShareUtils.PaperSize.A4)
                        Triple(uri, "application/pdf", com.hitbha.photo.R.string.failed_share_pdf)
                    }
                    OutputFormat.ID_SCAN -> {
                        val uri = ShareUtils.createShareFile(ctx, listOf(buildIdCardPagePreview(_pageBitmaps)),
                            ShareUtils.Format.PDF, "id_scan", paperSize = ShareUtils.PaperSize.A4, bitmapDpi = 144f)
                        Triple(uri, "application/pdf", com.hitbha.photo.R.string.failed_share_pdf)
                    }
                    OutputFormat.DOCUMENT -> {
                        val uri = OfficeUtils.createDocxCacheFile(ctx, _pageBitmaps.toList())
                        Triple(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", com.hitbha.photo.R.string.failed_docx)
                    }
                    OutputFormat.EXCEL -> {
                        val tables = TableExtractor.extractTables(_pageBitmaps.toList())
                        val uri = OfficeUtils.createXlsxPagesCacheFile(ctx, _pageBitmaps.toList(), tables)
                        Triple(uri, XLSX_MIME, com.hitbha.photo.R.string.failed_xlsx)
                    }
                }
            }
            _processing.value = false
            _shareData.value = ShareData(fileUri, mimeType, failRes)
        }
    }

    companion object {
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
