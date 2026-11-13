package com.hitbha.photo.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityEditorBinding
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.StatePersistence
import com.hitbha.photo.utils.ThemeManager
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.setupWindowInsets
import com.hitbha.photo.utils.showLoading
import com.hitbha.photo.utils.updateStatusBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class EditorActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    internal lateinit var binding: ActivityEditorBinding
    internal val viewModel: EditorViewModel by viewModels()
    internal val activityScope = CoroutineScope(Dispatchers.Main + Job())
    internal var selectedImageUri: Uri? = null

    private var zoomScale = 1f
    private var maxZoomScale = 5f
    private var minZoomScale = 1f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val zoomMatrix = Matrix()
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    internal var eraseSnapshot: Bitmap? = null
    private val baseMatrix = Matrix()
    private var checkerboardBitmap: Bitmap? = null

    internal var stampBitmap: Bitmap? = null
    internal var stampImageLeft = 0f
    internal var stampImageTop = 0f
    internal var stampImageRight = 0f
    internal var stampImageBottom = 0f

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedImageUri = it
            loadImage(it)
        }
    }

    internal val pickStampLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { loadStampImage(it) }
    }

    data class ColorOption(val argb: Int, val name: String, val isOpaque: Boolean = true)

    internal val colorOptions = listOf(
        ColorOption(android.graphics.Color.WHITE, "White"),
        ColorOption(android.graphics.Color.BLACK, "Black"),
        ColorOption(android.graphics.Color.parseColor("#FFF44336"), "Red"),
        ColorOption(android.graphics.Color.parseColor("#FF4CAF50"), "Green"),
        ColorOption(android.graphics.Color.parseColor("#FF2196F3"), "Blue"),
        ColorOption(android.graphics.Color.parseColor("#FFFFEB3B"), "Yellow"),
        ColorOption(android.graphics.Color.parseColor("#FF00BCD4"), "Cyan"),
        ColorOption(android.graphics.Color.parseColor("#FFE91E63"), "Pink"),
        ColorOption(android.graphics.Color.parseColor("#FFFF9800"), "Orange"),
        ColorOption(android.graphics.Color.parseColor("#FF9E9E9E"), "Gray"),
        ColorOption(android.graphics.Color.parseColor("#FF9C27B0"), "Purple"),
        ColorOption(android.graphics.Color.parseColor("#FF3F51B5"), "Indigo"),
        ColorOption(android.graphics.Color.parseColor("#FF009688"), "Teal"),
        ColorOption(android.graphics.Color.parseColor("#FF607D8B"), "Blue Grey")
    )

    internal var selectedFillColor: Int? = android.graphics.Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.topBar, binding.bottomActionBar)

        setupToolbar()
        setupObservers()
        setupClickListeners()
        setupZoom()
        setupCheckerboardBackground()
        AdManager.loadInterstitial(this)
        if (viewModel.restoreStateFromCache(this)) {
            viewModel.currentState?.currentBitmap?.let { bmp ->
                binding.imagePreview.post { setupEraseOverlay(bmp) }
            }
        }
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupObservers() {
        viewModel.editorState.observe(this) { state ->
            state.currentBitmap?.let { bitmap ->
                binding.imagePreview.setImageBitmap(bitmap)
                binding.btnPickImage.text = getString(R.string.select)
            }
            val hasImage = state.currentBitmap != null
            binding.btnUndo.isEnabled = hasImage && (viewModel.canUndo.value ?: false)
            binding.btnRedo.isEnabled = hasImage && (viewModel.canRedo.value ?: false)
            binding.btnCrop.isEnabled = hasImage
            binding.btnRemoveBg.isEnabled = hasImage
            binding.btnFinalTouches.isEnabled = hasImage
            binding.btnPage.isEnabled = hasImage
            binding.btnResize.isEnabled = hasImage
            binding.btnCompress.isEnabled = hasImage
            binding.btnAdjust.isEnabled = hasImage
            binding.btnRotate.isEnabled = hasImage
            binding.btnSave.isEnabled = hasImage
            binding.btnShare.isEnabled = hasImage
        }

        viewModel.processing.observe(this) { isProcessing ->
            binding.progressBar.showLoading(isProcessing)
        }

        viewModel.canUndo.observe(this) { canUndo ->
            binding.btnUndo.isEnabled = canUndo
        }

        viewModel.canRedo.observe(this) { canRedo ->
            binding.btnRedo.isEnabled = canRedo
        }

        viewModel.message.observe(this) { msg ->
            msg?.let {
                android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnPickImage.setOnClickListener {
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnResize.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            showResizeOptionsMenu(view)
        }
        binding.btnCrop.setOnClickListener {
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            showCropMode()
        }
        binding.btnAdjust.setOnClickListener {
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            showAdjustDialog()
        }
        binding.btnCompress.setOnClickListener {
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            showCompressDialog()
        }
        binding.btnRotate.setOnClickListener {
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            showRotateDialog()
        }
        binding.btnSave.setOnClickListener { view ->
            exitFullPageModeIfNeeded()
            view.performHapticClick()
            saveImage()
        }
        binding.btnShare.setOnClickListener { view ->
            exitFullPageModeIfNeeded()
            view.performHapticClick()
            shareImage()
        }

        binding.btnPage.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            view.performHapticClick()
            showFullPagePrintDialog()
        }

        binding.btnRemoveBg.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            view.performHapticClick()
            showBgTools(view)
        }

        binding.btnFinalTouches.setOnClickListener {
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            showFinalTouchesDialog()
        }

        binding.chipCancelCrop.setOnClickListener { hideCropMode() }
        binding.chipApplyCrop.setOnClickListener { view ->
            view.performHapticClick()
            applyCrop()
        }

        binding.btnUndo.setOnClickListener {
            exitFullPageModeIfNeeded()
            viewModel.undo()
        }
        binding.btnRedo.setOnClickListener {
            exitFullPageModeIfNeeded()
            viewModel.redo()
        }

        binding.btnBrushSize.setOnClickListener { showBrushSizePopup(it) }

        binding.btnAddStamp.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            exitFullPageModeIfNeeded()
            if (binding.stampOverlay.visibility == View.VISIBLE) {
                applyStamp()
            } else {
                showAddPageOrStampDialog()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.currentState?.currentBitmap != null) {
            viewModel.saveStateToCache(this)
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        binding.imageContainer.background = null
        checkerboardBitmap?.recycle()
        checkerboardBitmap = null
        super.onDestroy()
    }

    internal fun setToolMode(mode: ToolMode) {
        binding.eraseOverlay.toolMode = mode

        val brushVisible = mode == ToolMode.ERASER || mode == ToolMode.MAGIC_ERASE
        binding.btnBrushSize.visibility = if (brushVisible) View.VISIBLE else View.GONE
    }

    private fun setupCheckerboardBackground() {
        val size = 16
        val bmp = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        checkerboardBitmap = bmp
        val canvas = android.graphics.Canvas(bmp)
        val light = android.graphics.Color.argb(255, 220, 220, 220)
        val dark = android.graphics.Color.argb(255, 200, 200, 200)
        val paint = android.graphics.Paint()
        for (y in 0 until 2) {
            for (x in 0 until 2) {
                paint.color = if ((x + y) % 2 == 0) light else dark
                canvas.drawRect((x * size).toFloat(), (y * size).toFloat(), ((x + 1) * size).toFloat(), ((y + 1) * size).toFloat(), paint)
            }
        }
        val drawable = BitmapDrawable(resources, bmp)
        drawable.setTileModeX(Shader.TileMode.REPEAT)
        drawable.setTileModeY(Shader.TileMode.REPEAT)
        binding.imageContainer.background = drawable
    }

    private fun setupZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (zoomScale * detector.scaleFactor).coerceIn(minZoomScale, maxZoomScale)
                val factor = newScale / zoomScale
                zoomScale = newScale
                zoomMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                applyZoom()
                return true
            }
        })

        binding.imageContainer.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (binding.eraseOverlay.toolMode == ToolMode.NONE && viewModel.currentState?.currentBitmap != null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount == 1) {
                            val dx = event.x - lastTouchX
                            val dy = event.y - lastTouchY
                            lastTouchX = event.x
                            lastTouchY = event.y
                            zoomMatrix.postTranslate(dx, dy)
                            applyZoom()
                        }
                    }
                }
                true
            } else {
                viewModel.currentState?.let {
                    val mode = binding.eraseOverlay.toolMode
                    mode == ToolMode.ERASER || mode == ToolMode.COLOR_ERASE || mode == ToolMode.MAGIC_ERASE
                } ?: true
            }
        }
    }

    private fun applyZoom() {
        binding.imagePreview.scaleType = ImageView.ScaleType.MATRIX
        binding.imagePreview.imageMatrix = zoomMatrix
        updateEraseOverlayBounds()
    }

    private fun updateEraseOverlayBounds() {
        val bitmap = viewModel.currentState?.currentBitmap ?: return
        val matrix = if (binding.imagePreview.scaleType == ImageView.ScaleType.MATRIX)
            zoomMatrix else binding.imagePreview.imageMatrix
        val pts = floatArrayOf(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapPoints(pts)
        binding.eraseOverlay.setImageBounds(pts[0], pts[1], pts[2], pts[3], bitmap.width, bitmap.height)
    }

    internal fun resetZoom() {
        zoomScale = 1f
        zoomMatrix.reset()
        binding.imagePreview.scaleType = ImageView.ScaleType.FIT_CENTER
        binding.imagePreview.imageMatrix = Matrix()
    }

    private fun loadImage(uri: Uri) {
        StatePersistence.clearEditorState(this)
        exitFullPageModeIfNeeded()
        resetZoom()
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {
                    val scaled = downscaleIfNeeded(resource)
                    viewModel.setOriginalImage(scaled)
                    setupCropOverlay(scaled)
                    binding.imagePreview.post { setupEraseOverlay(scaled) }
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }

    internal fun setupCropOverlay(bitmap: Bitmap) {
        binding.imagePreview.post {
            val imageView = binding.imagePreview
            imageView.drawable ?: run { return@post }

            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()

            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            val displayedWidth = imageWidth * scale
            val displayedHeight = imageHeight * scale

            val left = (viewWidth - displayedWidth) / 2
            val top = (viewHeight - displayedHeight) / 2
            val right = left + displayedWidth
            val bottom = top + displayedHeight

            binding.cropOverlay.setImageBounds(left, top, right, bottom, bitmap.width, bitmap.height)
        }
    }

    internal fun setupEraseOverlay(bitmap: Bitmap) {
        binding.imagePreview.post {
            val imageView = binding.imagePreview
            imageView.drawable ?: return@post

            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            val displayedWidth = imageWidth * scale
            val displayedHeight = imageHeight * scale
            val left = (viewWidth - displayedWidth) / 2
            val top = (viewHeight - displayedHeight) / 2

            binding.eraseOverlay.setImageBounds(left, top, left + displayedWidth, top + displayedHeight, bitmap.width, bitmap.height)

            zoomMatrix.set(binding.imagePreview.imageMatrix)
            zoomScale = 1f

            val eraseAccumulated = mutableListOf<Pair<Float, Float>>()
            var eraseRadius = 0f

            binding.eraseOverlay.onErasePoints = { points ->
                eraseAccumulated.addAll(points)
                val values = FloatArray(9)
                zoomMatrix.getValues(values)
                val totalScale = kotlin.math.abs(values[Matrix.MSCALE_X])
                eraseRadius = if (totalScale > 0.001f) binding.eraseOverlay.brushRadius / totalScale else binding.eraseOverlay.brushRadius
            }
            binding.eraseOverlay.onEraseEnd = {
                when (binding.eraseOverlay.toolMode) {
                    ToolMode.MAGIC_ERASE -> viewModel.applyMagicRemove(binding.eraseOverlay.magicPickedColor)
                    else -> if (eraseAccumulated.isNotEmpty()) {
                        viewModel.applyErase(eraseAccumulated.toList(), eraseRadius)
                    }
                }
                eraseAccumulated.clear()
                binding.eraseOverlay.clearMask()
            }
            binding.eraseOverlay.onColorEraseTap = { x, y ->
                if (binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE) {
                    viewModel.currentState?.currentBitmap?.let { bmp ->
                        val cx = x.toInt().coerceIn(0, bmp.width - 1)
                        val cy = y.toInt().coerceIn(0, bmp.height - 1)
                        val pixel = bmp.getPixel(cx, cy)
                        if (android.graphics.Color.alpha(pixel) > 0) {
                            binding.eraseOverlay.setMagicColor(pixel)
                            android.widget.Toast.makeText(this, R.string.magic_color_picked_hint, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    viewModel.applyColorErase(x, y)
                }
            }
        }
    }

    private fun showCropMode() {
        val currentBitmap = viewModel.currentState?.currentBitmap ?: return
        resetZoom()
        binding.imagePreview.setImageBitmap(currentBitmap)
        setupCropOverlay(currentBitmap)
        binding.cropOverlay.visibility = View.VISIBLE
        binding.cropChipGroup.visibility = View.VISIBLE
        binding.bottomActionBar.visibility = View.GONE
        setToolMode(ToolMode.NONE)
    }

    internal fun hideCropMode() {
        binding.cropOverlay.visibility = View.GONE
        binding.cropChipGroup.visibility = View.GONE
        binding.bottomActionBar.visibility = View.VISIBLE
    }

    private fun applyCrop() {
        val cropRect = binding.cropOverlay.getCropRect()
        val bitmap = viewModel.currentState?.currentBitmap ?: return
        val overlay = binding.cropOverlay

        val displayWidth = overlay.imageDisplayRight - overlay.imageDisplayLeft
        val displayHeight = overlay.imageDisplayBottom - overlay.imageDisplayTop
        val scaleX = bitmap.width.toFloat() / displayWidth
        val scaleY = bitmap.height.toFloat() / displayHeight

        val left = ((cropRect.left - overlay.imageDisplayLeft) * scaleX).toInt()
        val top = ((cropRect.top - overlay.imageDisplayTop) * scaleY).toInt()
        val right = ((cropRect.right - overlay.imageDisplayLeft) * scaleX).toInt()
        val bottom = ((cropRect.bottom - overlay.imageDisplayTop) * scaleY).toInt()

        viewModel.applyCrop(left, top, right, bottom)
        hideCropMode()
    }

    internal fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int = 2048): Bitmap {
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
