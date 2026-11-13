package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs

fun enhanceDocument(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val gray = IntArray(w * h) { i ->
        val p = pixels[i]
        (0.299f * ((p shr 16) and 0xFF) +
         0.587f * ((p shr 8) and 0xFF) +
         0.114f * (p and 0xFF)).toInt().coerceIn(0, 255)
    }

    val hist = IntArray(256)
    for (v in gray) hist[v]++

    var total = w * h
    var minVal = 0
    var sum = 0
    while (minVal < 255 && sum < total * 0.005f) { sum += hist[minVal]; minVal++ }
    var maxVal = 255
    sum = 0
    while (maxVal > 0 && sum < total * 0.005f) { sum += hist[maxVal]; maxVal-- }
    maxVal = maxVal.coerceAtLeast(minVal + 1)

    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val resultPixels = IntArray(w * h)

    for (i in pixels.indices) {
        val a = (pixels[i] shr 24) and 0xFF
        val r = ((pixels[i] shr 16) and 0xFF)
        val g = ((pixels[i] shr 8) and 0xFF)
        val b = pixels[i] and 0xFF

        val nr = ((r - minVal).toFloat() / (maxVal - minVal) * 255f).toInt().coerceIn(0, 255)
        val ng = ((g - minVal).toFloat() / (maxVal - minVal) * 255f).toInt().coerceIn(0, 255)
        val nb = ((b - minVal).toFloat() / (maxVal - minVal) * 255f).toInt().coerceIn(0, 255)

        val luminance = 0.299f * nr + 0.587f * ng + 0.114f * nb
        val cleanR: Int
        val cleanG: Int
        val cleanB: Int
        if (luminance > 200f) {
            cleanR = 255; cleanG = 255; cleanB = 255
        } else {
            val boost = 1.4f
            cleanR = ((nr * boost).toInt()).coerceIn(0, 255)
            cleanG = ((ng * boost).toInt()).coerceIn(0, 255)
            cleanB = ((nb * boost).toInt()).coerceIn(0, 255)
        }

        resultPixels[i] = (a shl 24) or (cleanR shl 16) or (cleanG shl 8) or cleanB
    }

    result.setPixels(resultPixels, 0, w, 0, 0, w, h)

    val sharpened = applySharpen(result, 0.3f)
    if (result !== sharpened) result.recycle()
    return sharpened
}

fun isIdCard(bitmap: Bitmap): Boolean {
    val minDim = minOf(bitmap.width, bitmap.height)
    if (minDim < 200) return false
    val ratio = maxOf(bitmap.width, bitmap.height).toFloat() / minDim
    val targetRatio = 85.6f / 53.98f
    return abs(ratio - targetRatio) < 0.4f
}

fun binarize(bitmap: Bitmap, threshold: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val resultPixels = IntArray(w * h)
    for (i in pixels.indices) {
        val a = (pixels[i] shr 24) and 0xFF
        val r = (pixels[i] shr 16) and 0xFF
        val g = (pixels[i] shr 8) and 0xFF
        val b = pixels[i] and 0xFF
        val luminance = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        val value = if (luminance >= threshold) 255 else 0
        resultPixels[i] = (a shl 24) or (value shl 16) or (value shl 8) or value
    }
    result.setPixels(resultPixels, 0, w, 0, 0, w, h)
    return result
}

fun processWithProfile(
    bitmap: Bitmap,
    enhanceContrast: Boolean,
    binarizeThreshold: Int?,
    sharpenAmount: Float,
    autoCrop: Boolean
): Bitmap {
    var result = bitmap
    fun Bitmap.recycleIfIntermediate() {
        if (this !== bitmap && !this.isRecycled) this.recycle()
    }
    if (autoCrop) { val prev = result; result = autoCropContent(result); prev.recycleIfIntermediate() }
    if (enhanceContrast) { val prev = result; result = enhanceDocument(result); prev.recycleIfIntermediate() }
    if (binarizeThreshold != null) { val prev = result; result = binarize(result, binarizeThreshold); prev.recycleIfIntermediate() }
    if (sharpenAmount > 0f) { val prev = result; result = applySharpen(result, sharpenAmount); prev.recycleIfIntermediate() }
    return result
}
