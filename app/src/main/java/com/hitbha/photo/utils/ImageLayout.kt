package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.scale

private const val MAX_BITMAP_DIMENSION = 4096

private fun clampDimension(value: Int): Int = value.coerceAtMost(MAX_BITMAP_DIMENSION)

private fun scaleFactorToFit(width: Int, height: Int): Float {
    val maxDim = maxOf(width, height)
    return if (maxDim > MAX_BITMAP_DIMENSION) MAX_BITMAP_DIMENSION.toFloat() / maxDim else 1f
}

private fun scaledSource(bitmap: Bitmap, scale: Float, recycled: MutableList<Bitmap>): Bitmap {
    if (scale >= 1f) return bitmap
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
    if (scaled !== bitmap) recycled.add(bitmap)
    return scaled
}

fun stitchScreenshotsVertical(bitmaps: List<Bitmap>): Bitmap {
    if (bitmaps.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val totalHeight = bitmaps.sumOf { it.height }
    val maxWidth = bitmaps.maxOf { it.width }
    val scale = scaleFactorToFit(maxWidth, totalHeight)
    val recycled = mutableListOf<Bitmap>()
    return try {
        val sources = bitmaps.map { scaledSource(it, scale, recycled) }
        val resultWidth = clampDimension((maxWidth * scale).toInt().coerceAtLeast(1))
        val resultHeight = clampDimension((totalHeight * scale).toInt().coerceAtLeast(1))
        val result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var yOffset = 0
        for (bitmap in sources) {
            val drawH = clampDimension((bitmap.height * scale).toInt())
            canvas.drawBitmap(bitmap, 0f, yOffset.toFloat(), null)
            yOffset += drawH
        }
        result
    } finally {
        recycled.forEach { it.recycle() }
    }
}

fun stitchScreenshotsHorizontal(bitmaps: List<Bitmap>): Bitmap {
    if (bitmaps.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val totalWidth = bitmaps.sumOf { it.width }
    val maxHeight = bitmaps.maxOf { it.height }
    val scale = scaleFactorToFit(totalWidth, maxHeight)
    val recycled = mutableListOf<Bitmap>()
    return try {
        val sources = bitmaps.map { scaledSource(it, scale, recycled) }
        val resultWidth = clampDimension((totalWidth * scale).toInt().coerceAtLeast(1))
        val resultHeight = clampDimension((maxHeight * scale).toInt().coerceAtLeast(1))
        val result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var xOffset = 0
        for (bitmap in sources) {
            val drawW = clampDimension((bitmap.width * scale).toInt())
            canvas.drawBitmap(bitmap, xOffset.toFloat(), 0f, null)
            xOffset += drawW
        }
        result
    } finally {
        recycled.forEach { it.recycle() }
    }
}

fun stitchScreenshotsGrid(bitmaps: List<Bitmap>): Bitmap {
    if (bitmaps.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val cellW = bitmaps.maxOf { it.width }
    val cellH = bitmaps.maxOf { it.height }
    val cols = 2
    val rows = (bitmaps.size + 1) / 2
    val gridW = cellW * cols
    val gridH = cellH * rows
    val scale = scaleFactorToFit(gridW, gridH)
    val recycled = mutableListOf<Bitmap>()
    return try {
        val sources = bitmaps.map { scaledSource(it, scale, recycled) }
        val resultWidth = clampDimension((gridW * scale).toInt().coerceAtLeast(1))
        val resultHeight = clampDimension((gridH * scale).toInt().coerceAtLeast(1))
        val result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val scaledCellW = (cellW * scale).toInt()
        val scaledCellH = (cellH * scale).toInt()
        for (i in sources.indices) {
            val col = i % cols
            val row = i / cols
            canvas.drawBitmap(sources[i], (col * scaledCellW).toFloat(), (row * scaledCellH).toFloat(), null)
        }
        result
    } finally {
        recycled.forEach { it.recycle() }
    }
}

private const val CR80_WIDTH_MM = 86f
private const val CR80_HEIGHT_MM = 54f
private const val CR80_DPI = 300f

fun combineIdCards(top: Bitmap, bottom: Bitmap, marginPx: Int, targetDpi: Float = CR80_DPI): Bitmap {
    val cr80W = (CR80_WIDTH_MM / 25.4f * targetDpi).toInt()
    val cr80H = (CR80_HEIGHT_MM / 25.4f * targetDpi).toInt()
    val scaledTop = Bitmap.createScaledBitmap(top, cr80W, cr80H, true)
    val scaledBottom = Bitmap.createScaledBitmap(bottom, cr80W, cr80H, true)
    val totalHeight = cr80H * 2 + marginPx
    val result = Bitmap.createBitmap(cr80W, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(scaledTop, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.drawBitmap(scaledBottom, 0f, (cr80H + marginPx).toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
    return result
}

const val PASSPORT_DPI = 300f
const val A4_WIDTH_INCHES = 8.27f
const val A4_HEIGHT_INCHES = 11.69f

fun createPassportLayout(
    photos: List<Bitmap>,
    pageWidthPx: Int,
    pageHeightPx: Int,
    columns: Int,
    rows: Int,
    photoCount: Int = columns * rows,
    alignTop: Boolean = false,
    topMarginOnly: Boolean = false,
    removeBottomMargin: Boolean = false,
    noMargin: Boolean = false,
    marginSidePxOverride: Int? = null,
    topMarginOverride: Int? = null,
    alternateColumnAlign: Boolean = false,
    fillConsecutive: Boolean = false,
    rowsPerGroup: Int = 0,
    globalRowOffset: Int = 0,
    fillCells: Boolean = false,
    showCutLines: Boolean = true,
    noSpacing: Boolean = false,
    spacingPxOverride: Int? = null,
    spacingYOverride: Int? = null
): Bitmap {
    val rotatedPhotos = photos.map { rotateBitmap(it, -90f) }

    val result = Bitmap.createBitmap(pageWidthPx, pageHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)

    val marginPx = if (noMargin) 0 else (0.25f * PASSPORT_DPI).toInt()
    val topMarginPx = topMarginOverride ?: if (noMargin) 0 else (16 * PASSPORT_DPI / 160).toInt()
    val marginSidePx = marginSidePxOverride ?: if (noMargin) 0 else (40 * PASSPORT_DPI / 160).toInt()
    val marginBottomPx = 0
    val spacingPx = spacingPxOverride ?: if (noSpacing) 0 else 9
    val spacingY = spacingYOverride ?: if (noSpacing) 0 else spacingPx

    val contentWidth = pageWidthPx - 2 * marginSidePx
    val contentHeight = pageHeightPx - topMarginPx - marginBottomPx

    val cellWidth = (contentWidth - (columns - 1) * spacingPx) / columns
    val cellHeight = if (fillCells) {
        ((contentHeight - 10 - (rows - 1) * spacingY) / rows).coerceAtLeast(1)
    } else if (alignTop) {
        val refPhoto = rotatedPhotos.firstOrNull()
            ?: run {
                rotatedPhotos.forEach { recycleQuietly(it) }
                return result
            }
        val scale = cellWidth.toFloat() / refPhoto.width
        val naturalH = (refPhoto.height * scale).toInt().coerceAtLeast(1)
        val maxH = ((contentHeight - 10 - (rows - 1) * spacingY) / rows).coerceAtLeast(1)
        minOf(naturalH, maxH)
    } else {
        (contentHeight - (rows - 1) * spacingY) / rows
    }

    val borderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    val photoSequence = if (fillConsecutive && photos.isNotEmpty()) {
        val copiesPerPhoto = when (photos.size) {
            1 -> photoCount
            2 -> photoCount / 2
            3, 4 -> 2
            else -> 1
        }
        val seq = mutableListOf<Int>()
        for (pi in photos.indices) {
            repeat(copiesPerPhoto) { if (seq.size < photoCount) seq.add(pi) }
        }
        seq
    } else {
        emptyList()
    }

    val actualRowsToDraw = if (rowsPerGroup > 0 && photos.isNotEmpty()) {
            val totalConceptual = photos.size * rowsPerGroup
        val remaining = totalConceptual - globalRowOffset
        remaining.coerceIn(0, rows)
    } else {
        rows
    }

    var drawn = 0
    for (row in 0 until actualRowsToDraw) {
        for (col in 0 until columns) {
            if (drawn >= photoCount) break

            val globalRow = row + globalRowOffset
            val personIdx = if (rowsPerGroup > 0) {
                (globalRow / rowsPerGroup).coerceAtMost(photos.size - 1)
            } else if (fillConsecutive) {
                photoSequence.getOrNull(drawn) ?: break
            } else {
                if (photos.isNotEmpty()) drawn % photos.size else 0
            }
            val currentPhoto = rotatedPhotos.getOrNull(personIdx) ?: continue

            val cellLeft = (marginSidePx + col * (cellWidth + spacingPx)).toFloat()
            val cellTop: Float
            val cellHeightActual: Float

            if (fillCells) {
                cellTop = (topMarginPx + row * (cellHeight + spacingY)).toFloat()
                cellHeightActual = cellHeight.toFloat()
            } else {
                val scale = minOf(cellWidth.toFloat() / currentPhoto.width, cellHeight.toFloat() / currentPhoto.height)
                val photoW = (currentPhoto.width * scale).toInt().coerceAtLeast(1)
                val photoH = (currentPhoto.height * scale).toInt().coerceAtLeast(1)
                val scaledPhoto = currentPhoto.scale(photoW, photoH)
                val x = if (alternateColumnAlign) {
                    if (col == 0) {
                        marginSidePx + col * (cellWidth + spacingPx)
                    } else {
                        marginSidePx + col * (cellWidth + spacingPx) + (cellWidth - photoW)
                    }
                } else {
                    marginSidePx + col * (cellWidth + spacingPx) + (cellWidth - photoW) / 2
                }
                val y = if (alignTop) {
                    topMarginPx + row * (photoH + spacingY)
                } else {
                    topMarginPx + row * (cellHeight + spacingY) + (cellHeight - photoH) / 2
                }
                val paint = Paint().apply { isFilterBitmap = true }
                canvas.drawBitmap(scaledPhoto, x.toFloat(), y.toFloat(), paint)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + photoW).toFloat(), (y + photoH).toFloat(), borderPaint)
                recycleQuietly(scaledPhoto)
                drawn++
                continue
            }

            val cellRight = cellLeft + cellWidth
            val cellBottom = cellTop + cellHeightActual
            val scale = maxOf(cellWidth.toFloat() / currentPhoto.width, cellHeightActual / currentPhoto.height)
            val photoW = (currentPhoto.width * scale).toInt().coerceAtLeast(1)
            val photoH = (currentPhoto.height * scale).toInt().coerceAtLeast(1)
            val scaledPhoto = currentPhoto.scale(photoW, photoH)
            val x = cellLeft + (cellWidth - photoW) / 2f
            val y = cellTop + (cellHeightActual - photoH) / 2f
            val paint = Paint().apply { isFilterBitmap = true }
            canvas.save()
            canvas.clipRect(cellLeft, cellTop, cellRight, cellBottom)
            canvas.drawBitmap(scaledPhoto, x, y, paint)
            canvas.restore()
            canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, borderPaint)
            recycleQuietly(scaledPhoto)
            drawn++
        }
        if (drawn >= photoCount) break
    }

    val columnDividerPaint = Paint().apply {
        color = Color.parseColor("#999999")
        alpha = 120
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        isAntiAlias = true
    }
    val actualRowsDrawn = minOf(rows, (drawn + columns - 1) / columns)
    val topY = topMarginPx
    val bottomY = if (alignTop) {
        topMarginPx + actualRowsDrawn * cellHeight + (actualRowsDrawn - 1) * spacingY
    } else {
        topMarginPx + actualRowsDrawn * cellHeight + (actualRowsDrawn - 1) * spacingY
    }
    if (showCutLines && actualRowsDrawn > 0) {
        for (col in 0 until columns - 1) {
            val lineX = marginSidePx + (col + 1) * cellWidth + (col + 0.5f) * spacingPx
            canvas.drawLine(lineX, topY.toFloat(), lineX, bottomY.toFloat(), columnDividerPaint)
        }
    }

    if (!noMargin) {
        drawMarginGuides(canvas, pageWidthPx, pageHeightPx, marginPx, topMarginOnly, removeBottomMargin)
    }

    rotatedPhotos.forEach { recycleQuietly(it) }
    return result
}

fun createFullPageLayout(
    photo: Bitmap,
    pageWidthPx: Int,
    pageHeightPx: Int,
    marginPx: Int = 20,
    scaleToFill: Boolean = false,
    offsetXPx: Int = 0,
    offsetYPx: Int = 0,
    scaleMultiplier: Float = 1f,
    stretchLeft: Int = 0,
    stretchRight: Int = 0,
    stretchTop: Int = 0,
    stretchBottom: Int = 0
): Bitmap {
    val result = Bitmap.createBitmap(pageWidthPx, pageHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)

    val contentWidth = pageWidthPx - 2 * marginPx
    val contentHeight = pageHeightPx - 2 * marginPx

    val baseScale = if (scaleToFill) {
        maxOf(contentWidth.toFloat() / photo.width, contentHeight.toFloat() / photo.height)
    } else {
        minOf(contentWidth.toFloat() / photo.width, contentHeight.toFloat() / photo.height)
    }
    val effectiveScale = baseScale * scaleMultiplier.coerceAtLeast(0.1f)
    val scaledW = (photo.width * effectiveScale).toInt().coerceAtLeast(1)
    val scaledH = (photo.height * effectiveScale).toInt().coerceAtLeast(1)

    val baseX = marginPx + (contentWidth - scaledW) / 2
    val baseY = marginPx + (contentHeight - scaledH) / 2

    val left = (baseX + stretchLeft + offsetXPx).coerceIn(marginPx, pageWidthPx - marginPx - 1)
    val top = (baseY + stretchTop + offsetYPx).coerceIn(marginPx, pageHeightPx - marginPx - 1)
    val right = (baseX + scaledW + stretchRight + offsetXPx).coerceIn(marginPx + 1, pageWidthPx - marginPx)
    val bottom = (baseY + scaledH + stretchBottom + offsetYPx).coerceIn(marginPx + 1, pageHeightPx - marginPx)

    val paint = Paint().apply { isFilterBitmap = true }
    canvas.save()
    canvas.clipRect(marginPx, marginPx, pageWidthPx - marginPx, pageHeightPx - marginPx)
    canvas.drawBitmap(photo, null, Rect(left, top, right, bottom), paint)
    canvas.restore()
    return result
}

private fun drawMarginGuides(canvas: Canvas, pageWidthPx: Int, pageHeightPx: Int, marginPx: Int, topMarginOnly: Boolean, removeBottomMargin: Boolean) {
    if (!topMarginOnly && !removeBottomMargin) return

    val right = pageWidthPx.toFloat()
    val bottom = pageHeightPx.toFloat()
    val guideSize = 30f

    val guidePaint = Paint().apply {
        color = Color.parseColor("#FF5252")
        alpha = 80
        strokeWidth = 2.5f
        isAntiAlias = true
    }

    if (topMarginOnly) {
        val centerX = pageWidthPx / 2f
        val foldPaint = Paint().apply {
            color = Color.parseColor("#999999")
            alpha = 120
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            isAntiAlias = true
        }
        canvas.drawLine(centerX, 0f, centerX, bottom, foldPaint)

        canvas.drawLine(0f, 0f, 0f, guideSize, guidePaint)
        canvas.drawLine(0f, 0f, guideSize, 0f, guidePaint)
        canvas.drawLine(marginPx.toFloat(), 0f, marginPx.toFloat(), guideSize, guidePaint)

        canvas.drawLine(right, 0f, right, guideSize, guidePaint)
        canvas.drawLine(right, 0f, right - guideSize, 0f, guidePaint)
        canvas.drawLine(right - marginPx, 0f, right - marginPx, guideSize, guidePaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#757575")
            alpha = 120
            textSize = 20f
            isAntiAlias = true
        }
        canvas.drawText("L", guideSize + 4f, 16f, textPaint)
        canvas.drawText("R", right - guideSize - 18f, 16f, textPaint)
    }

    if (removeBottomMargin) {
        val hGuideSize = 30f
        canvas.drawLine(0f, bottom, hGuideSize, bottom, guidePaint)
        canvas.drawLine(right - hGuideSize, bottom, right, bottom, guidePaint)
        canvas.drawLine(right, bottom - hGuideSize, right, bottom, guidePaint)
        canvas.drawLine(0f, bottom - hGuideSize, 0f, bottom, guidePaint)
    }
}

private fun recycleQuietly(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
}
