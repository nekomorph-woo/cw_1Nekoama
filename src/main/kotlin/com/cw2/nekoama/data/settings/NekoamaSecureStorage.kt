package com.cw2.nekoama.data.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Nekoama 安全存储封装
 *
 * 使用 IntelliJ PasswordSafe 保存敏感信息，提供读/写/清除 API。
 */
object NekoamaSecureStorage {
    private const val SERVICE_NAME: String = "Nekoama"
    private const val API_KEY_KEY: String = "API_KEY"

    private fun credentialAttributes(key: String = API_KEY_KEY): CredentialAttributes {
        // 采用 generateServiceName 生成稳定的服务名，避免与其他插件冲突
        val service = generateServiceName(SERVICE_NAME, key)
        return CredentialAttributes(service)
    }

    /** 读取 API Key，若不存在返回空字符串 */
    suspend fun getApiKey(): String {
        return withContext(Dispatchers.IO) {
            // 在后台线程执行密码存储访问
            runCatching {
                val attrs = credentialAttributes()
                val creds = PasswordSafe.instance.get(attrs)
                creds?.getPasswordAsString().orEmpty()
            }.getOrElse {
                // 出错时不抛出到上层，避免影响 UI 与功能流
                ""
            }
        }
    }

    /** 读取 API Key，同步版本（兼容性） */
    fun getApiKeySync(): String {
        return try {
            val attrs = credentialAttributes()
            val creds = PasswordSafe.instance.get(attrs)
            creds?.getPasswordAsString().orEmpty()
        } catch (t: Throwable) {
            // 出错时不抛出到上层，避免影响 UI 与功能流
            ""
        }
    }

    /** 写入/更新 API Key（传入空字符串等价于清除） */
    fun setApiKey(value: String) {
        try {
            val attrs = credentialAttributes()
            if (value.isBlank()) {
                PasswordSafe.instance.set(attrs, null)
            } else {
                PasswordSafe.instance.set(attrs, Credentials(null, value))
            }
        } catch (_: Throwable) {
            // 静默处理：UI 层可通过提示引导用户重试
        }
    }

    /** 清除存储的 API Key */
    fun clearApiKey() {
        try {
            val attrs = credentialAttributes()
            PasswordSafe.instance.set(attrs, null)
        } catch (_: Throwable) {
            // 忽略异常，保证调用幂等
        }
    }
}
