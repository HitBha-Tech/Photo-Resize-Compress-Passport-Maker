package com.hitbha.photo.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

enum class ToolMode { NONE, ERASER, COLOR_ERASE, MAGIC_ERASE }

enum class MagicErasePhase { PICK_COLOR, ERASE }

class EraseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageLeft = 0f
    private var imageTop = 0f
    private var imageRight = 0f
    private var imageBottom = 0f
    private var bitmapWidth = 0
    private var bitmapHeight = 0

    var brushRadius = 30f
    var toolMode = ToolMode.NONE
    var magicPhase = MagicErasePhase.PICK_COLOR
        private set

    private var magicColor: Int = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    private var currentX = -1f
    private var currentY = -1f
    private var isDrawing = false

    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x50FF0000
        style = Paint.Style.FILL
    }
    private val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val cursorInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FF0000.toInt()
        style = Paint.Style.FILL
    }

    private val colorEraseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x50FF0000.toInt()
        style = Paint.Style.FILL
    }
    private val colorEraseStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val magicPickFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5000E676
        style = Paint.Style.FILL
    }
    private val magicPickStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E676.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val magicCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E676.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val magicCursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val magicCursorStrokeInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val magicCursorStrokeOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    var onErasePoints: ((List<Pair<Float, Float>>) -> Unit)? = null
    var onEraseEnd: (() -> Unit)? = null
    var onColorEraseTap: ((Float, Float) -> Unit)? = null

    fun setMagicColor(color: Int) {
        magicColor = color
        magicPhase = MagicErasePhase.ERASE
        invalidate()
    }

    fun resetMagicPhase() {
        magicColor = 0
        magicPhase = MagicErasePhase.PICK_COLOR
        invalidate()
    }

    val magicPickedColor: Int
        get() = magicColor

    private val pointsBatch = mutableListOf<Pair<Float, Float>>()
    private var lastBatchTime = 0L

    init {
        isClickable = true
    }

    fun setImageBounds(left: Float, top: Float, right: Float, bottom: Float, bmpW: Int, bmpH: Int) {
        imageLeft = left
        imageTop = top
        imageRight = right
        imageBottom = bottom
        bitmapWidth = bmpW
        bitmapHeight = bmpH
        invalidate()
    }

    private fun touchToBitmap(tx: Float, ty: Float): Pair<Float, Float>? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val dispW = imageRight - imageLeft
        val dispH = imageBottom - imageTop
        if (dispW <= 0 || dispH <= 0) return null
        val bx = (tx - imageLeft) * bitmapWidth / dispW
        val by = (ty - imageTop) * bitmapHeight / dispH
        if (bx < 0 || bx >= bitmapWidth || by < 0 || by >= bitmapHeight) return null
        return Pair(bx, by)
    }

    private fun ensureMask() {
        if (maskBitmap == null && width > 0 && height > 0) {
            maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            maskCanvas = Canvas(maskBitmap!!)
        }
    }

    private fun drawOnMask(cx: Float, cy: Float) {
        ensureMask()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x60FF0000
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        maskCanvas?.drawCircle(cx, cy, brushRadius, p)
    }

    private fun drawLineOnMask(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val steps = (dist / (brushRadius * 0.35f)).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            drawOnMask(x1 + dx * t, y1 + dy * t)
        }
    }

    fun clearMask() {
        maskBitmap?.recycle()
        maskBitmap = null
        maskCanvas = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maskBitmap?.recycle()
        maskBitmap = null
        maskCanvas = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        maskBitmap?.recycle()
        maskBitmap = null
        maskCanvas = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        maskBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (currentX < 0 || currentY < 0 || toolMode == ToolMode.NONE) return
        when (toolMode) {
            ToolMode.ERASER -> {
                canvas.drawCircle(currentX, currentY, brushRadius, cursorFill)
                canvas.drawCircle(currentX, currentY, brushRadius, cursorStroke)
                canvas.drawCircle(currentX, currentY, brushRadius * 0.25f, cursorInner)
            }
            ToolMode.COLOR_ERASE -> {
                val r = 20f
                canvas.drawCircle(currentX, currentY, r, colorEraseFill)
                canvas.drawCircle(currentX, currentY, r, colorEraseStroke)
                canvas.drawLine(currentX - r - 6, currentY, currentX + r + 6, currentY, crossPaint)
                canvas.drawLine(currentX, currentY - r - 6, currentX, currentY + r + 6, crossPaint)
            }
            ToolMode.MAGIC_ERASE -> {
                if (magicPhase == MagicErasePhase.PICK_COLOR) {
                    val r = 20f
                    canvas.drawCircle(currentX, currentY, r, magicPickFill)
                    canvas.drawCircle(currentX, currentY, r, magicPickStroke)
                    canvas.drawLine(currentX - r - 6, currentY, currentX + r + 6, currentY, magicCrossPaint)
                    canvas.drawLine(currentX, currentY - r - 6, currentX, currentY + r + 6, magicCrossPaint)
                } else {
                    val cursorColor = if (Color.alpha(magicColor) == 0) 0xFFFF0000.toInt() else magicColor
                    magicCursorFill.color = 0x66000000 or (cursorColor and 0x00FFFFFF)
                    canvas.drawCircle(currentX, currentY, brushRadius, magicCursorFill)
                    canvas.drawCircle(currentX, currentY, brushRadius, magicCursorStrokeInner)
                    canvas.drawCircle(currentX, currentY, brushRadius + 2f, magicCursorStrokeOuter)
                }
            }
            else -> {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (toolMode == ToolMode.NONE) return false
        if (toolMode == ToolMode.MAGIC_ERASE) return onMagicTouchEvent(event)
        parent.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentX = event.x
                currentY = event.y
                val bmp = touchToBitmap(event.x, event.y) ?: return false
                if (toolMode == ToolMode.COLOR_ERASE) {
                    onColorEraseTap?.invoke(bmp.first, bmp.second)
                    invalidate()
                    return true
                }
                isDrawing = true
                pointsBatch.clear()
                pointsBatch.add(bmp)
                drawOnMask(event.x, event.y)
                lastBatchTime = System.currentTimeMillis()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val prevX = currentX
                val prevY = currentY
                currentX = event.x
                currentY = event.y
                if (!isDrawing) {
                    val bmp = touchToBitmap(event.x, event.y)
                    if (bmp != null) {
                        isDrawing = true
                        pointsBatch.clear()
                        pointsBatch.add(bmp)
                        drawOnMask(event.x, event.y)
                        lastBatchTime = System.currentTimeMillis()
                    }
                    invalidate()
                    return true
                }
                val bmp = touchToBitmap(event.x, event.y) ?: return true
                pointsBatch.add(bmp)
                drawLineOnMask(prevX, prevY, event.x, event.y)
                val now = System.currentTimeMillis()
                if (now - lastBatchTime >= 30) {
                    if (pointsBatch.isNotEmpty()) {
                        onErasePoints?.invoke(pointsBatch.toList())
                        pointsBatch.clear()
                    }
                    lastBatchTime = now
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    if (pointsBatch.isNotEmpty()) {
                        onErasePoints?.invoke(pointsBatch.toList())
                        pointsBatch.clear()
                    }
                    onEraseEnd?.invoke()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onMagicTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentX = event.x
                currentY = event.y
                downX = event.x
                downY = event.y
                val bmp = touchToBitmap(event.x, event.y)
                if (magicPhase == MagicErasePhase.PICK_COLOR) {
                    if (bmp != null) onColorEraseTap?.invoke(bmp.first, bmp.second)
                    invalidate()
                    return true
                }
                pointsBatch.clear()
                if (bmp != null) pointsBatch.add(bmp)
                lastBatchTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val prevX = currentX
                val prevY = currentY
                currentX = event.x
                currentY = event.y
                if (magicPhase != MagicErasePhase.ERASE) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (!isDrawing && dx * dx + dy * dy > touchSlop * touchSlop) {
                    isDrawing = true
                    drawOnMask(downX, downY)
                    lastBatchTime = System.currentTimeMillis()
                }
                if (isDrawing) {
                    val bmp = touchToBitmap(event.x, event.y) ?: return true
                    pointsBatch.add(bmp)
                    drawLineOnMask(prevX, prevY, event.x, event.y)
                    val now = System.currentTimeMillis()
                    if (now - lastBatchTime >= 30) {
                        if (pointsBatch.isNotEmpty()) {
                            onErasePoints?.invoke(pointsBatch.toList())
                            pointsBatch.clear()
                        }
                        lastBatchTime = now
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    if (pointsBatch.isNotEmpty()) {
                        onErasePoints?.invoke(pointsBatch.toList())
                        pointsBatch.clear()
                    }
                    onEraseEnd?.invoke()
                } else if (event.actionMasked == MotionEvent.ACTION_UP &&
                    magicPhase == MagicErasePhase.ERASE) {
                    val bmp = touchToBitmap(event.x, event.y)
                    if (bmp != null) onColorEraseTap?.invoke(bmp.first, bmp.second)
                    pointsBatch.clear()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
