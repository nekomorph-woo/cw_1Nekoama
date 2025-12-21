package com.cw2.nekoama.infra.network.interceptor

import com.cw2.nekoama.shared.logging.NekoamaLogger
import okhttp3.Interceptor
import okhttp3.Response
import kotlin.system.measureTimeMillis

/**
 * OkHttp 监控拦截器
 *
 * 提供HTTP请求的性能监控和统计，包括：
 * - 请求计数（成功/失败）
 * - 响应时间统计（平均、最大、最小）
 * - 响应大小统计
 * - 状态码分布
 */
class MonitoringInterceptor : Interceptor {

    // 请求统计
    private var totalRequests = 0L
    private var successfulRequests = 0L
    private var failedRequests = 0L

    // 响应时间统计
    private var totalResponseTime = 0L
    private var minResponseTime = Long.MAX_VALUE
    private var maxResponseTime = 0L

    // 响应大小统计
    private var totalBytesReceived = 0L
    private var totalBytesSent = 0L

    // 状态码统计
    private val statusCodeCounts = mutableMapOf<Int, Long>()

    // 线程安全锁
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // 获取请求大小
        val requestSize = request.body?.contentLength() ?: 0L

        var response: Response
        val responseTime = measureTimeMillis {
            try {
                response = chain.proceed(request)
            } catch (e: Exception) {
                throw e
            }
        }

        try {
            updateSuccessStats(response, responseTime, requestSize)
        } catch (e: Exception) {
            updateFailureStats(responseTime, requestSize)
        }

        return response
    }

    /**
     * 更新成功请求统计
     */
    private fun updateSuccessStats(response: Response, responseTime: Long, requestSize: Long) {
        synchronized(lock) {
            totalRequests++
            successfulRequests++

            // 响应时间统计
            totalResponseTime += responseTime
            minResponseTime = minOf(minResponseTime, responseTime)
            maxResponseTime = maxOf(maxResponseTime, responseTime)

            // 响应大小统计
            val responseSize = response.body?.contentLength() ?: 0L
            totalBytesReceived += responseSize
            totalBytesSent += requestSize

            // 状态码统计
            statusCodeCounts[response.code] = statusCodeCounts.getOrDefault(response.code, 0) + 1

            // 定期记录统计信息
            if (totalRequests % 10 == 0L) {
                logStatistics()
            }
        }
    }

    /**
     * 更新失败请求统计
     */
    private fun updateFailureStats(responseTime: Long, requestSize: Long) {
        synchronized(lock) {
            totalRequests++
            failedRequests++

            // 响应时间统计
            totalResponseTime += responseTime
            minResponseTime = minOf(minResponseTime, responseTime)
            maxResponseTime = maxOf(maxResponseTime, responseTime)

            // 请求大小统计
            totalBytesSent += requestSize
        }
    }

    /**
     * 记录统计信息
     */
    private fun logStatistics() {
        synchronized(lock) {
            val avgResponseTime = if (successfulRequests > 0) {
                totalResponseTime / successfulRequests
            } else {
                0L
            }

            val successRate = if (totalRequests > 0) {
                (successfulRequests * 100.0 / totalRequests)
            } else {
                0.0
            }

            NekoamaLogger.info("MonitoringInterceptor", buildString {
                append("HTTP请求统计 - ")
                append("总请求数: $totalRequests, ")
                append("成功: $successfulRequests, ")
                append("失败: $failedRequests, ")
                append(String.format("成功率: %.1f%%, ", successRate))
                append("平均响应时间: ${avgResponseTime}ms, ")
                append("最大响应时间: ${maxResponseTime}ms, ")
                append("最小响应时间: ${if (minResponseTime == Long.MAX_VALUE) 0 else minResponseTime}ms, ")
                append("总上传: ${formatBytes(totalBytesSent)}, ")
                append("总下载: ${formatBytes(totalBytesReceived)}")
            })

            // 记录状态码分布
            if (statusCodeCounts.isNotEmpty()) {
                val statusDistribution = statusCodeCounts.entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key}: ${it.value}" }

                NekoamaLogger.debug("MonitoringInterceptor",
                    "状态码分布: $statusDistribution")
            }
        }
    }

    /**
     * 格式化字节数
     */
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return String.format("%.1f %s", size, units[unitIndex])
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): HttpStatistics {
        synchronized(lock) {
            return HttpStatistics(
                totalRequests = totalRequests,
                successfulRequests = successfulRequests,
                failedRequests = failedRequests,
                averageResponseTime = if (successfulRequests > 0) totalResponseTime / successfulRequests else 0L,
                minResponseTime = if (minResponseTime == Long.MAX_VALUE) 0L else minResponseTime,
                maxResponseTime = maxResponseTime,
                totalBytesSent = totalBytesSent,
                totalBytesReceived = totalBytesReceived,
                statusCodeDistribution = statusCodeCounts.toMap()
            )
        }
    }

    /**
     * 重置统计信息
     */
    fun resetStatistics() {
        synchronized(lock) {
            totalRequests = 0L
            successfulRequests = 0L
            failedRequests = 0L
            totalResponseTime = 0L
            minResponseTime = Long.MAX_VALUE
            maxResponseTime = 0L
            totalBytesReceived = 0L
            totalBytesSent = 0L
            statusCodeCounts.clear()
        }
    }

    /**
     * HTTP统计数据类
     */
    data class HttpStatistics(
        val totalRequests: Long,
        val successfulRequests: Long,
        val failedRequests: Long,
        val averageResponseTime: Long,
        val minResponseTime: Long,
        val maxResponseTime: Long,
        val totalBytesSent: Long,
        val totalBytesReceived: Long,
        val statusCodeDistribution: Map<Int, Long>
    ) {
        val successRate: Double
            get() = if (totalRequests > 0) (successfulRequests * 100.0 / totalRequests) else 0.0
    }
}