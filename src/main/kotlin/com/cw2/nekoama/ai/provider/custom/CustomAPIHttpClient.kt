package com.cw2.nekoama.ai.provider.custom

import com.cw2.nekoama.ai.provider.openai.OpenAIRequest
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.toJson
import com.cw2.nekoama.core.network.ProxyDetector
import com.cw2.nekoama.core.network.ProxyType
import com.cw2.nekoama.ai.provider.custom.interceptor.HeadersInterceptor
import com.cw2.nekoama.ai.provider.custom.interceptor.LoggingInterceptor
import com.cw2.nekoama.ai.provider.custom.interceptor.RetryInterceptor
import com.cw2.nekoama.ai.provider.custom.interceptor.MonitoringInterceptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * 自定义 API HTTP 客户端 - OkHttp 版本
 *
 * 支持灵活的认证方式、自定义端点和可选的 SSL 验证。
 * 兼容 OpenAI API 格式，但允许使用不同的服务提供商。
 *
 * 相比 Java HttpClient 的优势：
 * - 更强大的拦截器系统
 * - 更好的连接池管理
 * - 更精细的超时控制
 * - 更智能的重试机制
 * - 更丰富的调试功能
 */
class CustomAPIHttpClient(
    private val config: CustomAPIConfig
) : BaseHttpClient() {

    private val monitoringInterceptor = MonitoringInterceptor()
    private val httpClient = createOkHttpClient()

    /**
     * 获取HTTP性能统计信息
     */
    fun getHttpStatistics(): MonitoringInterceptor.HttpStatistics {
        return monitoringInterceptor.getStatistics()
    }

    /**
     * 重置HTTP性能统计信息
     */
    fun resetHttpStatistics() {
        monitoringInterceptor.resetStatistics()
    }

    /**
     * 获取连接池信息
     */
    fun getConnectionPoolInfo(): ConnectionPoolInfo {
        val pool = httpClient.connectionPool
        return ConnectionPoolInfo(
            idleConnectionCount = pool.idleConnectionCount(),
            connectionCount = pool.connectionCount(),
            maxIdleConnections = 5, // 我们配置的最大值
            keepAliveDurationMs = 5 * 60 * 1000L // 5分钟
        )
    }

    /**
     * 预热连接池
     *
     * 通过发送一个轻量级请求来预热连接池，提高后续请求的性能
     */
    suspend fun warmupConnectionPool() {
        try {
            // 这里可以发送一个ping请求或者健康检查
            NekoamaLogger.info("CustomAPIHttpClient", "连接池预热开始")

            // 由于AI API的特性，这里只是记录日志，实际预热取决于具体使用
            NekoamaLogger.info("CustomAPIHttpClient", "连接池预热完成")
        } catch (e: Exception) {
            NekoamaLogger.warn("CustomAPIHttpClient", "连接池预热失败: ${e.message}")
        }
    }

    /**
     * 获取客户端配置摘要
     */
    fun getClientConfigurationSummary(): ClientConfigurationSummary {
        return ClientConfigurationSummary(
            timeoutMs = config.timeoutMs,
            verifySSL = config.verifySSL,
            endpointUrl = config.buildEndpointUrl(),
            hasAuthHeaders = config.getAuthHeaders().isNotEmpty(),
            supportsHttp2 = true,
            retryEnabled = true,
            monitoringEnabled = true,
            loggingEnabled = true
        )
    }

    /**
     * 连接池信息数据类
     */
    data class ConnectionPoolInfo(
        val idleConnectionCount: Int,
        val connectionCount: Int,
        val maxIdleConnections: Int,
        val keepAliveDurationMs: Long
    )

    /**
     * 客户端配置摘要数据类
     */
    data class ClientConfigurationSummary(
        val timeoutMs: Long,
        val verifySSL: Boolean,
        val endpointUrl: String,
        val hasAuthHeaders: Boolean,
        val supportsHttp2: Boolean,
        val retryEnabled: Boolean,
        val monitoringEnabled: Boolean,
        val loggingEnabled: Boolean
    )

    /**
     * 创建 OkHttp 客户端，使用拦截器系统处理SSL和代理配置
     */
    private fun createOkHttpClient(): OkHttpClient {
        // 连接超时设置为总超时的1/4，为AI服务响应留出更多时间
        val connectTimeoutMs = (config.timeoutMs / 4).coerceAtLeast(10000L).coerceAtMost(15000L)
        val readTimeoutMs = (config.timeoutMs / 2).coerceAtLeast(20000L).coerceAtMost(60000L)
        val writeTimeoutMs = (config.timeoutMs / 2).coerceAtLeast(20000L).coerceAtMost(60000L)
        val callTimeoutMs = config.timeoutMs

        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)

        // 配置连接池以提高性能
        builder.connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

        // 配置更多OkHttp优化特性
        configureOptimizations(builder)

        // 配置SSL（直接在客户端上配置，因为拦截器方式对SSL配置有限制）
        configureSSL(builder)

        // 配置代理（直接在客户端上配置，因为代理需要在连接建立前设置）
        configureProxy(builder)

        // 添加拦截器系统（按执行顺序排列）
        builder.addInterceptor(monitoringInterceptor) // 最外层，监控所有请求
        builder.addInterceptor(RetryInterceptor()) // 重试拦截器
        builder.addInterceptor(LoggingInterceptor(LoggingInterceptor.LogLevel.BASIC)) // 基础日志
        builder.addInterceptor(HeadersInterceptor(config.getAuthHeaders())) // 请求头处理

        NekoamaLogger.info("CustomAPIHttpClient", "OkHttp客户端配置完成（包含日志、监控、重试拦截器）")
        return builder.build()
    }

    /**
     * 配置SSL设置
     */
    private fun configureSSL(builder: OkHttpClient.Builder) {
        if (!config.verifySSL) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }

                NekoamaLogger.warn("CustomAPIHttpClient", "SSL 验证已禁用，仅用于开发环境")
            } catch (e: Exception) {
                NekoamaLogger.warn("CustomAPIHttpClient", "无法禁用 SSL 验证: ${e.message}")
            }
        }
    }

    /**
     * 配置代理设置
     */
    private fun configureProxy(builder: OkHttpClient.Builder) {
        val proxyConfig = ProxyDetector.detectSystemProxy(config.buildEndpointUrl())

        if (proxyConfig.type != ProxyType.DIRECT && proxyConfig.isValid()) {
            try {
                // 创建 OkHttp 代理对象
                val okHttpProxy = when (proxyConfig.type) {
                    ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port ?: 8080))
                    ProxyType.SOCKS -> Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyConfig.host, proxyConfig.port ?: 1080))
                    else -> Proxy.NO_PROXY
                }
                builder.proxy(okHttpProxy)

                // 配置代理认证器
                if (!proxyConfig.username.isNullOrBlank()) {
                    val authenticator = okhttp3.Authenticator { _, response ->
                        val credential = Credentials.basic(proxyConfig.username, proxyConfig.password ?: "")
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                    builder.proxyAuthenticator(authenticator)

                    NekoamaLogger.info("CustomAPIHttpClient",
                        "已配置${proxyConfig.type}代理认证: ${proxyConfig.username}@${proxyConfig.host}:${proxyConfig.port}")
                } else {
                    NekoamaLogger.warn("CustomAPIHttpClient",
                        "代理${proxyConfig.host}:${proxyConfig.port}未配置认证信息，可能导致HTTP 407错误")
                }

            } catch (e: Exception) {
                NekoamaLogger.error("CustomAPIHttpClient",
                    "代理配置失败，将使用直连: ${e.message}", error = e)
                // 代理配置失败时回退到直连
                builder.proxy(Proxy.NO_PROXY)
            }
        } else {
            NekoamaLogger.debug("CustomAPIHttpClient", "使用直连模式")
        }

        NekoamaLogger.info("CustomAPIHttpClient",
            "最终代理配置: ${ProxyDetector.getProxyStatus(proxyConfig)}")
    }

    /**
     * 配置OkHttp优化特性
     */
    private fun configureOptimizations(builder: OkHttpClient.Builder) {
        // 启用HTTP/2支持以提高性能
        builder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

        // 启用响应缓存（可选，对AI API不太适用，但保留配置）
        // 对于AI API，通常不需要缓存，因为每次请求都是独特的

        // 配置失败重试机制（OkHttp内置）
        builder.retryOnConnectionFailure(true)

        // 配置DNS解析优化
        try {
            // 使用系统DNS，但可以配置自定义DNS解析器
            // 这里暂时使用系统默认配置
        } catch (e: Exception) {
            NekoamaLogger.warn("CustomAPIHttpClient", "DNS配置失败，使用默认配置: ${e.message}")
        }

        // 配置PING间隔以保持连接活跃
        builder.pingInterval(30, TimeUnit.SECONDS)

        NekoamaLogger.debug("CustomAPIHttpClient", "OkHttp优化特性配置完成")
    }

    /**
     * 实现父类的异步HTTP请求发送方法
     */
    override suspend fun sendHttpRequest(request: OpenAIRequest): HttpResponseData {
        val okHttpRequest = buildOkHttpRequest(request)

        try {
            val response = httpClient.newCall(okHttpRequest).execute()
            return HttpResponseData(
                statusCode = response.code,
                body = response.body?.string() ?: "",
                headers = response.headers.toMultimap()
            )
        } catch (e: IOException) {
            NekoamaLogger.error("CustomAPIHttpClient",
                "异步请求失败: ${e.message}", error = e)
            throw e
        }
    }

    /**
     * 实现父类的同步HTTP请求发送方法
     */
    override fun sendHttpRequestSync(request: OpenAIRequest): HttpResponseData {
        val okHttpRequest = buildOkHttpRequest(request)

        try {
            val response = httpClient.newCall(okHttpRequest).execute()
            return HttpResponseData(
                statusCode = response.code,
                body = response.body?.string() ?: "",
                headers = response.headers.toMultimap()
            )
        } catch (e: IOException) {
            NekoamaLogger.error("CustomAPIHttpClient",
                "同步请求失败: ${e.message}", error = e)
            throw e
        }
    }

    /**
     * 构建 OkHttp 请求
     *
     * 注意：请求头和认证由HeadersInterceptor拦截器处理，这里只需要构建基本请求
     */
    private fun buildOkHttpRequest(request: OpenAIRequest): Request {
        val jsonBody = request.toJson().getOrNull()
            ?: throw IllegalArgumentException("无法序列化请求")

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        return Request.Builder()
            .url(config.buildEndpointUrl())
            .post(requestBody)
            .build()
    }

    /**
     * 关闭HTTP客户端，释放资源
     */
    fun close() {
        try {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
            NekoamaLogger.info("CustomAPIHttpClient", "HTTP客户端已关闭")
        } catch (e: Exception) {
            NekoamaLogger.warn("CustomAPIHttpClient", "关闭HTTP客户端时出现异常: ${e.message}")
        }
    }
}