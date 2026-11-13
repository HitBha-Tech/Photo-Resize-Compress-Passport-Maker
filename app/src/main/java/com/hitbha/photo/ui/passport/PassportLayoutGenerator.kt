package com.hitbha.photo.ui.passport

import android.app.Dialog
import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import com.hitbha.photo.R
import com.hitbha.photo.databinding.DialogPassportPageSizeBinding
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.performHapticClick
import com.hitbha.photo.utils.showLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PassportSizeActivity.showPageSizeDialog() {
    val dialog = Dialog(this)
    val dialogBinding = DialogPassportPageSizeBinding.inflate(layoutInflater)
    dialog.setContentView(dialogBinding.root)
    dialog.window?.setLayout(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
    )

    data class PageOption(
        val rowId: Int,             val rb: android.widget.RadioButton,
        val cols: Int, val totalRows: Int, val pageWidthInch: Float, val pageHeightInch: Float
    )

    val options = listOf(
        PageOption(R.id.row_4x6, dialogBinding.rb4x6, 2, 4, 4f, 6f),
        PageOption(R.id.row_4x6_10, dialogBinding.rb4x610, 2, 5, 4f, 6f),
        PageOption(R.id.row_a4, dialogBinding.rbA4, 4, 8, 8.27f, 11.69f),
        PageOption(R.id.row_b4, dialogBinding.rbB4, 5, 8, 8.27f, 11.69f)
    )

    fun selectRow(rowId: Int) {
        selectedPageSizeRow = rowId
        for (opt in options) {
            opt.rb.isChecked = opt.rowId == rowId
        }
        val opt = options.find { it.rowId == rowId }
        if (opt != null) {
            rowsPerGroup = opt.totalRows
            dialogBinding.tvRawValue.text = rowsPerGroup.toString()
        }
    }

    dialogBinding.row4x6.setOnClickListener { selectRow(R.id.row_4x6) }
    dialogBinding.row4x610.setOnClickListener { selectRow(R.id.row_4x6_10) }
    dialogBinding.rowA4.setOnClickListener { selectRow(R.id.row_a4) }
    dialogBinding.rowB4.setOnClickListener { selectRow(R.id.row_b4) }

    dialogBinding.btnRawDecrease.setOnClickListener {
        rowsPerGroup = (rowsPerGroup - 1).coerceAtLeast(1)
        dialogBinding.tvRawValue.text = rowsPerGroup.toString()
    }
    dialogBinding.btnRawIncrease.setOnClickListener {
        rowsPerGroup = (rowsPerGroup + 1).coerceAtMost(10)
        dialogBinding.tvRawValue.text = rowsPerGroup.toString()
    }

    dialogBinding.btnGenerate.setOnClickListener { view ->
        view.performHapticClick()
        if (personBitmaps.isEmpty()) return@setOnClickListener

        val opt = options.find { it.rowId == selectedPageSizeRow } ?: return@setOnClickListener
        val pageWidthPx = (opt.pageWidthInch * ImageProcessor.PASSPORT_DPI).toInt()
        val pageHeightPx = (opt.pageHeightInch * ImageProcessor.PASSPORT_DPI).toInt()
        val photoCount = opt.cols * opt.totalRows
        val fillConsec = true
        val rpg = rowsPerGroup
        val alignTop = true
        val showCutLines = opt.rowId != R.id.row_b4 && opt.rowId != R.id.row_4x6

        generateLayout(
            personBitmaps.toList(), pageWidthPx, pageHeightPx,
            opt.cols, opt.totalRows, photoCount,
            alignTop = alignTop, fillConsecutive = fillConsec,
            rowsPerGroup = rpg, fillCells = opt.cols >= 5,
            showCutLines = showCutLines,
            noSpacing = false,
            spacingPx = null,
            spacingYOverride = if (opt.rowId == R.id.row_4x6_10) 2 else null
        )
        dialog.dismiss()
    }

    selectRow(R.id.row_a4)
    dialog.show()
}

fun PassportSizeActivity.clearFullPageState() {
    binding.imagePreview.visibility = View.VISIBLE
    preFillBitmap = null
    layoutBitmaps.clear()
}

fun PassportSizeActivity.showPage(index: Int) {
    if (layoutBitmaps.isEmpty()) return
    val idx = index.coerceIn(0, layoutBitmaps.size - 1)
    if (idx == currentPageIndex && idx == index) return
    currentPageIndex = idx
    binding.imagePreview.setImageBitmap(layoutBitmaps[currentPageIndex])
    binding.cropOverlay.visibility = View.GONE
    resetZoom()
    updatePageNavigation()
}

private fun PassportSizeActivity.updatePageNavigation() {
    val total = layoutBitmaps.size
    if (total <= 1) {
        binding.pageNavigation.visibility = View.GONE
    } else {
        binding.pageNavigation.visibility = View.VISIBLE
        binding.tvPageIndicator.text = "${currentPageIndex + 1} / $total"
        binding.btnPagePrev.isEnabled = currentPageIndex > 0
        binding.btnPageNext.isEnabled = currentPageIndex < total - 1
    }
}

fun PassportSizeActivity.generateLayout(
    photos: List<Bitmap>,
    pageWidthPx: Int,
    pageHeightPx: Int,
    columns: Int,
    rows: Int,
    photoCount: Int = columns * rows,
    alignTop: Boolean = false,
    fillConsecutive: Boolean = false,
    rowsPerGroup: Int = 0,
    fillCells: Boolean = false,
    showCutLines: Boolean = true,
    noSpacing: Boolean = false,
    spacingPx: Int? = null,
    spacingYOverride: Int? = null
) {
    val activity = this
    clearFullPageState()
    binding.progressBar.showLoading(true)
    currentPageWidthPx = pageWidthPx
    currentPageHeightPx = pageHeightPx
    currentColumns = columns
    currentRows = rows
    currentPhotoCount = photoCount

    CoroutineScope(Dispatchers.Main).launch {
        val pages = withContext(Dispatchers.IO) {
            val resultPages = mutableListOf<Bitmap>()
            val totalConceptualRows = if (fillConsecutive && rowsPerGroup > 0 && photos.isNotEmpty()) {
                photos.size * rowsPerGroup
            } else {
                rows
            }
            var globalRowOffset = 0
            var lastPageSignature: List<Int>? = null
            while (globalRowOffset < totalConceptualRows) {
                val signature = if (rowsPerGroup > 0 && photos.isNotEmpty()) {
                    val actualRowsToDraw = (totalConceptualRows - globalRowOffset).coerceIn(0, rows)
                    (0 until actualRowsToDraw).map { r ->
                        ((r + globalRowOffset) / rowsPerGroup).coerceAtMost(photos.size - 1)
                    }
                } else {
                    null
                }
                if (signature != null && signature == lastPageSignature) {
                    globalRowOffset += rows
                    if (resultPages.size > 30) break
                    continue
                }
                val page = ImageProcessor.createPassportLayout(
                    photos = photos,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    columns = columns,
                    rows = rows,
                    photoCount = photoCount,
                    alignTop = alignTop,
                    fillConsecutive = fillConsecutive,
                    rowsPerGroup = rowsPerGroup,
                    globalRowOffset = globalRowOffset,
                    fillCells = fillCells,
                    showCutLines = showCutLines,
                    noSpacing = noSpacing,
                    spacingPxOverride = spacingPx,
                    spacingYOverride = spacingYOverride
                )
                resultPages.add(page)
                lastPageSignature = signature
                globalRowOffset += rows
                if (resultPages.size > 30) break
            }
            resultPages
        }

        layoutBitmaps.clear()
        layoutBitmaps.addAll(pages)
        currentPageIndex = 0
        binding.cropOverlay.visibility = View.GONE
        resetZoom()
        binding.imagePreview.setImageBitmap(pages.firstOrNull())
        binding.btnSave.isEnabled = pages.isNotEmpty()
        binding.btnShare.isEnabled = pages.isNotEmpty()
        binding.progressBar.showLoading(false)
        updatePageNavigation()
        val total = columns * rows
        Toast.makeText(activity, getString(R.string.layout_generated, columns, rows, total), Toast.LENGTH_SHORT).show()
    }
}

fun PassportSizeActivity.exportBitmaps(): List<Bitmap> {
    return layoutBitmaps.toList()
}

val PassportSizeActivity.hasPageLayout: Boolean
    get() = layoutBitmaps.isNotEmpty()