package com.cw2.nekoama.infrastructure.config

import com.cw2.nekoama.domain.settings.model.MenuDisplayNameStyle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 菜单文本提供器测试
 *
 * 验证 MenuTextProvider 的各种菜单文本生成功能
 */
@DisplayName("菜单文本提供器测试")
class MenuTextProviderTest {

    // ==================== 命名菜单文本测试 ====================

    @Nested
    @DisplayName("命名菜单文本测试")
    inner class NamingTextTests {

        @Test
        @DisplayName("getNamingText - NEKO_BRAND 应该返回 Neko Name")
        fun `getNamingText - NEKO_BRAND 应该返回 Neko Name`() {
            val text = MenuTextProvider.getNamingText(
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Name")
        }

        @Test
        @DisplayName("getNamingText - AI_ASSISTANT 应该返回 AI Name Suggester")
        fun `getNamingText - AI_ASSISTANT 应该返回 AI Name Suggester`() {
            val text = MenuTextProvider.getNamingText(
                style = MenuDisplayNameStyle.AI_ASSISTANT,
                customText = ""
            )

            assertThat(text).isEqualTo("AI Name Suggester")
        }

        @Test
        @DisplayName("getNamingText - ACTION_VERB 应该返回 Naming It")
        fun `getNamingText - ACTION_VERB 应该返回 Naming It`() {
            val text = MenuTextProvider.getNamingText(
                style = MenuDisplayNameStyle.ACTION_VERB,
                customText = ""
            )

            assertThat(text).isEqualTo("Naming It")
        }

        @Test
        @DisplayName("getNamingText - MINIMALIST 应该返回 Quick Name")
        fun `getNamingText - MINIMALIST 应该返回 Quick Name`() {
            val text = MenuTextProvider.getNamingText(
                style = MenuDisplayNameStyle.MINIMALIST,
                customText = ""
            )

            assertThat(text).isEqualTo("Quick Name")
        }

        @Test
        @DisplayName("getNamingText - CUSTOM 应该返回自定义文本")
        fun `getNamingText - CUSTOM 应该返回自定义文本`() {
            val text = MenuTextProvider.getNamingText(
                style = MenuDisplayNameStyle.CUSTOM,
                customText = "My Custom Name"
            )

            assertThat(text).isEqualTo("My Custom Name")
        }

        @Test
        @DisplayName("getNamingText - CUSTOM 空文本应该回退到默认")
        fun `getNamingText - CUSTOM 空文本应该回退到默认`() {
            val text = MenuTextProvider.getNamingText(
                style = MenuDisplayNameStyle.CUSTOM,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Name")
        }
    }

    // ==================== 注释菜单文本测试 ====================

    @Nested
    @DisplayName("注释菜单文本测试")
    inner class CommentTextTests {

        @Test
        @DisplayName("getCommentText - NEKO_BRAND 应该返回 Neko Comment")
        fun `getCommentText - NEKO_BRAND 应该返回 Neko Comment`() {
            val text = MenuTextProvider.getCommentText(
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Comment")
        }

        @Test
        @DisplayName("getCommentText - AI_ASSISTANT 应该返回 AI Doc Writer")
        fun `getCommentText - AI_ASSISTANT 应该返回 AI Doc Writer`() {
            val text = MenuTextProvider.getCommentText(
                style = MenuDisplayNameStyle.AI_ASSISTANT,
                customText = ""
            )

            assertThat(text).isEqualTo("AI Doc Writer")
        }

        @Test
        @DisplayName("getCommentText - ACTION_VERB 应该返回 Documenting It")
        fun `getCommentText - ACTION_VERB 应该返回 Documenting It`() {
            val text = MenuTextProvider.getCommentText(
                style = MenuDisplayNameStyle.ACTION_VERB,
                customText = ""
            )

            assertThat(text).isEqualTo("Documenting It")
        }

        @Test
        @DisplayName("getCommentText - MINIMALIST 应该返回 Quick Doc")
        fun `getCommentText - MINIMALIST 应该返回 Quick Doc`() {
            val text = MenuTextProvider.getCommentText(
                style = MenuDisplayNameStyle.MINIMALIST,
                customText = ""
            )

            assertThat(text).isEqualTo("Quick Doc")
        }

        @Test
        @DisplayName("getCommentText - CUSTOM 应该返回自定义文本")
        fun `getCommentText - CUSTOM 应该返回自定义文本`() {
            val text = MenuTextProvider.getCommentText(
                style = MenuDisplayNameStyle.CUSTOM,
                customText = "My Custom Comment"
            )

            assertThat(text).isEqualTo("My Custom Comment")
        }

        @Test
        @DisplayName("getCommentText - CUSTOM 空文本应该回退到默认")
        fun `getCommentText - CUSTOM 空文本应该回退到默认`() {
            val text = MenuTextProvider.getCommentText(
                style = MenuDisplayNameStyle.CUSTOM,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Comment")
        }
    }

    // ==================== 生成菜单文本测试 ====================

    @Nested
    @DisplayName("生成菜单文本测试")
    inner class GenerateTextTests {

        @Test
        @DisplayName("getGenerateText - NEKO_BRAND 应该返回 Neko Magic")
        fun `getGenerateText - NEKO_BRAND 应该返回 Neko Magic`() {
            val text = MenuTextProvider.getGenerateText(
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Magic")
        }

        @Test
        @DisplayName("getGenerateText - AI_ASSISTANT 应该返回 AI Code Assistant")
        fun `getGenerateText - AI_ASSISTANT 应该返回 AI Code Assistant`() {
            val text = MenuTextProvider.getGenerateText(
                style = MenuDisplayNameStyle.AI_ASSISTANT,
                customText = ""
            )

            assertThat(text).isEqualTo("AI Code Assistant")
        }

        @Test
        @DisplayName("getGenerateText - ACTION_VERB 应该返回 Reasoning It")
        fun `getGenerateText - ACTION_VERB 应该返回 Reasoning It`() {
            val text = MenuTextProvider.getGenerateText(
                style = MenuDisplayNameStyle.ACTION_VERB,
                customText = ""
            )

            assertThat(text).isEqualTo("Reasoning It")
        }

        @Test
        @DisplayName("getGenerateText - MINIMALIST 应该返回 Quick Gen")
        fun `getGenerateText - MINIMALIST 应该返回 Quick Gen`() {
            val text = MenuTextProvider.getGenerateText(
                style = MenuDisplayNameStyle.MINIMALIST,
                customText = ""
            )

            assertThat(text).isEqualTo("Quick Gen")
        }

        @Test
        @DisplayName("getGenerateText - CUSTOM 应该返回自定义文本")
        fun `getGenerateText - CUSTOM 应该返回自定义文本`() {
            val text = MenuTextProvider.getGenerateText(
                style = MenuDisplayNameStyle.CUSTOM,
                customText = "My Custom Generate"
            )

            assertThat(text).isEqualTo("My Custom Generate")
        }

        @Test
        @DisplayName("getGenerateText - CUSTOM 空文本应该回退到默认")
        fun `getGenerateText - CUSTOM 空文本应该回退到默认`() {
            val text = MenuTextProvider.getGenerateText(
                style = MenuDisplayNameStyle.CUSTOM,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Magic")
        }
    }

    // ==================== 通用菜单文本测试 ====================

    @Nested
    @DisplayName("通用菜单文本测试")
    inner class GenericMenuTextTests {

        @Test
        @DisplayName("getMenuText - naming key 应该返回命名文本")
        fun `getMenuText - naming key 应该返回命名文本`() {
            val text = MenuTextProvider.getMenuText(
                menuTextKey = "naming",
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Name")
        }

        @Test
        @DisplayName("getMenuText - comment key 应该返回注释文本")
        fun `getMenuText - comment key 应该返回注释文本`() {
            val text = MenuTextProvider.getMenuText(
                menuTextKey = "comment",
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Comment")
        }

        @Test
        @DisplayName("getMenuText - generate key 应该返回生成文本")
        fun `getMenuText - generate key 应该返回生成文本`() {
            val text = MenuTextProvider.getMenuText(
                menuTextKey = "generate",
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Magic")
        }

        @Test
        @DisplayName("getMenuText - 未知 key 应该返回安全回退文本")
        fun `getMenuText - 未知 key 应该返回安全回退文本`() {
            val text = MenuTextProvider.getMenuText(
                menuTextKey = "unknown",
                style = MenuDisplayNameStyle.NEKO_BRAND,
                customText = ""
            )

            assertThat(text).isEqualTo("Neko Action")
        }

        @Test
        @DisplayName("getMenuText - CUSTOM 风格应该使用自定义文本")
        fun `getMenuText - CUSTOM 风格应该使用自定义文本`() {
            val text = MenuTextProvider.getMenuText(
                menuTextKey = "naming",
                style = MenuDisplayNameStyle.CUSTOM,
                customText = "Custom Name Action"
            )

            assertThat(text).isEqualTo("Custom Name Action")
        }
    }

    // ==================== 所有风格组合测试 ====================

    @Nested
    @DisplayName("所有风格组合测试")
    inner class AllStyleCombinationsTests {

        @Test
        @DisplayName("所有风格 - 都应该返回非空文本")
        fun `所有风格 - 都应该返回非空文本`() {
            val styles = MenuDisplayNameStyle.entries

            for (style in styles) {
                val namingText = MenuTextProvider.getNamingText(style, "")
                val commentText = MenuTextProvider.getCommentText(style, "")
                val generateText = MenuTextProvider.getGenerateText(style, "")

                assertThat(namingText).isNotEmpty()
                assertThat(commentText).isNotEmpty()
                assertThat(generateText).isNotEmpty()
            }
        }

        @Test
        @DisplayName("所有风格 - 命名和注释文本应该不同")
        fun `所有风格 - 命名和注释文本应该不同`() {
            val styles = MenuDisplayNameStyle.entries

            for (style in styles) {
                if (style != MenuDisplayNameStyle.CUSTOM) {
                    val namingText = MenuTextProvider.getNamingText(style, "")
                    val commentText = MenuTextProvider.getCommentText(style, "")

                    assertThat(namingText).isNotEqualTo(commentText)
                }
            }
        }
    }
}
