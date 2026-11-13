package com.hitbha.photo.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityDocumentScannerBinding
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.ThemeManager
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.setupWindowInsets
import com.hitbha.photo.utils.showLoading
import com.hitbha.photo.utils.updateStatusBarStyle
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentScannerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private lateinit var binding: ActivityDocumentScannerBinding
    private lateinit var viewModel: ScanViewModel

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = viewModel.pendingCameraUri
        viewModel.pendingCameraUri = null
        if (success && uri != null) loadPage(uri)
        cleanupCameraCache()
    }

    private val fullCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onFullCameraReturn()
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCapture() else Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
    }

    private val requestMediaPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startFullCameraCapture() else { Toast.makeText(this, R.string.media_permission_required, Toast.LENGTH_LONG).show(); startCameraCapture() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.toolbar, binding.bottomActionBar)

        viewModel = ViewModelProvider(this)[ScanViewModel::class.java]
        observeViewModel()
        setupToolbar()
        setupClickListeners()
    }

    private fun observeViewModel() {
        viewModel.processing.observe(this) { binding.progressBar.showLoading(it) }
        viewModel.pagesLiveData.observe(this) { updatePreview() }
        viewModel.toastMessage.observe(this) { msg ->
            if (msg != null) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); viewModel.clearToastMessage() }
        }
        viewModel.saveResult.observe(this) { result ->
            if (result != null) {
                val (successRes, failRes) = result
                Toast.makeText(this, if (viewModel.hasPages()) successRes else failRes, Toast.LENGTH_SHORT).show()
                viewModel.clearSaveResult()
            }
        }
        viewModel.shareData.observe(this) { data ->
            if (data != null) {
                if (data.uri != null) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = data.mimeType; putExtra(Intent.EXTRA_STREAM, data.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, getString(R.string.share)))
                } else { Toast.makeText(this, data.failRes, Toast.LENGTH_SHORT).show() }
                viewModel.clearShareData()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.page_scan)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupClickListeners() {
        binding.btnTakePhoto.setOnClickListener { view -> view.performHapticClick(); captureFromCamera() }
        binding.btnSavePdf.setOnClickListener { view -> view.performHapticClick(); AdManager.showInterstitial(this) { viewModel.saveOutput(); cleanupCameraCache() } }
        binding.btnSharePdf.setOnClickListener { view -> view.performHapticClick(); AdManager.showInterstitial(this) { viewModel.shareOutput(); cleanupCameraCache() } }
        binding.rgFormat.setOnCheckedChangeListener { _, checkedId ->
            val format = when (checkedId) {
                R.id.rb_id_scan -> OutputFormat.ID_SCAN
                R.id.rb_document -> OutputFormat.DOCUMENT
                R.id.rb_excel -> OutputFormat.EXCEL
                else -> OutputFormat.PDF
            }
            viewModel.setFormat(format)
            updatePreview()
        }
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val mediaReadPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasMediaReadPermission(): Boolean = ContextCompat.checkSelfPermission(this, mediaReadPermission) == PackageManager.PERMISSION_GRANTED

    private fun captureFromCamera() {
        if (hasCameraPermission()) launchCapture() else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCapture() {
        if (canLaunchFullCamera()) {
            if (hasMediaReadPermission()) startFullCameraCapture() else requestMediaPermissionLauncher.launch(mediaReadPermission)
        } else {
            startCameraCapture()
        }
    }

    private fun canLaunchFullCamera(): Boolean = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).resolveActivity(packageManager) != null

    private fun startFullCameraCapture() {
        viewModel.captureMarkerTime = System.currentTimeMillis()
        viewModel.captureMarkerDateAdded = viewModel.queryMaxMediaDateAdded()
        fullCameraLauncher.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    private fun onFullCameraReturn() {
        binding.progressBar.showLoading(true)
        lifecycleScope.launch {
            try {
                val captured = withContext(Dispatchers.IO) { viewModel.findNewlyCapturedMedia() }
                var added = 0
                for (media in captured) {
                    val bitmap = withContext(Dispatchers.IO) {
                        if (media.isVideo) viewModel.extractVideoFrame(media.uri) else viewModel.decodeBitmap(media.uri)
                    }
                    if (bitmap != null) { viewModel.addPage(bitmap); added++ }
                }
                binding.progressBar.showLoading(false)
                if (added == 0) Toast.makeText(this@DocumentScannerActivity, R.string.no_photo_captured, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.showLoading(false)
                Toast.makeText(this@DocumentScannerActivity, R.string.no_photo_captured, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCameraCapture() {
        val uri = createCameraImageUri()
        if (uri == null) { Toast.makeText(this, R.string.cannot_open_image, Toast.LENGTH_SHORT).show(); return }
        viewModel.pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun createCameraImageUri(): Uri? {
        return try {
            val dir = File(cacheDir, "scan"); if (!dir.exists()) dir.mkdirs()
            val file = File.createTempFile("scan_", ".jpg", dir)
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) { null }
    }

    private fun cleanupCameraCache() {
        try {
            File(cacheDir, "scan").listFiles()?.forEach { it.delete() }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun loadPage(uri: Uri) {
        binding.progressBar.showLoading(true)
        Glide.with(this).asBitmap().load(uri).override(1080).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                viewModel.addPage(resource); binding.progressBar.showLoading(false)
            }
            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) { binding.progressBar.showLoading(false) }
            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) { binding.progressBar.showLoading(false) }
        })
    }

    private fun updatePreview() {
        binding.pageThumbnailsContainer.removeAllViews()
        val pages = viewModel.pageBitmaps
        val format = viewModel.selectedFormat.value ?: OutputFormat.PDF
        pages.forEachIndexed { index, bitmap ->
            val thumb = ImageButton(this).apply {
                layoutParams = android.view.ViewGroup.MarginLayoutParams(120, 200).apply { setMargins(4, 4, 4, 4) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(bitmap)
                setBackgroundResource(R.drawable.bg_person_normal)
                setOnClickListener { showPage(index) }
                setOnLongClickListener { viewModel.removePage(index); updatePreview(); true }
                contentDescription = "Page ${index + 1}"
            }
            binding.pageThumbnailsContainer.addView(thumb)
        }
        if (pages.isNotEmpty()) {
            if (format == OutputFormat.ID_SCAN) {
                binding.tvPageCounter.text = "1 / 1"
                binding.ivPdfPage.setImageBitmap(viewModel.buildIdCardPagePreview(pages))
            } else {
                showPage(pages.lastIndex)
            }
        } else {
            binding.ivPdfPage.setImageDrawable(null)
            binding.tvPageCounter.text = ""
        }
        binding.btnSavePdf.isEnabled = pages.isNotEmpty()
        binding.btnSharePdf.isEnabled = pages.isNotEmpty()
    }

    private fun showPage(index: Int) {
        val bitmap = viewModel.pageBitmaps.getOrNull(index) ?: return
        binding.tvPageCounter.text = "${index + 1} / ${viewModel.pageBitmaps.size}"
        binding.ivPdfPage.setImageBitmap(viewModel.buildPdfPagePreview(bitmap))
    }
}
