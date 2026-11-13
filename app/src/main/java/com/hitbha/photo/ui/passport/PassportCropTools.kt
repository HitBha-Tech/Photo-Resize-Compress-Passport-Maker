package com.hitbha.photo.ui.passport

import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.utils.ImageProcessor

private fun PassportSizeActivity.setupCropOverlay(bitmap: Bitmap) {
    binding.imagePreview.post {
        val imageView = binding.imagePreview
        imageView.drawable ?: return@post

        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale

        val left = (viewWidth - displayedWidth) / 2
        val top = (viewHeight - displayedHeight) / 2

        binding.cropOverlay.setImageBounds(left, top, left + displayedWidth, top + displayedHeight, bitmap.width, bitmap.height)
    }
}

fun PassportSizeActivity.showCropMode() {
    val bitmap = currentBitmap ?: return
    resetZoom()
    binding.imagePreview.setImageBitmap(bitmap)
    setupCropOverlay(bitmap)
    binding.cropOverlay.visibility = View.VISIBLE
    binding.groupCropChip.visibility = View.VISIBLE
    binding.bottomActionBar.visibility = View.GONE
}

fun PassportSizeActivity.hideCropMode() {
    binding.cropOverlay.visibility = View.GONE
    binding.groupCropChip.visibility = View.GONE
    binding.bottomActionBar.visibility = View.VISIBLE
}

fun PassportSizeActivity.applyCrop() {
    clearFullPageState()
    val idx = selectedPersonIndex
    val bitmap = currentBitmap ?: return
    saveUndoState()
    val cropRect = binding.cropOverlay.getCropRect()
    val overlay = binding.cropOverlay

    val displayWidth = overlay.imageDisplayRight - overlay.imageDisplayLeft
    val displayHeight = overlay.imageDisplayBottom - overlay.imageDisplayTop
    val scaleX = bitmap.width.toFloat() / displayWidth
    val scaleY = bitmap.height.toFloat() / displayHeight

    val left = ((cropRect.left - overlay.imageDisplayLeft) * scaleX).toInt()
    val top = ((cropRect.top - overlay.imageDisplayTop) * scaleY).toInt()
    val right = ((cropRect.right - overlay.imageDisplayLeft) * scaleX).toInt()
    val bottom = ((cropRect.bottom - overlay.imageDisplayTop) * scaleY).toInt()

    if (right <= left || bottom <= top) return

    val cropped = ImageProcessor.cropBitmap(bitmap, left, top, right, bottom)
    personBitmaps[idx] = cropped
    updatePersonPreview()
    hideCropMode()
    resetZoom()
    updatePersonSelector()
    preFillBitmap = null
    Toast.makeText(this, R.string.crop_applied, Toast.LENGTH_SHORT).show()
}