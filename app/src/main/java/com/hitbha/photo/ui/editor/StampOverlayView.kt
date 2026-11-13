package com.hitbha.photo.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Allows the user to position and scale a stamp image (e.g. a transparent PNG
 * signature) on top of the base image. Single finger drags the stamp, a pinch
 * gesture resizes it. [getStampRect] returns the display rect for compositing.
 */
class StampOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageLeft = 0f
    private var imageTop = 0f
    private var imageRight = 0f
    private var imageBottom = 0f

    private var stamp: Bitmap? = null

    private var stampRect = RectF()

    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private enum class DragMode { NONE, MOVE }

    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D5AFE")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D5AFE")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cx = stampRect.centerX()
                val cy = stampRect.centerY()
                val w = stampRect.width() * detector.scaleFactor
                val h = stampRect.height() * detector.scaleFactor
                setStampRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                invalidate()
                return true
            }
        }
    )

    fun setImageBounds(left: Float, top: Float, right: Float, bottom: Float) {
        imageLeft = left
        imageTop = top
        imageRight = right
        imageBottom = bottom
        invalidate()
    }

    fun setStamp(bitmap: Bitmap) {
        stamp = bitmap
        val dispW = imageRight - imageLeft
        val dispH = imageBottom - imageTop
        if (dispW <= 0 || dispH <= 0) return
        val maxW = dispW * 0.7f
        val maxH = dispH * 0.7f
        val scale = minOf(maxW / bitmap.width.toFloat(), maxH / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val cx = (imageLeft + imageRight) / 2f
        val cy = (imageTop + imageBottom) / 2f
        setStampRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        invalidate()
    }

    internal fun setStampRect(left: Float, top: Float, right: Float, bottom: Float) {
        var l = left
        var t = top
        var r = right
        var b = bottom
        val minSize = 20f
        if (r - l < minSize) r = l + minSize
        if (b - t < minSize) b = t + minSize

        val imgW = imageRight - imageLeft
        val imgH = imageBottom - imageTop
        if (imgW <= 0 || imgH <= 0) return

        var w = r - l
        var h = b - t
        if (w > imgW || h > imgH) {
            val s = minOf(imgW / w, imgH / h)
            val cw = (r + l) / 2f
            val cy = (b + t) / 2f
            l = cw - (w * s) / 2f
            r = cw + (w * s) / 2f
            t = cy - (h * s) / 2f
            b = cy + (h * s) / 2f
            w = r - l
            h = b - t
        }

        // Keep the whole stamp within the image area.
        // If the stamp is wider/taller than the image, center it;
        // otherwise clamp left-then-right (top-then-bottom) so both
        // constraints are always honoured.
        if (w >= imgW) {
            val cx = (imageLeft + imageRight) / 2f
            l = cx - w / 2f
            r = cx + w / 2f
        } else {
            if (l < imageLeft) { l = imageLeft; r = l + w }
            if (r > imageRight) { r = imageRight; l = r - w }
        }
        if (h >= imgH) {
            val cy = (imageTop + imageBottom) / 2f
            t = cy - h / 2f
            b = cy + h / 2f
        } else {
            if (t < imageTop) { t = imageTop; b = t + h }
            if (b > imageBottom) { b = imageBottom; t = b - h }
        }

        stampRect.set(l, t, r, b)
    }

    fun getStampRect(): RectF = RectF(stampRect)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = stamp ?: return
        if (stampRect.isEmpty) return

        canvas.drawBitmap(bmp, null, stampRect, stampPaint)
        canvas.drawRect(stampRect, borderPaint)

        val r = 14f
        val corners = arrayOf(
            floatArrayOf(stampRect.left, stampRect.top),
            floatArrayOf(stampRect.right, stampRect.top),
            floatArrayOf(stampRect.left, stampRect.bottom),
            floatArrayOf(stampRect.right, stampRect.bottom)
        )
        for (c in corners) {
            canvas.drawCircle(c[0], c[1], r, handlePaint)
            canvas.drawCircle(c[0], c[1], r, handleBorder)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragMode = DragMode.MOVE
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    return true
                }
                if (dragMode == DragMode.MOVE) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    setStampRect(
                        stampRect.left + dx,
                        stampRect.top + dy,
                        stampRect.right + dx,
                        stampRect.bottom + dy
                    )
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

}
