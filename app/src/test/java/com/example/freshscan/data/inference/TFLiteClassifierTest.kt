package com.example.freshscan.data.inference

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class TFLiteClassifierTest {

    private lateinit var context: Context
    private lateinit var modelLoader: ModelLoader
    private lateinit var interpreter: Interpreter

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        modelLoader = mockk(relaxed = true)
        interpreter = mockk(relaxed = true)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Construction & Isolation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should not load model on construction`() {
        val classifier = TFLiteClassifier(
            context = context,
            modelFileName = "test_model.tflite",
            numClasses = 10,
            modelLoader = modelLoader
        )

        assertFalse("Model should not be loaded on construction", classifier.isLoaded)
    }

    @Test
    fun `two classifiers should be independent instances`() {
        // Setup: two different model files
        val classifier1 = TFLiteClassifier(
            context = context,
            modelFileName = "model_a.tflite",
            numClasses = 18,
            modelLoader = modelLoader
        )
        val classifier2 = TFLiteClassifier(
            context = context,
            modelFileName = "model_b.tflite",
            numClasses = 260,
            modelLoader = modelLoader
        )

        // Initially, neither is loaded
        assertFalse(classifier1.isLoaded)
        assertFalse(classifier2.isLoaded)
    }

    @Test
    fun `classifiers with different numClasses should not interfere`() {
        every { modelLoader.createInterpreter("model_a.tflite", any()) } returns interpreter
        every { modelLoader.createInterpreter("model_b.tflite", any()) } returns interpreter

        val classifier18 = TFLiteClassifier(
            context, "model_a.tflite", 18, modelLoader
        )
        val classifier260 = TFLiteClassifier(
            context, "model_b.tflite", 260, modelLoader
        )

        // Load both
        classifier18.ensureLoaded()
        classifier260.ensureLoaded()

        assertTrue(classifier18.isLoaded)
        assertTrue(classifier260.isLoaded)

        // Verify each loaded its own model file
        verify { modelLoader.createInterpreter("model_a.tflite", false) }
        verify { modelLoader.createInterpreter("model_b.tflite", false) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lazy Loading Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `ensureLoaded should create interpreter via modelLoader`() {
        every { modelLoader.createInterpreter("lazy_model.tflite", false) } returns interpreter

        val classifier = TFLiteClassifier(
            context, "lazy_model.tflite", 10, modelLoader
        )

        classifier.ensureLoaded()

        assertTrue("Should be loaded after ensureLoaded", classifier.isLoaded)
        verify(exactly = 1) { modelLoader.createInterpreter("lazy_model.tflite", false) }
    }

    @Test
    fun `ensureLoaded should be idempotent`() {
        every { modelLoader.createInterpreter("idem.tflite", false) } returns interpreter

        val classifier = TFLiteClassifier(
            context, "idem.tflite", 10, modelLoader
        )

        // Call multiple times
        classifier.ensureLoaded()
        classifier.ensureLoaded()
        classifier.ensureLoaded()

        // Interpreter should only be created once
        verify(exactly = 1) { modelLoader.createInterpreter("idem.tflite", false) }
    }

    @Test
    fun `classify should call ensureLoaded lazily on first invocation`() {
        every { modelLoader.createInterpreter("lazy2.tflite", false) } returns interpreter
        // Mock interpreter run to succeed
        every { interpreter.run(any<ByteBuffer>(), any<Array<FloatArray>>()) } answers {
            // Fill output with test values
            val output = args[1] as Array<FloatArray>
            output[0] = FloatArray(10) { it.toFloat() * 0.1f }
        }

        val classifier = TFLiteClassifier(
            context, "lazy2.tflite", 10, modelLoader
        )

        assertFalse("Not loaded before classify", classifier.isLoaded)

        val input = ByteBuffer.allocateDirect(10 * 4)
        val result = classifier.classify(input)

        assertTrue("Should be loaded after classify", classifier.isLoaded)
        assertNotNull(result)
        assertEquals(10, result.size)
        verify(exactly = 1) { modelLoader.createInterpreter("lazy2.tflite", false) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // forceCpuInitial Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `forceCpuInitial should pass true to modelLoader`() {
        every { modelLoader.createInterpreter("cpu_model.tflite", true) } returns interpreter

        val classifier = TFLiteClassifier(
            context = context,
            modelFileName = "cpu_model.tflite",
            numClasses = 18,
            modelLoader = modelLoader,
            forceCpuInitial = true
        )

        classifier.ensureLoaded()

        verify { modelLoader.createInterpreter("cpu_model.tflite", true) }
    }

    @Test
    fun `forceCpuInitial false should pass false to modelLoader`() {
        every { modelLoader.createInterpreter("gpu_model.tflite", false) } returns interpreter

        val classifier = TFLiteClassifier(
            context = context,
            modelFileName = "gpu_model.tflite",
            numClasses = 18,
            modelLoader = modelLoader,
            forceCpuInitial = false
        )

        classifier.ensureLoaded()

        verify { modelLoader.createInterpreter("gpu_model.tflite", false) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // close() Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `close should release interpreter`() {
        every { modelLoader.createInterpreter("close_test.tflite", false) } returns interpreter
        every { interpreter.close() } just Runs

        val classifier = TFLiteClassifier(
            context, "close_test.tflite", 10, modelLoader
        )
        classifier.ensureLoaded()
        assertTrue(classifier.isLoaded)

        classifier.close()

        assertFalse("Should not be loaded after close", classifier.isLoaded)
        verify { interpreter.close() }
    }

    @Test
    fun `close should be safe to call multiple times`() {
        every { modelLoader.createInterpreter("multi_close.tflite", false) } returns interpreter
        every { interpreter.close() } just Runs

        val classifier = TFLiteClassifier(
            context, "multi_close.tflite", 10, modelLoader
        )
        classifier.ensureLoaded()

        classifier.close()
        classifier.close()
        classifier.close()

        // close() should only call interpreter.close() once (the first time)
        // Subsequent calls find interpreter == null and skip
        verify(exactly = 1) { interpreter.close() }
    }

    @Test
    fun `should reload after close on next classify`() {
        every { modelLoader.createInterpreter("reload.tflite", false) } returns interpreter
        every { interpreter.run(any<ByteBuffer>(), any<Array<FloatArray>>()) } answers {
            val output = args[1] as Array<FloatArray>
            output[0] = FloatArray(10) { 1.0f }
        }

        val classifier = TFLiteClassifier(
            context, "reload.tflite", 10, modelLoader
        )
        classifier.ensureLoaded()
        classifier.close()

        assertFalse(classifier.isLoaded)

        // Classify should reload
        val input = ByteBuffer.allocateDirect(10 * 4)
        classifier.classify(input)

        assertTrue("Should be reloaded after classify", classifier.isLoaded)
        verify(exactly = 2) { modelLoader.createInterpreter("reload.tflite", false) }
    }

    @Test
    fun `close without load should not crash`() {
        val classifier = TFLiteClassifier(
            context, "no_load.tflite", 10, modelLoader
        )

        // close() when never loaded should be safe (no crash)
        classifier.close()
        assertFalse(classifier.isLoaded)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify() Behavior Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `classify should return FloatArray of correct size`() {
        every { modelLoader.createInterpreter("size_test.tflite", false) } returns interpreter
        every { interpreter.run(any<ByteBuffer>(), any<Array<FloatArray>>()) } answers {
            val output = args[1] as Array<FloatArray>
            output[0] = FloatArray(260) { 0.5f }
        }

        val classifier = TFLiteClassifier(
            context, "size_test.tflite", 260, modelLoader
        )

        val input = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
        val result = classifier.classify(input)

        assertEquals(260, result.size)
    }

    @Test
    fun `classify should throw when modelLoader fails`() {
        every { modelLoader.createInterpreter("fail.tflite", false) } throws
            RuntimeException("Model file not found")

        val classifier = TFLiteClassifier(
            context, "fail.tflite", 10, modelLoader
        )

        val input = ByteBuffer.allocateDirect(10 * 4)
        try {
            classifier.classify(input)
            org.junit.Assert.fail("Expected exception was not thrown")
        } catch (e: RuntimeException) {
            // Expected — modelLoader.createInterpreter throws, which propagates
            assertTrue(e.message!!.contains("Model file not found"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GPU Runtime Error Fallback Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `GPU runtime error should fall back to CPU`() {
        // First call: create with GPU (forceCpu=false)
        every { modelLoader.createInterpreter("gpu_fallback.tflite", false) } returns interpreter
        // Interpreter simulates GPU runtime error on first run
        every { interpreter.run(any<ByteBuffer>(), any<Array<FloatArray>>()) } answers {
            throw RuntimeException("GPU delegate runtime error")
        } andThen {
            val output = args[1] as Array<FloatArray>
            output[0] = FloatArray(10) { 0.8f }
        }
        every { interpreter.close() } just Runs

        // Second call: recreate with CPU (forceCpu=true)
        val cpuInterpreter = mockk<Interpreter>(relaxed = true)
        every { modelLoader.createInterpreter("gpu_fallback.tflite", true) } returns cpuInterpreter
        every { cpuInterpreter.run(any<ByteBuffer>(), any<Array<FloatArray>>()) } answers {
            val output = args[1] as Array<FloatArray>
            output[0] = FloatArray(10) { 0.8f }
        }

        val classifier = TFLiteClassifier(
            context, "gpu_fallback.tflite", 10, modelLoader
        )

        val input = ByteBuffer.allocateDirect(10 * 4)
        val result = classifier.classify(input)

        assertNotNull(result)
        assertEquals(10, result.size)

        // Verify: first attempt with GPU failed, second with CPU succeeded
        verify { modelLoader.createInterpreter("gpu_fallback.tflite", false) }
        verify { modelLoader.createInterpreter("gpu_fallback.tflite", true) }
        verify { interpreter.close() }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Thread Safety Smoke Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `concurrent ensureLoaded calls should create only one interpreter`() {
        every { modelLoader.createInterpreter("concurrent.tflite", false) } returns interpreter

        val classifier = TFLiteClassifier(
            context, "concurrent.tflite", 10, modelLoader
        )

        // Simulate concurrent calls from multiple threads
        val threads = (1..5).map {
            Thread { classifier.ensureLoaded() }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Due to synchronization, only one interpreter should be created
        verify(atLeast = 1, atMost = 1) {
            modelLoader.createInterpreter("concurrent.tflite", false)
        }
    }
}
