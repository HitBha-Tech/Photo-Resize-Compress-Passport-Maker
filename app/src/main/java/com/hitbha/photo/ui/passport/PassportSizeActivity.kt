package com.hitbha.photo.ui.passport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityPassportSizeBinding
import com.hitbha.photo.ui.editor.ToolMode
import com.hitbha.photo.utils.FileUtils
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.StatePersistence
import com.hitbha.photo.utils.ThemeManager
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.setupWindowInsets
import com.hitbha.photo.utils.showLoading
import com.hitbha.photo.utils.updateStatusBarStyle
import java.io.File

class PassportSizeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    internal lateinit var binding: ActivityPassportSizeBinding

    internal val personBitmaps = mutableListOf<Bitmap>()
    internal var selectedPersonIndex = 0
    internal val layoutBitmaps = mutableListOf<Bitmap>()

    internal val currentBitmap: Bitmap?
        get() = personBitmaps.getOrNull(selectedPersonIndex)

    internal var currentPageWidthPx = 0
    internal var currentPageHeightPx = 0
    internal var currentColumns = 2
    internal var currentRows = 4
    internal var currentPhotoCount: Int? = null
    internal var currentPageIndex = 0
    internal var originalFileSizeBytes = 0L

    internal var zoomScale = 1f
    private var maxZoomScale = 5f
    private var minZoomScale = 1f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    internal val zoomMatrix = Matrix()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var pendingReplaceIndex: Int? = null
    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()

    internal var preFillBitmap: Bitmap? = null
    internal var eraseSnapshot: Bitmap? = null
    internal val eraseAccumulated = mutableListOf<Pair<Float, Float>>()
    internal var eraseRadiusAccumulated = 0f

    internal var selectedPageSizeRow = R.id.rb_4x6
    internal var rowsPerGroup = 1

    internal data class ColorOption(val argb: Int, val name: String, val isOpaque: Boolean = true)

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

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val replaceIdx = pendingReplaceIndex
        pendingReplaceIndex = null
        if (replaceIdx != null && replaceIdx < personBitmaps.size && uris.isNotEmpty()) {
            loadAndReplaceImage(uris.first(), replaceIdx)
        } else {
            loadImages(uris)
        }
    }

    private var pendingCameraUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            loadImages(listOf(uri))
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityPassportSizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.toolbar, binding.bottomActionBar)

        setupToolbar()
        setupClickListeners()
        setupCheckerboardBackground()
        setupZoom()
        updatePersonSelector()
        restoreSavedState()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.passport_size_photo)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupClickListeners() {
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }

        binding.chipCancelCrop.setOnClickListener { hideCropMode() }
        binding.chipApplyCrop.setOnClickListener { view ->
            view.performHapticClick()
            applyCrop()
        }
        binding.btnCrop.setOnClickListener {
            exitEraseModeIfNeeded()
            showCropMode()
        }
        binding.btnRemoveBg.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            view.performHapticClick()
            showBgTools(view)
        }
        binding.btnFinalTouches.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            view.performHapticClick()
            showFinalTouchesDialog()
        }
        binding.btnSelectPage.setOnClickListener {
            exitEraseModeIfNeeded()
            showPageSizeDialog()
        }
        binding.btnSave.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            view.performHapticClick()
            showSaveOptions()
        }
        binding.btnShare.setOnClickListener { view ->
            exitEraseModeIfNeeded()
            view.performHapticClick()
            showShareOptions()
        }

        binding.btnPagePrev.setOnClickListener { showPage(currentPageIndex - 1) }
        binding.btnPageNext.setOnClickListener { showPage(currentPageIndex + 1) }

        binding.btnBrushSize.setOnClickListener { showBrushSizePopup(it) }
    }

    private fun setupCheckerboardBackground() {
        val size = 16
        val bmp = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
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

    private fun restoreSavedState() {
        val saved = StatePersistence.loadPassportState(this) ?: return
        if (saved.personBitmaps.isEmpty()) return
        personBitmaps.clear()
        personBitmaps.addAll(saved.personBitmaps)
        selectedPersonIndex = saved.selectedIndex
        personBitmaps.getOrNull(selectedPersonIndex)?.let {
            binding.imagePreview.setImageBitmap(it)
        }
        updatePersonSelector()
    }

    override fun onPause() {
        super.onPause()
        if (personBitmaps.isNotEmpty()) {
            StatePersistence.savePassportState(
                ctx = this,
                personBitmaps = personBitmaps.toList(),
                selectedIndex = selectedPersonIndex
            )
        }
    }

    private fun loadImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        binding.progressBar.showLoading(true)
        val queue = ArrayDeque<Uri>()
        queue.addAll(uris)

        fun loadNext() {
            if (queue.isEmpty()) {
                binding.progressBar.showLoading(false)
                if (personBitmaps.isEmpty()) return
                selectedPersonIndex = personBitmaps.size - 1
                clearFullPageState()
                resetZoom()
                updatePersonPreview()
                updatePersonSelector()
                currentBitmap?.let { binding.imagePreview.post { setupEraseOverlay(it) } }
                return
            }
            if (personBitmaps.size >= 24) {
                binding.progressBar.showLoading(false)
                Toast.makeText(this@PassportSizeActivity, R.string.max_images_limit, Toast.LENGTH_SHORT).show()
                return
            }
            val uri = queue.removeFirst()
            originalFileSizeBytes += FileUtils.getFileSizeFromUri(this@PassportSizeActivity, uri)
            Glide.with(this@PassportSizeActivity)
                .asBitmap()
                .load(uri)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        personBitmaps.add(downscaleIfNeeded(resource))
                        updatePersonSelector()
                        loadNext()
                    }

                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        loadNext()
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        loadNext()
                    }
                })
        }
        loadNext()
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

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun captureFromCamera() {
        if (hasCameraPermission()) {
            startCameraCapture()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraCapture() {
        val uri = createCameraImageUri()
        if (uri == null) {
            Toast.makeText(this, R.string.cannot_open_image, Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun createCameraImageUri(): Uri? {
        return try {
            val dir = File(cacheDir, "camera")
            if (!dir.exists()) dir.mkdirs()
            val file = File.createTempFile("passport_", ".jpg", dir)
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            null
        }
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
            if (binding.eraseOverlay.toolMode != ToolMode.NONE) {
                return@setOnTouchListener binding.eraseOverlay.toolMode == ToolMode.ERASER ||
                        binding.eraseOverlay.toolMode == ToolMode.COLOR_ERASE ||
                        binding.eraseOverlay.toolMode == ToolMode.MAGIC_ERASE
            }
            if (personBitmaps.isNotEmpty()) {
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
                    MotionEvent.ACTION_UP -> {
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun applyZoom() {
        binding.imagePreview.scaleType = ImageView.ScaleType.MATRIX
        binding.imagePreview.imageMatrix = zoomMatrix
        updateEraseOverlayBounds()
    }

    private fun updateEraseOverlayBounds() {
        val bitmap = currentBitmap ?: return
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

    internal fun saveUndoState() {
        currentBitmap?.let { bitmap ->
            undoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true))
            redoStack.clear()
            updateUndoRedoButtons()
        }
    }

    private fun undo() {
        if (undoStack.isEmpty() || currentBitmap == null) return
        redoStack.add(currentBitmap!!.copy(Bitmap.Config.ARGB_8888, true))
        val prev = undoStack.removeAt(undoStack.lastIndex)
        personBitmaps[selectedPersonIndex] = prev
        updatePersonPreview()
        clearFullPageState()
        updatePersonSelector()
        updateUndoRedoButtons()
    }

    private fun redo() {
        if (redoStack.isEmpty() || currentBitmap == null) return
        undoStack.add(currentBitmap!!.copy(Bitmap.Config.ARGB_8888, true))
        val next = redoStack.removeAt(redoStack.lastIndex)
        personBitmaps[selectedPersonIndex] = next
        updatePersonPreview()
        clearFullPageState()
        updatePersonSelector()
        updateUndoRedoButtons()
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = undoStack.isNotEmpty()
        binding.btnRedo.isEnabled = redoStack.isNotEmpty()
    }

    internal fun updatePersonPreview() {
        currentBitmap?.let { binding.imagePreview.setImageBitmap(it) }
    }

    private fun updateActionState() {
        val hasImage = personBitmaps.isNotEmpty()
        binding.btnCrop.isEnabled = hasImage
        binding.btnRemoveBg.isEnabled = hasImage
        binding.btnFinalTouches.isEnabled = hasImage
        binding.btnSelectPage.isEnabled = hasImage
        binding.btnSave.isEnabled = hasImage
        binding.btnShare.isEnabled = hasImage
    }

    internal fun updatePersonSelector() {
        binding.personThumbnails.removeAllViews()
        val ctx = binding.personThumbnails.context
        for (i in personBitmaps.indices) {
            val thumb = android.widget.ImageButton(ctx).apply {
                layoutParams = ViewGroup.MarginLayoutParams(120, 120).apply {
                    setMargins(4, 4, 4, 4)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(personBitmaps[i])
                setBackgroundResource(if (i == selectedPersonIndex) R.drawable.bg_person_selected else R.drawable.bg_person_normal)
                setOnClickListener { showImagePreview(i) }
                setOnLongClickListener {
                    if (personBitmaps.size > 1) {
                        removePerson(i)
                    }
                    true
                }
                contentDescription = "Person ${i + 1}"
            }
            binding.personThumbnails.addView(thumb)
        }
        val addBtn = android.widget.ImageButton(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(120, 120).apply {
                setMargins(4, 4, 4, 4)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(android.R.drawable.ic_menu_add)
            setBackgroundResource(R.drawable.bg_person_normal)
            setOnClickListener { addNewPerson(it) }
            contentDescription = "Add person"
        }
        binding.personThumbnails.addView(addBtn)
        binding.personSelectorContainer.visibility = View.VISIBLE
        updateActionState()
    }

    internal fun selectPerson(index: Int) {
        if (index < 0 || index >= personBitmaps.size || index == selectedPersonIndex) return
        selectedPersonIndex = index
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoButtons()
        preFillBitmap = null
        resetZoom()
        updatePersonPreview()
        updatePersonSelector()
        currentBitmap?.let { binding.imagePreview.post { setupEraseOverlay(it) } }
    }

    private fun removePerson(index: Int) {
        if (personBitmaps.size <= 1) return
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoButtons()
        preFillBitmap = null
        personBitmaps.removeAt(index)
        if (selectedPersonIndex >= personBitmaps.size) {
            selectedPersonIndex = personBitmaps.size - 1
        }
        resetZoom()
        personBitmaps.getOrNull(selectedPersonIndex)?.let {
            binding.imagePreview.setImageBitmap(it)
            binding.imagePreview.post { setupEraseOverlay(it) }
        } ?: run {
            binding.imagePreview.setImageDrawable(null)
        }
        updatePersonSelector()
    }

    private fun addNewPerson(anchor: View) {
        if (personBitmaps.size >= 24) {
            Toast.makeText(this, R.string.max_images_limit, Toast.LENGTH_SHORT).show()
            return
        }
        pendingReplaceIndex = null
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.from_gallery))
        popup.menu.add(0, 2, 0, getString(R.string.camera))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                2 -> captureFromCamera()
            }
            true
        }
        popup.show()
    }

    private fun loadAndReplaceImage(uri: Uri, index: Int) {
        saveUndoState()
        resetZoom()
        originalFileSizeBytes += FileUtils.getFileSizeFromUri(this, uri)
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    personBitmaps[index] = resource
                    selectedPersonIndex = index
                    clearFullPageState()
                    updatePersonPreview()
                    updatePersonSelector()
                    binding.imagePreview.post { setupEraseOverlay(resource) }
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }
}