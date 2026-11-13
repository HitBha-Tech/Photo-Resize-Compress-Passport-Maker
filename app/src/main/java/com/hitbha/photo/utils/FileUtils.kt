package com.hitbha.photo.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object FileUtils {

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[/\\\\:\\x00]"), "_")
            .replace(Regex("[\"*?<>|]"), "_")
            .trim('.',' ')
            .ifEmpty { "output" }
            .take(200)

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 95, fileName: String? = null): Uri? {
        val filename = sanitizeFileName(fileName ?: "PhotoResizeCompressPassport_${System.currentTimeMillis()}")
        val mimeType = when (format) {
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.PNG -> "image/png"
            else -> "image/jpeg"
        }

        val saveBitmap = if (format == Bitmap.CompressFormat.PNG) {
            ImageProcessor.ensureAlphaChannel(bitmap)
        } else {
            bitmap
        }

        return saveToMediaStore(context, saveBitmap, filename, mimeType, quality)
    }

    fun saveBitmapsToGallery(context: Context, bitmaps: List<Bitmap>, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 100, baseName: String? = null): List<Uri> {
        if (bitmaps.isEmpty()) return emptyList()
        val base = sanitizeFileName(baseName ?: "PhotoResizeCompressPassport_${System.currentTimeMillis()}")
        val mimeType = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            else -> "image/jpeg"
        }
        val results = mutableListOf<Uri>()
        for ((index, bitmap) in bitmaps.withIndex()) {
            val suffix = if (bitmaps.size > 1) "_${index + 1}" else ""
            val uri = saveToMediaStore(context, bitmap, "$base$suffix", mimeType, quality)
            if (uri != null) results.add(uri)
        }
        return results
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, filename: String, mimeType: String, quality: Int): Uri? {
        val extension = if (mimeType == "image/png") ".png" else ".jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename + extension)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hitbha/Photo")
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ).absolutePath + "/Hitbha/Photo/" + filename + extension)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
            outputStream?.use { stream ->
                bitmap.compress(
                    if (mimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    quality,
                    stream
                )
            }
        }
        return uri
    }

    fun getFileSize(bytes: ByteArray): Long = bytes.size.toLong()

    fun getFileSizeFromUri(context: Context, uri: Uri?): Long {
        if (uri == null) return 0L
        return try {
            val fdSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                ?: -1L
            if (fdSize > 0L) return fdSize
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> "%.2f MB".format(mb)
            kb >= 1 -> "%.2f KB".format(kb)
            else -> "$bytes bytes"
        }
    }

    fun savePdfToGallery(context: Context, bitmap: Bitmap, fileName: String? = null, dpi: Float = 72f, paperSize: ShareUtils.PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): Uri? =
        savePdfToGallery(context, listOf(bitmap), fileName, dpi, paperSize, bitmapDpi, quality, applyCompression)

    fun savePdfToGallery(context: Context, bitmaps: List<Bitmap>, fileName: String? = null, dpi: Float = 72f, paperSize: ShareUtils.PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): Uri? {
        if (bitmaps.isEmpty()) return null
        val pdfBytes = createPdfBytes(bitmaps, dpi, paperSize, bitmapDpi, quality, applyCompression)
        if (pdfBytes.isEmpty()) return null

        return try {
            val filename = sanitizeFileName(fileName ?: "PhotoResizeCompressPassport_${System.currentTimeMillis()}") + ".pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Hitbha")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(pdfBytes)
                    }
                }
                uri
            } else {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val photoResizeDir = File(documentsDir, "Hitbha")
                if (!photoResizeDir.exists()) photoResizeDir.mkdirs()
                val file = File(photoResizeDir, filename)
                FileOutputStream(file).use { stream ->
                    stream.write(pdfBytes)
                }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to save PDF", e)
            null
        }
    }

    fun createPdfBytes(bitmap: Bitmap, dpi: Float = 72f, paperSize: ShareUtils.PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): ByteArray =
        createPdfBytes(listOf(bitmap), dpi, paperSize, bitmapDpi, quality, applyCompression)

    fun createPdfBytes(bitmaps: List<Bitmap>, dpi: Float = 72f, paperSize: ShareUtils.PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): ByteArray {
        if (bitmaps.isEmpty()) return ByteArray(0)
        val document = PdfDocument()
        var result: ByteArray
        try {
            for (source in bitmaps) {
                val bmp = if (applyCompression) ImageProcessor.compressForPdf(source, quality) else ImageProcessor.scaleByQuality(source, quality)
                val (pageWidthPt, pageHeightPt) = if (paperSize != null && !paperSize.isCustom) {
                    Pair(paperSize.widthPt, paperSize.heightPt)
                } else {
                    Pair((bmp.width * 72 / dpi).toInt().coerceAtLeast(1), (bmp.height * 72 / dpi).toInt().coerceAtLeast(1))
                }
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(android.graphics.Color.WHITE)
                if (paperSize != null && !paperSize.isCustom) {
                    if (bitmapDpi != null) {
                        val renderW = (bmp.width * 72f / bitmapDpi).toInt().coerceAtLeast(1)
                        val renderH = (bmp.height * 72f / bitmapDpi).toInt().coerceAtLeast(1)
                        val left = (pageWidthPt - renderW) / 2
                        val top = (pageHeightPt - renderH) / 2
                        page.canvas.drawBitmap(bmp, null, android.graphics.Rect(left, top, left + renderW, top + renderH), null)
                    } else {
                        val marginPt = 36f
                        val maxWidthPt = pageWidthPt - 2 * marginPt
                        val maxHeightPt = pageHeightPt - 2 * marginPt
                        val scale = (maxWidthPt / bmp.width.toFloat()).coerceAtMost(maxHeightPt / bmp.height.toFloat()).coerceAtMost(1f)
                        val drawWidth = (bmp.width * scale).toInt()
                        val drawHeight = (bmp.height * scale).toInt()
                        val left = (pageWidthPt - drawWidth) / 2
                        val top = (pageHeightPt - drawHeight) / 2
                        page.canvas.drawBitmap(bmp, null, android.graphics.Rect(left, top, left + drawWidth, top + drawHeight), null)
                    }
                } else {
                    page.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, pageWidthPt, pageHeightPt), null)
                }
                document.finishPage(page)
                if (bmp !== source) bmp.recycle()
            }
            val out = java.io.ByteArrayOutputStream()
            document.writeTo(out)
            result = out.toByteArray()
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to create PDF bytes", e)
            result = ByteArray(0)
        } finally {
            document.close()
        }
        return result
    }
}