package com.hitbha.photo.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ShareUtils {

    enum class Format { JPEG, PNG, PDF }

    enum class PaperSize(val widthPt: Int, val heightPt: Int) {
        A4(595, 842),
        PHOTO_4X6(288, 432),
        LETTER(612, 792),
        A5(420, 595),
        A3(842, 1191),
        CUSTOM(0, 0);

        val isCustom: Boolean get() = this == CUSTOM
    }

    fun createShareFile(context: Context, bitmap: Bitmap, format: Format, prefix: String, fileName: String? = null, dpi: Float = 72f, paperSize: PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): Uri? =
        createShareFile(context, listOf(bitmap), format, prefix, fileName, dpi, paperSize, bitmapDpi, quality, applyCompression)

    fun createShareFiles(context: Context, bitmaps: List<Bitmap>, format: Format, prefix: String, fileName: String? = null, dpi: Float = 72f, paperSize: PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): List<Uri> {
        return try {
            if (bitmaps.isEmpty()) return emptyList()
            val cacheDir = File(context.cacheDir, "share")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            when (format) {
                Format.JPEG -> {
                    val uris = mutableListOf<Uri>()
                    for ((index, bitmap) in bitmaps.withIndex()) {
                        val suffix = if (bitmaps.size > 1) "_${index + 1}" else ""
                        val name = if (fileName != null) "$fileName$suffix.jpg" else "${prefix}_share_${System.currentTimeMillis()}_${index + 1}.jpg"
                        val file = File(cacheDir, name)
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
                        }
                        uris.add(context.getFileUri(file))
                    }
                    uris
                }
                Format.PNG -> {
                    val uris = mutableListOf<Uri>()
                    for ((index, bitmap) in bitmaps.withIndex()) {
                        val suffix = if (bitmaps.size > 1) "_${index + 1}" else ""
                        val name = if (fileName != null) "$fileName$suffix.png" else "${prefix}_share_${System.currentTimeMillis()}_${index + 1}.png"
                        val file = File(cacheDir, name)
                        val shareBitmap = ImageProcessor.ensureAlphaChannel(bitmap)
                        FileOutputStream(file).use { out ->
                            shareBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        if (shareBitmap != bitmap) shareBitmap.recycle()
                        uris.add(context.getFileUri(file))
                    }
                    uris
                }
                Format.PDF -> {
                    val pdfBytes = FileUtils.createPdfBytes(bitmaps, dpi, paperSize, bitmapDpi, quality, applyCompression)
                    if (pdfBytes.isEmpty()) return emptyList()
                    val name = if (fileName != null) "$fileName.pdf" else "${prefix}_share_${System.currentTimeMillis()}.pdf"
                    val file = File(cacheDir, name)
                    FileOutputStream(file).use { stream -> stream.write(pdfBytes) }
                    listOf(context.getFileUri(file))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShareUtils", "Failed to create share file", e)
            emptyList()
        }
    }

    fun createShareFile(context: Context, bitmaps: List<Bitmap>, format: Format, prefix: String, fileName: String? = null, dpi: Float = 72f, paperSize: PaperSize? = null, bitmapDpi: Float? = null, quality: Int = 100, applyCompression: Boolean = false): Uri? =
        createShareFiles(context, bitmaps, format, prefix, fileName, dpi, paperSize, bitmapDpi, quality, applyCompression).firstOrNull()
}
