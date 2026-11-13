package com.hitbha.photo.ui.cut

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.hitbha.photo.R
import kotlin.math.abs

class CutRectangleOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Orientation { HORIZONTAL, VERTICAL }

    var orientation: Orientation = Orientation.HORIZONTAL

    private var imageLeft = 0f
    private var imageTop = 0f
    private var imageRight = 0f
    private var imageBottom = 0f

    private var rectTop = 0f
    private var rectBottom = 0f
    private var rectLeft = 0f
    private var rectRight = 0f

    private val minSize = 60f
    private val handleTouchSize = 80f

    private enum class Handle { NONE, TOP, BOTTOM, LEFT, RIGHT, MOVE }

    private var activeHandle = Handle.NONE
    private var lastTouchY = 0f
    private var lastTouchX = 0f

    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.white)
    }

    private val handlePaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.gray)
    }

    private val handleStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.dark_blue_700)
    }

    fun setImageBounds(left: Float, top: Float, right: Float, bottom: Float) {
        imageLeft = left
        imageTop = top
        imageRight = right
        imageBottom = bottom
        val w = right - left
        val h = bottom - top
        rectTop = top + h * 0.3f
        rectBottom = bottom - h * 0.3f
        rectLeft = left + w * 0.3f
        rectRight = right - w * 0.3f
        invalidate()
    }

    data class CutParams(
        val top: Float,
        val bottom: Float,
        val left: Float,
        val right: Float,
        val displayWidth: Float,
        val displayHeight: Float
    )

    fun getCutParams(): CutParams {
        return CutParams(
            top = rectTop - imageTop,
            bottom = rectBottom - imageTop,
            left = rectLeft - imageLeft,
            right = rectRight - imageLeft,
            displayWidth = imageRight - imageLeft,
            displayHeight = imageBottom - imageTop
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (orientation == Orientation.HORIZONTAL) {
            drawHorizontal(canvas)
        } else {
            drawVertical(canvas)
        }
    }

    private fun drawHorizontal(canvas: Canvas) {
        val clampedTop = rectTop.coerceIn(imageTop, imageBottom)
        val clampedBottom = rectBottom.coerceIn(imageTop, imageBottom)

        canvas.drawRect(imageLeft, imageTop, imageRight, clampedTop, dimPaint)
        canvas.drawRect(imageLeft, clampedBottom, imageRight, imageBottom, dimPaint)

        canvas.drawRect(imageLeft, clampedTop, imageRight, clampedBottom, borderPaint)

        val handleW = (imageRight - imageLeft) * 0.3f
        val handleH = handleTouchSize * 0.5f

        drawTopBottomHandles(canvas, clampedTop, clampedBottom, handleW, handleH)
    }

    private fun drawVertical(canvas: Canvas) {
        val clampedLeft = rectLeft.coerceIn(imageLeft, imageRight)
        val clampedRight = rectRight.coerceIn(imageLeft, imageRight)

        canvas.drawRect(imageLeft, imageTop, clampedLeft, imageBottom, dimPaint)
        canvas.drawRect(clampedRight, imageTop, imageRight, imageBottom, dimPaint)

        canvas.drawRect(clampedLeft, imageTop, clampedRight, imageBottom, borderPaint)

        val handleH = (imageBottom - imageTop) * 0.3f
        val handleW = handleTouchSize * 0.5f

        drawLeftRightHandles(canvas, clampedLeft, clampedRight, handleW, handleH)
    }

    private fun drawTopBottomHandles(canvas: Canvas, top: Float, bottom: Float, handleW: Float, handleH: Float) {
        val cx = (imageLeft + imageRight) / 2f
        val radius = handleH.coerceAtMost(handleW / 2f)

        canvas.drawCircle(cx, top, radius, handlePaint)
        canvas.drawCircle(cx, top, radius, handleStrokePaint)
        canvas.drawCircle(cx, bottom, radius, handlePaint)
        canvas.drawCircle(cx, bottom, radius, handleStrokePaint)
    }

    private fun drawLeftRightHandles(canvas: Canvas, left: Float, right: Float, handleW: Float, handleH: Float) {
        val cy = (imageTop + imageBottom) / 2f
        val radius = handleW.coerceAtMost(handleH / 2f)

        canvas.drawCircle(left, cy, radius, handlePaint)
        canvas.drawCircle(left, cy, radius, handleStrokePaint)
        canvas.drawCircle(right, cy, radius, handlePaint)
        canvas.drawCircle(right, cy, radius, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (orientation == Orientation.HORIZONTAL) {
                    val distTop = abs(y - rectTop)
                    val distBottom = abs(y - rectBottom)
                    if (distTop < handleTouchSize) {
                        activeHandle = Handle.TOP
                        lastTouchY = y
                        return true
                    }
                    if (distBottom < handleTouchSize) {
                        activeHandle = Handle.BOTTOM
                        lastTouchY = y
                        return true
                    }
                    if (y in rectTop..rectBottom) {
                        activeHandle = Handle.MOVE
                        lastTouchY = y
                        return true
                    }
                } else {
                    val distLeft = abs(x - rectLeft)
                    val distRight = abs(x - rectRight)
                    if (distLeft < handleTouchSize) {
                        activeHandle = Handle.LEFT
                        lastTouchX = x
                        return true
                    }
                    if (distRight < handleTouchSize) {
                        activeHandle = Handle.RIGHT
                        lastTouchX = x
                        return true
                    }
                    if (x in rectLeft..rectRight) {
                        activeHandle = Handle.MOVE
                        lastTouchX = x
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (activeHandle) {
                    Handle.TOP -> {
                        val delta = y - lastTouchY
                        rectTop = (rectTop + delta).coerceIn(imageTop, rectBottom - minSize)
                        invalidate()
                        lastTouchY = y
                    }
                    Handle.BOTTOM -> {
                        val delta = y - lastTouchY
                        rectBottom = (rectBottom + delta).coerceIn(rectTop + minSize, imageBottom)
                        invalidate()
                        lastTouchY = y
                    }
                    Handle.LEFT -> {
                        val delta = x - lastTouchX
                        rectLeft = (rectLeft + delta).coerceIn(imageLeft, rectRight - minSize)
                        invalidate()
                        lastTouchX = x
                    }
                    Handle.RIGHT -> {
                        val delta = x - lastTouchX
                        rectRight = (rectRight + delta).coerceIn(rectLeft + minSize, imageRight)
                        invalidate()
                        lastTouchX = x
                    }
                    Handle.MOVE -> {
                        if (orientation == Orientation.HORIZONTAL) {
                            val delta = y - lastTouchY
                            val h = rectBottom - rectTop
                            var newTop = rectTop + delta
                            var newBottom = rectBottom + delta
                            if (newTop < imageTop) {
                                newTop = imageTop
                                newBottom = newTop + h
                            }
                            if (newBottom > imageBottom) {
                                newBottom = imageBottom
                                newTop = newBottom - h
                            }
                            rectTop = newTop
                            rectBottom = newBottom
                            lastTouchY = y
                        } else {
                            val delta = x - lastTouchX
                            val w = rectRight - rectLeft
                            var newLeft = rectLeft + delta
                            var newRight = rectRight + delta
                            if (newLeft < imageLeft) {
                                newLeft = imageLeft
                                newRight = newLeft + w
                            }
                            if (newRight > imageRight) {
                                newRight = imageRight
                                newLeft = newRight - w
                            }
                            rectLeft = newLeft
                            rectRight = newRight
                            lastTouchX = x
                        }
                        invalidate()
                    }
                    else -> return false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                activeHandle = Handle.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
