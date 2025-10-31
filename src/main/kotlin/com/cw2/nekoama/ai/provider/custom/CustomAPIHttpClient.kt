package com.cw2.nekoama.ai.provider.custom

import com.cw2.nekoama.ai.provider.openai.OpenAIRequest
import com.cw2.nekoama.ai.provider.openai.OpenAIResponse
import com.cw2.nekoama.core.result.Result
import com.cw2.nekoama.core.exception.NekoamaError
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.toJson
import com.cw2.nekoama.core.serialization.fromJson
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * 自定义 API HTTP 客户端
 * 
 * 支持灵活的认证方式、自定义端点和可选的 SSL 验证。
 * 兼容 OpenAI API 格式，但允许使用不同的服务提供商。
 */
class CustomAPIHttpClient(
    private val config: CustomAPIConfig
) {
    
    private val httpClient = createHttpClient()
    
    /**
     * 创建 HTTP 客户端，支持自定义 SSL 设置
     */
    private fun createHttpClient(): HttpClient {
        // 连接超时设置为总超时的1/4，为AI服务响应留出更多时间
        val connectTimeoutMs = (config.timeoutMs / 4).coerceAtLeast(10000L).coerceAtMost(15000L)
        
        val builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        
        // 如果禁用 SSL 验证，使用自定义 SSL 上下文
        if (!config.verifySSL) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                builder.sslContext(sslContext)
                
                NekoamaLogger.warn("CustomAPIHttpClient", "SSL 验证已禁用，仅用于开发环境")
            } catch (e: Exception) {
                NekoamaLogger.warn("CustomAPIHttpClient", "无法禁用 SSL 验证: ${e.message}")
            }
        }
        
        return builder.build()
    }
    
    /**
     * 发送请求到自定义 API 端点
     */
    suspend fun sendRequest(request: OpenAIRequest): Result<OpenAIResponse> {
        return try {
            val startTime = System.currentTimeMillis()
            
            // 移除协程超时，使用HTTP请求本身的超时设置
            val httpRequest = buildHttpRequest(request)
            val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            
            val duration = System.currentTimeMillis() - startTime
            
            when (httpResponse.statusCode()) {
                200 -> {
                    val response = parseSuccessResponse(httpResponse.body())
                    response.onSuccess { 
                        NekoamaLogger.logAICall(
                            provider = config.providerName,
                            model = config.model,
                            operation = "sendRequest",
                            success = true,
                            durationMs = duration,
                            tokenCount = it.usage?.totalTokens
                        )
                    }
                    response
                }
                400 -> {
                    val error = NekoamaError.APIError.BadRequest("请求格式错误: ${httpResponse.body()}")
                    NekoamaLogger.logError("sendRequest", error)
                    Result.error(error)
                }
                401 -> {
                    val error = NekoamaError.AuthenticationError.InvalidApiKey("认证失败")
                    NekoamaLogger.logError("sendRequest", error)
                    Result.error(error)
                }
                403 -> {
                    val error = NekoamaError.AuthenticationError.InsufficientPermissions("权限不足")
                    NekoamaLogger.logError("sendRequest", error)
                    Result.error(error)
                }
                429 -> {
                    val retryAfter = parseRetryAfter(httpResponse.headers().map())
                    val error = NekoamaError.RateLimitError.TooManyRequests(retryAfter = retryAfter)
                    NekoamaLogger.logError("sendRequest", error)
                    Result.error(error)
                }
                500, 502, 503, 504 -> {
                    val error = NekoamaError.APIError.ServerError("服务器错误: ${httpResponse.statusCode()}")
                    NekoamaLogger.logError("sendRequest", error)
                    Result.error(error)
                }
                else -> {
                    val error = NekoamaError.APIError.ServerError("未知错误: ${httpResponse.statusCode()}")
                    NekoamaLogger.logError("sendRequest", error)
                    Result.error(error)
                }
            }
        } catch (e: java.net.http.HttpTimeoutException) {
            val error = NekoamaError.NetworkError.ReadTimeout("请求超时")
            NekoamaLogger.logError("sendRequest", error)
            Result.error(error)
        } catch (e: Exception) {
            val error = NekoamaError.NetworkError.Generic("网络请求失败: ${e.message}")
            NekoamaLogger.logError("sendRequest", error, context = mapOf("exception" to e.message))
            Result.error(error)
        }
    }
    
    /**
     * 构建 HTTP 请求
     */
    private fun buildHttpRequest(request: OpenAIRequest): HttpRequest {
        val jsonBody = request.toJson().getOrNull() 
            ?: throw IllegalArgumentException("无法序列化请求")
        
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.buildEndpointUrl()))
            .header("Content-Type", "application/json")
            .header("User-Agent", "nekoama-intellij-plugin/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofMillis(config.timeoutMs))
        
        // 添加认证和自定义头部
        config.getAuthHeaders().forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        
        return requestBuilder.build()
    }
    
    /**
     * 解析成功响应
     */
    private fun parseSuccessResponse(body: String): Result<OpenAIResponse> {
        return try {
            body.fromJson<OpenAIResponse>()
        } catch (e: Exception) {
            val error = NekoamaError.ParseError.JsonParse("解析响应失败: ${e.message}")
            NekoamaLogger.logError("parseSuccessResponse", error, context = mapOf("exception" to e.message))
            Result.error(error)
        }
    }
    
    /**
     * 同步发送请求到自定义 API 端点（用于后台线程调用，避免 runBlocking 导致的线程模型违规）
     */
    fun sendRequestSync(request: OpenAIRequest): Result<OpenAIResponse> {
        return try {
            val startTime = System.currentTimeMillis()
            
            val httpRequest = buildHttpRequest(request)
            val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            
            val duration = System.currentTimeMillis() - startTime
            
            when (httpResponse.statusCode()) {
                200 -> {
                    val response = parseSuccessResponse(httpResponse.body())
                    response.onSuccess { 
                        NekoamaLogger.logAICall(
                            provider = config.providerName,
                            model = config.model,
                            operation = "sendRequestSync",
                            success = true,
                            durationMs = duration,
                            tokenCount = it.usage?.totalTokens
                        )
                    }
                    response
                }
                400 -> {
                    val error = NekoamaError.APIError.BadRequest("请求格式错误: ${httpResponse.body()}")
                    NekoamaLogger.logError("sendRequestSync", error)
                    Result.error(error)
                }
                401 -> {
                    val error = NekoamaError.AuthenticationError.InvalidApiKey("认证失败")
                    NekoamaLogger.logError("sendRequestSync", error)
                    Result.error(error)
                }
                403 -> {
                    val error = NekoamaError.AuthenticationError.InsufficientPermissions("权限不足")
                    NekoamaLogger.logError("sendRequestSync", error)
                    Result.error(error)
                }
                429 -> {
                    val retryAfter = parseRetryAfter(httpResponse.headers().map())
                    val error = NekoamaError.RateLimitError.TooManyRequests(retryAfter = retryAfter)
                    NekoamaLogger.logError("sendRequestSync", error)
                    Result.error(error)
                }
                500, 502, 503, 504 -> {
                    val error = NekoamaError.APIError.ServerError("服务器错误: ${httpResponse.statusCode()}")
                    NekoamaLogger.logError("sendRequestSync", error)
                    Result.error(error)
                }
                else -> {
                    val error = NekoamaError.APIError.ServerError("未知错误: ${httpResponse.statusCode()}")
                    NekoamaLogger.logError("sendRequestSync", error)
                    Result.error(error)
                }
            }
        } catch (e: java.net.http.HttpTimeoutException) {
            val error = NekoamaError.NetworkError.ReadTimeout("请求超时")
            NekoamaLogger.logError("sendRequestSync", error)
            Result.error(error)
        } catch (e: Exception) {
            val error = NekoamaError.NetworkError.Generic("网络请求失败: ${e.message}")
            NekoamaLogger.logError("sendRequestSync", error, context = mapOf("exception" to e.message))
            Result.error(error)
        }
    }
    
    /**
     * 解析 Retry-After 头部
     */
    private fun parseRetryAfter(headers: Map<String, List<String>>): Long? {
        return headers["retry-after"]?.firstOrNull()?.toLongOrNull()?.times(1000)
    }
}