package com.hitbha.photo.ui.batch

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hitbha.photo.utils.FileUtils
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedImages = MutableLiveData(mutableListOf<Uri>())
    val selectedImages: LiveData<MutableList<Uri>> = _selectedImages

    private val _lastProcessedBitmap = MutableLiveData<Bitmap?>()
    val lastProcessedBitmap: LiveData<Bitmap?> = _lastProcessedBitmap

    private val _processing = MutableLiveData(false)
    val processing: LiveData<Boolean> = _processing

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _operationComplete = MutableLiveData<String?>()
    val operationComplete: LiveData<String?> = _operationComplete

    fun clearToastMessage() { _toastMessage.value = null }
    fun clearOperationComplete() { _operationComplete.value = null }

    val images: List<Uri> get() = _selectedImages.value.orEmpty().toList()

    fun addImages(uris: List<Uri>, maxLimit: Int = 99): Pair<Int, Boolean> {
        val current = _selectedImages.value ?: mutableListOf()
        var added = 0
        for (uri in uris) {
            if (current.size >= maxLimit) break
            if (!current.contains(uri)) {
                current.add(uri)
                added++
            }
        }
        _selectedImages.value = current
        return Pair(added, added < uris.size)
    }

    fun removeImage(position: Int) {
        val current = _selectedImages.value ?: return
        if (position in current.indices) {
            current.removeAt(position)
            _selectedImages.value = current
        }
    }

    fun isInternetAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun decodeSample(uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val ctx = getApplication<Application>()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension && sampleSize < 64) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (t: Throwable) {
            null
        }
    }

    private fun decodeFull(uri: Uri): Bitmap? {
        return try {
            val ctx = getApplication<Application>()
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (t: Throwable) {
            null
        }
    }

    fun processBatchResize(width: Int, height: Int, format: String, quality: Int = 100, paperSize: ShareUtils.PaperSize? = null) {
        val currentImages = _selectedImages.value.orEmpty()
        _toastMessage.value = "processing_count"
        _processing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            var lastBitmap: Bitmap? = null
            val pdfBitmaps = mutableListOf<Bitmap>()
            currentImages.forEach { uri ->
                try {
                    val bitmap = decodeSample(uri)
                    bitmap?.let {
                        val resized = ImageProcessor.resizeBitmap(it, width, height, false)
                        if (format == "pdf") {
                            pdfBitmaps.add(resized)
                        } else {
                            lastBitmap = resized
                            when (format) {
                                "png" -> {
                                    val png = if (quality < 100) ImageProcessor.scaleByQuality(resized, quality) else resized
                                    FileUtils.saveBitmapToGallery(ctx, png, Bitmap.CompressFormat.PNG, 100)
                                }
                                else -> FileUtils.saveBitmapToGallery(ctx, resized, Bitmap.CompressFormat.JPEG, quality)
                            }
                        }
                        if (!it.isRecycled) it.recycle()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BatchVM", "Resize failed", e)
                }
            }

            if (format == "pdf" && pdfBitmaps.isNotEmpty()) {
                FileUtils.savePdfToGallery(ctx, pdfBitmaps, paperSize = paperSize, quality = quality)
                lastBitmap = pdfBitmaps.last()
                pdfBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                pdfBitmaps.clear()
            }

            withContext(Dispatchers.Main) {
                _processing.value = false
                _lastProcessedBitmap.value = lastBitmap
                _operationComplete.value = "resize_done"
            }
        }
    }

    fun processBatchCompress(quality: Int, format: String, paperSize: ShareUtils.PaperSize? = null) {
        val currentImages = _selectedImages.value.orEmpty()
        _toastMessage.value = "processing_count"
        _processing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            var lastBitmap: Bitmap? = null
            val pdfBitmaps = mutableListOf<Bitmap>()
            currentImages.forEach { uri ->
                try {
                    val bitmap = decodeFull(uri)
                    bitmap?.let {
                        if (format == "pdf") {
                            pdfBitmaps.add(it)
                        } else {
                            lastBitmap = it
                            when (format) {
                                "png" -> {
                                    val png = if (quality < 100) ImageProcessor.scaleByQuality(it, quality) else it
                                    FileUtils.saveBitmapToGallery(ctx, png, Bitmap.CompressFormat.PNG, 100)
                                }
                                else -> FileUtils.saveBitmapToGallery(ctx, it, Bitmap.CompressFormat.JPEG, quality)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BatchVM", "Compress failed", e)
                }
            }

            if (format == "pdf" && pdfBitmaps.isNotEmpty()) {
                FileUtils.savePdfToGallery(ctx, pdfBitmaps, paperSize = paperSize, quality = quality)
                lastBitmap = pdfBitmaps.last()
                pdfBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                pdfBitmaps.clear()
            }

            withContext(Dispatchers.Main) {
                _processing.value = false
                _lastProcessedBitmap.value = lastBitmap
                _operationComplete.value = "compress_done"
            }
        }
    }

    fun shareBatchResize(width: Int, height: Int, format: String, quality: Int = 100, paperSize: ShareUtils.PaperSize? = null) {
        val currentImages = _selectedImages.value.orEmpty()
        _toastMessage.value = "processing_count"
        _processing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var lastBitmap: Bitmap? = null
            val pdfBitmaps = mutableListOf<Bitmap>()
            currentImages.forEach { uri ->
                try {
                    val bitmap = decodeFull(uri)
                    bitmap?.let {
                        val resized = ImageProcessor.resizeBitmap(it, width, height, false)
                        it.recycle()
                        if (format == "pdf") pdfBitmaps.add(resized) else lastBitmap = resized
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BatchVM", "Share resize failed", e)
                }
            }

            withContext(Dispatchers.Main) {
                _processing.value = false
                _lastProcessedBitmap.value = lastBitmap
                if (format == "pdf") {
                    _operationComplete.value = if (pdfBitmaps.isNotEmpty()) "share_pdf:${pdfBitmaps.size}" else "share_empty"
                } else if (lastBitmap != null) {
                    _operationComplete.value = "share_image:$format:$quality"
                } else {
                    _operationComplete.value = "share_empty"
                }
            }
        }
    }

    fun shareBatchCompress(format: String, quality: Int = 100, paperSize: ShareUtils.PaperSize? = null) {
        val currentImages = _selectedImages.value.orEmpty()
        _toastMessage.value = "processing_count"
        _processing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var lastBitmap: Bitmap? = null
            val pdfBitmaps = mutableListOf<Bitmap>()
            currentImages.forEach { uri ->
                try {
                    val bitmap = decodeFull(uri)
                    bitmap?.let {
                        if (format == "pdf") pdfBitmaps.add(it) else lastBitmap = it
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BatchVM", "Share compress failed", e)
                }
            }

            withContext(Dispatchers.Main) {
                _processing.value = false
                _lastProcessedBitmap.value = lastBitmap
                if (format == "pdf") {
                    _operationComplete.value = if (pdfBitmaps.isNotEmpty()) "share_pdf:${pdfBitmaps.size}" else "share_empty"
                } else if (lastBitmap != null) {
                    _operationComplete.value = "share_image:$format:$quality"
                } else {
                    _operationComplete.value = "share_empty"
                }
            }
        }
    }
}
