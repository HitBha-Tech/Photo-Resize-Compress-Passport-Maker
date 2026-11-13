package com.hitbha.photo.ui.batch

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityBatchProcessingBinding
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.ShareUtils
import com.hitbha.photo.utils.ThemeManager
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.showLoading
import com.hitbha.photo.utils.updateStatusBarStyle
import com.hitbha.photo.utils.setupWindowInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchProcessingActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private lateinit var binding: ActivityBatchProcessingBinding
    private lateinit var viewModel: BatchViewModel
    private lateinit var adapter: BatchImageAdapter

    private var currentGalleryPosition = 0
    private var controlsVisible = true
    private var controlsHideRunnable: Runnable? = null

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val (added, hitLimit) = viewModel.addImages(uris)
        adapter.submitList(viewModel.images.toList())
        if (hitLimit) Toast.makeText(this, R.string.max_batch_images_limit, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityBatchProcessingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.toolbar, binding.bottomActionBar)

        viewModel = ViewModelProvider(this)[BatchViewModel::class.java]
        observeViewModel()
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun observeViewModel() {
        viewModel.processing.observe(this) { binding.progressBar.showLoading(it) }
        viewModel.toastMessage.observe(this) { msg ->
            if (msg != null) {
                val text = when (msg) {
                    "processing_count" -> getString(R.string.processing_count, viewModel.images.size)
                    else -> msg
                }
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                viewModel.clearToastMessage()
            }
        }
        viewModel.operationComplete.observe(this) { result ->
            if (result != null) {
                val text = when {
                    result == "resize_done" -> getString(R.string.batch_resize_completed)
                    result == "compress_done" -> getString(R.string.batch_compress_completed)
                    else -> null
                }
                if (text != null) Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                if (result.startsWith("share_")) handleShareResult(result)
                viewModel.clearOperationComplete()
            }
        }
    }

    private fun handleShareResult(result: String) {
        val lastBitmap = viewModel.lastProcessedBitmap.value
        when {
            result.startsWith("share_pdf:") -> {
                val count = result.substringAfter(":").toIntOrNull() ?: return
                Toast.makeText(this, getString(R.string.processing_count, count), Toast.LENGTH_SHORT).show()
            }
            result.startsWith("share_image:") -> {
                val parts = result.split(":")
                if (parts.size >= 3 && lastBitmap != null) {
                    val format = ShareUtils.Format.valueOf(parts[1].uppercase())
                    val quality = parts[2].toIntOrNull() ?: 100
                    shareBitmap(lastBitmap, format, quality = quality)
                }
            }
            result == "share_empty" -> {
                Toast.makeText(this, R.string.no_image_to_save, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.batch_processing)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = BatchImageAdapter(
            onRemove = { position -> viewModel.removeImage(position); adapter.submitList(viewModel.images.toList()) },
            onImageClick = { position -> showGalleryPreview(position) }
        )
        binding.rvSelectedImages.layoutManager = GridLayoutManager(this, 3)
        binding.rvSelectedImages.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnSelectImages.setOnClickListener {
            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnBatchResize.setOnClickListener { view ->
            if (viewModel.images.isEmpty()) { Toast.makeText(this, R.string.select_images_first, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            showBatchResizeOptionsMenu(view) { option ->
                when (option) {
                    1 -> showBatchResizeDialog({ w, h, fmt, ps -> AdManager.showRewardedInterstitial(this) { viewModel.processBatchResize(w, h, fmt, paperSize = ps) } },
                        { w, h, fmt, ps -> AdManager.showRewardedInterstitial(this) { viewModel.shareBatchResize(w, h, fmt, paperSize = ps) } })
                    2 -> showBatchResizeMmDialog({ w, h, f, ps -> AdManager.showRewardedInterstitial(this) { viewModel.processBatchResize(w, h, f, paperSize = ps) } },
                        { w, h, f, ps -> AdManager.showRewardedInterstitial(this) { viewModel.shareBatchResize(w, h, f, paperSize = ps) } })
                    3 -> showBatchResizeCmDialog({ w, h, f, ps -> AdManager.showRewardedInterstitial(this) { viewModel.processBatchResize(w, h, f, paperSize = ps) } },
                        { w, h, f, ps -> AdManager.showRewardedInterstitial(this) { viewModel.shareBatchResize(w, h, f, paperSize = ps) } })
                    4 -> showBatchResizeInchDialog({ w, h, f, ps -> AdManager.showRewardedInterstitial(this) { viewModel.processBatchResize(w, h, f, paperSize = ps) } },
                        { w, h, f, ps -> AdManager.showRewardedInterstitial(this) { viewModel.shareBatchResize(w, h, f, paperSize = ps) } })
                }
            }
        }
        binding.btnBatchCompress.setOnClickListener {
            if (viewModel.images.isEmpty()) { Toast.makeText(this, R.string.select_images_first, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            showBatchCompressDialog(
                onApply = { quality, fmt, ps -> AdManager.showRewardedInterstitial(this) { viewModel.processBatchCompress(quality, fmt, ps) } },
                onShare = { quality, fmt, ps -> AdManager.showRewardedInterstitial(this) { viewModel.shareBatchCompress(fmt, quality, ps) } }
            )
        }
    }

    private fun showGalleryPreview(startPosition: Int) {
        if (viewModel.images.isEmpty()) return
        currentGalleryPosition = startPosition
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        val window = dialog.window!!
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        dialog.setContentView(R.layout.dialog_image_preview)

        val ivPreview = dialog.findViewById<android.widget.ImageView>(R.id.iv_preview)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_preview)
        val btnConfirm = dialog.findViewById<View>(R.id.btn_confirm_preview)
        val tvCounter = dialog.findViewById<android.widget.TextView>(R.id.tv_counter)
        val controlsOverlay = dialog.findViewById<View>(R.id.controls_overlay)
        val tapPrev = dialog.findViewById<View>(R.id.tap_prev)
        val tapNext = dialog.findViewById<View>(R.id.tap_next)
        val tapToggle = dialog.findViewById<View>(R.id.tap_toggle)

        fun loadImage(index: Int) {
            val uri = viewModel.images.getOrNull(index) ?: return
            Glide.with(this).load(uri).fitCenter().into(ivPreview)
            tvCounter.text = "${index + 1} / ${viewModel.images.size}"
        }

        fun hideControls() { controlsOverlay.visibility = View.GONE; controlsVisible = false }
        fun scheduleControlsHide(dlg: Dialog, overlay: View) {
            controlsHideRunnable?.let { dlg.window?.decorView?.removeCallbacks(it) }
            controlsHideRunnable = Runnable { overlay.visibility = View.GONE; controlsVisible = false }
            dlg.window?.decorView?.postDelayed(controlsHideRunnable, 3000)
        }
        fun showControls() { controlsOverlay.visibility = View.VISIBLE; controlsVisible = true; scheduleControlsHide(dialog, controlsOverlay) }
        fun toggleControls() { if (controlsVisible) hideControls() else showControls() }

        loadImage(startPosition)
        scheduleControlsHide(dialog, controlsOverlay)
        binding.toolbar.visibility = View.INVISIBLE
        binding.bottomActionBar.visibility = View.INVISIBLE

        tapPrev.setOnClickListener { if (currentGalleryPosition > 0) { currentGalleryPosition--; loadImage(currentGalleryPosition); showControls() } }
        tapNext.setOnClickListener { if (currentGalleryPosition < viewModel.images.size - 1) { currentGalleryPosition++; loadImage(currentGalleryPosition); showControls() } }
        tapToggle.setOnClickListener { toggleControls() }
        btnClose.setOnClickListener {
            val removedIdx = currentGalleryPosition
            viewModel.removeImage(removedIdx)
            if (viewModel.images.isEmpty()) {
                binding.toolbar.visibility = View.VISIBLE; binding.bottomActionBar.visibility = View.VISIBLE
                adapter.submitList(viewModel.images.toList()); dialog.dismiss()
            } else {
                if (removedIdx < currentGalleryPosition) currentGalleryPosition--
                else if (currentGalleryPosition >= viewModel.images.size) currentGalleryPosition = viewModel.images.size - 1
                adapter.submitList(viewModel.images.toList()); loadImage(currentGalleryPosition); showControls()
            }
        }
        btnConfirm.setOnClickListener { binding.toolbar.visibility = View.VISIBLE; binding.bottomActionBar.visibility = View.VISIBLE; dialog.dismiss() }
        dialog.setOnDismissListener { binding.toolbar.visibility = View.VISIBLE; binding.bottomActionBar.visibility = View.VISIBLE; controlsHideRunnable?.let { dialog.window?.decorView?.removeCallbacks(it) } }
        dialog.show()
    }

    private fun shareBitmap(bitmap: android.graphics.Bitmap, format: ShareUtils.Format, fileName: String? = null, quality: Int = 100, paperSize: ShareUtils.PaperSize? = null) =
        shareBitmaps(listOf(bitmap), format, fileName, quality, paperSize)

    private fun shareBitmaps(bitmaps: List<android.graphics.Bitmap>, format: ShareUtils.Format, fileName: String? = null, quality: Int = 100, paperSize: ShareUtils.PaperSize? = null) {
        if (bitmaps.isEmpty()) return
        val mimeType = when (format) { ShareUtils.Format.JPEG -> "image/jpeg"; ShareUtils.Format.PNG -> "image/png"; ShareUtils.Format.PDF -> "application/pdf" }
        val failRes = when (format) { ShareUtils.Format.JPEG -> R.string.failed_share_jpeg; ShareUtils.Format.PNG -> R.string.failed_share_png; ShareUtils.Format.PDF -> R.string.failed_share_pdf }
        CoroutineScope(Dispatchers.Main).launch {
            val fileUri = withContext(Dispatchers.IO) {
                val source = if (format == ShareUtils.Format.PNG && quality < 100)
                    bitmaps.map { com.hitbha.photo.utils.scaleByQuality(it, quality) } else bitmaps
                ShareUtils.createShareFile(this@BatchProcessingActivity, source, format, "batch", fileName, quality = quality, paperSize = paperSize)
            }
            if (fileUri != null) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mimeType; putExtra(Intent.EXTRA_STREAM, fileUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, getString(R.string.share)))
            } else { Toast.makeText(this@BatchProcessingActivity, failRes, Toast.LENGTH_SHORT).show() }
        }
    }
}
