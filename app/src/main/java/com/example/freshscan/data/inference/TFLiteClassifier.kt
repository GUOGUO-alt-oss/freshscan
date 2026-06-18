package com.example.freshscan.data.inference

import android.content.Context
import com.example.freshscan.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import javax.inject.Singleton

/**
 * Wraps a TFLite [Interpreter] for image classification.
 *
 * Supports multiple instances: the v1 freshness model (18-class) and the
 * v2 Fruits-360 model (260-class) are separate instances with different
 * [modelFileName] and [numClasses] parameters, constructed via @Provides
 * methods in [AppModule].
 *
 * Lifecycle:
 * - Model is loaded lazily on first [classify] call via [ensureLoaded].
 * - Call [close] to release the interpreter (e.g., when leaving analysis page).
 *
 * GPU delegate strategy:
 * - Initial load: tries GPU via [ModelLoader]; falls back to CPU on failure.
 * - GPU runtime error: closes interpreter, forces CPU-only reload on next classify.
 *
 * Thread safety: [ensureLoaded] and [close] are guarded by [interpreterLock];
 * [classify] accesses [interpreter] under the same lock to prevent races.
 */
@Singleton
class TFLiteClassifier(
    @ApplicationContext private val context: Context,
    private val modelFileName: String,
    private val numClasses: Int,
    private val modelLoader: ModelLoader,
    /** If true, skip GPU delegate entirely and use CPU from the start.
     *  Recommended for small precision-critical models where GPU
     *  floating-point variance can cause misclassification. */
    private val forceCpuInitial: Boolean = false
) {
    private var interpreter: Interpreter? = null
    private var useGpu: Boolean = false
    private var forceCpu: Boolean = forceCpuInitial
    private val interpreterLock = Any()

    /** Whether the model has been loaded successfully. */
    val isLoaded: Boolean get() = interpreter != null

    /**
     * Ensure the TFLite interpreter is loaded (lazy initialization).
     *
     * Safe to call multiple times — subsequent calls are no-ops.
     * Thread-safe.
     */
    fun ensureLoaded() {
        synchronized(interpreterLock) {
            if (interpreter != null) return
            interpreter = modelLoader.createInterpreter(modelFileName, forceCpu)
            useGpu = !forceCpu  // If we forced CPU, GPU is not active
            Logger.i("TFLite", "Model loaded: $modelFileName (gpu=$useGpu, classes=$numClasses)")
        }
    }

    /**
     * Run inference on a preprocessed ByteBuffer.
     *
     * Input: ByteBuffer of shape [1, 224, 224, 3] with float32 values in [0, 1].
     * Output: FloatArray of shape [numClasses] containing raw logits.
     *
     * Calls [ensureLoaded] lazily if the model hasn't been loaded yet.
     * If a GPU runtime error occurs, the interpreter is closed and will be
     * re-created with CPU-only on the next classify call.
     *
     * @return FloatArray of [numClasses] raw logits.
     * @throws IllegalStateException if model cannot be loaded.
     */
    fun classify(inputBuffer: ByteBuffer): FloatArray {
        // Lazy load on first call
        ensureLoaded()

        val currentInterpreter: Interpreter
        val isGpu: Boolean
        synchronized(interpreterLock) {
            currentInterpreter = interpreter ?: throw IllegalStateException(
                "Model $modelFileName not loaded"
            )
            isGpu = useGpu
        }

        val output = Array(1) { FloatArray(numClasses) }

        try {
            currentInterpreter.run(inputBuffer, output)
        } catch (e: Throwable) {
            // If GPU delegate throws at runtime, close and mark for CPU rebuild
            if (isGpu) {
                Logger.w("TFLite", "GPU runtime error for $modelFileName, closing for CPU rebuild", e)
                synchronized(interpreterLock) {
                    useGpu = false
                    forceCpu = true
                    try { interpreter?.close() } catch (_: Throwable) { }
                    interpreter = null
                }
                // Retry with CPU only
                return classify(inputBuffer)
            } else {
                Logger.e("TFLite", "Classification failed for $modelFileName", e)
                throw IllegalStateException("Classification failed: ${e.message}", e)
            }
        }

        return output[0]
    }

    /**
     * Release the TFLite interpreter and GPU delegate.
     */
    fun close() {
        synchronized(interpreterLock) {
            interpreter?.close()
            interpreter = null
            useGpu = false
        }
        Logger.i("TFLite", "Interpreter closed: $modelFileName")
    }
}
