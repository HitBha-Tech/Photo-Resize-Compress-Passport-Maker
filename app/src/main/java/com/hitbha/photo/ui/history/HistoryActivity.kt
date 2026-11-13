package com.hitbha.photo.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityHistoryBinding
import com.hitbha.photo.ui.history.HistoryImageAdapter.HistoryImage
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.ThemeManager
import com.hitbha.photo.utils.updateStatusBarStyle
import com.hitbha.photo.utils.setupWindowInsets
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle(binding.root)
        setupWindowInsets(binding.root, binding.toolbar, binding.rvHistory)

        setupToolbar()
        loadSavedImages()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun openImage(image: HistoryImage) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(image.uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open_image, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedImages() {
        lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) { querySavedImages() }
            if (!isDestroyed) {
                showImages(images)
            }
        }
    }

    private fun querySavedImages(): List<HistoryImage> {
        val images = mutableListOf<HistoryImage>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("PhotoResizeCompressPassport_%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: ""
                val date = it.getLong(dateCol)
                val size = it.getLong(sizeCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                images.add(HistoryImage(uri, name, date, size))
            }
        }
        return images
    }

    private fun showImages(images: List<HistoryImage>) {
        if (images.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
            binding.rvHistory.layoutManager = LinearLayoutManager(this)
            binding.rvHistory.adapter = HistoryImageAdapter(images) { openImage(it) }
        }
    }

}
