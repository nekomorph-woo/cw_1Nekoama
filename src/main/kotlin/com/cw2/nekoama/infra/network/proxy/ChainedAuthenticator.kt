package com.cw2.nekoama.infra.network.proxy

import com.cw2.nekoama.shared.logging.NekoamaLogger
import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * 链式认证器
 *
 * 支持多个认证器的链式调用，避免覆盖已有的系统认证器
 */
class ChainedAuthenticator(
    private val existingAuthenticator: Authenticator?,
    private val newAuthenticator: Authenticator
) : Authenticator() {

    companion object {
        private const val LOG_TAG = "ChainedAuthenticator"
    }

    override fun getPasswordAuthentication(): PasswordAuthentication {
        // 首先尝试新的认证器（通常是我们自己的SOCKS认证器）
        try {
            // 由于getPasswordAuthentication()是protected方法，我们需要通过反射调用
            val newResult = invokeGetPasswordAuthentication(newAuthenticator)
            if (isValidAuthentication(newResult)) {
                NekoamaLogger.debug(LOG_TAG, "使用新认证器提供认证")
                return newResult
            }
        } catch (e: Exception) {
            NekoamaLogger.debug(LOG_TAG, "新认证器处理失败: ${e.message}")
        }

        // 如果新认证器没有提供有效的认证信息，尝试原有的认证器
        if (existingAuthenticator != null) {
            try {
                val existingResult = invokeGetPasswordAuthentication(existingAuthenticator)
                if (isValidAuthentication(existingResult)) {
                    NekoamaLogger.debug(LOG_TAG, "使用原有认证器提供认证")
                    return existingResult
                }
            } catch (e: Exception) {
                NekoamaLogger.debug(LOG_TAG, "原有认证器处理失败: ${e.message}")
            }
        }

        // 如果都没有提供有效的认证信息，返回空认证
        NekoamaLogger.debug(LOG_TAG, "没有认证器能提供有效认证，返回空认证")
        return PasswordAuthentication("", charArrayOf())
    }

    /**
     * 通过反射调用protected方法getPasswordAuthentication
     */
    @Suppress("UNCHECKED_CAST")
    private fun invokeGetPasswordAuthentication(authenticator: Authenticator): PasswordAuthentication {
        try {
            val method = Authenticator::class.java.getDeclaredMethod("getPasswordAuthentication")
            method.isAccessible = true
            return method.invoke(authenticator) as PasswordAuthentication
        } catch (e: Exception) {
            throw RuntimeException("无法调用认证器的getPasswordAuthentication方法", e)
        }
    }

    /**
     * 检查认证结果是否有效
     */
    private fun isValidAuthentication(auth: PasswordAuthentication): Boolean {
        return auth.userName.isNotEmpty() || auth.password.isNotEmpty()
    }
}