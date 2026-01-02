package com.cw2.nekoama.infrastructure.network.proxy

import kotlinx.serialization.Serializable
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 代理配置数据模型
 *
 * 支持HTTP、HTTPS和SOCKS代理配置，包含认证信息
 */
@Serializable
data class ProxyConfig(
    /**
     * 代理类型
     */
    val type: ProxyType = ProxyType.DIRECT,

    /**
     * 代理主机地址
     */
    val host: String? = null,

    /**
     * 代理端口
     */
    val port: Int? = null,

    /**
     * 用户名（可选）
     */
    val username: String? = null,

    /**
     * 密码（可选）
     */
    val password: String? = null,

    /**
     * 是否绕过本地地址
     */
    val bypassLocal: Boolean = true,

    /**
     * 不使用代理的主机列表
     */
    val bypassHosts: List<String> = emptyList()
) {

    /**
     * 检查配置是否有效
     */
    fun isValid(): Boolean {
        return when (type) {
            ProxyType.DIRECT -> true
            ProxyType.HTTP, ProxyType.HTTPS, ProxyType.SOCKS -> {
                !host.isNullOrBlank() && port != null && port in 1..65535
            }
        }
    }

    /**
     * 转换为Java Proxy对象
     */
    fun toJavaProxy(): Proxy {
        return when (type) {
            ProxyType.DIRECT -> Proxy.NO_PROXY
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host!!, port!!))
            ProxyType.HTTPS -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host!!, port!!))
            ProxyType.SOCKS -> Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host!!, port!!))
        }
    }

    /**
     * 检查给定主机是否应该绕过代理
     */
    fun shouldBypass(host: String): Boolean {
        if (bypassLocal && isLocalHost(host)) {
            return true
        }

        return bypassHosts.any { pattern ->
            host.contains(pattern, ignoreCase = true) ||
            host.matches(pattern.toRegex())
        }
    }

    /**
     * 检查是否为本地主机
     */
    private fun isLocalHost(host: String): Boolean {
        return host.equals("localhost", ignoreCase = true) ||
               host.equals("127.0.0.1") ||
               host.startsWith("192.168.") ||
               host.startsWith("10.") ||
               host.startsWith("172.16.") ||
               host.endsWith(".local")
    }

    companion object {
        /**
         * 创建直连配置
         */
        fun direct(): ProxyConfig = ProxyConfig()

        /**
         * 创建HTTP代理配置
         */
        fun http(host: String, port: Int, username: String? = null, password: String? = null): ProxyConfig {
            return ProxyConfig(ProxyType.HTTP, host, port, username, password)
        }

        /**
         * 创建HTTPS代理配置
         */
        fun https(host: String, port: Int, username: String? = null, password: String? = null): ProxyConfig {
            return ProxyConfig(ProxyType.HTTPS, host, port, username, password)
        }

        /**
         * 创建SOCKS代理配置
         */
        fun socks(host: String, port: Int, username: String? = null, password: String? = null): ProxyConfig {
            return ProxyConfig(ProxyType.SOCKS, host, port, username, password)
        }
    }
}

/**
 * 代理类型枚举
 */
@Serializable
enum class ProxyType {
    /**
     * 直连，不使用代理
     */
    DIRECT,

    /**
     * HTTP代理
     */
    HTTP,

    /**
     * HTTPS代理
     */
    HTTPS,

    /**
     * SOCKS代理
     */
    SOCKS
}
