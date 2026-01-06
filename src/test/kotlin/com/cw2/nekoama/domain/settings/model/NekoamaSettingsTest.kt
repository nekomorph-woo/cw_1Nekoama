package com.cw2.nekoama.domain.settings.model

import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Nekoama 设置测试
 *
 * 验证默认值和基本配置
 */
@DisplayName("Nekoama 设置测试")
class NekoamaSettingsTest {

    @BeforeEach
    fun setup() {
        // Mock ApplicationManager 和相关类
        mockkStatic("com.intellij.openapi.application.ApplicationManager")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== 默认值测试 ====================

    @Nested
    @DisplayName("默认值测试")
    inner class DefaultValueTests {

        @Test
        @DisplayName("默认值 - 命名功能应该启用")
        fun `默认值 - 命名功能应该启用`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.enableNaming).isTrue()
        }

        @Test
        @DisplayName("默认值 - 注释功能应该启用")
        fun `默认值 - 注释功能应该启用`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.enableComment).isTrue()
        }

        @Test
        @DisplayName("默认值 - 上下文深度应该为 2")
        fun `默认值 - 上下文深度应该为 2`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.contextDepth).isEqualTo(2)
        }

        @Test
        @DisplayName("默认值 - 缓存应该启用")
        fun `默认值 - 缓存应该启用`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.cacheEnabled).isTrue()
        }

        @Test
        @DisplayName("默认值 - AI 提供商应该是 Custom")
        fun `默认值 - AI 提供商应该是 Custom`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.aiProvider).isEqualTo("Custom")
        }

        @Test
        @DisplayName("默认值 - API 端点应该为空")
        fun `默认值 - API 端点应该为空`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.apiEndpoint).isEmpty()
        }

        @Test
        @DisplayName("默认值 - API Key 应该为空")
        fun `默认值 - API Key 应该为空`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.apiKey).isEmpty()
        }

        @Test
        @DisplayName("默认值 - 模型应该是 gpt-4o-mini")
        fun `默认值 - 模型应该是 gpt-4o-mini`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.model).isEqualTo("gpt-4o-mini")
        }

        @Test
        @DisplayName("默认值 - 温度应该是 70")
        fun `默认值 - 温度应该是 70`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.modelTemperature).isEqualTo(70)
        }

        @Test
        @DisplayName("默认值 - 超时时间应该是 60000ms")
        fun `默认值 - 超时时间应该是 60000ms`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.requestTimeoutMs).isEqualTo(60000)
        }

        @Test
        @DisplayName("默认值 - 语言偏好应该是 AUTO")
        fun `默认值 - 语言偏好应该是 AUTO`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.languagePreference).isEqualTo("AUTO")
        }

        @Test
        @DisplayName("默认值 - 命名风格应该是 CAMEL_CASE")
        fun `默认值 - 命名风格应该是 CAMEL_CASE`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.namingStyle).isEqualTo("CAMEL_CASE")
        }

        @Test
        @DisplayName("默认值 - 注释格式应该是 JAVADOC")
        fun `默认值 - 注释格式应该是 JAVADOC`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.commentFormat).isEqualTo("JAVADOC")
        }

        @Test
        @DisplayName("默认值 - 菜单显示风格应该是 NEKO_BRAND")
        fun `默认值 - 菜单显示风格应该是 NEKO_BRAND`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.menuDisplayNameStyle).isEqualTo(MenuDisplayNameStyle.NEKO_BRAND)
        }

        @Test
        @DisplayName("默认值 - 自定义命名菜单文本应该为 Neko Name")
        fun `默认值 - 自定义命名菜单文本应该为 Neko Name`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.customNamingMenuText).isEqualTo("Neko Name")
        }

        @Test
        @DisplayName("默认值 - 自定义注释菜单文本应该为 Neko Comment")
        fun `默认值 - 自定义注释菜单文本应该为 Neko Comment`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.customCommentMenuText).isEqualTo("Neko Comment")
        }

        @Test
        @DisplayName("默认值 - 自定义生成菜单文本应该为 Neko Magic")
        fun `默认值 - 自定义生成菜单文本应该为 Neko Magic`() {
            // 验证默认值
            val settings = NekoamaSettings()
            assertThat(settings.customGenerateMenuText).isEqualTo("Neko Magic")
        }
    }

    // ==================== 状态管理测试 ====================

    @Nested
    @DisplayName("状态管理测试")
    inner class StateManagementTests {

        @Test
        @DisplayName("getState - 应该返回自身实例")
        fun `getState - 应该返回自身实例`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            val state = settings.getState()

            // 验证结果
            assertThat(state).isSameAs(settings)
        }

        @Test
        @DisplayName("loadState - 应该复制状态")
        fun `loadState - 应该复制状态`() {
            // 准备测试数据
            val settings1 = NekoamaSettings()
            val settings2 = NekoamaSettings()

            // 修改 settings1
            settings1.enableNaming = false
            settings1.contextDepth = 3

            // 执行测试 - 加载状态
            settings2.loadState(settings1)

            // 验证结果 - 状态应该被复制
            assertThat(settings2.enableNaming).isFalse()
            assertThat(settings2.contextDepth).isEqualTo(3)
        }
    }

    // ==================== 配置修改测试 ====================

    @Nested
    @DisplayName("配置修改测试")
    inner class ConfigurationModificationTests {

        @Test
        @DisplayName("修改配置 - 应该能够禁用命名功能")
        fun `修改配置 - 应该能够禁用命名功能`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            settings.enableNaming = false

            // 验证结果
            assertThat(settings.enableNaming).isFalse()
        }

        @Test
        @DisplayName("修改配置 - 应该能够禁用注释功能")
        fun `修改配置 - 应该能够禁用注释功能`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            settings.enableComment = false

            // 验证结果
            assertThat(settings.enableComment).isFalse()
        }

        @Test
        @DisplayName("修改配置 - 应该能够修改上下文深度")
        fun `修改配置 - 应该能够修改上下文深度`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            settings.contextDepth = 3

            // 验证结果
            assertThat(settings.contextDepth).isEqualTo(3)
        }

        @Test
        @DisplayName("修改配置 - 应该能够修改模型温度")
        fun `修改配置 - 应该能够修改模型温度`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            settings.modelTemperature = 85

            // 验证结果
            assertThat(settings.modelTemperature).isEqualTo(85)
        }

        @Test
        @DisplayName("修改配置 - 应该能够修改语言偏好")
        fun `修改配置 - 应该能够修改语言偏好`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            settings.languagePreference = "ZH"

            // 验证结果
            assertThat(settings.languagePreference).isEqualTo("ZH")
        }

        @Test
        @DisplayName("修改配置 - 应该能够修改菜单显示风格")
        fun `修改配置 - 应该能够修改菜单显示风格`() {
            // 准备测试数据
            val settings = NekoamaSettings()

            // 执行测试
            settings.menuDisplayNameStyle = MenuDisplayNameStyle.AI_ASSISTANT

            // 验证结果
            assertThat(settings.menuDisplayNameStyle).isEqualTo(MenuDisplayNameStyle.AI_ASSISTANT)
        }
    }

    // ==================== 枚举测试 ====================

    @Nested
    @DisplayName("菜单显示风格枚举测试")
    inner class MenuDisplayNameStyleTests {

        @Test
        @DisplayName("枚举值 - 应该包含所有预定义风格")
        fun `枚举值 - 应该包含所有预定义风格`() {
            // 验证所有枚举值
            assertThat(MenuDisplayNameStyle.NEKO_BRAND).isNotNull()
            assertThat(MenuDisplayNameStyle.AI_ASSISTANT).isNotNull()
            assertThat(MenuDisplayNameStyle.ACTION_VERB).isNotNull()
            assertThat(MenuDisplayNameStyle.MINIMALIST).isNotNull()
            assertThat(MenuDisplayNameStyle.CUSTOM).isNotNull()
        }

        @Test
        @DisplayName("枚举值 - NEKO_BRAND 应该是猫主题品牌型")
        fun `枚举值 - NEKO_BRAND 应该是猫主题品牌型`() {
            // 验证枚举存在
            assertThat(MenuDisplayNameStyle.valueOf("NEKO_BRAND")).isEqualTo(MenuDisplayNameStyle.NEKO_BRAND)
        }

        @Test
        @DisplayName("枚举值 - AI_ASSISTANT 应该是 AI 助手型")
        fun `枚举值 - AI_ASSISTANT 应该是 AI 助手型`() {
            // 验证枚举存在
            assertThat(MenuDisplayNameStyle.valueOf("AI_ASSISTANT")).isEqualTo(MenuDisplayNameStyle.AI_ASSISTANT)
        }

        @Test
        @DisplayName("枚举值 - ACTION_VERB 应该是动词行动型")
        fun `枚举值 - ACTION_VERB 应该是动词行动型`() {
            // 验证枚举存在
            assertThat(MenuDisplayNameStyle.valueOf("ACTION_VERB")).isEqualTo(MenuDisplayNameStyle.ACTION_VERB)
        }

        @Test
        @DisplayName("枚举值 - MINIMALIST 应该是极简两词型")
        fun `枚举值 - MINIMALIST 应该是极简两词型`() {
            // 验证枚举存在
            assertThat(MenuDisplayNameStyle.valueOf("MINIMALIST")).isEqualTo(MenuDisplayNameStyle.MINIMALIST)
        }

        @Test
        @DisplayName("枚举值 - CUSTOM 应该是自定义文本")
        fun `枚举值 - CUSTOM 应该是自定义文本`() {
            // 验证枚举存在
            assertThat(MenuDisplayNameStyle.valueOf("CUSTOM")).isEqualTo(MenuDisplayNameStyle.CUSTOM)
        }
    }
}
