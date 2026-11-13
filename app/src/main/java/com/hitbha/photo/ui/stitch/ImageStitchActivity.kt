package com.hitbha.photo.ui.stitch

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityImageStitchBinding
import com.hitbha.photo.databinding.DialogSaveBinding
import com.hitbha.photo.databinding.DialogShareBinding
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.FileUtils
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.ShareUtils
import com.hitbha.photo.utils.ThemeManager
import com.hitbha.photo.utils.getColorAttr
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.updateStatusBarStyle
import com.hitbha.photo.utils.setupWindowInsets
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageStitchActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private lateinit var binding: ActivityImageStitchBinding
    private val selectedScreenshots = mutableListOf<Uri>()
    private var stitchedBitmap: Bitmap? = null

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        uris.forEach { uri ->
            if (!selectedScreenshots.contains(uri)) {
                selectedScreenshots.add(uri)
            }
        }
        if (selectedScreenshots.size > 4) {
            selectedScreenshots.subList(4, selectedScreenshots.size).clear()
            Toast.makeText(this, R.string.max_4_photos, Toast.LENGTH_SHORT).show()
        }
        updatePreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityImageStitchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.toolbar, binding.bottomActionBar)

        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.image_stitch)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupClickListeners() {
        binding.btnSelectScreenshots.setOnClickListener {
            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnStitch.setOnClickListener { view ->
            view.performHapticClick()
            if (selectedScreenshots.size < 2) {
                Toast.makeText(this, R.string.select_at_least_2, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.rbGrid.isChecked && selectedScreenshots.size != 4) {
                Toast.makeText(this, R.string.grid_requires_4, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            stitchImages()
        }

        binding.btnSaveStitch.setOnClickListener { view ->
            view.performHapticClick()
            performSave()
        }

        binding.btnShareStitch.setOnClickListener { view ->
            view.performHapticClick()
            showShareOptions()
        }
    }

    private fun originalScreenshotsSize(): Long =
        selectedScreenshots.sumOf { FileUtils.getFileSizeFromUri(this, it) }

    private fun updatePreview() {
        binding.layoutScreenshotsPreview.removeAllViews()
        selectedScreenshots.forEachIndexed { index, uri ->
            val itemView = layoutInflater.inflate(R.layout.item_batch_image, binding.layoutScreenshotsPreview, false)
            val imageView: ImageView = itemView.findViewById(R.id.iv_image)
            val tvRemove: TextView = itemView.findViewById(R.id.tv_remove)
            Glide.with(this).load(uri).into(imageView)
            tvRemove.setOnClickListener {
                selectedScreenshots.removeAt(index)
                updatePreview()
                if (selectedScreenshots.isEmpty()) {
                    binding.ivStitchedResult.setImageDrawable(null)
                    binding.btnSaveStitch.isEnabled = false
                    binding.btnShareStitch.isEnabled = false
                }
            }
            binding.layoutScreenshotsPreview.addView(itemView)
        }
    }

    private fun stitchImages() {
        lifecycleScope.launch {
            binding.btnStitch.isEnabled = false
            val bitmaps = mutableListOf<Bitmap>()

            withContext(Dispatchers.IO) {
                selectedScreenshots.forEach { uri ->
                    val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }?.let { bmp ->
                        downscaleIfNeeded(bmp)
                    }
                    bitmap?.let { bitmaps.add(it) }
                }
            }

            if (bitmaps.isNotEmpty()) {
                stitchedBitmap = when {
                    binding.rbHorizontal.isChecked -> ImageProcessor.stitchScreenshotsHorizontal(bitmaps)
                    binding.rbGrid.isChecked -> ImageProcessor.stitchScreenshotsGrid(bitmaps)
                    else -> ImageProcessor.stitchScreenshotsVertical(bitmaps)
                }
                binding.ivStitchedResult.setImageBitmap(stitchedBitmap)
                binding.btnSaveStitch.isEnabled = true
                binding.btnShareStitch.isEnabled = true
            }

            binding.btnStitch.isEnabled = true
        }
    }

    private fun performSave() {
        val bitmap = stitchedBitmap
        if (bitmap == null) {
            Toast.makeText(this, R.string.no_image_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        showOutputDialog(
            bitmap = bitmap,
            originalSizeBytes = originalScreenshotsSize(),
            inflate = { DialogSaveBinding.inflate(it).root },
            applyButtonId = R.id.btnApplySave,
            onApply = { formatIndex, quality, fileName, paperSize ->
                when (formatIndex) {
                    0 -> saveAsJpeg(bitmap, quality, fileName)
                    1 -> saveAsPng(bitmap, quality, fileName)
                    2 -> saveAsPdf(bitmap, fileName, paperSize, quality)
                }
            }
        )
    }

    private fun showShareOptions() {
        val bitmap = stitchedBitmap
        if (bitmap == null) {
            Toast.makeText(this, R.string.no_image_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        showOutputDialog(
            bitmap = bitmap,
            originalSizeBytes = originalScreenshotsSize(),
            inflate = { DialogShareBinding.inflate(it).root },
            applyButtonId = R.id.btnApplyShare,
            onApply = { formatIndex, quality, fileName, paperSize ->
                val shareFormat = when (formatIndex) {
                    0 -> ShareUtils.Format.JPEG
                    1 -> ShareUtils.Format.PNG
                    2 -> ShareUtils.Format.PDF
                    else -> ShareUtils.Format.JPEG
                }
                shareBitmap(bitmap, shareFormat, fileName, paperSize, quality)
            }
        )
    }

    private fun showOutputDialog(
        bitmap: Bitmap,
        originalSizeBytes: Long,
        inflate: (LayoutInflater) -> View,
        applyButtonId: Int,
        onApply: (formatIndex: Int, quality: Int, fileName: String?, paperSize: ShareUtils.PaperSize?) -> Unit
    ) {
        val dialog = Dialog(this)
        val root = inflate(layoutInflater)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        val etFileName = root.findViewById<android.widget.EditText>(R.id.etFileName)
        val cardJpeg = root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardFormatJpeg)
        val cardPng = root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardFormatPng)
        val cardPdf = root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardFormatPdf)
        val layoutPaperSize = root.findViewById<View>(R.id.layoutPaperSize)
        val tvQualityValue = root.findViewById<TextView>(R.id.tvQualityValue)
        val tvOriginalSize = root.findViewById<TextView>(R.id.tvOriginalSize)
        val tvCompressedSize = root.findViewById<TextView>(R.id.tvCompressedSize)
        val seekQuality = root.findViewById<SeekBar>(R.id.seekQuality)
        val btnCancel = root.findViewById<View>(R.id.btnCancel)
        val btnApply = root.findViewById<View>(applyButtonId)
        val paperRadioButtons = listOf(
            root.findViewById<android.widget.RadioButton>(R.id.rbPaperA4),
            root.findViewById<android.widget.RadioButton>(R.id.rbPaper4x6),
            root.findViewById<android.widget.RadioButton>(R.id.rbPaperLetter),
            root.findViewById<android.widget.RadioButton>(R.id.rbPaperA5),
            root.findViewById<android.widget.RadioButton>(R.id.rbPaperA3),
            root.findViewById<android.widget.RadioButton>(R.id.rbPaperCustom)
        )

        var selectedFormatIndex = 0
        var selectedPaperSizeIndex = 0
        var selectedQuality = 80
        var sizeJob: Job? = null

        fun updateSizePreview() {
            tvQualityValue.text = "$selectedQuality%"
            sizeJob?.cancel()
            sizeJob = lifecycleScope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    if (selectedFormatIndex == 2) {
                        FileUtils.createPdfBytes(
                            bitmap,
                            dpi = 72f,
                            paperSize = ShareUtils.PaperSize.values().getOrNull(selectedPaperSizeIndex),
                            quality = selectedQuality
                        )
                    } else if (selectedFormatIndex == 1) {
                        ImageProcessor.encodePng(bitmap, selectedQuality)
                    } else {
                        ImageProcessor.compressBitmap(bitmap, selectedQuality, Bitmap.CompressFormat.JPEG)
                    }
                }
                tvCompressedSize.text = "Compressed: ${FileUtils.formatFileSize(bytes.size.toLong())}"
            }
        }

        fun updatePaperSelection(index: Int) {
            selectedPaperSizeIndex = index
            paperRadioButtons.forEachIndexed { i, rb -> rb.isChecked = i == index }
            if (selectedFormatIndex == 2) updateSizePreview()
        }

        fun updateFormatSelection(index: Int) {
            selectedFormatIndex = index
            val cards = listOf(cardJpeg, cardPng, cardPdf)
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
            layoutPaperSize.visibility = if (index == 2) View.VISIBLE else View.GONE
            updateSizePreview()
        }

        tvOriginalSize.text = "Original: ${FileUtils.formatFileSize(originalSizeBytes.takeIf { it > 0 } ?: bitmap.width.toLong() * bitmap.height * 4L)}"
        seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedQuality = progress.coerceIn(1, 100)
                updateSizePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        updateSizePreview()

        cardJpeg.setOnClickListener { updateFormatSelection(0) }
        cardPng.setOnClickListener { updateFormatSelection(1) }
        cardPdf.setOnClickListener { updateFormatSelection(2) }
        paperRadioButtons.forEachIndexed { i, rb -> rb.setOnClickListener { updatePaperSelection(i) } }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnApply.setOnClickListener { view ->
            view.performHapticClick()
            dialog.dismiss()
            val fileName = etFileName.text.toString().trim().ifEmpty { null }
            val quality = selectedQuality
            val paperSize = if (selectedFormatIndex == 2) ShareUtils.PaperSize.values()[selectedPaperSizeIndex] else null
            AdManager.showInterstitial(this@ImageStitchActivity) {
                onApply(selectedFormatIndex, quality, fileName, paperSize)
            }
        }

        dialog.show()
    }

    private fun saveAsJpeg(bitmap: Bitmap, quality: Int = 100, fileName: String? = null) {
        val uri = FileUtils.saveBitmapToGallery(this, bitmap, Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), fileName)
        if (uri != null) {
            Toast.makeText(this, R.string.jpg_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.failed_jpg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsPng(bitmap: Bitmap, quality: Int = 100, fileName: String? = null) {
        val png = if (quality < 100) ImageProcessor.scaleByQuality(bitmap, quality) else bitmap
        val uri = FileUtils.saveBitmapToGallery(this, png, Bitmap.CompressFormat.PNG, 100, fileName)
        if (uri != null) {
            Toast.makeText(this, R.string.image_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.failed_save, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsPdf(bitmap: Bitmap, fileName: String? = null, paperSize: ShareUtils.PaperSize? = null, quality: Int = 100) {
        val uri = FileUtils.savePdfToGallery(this, bitmap, fileName, paperSize = paperSize, quality = quality)
        if (uri != null) {
            Toast.makeText(this, R.string.pdf_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.failed_pdf, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareBitmap(bitmap: Bitmap, format: ShareUtils.Format, fileName: String? = null, paperSize: ShareUtils.PaperSize? = null, quality: Int = 100) {
        val mimeType = when (format) {
            ShareUtils.Format.JPEG -> "image/jpeg"
            ShareUtils.Format.PNG -> "image/png"
            ShareUtils.Format.PDF -> "application/pdf"
        }
        val failRes = when (format) {
            ShareUtils.Format.JPEG -> R.string.failed_share_jpeg
            ShareUtils.Format.PNG -> R.string.failed_share_png
            ShareUtils.Format.PDF -> R.string.failed_share_pdf
        }
        lifecycleScope.launch {
            val fileUri = withContext(Dispatchers.IO) {
                val source = if (format == ShareUtils.Format.PNG && quality < 100)
                    ImageProcessor.scaleByQuality(bitmap, quality)
                else
                    bitmap
                ShareUtils.createShareFile(this@ImageStitchActivity, source, format, "stitch", fileName, paperSize = paperSize, quality = quality)
            }
            if (fileUri != null) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, getString(R.string.share)))
            } else {
                Toast.makeText(this@ImageStitchActivity, failRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int = 1080): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
