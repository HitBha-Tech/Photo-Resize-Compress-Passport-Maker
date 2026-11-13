package com.hitbha.photo.utils.bgremover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.android.gms.tasks.Tasks
import kotlin.math.sqrt

data class BgResult(
    val bitmap: Bitmap,
    val mask: Bitmap? = null,
    val processingTimeMs: Long = 0
)

class BackgroundRemover(private val context: Context) {

    fun removeBackground(bitmap: Bitmap, onProgress: ((Int) -> Unit)? = null): BgResult {
        val start = System.currentTimeMillis()
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val segmentationMask = Tasks.await(segmenter.process(inputImage))
            val w = bitmap.width; val h = bitmap.height
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val srcPixels = IntArray(w * h); bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
            val dstPixels = IntArray(w * h); result.getPixels(dstPixels, 0, w, 0, 0, w, h)
            val maskBuffer = segmentationMask.buffer; maskBuffer.rewind()
            val mw = segmentationMask.width; val mh = segmentationMask.height

            val maskOut = IntArray(w * h)

            val floatMask = FloatArray(mw * mh)
            for (i in floatMask.indices) {
                floatMask[i] = maskBuffer.getFloat(i * 4)
            }

            val smoothedMask = gaussianBlur1D(floatMask, mw, mh, 1.5f)

            val gradientMap = computeGradientMagnitude(srcPixels, w, h)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val mx = if (w <= 1) 0f else x.toFloat() / (w - 1) * (mw - 1)
                    val my = if (h <= 1) 0f else y.toFloat() / (h - 1) * (mh - 1)
                    var rawConfidence = bilinearInterpolate(smoothedMask, mw, mh, mx, my)

                    val edgeWeight = gradientMap[y * w + x]
                    if (edgeWeight > 0.12f) {
                        rawConfidence = if (rawConfidence > 0.45f) {
                            (rawConfidence + 0.25f).coerceIn(0f, 1f)
                        } else {
                            (rawConfidence - 0.25f).coerceIn(0f, 1f)
                        }
                    }

                    val alpha = smoothstep(rawConfidence, 0.3f, 0.7f)
                    maskOut[y * w + x] = alpha
                    if (alpha == 0) {
                        dstPixels[y * w + x] = Color.TRANSPARENT
                    } else if (alpha < 255) {
                        val srcAlpha = (srcPixels[y * w + x] shr 24) and 0xFF
                        val blended = (srcAlpha * alpha) / 255
                        dstPixels[y * w + x] = (dstPixels[y * w + x] and 0x00FFFFFF) or (blended shl 24)
                    }
                }
                onProgress?.invoke((y * 100) / h)
            }

            result.setPixels(dstPixels, 0, w, 0, 0, w, h)
            result.setHasAlpha(true)
            val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
            for (i in maskOut.indices) {
                maskOut[i] = maskOut[i] shl 24
            }
            maskBmp.setPixels(maskOut, 0, w, 0, 0, w, h)
            BgResult(result, maskBmp, processingTimeMs = System.currentTimeMillis() - start)
        } finally {
            segmenter.close()
        }
    }

    fun trimEmptyParts(bitmap: Bitmap, emptyColor: Int = Color.TRANSPARENT): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = pixels[y * w + x]
                if (pixel != emptyColor) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        if (minX >= maxX || minY >= maxY) return bitmap

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1
        return Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)
    }

    private fun gaussianBlur1D(mask: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val radius = (sigma * 2).toInt().coerceAtLeast(1)
        val kernelSize = radius * 2 + 1
        val kernel = FloatArray(kernelSize)
        var sum = 0f
        for (i in 0 until kernelSize) {
            val x = i - radius
            kernel[i] = kotlin.math.exp(-(x * x) / (2 * sigma * sigma))
            sum += kernel[i]
        }
        for (i in 0 until kernelSize) kernel[i] /= sum

        val temp = FloatArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var accum = 0f
                for (k in 0 until kernelSize) {
                    val sx = (x + k - radius).coerceIn(0, w - 1)
                    accum += mask[rowOffset + sx] * kernel[k]
                }
                temp[rowOffset + x] = accum
            }
        }

        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var accum = 0f
                for (k in 0 until kernelSize) {
                    val sy = (y + k - radius).coerceIn(0, h - 1)
                    accum += temp[sy * w + x] * kernel[k]
                }
                result[y * w + x] = accum
            }
        }
        return result
    }

    private fun bilinearInterpolate(mask: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
        if (w <= 1 || h <= 1) {
            val ix = x.toInt().coerceIn(0, (w - 1).coerceAtLeast(0))
            val iy = y.toInt().coerceIn(0, (h - 1).coerceAtLeast(0))
            return mask[iy * w + ix]
        }
        val ix = x.toInt().coerceIn(0, w - 2)
        val iy = y.toInt().coerceIn(0, h - 2)
        val fx = x - ix
        val fy = y - iy

        val idx00 = iy * w + ix
        val idx10 = iy * w + (ix + 1)
        val idx01 = (iy + 1) * w + ix
        val idx11 = (iy + 1) * w + (ix + 1)

        return mask[idx00] * (1 - fx) * (1 - fy) +
               mask[idx10] * fx * (1 - fy) +
               mask[idx01] * (1 - fx) * fy +
               mask[idx11] * fx * fy
    }

    private fun smoothstep(value: Float, edge0: Float, edge1: Float): Int {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        val smooth = t * t * (3 - 2 * t)
        return (smooth * 255).toInt()
    }

    private fun computeGradientMagnitude(pixels: IntArray, w: Int, h: Int): FloatArray {
        val gray = FloatArray(w * h) { i ->
            val p = pixels[i]
            (0.299f * ((p shr 16) and 0xFF) +
             0.587f * ((p shr 8) and 0xFF) +
             0.114f * (p and 0xFF)) / 255f
        }
        val gradients = FloatArray(w * h)
        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val gx = gray[rowOffset + x + 1] - gray[rowOffset + x - 1]
                val gy = gray[(y + 1) * w + x] - gray[(y - 1) * w + x]
                gradients[rowOffset + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return gradients
    }
}
