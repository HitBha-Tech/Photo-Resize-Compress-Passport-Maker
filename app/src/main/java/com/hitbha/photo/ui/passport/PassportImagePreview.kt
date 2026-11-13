package com.hitbha.photo.ui.passport

import android.app.Dialog
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.hitbha.photo.R

fun PassportSizeActivity.showImagePreview(startIndex: Int) {
    if (personBitmaps.isEmpty()) return
    var currentIndex = startIndex
    val dialog = Dialog(this)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT
    )
    @Suppress("DEPRECATION")
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    dialog.setContentView(R.layout.dialog_image_preview)

    val ivPreview = dialog.findViewById<ImageView>(R.id.iv_preview)
    val btnClose = dialog.findViewById<View>(R.id.btn_close_preview)
    val btnConfirm = dialog.findViewById<View>(R.id.btn_confirm_preview)
    val tvCounter = dialog.findViewById<TextView>(R.id.tv_counter)
    val controlsOverlay = dialog.findViewById<View>(R.id.controls_overlay)
    val tapPrev = dialog.findViewById<View>(R.id.tap_prev)
    val tapNext = dialog.findViewById<View>(R.id.tap_next)
    val tapToggle = dialog.findViewById<View>(R.id.tap_toggle)

    var controlsVisible = true
    var controlsHideRunnable: Runnable? = null

    fun loadImage(index: Int) {
        val bmp = personBitmaps.getOrNull(index) ?: return
        ivPreview.setImageBitmap(bmp)
        tvCounter.text = "${index + 1} / ${personBitmaps.size}"
    }

    fun hideControls() {
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
    }

    fun scheduleControlsHide() {
        controlsHideRunnable?.let { dialog.window?.decorView?.removeCallbacks(it) }
        controlsHideRunnable = Runnable { controlsOverlay.visibility = View.GONE; controlsVisible = false }
        dialog.window?.decorView?.postDelayed(controlsHideRunnable, 3000)
    }

    fun showControls() {
        controlsOverlay.visibility = View.VISIBLE
        controlsVisible = true
        scheduleControlsHide()
    }

    fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    loadImage(startIndex)
    scheduleControlsHide()

    tapPrev.setOnClickListener {
        if (currentIndex > 0) {
            currentIndex--
            loadImage(currentIndex)
            showControls()
        }
    }
    tapNext.setOnClickListener {
        if (currentIndex < personBitmaps.size - 1) {
            currentIndex++
            loadImage(currentIndex)
            showControls()
        }
    }
    tapToggle.setOnClickListener { toggleControls() }
    btnClose.setOnClickListener {
        val removedIdx = currentIndex
        personBitmaps.removeAt(removedIdx)
        if (personBitmaps.isEmpty()) {
            selectedPersonIndex = 0
            binding.imagePreview.setImageDrawable(null)
            updatePersonSelector()
            dialog.dismiss()
        } else {
            if (removedIdx < selectedPersonIndex) selectedPersonIndex--
            else if (selectedPersonIndex >= personBitmaps.size) selectedPersonIndex = personBitmaps.size - 1
            if (removedIdx < currentIndex) currentIndex--
            else if (currentIndex >= personBitmaps.size) currentIndex = personBitmaps.size - 1
            personBitmaps.getOrNull(selectedPersonIndex)?.let {
                binding.imagePreview.setImageBitmap(it)
            }
            updatePersonSelector()
            loadImage(currentIndex)
            showControls()
        }
    }
    btnConfirm.setOnClickListener {
        selectPerson(currentIndex)
        dialog.dismiss()
    }
    dialog.setOnDismissListener {
        controlsHideRunnable?.let { dialog.window?.decorView?.removeCallbacks(it) }
    }

    dialog.show()
}