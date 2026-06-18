package com.example.freshscan.data.ai

/**
 * Abstract interface for cloud AI services (Qwen, DeepSeek, etc.).
 *
 * All methods are suspend functions safe to call from ViewModel coroutine scope.
 * Implementation runs IO on OkHttp dispatcher threads.
 */
interface AIService {
    /**
     * General chat completion.
     *
     * @param systemPrompt System role prompt (character/persona).
     * @param userMessage User's message.
     * @return AI-generated text, or [Result.failure] with [AIServiceError].
     */
    suspend fun chat(systemPrompt: String, userMessage: String): Result<String>

    /**
     * Chat completion with JSON output expectation.
     *
     * The system prompt should instruct the model to output pure JSON.
     * The implementation may add format enforcement headers.
     *
     * @param systemPrompt System prompt that includes JSON format instructions.
     * @param userMessage User's message.
     * @param jsonSchema Optional JSON schema string for validation.
     * @return A JSON string, or [Result.failure] with [AIServiceError].
     */
    suspend fun chatJson(
        systemPrompt: String,
        userMessage: String,
        jsonSchema: String = ""
    ): Result<String>
}
