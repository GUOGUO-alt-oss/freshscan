package com.example.freshscan.data.ai

import com.example.freshscan.di.AIApiKey
import com.example.freshscan.di.AIBaseUrl
import com.example.freshscan.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QwenAIService @Inject constructor(
    @AIApiKey private val apiKey: String,
    @AIBaseUrl private val baseUrl: String
) : AIService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val chatEndpoint = "${baseUrl}/services/aigc/text-generation/generation"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    override suspend fun chat(systemPrompt: String, userMessage: String): Result<String> {
        return executeRequest(systemPrompt, userMessage, maxTokens = 1024, model = "qwen-turbo")
    }

    override suspend fun chatJson(
        systemPrompt: String,
        userMessage: String,
        jsonSchema: String
    ): Result<String> {
        val jsonSystemPrompt = buildString {
            append(systemPrompt)
            append("\n\n【重要】请严格输出纯 JSON，" +
                   "不要包含 markdown 代码块标记（```json），只输出 JSON 对象本身。")
        }
        return executeRequest(jsonSystemPrompt, userMessage, maxTokens = 4096, model = "qwen-plus")
    }

    private suspend fun executeRequest(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("input", JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userMessage)
                        })
                    })
                })
                put("parameters", JSONObject().apply {
                    put("max_tokens", maxTokens)
                    put("temperature", 0.7)
                    put("top_p", 0.9)
                    put("result_format", "message")
                })
            }.toString()

            val request = Request.Builder()
                .url(chatEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Logger.e("QwenAIService", "API error ${response.code}: $errorBody")
                when (response.code) {
                    401, 403 -> return@withContext Result.failure(AIServiceError.QuotaExceeded())
                    429 -> return@withContext Result.failure(AIServiceError.QuotaExceeded())
                    else -> return@withContext Result.failure(
                        AIServiceError.UnknownError(IOException("HTTP ${response.code}"))
                    )
                }
            }

            val responseBody = response.body?.string() ?: ""
            parseResponse(responseBody)
        } catch (e: java.net.SocketTimeoutException) {
            Logger.e("QwenAIService", "Request timed out", e)
            Result.failure(AIServiceError.TimeoutError())
        } catch (e: IOException) {
            Logger.e("QwenAIService", "Network error", e)
            // Retry once for transient network errors
            try {
                Logger.d("QwenAIService", "Retrying request after network error...")
                retryRequest(systemPrompt, userMessage, maxTokens, model)
            } catch (retryErr: Exception) {
                Result.failure(AIServiceError.NetworkError(e))
            }
        } catch (e: Exception) {
            Logger.e("QwenAIService", "Unexpected error", e)
            Result.failure(AIServiceError.UnknownError(e))
        }
    }

    private suspend fun retryRequest(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("input", JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system"); put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user"); put("content", userMessage)
                        })
                    })
                })
                put("parameters", JSONObject().apply {
                    put("max_tokens", maxTokens)
                    put("temperature", 0.7)
                    put("result_format", "message")
                })
            }.toString()
            val request = Request.Builder()
                .url(chatEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = client.newCall(request).execute()
            parseResponse(response.body?.string() ?: "")
        } catch (e: Exception) {
            Result.failure(AIServiceError.NetworkError(e))
        }
    }

    private fun parseResponse(responseBody: String): Result<String> {
        return try {
            val json = JSONObject(responseBody)
            val output = json.optJSONObject("output")
                ?: return Result.failure(AIServiceError.InvalidResponse("Missing 'output' field"))
            // Try choices (result_format=message) first, then text field
            val choices = output.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0)
                    .optJSONObject("message")?.optString("content", "")
                if (!content.isNullOrBlank())
                    return Result.success(stripCodeFences(content))
            }
            val text = output.optString("text", "")
            if (text.isNotBlank())
                return Result.success(stripCodeFences(text))
            Result.failure(AIServiceError.InvalidResponse("No content in response"))
        } catch (e: Exception) {
            Result.failure(AIServiceError.InvalidResponse("JSON parse error: ${e.message}"))
        }
    }

    private fun stripCodeFences(text: String): String {
        var result = text.trim()
        if (result.startsWith("```json")) result = result.removePrefix("```json").trim()
        else if (result.startsWith("```")) result = result.removePrefix("```").trim()
        if (result.endsWith("```")) result = result.removeSuffix("```").trim()
        return result
    }
}
