package com.example.freshscan.data.inference

import android.content.Context
import com.example.freshscan.util.Constants
import com.example.freshscan.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates TFLite [Interpreter] instances with GPU/CPU delegate strategy.
 *
 * Extracted from TFLiteClassifier to support multiple models (freshness 18-class
 * + Fruits-360 260-class) reusing the same loading logic.
 *
 * GPU strategy:
 * - Try GPU delegate first; fall back to CPU (4 threads + XNNPACK) on failure.
 * - Catches both Exception and Error (e.g. NoClassDefFoundError) for robust fallback.
 * - The fallback decision is per-createInterpreter call, not cached.
 */
@Singleton
class ModelLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Create a TFLite [Interpreter] for the given model file.
     *
     * Loads the .tflite file from assets/model/, tries GPU delegate,
     * and falls back to CPU with XNNPACK on failure.
     *
     * @param modelFileName The model filename (e.g., "fruit_freshness_model.tflite"),
     *        located in assets/model/.
     * @param forceCpu If true, skip GPU delegate attempt entirely (used for
     *        GPU runtime error recovery).
     * @return A ready-to-use [Interpreter].
     */
    fun createInterpreter(modelFileName: String, forceCpu: Boolean = false): Interpreter {
        Logger.i("ModelLoader", "Loading model: $modelFileName (forceCpu=$forceCpu)")

        val modelBuffer = loadModelFile(modelFileName)

        if (!forceCpu) {
            val options = Interpreter.Options().apply {
                setNumThreads(Constants.TFLITE_NUM_THREADS)
            }

            // Try GPU delegate (may fail due to missing classes, incompatible drivers, etc.)
            val gpuDelegate = try {
                GpuDelegate()
            } catch (e: Throwable) {
                Logger.w("ModelLoader", "GPU delegate unavailable for $modelFileName", e)
                null
            }

            if (gpuDelegate != null) {
                try {
                    options.addDelegate(gpuDelegate)
                    val interpreter = Interpreter(modelBuffer, options)
                    Logger.i("ModelLoader", "GPU delegate active for $modelFileName")
                    return interpreter
                } catch (e: Throwable) {
                    Logger.w("ModelLoader", "GPU delegate init failed for $modelFileName, falling back to CPU", e)
                    try { gpuDelegate.close() } catch (_: Throwable) { }
                    // Fall through to CPU path
                }
            }
        }

        // Fallback: CPU with XNNPACK
        val cpuOptions = Interpreter.Options().apply {
            setNumThreads(Constants.TFLITE_NUM_THREADS)
        }
        modelBuffer.rewind()
        val interpreter = Interpreter(modelBuffer, cpuOptions)
        Logger.i("ModelLoader", "Using CPU for $modelFileName (${Constants.TFLITE_NUM_THREADS} threads + XNNPACK)")
        return interpreter
    }

    // --- Private helpers ---

    /**
     * Load a TFLite model file from assets/model/ as a memory-mapped ByteBuffer.
     *
     * Memory-mapped I/O avoids copying the entire file into heap, reducing
     * memory pressure for large models (EfficientDet-Lite0 is ~6MB).
     */
    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("model/$modelFileName")
        return assetFileDescriptor.use { afd ->
            val fileInputStream = FileInputStream(afd.fileDescriptor)
            fileInputStream.use { fis ->
                val fileChannel = fis.channel
                fileChannel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }
    }
}
