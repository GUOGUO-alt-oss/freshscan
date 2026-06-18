package com.example.freshscan.domain.usecase

import com.example.freshscan.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes a single history item by ID.
 */
@Singleton
class DeleteHistoryItemUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return historyRepository.delete(id)
    }
}
