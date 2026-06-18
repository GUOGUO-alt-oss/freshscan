package com.example.freshscan.domain.usecase

import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns a reactive Flow of all history items, newest first.
 */
@Singleton
class GetHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    operator fun invoke(): Flow<List<HistoryItem>> {
        return historyRepository.getHistory()
    }
}
