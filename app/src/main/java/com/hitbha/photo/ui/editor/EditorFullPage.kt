package com.hitbha.photo.ui.editor

import android.app.Dialog
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.databinding.DialogFullPagePrintBinding
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.showLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun EditorActivity.showFullPagePrintDialog() {
    val activity = this
    if (viewModel.currentState?.currentBitmap == null) {
        Toast.makeText(this, R.string.add_image_first, Toast.LENGTH_SHORT).show()
        return
    }

    val dialog = Dialog(this)
    val dialogBinding = DialogFullPagePrintBinding.inflate(layoutInflater)
    dialog.setContentView(dialogBinding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    data class PaperOption(val id: Int, val rb: RadioButton, val widthInch: Float, val heightInch: Float)

    val paperOptions = listOf(
        PaperOption(0, dialogBinding.rbFullPageA4, 8.27f, 11.69f),
        PaperOption(1, dialogBinding.rbFullPageA3, 11.69f, 16.54f),
        PaperOption(2, dialogBinding.rbFullPageA5, 5.83f, 8.27f),
        PaperOption(3, dialogBinding.rbFullPage4x6, 4f, 6f),
        PaperOption(4, dialogBinding.rbFullPageLetter, 8.5f, 11f)
    )
    var selectedId = 0

    fun selectPaper(id: Int) {
        selectedId = id
        paperOptions.forEach { it.rb.isChecked = it.id == id }
    }
    paperOptions.forEach { it.rb.setOnClickListener { selectPaper(it.id) } }
    selectPaper(0)

    dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
    dialogBinding.btnGenerate.setOnClickListener { view ->
        view.performHapticClick()
        val opt = paperOptions.find { it.id == selectedId } ?: return@setOnClickListener
        val pageWidth = (opt.widthInch * ImageProcessor.PASSPORT_DPI).toInt()
        val pageHeight = (opt.heightInch * ImageProcessor.PASSPORT_DPI).toInt()
        dialog.dismiss()
        viewModel.currentState?.currentBitmap?.let { bitmap ->
            generateFullPage(bitmap, pageWidth, pageHeight, scaleToFill = false)
        }
    }

    dialog.show()
}

fun EditorActivity.generateFullPage(bitmap: Bitmap, pageWidth: Int, pageHeight: Int, scaleToFill: Boolean) {
    val activity = this
    val marginPx = 0
    binding.progressBar.showLoading(true)
    CoroutineScope(Dispatchers.Main).launch {
        val page = withContext(Dispatchers.IO) {
            ImageProcessor.createFullPageLayout(bitmap, pageWidth, pageHeight, marginPx, scaleToFill)
        }
        viewModel.setFullPageResult(page)

        binding.imagePreview.visibility = View.GONE
        val overlay = binding.fullpageOverlay
        overlay.visibility = View.VISIBLE
        overlay.setPageSize(pageWidth, pageHeight, marginPx, scaleToFill)
        overlay.setPhoto(bitmap)

        overlay.onOffsetChanged = { _, _ -> }
        overlay.onScaleChanged = { _ -> }

        overlay.onInteractionEnd = {
            CoroutineScope(Dispatchers.Main).launch {
                val updated = withContext(Dispatchers.IO) {
                    ImageProcessor.createFullPageLayout(
                        bitmap, pageWidth, pageHeight, marginPx, scaleToFill,
                        overlay.getPanOffsetX(), overlay.getPanOffsetY(), overlay.getScaleMultiplier(),
                        overlay.getStretchLeft(), overlay.getStretchRight(),
                        overlay.getStretchTop(), overlay.getStretchBottom()
                    )
                }
                viewModel.updateFullPageResult(updated)
            }
        }

        binding.progressBar.showLoading(false)
        Toast.makeText(activity, getString(R.string.layout_generated, 1, 1, 1), Toast.LENGTH_SHORT).show()
        Toast.makeText(activity, getString(R.string.fullpage_feature_info), Toast.LENGTH_LONG).show()
    }
}

fun EditorActivity.exitFullPageModeIfNeeded() {
    if (binding.fullpageOverlay.visibility != View.VISIBLE) return
    binding.fullpageOverlay.visibility = View.GONE
    binding.fullpageOverlay.onOffsetChanged = null
    binding.fullpageOverlay.onScaleChanged = null
    binding.fullpageOverlay.onInteractionEnd = null
    binding.fullpageOverlay.onInteractionStart = null
    binding.imagePreview.visibility = View.VISIBLE
    resetZoom()
    viewModel.currentState?.currentBitmap?.let { bmp ->
        binding.imagePreview.setImageBitmap(bmp)
        binding.imagePreview.post { setupEraseOverlay(bmp) }
    }
}