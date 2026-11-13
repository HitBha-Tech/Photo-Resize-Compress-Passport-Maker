package com.hitbha.photo.ui.editor

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.databinding.DialogSaveBinding
import com.hitbha.photo.databinding.DialogShareBinding
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.FileUtils
import com.hitbha.photo.utils.SaveShareHelper
import com.hitbha.photo.utils.ShareUtils
import com.hitbha.photo.utils.performHapticClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun EditorActivity.saveImage() {
    val state = viewModel.currentState
    val bitmap = state?.currentBitmap
    if (bitmap == null) {
        Toast.makeText(this, R.string.add_image_first, Toast.LENGTH_SHORT).show()
        return
    }

    val dialog = Dialog(this)
    val binding = DialogSaveBinding.inflate(layoutInflater)
    dialog.setContentView(binding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val originalBytes = FileUtils.getFileSizeFromUri(this, selectedImageUri).takeIf { it > 0 }
        ?: bitmap.width.toLong() * bitmap.height * 4L

    val formatState = SaveShareHelper.setupFormatDialog(
        activity = this,
        scope = activityScope,
        cardFormatJpeg = binding.cardFormatJpeg,
        cardFormatPng = binding.cardFormatPng,
        cardFormatPdf = binding.cardFormatPdf,
        paperRadioButtons = listOf(binding.rbPaperA4, binding.rbPaper4x6, binding.rbPaperLetter, binding.rbPaperA5, binding.rbPaperA3, binding.rbPaperCustom),
        layoutPaperSize = binding.layoutPaperSize,
        seekQuality = binding.seekQuality,
        tvQualityValue = binding.tvQualityValue,
        tvOriginalSize = binding.tvOriginalSize,
        tvCompressedSize = binding.tvCompressedSize,
        etFileName = binding.etFileName,
        originalSizeBytes = originalBytes,
        hasPageLayout = false,
        initialPaperSizeIndex = 0,
        computePreviewSize = { fi, qi, pi -> SaveShareHelper.computePreviewSize(bitmap, fi, qi, pi) },
        onConfirm = { fi, qi, pi, fn ->
            dialog.dismiss()
            val paperSize = if (fi == 2) SaveShareHelper.paperSizeAt(pi) else null
            AdManager.showInterstitial(this) {
                when (fi) {
                    0 -> saveAsJpeg(bitmap, qi, fn)
                    1 -> saveAsPng(bitmap, qi, fn)
                    2 -> saveAsPdf(bitmap, fn, paperSize, qi)
                }
            }
        },
        btnCancel = binding.btnCancel,
        btnConfirm = binding.btnApplySave
    )

    dialog.show()
}

fun EditorActivity.saveAsJpeg(bitmap: Bitmap, quality: Int = 100, fileName: String? = null) {
    activityScope.launch {
        val uri = withContext(Dispatchers.IO) { FileUtils.saveBitmapToGallery(this@saveAsJpeg, bitmap, Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), fileName) }
        Toast.makeText(this@saveAsJpeg, if (uri != null) R.string.image_saved else R.string.failed_save, Toast.LENGTH_SHORT).show()
    }
}

fun EditorActivity.saveAsPng(bitmap: Bitmap, quality: Int = 100, fileName: String? = null) {
    activityScope.launch {
        val png = withContext(Dispatchers.IO) { if (quality < 100) com.hitbha.photo.utils.ImageProcessor.scaleByQuality(bitmap, quality) else bitmap }
        val uri = withContext(Dispatchers.IO) { FileUtils.saveBitmapToGallery(this@saveAsPng, png, Bitmap.CompressFormat.PNG, 100, fileName) }
        Toast.makeText(this@saveAsPng, if (uri != null) R.string.image_saved else R.string.failed_save, Toast.LENGTH_SHORT).show()
    }
}

fun EditorActivity.saveAsPdf(bitmap: Bitmap, fileName: String? = null, paperSize: ShareUtils.PaperSize? = null, quality: Int = 100) {
    activityScope.launch {
        val uri = withContext(Dispatchers.IO) { FileUtils.savePdfToGallery(this@saveAsPdf, bitmap, fileName, paperSize = paperSize, quality = quality) }
        Toast.makeText(this@saveAsPdf, if (uri != null) R.string.image_saved else R.string.failed_save, Toast.LENGTH_SHORT).show()
    }
}

fun EditorActivity.shareImage() {
    val state = viewModel.currentState
    val bitmap = state?.currentBitmap
    if (bitmap == null) {
        Toast.makeText(this, R.string.add_image_first, Toast.LENGTH_SHORT).show()
        return
    }

    val dialog = Dialog(this)
    val binding = DialogShareBinding.inflate(layoutInflater)
    dialog.setContentView(binding.root)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val originalBytes = FileUtils.getFileSizeFromUri(this, selectedImageUri).takeIf { it > 0 }
        ?: bitmap.width.toLong() * bitmap.height * 4L

    val formatState = SaveShareHelper.setupFormatDialog(
        activity = this,
        scope = activityScope,
        cardFormatJpeg = binding.cardFormatJpeg,
        cardFormatPng = binding.cardFormatPng,
        cardFormatPdf = binding.cardFormatPdf,
        paperRadioButtons = listOf(binding.rbPaperA4, binding.rbPaper4x6, binding.rbPaperLetter, binding.rbPaperA5, binding.rbPaperA3, binding.rbPaperCustom),
        layoutPaperSize = binding.layoutPaperSize,
        seekQuality = binding.seekQuality,
        tvQualityValue = binding.tvQualityValue,
        tvOriginalSize = binding.tvOriginalSize,
        tvCompressedSize = binding.tvCompressedSize,
        etFileName = binding.etFileName,
        originalSizeBytes = originalBytes,
        hasPageLayout = false,
        initialPaperSizeIndex = 0,
        computePreviewSize = { fi, qi, pi -> SaveShareHelper.computePreviewSize(bitmap, fi, qi, pi) },
        onConfirm = { fi, qi, pi, fn ->
            dialog.dismiss()
            val paperSize = if (fi == 2) SaveShareHelper.paperSizeAt(pi) else null
            AdManager.showInterstitial(this) {
                activityScope.launch {
                    val success = SaveShareHelper.shareBitmaps(this@shareImage, listOf(bitmap), fi, qi, paperSize, fn, "editor")
                    if (!success) Toast.makeText(this@shareImage, R.string.failed_share, Toast.LENGTH_SHORT).show()
                }
            }
        },
        btnCancel = binding.btnCancel,
        btnConfirm = binding.btnApplyShare
    )

    dialog.show()
}
