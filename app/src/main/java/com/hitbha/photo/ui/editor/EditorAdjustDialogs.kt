package com.hitbha.photo.ui.editor

import android.app.Dialog
import android.graphics.BitmapFactory
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.hitbha.photo.R
import com.hitbha.photo.databinding.DialogAdjustBinding
import com.hitbha.photo.databinding.DialogCompressBinding
import com.hitbha.photo.databinding.DialogResizeBinding
import com.hitbha.photo.databinding.DialogResizeCmBinding
import com.hitbha.photo.databinding.DialogResizeInchBinding
import com.hitbha.photo.databinding.DialogResizeMmBinding
import com.hitbha.photo.databinding.DialogRotateBinding
import com.hitbha.photo.utils.FileUtils
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.getColorAttr
import com.hitbha.photo.utils.performHapticClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun EditorActivity.showResizeDialog() {
    val dialog = Dialog(this)
    val dialogBinding = DialogResizeBinding.inflate(layoutInflater)
    dialog.setContentView(dialogBinding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val currentState = viewModel.currentState
    currentState?.currentBitmap?.let { bitmap ->
        dialogBinding.etWidth.setText(bitmap.width.toString())
        dialogBinding.etHeight.setText(bitmap.height.toString())
        dialogBinding.tvResolutionPreview.text = "Result: ${bitmap.width} x ${bitmap.height}"
    }

    dialogBinding.cbKeepAspectRatio.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            currentState?.currentBitmap?.let { bitmap ->
                val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: return@let
                val targetHeight = (bitmap.height.toFloat() / bitmap.width * width).toInt()
                dialogBinding.etHeight.setText(targetHeight.toString())
            }
        }
    }

    dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
    dialogBinding.btnApplyResize.setOnClickListener { view ->
        view.performHapticClick()
        val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: return@setOnClickListener
        val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: return@setOnClickListener
        val keepAspect = dialogBinding.cbKeepAspectRatio.isChecked
        viewModel.applyResize(width, height, keepAspect)
        dialog.dismiss()
    }

    dialog.show()
}

fun EditorActivity.showResizeOptionsMenu(anchor: View) {
    val popup = android.widget.PopupMenu(this, anchor)
    popup.menu.add(0, 1, 0, getString(R.string.resize_options_pixels))
    popup.menu.add(0, 2, 0, getString(R.string.resize_options_mm))
    popup.menu.add(0, 3, 0, getString(R.string.resize_options_cm))
    popup.menu.add(0, 4, 0, getString(R.string.resize_options_inch))
    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            1 -> showResizeDialog()
            2 -> showResizeMmDialog()
            3 -> showResizeCmDialog()
            4 -> showResizeInchDialog()
        }
        true
    }
    popup.show()
}

fun EditorActivity.showResizeMmDialog() =
    showResizeUnitDialog(DialogResizeMmBinding.inflate(layoutInflater).root, 25.4f)

fun EditorActivity.showResizeCmDialog() =
    showResizeUnitDialog(DialogResizeCmBinding.inflate(layoutInflater).root, 2.54f)

fun EditorActivity.showResizeInchDialog() =
    showResizeUnitDialog(DialogResizeInchBinding.inflate(layoutInflater).root, 1f)

private fun EditorActivity.showResizeUnitDialog(root: View, unitsPerInch: Float) {
    val dialog = Dialog(this)
    dialog.setContentView(root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val etWidth = root.findViewById<TextInputEditText>(R.id.etWidth)
    val etHeight = root.findViewById<TextInputEditText>(R.id.etHeight)
    val tvResultPreview = root.findViewById<TextView>(R.id.tvResultPreview)
    val cbKeepAspectRatio = root.findViewById<CheckBox>(R.id.cbKeepAspectRatio)
    val dpiValues = intArrayOf(200, 400)
    var selectedDpiIndex = 0

    fun toPx(value: Float, dpi: Int): Int = Math.round(value * dpi / unitsPerInch)

    val dpiCards = listOf(
        root.findViewById<MaterialCardView>(R.id.cardDpi300),
        root.findViewById<MaterialCardView>(R.id.cardDpi600)
    )

    fun updateDpiSelection() {
        dpiCards.forEachIndexed { i, card ->
            val isSelected = i == selectedDpiIndex
            val textView = card.getChildAt(0) as? TextView
            if (isSelected) {
                card.setCardBackgroundColor(getColorAttr(android.R.attr.colorPrimary))
                card.strokeWidth = 0
                textView?.setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnPrimary))
            } else {
                card.setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                card.strokeWidth = 1
                card.strokeColor = getColorAttr(com.google.android.material.R.attr.colorOutlineVariant)
                textView?.setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }
    }

    fun updatePreview() {
        val w = etWidth.text.toString().toFloatOrNull()
        val h = etHeight.text.toString().toFloatOrNull()
        val dpi = dpiValues[selectedDpiIndex]
        if (w != null && h != null && w > 0f && h > 0f) {
            tvResultPreview.text =
                getString(R.string.result_preview_unit, toPx(w, dpi), toPx(h, dpi), dpi)
        } else {
            tvResultPreview.setText(R.string.result_preview)
        }
    }

    dpiCards[0].setOnClickListener { selectedDpiIndex = 0; updateDpiSelection(); updatePreview() }
    dpiCards[1].setOnClickListener { selectedDpiIndex = 1; updateDpiSelection(); updatePreview() }
    updateDpiSelection()

    val previewWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { updatePreview() }
    }
    etWidth.addTextChangedListener(previewWatcher)
    etHeight.addTextChangedListener(previewWatcher)

    viewModel.currentState?.currentBitmap?.let { bitmap ->
        etWidth.setText(String.format(java.util.Locale.US, "%.2f", bitmap.width * unitsPerInch / 200f))
        etHeight.setText(String.format(java.util.Locale.US, "%.2f", bitmap.height * unitsPerInch / 200f))
        updatePreview()
    }

    cbKeepAspectRatio.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            viewModel.currentState?.currentBitmap?.let { bitmap ->
                val widthVal = etWidth.text.toString().toFloatOrNull() ?: return@let
                val targetHeight = Math.round(widthVal * 100f * bitmap.height / bitmap.width) / 100f
                etHeight.setText(targetHeight.toString())
            }
        }
    }

    root.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
    root.findViewById<MaterialButton>(R.id.btnApplyResize).setOnClickListener { view ->
        view.performHapticClick()
        val w = etWidth.text.toString().toFloatOrNull()
        val h = etHeight.text.toString().toFloatOrNull()
        if (w == null || h == null || w <= 0f || h <= 0f) {
            Toast.makeText(this, R.string.enter_valid_dimensions, Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val dpi = dpiValues[selectedDpiIndex]
        val keepAspect = cbKeepAspectRatio.isChecked
        viewModel.applyResize(toPx(w, dpi), toPx(h, dpi), keepAspect)
        dialog.dismiss()
    }

    dialog.show()
}

fun EditorActivity.showAdjustDialog() {
    val dialog = BottomSheetDialog(this)
    val dialogBinding = DialogAdjustBinding.inflate(layoutInflater)
    dialog.setContentView(dialogBinding.root)
    dialog.window?.setDimAmount(0f)

    val state = viewModel.currentState ?: return

    dialogBinding.seekBrightness.progress = (state.brightness + 100).coerceIn(0, 200)
    dialogBinding.tvBrightnessValue.text = state.brightness.toString()
    dialogBinding.seekContrast.progress = (state.contrast * 100 + 100).toInt().coerceIn(0, 200)
    dialogBinding.tvContrastValue.text = (state.contrast * 100).toInt().toString()
    dialogBinding.seekTemperature.progress = (state.temperature * 100 + 100).toInt().coerceIn(0, 200)
    dialogBinding.tvTemperatureValue.text = (state.temperature * 100).toInt().toString()
    dialogBinding.seekSharpness.progress = (state.sharpen * 100).toInt().coerceIn(0, 100)
    dialogBinding.tvSharpnessValue.text = (state.sharpen * 100).toInt().toString()

    dialogBinding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            val value = progress - 100
            dialogBinding.tvBrightnessValue.text = value.toString()
            viewModel.applyBrightness(value)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    dialogBinding.seekContrast.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            val value = (progress - 100) / 100f
            dialogBinding.tvContrastValue.text = (progress - 100).toString()
            viewModel.applyContrast(value)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    dialogBinding.seekTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            val value = (progress - 100) / 100f
            dialogBinding.tvTemperatureValue.text = (progress - 100).toString()
            viewModel.applyTemperature(value)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    dialogBinding.seekSharpness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            dialogBinding.tvSharpnessValue.text = progress.toString()
            viewModel.applySharpen(progress / 100f)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    dialogBinding.btnResetAdjust.setOnClickListener {
        viewModel.resetAdjustments()
        dialogBinding.seekBrightness.progress = 100
        dialogBinding.seekContrast.progress = 100
        dialogBinding.seekTemperature.progress = 100
        dialogBinding.seekSharpness.progress = 0
        dialogBinding.tvBrightnessValue.text = "0"
        dialogBinding.tvContrastValue.text = "0"
        dialogBinding.tvTemperatureValue.text = "0"
        dialogBinding.tvSharpnessValue.text = "0"
    }

    dialogBinding.btnApplyAdjust.setOnClickListener { view ->
        view.performHapticClick()
        viewModel.commitAdjustments()
        dialog.dismiss()
    }
    dialog.show()
}

fun EditorActivity.showCompressDialog() {
    val dialog = Dialog(this)
    val dialogBinding = DialogCompressBinding.inflate(layoutInflater)
    dialog.setContentView(dialogBinding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val currentBitmap = viewModel.currentState?.currentBitmap ?: return

    val originalSize = FileUtils.getFileSizeFromUri(this, selectedImageUri)
    dialogBinding.tvOriginalSize.text = "Original: ${FileUtils.formatFileSize(originalSize)}"

    val dialogScope = CoroutineScope(Dispatchers.Main)
    var sizeJob: Job? = null

    fun updateCompressedSize(quality: Int) {
        sizeJob?.cancel()
        sizeJob = dialogScope.launch {
            val compressedBytes = withContext(Dispatchers.IO) {
                ImageProcessor.compressBitmap(currentBitmap, quality)
            }
            val compressedSize = compressedBytes.size.toLong()
            dialogBinding.tvCompressedSize.text = "Compressed: ${FileUtils.formatFileSize(compressedSize)}"
            val savings = originalSize - compressedSize
            dialogBinding.tvStorageSaved.text = when {
                savings > 0 -> "Saved: ${FileUtils.formatFileSize(savings)} (${savings * 100 / maxOf(originalSize, 1L)}%)"
                savings < 0 -> "Larger by: ${FileUtils.formatFileSize(-savings)}"
                else -> "Same as original"
            }
        }
    }

    updateCompressedSize(dialogBinding.seekQuality.progress)

    dialogBinding.seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            dialogBinding.tvQualityValue.text = getString(R.string.quality_value, progress)
            updateCompressedSize(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    dialog.setOnDismissListener { dialogScope.cancel() }
    dialogBinding.btnCancelCompress.setOnClickListener { dialog.dismiss() }
    dialogBinding.btnApplyCompress.setOnClickListener { view ->
        view.performHapticClick()
        val quality = dialogBinding.seekQuality.progress
        dialogScope.launch {
            val compressedBytes = withContext(Dispatchers.IO) {
                ImageProcessor.compressBitmap(currentBitmap, quality)
            }
            val compressedBitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
            }
            if (compressedBitmap != null) {
                viewModel.setCompressedImage(compressedBitmap, quality)
            }
            dialog.dismiss()
        }
    }

    dialog.show()
}

fun EditorActivity.showRotateDialog() {
    val dialog = Dialog(this)
    val dialogBinding = DialogRotateBinding.inflate(layoutInflater)
    dialog.setContentView(dialogBinding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    var selectedOperation = -1

    val cards = listOf(
        dialogBinding.cardRotateMinus90,
        dialogBinding.cardRotate180,
        dialogBinding.cardRotate90,
        dialogBinding.cardMirrorH
    )

    fun updateOperationSelection(index: Int) {
        selectedOperation = index
        cards.forEachIndexed { i, card ->
            val isSelected = i == index
            val textView = card.getChildAt(0) as? TextView
            if (isSelected) {
                card.setCardBackgroundColor(getColorAttr(android.R.attr.colorPrimary))
                card.strokeWidth = 0
                textView?.setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnPrimary))
            } else {
                card.setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                card.strokeWidth = 1
                card.strokeColor = getColorAttr(com.google.android.material.R.attr.colorOutlineVariant)
                textView?.setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }
    }

    dialogBinding.cardRotateMinus90.setOnClickListener { updateOperationSelection(0) }
    dialogBinding.cardRotate180.setOnClickListener { updateOperationSelection(1) }
    dialogBinding.cardRotate90.setOnClickListener { updateOperationSelection(2) }
    dialogBinding.cardMirrorH.setOnClickListener { updateOperationSelection(3) }

    dialogBinding.btnApplyTransform.setOnClickListener { view ->
        view.performHapticClick()
        when (selectedOperation) {
            0 -> viewModel.applyRotation(-90f)
            1 -> viewModel.applyRotation(180f)
            2 -> viewModel.applyRotation(90f)
            3 -> viewModel.applyMirror(true)
        }
        dialog.dismiss()
    }
    dialog.show()
}
