package com.example.freshscan.util

import android.graphics.Bitmap
import com.example.freshscan.data.inference.model.ModelConfig
import com.example.freshscan.di.ModelV1
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image preprocessing pipeline: Bitmap → TFLite-compatible TensorBuffer.
 *
 * Two paths are provided:
 * - [bitmapToTensorBuffer] (recommended): TFLite Support Library pipeline
 *   for resize and normalization.
 * - [bitmapToByteBuffer] (fallback): Manual pixel extraction for cases where
 *   TensorImage is unavailable.
 *
 * v2.0: CameraX-specific methods (preprocess(ImageProxy), yuvToRgbBitmap,
 * imageProxyToNv21) removed during v1 cleanup.
 */
@Singleton
class ImagePreprocessor @Inject constructor(
    @ModelV1 private val modelConfig: ModelConfig
) {
    /** Pre-built ImageProcessor: resize to 224×224 then normalize to [0, 1]. */
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(modelConfig.inputHeight, modelConfig.inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(modelConfig.normalizationMean, modelConfig.normalizationStd))
        .build()

    /**
     * Convert a pre-computed RGB Bitmap to a TFLite-compatible TensorBuffer.
     *
     * Applies resize (224×224) and normalization (pixel/255.0 → [0,1]) via
     * the pre-built ImageProcessor pipeline.
     *
     * IMPORTANT: The input bitmap is normalized to ARGB_8888 config before
     * processing, because camera photos may use wide-gamut color spaces
     * (Display P3) that shift RGB values away from training distribution.
     * Forcing ARGB_8888 ensures sRGB-like pixel values.
     */
    fun bitmapToTensorBuffer(bitmap: Bitmap): TensorBuffer {
        // Normalize to ARGB_8888 to avoid wide-gamut color space issues
        val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val tensorImage = TensorImage.fromBitmap(safeBitmap)
        return imageProcessor.process(tensorImage).tensorBuffer
    }

    /**
     * Manual Bitmap-to-ByteBuffer conversion without TFLite Support Library.
     *
     * This is the fallback inference preprocessing path. Slower than
     * [bitmapToTensorBuffer] because it involves getPixels() pixel extraction.
     *
     * @param bitmap RGB_888 Bitmap at 224×224.
     * @return Direct ByteBuffer with RGB float32 values in [0, 1].
     */
    fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = modelConfig.inputHeight
        val pixelCount = inputSize * inputSize * modelConfig.inputChannels
        val byteBuffer = ByteBuffer.allocateDirect(pixelCount * 4) // float32 = 4 bytes
        byteBuffer.order(java.nio.ByteOrder.nativeOrder())

        // Scale bitmap to model input size first
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        byteBuffer.rewind()
        return byteBuffer
    }
}
