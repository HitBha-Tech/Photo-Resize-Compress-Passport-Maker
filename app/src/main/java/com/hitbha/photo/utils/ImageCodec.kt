package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

fun compressBitmap(bitmap: Bitmap, quality: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(format, quality, stream)
    return stream.toByteArray()
}

fun compressForPdf(bitmap: Bitmap, quality: Int): Bitmap {
    val q = quality.coerceIn(1, 100)
    val config = when {
        q >= 91 -> Bitmap.Config.ARGB_8888
        q >= 70 -> Bitmap.Config.RGB_565
		else    -> Bitmap.Config.ARGB_4444
    }
    return bitmap.copy(config, false)
}

fun scaleByQuality(bitmap: Bitmap, quality: Int): Bitmap {
    val q = quality.coerceIn(1, 100)
    if (q >= 100) return bitmap
    val scale = 0.70f * kotlin.math.sqrt(q / 100f)
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return bitmap.scale(w, h, true)
}

fun encodePng(bitmap: Bitmap, quality: Int): ByteArray {
    val q = quality.coerceIn(1, 100)
    val src = ensureAlphaChannel(bitmap)
    val scale = 0.70f * kotlin.math.sqrt(q / 100f)
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val toEncode = if (w < src.width || h < src.height) src.scale(w, h, true) else src
    val stream = ByteArrayOutputStream()
    toEncode.compress(Bitmap.CompressFormat.PNG, 100, stream)
    if (toEncode !== src) toEncode.recycle()
    if (src !== bitmap) src.recycle()
    return stream.toByteArray()
}
