package com.cw2.nekoama.domain.statistics.model

import com.cw2.nekoama.infrastructure.network.proxy.ProxyConfig

/**
 * API 连通性状态
 *
 * @property isConnected 是否连通
 * @property responseTime 响应时间（毫秒）
 * @property message 状态消息
 * @property proxyConfig 代理配置信息
 * @property troubleshootingGuide 排查指南（仅失败时）
 */
data class ConnectivityStatus(
    val isConnected: Boolean,
    val responseTime: Long = -1,
    val message: String,
    val proxyConfig: ProxyConfig? = null,
    val troubleshootingGuide: List<String>? = null
) {
    /**
     * 获取状态描述
     */
    fun getStatusDescription(): String {
        return if (isConnected) {
            "Connected (${responseTime}ms)"
        } else {
            "Disconnected"
        }
    }
}
