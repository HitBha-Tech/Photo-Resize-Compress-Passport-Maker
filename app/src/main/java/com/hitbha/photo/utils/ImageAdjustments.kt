package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.pow

fun adjustBrightness(bitmap: Bitmap, value: Int): Bitmap {
    val paint = Paint().apply {
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, value.toFloat(),
                0f, 1f, 0f, 0f, value.toFloat(),
                0f, 0f, 1f, 0f, value.toFloat(),
                0f, 0f, 0f, 1f, 0f
            ))
        }
        colorFilter = ColorMatrixColorFilter(cm)
    }
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, safeConfig(bitmap))
    Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

fun adjustContrast(bitmap: Bitmap, value: Float): Bitmap {
    val scale = value + 1f
    val translate = (-0.5f * scale + 0.5f) * 255f
    val paint = Paint().apply {
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        colorFilter = ColorMatrixColorFilter(cm)
    }
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, safeConfig(bitmap))
    Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

fun adjustTemperature(bitmap: Bitmap, value: Float): Bitmap {
    val paint = Paint().apply {
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1 + value, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1 - value, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        colorFilter = ColorMatrixColorFilter(cm)
    }
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, safeConfig(bitmap))
    Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

fun applyAdjustments(
    bitmap: Bitmap,
    brightness: Int,
    contrast: Float,
    temperature: Float,
    saturation: Float = 0f,
    highlights: Float = 0f,
    shadows: Float = 0f,
    exposure: Float = 0f
): Bitmap {
    val scale = contrast + 1f
    val translate = (-0.5f * scale + 0.5f) * 255f
    val expScale = 2.0.pow(exposure / 100.0).toFloat()
    val paint = Paint().apply {
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                scale * expScale, 0f, 0f, 0f, translate + brightness,
                0f, scale * expScale, 0f, 0f, translate + brightness,
                0f, 0f, scale * expScale, 0f, translate + brightness,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        if (saturation != 0f) {
            val satMatrix = ColorMatrix().apply {
                val s = saturation + 1f
                val lr = 0.3086f * (1f - s)
                val lg = 0.6094f * (1f - s)
                val lb = 0.0820f * (1f - s)
                set(floatArrayOf(
                    lr + s, lg, lb, 0f, 0f,
                    lr, lg + s, lb, 0f, 0f,
                    lr, lg, lb + s, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            cm.postConcat(satMatrix)
        }
        val tempMatrix = ColorMatrix(floatArrayOf(
            1 + temperature, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1 - temperature, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tempMatrix)
        colorFilter = ColorMatrixColorFilter(cm)
    }
    var result = Bitmap.createBitmap(bitmap.width, bitmap.height, safeConfig(bitmap))
    Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)

    if (highlights != 0f || shadows != 0f) {
        result = applyHighlightsShadows(result, highlights, shadows)
    }
    return result
}

fun applyHighlightsShadows(bitmap: Bitmap, highlights: Float, shadows: Float): Bitmap {
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val a = (pixel shr 24) and 0xFF
        if (a == 0) continue
        var r = ((pixel shr 16) and 0xFF).toFloat()
        var g = ((pixel shr 8) and 0xFF).toFloat()
        var b = (pixel and 0xFF).toFloat()

        val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

        if (luminance > 0.5f && highlights != 0f) {
            val factor = (luminance - 0.5f) * 2f * highlights
            r = (r + factor * 255f).coerceIn(0f, 255f)
            g = (g + factor * 255f).coerceIn(0f, 255f)
            b = (b + factor * 255f).coerceIn(0f, 255f)
        }

        if (luminance < 0.5f && shadows != 0f) {
            val factor = (0.5f - luminance) * 2f * shadows
            r = (r + factor * 255f).coerceIn(0f, 255f)
            g = (g + factor * 255f).coerceIn(0f, 255f)
            b = (b + factor * 255f).coerceIn(0f, 255f)
        }

        pixels[i] = (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun ensureAlphaChannel(bitmap: Bitmap): Bitmap {
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    result.setHasAlpha(true)
    return result
}
