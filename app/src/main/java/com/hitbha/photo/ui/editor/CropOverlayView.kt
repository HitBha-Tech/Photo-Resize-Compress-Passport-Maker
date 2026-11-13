package com.hitbha.photo.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.hitbha.photo.R
import kotlin.math.abs

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cropRect = RectF()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val dimPaint = Paint().apply {
        color = 0x80000000.toInt()
    }
    private val handlePaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.white)
    }

    private var bitmapWidth = 0
    private var bitmapHeight = 0
    private var imageLeft = 0f
    private var imageTop = 0f
    private var imageRight = 0f
    private var imageBottom = 0f

    private var draggingCorner = -1
    private val handleRadius = 20f
    private val touchRadius = 40f

    private var aspectRatio: Float? = null

    companion object {
        private const val CORNER_NONE = -1
        private const val DRAG_BODY = -2
        private const val CORNER_LEFT_TOP = 0
        private const val CORNER_RIGHT_TOP = 1
        private const val CORNER_LEFT_BOTTOM = 2
        private const val CORNER_RIGHT_BOTTOM = 3
    }

    fun setAspectRatio(ratio: Float?) {
        aspectRatio = ratio
        if (ratio != null && cropRect.width() > 0 && cropRect.height() > 0) {
            val centerX = cropRect.centerX()
            val centerY = cropRect.centerY()
            var w = cropRect.width()
            var h = w / ratio
            if (h > cropRect.height()) {
                h = cropRect.height()
                w = h * ratio
            }
            cropRect.set(
                centerX - w / 2f,
                centerY - h / 2f,
                centerX + w / 2f,
                centerY + h / 2f
            )
            clampToImageBounds()
        } else if (ratio == null) {
            cropRect.set(0f, 0f, 0f, 0f)
        }
        invalidate()
    }

    fun setImageBounds(left: Float, top: Float, right: Float, bottom: Float, bmpWidth: Int, bmpHeight: Int) {
        imageLeft = left
        imageTop = top
        imageRight = right
        imageBottom = bottom
        bitmapWidth = bmpWidth
        bitmapHeight = bmpHeight

        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        val initialSize = minOf(right - left, bottom - top) * 0.7f

        val ratio = aspectRatio
        if (ratio != null) {
            var w = initialSize
            var h = w / ratio
            if (h > initialSize) {
                h = initialSize
                w = h * ratio
            }
            cropRect.set(
                centerX - w / 2f,
                centerY - h / 2f,
                centerX + w / 2f,
                centerY + h / 2f
            )
        } else {
            cropRect.set(
                centerX - initialSize / 2,
                centerY - initialSize / 2,
                centerX + initialSize / 2,
                centerY + initialSize / 2
            )
        }
        invalidate()
    }

    val imageDisplayLeft: Float get() = imageLeft
    val imageDisplayTop: Float get() = imageTop
    val imageDisplayRight: Float get() = imageRight
    val imageDisplayBottom: Float get() = imageBottom

    fun getCropRect(): RectF {
        return RectF(
            cropRect.left.coerceIn(imageLeft, imageRight),
            cropRect.top.coerceIn(imageTop, imageBottom),
            cropRect.right.coerceIn(imageLeft, imageRight),
            cropRect.bottom.coerceIn(imageTop, imageBottom)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(imageLeft, imageTop, imageRight, cropRect.top, dimPaint)
        canvas.drawRect(imageLeft, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, imageRight, cropRect.bottom, dimPaint)
        canvas.drawRect(imageLeft, cropRect.bottom, imageRight, imageBottom, dimPaint)

        canvas.drawRect(cropRect, paint)

        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
    }

    private fun clampToImageBounds() {
        val imgW = imageRight - imageLeft
        val imgH = imageBottom - imageTop
        val margin = 50f

        if (imgW <= 0 || imgH <= 0) {
            cropRect.set(imageLeft, imageTop, imageRight, imageBottom)
            return
        }

        // Clamp to image bounds first.
        cropRect.left = cropRect.left.coerceIn(imageLeft, imageRight)
        cropRect.right = cropRect.right.coerceIn(imageLeft, imageRight)
        cropRect.top = cropRect.top.coerceIn(imageTop, imageBottom)
        cropRect.bottom = cropRect.bottom.coerceIn(imageTop, imageBottom)

        // Enforce minimum size when the image is large enough; otherwise
        // use the full image to avoid an inverted (left > right) rect.
        if (imgW > 2 * margin) {
            if (cropRect.right - cropRect.left < margin) {
                val cx = cropRect.centerX().coerceIn(imageLeft + margin / 2, imageRight - margin / 2)
                cropRect.left = cx - margin / 2
                cropRect.right = cx + margin / 2
            }
            cropRect.left = cropRect.left.coerceAtLeast(imageLeft)
            cropRect.right = cropRect.right.coerceAtMost(imageRight)
        } else {
            cropRect.left = imageLeft
            cropRect.right = imageRight
        }

        if (imgH > 2 * margin) {
            if (cropRect.bottom - cropRect.top < margin) {
                val cy = cropRect.centerY().coerceIn(imageTop + margin / 2, imageBottom - margin / 2)
                cropRect.top = cy - margin / 2
                cropRect.bottom = cy + margin / 2
            }
            cropRect.top = cropRect.top.coerceAtLeast(imageTop)
            cropRect.bottom = cropRect.bottom.coerceAtMost(imageBottom)
        } else {
            cropRect.top = imageTop
            cropRect.bottom = imageBottom
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingCorner = getTouchedCorner(x, y)
                if (draggingCorner == CORNER_NONE) {
                    if (cropRect.contains(x, y)) {
                        draggingCorner = DRAG_BODY
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (aspectRatio != null && draggingCorner in 0..3) {
                    dragCornerWithAspectRatio(draggingCorner, x, y)
                } else {
                    when (draggingCorner) {
                        CORNER_LEFT_TOP -> {
                            cropRect.left = x.coerceIn(imageLeft, cropRect.right - 50)
                            cropRect.top = y.coerceIn(imageTop, cropRect.bottom - 50)
                        }
                        CORNER_RIGHT_TOP -> {
                            cropRect.right = x.coerceIn(cropRect.left + 50, imageRight)
                            cropRect.top = y.coerceIn(imageTop, cropRect.bottom - 50)
                        }
                        CORNER_LEFT_BOTTOM -> {
                            cropRect.left = x.coerceIn(imageLeft, cropRect.right - 50)
                            cropRect.bottom = y.coerceIn(cropRect.top + 50, imageBottom)
                        }
                        CORNER_RIGHT_BOTTOM -> {
                            cropRect.right = x.coerceIn(cropRect.left + 50, imageRight)
                            cropRect.bottom = y.coerceIn(cropRect.top + 50, imageBottom)
                        }
                        DRAG_BODY -> {
                            val dx = x - cropRect.centerX()
                            val dy = y - cropRect.centerY()
                            cropRect.offset(dx, dy)
                            clampToImageBounds()
                        }
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                draggingCorner = CORNER_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dragCornerWithAspectRatio(corner: Int, newX: Float, newY: Float) {
        val ratio = aspectRatio ?: return

        val cornerX: Float
        val cornerY: Float
        when (corner) {
            CORNER_RIGHT_BOTTOM -> {
                cornerX = cropRect.right
                cornerY = cropRect.bottom
            }
            CORNER_RIGHT_TOP -> {
                cornerX = cropRect.right
                cornerY = cropRect.top
            }
            CORNER_LEFT_BOTTOM -> {
                cornerX = cropRect.left
                cornerY = cropRect.bottom
            }
            CORNER_LEFT_TOP -> {
                cornerX = cropRect.left
                cornerY = cropRect.top
            }
            else -> return
        }

        val dx = newX - cornerX
        val dy = newY - cornerY

        val hysteresis = 8f
        if (abs(dx) > abs(dy) + hysteresis) {
            val w: Float
            val h: Float
            when (corner) {
                CORNER_RIGHT_BOTTOM -> {
                    w = (newX - cropRect.left).coerceAtLeast(50f)
                    h = w / ratio
                    cropRect.right = cropRect.left + w
                    cropRect.bottom = cropRect.top + h
                }
                CORNER_RIGHT_TOP -> {
                    w = (newX - cropRect.left).coerceAtLeast(50f)
                    h = w / ratio
                    cropRect.right = cropRect.left + w
                    cropRect.top = cropRect.bottom - h
                }
                CORNER_LEFT_BOTTOM -> {
                    w = (cropRect.right - newX).coerceAtLeast(50f)
                    h = w / ratio
                    cropRect.left = cropRect.right - w
                    cropRect.bottom = cropRect.top + h
                }
                CORNER_LEFT_TOP -> {
                    w = (cropRect.right - newX).coerceAtLeast(50f)
                    h = w / ratio
                    cropRect.left = cropRect.right - w
                    cropRect.top = cropRect.bottom - h
                }
            }
        } else {
            val h: Float
            val w: Float
            when (corner) {
                CORNER_RIGHT_BOTTOM -> {
                    h = (newY - cropRect.top).coerceAtLeast(50f)
                    w = h * ratio
                    cropRect.bottom = cropRect.top + h
                    cropRect.right = cropRect.left + w
                }
                CORNER_RIGHT_TOP -> {
                    h = (cropRect.bottom - newY).coerceAtLeast(50f)
                    w = h * ratio
                    cropRect.top = cropRect.bottom - h
                    cropRect.right = cropRect.left + w
                }
                CORNER_LEFT_BOTTOM -> {
                    h = (newY - cropRect.top).coerceAtLeast(50f)
                    w = h * ratio
                    cropRect.bottom = cropRect.top + h
                    cropRect.left = cropRect.right - w
                }
                CORNER_LEFT_TOP -> {
                    h = (cropRect.bottom - newY).coerceAtLeast(50f)
                    w = h * ratio
                    cropRect.top = cropRect.bottom - h
                    cropRect.left = cropRect.right - w
                }
            }
        }

        clampToImageBounds()
    }

    private fun getTouchedCorner(x: Float, y: Float): Int {
        if (isNear(x, y, cropRect.left, cropRect.top)) return CORNER_LEFT_TOP
        if (isNear(x, y, cropRect.right, cropRect.top)) return CORNER_RIGHT_TOP
        if (isNear(x, y, cropRect.left, cropRect.bottom)) return CORNER_LEFT_BOTTOM
        if (isNear(x, y, cropRect.right, cropRect.bottom)) return CORNER_RIGHT_BOTTOM
        return CORNER_NONE
    }

    private fun isNear(x: Float, y: Float, targetX: Float, targetY: Float): Boolean {
        return (x - targetX) * (x - targetX) + (y - targetY) * (y - targetY) <= touchRadius * touchRadius
    }
}
