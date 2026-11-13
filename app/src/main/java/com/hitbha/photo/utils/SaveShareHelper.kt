package com.hitbha.photo.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SaveShareHelper {

    fun paperSizeAt(index: Int): ShareUtils.PaperSize? =
        ShareUtils.PaperSize.entries.getOrNull(index)

    data class FormatState(
        var formatIndex: Int = 0,
        var quality: Int = 100,
        var paperSizeIndex: Int = 0,
        var sizeJob: Job? = null
    )

    fun setupFormatDialog(
        activity: Activity,
        scope: CoroutineScope,
        cardFormatJpeg: MaterialCardView,
        cardFormatPng: MaterialCardView,
        cardFormatPdf: MaterialCardView,
        paperRadioButtons: List<RadioButton>,
        layoutPaperSize: View,
        seekQuality: SeekBar,
        tvQualityValue: TextView,
        tvOriginalSize: TextView,
        tvCompressedSize: TextView,
        etFileName: EditText,
        originalSizeBytes: Long,
        hasPageLayout: Boolean,
        initialPaperSizeIndex: Int,
        computePreviewSize: suspend (formatIndex: Int, quality: Int, paperSizeIndex: Int) -> ByteArray,
        onConfirm: (formatIndex: Int, quality: Int, paperSizeIndex: Int, fileName: String?) -> Unit,
        btnCancel: View,
        btnConfirm: View
    ): FormatState {
        val state = FormatState(paperSizeIndex = initialPaperSizeIndex)
        val cards = listOf(cardFormatJpeg, cardFormatPng, cardFormatPdf)

        fun updateSizePreview() {
            tvQualityValue.text = "${state.quality}%"
            state.sizeJob?.cancel()
            state.sizeJob = scope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    computePreviewSize(state.formatIndex, state.quality, state.paperSizeIndex)
                }
                tvCompressedSize.text = "Compressed: ${FileUtils.formatFileSize(bytes.size.toLong())}"
            }
        }

        fun updateFormatSelection(index: Int) {
            state.formatIndex = index
            cards.forEachIndexed { i, card ->
                val isSelected = i == index
                val textView = card.getChildAt(0) as? TextView
                if (isSelected) {
                    card.setCardBackgroundColor(activity.getColorAttr(android.R.attr.colorPrimary))
                    card.strokeWidth = 0
                    textView?.setTextColor(activity.getColorAttr(com.google.android.material.R.attr.colorOnPrimary))
                } else {
                    card.setCardBackgroundColor(activity.getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                    card.strokeWidth = 1
                    card.strokeColor = activity.getColorAttr(com.google.android.material.R.attr.colorOutlineVariant)
                    textView?.setTextColor(activity.getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                }
            }
            layoutPaperSize.visibility = if (!hasPageLayout && index == 2) View.VISIBLE else View.GONE
            updateSizePreview()
        }

        fun updatePaperSelection(index: Int) {
            state.paperSizeIndex = index
            paperRadioButtons.forEachIndexed { i, rb -> rb.isChecked = i == index }
            if (state.formatIndex == 2) updateSizePreview()
        }

        tvOriginalSize.text = "Original: ${FileUtils.formatFileSize(originalSizeBytes)}"
        seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                state.quality = progress.coerceIn(1, 100)
                updateSizePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekQuality.progress = 80
        state.quality = seekQuality.progress

        cardFormatJpeg.setOnClickListener { updateFormatSelection(0) }
        cardFormatPng.setOnClickListener { updateFormatSelection(1) }
        cardFormatPdf.setOnClickListener { updateFormatSelection(2) }
        paperRadioButtons.forEachIndexed { i, rb ->
            rb.isChecked = i == state.paperSizeIndex
            rb.setOnClickListener { updatePaperSelection(i) }
        }

        layoutPaperSize.visibility = if (!hasPageLayout && state.formatIndex == 2) View.VISIBLE else View.GONE
        updateSizePreview()

        btnCancel.setOnClickListener { (activity as? android.app.DialogFragment)?.dismiss() }
        btnConfirm.setOnClickListener { view ->
            view.performHapticClick()
            val fileName = etFileName.text.toString().trim().ifEmpty { null }
            onConfirm(state.formatIndex, state.quality, state.paperSizeIndex, fileName)
        }

        return state
    }

    suspend fun saveBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        formatIndex: Int,
        quality: Int,
        paperSize: ShareUtils.PaperSize?,
        fileName: String?,
        applyCompression: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        when (formatIndex) {
            0 -> FileUtils.saveBitmapsToGallery(context, bitmaps, Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), fileName).isNotEmpty()
            1 -> {
                val scaled = if (quality >= 100) bitmaps else bitmaps.map { ImageProcessor.scaleByQuality(it, quality) }
                FileUtils.saveBitmapsToGallery(context, scaled, Bitmap.CompressFormat.PNG, 100, fileName).isNotEmpty()
            }
            2 -> FileUtils.savePdfToGallery(context, bitmaps, fileName, paperSize = paperSize, quality = quality, applyCompression = applyCompression) != null
            else -> false
        }
    }

    fun computePreviewSize(bitmaps: List<Bitmap>, formatIndex: Int, quality: Int, paperSizeIndex: Int, applyCompression: Boolean = false): ByteArray {
        val paperSize = paperSizeAt(paperSizeIndex)
        return when (formatIndex) {
            2 -> FileUtils.createPdfBytes(bitmaps, paperSize = paperSize, quality = quality, applyCompression = applyCompression)
            1 -> ImageProcessor.encodePng(bitmaps.first(), quality)
            else -> ImageProcessor.compressBitmap(bitmaps.first(), quality.coerceIn(1, 100), Bitmap.CompressFormat.JPEG)
        }
    }

    fun computePreviewSize(bitmap: Bitmap, formatIndex: Int, quality: Int, paperSizeIndex: Int): ByteArray {
        val paperSize = paperSizeAt(paperSizeIndex)
        return when (formatIndex) {
            2 -> FileUtils.createPdfBytes(bitmap, paperSize = paperSize, quality = quality)
            1 -> ImageProcessor.encodePng(bitmap, quality)
            else -> ImageProcessor.compressBitmap(bitmap, quality.coerceIn(1, 100), Bitmap.CompressFormat.JPEG)
        }
    }

    suspend fun shareBitmaps(
        activity: Activity,
        bitmaps: List<Bitmap>,
        formatIndex: Int,
        quality: Int,
        paperSize: ShareUtils.PaperSize?,
        fileName: String?,
        prefix: String,
        dpi: Float = 72f,
        applyCompression: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val format = when (formatIndex) {
            0 -> ShareUtils.Format.JPEG
            1 -> ShareUtils.Format.PNG
            else -> ShareUtils.Format.PDF
        }
        val mimeType = when (format) {
            ShareUtils.Format.JPEG -> "image/jpeg"
            ShareUtils.Format.PNG -> "image/png"
            ShareUtils.Format.PDF -> "application/pdf"
        }
        val source = if (format == ShareUtils.Format.PNG && quality < 100)
            bitmaps.map { ImageProcessor.scaleByQuality(it, quality) }
        else
            bitmaps
        val uris = ShareUtils.createShareFiles(activity, source, format, prefix, fileName, dpi, paperSize, quality = quality, applyCompression = applyCompression)
        if (uris.isNotEmpty()) {
            val sendIntent = if (uris.size > 1) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            }
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(sendIntent, activity.getString(com.hitbha.photo.R.string.share)))
            true
        } else false
    }
}
