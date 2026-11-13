package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.scale

internal fun safeConfig(bitmap: Bitmap): Bitmap.Config =
    bitmap.config ?: Bitmap.Config.ARGB_8888

fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int, keepAspectRatio: Boolean): Bitmap {
    if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
    return if (keepAspectRatio) {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetHeight = (width / aspectRatio).toInt()
        if (width <= 0 || targetHeight <= 0) return bitmap
        bitmap.scale(width, targetHeight)
    } else {
        if (width <= 0 || height <= 0) return bitmap
        bitmap.scale(width, height)
    }
}

fun downscaleForPreview(bitmap: Bitmap, maxDim: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val longest = maxOf(w, h)
    if (longest <= maxDim) return bitmap
    val scale = maxDim.toFloat() / longest
    return bitmap.scale((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun mirrorBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
    val matrix = Matrix().apply {
        if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
    val cropLeft = left.coerceIn(0, bitmap.width)
    val cropTop = top.coerceIn(0, bitmap.height)
    val cropRight = right.coerceIn(0, bitmap.width)
    val cropBottom = bottom.coerceIn(0, bitmap.height)
    val width = (cropRight - cropLeft).coerceAtLeast(1)
    val height = (cropBottom - cropTop).coerceAtLeast(1)
    return Bitmap.createBitmap(bitmap, cropLeft, cropTop, width, height)
}

fun compositeImage(base: Bitmap, overlay: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
    if (base.width <= 0 || base.height <= 0) return base
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val srcRect = Rect(0, 0, overlay.width, overlay.height)
    val dstW = (right - left).coerceAtLeast(1)
    val dstH = (bottom - top).coerceAtLeast(1)
    val dstRect = Rect(left, top, left + dstW, top + dstH)
    canvas.drawBitmap(overlay, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
    return result
}

fun cutCircularRegion(bitmap: Bitmap, centerX: Float, centerY: Float, radius: Float): Bitmap {
    val left = (centerX - radius).toInt().coerceAtLeast(0)
    val top = (centerY - radius).toInt().coerceAtLeast(0)
    val right = (centerX + radius).toInt().coerceAtMost(bitmap.width)
    val bottom = (centerY + radius).toInt().coerceAtMost(bitmap.height)
    val diameter = (radius * 2).toInt()
    val size = minOf(diameter, right - left, bottom - top).coerceAtLeast(1)

    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply { isAntiAlias = true }

    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val srcLeft = (centerX - size / 2f).toInt().coerceIn(0, bitmap.width - size)
    val srcTop = (centerY - size / 2f).toInt().coerceIn(0, bitmap.height - size)
    val src = Bitmap.createBitmap(bitmap, srcLeft, srcTop, size, size)
    canvas.drawBitmap(src, 0f, 0f, null)
    src.recycle()

    return result
}

fun cutHorizontalStripAndStitch(bitmap: Bitmap, stripTop: Int, stripBottom: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    val top = stripTop.coerceIn(0, height)
    val bottom = stripBottom.coerceIn(0, height)

    if (top >= bottom) return bitmap

    val topPartHeight = top
    val bottomPartHeight = height - bottom
    val newHeight = topPartHeight + bottomPartHeight

    if (newHeight <= 0) return Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888)

    val result = Bitmap.createBitmap(width, newHeight, safeConfig(bitmap))
    val canvas = Canvas(result)

    if (topPartHeight > 0) {
        val srcRect = Rect(0, 0, width, topPartHeight)
        val dstRect = Rect(0, 0, width, topPartHeight)
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    if (bottomPartHeight > 0) {
        val srcRect = Rect(0, bottom, width, height)
        val dstRect = Rect(0, topPartHeight, width, newHeight)
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    return result
}

fun cutVerticalStripAndStitch(bitmap: Bitmap, stripLeft: Int, stripRight: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    val left = stripLeft.coerceIn(0, width)
    val right = stripRight.coerceIn(0, width)

    if (left >= right) return bitmap

    val leftPartWidth = left
    val rightPartWidth = width - right
    val newWidth = leftPartWidth + rightPartWidth

    if (newWidth <= 0) return Bitmap.createBitmap(1, height, Bitmap.Config.ARGB_8888)

    val result = Bitmap.createBitmap(newWidth, height, safeConfig(bitmap))
    val canvas = Canvas(result)

    if (leftPartWidth > 0) {
        val srcRect = Rect(0, 0, leftPartWidth, height)
        val dstRect = Rect(0, 0, leftPartWidth, height)
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    if (rightPartWidth > 0) {
        val srcRect = Rect(right, 0, width, height)
        val dstRect = Rect(leftPartWidth, 0, newWidth, height)
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    return result
}
