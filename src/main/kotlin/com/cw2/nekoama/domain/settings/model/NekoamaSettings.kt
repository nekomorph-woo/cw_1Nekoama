package com.cw2.nekoama.domain.settings.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 菜单显示风格枚举
 */
enum class MenuDisplayNameStyle {
    /** 猫主题品牌型 - Neko Name / Neko Comment / Neko Magic */
    NEKO_BRAND,

    /** AI 助手型 - AI Name Suggester / AI Doc Writer / AI Code Assistant */
    AI_ASSISTANT,

    /** 动词行动型 - Rename Smart / Document It / Generate Context */
    ACTION_VERB,

    /** 极简两词型 - Quick Name / Quick Doc / Quick Gen */
    MINIMALIST,

    /** 自定义文本 */
    CUSTOM
}

/**
 * 插件设置持久化组件
 *
 * 设计原则：
 * - 默认值安全：即使未初始化也有合理默认值
 * - 敏感信息后续使用安全存储（本次不涉及 API Key）
 */
@Service(Service.Level.APP)
@State(name = "NekoamaSettings", storages = [Storage("nym_settings.xml")])
class NekoamaSettings : PersistentStateComponent<NekoamaSettings> {

    // 功能开关（默认启用）
    var enableNaming: Boolean = true
    var enableComment: Boolean = true

    // 分析深度（简化为 1-3）
    var contextDepth: Int = 2

    // 缓存开关
    var cacheEnabled: Boolean = true

    // ===== AI 服务配置 =====
    var aiProvider: String = "Custom"
    var apiEndpoint: String = ""
    var apiKey: String = ""
    // 自定义/兼容 OpenAI 的模型名称（Custom 模式下可编辑），例如：gpt-4o-mini、gpt-4、qwen2.5
    var model: String = "gpt-4o-mini"
    // 模型温度（0-100，对应 0.0-1.0），便于 UI slider 绑定
    var modelTemperature: Int = 70

    // ===== 高级性能配置 =====
    var requestTimeoutMs: Int = 60000 // 单请求超时，默认 60s（AI服务响应可能较慢）

    // ===== 偏好设置 =====
    var languagePreference: String = "AUTO"
    var namingStyle: String = "CAMEL_CASE"
    var commentFormat: String = "JAVADOC"

    // ===== 菜单外观配置 =====
    /** 菜单显示风格（默认：猫主题品牌型） */
    var menuDisplayNameStyle: MenuDisplayNameStyle = MenuDisplayNameStyle.NEKO_BRAND

    /** 自定义命名菜单文本（当 style = CUSTOM 时使用） */
    var customNamingMenuText: String = "Neko Name"

    /** 自定义注释菜单文本（当 style = CUSTOM 时使用） */
    var customCommentMenuText: String = "Neko Comment"

    /** 自定义生成菜单文本（当 style = CUSTOM 时使用） */
    var customGenerateMenuText: String = "Neko Magic"

    override fun getState(): NekoamaSettings = this

    override fun loadState(state: NekoamaSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /**
         * 获取单例实例
         */
        @JvmStatic
        fun getInstance(): NekoamaSettings = ApplicationManager
            .getApplication()
            .getService(NekoamaSettings::class.java)
    }
}