package com.cw2.nekoama.infrastructure.config

import com.cw2.nekoama.domain.settings.model.MenuDisplayNameStyle

/**
 * 菜单文本提供器
 *
 * 职责：
 * - 根据配置的风格返回对应的菜单文本
 * - 封装预设风格的文本映射
 * - 提供自定义文本的回退机制
 */
object MenuTextProvider {

    /**
     * 预设风格的菜单文本映射
     * Triple 结构: (命名菜单文本, 注释菜单文本, 生成菜单文本)
     */
    private val styleTexts: Map<MenuDisplayNameStyle, Triple<String, String, String>> = mapOf(
        MenuDisplayNameStyle.NEKO_BRAND to Triple(
            "Neko Name",
            "Neko Comment",
            "Neko Magic"
        ),
        MenuDisplayNameStyle.AI_ASSISTANT to Triple(
            "AI Name Suggester",
            "AI Doc Writer",
            "AI Code Assistant"
        ),
        MenuDisplayNameStyle.ACTION_VERB to Triple(
            "Naming It",
            "Documenting It",
            "Reasoning It"
        ),
        MenuDisplayNameStyle.MINIMALIST to Triple(
            "Quick Name",
            "Quick Doc",
            "Quick Gen"
        )
    )

    /**
     * 获取命名菜单文本
     *
     * @param style 当前配置的风格
     * @param customText 自定义文本（仅当 style = CUSTOM 时使用）
     * @return 菜单显示文本
     */
    fun getNamingText(style: MenuDisplayNameStyle, customText: String): String {
        return if (style == MenuDisplayNameStyle.CUSTOM) {
            customText.ifEmpty { "Neko Name" }
        } else {
            styleTexts[style]?.first ?: "Neko Name"
        }
    }

    /**
     * 获取注释菜单文本
     *
     * @param style 当前配置的风格
     * @param customText 自定义文本（仅当 style = CUSTOM 时使用）
     * @return 菜单显示文本
     */
    fun getCommentText(style: MenuDisplayNameStyle, customText: String): String {
        return if (style == MenuDisplayNameStyle.CUSTOM) {
            customText.ifEmpty { "Neko Comment" }
        } else {
            styleTexts[style]?.second ?: "Neko Comment"
        }
    }

    /**
     * 获取生成菜单文本
     *
     * @param style 当前配置的风格
     * @param customText 自定义文本（仅当 style = CUSTOM 时使用）
     * @return 菜单显示文本
     */
    fun getGenerateText(style: MenuDisplayNameStyle, customText: String): String {
        return if (style == MenuDisplayNameStyle.CUSTOM) {
            customText.ifEmpty { "Neko Magic" }
        } else {
            styleTexts[style]?.third ?: "Neko Magic"
        }
    }

    /**
     * 通用菜单文本获取方法
     *
     * @param menuTextKey 菜单类型标识 ("naming", "comment", "generate")
     * @param style 当前配置的风格
     * @param customText 自定义文本
     * @return 菜单显示文本
     */
    fun getMenuText(menuTextKey: String, style: MenuDisplayNameStyle, customText: String): String {
        return when (menuTextKey) {
            "naming" -> getNamingText(style, customText)
            "comment" -> getCommentText(style, customText)
            "generate" -> getGenerateText(style, customText)
            else -> "Neko Action" // 安全回退
        }
    }
}
