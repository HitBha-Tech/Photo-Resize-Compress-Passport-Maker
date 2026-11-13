package com.hitbha.photo.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.hitbha.photo.R
import com.hitbha.photo.utils.getColorAttr
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

private data class PageOption(val nameRes: Int, val widthPx: Int, val heightPx: Int)

private fun EditorActivity.pageOptions(): List<PageOption> = listOf(
    PageOption(R.string.page_4x6_plain, 864, 1296),
    PageOption(R.string.page_letter_plain, 1836, 2376),
    PageOption(R.string.page_a5_plain, 1260, 1785),
    PageOption(R.string.page_a4_plain, 1785, 2526),
    PageOption(R.string.page_a3_plain, 2526, 3573)
)

fun EditorActivity.showAddPageOrStampDialog() {
    val activity = this
    val dialog = BottomSheetDialog(activity)

    val container = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(40, 16, 40, 24)
    }

    val pageLabel = android.widget.TextView(this).apply {
        setText(R.string.add_page)
        textSize = 15f
        setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(0, 0, 0, 12)
    }
    container.addView(pageLabel)

    for (opt in pageOptions()) {
        val btn = MaterialButton(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 }
            setText(opt.nameRes)
            setOnClickListener {
                dialog.dismiss()
                createPage(opt.widthPx, opt.heightPx)
            }
        }
        container.addView(btn)
    }

    val divider = View(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            1
        ).apply {
            topMargin = 24
            bottomMargin = 16
        }
        setBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorOutlineVariant))
    }
    container.addView(divider)

    val stampBtn = MaterialButton(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setText(R.string.add_image_stamp)
        setOnClickListener {
            dialog.dismiss()
            if (viewModel.currentState?.currentBitmap == null) {
                android.widget.Toast.makeText(activity, R.string.add_sign_first, android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickStampLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    container.addView(stampBtn)

    val scrollView = android.widget.ScrollView(this).apply {
        addView(container)
    }
    dialog.setContentView(scrollView)
    dialog.window?.setDimAmount(0.4f)
    dialog.show()
}

fun EditorActivity.createPage(width: Int, height: Int) {
    viewModel.createBlankPage(width, height)
    android.widget.Toast.makeText(this, R.string.page_created, android.widget.Toast.LENGTH_SHORT).show()
    binding.imagePreview.post {
        resetZoom()
        viewModel.currentState?.currentBitmap?.let { bmp ->
            setupEraseOverlay(bmp)
        }
    }
}

fun EditorActivity.loadStampImage(uri: Uri) {
    val bmp = decodeImageFromUri(uri) ?: run {
        android.widget.Toast.makeText(this, R.string.cannot_open_image, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val base = viewModel.currentState?.currentBitmap ?: run {
        android.widget.Toast.makeText(this, R.string.add_sign_first, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    stampBitmap = downscaleIfNeeded(bmp, 1024)
    showStampMode(base)
}

private fun EditorActivity.decodeImageFromUri(uri: Uri): Bitmap? {
    return try {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > 2048 && sampleSize < 32) {
            sampleSize *= 2
        }
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    } catch (t: Throwable) {
        null
    }
}

fun EditorActivity.showStampMode(base: Bitmap) {
    exitEraseModeIfNeeded()
    hideCropMode()
    resetZoom()
    binding.imagePreview.setImageBitmap(base)

    // Hide the bottom bar first so the image container expands before we
    // measure the display rect. Otherwise the measured position is based on
    // the old (smaller) area and the stamp ends up placed below where the
    // image is actually shown.
    binding.bottomActionBar.visibility = View.GONE
    binding.imagePreview.post {
        binding.imagePreview.requestLayout()
        binding.imagePreview.post {
            val imageView = binding.imagePreview
            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()
            if (viewWidth <= 0 || viewHeight <= 0) return@post
            val scale = minOf(viewWidth / base.width.toFloat(), viewHeight / base.height.toFloat())
            val dw = base.width.toFloat() * scale
            val dh = base.height.toFloat() * scale
            stampImageLeft = (viewWidth - dw) / 2f
            stampImageTop = (viewHeight - dh) / 2f
            stampImageRight = stampImageLeft + dw
            stampImageBottom = stampImageTop + dh

            binding.stampOverlay.setImageBounds(stampImageLeft, stampImageTop, stampImageRight, stampImageBottom)
            stampBitmap?.let { binding.stampOverlay.setStamp(it) }
            binding.stampOverlay.visibility = View.VISIBLE
        }
    }
}

fun EditorActivity.hideStampMode() {
    binding.stampOverlay.visibility = View.GONE
    binding.bottomActionBar.visibility = View.VISIBLE
    stampBitmap = null
}

fun EditorActivity.applyStamp() {
    val stamp = stampBitmap ?: run { hideStampMode(); return }
    val base = viewModel.currentState?.currentBitmap ?: run { hideStampMode(); return }
    val rect = binding.stampOverlay.getStampRect()

    val displayWidth = stampImageRight - stampImageLeft
    val displayHeight = stampImageBottom - stampImageTop
    if (displayWidth <= 0 || displayHeight <= 0) {
        hideStampMode()
        return
    }
    val scaleX = base.width.toFloat() / displayWidth
    val scaleY = base.height.toFloat() / displayHeight

    val left = ((rect.left - stampImageLeft) * scaleX).toInt()
    val top = ((rect.top - stampImageTop) * scaleY).toInt()
    val right = ((rect.right - stampImageLeft) * scaleX).toInt()
    val bottom = ((rect.bottom - stampImageTop) * scaleY).toInt()

    viewModel.addStamp(stamp, left, top, right, bottom)
    hideStampMode()
    binding.imagePreview.post {
        resetZoom()
        viewModel.currentState?.currentBitmap?.let { setupEraseOverlay(it) }
    }
    android.widget.Toast.makeText(this, R.string.stamp_added, android.widget.Toast.LENGTH_SHORT).show()
}
