package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sqrt

fun applySharpen(bitmap: Bitmap, amount: Float): Bitmap {
    if (amount <= 0f) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val srcPixels = IntArray(w * h)
    bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
    val dstPixels = srcPixels.copyOf()

    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val alpha = srcPixels[i] shr 24 and 0xFF
            val r = ((
                (srcPixels[i - w] shr 16 and 0xFF) * -amount +
                (srcPixels[i - 1] shr 16 and 0xFF) * -amount +
                (srcPixels[i] shr 16 and 0xFF) * (1f + 4f * amount) +
                (srcPixels[i + 1] shr 16 and 0xFF) * -amount +
                (srcPixels[i + w] shr 16 and 0xFF) * -amount
                )).toInt().coerceIn(0, 255)
            val g = ((
                (srcPixels[i - w] shr 8 and 0xFF) * -amount +
                (srcPixels[i - 1] shr 8 and 0xFF) * -amount +
                (srcPixels[i] shr 8 and 0xFF) * (1f + 4f * amount) +
                (srcPixels[i + 1] shr 8 and 0xFF) * -amount +
                (srcPixels[i + w] shr 8 and 0xFF) * -amount
                )).toInt().coerceIn(0, 255)
            val b = ((
                (srcPixels[i - w] and 0xFF) * -amount +
                (srcPixels[i - 1] and 0xFF) * -amount +
                (srcPixels[i] and 0xFF) * (1f + 4f * amount) +
                (srcPixels[i + 1] and 0xFF) * -amount +
                (srcPixels[i + w] and 0xFF) * -amount
                )).toInt().coerceIn(0, 255)
            dstPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    result.setPixels(dstPixels, 0, w, 0, 0, w, h)
    return result
}

fun applyVignette(bitmap: Bitmap, amount: Float): Bitmap {
    if (amount <= 0f) return bitmap
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val w = result.width
    val h = result.height
    val cx = w / 2f
    val cy = h / 2f
    val maxDist = sqrt(cx * cx + cy * cy)

    if (w == 0 || h == 0) return result

    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val idx = y * w + x
            val alpha = (pixels[idx] ushr 24) and 0xFF
            if (alpha == 0) continue
            val dx = x - cx
            val dy = y - cy
            val dist = sqrt(dx * dx + dy * dy) / maxDist
            val strength = (dist * amount).coerceIn(0f, 1f)
            if (strength <= 0f) continue
            val darken = 1f - strength
            val r = (((pixels[idx] shr 16) and 0xFF) * darken).toInt().coerceIn(0, 255)
            val g = (((pixels[idx] shr 8) and 0xFF) * darken).toInt().coerceIn(0, 255)
            val b = ((pixels[idx] and 0xFF) * darken).toInt().coerceIn(0, 255)
            pixels[idx] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun applyBlur(bitmap: Bitmap, radius: Float): Bitmap {
    if (radius <= 0) return bitmap
    val scale = 1f / (radius * 0.15f + 1f)
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val result = Bitmap.createScaledBitmap(scaled, bitmap.width, bitmap.height, true)
    if (scaled != result) scaled.recycle()
    return result
}

fun featherEdges(bitmap: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)
    val alphas = IntArray(w * h) { i -> (pixels[i] ushr 24) and 0xFF }

    for (y in 0 until h) {
        for (x in 0 until w) {
            val idx = y * w + x
            val alpha = alphas[idx]
            if (alpha == 0) continue

            var minDist = Float.MAX_VALUE
            val minY = (y - radius).coerceAtLeast(0)
            val maxY = (y + radius).coerceAtMost(h - 1)
            val minX = (x - radius).coerceAtLeast(0)
            val maxX = (x + radius).coerceAtMost(w - 1)

            for (ny in minY..maxY) {
                for (nx in minX..maxX) {
                    if (alphas[ny * w + nx] == 0) {
                        val dist = sqrt(((nx - x) * (nx - x) + (ny - y) * (ny - y)).toFloat())
                        if (dist < minDist) minDist = dist
                    }
                }
            }

            if (minDist <= radius) {
                val factor = (minDist / radius).coerceIn(0f, 1f)
                val newAlpha = (alpha * factor).toInt().coerceIn(0, alpha)
                pixels[idx] = (newAlpha shl 24) or (pixels[idx] and 0x00FFFFFF)
            }
        }
    }

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun fillBackground(bitmap: Bitmap, color: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val bgR = Color.red(color)
    val bgG = Color.green(color)
    val bgB = Color.blue(color)

    for (i in pixels.indices) {
        val a = (pixels[i] ushr 24) and 0xFF
        if (a == 0) {
            pixels[i] = color
        } else if (a < 255) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            val inv = 255 - a
            pixels[i] = (0xFF shl 24) or
                ((r * a + bgR * inv) / 255 shl 16) or
                ((g * a + bgG * inv) / 255 shl 8) or
                ((b * a + bgB * inv) / 255)
        }
    }

    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun cleanSemiTransparent(bitmap: Bitmap, threshold: Int = 30): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)

    for (i in pixels.indices) {
        val alpha = (pixels[i] ushr 24) and 0xFF
        if (alpha in 1 until threshold) {
            pixels[i] = Color.TRANSPARENT
        }
    }

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun removeOutline(bitmap: Bitmap, removePx: Int = 0): Bitmap {
    if (removePx <= 0) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)
    val alphas = IntArray(w * h) { i -> (pixels[i] ushr 24) and 0xFF }

    for (y in 0 until h) {
        for (x in 0 until w) {
            val idx = y * w + x
            val alpha = alphas[idx]
            if (alpha == 0) continue

            var minDist = Float.MAX_VALUE
            val minY = (y - removePx).coerceAtLeast(0)
            val maxY = (y + removePx).coerceAtMost(h - 1)
            val minX = (x - removePx).coerceAtLeast(0)
            val maxX = (x + removePx).coerceAtMost(w - 1)

            for (ny in minY..maxY) {
                for (nx in minX..maxX) {
                    if (alphas[ny * w + nx] == 0) {
                        val dist = sqrt(((nx - x) * (nx - x) + (ny - y) * (ny - y)).toFloat())
                        if (dist < minDist) minDist = dist
                    }
                }
            }

            if (minDist <= removePx) {
                pixels[idx] = Color.TRANSPARENT
            }
        }
    }

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun autoCropContent(bitmap: Bitmap, tolerance: Int = 30): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    var minX = w
    var minY = h
    var maxX = 0
    var maxY = 0

    for (y in 0 until h) {
        for (x in 0 until w) {
            val pixel = pixels[y * w + x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            if (luminance < 255 - tolerance) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
    }

    if (minX >= maxX || minY >= maxY) return bitmap

    val pad = 10
    val cropLeft = (minX - pad).coerceIn(0, w)
    val cropTop = (minY - pad).coerceIn(0, h)
    val cropRight = (maxX + pad).coerceIn(0, w)
    val cropBottom = (maxY + pad).coerceIn(0, h)
    return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
}
