package com.hitbha.photo.ui.editor

import android.view.View
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import com.hitbha.photo.R
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.bgremover.BackgroundRemover
import com.hitbha.photo.utils.showLoading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun EditorActivity.showBgTools(anchor: View) {
    if (viewModel.currentState?.currentBitmap == null) {
        android.widget.Toast.makeText(this, R.string.add_image_first, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val popup = PopupMenu(this, anchor)
    popup.menu.add(0, 1, 0, getString(R.string.bg_tools_remove_bg))
    popup.menu.add(0, 2, 0, getString(R.string.bg_tools_brush_eraser))
    popup.menu.add(0, 3, 0, getString(R.string.bg_tools_color_eraser))
    popup.menu.add(0, 4, 0, getString(R.string.bg_tools_magic_remove))
    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            1 -> removeBg()
            2 -> startBrushEraser()
            3 -> startColorEraser()
            4 -> startMagicRemove()
        }
        true
    }
    popup.show()
}

fun EditorActivity.removeBg() {
    val activity = this
    val bitmap = viewModel.currentState?.currentBitmap ?: return
    binding.progressBar.showLoading(true)
    setToolMode(ToolMode.NONE)

    activityScope.launch {
        try {
            val result = withContext(Dispatchers.Default) {
                val scaled = downscaleIfNeeded(bitmap)
                val remover = BackgroundRemover(activity)
                val bgResult = remover.removeBackground(scaled)
                val trimmed = remover.trimEmptyParts(bgResult.bitmap)
                var cleaned = ImageProcessor.cleanSemiTransparent(trimmed)
                cleaned = ImageProcessor.featherEdges(cleaned, 1)
                bgResult.copy(bitmap = cleaned)
            }
            viewModel.applyBgRemoveResult(result.bitmap)
            binding.imagePreview.post {
                resetZoom()
                setupEraseOverlay(result.bitmap)
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(activity, "Background removal failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        } finally {
            binding.progressBar.showLoading(false)
        }
    }
}

fun EditorActivity.startBrushEraser() {
    if (binding.eraseOverlay.toolMode == ToolMode.ERASER) {
        doneErase()
        return
    }
    saveEraseSnapshot()
    enterEraseMode(ToolMode.ERASER)
}

fun EditorActivity.startColorEraser() {
    saveEraseSnapshot()
    enterEraseMode(ToolMode.COLOR_ERASE)
}

fun EditorActivity.startMagicRemove() {
    if (binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE) {
        doneErase()
        return
    }
    saveEraseSnapshot()
    binding.eraseOverlay.resetMagicPhase()
    enterEraseMode(ToolMode.MAGIC_ERASE)
    android.widget.Toast.makeText(this, R.string.magic_pick_color_hint, android.widget.Toast.LENGTH_LONG).show()
}

private fun EditorActivity.saveEraseSnapshot() {
    eraseSnapshot = viewModel.currentState?.currentBitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
}

private fun EditorActivity.enterEraseMode(mode: ToolMode) {
    setToolMode(mode)
}

private fun EditorActivity.exitEraseMode() {
    setToolMode(ToolMode.NONE)
    eraseSnapshot?.recycle()
    eraseSnapshot = null
}

fun EditorActivity.showBrushSizePopup(anchor: View) {
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
        android.view.View.MeasureSpec.UNSPECIFIED,
        android.view.View.MeasureSpec.UNSPECIFIED
    )
    val popupHeight = popupView.measuredHeight
    val anchorLocation = IntArray(2)
    anchor.getLocationOnScreen(anchorLocation)

    val popupWindow = PopupWindow(
        popupView,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )
    popupWindow.elevation = 10f
    popupWindow.isOutsideTouchable = true
    popupWindow.showAtLocation(
        anchor,
        android.view.Gravity.NO_GRAVITY,
        anchorLocation[0] - popupView.measuredWidth + anchor.width,
        anchorLocation[1] - popupHeight
    )
}

fun EditorActivity.exitEraseModeIfNeeded() {
    if (binding.eraseOverlay.toolMode == ToolMode.ERASER ||
        binding.eraseOverlay.toolMode == ToolMode.COLOR_ERASE ||
        binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE
    ) {
        doneErase()
    }
}

private fun EditorActivity.doneErase() {
    binding.eraseOverlay.clearMask()
    exitEraseMode()
}

private fun EditorActivity.cancelErase() {
    binding.eraseOverlay.clearMask()
    eraseSnapshot?.let { snapshot ->
        viewModel.restoreSnapshot(snapshot)
        binding.imagePreview.setImageBitmap(snapshot)
    }
    exitEraseMode()
}
