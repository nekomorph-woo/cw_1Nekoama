package com.cw2.nekoama.infra.network.proxy

import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.infra.network.config.ProxyType
import com.cw2.nekoama.infra.network.config.ProxyConfig
import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * SOCKS5代理认证处理器
 *
 * 处理SOCKS代理的认证需求，包括：
 * - SOCKS5用户名密码认证
 * - 作用域控制的认证设置
 * - 认证信息验证
 */
object SocksAuthenticator {

    private const val LOG_TAG = "SocksAuthenticator"

    // 存储当前配置的代理信息，用于作用域控制
    private var currentProxyConfig: ProxyConfig? = null
    private var isConfigured = false

    /**
     * 配置SOCKS代理认证（作用域控制版本）
     *
     * 为JVM系统配置SOCKS代理认证，严格限制作用域避免影响其他网络操作
     *
     * @param proxyConfig 代理配置
     */
    fun configureSocksAuthentication(proxyConfig: ProxyConfig) {
        if (proxyConfig.type != ProxyType.SOCKS) {
            NekoamaLogger.debug(LOG_TAG, "非SOCKS代理类型，跳过认证配置")
            return
        }

        if (proxyConfig.username.isNullOrBlank()) {
            NekoamaLogger.info(LOG_TAG, "SOCKS代理未配置认证信息，使用无认证模式")
            clearSocksAuthentication() // 确保清理之前的配置
            return
        }

        try {
            // 验证配置完整性
            val validationResult = validateSocksAuthentication(proxyConfig)
            if (!validationResult.isValid) {
                NekoamaLogger.error(LOG_TAG, "SOCKS代理认证配置无效: ${validationResult.message}")
                return
            }

            // 清理之前的配置
            clearSocksAuthentication()

            // 创建作用域受限的认证器
            val scopedAuthenticator = createScopedAuthenticator(proxyConfig)

            // 设置系统认证器（使用链式模式保留原有认证器）
            val existingAuthenticator = Authenticator.getDefault()
            Authenticator.setDefault(ChainedAuthenticator(existingAuthenticator, scopedAuthenticator))

            // 设置系统属性作为备选方案
            configureSystemProperties(proxyConfig)

            // 记录当前配置
            currentProxyConfig = proxyConfig
            isConfigured = true

            NekoamaLogger.info(LOG_TAG,
                "SOCKS5代理认证已配置（作用域受限）: ${proxyConfig.username}@${proxyConfig.host}:${proxyConfig.port}")

        } catch (e: Exception) {
            NekoamaLogger.error(LOG_TAG, "SOCKS代理认证配置失败: ${e.message}", error = e)
            clearSocksAuthentication() // 出错时清理配置
        }
    }

    /**
     * 创建作用域受限的认证器
     */
    private fun createScopedAuthenticator(proxyConfig: ProxyConfig): Authenticator {
        return object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                val requestingHost = requestingHost
                val requestingPort = requestingPort
                val requestingProtocol = requestingProtocol

                NekoamaLogger.debug(LOG_TAG,
                    "SOCKS认证请求检查: $requestingHost:$requestingPort, 协议: $requestingProtocol, 请求类型: $requestorType")

                // 严格的作用域控制：只对我们配置的SOCKS代理服务器提供认证
                if (isSocksProxyRequest(requestingHost, requestingPort, proxyConfig)) {
                    NekoamaLogger.debug(LOG_TAG, "认证请求匹配当前SOCKS代理配置，提供认证信息")
                    return PasswordAuthentication(
                        proxyConfig.username,
                        proxyConfig.password?.toCharArray() ?: charArrayOf()
                    )
                }

                // 对于不匹配的请求，返回空认证信息，让下一个认证器处理
                NekoamaLogger.debug(LOG_TAG, "认证请求不匹配SOCKS代理配置，跳过认证")
                return PasswordAuthentication("", charArrayOf())
            }
        }
    }

    /**
     * 检查是否为当前配置的SOCKS代理请求
     */
    private fun isSocksProxyRequest(host: String?, port: Int, proxyConfig: ProxyConfig): Boolean {
        // 主机名和端口必须完全匹配
        if (host != proxyConfig.host || port != proxyConfig.port) {
            return false
        }

        // 由于无法直接访问requestingProtocol和requestorType（这些是protected属性），
        // 我们使用更简单的逻辑：主要依靠主机名和端口匹配
        // 同时检查当前是否确实配置了SOCKS认证
        return isConfigured && currentProxyConfig == proxyConfig
    }

    /**
     * 配置系统属性作为备选方案
     */
    private fun configureSystemProperties(proxyConfig: ProxyConfig) {
        try {
            System.setProperty("java.net.socks.username", proxyConfig.username)
            if (!proxyConfig.password.isNullOrBlank()) {
                System.setProperty("java.net.socks.password", proxyConfig.password)
            }
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "设置系统属性失败: ${e.message}")
        }
    }

    /**
     * 验证SOCKS代理认证配置
     *
     * @param proxyConfig 代理配置
     * @return 验证结果
     */
    fun validateSocksAuthentication(proxyConfig: ProxyConfig): ValidationResult {
        if (proxyConfig.type != ProxyType.SOCKS) {
            return ValidationResult(true, "非SOCKS代理，无需验证")
        }

        return when {
            proxyConfig.host.isNullOrBlank() -> {
                ValidationResult(false, "SOCKS代理主机地址不能为空")
            }
            proxyConfig.port == null || proxyConfig.port !in 1..65535 -> {
                ValidationResult(false, "SOCKS代理端口无效: ${proxyConfig.port}")
            }
            proxyConfig.username.isNullOrBlank() -> {
                ValidationResult(true, "SOCKS代理无认证模式")
            }
            proxyConfig.password.isNullOrBlank() -> {
                ValidationResult(false, "SOCKS代理已配置用户名但密码为空")
            }
            else -> {
                ValidationResult(true, "SOCKS代理认证配置完整")
            }
        }
    }

    /**
     * 清理SOCKS认证设置（改进版本）
     */
    fun clearSocksAuthentication() {
        try {
            // 只有在当前配置了SOCKS认证时才进行清理
            if (!isConfigured) {
                return
            }

            NekoamaLogger.info(LOG_TAG, "开始清理SOCKS代理认证设置")

            // 清理系统属性
            try {
                System.clearProperty("java.net.socks.username")
                System.clearProperty("java.net.socks.password")
            } catch (e: Exception) {
                NekoamaLogger.warn(LOG_TAG, "清理系统属性时出现异常: ${e.message}")
            }

            // 注意：不直接清理Authenticator.setDefault(null)，避免影响其他组件
            // 系统认证器的清理由ChainedAuthenticator处理

            // 重置状态
            currentProxyConfig = null
            isConfigured = false

            NekoamaLogger.info(LOG_TAG, "SOCKS代理认证设置已清理")
        } catch (e: Exception) {
            NekoamaLogger.warn(LOG_TAG, "清理SOCKS认证设置时出现异常: ${e.message}")
        }
    }

    /**
     * 获取当前SOCKS认证状态
     */
    fun getSocksAuthenticationStatus(): String {
        val socksUsername = System.getProperty("java.net.socks.username")
        val defaultAuthenticator = Authenticator.getDefault()

        return when {
            isConfigured && currentProxyConfig != null -> {
                "已配置SOCKS认证: ${currentProxyConfig!!.username}@${currentProxyConfig!!.host}:${currentProxyConfig!!.port}"
            }
            socksUsername != null -> {
                "已配置SOCKS认证: $socksUsername"
            }
            defaultAuthenticator != null -> {
                "已配置系统认证器"
            }
            else -> {
                "未配置SOCKS认证"
            }
        }
    }

    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}