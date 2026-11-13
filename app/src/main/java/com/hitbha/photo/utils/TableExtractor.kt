package com.hitbha.photo.utils

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object TableExtractor {

    private data class Entry(val text: String, val box: Rect)

    /**
     * When a page has more visual columns than this it is treated as free-flowing
     * text and rebuilt as a single-column grid so nothing gets cut off.
     */
    private const val MAX_COLUMNS = 12

    /**
     * Recognizes text on each page and reconstructs a table (rows of cell strings).
     * Returns one grid per page; an empty grid means no text was recognized.
     */
    suspend fun extractTables(bitmaps: List<Bitmap>): List<List<List<String>>> =
        withContext(Dispatchers.IO) {
            val recognizer: TextRecognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                bitmaps.map { bmp ->
                    val image = InputImage.fromBitmap(bmp, 0)
                    val text = await(recognizer.process(image))
                    buildTable(text)
                }
            } finally {
                recognizer.close()
            }
        }

    private suspend fun await(task: Task<Text>): Text =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun buildTable(text: Text): List<List<String>> {
        val entries = mutableListOf<Entry>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    element.boundingBox?.let { box ->
                        if (element.text.isNotBlank()) entries.add(Entry(element.text.trim(), box))
                    }
                }
            }
        }
        if (entries.isEmpty()) return emptyList()

        val medianHeight = median(entries.map { it.box.height().toFloat() }).coerceAtLeast(4f)

        // Group words into visual lines using vertical overlap. Only a small gap
        // tolerance is allowed so separate table rows are never merged together.
        val lines = groupIntoLines(entries, medianHeight * 0.2f)
        if (lines.isEmpty()) return emptyList()

        // Within each line split words into cells using horizontal gaps.
        val cellLines = lines.map { line ->
            splitIntoCells(line.sortedBy { it.box.left }, medianHeight)
        }
        val allCells = cellLines.flatten()
        if (allCells.isEmpty()) return emptyList()

        // Detect global column boundaries across every line.
        val columns = clusterColumns(allCells)

        val grid = Array(cellLines.size) { Array(columns.size) { "" } }
        cellLines.forEachIndexed { rIdx, cells ->
            for (cell in cells) {
                val c = bestColumnIndex(cell, columns)
                if (c >= 0) {
                    val cellText = cell.joinToString(" ") { it.text }
                    grid[rIdx][c] = (grid[rIdx][c] + " " + cellText).trim()
                }
            }
        }

        // Drop trailing empty columns so sparse tables don't look half built.
        var lastCol = columns.size
        while (lastCol > 0 && grid.all { row -> row[lastCol - 1].isBlank() }) lastCol--
        if (lastCol == 0) return emptyList()

        return grid.map { row -> row.take(lastCol) }
    }

    private fun groupIntoLines(entries: List<Entry>, tolerance: Float): List<List<Entry>> {
        val sorted = entries.sortedBy { it.box.top }
        val lines = mutableListOf<MutableList<Entry>>()
        for (entry in sorted) {
            var best: MutableList<Entry>? = null
            var bestScore = Float.NEGATIVE_INFINITY
            for (line in lines) {
                val score = lineScore(line, entry)
                if (score > bestScore) {
                    bestScore = score
                    best = line
                }
            }
            if (best != null && bestScore >= -tolerance) {
                best.add(entry)
            } else {
                lines.add(mutableListOf(entry))
            }
        }
        return lines.sortedBy { line -> line.minOf { it.box.top } }
    }

    private fun lineScore(line: List<Entry>, e: Entry): Float {
        var lineTop = Int.MAX_VALUE
        var lineBottom = Int.MIN_VALUE
        for (en in line) {
            if (en.box.top < lineTop) lineTop = en.box.top
            if (en.box.bottom > lineBottom) lineBottom = en.box.bottom
        }
        val overlap = min(lineBottom, e.box.bottom) - max(lineTop, e.box.top)
        if (overlap > 0) return Float.MAX_VALUE
        val gap = if (e.box.top > lineBottom) {
            e.box.top - lineBottom
        } else {
            lineTop - e.box.bottom
        }
        return -gap.toFloat()
    }

    private fun splitIntoCells(line: List<Entry>, height: Float): List<List<Entry>> {
        val cells = mutableListOf<MutableList<Entry>>()
        var current = mutableListOf<Entry>()
        cells.add(current)
        var prevRight = line.first().box.right
        for (entry in line) {
            if (current.isNotEmpty()) {
                val gap = entry.box.left - prevRight
                if (gap > height * 0.5f) {
                    current = mutableListOf()
                    cells.add(current)
                }
            }
            current.add(entry)
            prevRight = entry.box.right
        }
        return cells
    }

    private fun clusterColumns(cells: List<List<Entry>>): List<IntRange> {
        val ranges = cells.map { cell ->
            val left = cell.minOf { it.box.left }
            val right = cell.maxOf { it.box.right }
            left until right.coerceAtLeast(left + 1)
        }
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val clusters = mutableListOf<MutableList<IntRange>>()
        for (r in sorted) {
            var best: MutableList<IntRange>? = null
            var bestOverlap = 0
            for (cluster in clusters) {
                val clusterLeft = cluster.minOf { it.first }
                val clusterRight = cluster.maxOf { it.last }
                val overlap = min(clusterRight, r.last) - max(clusterLeft, r.first)
                if (overlap > bestOverlap) {
                    bestOverlap = overlap
                    best = cluster
                }
            }
            if (best != null && bestOverlap > 0) {
                best.add(r)
            } else {
                clusters.add(mutableListOf(r))
            }
        }
        val ordered = clusters.sortedBy { it.minOf { r -> r.first } }
        return if (ordered.size in 1..MAX_COLUMNS) {
            ordered.map { c -> c.minOf { it.first } until c.maxOf { it.last } }
        } else {
            // Too many columns -> free flowing text, keep it in one column.
            listOf(
                ordered.minOf { it.minOf { r -> r.first } } until
                    ordered.maxOf { it.maxOf { r -> r.last } }
            )
        }
    }

    private fun bestColumnIndex(cell: List<Entry>, columns: List<IntRange>): Int {
        val left = cell.minOf { it.box.left }
        val right = cell.maxOf { it.box.right }
        var best = -1
        var bestOverlap = 0
        columns.forEachIndexed { i, c ->
            val overlap = min(c.last, right) - max(c.first, left)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                best = i
            }
        }
        if (best >= 0) return best
        val center = (left + right) / 2f
        var bestDist = Float.MAX_VALUE
        columns.forEachIndexed { i, c ->
            val cCenter = (c.first + c.last) / 2f
            val d = abs(cCenter - center)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }
}
