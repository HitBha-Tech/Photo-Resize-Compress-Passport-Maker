package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

private const val MAX_ERASE_DIMENSION = 4096

private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
    val maxDim = maxOf(bitmap.width, bitmap.height)
    if (maxDim <= MAX_ERASE_DIMENSION) return bitmap
    val scale = MAX_ERASE_DIMENSION.toFloat() / maxDim
    val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
}

fun colorEraseBitmap(bitmap: Bitmap, x: Float, y: Float, tolerance: Int = 40): Bitmap {
    val scaled = downscaleIfNeeded(bitmap)
    val cx = (x * scaled.width / bitmap.width).toInt().coerceIn(0, scaled.width - 1)
    val cy = (y * scaled.height / bitmap.height).toInt().coerceIn(0, scaled.height - 1)

    val result = scaled.copy(Bitmap.Config.ARGB_8888, true)
    if (scaled !== bitmap) scaled.recycle()
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)

    val targetColor = findDominantColor(pixels, w, h, cx, cy)
    floodFillTransparent(pixels, w, h, cx, cy, targetColor, tolerance)

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

private fun findDominantColor(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int): Int {
    val sampleRadius = 5
    val colorCounts = mutableMapOf<Int, Int>()

    for (dy in -sampleRadius..sampleRadius) {
        for (dx in -sampleRadius..sampleRadius) {
            val px = (cx + dx).coerceIn(0, w - 1)
            val py = (cy + dy).coerceIn(0, h - 1)
            val color = pixels[py * w + px]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val qr = (r / 32) * 32
            val qg = (g / 32) * 32
            val qb = (b / 32) * 32
            val quantized = (0xFF shl 24) or (qr shl 16) or (qg shl 8) or qb
            colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
        }
    }

    val dominantQuantized = colorCounts.maxByOrNull { it.value }?.key
        ?: return pixels[cy * w + cx]
    val dominantR = (dominantQuantized shr 16) and 0xFF
    val dominantG = (dominantQuantized shr 8) and 0xFF
    val dominantB = dominantQuantized and 0xFF
    var bestColor = pixels[cy * w + cx]
    var bestDist = Int.MAX_VALUE
    for (dy in -sampleRadius..sampleRadius) {
        for (dx in -sampleRadius..sampleRadius) {
            val px = (cx + dx).coerceIn(0, w - 1)
            val py = (cy + dy).coerceIn(0, h - 1)
            val color = pixels[py * w + px]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val dr = r - dominantR
            val dg = g - dominantG
            val db = b - dominantB
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestColor = color
            }
        }
    }
    return bestColor
}

private fun matchesTarget(pixel: Int, targetR: Int, targetG: Int, targetB: Int, toleranceSq: Int): Boolean {
    if (Color.alpha(pixel) == 0) return false
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    val dr = r - targetR
    val dg = g - targetG
    val db = b - targetB
    return dr * dr + dg * dg + db * db <= toleranceSq
}

private fun floodFillTransparent(pixels: IntArray, w: Int, h: Int, startX: Int, startY: Int, targetColor: Int, tolerance: Int) {
    val targetR = (targetColor shr 16) and 0xFF
    val targetG = (targetColor shr 8) and 0xFF
    val targetB = targetColor and 0xFF
    val toleranceSq = tolerance * tolerance

    val visited = BooleanArray(w * h)
    val stack = ArrayDeque<Int>()
    stack.addLast(startY * w + startX)

    while (stack.isNotEmpty()) {
        val offset = stack.removeLast()
        val x = offset % w
        val y = offset / w
        if (x < 0 || x >= w || y < 0 || y >= h) continue
        if (visited[offset]) continue
        if (!matchesTarget(pixels[offset], targetR, targetG, targetB, toleranceSq)) continue

        var lx = x
        var rx = x

        while (lx > 0 && !visited[(y * w) + lx - 1] && matchesTarget(pixels[(y * w) + lx - 1], targetR, targetG, targetB, toleranceSq)) {
            lx--
        }
        while (rx < w - 1 && !visited[(y * w) + rx + 1] && matchesTarget(pixels[(y * w) + rx + 1], targetR, targetG, targetB, toleranceSq)) {
            rx++
        }

        for (cx in lx..rx) {
            val idx = y * w + cx
            visited[idx] = true
            pixels[idx] = Color.TRANSPARENT
        }

        if (y > 0) {
            for (cx in lx..rx) {
                val above = (y - 1) * w + cx
                if (!visited[above] && matchesTarget(pixels[above], targetR, targetG, targetB, toleranceSq)) {
                    stack.addLast(above)
                }
            }
        }
        if (y < h - 1) {
            for (cx in lx..rx) {
                val below = (y + 1) * w + cx
                if (!visited[below] && matchesTarget(pixels[below], targetR, targetG, targetB, toleranceSq)) {
                    stack.addLast(below)
                }
            }
        }
    }
}

fun magicRemoveBitmap(bitmap: Bitmap, targetColor: Int, tolerance: Int = 40): Bitmap {
    val scaled = downscaleIfNeeded(bitmap)
    val result = scaled.copy(Bitmap.Config.ARGB_8888, true)
    if (scaled !== bitmap) scaled.recycle()
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)

    val targetR = (targetColor shr 16) and 0xFF
    val targetG = (targetColor shr 8) and 0xFF
    val targetB = targetColor and 0xFF
    val toleranceSq = tolerance * tolerance

    for (i in pixels.indices) {
        if (matchesTarget(pixels[i], targetR, targetG, targetB, toleranceSq)) {
            pixels[i] = Color.TRANSPARENT
        }
    }

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

fun eraseBitmap(bitmap: Bitmap, points: List<Pair<Float, Float>>, brushRadius: Float): Bitmap {
    val scaled = downscaleIfNeeded(bitmap)
    val scaleX = scaled.width.toFloat() / bitmap.width
    val scaleY = scaled.height.toFloat() / bitmap.height
    val result = scaled.copy(Bitmap.Config.ARGB_8888, true)
    if (scaled !== bitmap) scaled.recycle()
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)

    val scaledPoints = points.map { (px, py) -> px * scaleX to py * scaleY }
    val scaledRadius = brushRadius * maxOf(scaleX, scaleY)

    if (scaledPoints.size == 1) {
        val (cx, cy) = scaledPoints[0]
        fillCircle(pixels, w, h, cx, cy, scaledRadius)
    } else {
        for (i in 1 until scaledPoints.size) {
            val (x1, y1) = scaledPoints[i - 1]
            val (x2, y2) = scaledPoints[i]
            drawThickLine(pixels, w, h, x1, y1, x2, y2, scaledRadius)
        }
    }

    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

private fun fillCircle(pixels: IntArray, w: Int, h: Int, cx: Float, cy: Float, radius: Float) {
    val rSq = radius * radius
    val minX = (cx - radius).toInt().coerceAtLeast(0)
    val maxX = (cx + radius).toInt().coerceAtMost(w - 1)
    val minY = (cy - radius).toInt().coerceAtLeast(0)
    val maxY = (cy + radius).toInt().coerceAtMost(h - 1)
    for (y in minY..maxY) {
        val rowOffset = y * w
        val dy = y - cy
        val dySq = dy * dy
        for (x in minX..maxX) {
            val dx = x - cx
            if (dx * dx + dySq <= rSq) {
                pixels[rowOffset + x] = Color.TRANSPARENT
            }
        }
    }
}

private fun drawThickLine(pixels: IntArray, w: Int, h: Int, x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) {
    val dx = x2 - x1
    val dy = y2 - y1
    val dist = sqrt(dx * dx + dy * dy)
    val steps = (dist / maxOf(radius * 0.3f, 0.5f)).toInt().coerceAtLeast(1)
    for (step in 0..steps) {
        val t = step.toFloat() / steps
        fillCircle(pixels, w, h, x1 + dx * t, y1 + dy * t, radius)
    }
}
