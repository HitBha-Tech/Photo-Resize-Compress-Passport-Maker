package com.hitbha.photo.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.hitbha.photo.R
import kotlin.math.sqrt

class FullPageOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var photo: Bitmap? = null
    private var pageWidth = 0
    private var pageHeight = 0
    private var marginPx = 20

    private var photoDisplayRect = RectF()

    private var stretchLeft = 0f
    private var stretchRight = 0f
    private var stretchTop = 0f
    private var stretchBottom = 0f

    private var panOffsetX = 0f
    private var panOffsetY = 0f

    private var scaleToFill = false

    private var scaleMultiplier = 1f
    private var minScale = 0.25f
    private var maxScale = 4f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = -1
    private var dragMode = DragMode.NONE
    private var isDragging = false
    private var isPinching = false

    private val scaleDetector: ScaleGestureDetector

    private var lastSpan = 0f

    private enum class DragMode { NONE, STRETCH_LEFT, STRETCH_RIGHT, STRETCH_TOP, STRETCH_BOTTOM, PAN }

    private val bgPaint = Paint().apply { color = Color.WHITE }
    private val dashedPaint = Paint().apply {
        color = Color.parseColor("#999999")
        alpha = 100
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val imagePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    private val dragHintPaint = Paint().apply {
        color = Color.parseColor("#757575")
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val topHintPaint = Paint().apply {
        color = Color.parseColor("#757575")
        textSize = 34f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    var onOffsetChanged: ((offsetXPx: Int, offsetYPx: Int) -> Unit)? = null
    var onScaleChanged: ((scale: Float) -> Unit)? = null
    var onInteractionEnd: (() -> Unit)? = null
    var onInteractionStart: (() -> Unit)? = null

    private var pageScale = 1f
    private var baseScale = 1f
    private var contentW = 0f
    private var contentH = 0f
    private var pageLeft = 0f
    private var pageTop = 0f
    private var photoW = 0f
    private var photoH = 0f
    private var marginPageLeft = 0f
    private var marginPageTop = 0f
    private var marginPageRight = 0f
    private var marginPageBottom = 0f

    fun setPhoto(bitmap: Bitmap) {
        photo = bitmap
        post { recalc(); invalidate() }
    }

    fun setPageSize(widthPx: Int, heightPx: Int, margin: Int = 20, fill: Boolean = false) {
        pageWidth = widthPx
        pageHeight = heightPx
        marginPx = margin
        scaleToFill = fill
        post { recalc(); invalidate() }
    }

    fun getPanOffsetX(): Int = panOffsetX.toInt()
    fun getPanOffsetY(): Int = panOffsetY.toInt()
    fun getScaleMultiplier(): Float = scaleMultiplier
    fun getStretchLeft(): Int = stretchLeft.toInt()
    fun getStretchRight(): Int = stretchRight.toInt()
    fun getStretchTop(): Int = stretchTop.toInt()
    fun getStretchBottom(): Int = stretchBottom.toInt()

    fun getOffsetX(): Int = panOffsetX.toInt()
    fun getOffsetY(): Int = panOffsetY.toInt()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalc()
        invalidate()
    }

    private fun recalc() {
        if (width <= 0 || height <= 0) return
        val bmp = photo ?: return
        if (pageWidth <= 0 || pageHeight <= 0) return

        contentW = pageWidth - 2f * marginPx
        contentH = pageHeight - 2f * marginPx

        val vpW = width.toFloat().coerceAtLeast(1f)
        val vpH = height.toFloat().coerceAtLeast(1f)

        pageScale = minOf(vpW / pageWidth, vpH / pageHeight)
        val dispPageW = pageWidth * pageScale
        val dispPageH = pageHeight * pageScale
        pageLeft = (vpW - dispPageW) / 2f
        pageTop = (vpH - dispPageH) / 2f

        baseScale = if (scaleToFill) {
            maxOf(contentW / bmp.width, contentH / bmp.height)
        } else {
            minOf(contentW / bmp.width, contentH / bmp.height)
        }
        photoW = bmp.width * baseScale
        photoH = bmp.height * baseScale

        marginPageLeft = pageLeft + marginPx * pageScale
        marginPageTop = pageTop + marginPx * pageScale
        marginPageRight = pageLeft + dispPageW - marginPx * pageScale
        marginPageBottom = pageTop + dispPageH - marginPx * pageScale

        resetState()
    }

    private fun resetState() {
        stretchLeft = 0f
        stretchRight = 0f
        stretchTop = 0f
        stretchBottom = 0f
        panOffsetX = 0f
        panOffsetY = 0f
        scaleMultiplier = 1f
        photoDisplayRect = calcDisplayRect()
    }

    private fun calcDisplayRect(): RectF {
        val effScale = scaleMultiplier.coerceIn(minScale, maxScale)
        val scaledW = photoW * effScale
        val scaledH = photoH * effScale

        val baseLeft = marginPageLeft + (contentW * pageScale - scaledW * pageScale) / 2f
        val baseTop = marginPageTop + (contentH * pageScale - scaledH * pageScale) / 2f

        val minPhotoSize = 20f

        var left = baseLeft + stretchLeft * pageScale + panOffsetX
        var top = baseTop + stretchTop * pageScale + panOffsetY
        var right = baseLeft + scaledW * pageScale + stretchRight * pageScale + panOffsetX
        var bottom = baseTop + scaledH * pageScale + stretchBottom * pageScale + panOffsetY

        left = left.coerceIn(marginPageLeft, marginPageRight - minPhotoSize)
        top = top.coerceIn(marginPageTop, marginPageBottom - minPhotoSize)
        right = right.coerceIn(marginPageLeft + minPhotoSize, marginPageRight)
        bottom = bottom.coerceIn(marginPageTop + minPhotoSize, marginPageBottom)

        if (right - left < minPhotoSize) right = (left + minPhotoSize).coerceAtMost(marginPageRight)
        if (bottom - top < minPhotoSize) bottom = (top + minPhotoSize).coerceAtMost(marginPageBottom)

        return RectF(left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dispPageW = pageWidth * pageScale
        val dispPageH = pageHeight * pageScale

        canvas.drawRect(pageLeft, pageTop, pageLeft + dispPageW, pageTop + dispPageH, bgPaint)

        canvas.drawRect(marginPageLeft, marginPageTop, marginPageRight, marginPageBottom, dashedPaint)

        val displayRect = photoDisplayRect
        if (photo != null) {
            canvas.save()
            canvas.clipRect(pageLeft, pageTop, pageLeft + dispPageW, pageTop + dispPageH)
            canvas.drawBitmap(photo!!, null, displayRect, imagePaint)
            canvas.restore()
        }

        if (!isDragging && !isPinching) {
            val topTextY = pageTop - topHintPaint.textSize - 8f
            if (topTextY > 0f) {
                canvas.drawText(context.getString(R.string.fullpage_three_finger_hint), width / 2f, topTextY, topHintPaint)
            }
            val cx = (marginPageLeft + marginPageRight) / 2f
            val textY = marginPageBottom + dragHintPaint.textSize + 8f
            if (textY < height.toFloat()) {
                canvas.drawText(context.getString(R.string.drag_to_reposition), cx, textY, dragHintPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                dragMode = determineDragMode(event.x, event.y)
                isDragging = true
                isPinching = false
                onInteractionStart?.invoke()
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (event.pointerCount) {
                    2 -> {
                        isPinching = true
                        isDragging = false
                        lastSpan = spacing(event)
                    }
                    3 -> {
                        isDragging = true
                        isPinching = false
                        dragMode = DragMode.PAN
                        val idx = event.findPointerIndex(activePointerId)
                        if (idx >= 0) {
                            lastTouchX = event.getX(idx)
                            lastTouchY = event.getY(idx)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        lastTouchX = x
                        lastTouchY = y

                        when (dragMode) {
                            DragMode.STRETCH_LEFT -> stretchLeft += dx / pageScale
                            DragMode.STRETCH_RIGHT -> stretchRight += dx / pageScale
                            DragMode.STRETCH_TOP -> stretchTop += dy / pageScale
                            DragMode.STRETCH_BOTTOM -> stretchBottom += dy / pageScale
                            DragMode.PAN -> {
                                panOffsetX += dx
                                panOffsetY += dy
                            }
                            DragMode.NONE -> {}
                        }

                        photoDisplayRect = calcDisplayRect()
                        invalidate()
                    }
                } else if (isPinching && event.pointerCount == 2) {
                    val newSpan = spacing(event)
                    if (lastSpan > 0f) {
                        val factor = newSpan / lastSpan
                        scaleMultiplier = (scaleMultiplier * factor).coerceIn(minScale, maxScale)
                        photoDisplayRect = calcDisplayRect()
                        invalidate()
                        onScaleChanged?.invoke(scaleMultiplier)
                    }
                    lastSpan = newSpan
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                when (event.pointerCount) {
                    3 -> {
                        isDragging = false
                        isPinching = true
                        val upIdx = event.actionIndex
                        val remaining = (0 until event.pointerCount).filter { it != upIdx }
                        if (remaining.size >= 2) {
                            val dx = event.getX(remaining[0]) - event.getX(remaining[1])
                            val dy = event.getY(remaining[0]) - event.getY(remaining[1])
                            lastSpan = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        } else {
                            lastSpan = 0f
                        }
                    }
                    2 -> {
                        isPinching = false
                        isDragging = true
                        val upIdx = event.actionIndex
                        val remainingIdx = if (upIdx == 0) 1 else 0
                        activePointerId = event.getPointerId(remainingIdx)
                        lastTouchX = event.getX(remainingIdx)
                        lastTouchY = event.getY(remainingIdx)
                        dragMode = determineDragMode(lastTouchX, lastTouchY)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isPinching = false
                lastSpan = 0f
                dragMode = DragMode.NONE
                invalidate()
                onInteractionEnd?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun determineDragMode(x: Float, y: Float): DragMode {
        val thirdW = width / 3f
        val thirdH = height / 3f
        return when {
            x < thirdW -> DragMode.STRETCH_LEFT
            x > width - thirdW -> DragMode.STRETCH_RIGHT
            y < thirdH -> DragMode.STRETCH_TOP
            y > height - thirdH -> DragMode.STRETCH_BOTTOM
            else -> DragMode.NONE
        }
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
            override fun onScale(detector: ScaleGestureDetector): Boolean = true
            override fun onScaleEnd(detector: ScaleGestureDetector) {}
        })
    }
}
