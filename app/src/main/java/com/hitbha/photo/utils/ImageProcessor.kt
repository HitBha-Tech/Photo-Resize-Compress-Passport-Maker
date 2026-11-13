package com.hitbha.photo.utils

import android.graphics.Bitmap

/**
 * Facade that preserves the existing public API while delegating to focused modules:
 * - ImageTransforms   – resize, rotate, mirror, crop, cut operations
 * - ImageAdjustments  – brightness, contrast, temperature, highlights/shadows
 * - ImageFilters      – sharpen, vignette, blur, feather, fill, auto-crop
 * - ImageErasing      – color erase, magic remove, brush erase
 * - ImageDocument     – enhance, binarize, ID-card detection, profile processing
 * - ImageLayout       – passport layout, full-page layout, screenshot stitching, ID cards
 */
object ImageProcessor {

    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int, keepAspectRatio: Boolean): Bitmap =
        com.hitbha.photo.utils.resizeBitmap(bitmap, width, height, keepAspectRatio)

    fun downscaleForPreview(bitmap: Bitmap, maxDim: Int): Bitmap =
        com.hitbha.photo.utils.downscaleForPreview(bitmap, maxDim)

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap =
        com.hitbha.photo.utils.rotateBitmap(bitmap, degrees)

    fun mirrorBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap =
        com.hitbha.photo.utils.mirrorBitmap(bitmap, horizontal)

    fun adjustBrightness(bitmap: Bitmap, value: Int): Bitmap =
        com.hitbha.photo.utils.adjustBrightness(bitmap, value)

    fun adjustContrast(bitmap: Bitmap, value: Float): Bitmap =
        com.hitbha.photo.utils.adjustContrast(bitmap, value)

    fun adjustTemperature(bitmap: Bitmap, value: Float): Bitmap =
        com.hitbha.photo.utils.adjustTemperature(bitmap, value)

    fun applyAdjustments(
        bitmap: Bitmap, brightness: Int, contrast: Float, temperature: Float,
        saturation: Float = 0f, highlights: Float = 0f, shadows: Float = 0f, exposure: Float = 0f
    ): Bitmap = com.hitbha.photo.utils.applyAdjustments(bitmap, brightness, contrast, temperature, saturation, highlights, shadows, exposure)

    fun applyHighlightsShadows(bitmap: Bitmap, highlights: Float, shadows: Float): Bitmap =
        com.hitbha.photo.utils.applyHighlightsShadows(bitmap, highlights, shadows)

    fun applySharpen(bitmap: Bitmap, amount: Float): Bitmap =
        com.hitbha.photo.utils.applySharpen(bitmap, amount)

    fun applyVignette(bitmap: Bitmap, amount: Float): Bitmap =
        com.hitbha.photo.utils.applyVignette(bitmap, amount)

    fun applyBlur(bitmap: Bitmap, radius: Float): Bitmap =
        com.hitbha.photo.utils.applyBlur(bitmap, radius)

    fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap =
        com.hitbha.photo.utils.cropBitmap(bitmap, left, top, right, bottom)

    fun compositeImage(base: Bitmap, overlay: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap =
        com.hitbha.photo.utils.compositeImage(base, overlay, left, top, right, bottom)

    fun cutCircularRegion(bitmap: Bitmap, centerX: Float, centerY: Float, radius: Float): Bitmap =
        com.hitbha.photo.utils.cutCircularRegion(bitmap, centerX, centerY, radius)

    fun cutHorizontalStripAndStitch(bitmap: Bitmap, stripTop: Int, stripBottom: Int): Bitmap =
        com.hitbha.photo.utils.cutHorizontalStripAndStitch(bitmap, stripTop, stripBottom)

    fun cutVerticalStripAndStitch(bitmap: Bitmap, stripLeft: Int, stripRight: Int): Bitmap =
        com.hitbha.photo.utils.cutVerticalStripAndStitch(bitmap, stripLeft, stripRight)

    fun compressBitmap(bitmap: Bitmap, quality: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray =
        com.hitbha.photo.utils.compressBitmap(bitmap, quality, format)

    fun compressForPdf(bitmap: Bitmap, quality: Int): Bitmap =
        com.hitbha.photo.utils.compressForPdf(bitmap, quality)

    fun scaleByQuality(bitmap: Bitmap, quality: Int): Bitmap =
        com.hitbha.photo.utils.scaleByQuality(bitmap, quality)

    fun encodePng(bitmap: Bitmap, quality: Int): ByteArray =
        com.hitbha.photo.utils.encodePng(bitmap, quality)

    fun stitchScreenshotsVertical(bitmaps: List<Bitmap>): Bitmap =
        com.hitbha.photo.utils.stitchScreenshotsVertical(bitmaps)

    fun stitchScreenshotsHorizontal(bitmaps: List<Bitmap>): Bitmap =
        com.hitbha.photo.utils.stitchScreenshotsHorizontal(bitmaps)

    fun stitchScreenshotsGrid(bitmaps: List<Bitmap>): Bitmap =
        com.hitbha.photo.utils.stitchScreenshotsGrid(bitmaps)

    fun combineIdCards(top: Bitmap, bottom: Bitmap, marginPx: Int, targetDpi: Float = 300f): Bitmap =
        com.hitbha.photo.utils.combineIdCards(top, bottom, marginPx, targetDpi)

    const val PASSPORT_DPI = 300f
    const val A4_WIDTH_INCHES = 8.27f
    const val A4_HEIGHT_INCHES = 11.69f

    fun createPassportLayout(
        photos: List<Bitmap>, pageWidthPx: Int, pageHeightPx: Int,
        columns: Int, rows: Int, photoCount: Int = columns * rows,
        alignTop: Boolean = false, topMarginOnly: Boolean = false,
        removeBottomMargin: Boolean = false, noMargin: Boolean = false,
        marginSidePxOverride: Int? = null, topMarginOverride: Int? = null,
        alternateColumnAlign: Boolean = false, fillConsecutive: Boolean = false,
        rowsPerGroup: Int = 0, globalRowOffset: Int = 0,
        fillCells: Boolean = false, showCutLines: Boolean = true,
        noSpacing: Boolean = false, spacingPxOverride: Int? = null,
        spacingYOverride: Int? = null
    ): Bitmap = com.hitbha.photo.utils.createPassportLayout(
        photos, pageWidthPx, pageHeightPx, columns, rows, photoCount,
        alignTop, topMarginOnly, removeBottomMargin, noMargin, marginSidePxOverride,
        topMarginOverride, alternateColumnAlign, fillConsecutive, rowsPerGroup,
        globalRowOffset, fillCells, showCutLines, noSpacing, spacingPxOverride,
        spacingYOverride
    )

    fun createFullPageLayout(
        photo: Bitmap, pageWidthPx: Int, pageHeightPx: Int,
        marginPx: Int = 20, scaleToFill: Boolean = false,
        offsetXPx: Int = 0, offsetYPx: Int = 0, scaleMultiplier: Float = 1f,
        stretchLeft: Int = 0, stretchRight: Int = 0,
        stretchTop: Int = 0, stretchBottom: Int = 0
    ): Bitmap = com.hitbha.photo.utils.createFullPageLayout(
        photo, pageWidthPx, pageHeightPx, marginPx, scaleToFill,
        offsetXPx, offsetYPx, scaleMultiplier, stretchLeft, stretchRight,
        stretchTop, stretchBottom
    )

    fun colorEraseBitmap(bitmap: Bitmap, x: Float, y: Float, tolerance: Int = 40): Bitmap =
        com.hitbha.photo.utils.colorEraseBitmap(bitmap, x, y, tolerance)

    fun magicRemoveBitmap(bitmap: Bitmap, targetColor: Int, tolerance: Int = 40): Bitmap =
        com.hitbha.photo.utils.magicRemoveBitmap(bitmap, targetColor, tolerance)

    fun eraseBitmap(bitmap: Bitmap, points: List<Pair<Float, Float>>, brushRadius: Float): Bitmap =
        com.hitbha.photo.utils.eraseBitmap(bitmap, points, brushRadius)

    fun ensureAlphaChannel(bitmap: Bitmap): Bitmap =
        com.hitbha.photo.utils.ensureAlphaChannel(bitmap)

    fun featherEdges(bitmap: Bitmap, radius: Int): Bitmap =
        com.hitbha.photo.utils.featherEdges(bitmap, radius)

    fun fillBackground(bitmap: Bitmap, color: Int): Bitmap =
        com.hitbha.photo.utils.fillBackground(bitmap, color)

    fun cleanSemiTransparent(bitmap: Bitmap, threshold: Int = 30): Bitmap =
        com.hitbha.photo.utils.cleanSemiTransparent(bitmap, threshold)

    fun removeOutline(bitmap: Bitmap, removePx: Int = 0): Bitmap =
        com.hitbha.photo.utils.removeOutline(bitmap, removePx)

    fun autoCropContent(bitmap: Bitmap, tolerance: Int = 30): Bitmap =
        com.hitbha.photo.utils.autoCropContent(bitmap, tolerance)

    fun enhanceDocument(bitmap: Bitmap): Bitmap =
        com.hitbha.photo.utils.enhanceDocument(bitmap)

    fun isIdCard(bitmap: Bitmap): Boolean =
        com.hitbha.photo.utils.isIdCard(bitmap)

    fun binarize(bitmap: Bitmap, threshold: Int): Bitmap =
        com.hitbha.photo.utils.binarize(bitmap, threshold)

    fun processWithProfile(
        bitmap: Bitmap, enhanceContrast: Boolean,
        binarizeThreshold: Int?, sharpenAmount: Float, autoCrop: Boolean
    ): Bitmap = com.hitbha.photo.utils.processWithProfile(bitmap, enhanceContrast, binarizeThreshold, sharpenAmount, autoCrop)
}
