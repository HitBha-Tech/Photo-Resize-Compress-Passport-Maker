package com.hitbha.photo.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import java.io.File

fun View.performHapticClick() {
    performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
}

fun Activity.getColorAttr(attr: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val color = ta.getColor(0, 0)
    ta.recycle()
    return color
}

fun Context.getColorAttrCompat(attr: Int): Int {
    val ta = theme.obtainStyledAttributes(intArrayOf(attr))
    val color = ta.getColor(0, 0)
    ta.recycle()
    return color
}

fun Context.getFileUri(file: File): Uri {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    } else {
        Uri.fromFile(file)
    }
}

fun Activity.updateStatusBarStyle(rootView: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val isNightMode = ThemeManager.isNightMode(this)
        WindowInsetsControllerCompat(window, rootView).isAppearanceLightStatusBars = !isNightMode
    }
}

fun setupWindowInsets(rootView: View, topBar: View, bottomBar: View? = null) {
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        topBar.updatePadding(top = systemBars.top)
        if (bottomBar != null) {
            bottomBar.updatePadding(bottom = systemBars.bottom)
        }
        insets
    }
}

fun View.showLoading(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

fun ViewGroup.updateBottomMargin(extraBottom: Int) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    lp.bottomMargin = lp.bottomMargin + extraBottom
    layoutParams = lp
}
