package com.example.freshscan.domain.usecase

import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.FruitCategory
import com.example.freshscan.domain.model.Prediction
import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.domain.repository.HistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveResultUseCaseTest {

    private lateinit var historyRepository: HistoryRepository
    private lateinit var saveResultUseCase: SaveResultUseCase

    @Before
    fun setUp() {
        historyRepository = mockk()
        saveResultUseCase = SaveResultUseCase(historyRepository)
    }

    @Test
    fun `invoke should delegate to historyRepository saveResult on success`() = runTest {
        // Given
        val result = createTestResult()
        coEvery { historyRepository.saveResult(result) } returns Result.success(Unit)

        // When
        val outcome = saveResultUseCase(result)

        // Then
        assertTrue(outcome.isSuccess)
        coVerify(exactly = 1) { historyRepository.saveResult(result) }
    }

    @Test
    fun `invoke should propagate failure from repository`() = runTest {
        // Given
        val result = createTestResult()
        val error = RuntimeException("Database error")
        coEvery { historyRepository.saveResult(result) } returns Result.failure(error)

        // When
        val outcome = saveResultUseCase(result)

        // Then
        assertTrue(outcome.isFailure)
        assertEquals(error, outcome.exceptionOrNull())
        coVerify(exactly = 1) { historyRepository.saveResult(result) }
    }

    @Test
    fun `invoke should pass recognition result fields intact`() = runTest {
        // Given
        val result = RecognitionResult(
            id = "test-uuid-123",
            fruitCategory = FruitCategory.BANANA,
            freshnessLevel = FreshnessLevel.FRESH,
            confidence = 0.95f,
            topPredictions = listOf(
                Prediction("fresh_banana", "香蕉-新鲜", 0.95f),
                Prediction("rotten_banana", "香蕉-腐烂", 0.03f),
                Prediction("fresh_apple", "苹果-新鲜", 0.02f)
            ),
            inferenceTimeMs = 42L,
            timestamp = 1_700_000_000_000L
        )

        var capturedResult: RecognitionResult? = null
        coEvery { historyRepository.saveResult(any()) } answers {
            capturedResult = firstArg()
            Result.success(Unit)
        }

        // When
        saveResultUseCase(result)

        // Then
        val captured = capturedResult!!
        assertEquals("test-uuid-123", captured.id)
        assertEquals(FruitCategory.BANANA, captured.fruitCategory)
        assertEquals(FreshnessLevel.FRESH, captured.freshnessLevel)
        assertEquals(0.95f, captured.confidence)
        assertEquals(42L, captured.inferenceTimeMs)
        assertEquals(3, captured.topPredictions.size)
    }

    @Test
    fun `invoke should handle UNCERTAIN result correctly`() = runTest {
        // Given
        val result = RecognitionResult(
            id = "uncertain-id",
            fruitCategory = FruitCategory.UNKNOWN,
            freshnessLevel = FreshnessLevel.UNCERTAIN,
            confidence = 0.35f,
            topPredictions = emptyList(),
            inferenceTimeMs = 10L
        )
        coEvery { historyRepository.saveResult(result) } returns Result.success(Unit)

        // When
        val outcome = saveResultUseCase(result)

        // Then
        assertTrue(outcome.isSuccess)
        coVerify(exactly = 1) { historyRepository.saveResult(result) }
    }

    private fun createTestResult() = RecognitionResult(
        id = "test-id",
        fruitCategory = FruitCategory.APPLE,
        freshnessLevel = FreshnessLevel.FRESH,
        confidence = 0.92f,
        topPredictions = listOf(
            Prediction("fresh_apple", "苹果-新鲜", 0.92f)
        ),
        inferenceTimeMs = 15L
    )
}
