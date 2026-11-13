package com.hitbha.photo.ui.passport

import android.app.Dialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.WindowManager
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.databinding.DialogSaveBinding
import com.hitbha.photo.databinding.DialogShareBinding
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.SaveShareHelper
import com.hitbha.photo.utils.performHapticClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun PassportSizeActivity.showSaveOptions() {
    if (!hasPageLayout) {
        Toast.makeText(this, R.string.no_layout, Toast.LENGTH_SHORT).show()
        return
    }
    val bitmaps = exportBitmaps()
    if (bitmaps.isEmpty()) {
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

    val previewBitmap = bitmaps.first()
    val initialPaperIndex = if (hasPageLayout) 0 else 5
    val initialPaperVisible = !hasPageLayout

    SaveShareHelper.setupFormatDialog(
        activity = this,
        scope = CoroutineScope(Dispatchers.Main),
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
        originalSizeBytes = originalFileSizeBytes.takeIf { it > 0 } ?: previewBitmap.width.toLong() * previewBitmap.height * 4L,
        hasPageLayout = hasPageLayout,
        initialPaperSizeIndex = initialPaperIndex,
        computePreviewSize = { fi, qi, pi -> SaveShareHelper.computePreviewSize(bitmaps, fi, qi, pi, applyCompression = true) },
        onConfirm = { fi, qi, pi, fn ->
            if (!isInternetAvailable()) {
                showNoInternetMessage()
                return@setupFormatDialog
            }
            dialog.dismiss()
            val paperSize = if (fi == 2 && !hasPageLayout) SaveShareHelper.paperSizeAt(pi) else null
            AdManager.showRewardedInterstitial(this) {
                when (fi) {
                    0 -> saveAsJpeg(bitmaps, qi, fn)
                    1 -> saveAsPng(bitmaps, qi, fn)
                    2 -> saveAsPdf(bitmaps, fn, paperSize, qi)
                }
            }
        },
        btnCancel = binding.btnCancel,
        btnConfirm = binding.btnApplySave
    )

    dialog.show()
}

fun PassportSizeActivity.showShareOptions() {
    if (!hasPageLayout) {
        Toast.makeText(this, R.string.no_layout, Toast.LENGTH_SHORT).show()
        return
    }
    val bitmaps = exportBitmaps()
    if (bitmaps.isEmpty()) {
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

    val previewBitmap = bitmaps.first()
    val initialPaperIndex = if (hasPageLayout) 0 else 5

    SaveShareHelper.setupFormatDialog(
        activity = this,
        scope = CoroutineScope(Dispatchers.Main),
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
        originalSizeBytes = originalFileSizeBytes.takeIf { it > 0 } ?: previewBitmap.width.toLong() * previewBitmap.height * 4L,
        hasPageLayout = hasPageLayout,
        initialPaperSizeIndex = initialPaperIndex,
        computePreviewSize = { fi, qi, pi -> SaveShareHelper.computePreviewSize(bitmaps, fi, qi, pi, applyCompression = true) },
        onConfirm = { fi, qi, pi, fn ->
            if (!isInternetAvailable()) {
                showNoInternetMessage()
                return@setupFormatDialog
            }
            dialog.dismiss()
            val paperSize = if (fi == 2 && !hasPageLayout) SaveShareHelper.paperSizeAt(pi) else null
            AdManager.showRewardedInterstitial(this) {
                CoroutineScope(Dispatchers.Main).launch {
                    val success = SaveShareHelper.shareBitmaps(this@showShareOptions, bitmaps, fi, qi, paperSize, fn, "passport", com.hitbha.photo.utils.ImageProcessor.PASSPORT_DPI, applyCompression = true)
                    if (!success) Toast.makeText(this@showShareOptions, R.string.failed_share, Toast.LENGTH_SHORT).show()
                }
            }
        },
        btnCancel = binding.btnCancel,
        btnConfirm = binding.btnApplyShare
    )

    dialog.show()
}

private fun PassportSizeActivity.isInternetAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun PassportSizeActivity.showNoInternetMessage() {
    Toast.makeText(this, R.string.no_internet, Toast.LENGTH_LONG).show()
}

fun PassportSizeActivity.saveAsJpeg(bitmaps: List<android.graphics.Bitmap>, quality: Int = 100, fileName: String? = null) {
    val uris = com.hitbha.photo.utils.FileUtils.saveBitmapsToGallery(this, bitmaps, android.graphics.Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), fileName)
    Toast.makeText(this, if (uris.isNotEmpty()) R.string.jpg_saved else R.string.failed_jpg, Toast.LENGTH_SHORT).show()
}

fun PassportSizeActivity.saveAsPng(bitmaps: List<android.graphics.Bitmap>, quality: Int = 100, fileName: String? = null) {
    val scaled = if (quality >= 100) bitmaps else bitmaps.map { com.hitbha.photo.utils.ImageProcessor.scaleByQuality(it, quality) }
    val uris = com.hitbha.photo.utils.FileUtils.saveBitmapsToGallery(this, scaled, android.graphics.Bitmap.CompressFormat.PNG, 100, fileName)
    Toast.makeText(this, if (uris.isNotEmpty()) R.string.image_saved else R.string.failed_save, Toast.LENGTH_SHORT).show()
}

fun PassportSizeActivity.saveAsPdf(bitmaps: List<android.graphics.Bitmap>, fileName: String? = null, paperSize: com.hitbha.photo.utils.ShareUtils.PaperSize? = null, quality: Int = 100) {
    val uri = com.hitbha.photo.utils.FileUtils.savePdfToGallery(this, bitmaps, fileName, paperSize = paperSize, quality = quality, applyCompression = true)
    Toast.makeText(this, if (uri != null) R.string.image_saved else R.string.failed_save, Toast.LENGTH_SHORT).show()
}
