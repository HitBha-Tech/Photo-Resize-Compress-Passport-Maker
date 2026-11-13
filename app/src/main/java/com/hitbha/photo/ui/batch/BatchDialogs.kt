package com.hitbha.photo.ui.batch

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.hitbha.photo.R
import com.hitbha.photo.databinding.DialogBatchCompressBinding
import com.hitbha.photo.databinding.DialogBatchResizeCmBinding
import com.hitbha.photo.databinding.DialogBatchResizeInchBinding
import com.hitbha.photo.databinding.DialogBatchResizeMmBinding
import com.hitbha.photo.databinding.DialogBatchResizePxBinding
import com.hitbha.photo.utils.ShareUtils
import com.hitbha.photo.utils.getColorAttr
import com.hitbha.photo.utils.performHapticClick


internal fun Activity.showBatchResizeOptionsMenu(anchor: View, onOption: (Int) -> Unit) {
    val popup = PopupMenu(this, anchor)
    popup.menu.add(0, 1, 0, getString(R.string.resize_options_pixels))
    popup.menu.add(0, 2, 0, getString(R.string.resize_options_mm))
    popup.menu.add(0, 3, 0, getString(R.string.resize_options_cm))
    popup.menu.add(0, 4, 0, getString(R.string.resize_options_inch))
    popup.setOnMenuItemClickListener { item -> onOption(item.itemId); true }
    popup.show()
}

internal fun Activity.showBatchResizeDialog(onApply: (width: Int, height: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit, onShare: (width: Int, height: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit) {
    val dialog = Dialog(this)
    val binding = DialogBatchResizePxBinding.inflate(layoutInflater)
    dialog.setContentView(binding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    var selectedFormatIndex = 0
    var selectedPaperSizeIndex = 0
    val paperRadioButtons = listOf(
        binding.rbPaperA4, binding.rbPaper4x6, binding.rbPaperLetter,
        binding.rbPaperA5, binding.rbPaperA3, binding.rbPaperCustom
    )

    fun updatePaperSelection(index: Int) {
        selectedPaperSizeIndex = index
        paperRadioButtons.forEachIndexed { i, rb -> rb.isChecked = i == index }
    }

    fun updateFormatSelection(index: Int) {
        selectedFormatIndex = index
        val cards = listOf(binding.cardFormatJpeg, binding.cardFormatPng, binding.cardFormatPdf)
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
        binding.layoutPaperSize.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    binding.cardFormatJpeg.setOnClickListener { updateFormatSelection(0) }
    binding.cardFormatPng.setOnClickListener { updateFormatSelection(1) }
    binding.cardFormatPdf.setOnClickListener { updateFormatSelection(2) }
    paperRadioButtons.forEachIndexed { i, rb -> rb.setOnClickListener { updatePaperSelection(i) } }

    binding.btnCancel.setOnClickListener { dialog.dismiss() }
    binding.btnApplyResize.setOnClickListener { view ->
        view.performHapticClick()
        val width = binding.etWidth.text.toString().toIntOrNull()
        val height = binding.etHeight.text.toString().toIntOrNull()
        if (width != null && height != null && width > 0 && height > 0) {
            val format = when (selectedFormatIndex) { 1 -> "png"; 2 -> "pdf"; else -> "jpeg" }
            val paperSize = if (selectedFormatIndex == 2) ShareUtils.PaperSize.values()[selectedPaperSizeIndex] else null
            dialog.dismiss()
            onApply(width, height, format, paperSize)
        } else {
            Toast.makeText(this, R.string.enter_valid_dimensions, Toast.LENGTH_SHORT).show()
        }
    }

    binding.btnShareResize.setOnClickListener { view ->
        view.performHapticClick()
        val width = binding.etWidth.text.toString().toIntOrNull()
        val height = binding.etHeight.text.toString().toIntOrNull()
        if (width != null && height != null && width > 0 && height > 0) {
            val format = when (selectedFormatIndex) { 1 -> "png"; 2 -> "pdf"; else -> "jpeg" }
            val paperSize = if (selectedFormatIndex == 2) ShareUtils.PaperSize.values()[selectedPaperSizeIndex] else null
            dialog.dismiss()
            onShare(width, height, format, paperSize)
        } else {
            Toast.makeText(this, R.string.enter_valid_dimensions, Toast.LENGTH_SHORT).show()
        }
    }

    dialog.show()
}

internal fun Activity.showBatchResizeCmDialog(onApply: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit, onShare: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit) =
    showBatchResizeUnitDialog(DialogBatchResizeCmBinding.inflate(layoutInflater).root, 2.54f, onApply, onShare)

internal fun Activity.showBatchResizeMmDialog(onApply: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit, onShare: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit) =
    showBatchResizeUnitDialog(DialogBatchResizeMmBinding.inflate(layoutInflater).root, 25.4f, onApply, onShare)

internal fun Activity.showBatchResizeInchDialog(onApply: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit, onShare: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit) =
    showBatchResizeUnitDialog(DialogBatchResizeInchBinding.inflate(layoutInflater).root, 1f, onApply, onShare)

private fun Activity.showBatchResizeUnitDialog(root: View, unitsPerInch: Float, onApply: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit, onShare: (widthPx: Int, heightPx: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit) {
    val dialog = Dialog(this)
    dialog.setContentView(root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val etWidth = root.findViewById<TextInputEditText>(R.id.etWidth)
    val etHeight = root.findViewById<TextInputEditText>(R.id.etHeight)
    val layoutPaperSize = root.findViewById<View>(R.id.layoutPaperSize)

    var selectedFormatIndex = 0
    var selectedDpiIndex = 0
    var selectedPaperSizeIndex = 0
    val dpiValues = intArrayOf(200, 300)
    val paperRadioButtons = listOf(
        root.findViewById<RadioButton>(R.id.rbPaperA4), root.findViewById<RadioButton>(R.id.rbPaper4x6),
        root.findViewById<RadioButton>(R.id.rbPaperLetter), root.findViewById<RadioButton>(R.id.rbPaperA5),
        root.findViewById<RadioButton>(R.id.rbPaperA3), root.findViewById<RadioButton>(R.id.rbPaperCustom)
    )

    fun updatePaperSelection(index: Int) {
        selectedPaperSizeIndex = index
        paperRadioButtons.forEachIndexed { i, rb -> rb.isChecked = i == index }
    }

    fun toPx(value: Float, dpi: Int): Int = Math.round(value * dpi / unitsPerInch)

    val formatCards = listOf(
        root.findViewById<MaterialCardView>(R.id.cardFormatJpeg),
        root.findViewById<MaterialCardView>(R.id.cardFormatPng),
        root.findViewById<MaterialCardView>(R.id.cardFormatPdf)
    )

    fun updateFormatSelection(index: Int) {
        selectedFormatIndex = index
        formatCards.forEachIndexed { i, card ->
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
        layoutPaperSize.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    formatCards[0].setOnClickListener { updateFormatSelection(0) }
    formatCards[1].setOnClickListener { updateFormatSelection(1) }
    formatCards[2].setOnClickListener { updateFormatSelection(2) }
    updateFormatSelection(0)
    paperRadioButtons.forEachIndexed { i, rb -> rb.setOnClickListener { updatePaperSelection(i) } }

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

    dpiCards[0].setOnClickListener { selectedDpiIndex = 0; updateDpiSelection() }
    dpiCards[1].setOnClickListener { selectedDpiIndex = 1; updateDpiSelection() }
    updateDpiSelection()

    fun applyUnitResize(share: Boolean) {
        val w = etWidth.text.toString().toFloatOrNull()
        val h = etHeight.text.toString().toFloatOrNull()
        if (w == null || h == null || w <= 0f || h <= 0f) {
            Toast.makeText(this, R.string.enter_valid_dimensions, Toast.LENGTH_SHORT).show()
            return
        }
        val format = when (selectedFormatIndex) { 1 -> "png"; 2 -> "pdf"; else -> "jpeg" }
        val widthPx = toPx(w, dpiValues[selectedDpiIndex])
        val heightPx = toPx(h, dpiValues[selectedDpiIndex])
        val paperSize = if (selectedFormatIndex == 2) ShareUtils.PaperSize.values()[selectedPaperSizeIndex] else null
        dialog.dismiss()
        if (share) onShare(widthPx, heightPx, format, paperSize)
        else onApply(widthPx, heightPx, format, paperSize)
    }

    root.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
    root.findViewById<MaterialButton>(R.id.btnApplyResize).setOnClickListener { view -> view.performHapticClick(); applyUnitResize(share = false) }
    root.findViewById<MaterialButton>(R.id.btnShareResize).setOnClickListener { view -> view.performHapticClick(); applyUnitResize(share = true) }

    dialog.show()
}

internal fun Activity.showBatchCompressDialog(onApply: (quality: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit, onShare: (quality: Int, format: String, paperSize: ShareUtils.PaperSize?) -> Unit) {
    val dialog = Dialog(this)
    val binding = DialogBatchCompressBinding.inflate(layoutInflater)
    dialog.setContentView(binding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    var selectedFormatIndex = 0
    var selectedPaperSizeIndex = 0
    val paperRadioButtons = listOf(
        binding.rbPaperA4, binding.rbPaper4x6, binding.rbPaperLetter,
        binding.rbPaperA5, binding.rbPaperA3, binding.rbPaperCustom
    )

    fun updatePaperSelection(index: Int) {
        selectedPaperSizeIndex = index
        paperRadioButtons.forEachIndexed { i, rb -> rb.isChecked = i == index }
    }

    fun updateFormatSelection(index: Int) {
        selectedFormatIndex = index
        val cards = listOf(binding.cardFormatJpeg, binding.cardFormatPng, binding.cardFormatPdf)
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
        binding.layoutPaperSize.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    binding.cardFormatJpeg.setOnClickListener { updateFormatSelection(0) }
    binding.cardFormatPng.setOnClickListener { updateFormatSelection(1) }
    binding.cardFormatPdf.setOnClickListener { updateFormatSelection(2) }
    paperRadioButtons.forEachIndexed { i, rb -> rb.setOnClickListener { updatePaperSelection(i) } }

    binding.seekQuality.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
            binding.tvQualityValue.text = getString(R.string.quality_value, progress)
        }
        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
    })
    binding.seekQuality.progress = 80

    binding.btnCancel.setOnClickListener { dialog.dismiss() }
    binding.btnApplyCompress.setOnClickListener { view ->
        view.performHapticClick()
        val quality = binding.seekQuality.progress
        val format = when (selectedFormatIndex) { 1 -> "png"; 2 -> "pdf"; else -> "jpeg" }
        val paperSize = if (selectedFormatIndex == 2) ShareUtils.PaperSize.values()[selectedPaperSizeIndex] else null
        dialog.dismiss()
        onApply(quality, format, paperSize)
    }

    binding.btnShareCompress.setOnClickListener { view ->
        view.performHapticClick()
        val format = when (selectedFormatIndex) { 1 -> "png"; 2 -> "pdf"; else -> "jpeg" }
        val quality = binding.seekQuality.progress
        val paperSize = if (selectedFormatIndex == 2) ShareUtils.PaperSize.values()[selectedPaperSizeIndex] else null
        dialog.dismiss()
        onShare(quality, format, paperSize)
    }

    dialog.show()
}
