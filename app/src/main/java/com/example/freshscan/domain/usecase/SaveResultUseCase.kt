package com.example.freshscan.domain.usecase

import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves a recognition result to the history database.
 */
@Singleton
class SaveResultUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    /**
     * Persist a recognition result.
     *
     * @param result The recognition result to save.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(result: RecognitionResult): Result<Unit> {
        return historyRepository.saveResult(result)
    }
}
