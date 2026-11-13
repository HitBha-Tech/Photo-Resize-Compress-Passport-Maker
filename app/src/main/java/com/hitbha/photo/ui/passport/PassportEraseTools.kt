package com.hitbha.photo.ui.passport

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.ui.editor.ToolMode
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.showLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PassportSizeActivity.startBrushEraser() {
    if (binding.eraseOverlay.toolMode == ToolMode.ERASER) {
        doneErase()
        return
    }
    saveEraseSnapshot()
    enterEraseMode(ToolMode.ERASER)
}

fun PassportSizeActivity.startColorEraser() {
    saveEraseSnapshot()
    enterEraseMode(ToolMode.COLOR_ERASE)
}

fun PassportSizeActivity.startMagicRemove() {
    if (binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE) {
        doneErase()
        return
    }
    saveEraseSnapshot()
    binding.eraseOverlay.resetMagicPhase()
    enterEraseMode(ToolMode.MAGIC_ERASE)
    Toast.makeText(this, R.string.magic_pick_color_hint, Toast.LENGTH_LONG).show()
}

private fun PassportSizeActivity.saveEraseSnapshot() {
    eraseSnapshot = currentBitmap?.copy(Bitmap.Config.ARGB_8888, true)
}

private fun PassportSizeActivity.enterEraseMode(mode: ToolMode) {
    setToolMode(mode)
    binding.groupCropChip.visibility = View.GONE
    binding.cropOverlay.visibility = View.GONE
}

private fun PassportSizeActivity.setToolMode(mode: ToolMode) {
    binding.eraseOverlay.toolMode = mode
    val brushVisible = mode == ToolMode.ERASER || mode == ToolMode.MAGIC_ERASE
    binding.btnBrushSize.visibility = if (brushVisible) View.VISIBLE else View.GONE
}

private fun PassportSizeActivity.exitEraseMode() {
    setToolMode(ToolMode.NONE)
    eraseSnapshot = null
}

fun PassportSizeActivity.exitEraseModeIfNeeded() {
    if (binding.eraseOverlay.toolMode == ToolMode.ERASER ||
        binding.eraseOverlay.toolMode == ToolMode.COLOR_ERASE ||
        binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE
    ) {
        doneErase()
    }
}

private fun PassportSizeActivity.doneErase() {
    binding.eraseOverlay.clearMask()
    exitEraseMode()
}

fun PassportSizeActivity.showBrushSizePopup(anchor: View) {
    val popupView = layoutInflater.inflate(R.layout.popup_brush_size, null)
    val seekBar = popupView.findViewById<SeekBar>(R.id.popup_brush_slider)
    val label = popupView.findViewById<TextView>(R.id.popup_brush_label)

    seekBar.progress = binding.eraseOverlay.brushRadius.toInt()
    label.text = binding.eraseOverlay.brushRadius.toInt().toString()

    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val r = progress.coerceAtLeast(5)
            label.text = r.toString()
            binding.eraseOverlay.brushRadius = r.toFloat()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    popupView.measure(
        View.MeasureSpec.UNSPECIFIED,
        View.MeasureSpec.UNSPECIFIED
    )
    val popupHeight = popupView.measuredHeight
    val anchorLocation = IntArray(2)
    anchor.getLocationOnScreen(anchorLocation)

    val popupWindow = PopupWindow(
        popupView,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )
    popupWindow.elevation = 10f
    popupWindow.isOutsideTouchable = true
    popupWindow.showAtLocation(
        anchor,
        Gravity.NO_GRAVITY,
        anchorLocation[0] - popupView.measuredWidth + anchor.width,
        anchorLocation[1] - popupHeight
    )
}

internal fun PassportSizeActivity.setupEraseOverlay(bitmap: Bitmap) {
    binding.imagePreview.post {
        val imageView = binding.imagePreview
        imageView.drawable ?: return@post

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale
        val left = (viewWidth - displayedWidth) / 2
        val top = (viewHeight - displayedHeight) / 2

        binding.eraseOverlay.setImageBounds(left, top, left + displayedWidth, top + displayedHeight, bitmap.width, bitmap.height)

        zoomMatrix.set(binding.imagePreview.imageMatrix)
        zoomScale = 1f

        eraseAccumulated.clear()

        binding.eraseOverlay.onErasePoints = { points ->
            eraseAccumulated.addAll(points)
            val values = FloatArray(9)
            zoomMatrix.getValues(values)
            val totalScale = kotlin.math.abs(values[Matrix.MSCALE_X])
            eraseRadiusAccumulated = if (totalScale > 0.001f) binding.eraseOverlay.brushRadius / totalScale else binding.eraseOverlay.brushRadius
        }
        binding.eraseOverlay.onEraseEnd = {
            when (binding.eraseOverlay.toolMode) {
                ToolMode.MAGIC_ERASE -> applyMagicRemove(binding.eraseOverlay.magicPickedColor)
                else -> if (eraseAccumulated.isNotEmpty()) {
                    applyBrushErase(eraseAccumulated.toList(), eraseRadiusAccumulated)
                }
            }
            eraseAccumulated.clear()
            binding.eraseOverlay.clearMask()
        }
        binding.eraseOverlay.onColorEraseTap = { x, y ->
            if (binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE) {
                currentBitmap?.let { bmp ->
                    val cx = x.toInt().coerceIn(0, bmp.width - 1)
                    val cy = y.toInt().coerceIn(0, bmp.height - 1)
                    val pixel = bmp.getPixel(cx, cy)
                    if (android.graphics.Color.alpha(pixel) > 0) {
                        binding.eraseOverlay.setMagicColor(pixel)
                        Toast.makeText(this, R.string.magic_color_picked_hint, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                applyColorEraseTap(x, y)
            }
        }
    }
}

private fun PassportSizeActivity.applyBrushErase(points: List<Pair<Float, Float>>, brushRadius: Float) {
    val idx = selectedPersonIndex
    val bitmap = currentBitmap ?: return
    saveUndoState()
    CoroutineScope(Dispatchers.Main).launch {
        val erased = withContext(Dispatchers.Default) {
            ImageProcessor.eraseBitmap(bitmap, points, brushRadius)
        }
        personBitmaps[idx] = erased
        updatePersonPreview()
        clearFullPageState()
        updatePersonSelector()
    }
}

private fun PassportSizeActivity.applyColorEraseTap(x: Float, y: Float) {
    val idx = selectedPersonIndex
    val bitmap = currentBitmap ?: return
    saveUndoState()
    binding.progressBar.showLoading(true)
    CoroutineScope(Dispatchers.Main).launch {
        val erased = withContext(Dispatchers.Default) {
            ImageProcessor.colorEraseBitmap(bitmap, x, y)
        }
        personBitmaps[idx] = erased
        updatePersonPreview()
        clearFullPageState()
        updatePersonSelector()
        binding.progressBar.showLoading(false)
    }
}

private fun PassportSizeActivity.applyMagicRemove(targetColor: Int) {
    if (android.graphics.Color.alpha(targetColor) == 0) return
    val idx = selectedPersonIndex
    val bitmap = currentBitmap ?: return
    saveUndoState()
    binding.progressBar.showLoading(true)
    CoroutineScope(Dispatchers.Main).launch {
        val removed = withContext(Dispatchers.Default) {
            ImageProcessor.magicRemoveBitmap(bitmap, targetColor)
        }
        personBitmaps[idx] = removed
        updatePersonPreview()
        clearFullPageState()
        updatePersonSelector()
        binding.progressBar.showLoading(false)
    }
}