package com.hitbha.photo.ui.editor

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.utils.performHapticClick

fun EditorActivity.showFinalTouchesDialog() {
    val bitmap = viewModel.currentState?.currentBitmap ?: run {
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

    val ckFeather = rootView.findViewById<CheckBox>(R.id.ck_feather)
    val seekFeather = rootView.findViewById<SeekBar>(R.id.seek_feather)
    val tvFeather = rootView.findViewById<TextView>(R.id.tv_feather_value)

    val ckFill = rootView.findViewById<CheckBox>(R.id.ck_fill_bg)
    val fillColors = rootView.findViewById<LinearLayout>(R.id.fill_colors)

    val ckOutline = rootView.findViewById<CheckBox>(R.id.ck_outline)
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

    fun addColorChips(container: LinearLayout, colors: List<EditorActivity.ColorOption>, initial: Int, onSelect: (Int) -> Unit) {
        val act = this
        container.removeAllViews()
        var selectedValue = initial
        val midpoint = (colors.size + 1) / 2
        val row1 = colors.subList(0, midpoint)
        val row2 = colors.subList(midpoint, colors.size)

        fun makeRow(rowColors: List<EditorActivity.ColorOption>): LinearLayout {
            val row = LinearLayout(act).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            for (c in rowColors) {
                val chip = View(act).apply {
                    layoutParams = LinearLayout.LayoutParams(chipSize, chipSize).apply {
                        marginStart = chipMargin
                        marginEnd = chipMargin
                        topMargin = chipMargin / 2
                        bottomMargin = chipMargin / 2
                    }
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = chipCorner.toFloat()
                        if (c.isOpaque) setColor(c.argb) else setColor(Color.TRANSPARENT)
                        if (c.argb == Color.WHITE || !c.isOpaque) {
                            setStroke(2, Color.parseColor("#FFBDBDBD"))
                        }
                    }
                    background = bg
                    contentDescription = c.name
                    setOnClickListener {
                        selectedValue = c.argb
                        onSelect(c.argb)
                        for (r in 0 until container.childCount) {
                            val rowView = container.getChildAt(r) as LinearLayout
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

    addColorChips(fillColors, colorOptions, selectedFillColor ?: Color.WHITE) { selectedFillColor = it }

    rootView.findViewById<View>(R.id.btn_cancel_final)?.setOnClickListener { dialog.dismiss() }

    rootView.findViewById<View>(R.id.btn_apply_final)?.setOnClickListener { v ->
        v.performHapticClick()
        val feather = if (ckFeather.isChecked) seekFeather.progress else 0
        val fill = if (ckFill.isChecked) selectedFillColor else null
        val outline = if (ckOutline.isChecked) seekOutline.progress else 0

        viewModel.applyFinalTouches(
            featherRadius = feather,
            fillColor = fill,
            outlineRemovePx = outline
        )
        dialog.dismiss()
    }

    dialog.show()
}
