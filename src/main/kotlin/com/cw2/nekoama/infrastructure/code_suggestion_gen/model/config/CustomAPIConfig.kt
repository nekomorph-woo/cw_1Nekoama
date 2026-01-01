package com.cw2.nekoama.infrastructure.code_suggestion_gen.model.config

import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.model.Result
import kotlinx.serialization.Serializable

/**
 * 自定义 API 配置实现
 */
@Serializable
data class CustomAPIConfig(
    /**
     * 提供商显示名称
     */
    val providerName: String,

    override val apiUrl: String,
    override val apiKey: String,
    override val model: String,
    override val maxTokens: Int = 150,
    override val temperature: Double = 0.7,
    override val timeoutMs: Long = 30000,
    override val maxRetries: Int = 3,

    /**
     * 自定义请求头
     */
    val customHeaders: Map<String, String> = emptyMap(),

    /**
     * 认证方式类型
     */
    val authType: AuthenticationType = AuthenticationType.BEARER_TOKEN,

    /**
     * API 版本（用于 Azure OpenAI 等服务）
     */
    val apiVersion: String? = null,

    /**
     * 部署名称（用于 Azure OpenAI）
     */
    val deploymentName: String? = null,

    /**
     * 组织 ID（用于 OpenAI 组织账户）
     */
    val organizationId: String? = null,

    /**
     * 请求路径模板（用于非标准端点）
     */
    val pathTemplate: String = "/chat/completions",

    /**
     * 是否验证 SSL 证书
     */
    val verifySSL: Boolean = true

) : AIProviderConfig {

    override fun validate(): Result<Unit> {
        return when {
            providerName.isBlank() -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("提供商名称不能为空")
            )
            apiUrl.isBlank() -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("API URL 不能为空")
            )
            !apiUrl.startsWith("http") -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("无效的API URL")
            )
            apiKey.isBlank() -> Result.error(
                NekoamaError.AuthenticationError.ApiKeyNotConfigured()
            )
            model.isBlank() -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("模型名称不能为空")
            )
            maxTokens <= 0 -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("maxTokens必须大于0")
            )
            temperature !in 0.0..2.0 -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("temperature必须在0.0-2.0之间")
            )
            timeoutMs <= 0 -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("超时时间必须大于0")
            )
            maxRetries < 0 -> Result.error(
                NekoamaError.ParseError.InvalidConfiguration("重试次数不能为负数")
            )
            else -> Result.success(Unit)
        }
    }

    /**
     * 构建完整的 API 端点 URL
     */
    fun buildEndpointUrl(): String {
        return buildString {
            append(apiUrl.trimEnd('/'))

            // 处理 Azure OpenAI 的特殊路径格式
            if (deploymentName != null && apiVersion != null) {
                append("/openai/deployments/$deploymentName/chat/completions")
                append("?api-version=$apiVersion")
            } else {
                append(pathTemplate)
            }
        }
    }

    /**
     * 获取认证头部信息
     */
    fun getAuthHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        when (authType) {
            AuthenticationType.BEARER_TOKEN -> {
                headers["Authorization"] = "Bearer $apiKey"
            }
            AuthenticationType.API_KEY_HEADER -> {
                headers["api-key"] = apiKey
            }
            AuthenticationType.X_API_KEY -> {
                headers["X-API-Key"] = apiKey
            }
            AuthenticationType.CUSTOM -> {
                // 自定义认证方式通过 customHeaders 传递
            }
        }

        // 添加组织 ID（如果有）
        organizationId?.let { orgId ->
            headers["OpenAI-Organization"] = orgId
        }

        // 合并自定义头部
        headers.putAll(customHeaders)

        return headers
    }
}
