package com.hitbha.photo.ui.passport

import android.app.Dialog
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.showLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PassportSizeActivity.showFinalTouchesDialog() {
    val bitmap = currentBitmap ?: run {
        Toast.makeText(this, R.string.add_image_first, Toast.LENGTH_SHORT).show()
        return
    }
    val dialog = Dialog(this)
    val rootView = layoutInflater.inflate(R.layout.dialog_final_touches, null)
    dialog.setContentView(rootView)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )

    val ckFeather = rootView.findViewById<android.widget.CheckBox>(R.id.ck_feather)
    val seekFeather = rootView.findViewById<SeekBar>(R.id.seek_feather)
    val tvFeather = rootView.findViewById<TextView>(R.id.tv_feather_value)

    val ckFill = rootView.findViewById<android.widget.CheckBox>(R.id.ck_fill_bg)
    val fillColors = rootView.findViewById<android.widget.LinearLayout>(R.id.fill_colors)

    val ckOutline = rootView.findViewById<android.widget.CheckBox>(R.id.ck_outline)
    val seekOutline = rootView.findViewById<SeekBar>(R.id.seek_outline)
    val tvOutline = rootView.findViewById<TextView>(R.id.tv_outline_value)

    fun setupSeekBar(seek: SeekBar, label: TextView, suffix: String = "px") {
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "${progress}$suffix"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    setupSeekBar(seekFeather, tvFeather)
    setupSeekBar(seekOutline, tvOutline)

    val chipSize = resources.getDimensionPixelSize(com.hitbha.photo.R.dimen.spacing_xxl)
    val chipMargin = resources.getDimensionPixelSize(com.hitbha.photo.R.dimen.spacing_xs)
    val chipCorner = resources.getDimensionPixelSize(com.hitbha.photo.R.dimen.corner_small)

    fun addColorChips(container: android.widget.LinearLayout, colors: List<PassportSizeActivity.ColorOption>, initial: Int, onSelect: (Int) -> Unit) {
        container.removeAllViews()
        var selectedValue = initial
        val midpoint = (colors.size + 1) / 2
        val row1 = colors.subList(0, midpoint)
        val row2 = colors.subList(midpoint, colors.size)

        fun makeRow(rowColors: List<PassportSizeActivity.ColorOption>): android.widget.LinearLayout {
            val row = android.widget.LinearLayout(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            for (c in rowColors) {
                val chip = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(chipSize, chipSize).apply {
                        marginStart = chipMargin
                        marginEnd = chipMargin
                        topMargin = chipMargin / 2
                        bottomMargin = chipMargin / 2
                    }
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = chipCorner.toFloat()
                        if (c.isOpaque) setColor(c.argb) else setColor(android.graphics.Color.TRANSPARENT)
                        if (c.argb == android.graphics.Color.WHITE || !c.isOpaque) {
                            setStroke(2, android.graphics.Color.parseColor("#FFBDBDBD"))
                        }
                    }
                    background = bg
                    contentDescription = c.name
                    setOnClickListener {
                        selectedValue = c.argb
                        onSelect(c.argb)
                        for (r in 0 until container.childCount) {
                            val rowView = container.getChildAt(r) as android.widget.LinearLayout
                            for (i in 0 until rowView.childCount) {
                                val child = rowView.getChildAt(i)
                                val flatIdx = (if (r == 0) 0 else midpoint) + i
                                val colorOpt = colors.getOrNull(flatIdx) ?: return@setOnClickListener
                                child.alpha = if (colorOpt.argb == selectedValue) 1f else 0.4f
                            }
                        }
                    }
                }
                chip.alpha = if (c.argb == selectedValue) 1f else 0.4f
                row.addView(chip)
            }
            return row
        }

        container.addView(makeRow(row1))
        if (row2.isNotEmpty()) {
            container.addView(makeRow(row2))
        }
    }

    addColorChips(fillColors, colorOptions, selectedFillColor ?: android.graphics.Color.WHITE) { selectedFillColor = it }

    rootView.findViewById<View>(R.id.btn_cancel_final)?.setOnClickListener { dialog.dismiss() }

    rootView.findViewById<View>(R.id.btn_apply_final)?.setOnClickListener { v ->
        v.performHapticClick()
        val feather = if (ckFeather.isChecked) seekFeather.progress else 0
        val fill = if (ckFill.isChecked) selectedFillColor else null
        val outline = if (ckOutline.isChecked) seekOutline.progress else 0

        applyFinalTouches(featherRadius = feather, fillColor = fill, outlineRemovePx = outline)
        dialog.dismiss()
    }

    dialog.show()
}

fun PassportSizeActivity.applyFinalTouches(featherRadius: Int = 0, fillColor: Int? = null, outlineRemovePx: Int = 0) {
    val activity = this
    val idx = selectedPersonIndex
    val current = currentBitmap ?: return
    saveUndoState()

    val baseBitmap = if (fillColor != null && preFillBitmap != null) {
        preFillBitmap!!
    } else {
        current
    }
    val isFirstFill = fillColor != null && preFillBitmap == null

    binding.progressBar.showLoading(true)
    CoroutineScope(Dispatchers.Main).launch {
        var preFillResult: Bitmap? = null
        val result = withContext(Dispatchers.Default) {
            var result = baseBitmap

            if (isFirstFill || fillColor == null) {
                if (outlineRemovePx > 0)
                    result = ImageProcessor.removeOutline(result, outlineRemovePx)
                if (featherRadius > 0)
                    result = ImageProcessor.featherEdges(result, featherRadius)
            }

            if (isFirstFill) {
                preFillResult = result.copy(Bitmap.Config.ARGB_8888, true)
            }

            if (fillColor != null) {
                result = ImageProcessor.fillBackground(result, fillColor)
            }
            result
        }
        personBitmaps[idx] = result
        updatePersonPreview()
        clearFullPageState()
        if (isFirstFill) {
            preFillBitmap = preFillResult
        } else if (fillColor == null) {
            preFillBitmap = null
        }
        updatePersonSelector()
        binding.progressBar.showLoading(false)
        Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()
    }
}