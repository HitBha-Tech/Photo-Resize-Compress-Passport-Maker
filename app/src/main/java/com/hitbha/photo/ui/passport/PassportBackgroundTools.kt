package com.hitbha.photo.ui.passport

import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.bgremover.BackgroundRemover
import com.hitbha.photo.utils.showLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PassportSizeActivity.showBgTools(anchor: View) {
    if (currentBitmap == null) {
        Toast.makeText(this, R.string.add_image_first, Toast.LENGTH_SHORT).show()
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

fun PassportSizeActivity.removeBg() {
    val activity = this
    val bitmap = currentBitmap ?: return
    val idx = selectedPersonIndex
    binding.progressBar.showLoading(true)

    CoroutineScope(Dispatchers.Main).launch {
        try {
            val result = withContext(Dispatchers.Default) {
                val remover = BackgroundRemover(activity)
                val bgResult = remover.removeBackground(bitmap)
                val trimmed = remover.trimEmptyParts(bgResult.bitmap)
                var cleaned = ImageProcessor.cleanSemiTransparent(trimmed)
                cleaned = ImageProcessor.featherEdges(cleaned, 1)
                bgResult.copy(bitmap = cleaned)
            }
            saveUndoState()
            personBitmaps[idx] = result.bitmap
            clearFullPageState()
            updatePersonPreview()
            resetZoom()
            updatePersonSelector()
            binding.imagePreview.post {
                setupEraseOverlay(result.bitmap)
            }
            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Background removal failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            binding.progressBar.showLoading(false)
        }
    }
}