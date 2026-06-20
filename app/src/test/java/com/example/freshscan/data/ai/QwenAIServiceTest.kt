package com.example.freshscan.data.ai

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class QwenAIServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var service: QwenAIService
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: Call

    private val testApiKey = "test-api-key-12345"
    private val testBaseUrl = "https://mock-dashscope.example.com"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockCall = mockk(relaxed = true)
        mockClient = mockk(relaxed = true)
        every { mockClient.newCall(any()) } returns mockCall

        service = QwenAIService(apiKey = testApiKey, baseUrl = testBaseUrl)

        // Replace the private OkHttpClient with our mock via reflection
        val clientField = QwenAIService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(service, mockClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Build a realistic DashScope response JSON matching the structure
     * that [QwenAIService.parseResponse] expects (result_format=message).
     */
    private fun buildChoicesResponse(content: String): String {
        return JSONObject().apply {
            put("output", JSONObject().apply {
                put("choices", JSONArray().apply {
                    put(JSONObject().apply {
                        put("message", JSONObject().apply {
                            put("role", "assistant")
                            put("content", content)
                        })
                    })
                })
            })
            put("usage", JSONObject().put("total_tokens", 100))
        }.toString()
    }

    /** Build a response using the legacy `text` field instead of choices. */
    private fun buildTextResponse(text: String): String {
        return JSONObject().apply {
            put("output", JSONObject().apply {
                put("text", text)
            })
            put("usage", JSONObject().put("total_tokens", 50))
        }.toString()
    }

    private fun mockResponse(httpCode: Int, body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url("$testBaseUrl/services/aigc/text-generation/generation").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(httpCode)
            .message(if (httpCode in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun mockEmptyBodyResponse(httpCode: Int): Response {
        return Response.Builder()
            .request(Request.Builder().url("$testBaseUrl/services/aigc/text-generation/generation").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(httpCode)
            .message("OK")
            .build()
    }

    // ── Scenario 1: Successful chatJson response parses JSON correctly ──

    @Test
    fun `given valid API response with choices when chatJson called then returns parsed content`() = runTest {
        val expectedJson = """{"items": [{"name": "apple"}]}"""
        val response = mockResponse(200, buildChoicesResponse(expectedJson))
        every { mockCall.execute() } returns response

        val result = service.chatJson("system prompt", "user message", "{}")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals(expectedJson, result.getOrNull())
    }

    @Test
    fun `given valid API response with text field when chatJson called then returns parsed content`() = runTest {
        val expectedText = """{"status": "ok"}"""
        val response = mockResponse(200, buildTextResponse(expectedText))
        every { mockCall.execute() } returns response

        val result = service.chatJson("system prompt", "user message", "")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals(expectedText, result.getOrNull())
    }

    @Test
    fun `given response wrapped in code fences when chatJson called then strips fences`() = runTest {
        val fencedContent = "```json\n{\"key\": \"value\"}\n```"
        val response = mockResponse(200, buildChoicesResponse(fencedContent))
        every { mockCall.execute() } returns response

        val result = service.chatJson("sys", "msg", "")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals("{\"key\": \"value\"}", result.getOrNull())
    }

    @Test
    fun `given blank jsonSchema when chatJson called then still succeeds`() = runTest {
        val response = mockResponse(200, buildChoicesResponse("""{"data": 1}"""))
        every { mockCall.execute() } returns response

        val result = service.chatJson("system", "user", "")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `given valid API response when chat called then returns content`() = runTest {
        val expectedContent = "Hello! I am Qwen."
        val response = mockResponse(200, buildChoicesResponse(expectedContent))
        every { mockCall.execute() } returns response

        val result = service.chat("You are a helpful assistant.", "Hi")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals(expectedContent, result.getOrNull())
    }

    // ── Scenario 2: 401/403 HTTP response returns authentication error ──

    @Test
    fun `given HTTP 401 response when request made then returns AuthenticationError`() = runTest {
        val response = mockResponse(401, """{"error": "Unauthorized"}""")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.AuthenticationError)
    }

    @Test
    fun `given HTTP 403 response when request made then returns AuthenticationError`() = runTest {
        val response = mockResponse(403, """{"error": "Forbidden"}""")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.AuthenticationError)
    }

    @Test
    fun `given HTTP 401 response when chatJson called then returns AuthenticationError`() = runTest {
        val response = mockResponse(401, "")
        every { mockCall.execute() } returns response

        val result = service.chatJson("sys", "msg", "{}")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.AuthenticationError)
    }

    @Test
    fun `given HTTP 429 response when request made then returns QuotaExceeded`() = runTest {
        val response = mockResponse(429, """{"error": "rate limit"}""")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.QuotaExceeded)
    }

    @Test
    fun `given HTTP 500 response when request made then returns UnknownError`() = runTest {
        val response = mockResponse(500, """{"error": "internal"}""")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.UnknownError)
    }

    // ── Scenario 3: IOException during request (retry or network error) ──

    @Test
    fun `given IOException on first call and success on retry when request made then returns success`() = runTest {
        val successResponse = mockResponse(200, buildChoicesResponse("recovered"))
        var callCount = 0
        every { mockCall.execute() } answers {
            callCount++
            if (callCount == 1) throw java.io.IOException("Connection reset")
            else successResponse
        }

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals("recovered", result.getOrNull())
        assertEquals(2, callCount)
    }

    @Test
    fun `given IOException on both calls when request made then returns NetworkError`() = runTest {
        every { mockCall.execute() } throws java.io.IOException("Connection refused")

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.NetworkError)
    }

    @Test
    fun `given IOException then generic Exception on retry when request made then returns NetworkError`() = runTest {
        var callCount = 0
        every { mockCall.execute() } answers {
            callCount++
            if (callCount == 1) throw java.io.IOException("Socket closed")
            else throw RuntimeException("Unexpected retry failure")
        }

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        // The outer catch wraps retry exception in NetworkError with original cause
        assertTrue(result.exceptionOrNull() is AIServiceError.NetworkError)
    }

    // ── Scenario 4: Timeout returns failure ───────────────────────────

    @Test
    fun `given SocketTimeoutException when request made then returns TimeoutError`() = runTest {
        every { mockCall.execute() } throws SocketTimeoutException("Read timed out")

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.TimeoutError)
    }

    @Test
    fun `given SocketTimeoutException when chatJson called then returns TimeoutError`() = runTest {
        every { mockCall.execute() } throws SocketTimeoutException("Read timed out")

        val result = service.chatJson("sys", "msg", "{}")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.TimeoutError)
    }

    // ── Scenario 5: Empty/null response body returns failure ──────────

    @Test
    fun `given empty response body string when request made then returns InvalidResponse`() = runTest {
        val response = mockResponse(200, "")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.InvalidResponse)
    }

    @Test
    fun `given null response body when request made then returns InvalidResponse`() = runTest {
        val response = mockEmptyBodyResponse(200)
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.InvalidResponse)
    }

    @Test
    fun `given malformed JSON response when request made then returns InvalidResponse`() = runTest {
        val response = mockResponse(200, "this is not json at all")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.InvalidResponse)
    }

    @Test
    fun `given response missing output field when request made then returns InvalidResponse`() = runTest {
        val response = mockResponse(200, """{"usage": {"total_tokens": 10}}""")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.InvalidResponse)
    }

    @Test
    fun `given response with empty choices and blank text when request made then returns InvalidResponse`() = runTest {
        val jsonBody = """{"output": {"choices": [], "text": ""}}"""
        val response = mockResponse(200, jsonBody)
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.InvalidResponse)
    }

    // ── Scenario 6: API key not configured — behavior check ──────────

    @Test
    fun `given empty API key when request made then sends request with empty bearer token`() = runTest {
        val emptyKeyService = QwenAIService(apiKey = "", baseUrl = testBaseUrl)
        val emptyKeyField = QwenAIService::class.java.getDeclaredField("client")
        emptyKeyField.isAccessible = true
        emptyKeyField.set(emptyKeyService, mockClient)

        val response = mockResponse(200, buildChoicesResponse("ok"))
        every { mockCall.execute() } returns response

        val result = emptyKeyService.chat("sys", "msg")
        advanceUntilIdle()

        // Service does not pre-validate the key; it delegates to the API.
        // If the server still responds 200, the result is success.
        assertTrue(result.isSuccess)
        verify { mockClient.newCall(any()) }
    }

    @Test
    fun `given empty API key when server rejects with 401 then returns AuthenticationError`() = runTest {
        val emptyKeyService = QwenAIService(apiKey = "", baseUrl = testBaseUrl)
        val emptyKeyField = QwenAIService::class.java.getDeclaredField("client")
        emptyKeyField.isAccessible = true
        emptyKeyField.set(emptyKeyService, mockClient)

        val response = mockResponse(401, """{"error": "invalid api-key"}""")
        every { mockCall.execute() } returns response

        val result = emptyKeyService.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.AuthenticationError)
    }

    // ── Error message content checks ─────────────────────────────────

    @Test
    fun `given TimeoutError when checked then has expected message`() = runTest {
        every { mockCall.execute() } throws SocketTimeoutException("timed out")

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        val error = result.exceptionOrNull() as AIServiceError.TimeoutError
        assertEquals("AI 响应超时，请稍后重试", error.message)
    }

    @Test
    fun `given NetworkError when checked then has expected message`() = runTest {
        every { mockCall.execute() } throws java.io.IOException("no network")

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        val error = result.exceptionOrNull() as AIServiceError.NetworkError
        assertEquals("网络连接失败，请检查网络后重试", error.message)
    }

    @Test
    fun `given AuthenticationError when checked then has expected message`() = runTest {
        val response = mockResponse(401, "")
        every { mockCall.execute() } returns response

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        val error = result.exceptionOrNull() as AIServiceError.AuthenticationError
        assertEquals("AI 服务认证失败，请检查 API 密钥", error.message)
    }

    @Test
    fun `given unexpected exception when request made then returns UnknownError`() = runTest {
        every { mockCall.execute() } throws IllegalStateException("Something very unexpected")

        val result = service.chat("sys", "msg")
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIServiceError.UnknownError)
    }
}
