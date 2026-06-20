package com.example.freshscan.data.ai

sealed class AIServiceError(message: String) : Exception(message) {
    class NetworkError(cause: Throwable? = null) :
        AIServiceError("网络连接失败，请检查网络后重试")
    class TimeoutError :
        AIServiceError("AI 响应超时，请稍后重试")
    class AuthenticationError(cause: Throwable? = null) :
        AIServiceError("AI 服务认证失败，请检查 API 密钥")
    class QuotaExceeded :
        AIServiceError("AI 服务额度已用完")
    class InvalidResponse(cause: String) :
        AIServiceError("AI 返回格式异常：$cause")
    class UnknownError(cause: Throwable? = null) :
        AIServiceError("AI 服务异常：${cause?.message ?: "未知错误"}")
}
