package com.hitbha.photo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object StatePersistence {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val PREFS = "state_persistence"
    private const val MAX_CACHE_SIZE = 100L * 1024 * 1024
    private const val TTL_MS = 60L * 60 * 1000
    private const val KEY_PASSPORT = "passport_paths"
    private const val KEY_PASSPORT_UNDO = "passport_undo_paths"
    private const val KEY_PASSPORT_REDO = "passport_redo_paths"
    private const val KEY_PASSPORT_INDEX = "passport_selected_index"
    private const val KEY_PASSPORT_LAYOUT = "passport_layout_paths"
    private const val KEY_PASSPORT_PAGE_W = "passport_page_w"
    private const val KEY_PASSPORT_PAGE_H = "passport_page_h"
    private const val KEY_PASSPORT_COLS = "passport_cols"
    private const val KEY_PASSPORT_ROWS = "passport_rows"
    private const val KEY_PASSPORT_PHOTO_COUNT = "passport_photo_count"
    private const val KEY_PASSPORT_PAGE_INDEX = "passport_page_index"
    private const val KEY_PASSPORT_FULLPAGE = "passport_fullpage_mode"
    private const val KEY_PASSPORT_MARGIN = "passport_margin"
    private const val KEY_PASSPORT_FILL_COLOR = "passport_fill_color"

    private const val KEY_EDITOR_STATE = "editor_state_paths"
    private const val KEY_EDITOR_UNDO = "editor_undo_paths"
    private const val KEY_EDITOR_REDO = "editor_redo_paths"

    private fun cacheDir(ctx: Context): File {
        val dir = File(ctx.cacheDir, "state_persistence")
        dir.mkdirs()
        return dir
    }

    fun cleanupOldCache(ctx: Context) {
        val dir = File(ctx.cacheDir, "state_persistence")
        if (!dir.exists()) return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > TTL_MS) {
                file.delete()
            }
        }
    }

    private fun enforceCacheLimit(ctx: Context) {
        val dir = File(ctx.cacheDir, "state_persistence")
        if (!dir.exists()) return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_SIZE) return
        for (file in files) {
            if (totalSize <= MAX_CACHE_SIZE) break
            val size = file.length()
            if (file.delete()) totalSize -= size
        }
    }

    private fun saveBitmap(file: File, bmp: Bitmap) {
        FileOutputStream(file).use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP
            }
            bmp.compress(format, 95, out)
        }
    }

    fun saveBitmaps(dir: File, prefix: String, bitmaps: List<Bitmap>): List<String> {
        val paths = mutableListOf<String>()
        for ((i, bmp) in bitmaps.withIndex()) {
            val file = File(dir, "${prefix}_${i}.png")
            saveBitmap(file, bmp)
            paths.add(file.absolutePath)
        }
        return paths
    }

    fun loadBitmaps(paths: List<String>, maxDimension: Int = 2048): List<Bitmap> {
        return paths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) decodeBitmapSafely(path, maxDimension) else null
        }
    }

    private fun decodeBitmapSafely(path: String, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, opts)
        } catch (t: Throwable) {
            null
        }
    }

    private fun saveStrings(ctx: Context, key: String, values: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, values.joinToString(","))
            .apply()
    }

    private fun loadStrings(ctx: Context, key: String): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",").filter { it.isNotEmpty() }
    }

    private fun saveInt(ctx: Context, key: String, value: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(key, value).apply()
    }

    private fun loadInt(ctx: Context, key: String, default: Int = 0): Int {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key, default)
    }

    private fun saveBoolean(ctx: Context, key: String, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    private fun loadBoolean(ctx: Context, key: String, default: Boolean = false): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, default)
    }

    private fun clearKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    private fun deleteFiles(paths: List<String>) {
        for (path in paths) {
            try { File(path).delete() } catch (_: Exception) {}
        }
    }

    // ============ PASSPORT ============

    fun savePassportState(
        ctx: Context,
        personBitmaps: List<Bitmap>,
        selectedIndex: Int
    ) {
        ioScope.launch {
            val dir = cacheDir(ctx)
            val personPaths = saveBitmaps(dir, "pp_person", personBitmaps)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PASSPORT, personPaths.joinToString(","))
                .putInt(KEY_PASSPORT_INDEX, selectedIndex)
                .apply()
            enforceCacheLimit(ctx)
        }
    }

    data class PassportSavedState(
        val personBitmaps: List<Bitmap>,
        val selectedIndex: Int
    )

    fun loadPassportState(ctx: Context): PassportSavedState? {
        val personPaths = loadStrings(ctx, KEY_PASSPORT)
        if (personPaths.isEmpty()) return null
        val personBitmaps = loadBitmaps(personPaths)
        if (personBitmaps.isEmpty()) return null

        return PassportSavedState(
            personBitmaps = personBitmaps,
            selectedIndex = loadInt(ctx, KEY_PASSPORT_INDEX)
        )
    }

    fun clearPassportState(ctx: Context) {
        deleteFiles(loadStrings(ctx, KEY_PASSPORT))
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        prefs.remove(KEY_PASSPORT).remove(KEY_PASSPORT_INDEX).apply()
    }

    // ============ EDITOR ============

    fun saveEditorState(
        ctx: Context,
        original: Bitmap?,
        source: Bitmap?,
        current: Bitmap?,
        preFill: Bitmap?,
        undoStack: List<Bitmap>,
        redoStack: List<Bitmap>,
        compressQuality: Int
    ) {
        ioScope.launch {
            val dir = cacheDir(ctx)
            val paths = mutableListOf<String>()
            listOfNotNull("ed_orig" to original, "ed_src" to source, "ed_curr" to current, "ed_prefill" to preFill)
                .forEach { (prefix, bmp) ->
                    bmp?.let {
                        val file = File(dir, "$prefix.png")
                        saveBitmap(file, it)
                        paths.add("$prefix=${file.absolutePath}")
                    }
                }

            val undoPaths = saveBitmaps(dir, "ed_undo", undoStack)
            val redoPaths = saveBitmaps(dir, "ed_redo", redoStack)

            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_EDITOR_STATE, paths.joinToString(","))
                .putString(KEY_EDITOR_UNDO, undoPaths.joinToString(","))
                .putString(KEY_EDITOR_REDO, redoPaths.joinToString(","))
                .putInt("editor_quality", compressQuality)
                .apply()
            enforceCacheLimit(ctx)
        }
    }

    data class EditorSavedState(
        val original: Bitmap?,
        val source: Bitmap?,
        val current: Bitmap?,
        val preFill: Bitmap?,
        val undoStack: List<Bitmap>,
        val redoStack: List<Bitmap>,
        val compressQuality: Int
    )

    fun loadEditorState(ctx: Context): EditorSavedState? {
        val raw = loadStrings(ctx, KEY_EDITOR_STATE)
        if (raw.isEmpty()) return null
        val map = mutableMapOf<String, Bitmap>()
        for (entry in raw) {
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) {
                val f = File(parts[1])
                if (f.exists()) decodeBitmapSafely(parts[1], 2048)?.let { map[parts[0]] = it }
            }
        }
        if (map.isEmpty()) return null

        return EditorSavedState(
            original = map["ed_orig"],
            source = map["ed_src"],
            current = map["ed_curr"],
            preFill = map["ed_prefill"],
            undoStack = loadBitmaps(loadStrings(ctx, KEY_EDITOR_UNDO)),
            redoStack = loadBitmaps(loadStrings(ctx, KEY_EDITOR_REDO)),
            compressQuality = loadInt(ctx, "editor_quality", -1)
        )
    }

    fun clearEditorState(ctx: Context) {
        deleteFiles(loadStrings(ctx, KEY_EDITOR_STATE).map { it.substringAfter("=") })
        deleteFiles(loadStrings(ctx, KEY_EDITOR_UNDO))
        deleteFiles(loadStrings(ctx, KEY_EDITOR_REDO))
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        prefs.remove(KEY_EDITOR_STATE).remove(KEY_EDITOR_UNDO).remove(KEY_EDITOR_REDO)
            .remove("editor_quality").apply()
    }
}
