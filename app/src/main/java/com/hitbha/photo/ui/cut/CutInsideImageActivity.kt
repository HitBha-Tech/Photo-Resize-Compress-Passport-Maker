package com.hitbha.photo.ui.cut

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityCutInsideBinding
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
import com.hitbha.photo.utils.showLoading
import com.hitbha.photo.utils.updateStatusBarStyle
import com.hitbha.photo.utils.setupWindowInsets
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CutInsideImageActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private lateinit var binding: ActivityCutInsideBinding

    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var originalFileSizeBytes = 0L

    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()

    private var cutMode: CutMode = CutMode.HORIZONTAL

    enum class CutMode { HORIZONTAL, VERTICAL }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityCutInsideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.topBar, binding.bottomActionBar)

        setupToolbar()
        setupClickListeners()
        setupModeToggle()
        updateUndoRedoButtons()
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupClickListeners() {
        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnUndo.setOnClickListener { performUndo() }
        binding.btnRedo.setOnClickListener { performRedo() }
        binding.btnCut.setOnClickListener { view ->
            view.performHapticClick()
            performCut()
        }
        binding.btnSave.setOnClickListener { view ->
            view.performHapticClick()
            performSave()
        }
        binding.btnShare.setOnClickListener { view ->
            view.performHapticClick()
            shareImage()
        }
    }

    private fun setupModeToggle() {
        ArrayAdapter.createFromResource(
            this, R.array.cut_modes, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.cutModeSpinner.adapter = adapter
        }
        binding.cutModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                cutMode = when (position) {
                    0 -> CutMode.HORIZONTAL
                    else -> CutMode.VERTICAL
                }
                binding.rectangleOverlay.orientation = when (cutMode) {
                    CutMode.HORIZONTAL -> CutRectangleOverlayView.Orientation.HORIZONTAL
                    CutMode.VERTICAL -> CutRectangleOverlayView.Orientation.VERTICAL
                }
                currentBitmap?.let { setupCutOverlayForMode(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadImage(uri: Uri) {
        originalFileSizeBytes = FileUtils.getFileSizeFromUri(this, uri)
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val scaled = downscaleIfNeeded(resource)
                    originalBitmap = scaled
                    currentBitmap = scaled
                    binding.imagePreview.setImageBitmap(scaled)

                    undoStack.clear()
                    redoStack.clear()
                    updateUndoRedoButtons()
                    binding.bottomActionBar.visibility = View.VISIBLE

                    setupCutOverlayForMode(scaled)
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }

    private fun setupCutOverlayForMode(bitmap: Bitmap) {
        binding.imagePreview.post {
            val imageView = binding.imagePreview
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()
            val viewWidth = imageView.width.toFloat().coerceAtLeast(1f)
            val viewHeight = imageView.height.toFloat().coerceAtLeast(1f)

            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            val displayedWidth = imageWidth * scale
            val displayedHeight = imageHeight * scale
            val left = (viewWidth - displayedWidth) / 2
            val top = (viewHeight - displayedHeight) / 2
            val right = left + displayedWidth
            val bottom = top + displayedHeight

            binding.rectangleOverlay.setImageBounds(left, top, right, bottom)
        }
    }

    private fun performCut() {
        val bitmap = currentBitmap ?: return
        val params = binding.rectangleOverlay.getCutParams()

        var stripTop = 0
        var stripBottom = 0
        var stripLeft = 0
        var stripRight = 0

        if (cutMode == CutMode.HORIZONTAL) {
            val scaleY = bitmap.height.toFloat() / params.displayHeight
            stripTop = (params.top * scaleY).toInt().coerceIn(0, bitmap.height)
            stripBottom = (params.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
        } else {
            val scaleX = bitmap.width.toFloat() / params.displayWidth
            stripLeft = (params.left * scaleX).toInt().coerceIn(0, bitmap.width)
            stripRight = (params.right * scaleX).toInt().coerceIn(0, bitmap.width)
        }

        binding.progressBar.showLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                when (cutMode) {
                    CutMode.HORIZONTAL -> {
                        if (stripTop >= stripBottom) null
                        else ImageProcessor.cutHorizontalStripAndStitch(bitmap, stripTop, stripBottom)
                    }
                    CutMode.VERTICAL -> {
                        if (stripLeft >= stripRight) null
                        else ImageProcessor.cutVerticalStripAndStitch(bitmap, stripLeft, stripRight)
                    }
                }
            }

            binding.progressBar.showLoading(false)

            if (result == null) {
                Toast.makeText(this@CutInsideImageActivity, R.string.selection_too_small, Toast.LENGTH_SHORT).show()
                return@launch
            }

            pushUndoState(bitmap)
            currentBitmap = result
            binding.imagePreview.setImageBitmap(result)
            setupCutOverlayForMode(result)
            updateUndoRedoButtons()
        }
    }

    private fun pushUndoState(bitmap: Bitmap) {
        undoStack.add(bitmap)
        if (undoStack.size > 10) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        currentBitmap?.let { redoStack.add(it) }
        currentBitmap = previous
        binding.imagePreview.setImageBitmap(previous)
        setupCutOverlayForMode(previous)
        updateUndoRedoButtons()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        currentBitmap?.let { undoStack.add(it) }
        currentBitmap = next
        binding.imagePreview.setImageBitmap(next)
        setupCutOverlayForMode(next)
        updateUndoRedoButtons()
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = undoStack.isNotEmpty()
        binding.btnRedo.isEnabled = redoStack.isNotEmpty()
    }

    private fun performSave() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, R.string.no_image_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        showOutputDialog(
            bitmap = bitmap,
            originalSizeBytes = originalFileSizeBytes,
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

    private fun shareImage() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, R.string.no_image_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        showOutputDialog(
            bitmap = bitmap,
            originalSizeBytes = originalFileSizeBytes,
            inflate = { DialogShareBinding.inflate(it).root },
            applyButtonId = R.id.btnApplyShare,
            onApply = { formatIndex, quality, fileName, paperSize ->
                val shareFormat = when (formatIndex) {
                    0 -> ShareUtils.Format.JPEG
                    1 -> ShareUtils.Format.PNG
                    else -> ShareUtils.Format.PDF
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

        val etFileName = root.findViewById<EditText>(R.id.etFileName)
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
            root.findViewById<RadioButton>(R.id.rbPaperA4),
            root.findViewById<RadioButton>(R.id.rbPaper4x6),
            root.findViewById<RadioButton>(R.id.rbPaperLetter),
            root.findViewById<RadioButton>(R.id.rbPaperA5),
            root.findViewById<RadioButton>(R.id.rbPaperA3),
            root.findViewById<RadioButton>(R.id.rbPaperCustom)
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
            AdManager.showInterstitial(this@CutInsideImageActivity) {
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
                ShareUtils.createShareFile(this@CutInsideImageActivity, source, format, "cut", fileName, paperSize = paperSize, quality = quality)
            }
            if (fileUri != null) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, getString(R.string.share)))
            } else {
                Toast.makeText(this@CutInsideImageActivity, failRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int = 2048): Bitmap {
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
