package com.cw2.nekoama.core.network

import com.cw2.nekoama.core.logging.NekoamaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 代理连接测试器
 *
 * 提供代理连接的测试和诊断功能
 */
object ProxyConnectionTester {

    private const val LOG_TAG = "ProxyConnectionTester"
    private const val DEFAULT_TEST_URL = "https://api.openai.com"
    private const val CONNECTION_TIMEOUT = 10000 // 10秒
    private const val READ_TIMEOUT = 10000 // 10秒

    /**
     * 代理连接测试结果
     */
    data class ProxyTestResult(
        val success: Boolean,
        val responseTime: Long = -1,
        val statusCode: Int = -1,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )

    /**
     * 测试代理连接
     *
     * @param proxyConfig 代理配置
     * @param testUrl 测试URL
     * @return 测试结果
     */
    suspend fun testProxyConnection(
        proxyConfig: ProxyConfig,
        testUrl: String = DEFAULT_TEST_URL
    ): ProxyTestResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val startTime = System.currentTimeMillis()

            if (proxyConfig.type == ProxyType.DIRECT) {
                // 测试直连
                testDirectConnection(testUrl, startTime)
            } else if (!proxyConfig.isValid()) {
                ProxyTestResult(
                    success = false,
                    message = "代理配置无效: ${getInvalidReason(proxyConfig)}"
                )
            } else {
                // 测试代理连接
                testProxyConnectionInternal(proxyConfig, testUrl, startTime)
            }
        } catch (e: Exception) {
            NekoamaLogger.error(LOG_TAG, "代理连接测试异常: ${e.message}")
            ProxyTestResult(
                success = false,
                message = "测试异常: ${e.message}",
                details = mapOf("exception_type" to e.javaClass.simpleName)
            )
        }
    }

    /**
     * 测试直连
     */
    private fun testDirectConnection(testUrl: String, startTime: Long): ProxyTestResult {
        // 首先尝试HEAD请求
        val headResult = testConnectionWithMethod(testUrl, startTime, "HEAD", "direct", null)

        // 如果HEAD请求返回404，尝试GET请求
        if (headResult.statusCode == 404) {
            NekoamaLogger.info(LOG_TAG, "HEAD请求返回404，尝试GET请求进行验证")
            val getResult = testConnectionWithMethod(testUrl, System.currentTimeMillis(), "GET", "direct", null)

            // 如果GET请求成功，认为连接正常
            if (getResult.success) {
                return getResult.copy(
                    message = "直连测试成功 (HEAD返回404但GET正常，响应时间: ${getResult.responseTime}ms)",
                    statusCode = 200, // 将状态码改为200表示连接成功
                    details = getResult.details.toMutableMap() + ("head_method_404" to true)
                )
            } else {
                return getResult
            }
        }

        return headResult
    }

    /**
     * 使用指定HTTP方法测试连接
     */
    private fun testConnectionWithMethod(
        testUrl: String,
        startTime: Long,
        method: String,
        connectionType: String,
        proxyConfig: ProxyConfig?
    ): ProxyTestResult {
        return try {
            val url = URL(testUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = method
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("User-Agent", "nekoama-intellij-plugin/1.0")

            // 为GET请求添加合适的Accept头
            if (method == "GET") {
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
            }

            val responseTime = System.currentTimeMillis() - startTime
            val statusCode = connection.responseCode

            // 获取响应头信息用于诊断
            val responseHeaders = mutableMapOf<String, Any>()
            for (key in connection.headerFields.keys) {
                key?.let {
                    val value = connection.getHeaderField(it)
                    if (value != null) {
                        responseHeaders[it] = value
                    }
                }
            }

            connection.disconnect()

            // 更宽松的成功判断：对于测试连接，404有时是可以接受的
            val isSuccess = when {
                statusCode in 200..399 -> true
                statusCode == 404 && method == "HEAD" -> false // HEAD的404需要后续验证
                statusCode == 404 && method == "GET" -> true // GET的404表示服务器可达，只是路径不存在
                statusCode in 400..499 -> false // 客户端错误
                statusCode >= 500 -> false // 服务器错误
                else -> false
            }

            val baseDetails = mutableMapOf(
                "connection_type" to connectionType,
                "http_method" to method,
                "test_url" to testUrl,
                "response_headers" to responseHeaders
            )

            // 添加代理相关详细信息
            if (connectionType == "proxy" && proxyConfig != null) {
                baseDetails["proxy_type"] = proxyConfig.type.name
                baseDetails["proxy_host"] = proxyConfig.host ?: ""
                baseDetails["proxy_port"] = (proxyConfig.port ?: 0).toString()
                baseDetails["proxy_auth"] = (!proxyConfig.username.isNullOrBlank())
            }

            if (isSuccess) {
                ProxyTestResult(
                    success = true,
                    responseTime = responseTime,
                    statusCode = statusCode,
                    message = if (statusCode == 404) {
                        "服务器可达但路径不存在 (连接正常，HTTP $statusCode)"
                    } else {
                        "测试成功 (响应时间: ${responseTime}ms)"
                    },
                    details = baseDetails as Map<String, Any>
                )
            } else {
                ProxyTestResult(
                    success = false,
                    responseTime = responseTime,
                    statusCode = statusCode,
                    message = getErrorMessage(statusCode, method),
                    details = baseDetails as Map<String, Any>
                )
            }
        } catch (e: Exception) {
            val baseDetails = mutableMapOf(
                "connection_type" to connectionType,
                "http_method" to method,
                "test_url" to testUrl,
                "exception_type" to e.javaClass.simpleName,
                "exception_message" to e.message
            )

            if (connectionType == "proxy" && proxyConfig != null) {
                baseDetails["proxy_type"] = proxyConfig.type.name
                baseDetails["proxy_host"] = proxyConfig.host ?: ""
                baseDetails["proxy_port"] = (proxyConfig.port ?: 0).toString()
            }

            ProxyTestResult(
                success = false,
                message = getErrorMessage(-1, method) + ": ${e.message}",
                details = baseDetails as Map<String, Any>
            )
        }
    }

    /**
     * 获取错误消息
     */
    private fun getErrorMessage(statusCode: Int, method: String): String {
        return when {
            statusCode == -1 -> "连接异常"
            statusCode == 401 -> "认证失败"
            statusCode == 403 -> "权限不足"
            statusCode == 404 -> {
                if (method == "HEAD") {
                    "HEAD请求返回404 (路径可能不存在，但服务器可达)"
                } else {
                    "路径不存在"
                }
            }
            statusCode == 407 -> "代理认证失败"
            statusCode in 400..499 -> "客户端错误 HTTP $statusCode"
            statusCode in 500..599 -> "服务器错误 HTTP $statusCode"
            else -> "测试失败 HTTP $statusCode"
        }
    }

    /**
     * 测试代理连接
     */
    private fun testProxyConnectionInternal(
        proxyConfig: ProxyConfig,
        testUrl: String,
        startTime: Long
    ): ProxyTestResult {
        return try {
            // 配置系统代理
            HttpClientProxyConfigurator.configureSystemProxy(proxyConfig)

            // 首先尝试HEAD请求
            val headResult = testConnectionWithMethod(testUrl, startTime, "HEAD", "proxy", proxyConfig)

            // 如果HEAD请求返回404，尝试GET请求
            if (headResult.statusCode == 404) {
                NekoamaLogger.info(LOG_TAG, "代理HEAD请求返回404，尝试GET请求进行验证")
                val getResult = testConnectionWithMethod(testUrl, System.currentTimeMillis(), "GET", "proxy", proxyConfig)

                // 如果GET请求成功，认为连接正常
                if (getResult.success) {
                    return getResult.copy(
                        message = "代理测试成功 (HEAD返回404但GET正常，响应时间: ${getResult.responseTime}ms)",
                        statusCode = 200, // 将状态码改为200表示连接成功
                        details = getResult.details.toMutableMap() + ("head_method_404" to true)
                    )
                } else {
                    return getResult
                }
            }

            return headResult

        } catch (e: Exception) {
            ProxyTestResult(
                success = false,
                message = "代理测试失败: ${e.message}",
                details = mapOf<String, Any>(
                    "connection_type" to "proxy",
                    "proxy_type" to proxyConfig.type.name,
                    "proxy_host" to (proxyConfig.host ?: ""),
                    "proxy_port" to (proxyConfig.port ?: 0),
                    "proxy_auth" to (!proxyConfig.username.isNullOrBlank()),
                    "test_url" to testUrl,
                    "exception_type" to e.javaClass.simpleName,
                    "exception_message" to (e.message ?: "")
                )
            )
        }
    }

    /**
     * 获取代理配置无效的原因
     */
    private fun getInvalidReason(proxyConfig: ProxyConfig): String {
        return when {
            proxyConfig.host.isNullOrBlank() -> "代理主机地址为空"
            proxyConfig.port == null -> "代理端口未设置"
            proxyConfig.port!! < 1 || proxyConfig.port!! > 65535 -> "代理端口超出有效范围 (1-65535)"
            else -> "代理配置无效"
        }
    }

    /**
     * 测试当前IDEA代理设置
     *
     * @param testUrl 测试URL
     * @return 测试结果
     */
    suspend fun testCurrentIDEAProxy(testUrl: String = DEFAULT_TEST_URL): ProxyTestResult {
        val proxyConfig = ProxyDetector.detectSystemProxy(testUrl)
        return testProxyConnection(proxyConfig, testUrl)
    }


}