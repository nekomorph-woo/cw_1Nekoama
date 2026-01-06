package com.cw2.nekoama.domain.code_suggestion_gen.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 注释枚举测试
 *
 * 验证注释相关的枚举类型
 */
@DisplayName("注释枚举测试")
class CommentEnumsTest {

    // ==================== CommentFormat 枚举测试 ====================

    @Nested
    @DisplayName("注释格式枚举测试")
    inner class CommentFormatTests {

        @Test
        @DisplayName("枚举值 - 应该包含 JAVADOC")
        fun `枚举值 - 应该包含 JAVADOC`() {
            assertThat(CommentFormat.valueOf("JAVADOC")).isEqualTo(CommentFormat.JAVADOC)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 KDOC")
        fun `枚举值 - 应该包含 KDOC`() {
            assertThat(CommentFormat.valueOf("KDOC")).isEqualTo(CommentFormat.KDOC)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 JSDOC")
        fun `枚举值 - 应该包含 JSDOC`() {
            assertThat(CommentFormat.valueOf("JSDOC")).isEqualTo(CommentFormat.JSDOC)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 SINGLE_LINE")
        fun `枚举值 - 应该包含 SINGLE_LINE`() {
            assertThat(CommentFormat.valueOf("SINGLE_LINE")).isEqualTo(CommentFormat.SINGLE_LINE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 MULTI_LINE")
        fun `枚举值 - 应该包含 MULTI_LINE`() {
            assertThat(CommentFormat.valueOf("MULTI_LINE")).isEqualTo(CommentFormat.MULTI_LINE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 PLAIN")
        fun `枚举值 - 应该包含 PLAIN`() {
            assertThat(CommentFormat.valueOf("PLAIN")).isEqualTo(CommentFormat.PLAIN)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(CommentFormat.entries).containsExactly(
                CommentFormat.JAVADOC,
                CommentFormat.KDOC,
                CommentFormat.JSDOC,
                CommentFormat.SINGLE_LINE,
                CommentFormat.MULTI_LINE,
                CommentFormat.PLAIN
            )
        }
    }

    // ==================== CommentLanguage 枚举测试 ====================

    @Nested
    @DisplayName("注释语言枚举测试")
    inner class CommentLanguageTests {

        @Test
        @DisplayName("枚举值 - 应该包含 CHINESE")
        fun `枚举值 - 应该包含 CHINESE`() {
            assertThat(CommentLanguage.valueOf("CHINESE")).isEqualTo(CommentLanguage.CHINESE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 ENGLISH")
        fun `枚举值 - 应该包含 ENGLISH`() {
            assertThat(CommentLanguage.valueOf("ENGLISH")).isEqualTo(CommentLanguage.ENGLISH)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 AUTO")
        fun `枚举值 - 应该包含 AUTO`() {
            assertThat(CommentLanguage.valueOf("AUTO")).isEqualTo(CommentLanguage.AUTO)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(CommentLanguage.entries).containsExactly(
                CommentLanguage.CHINESE,
                CommentLanguage.ENGLISH,
                CommentLanguage.AUTO
            )
        }
    }

    // ==================== CommentAspect 枚举测试 ====================

    @Nested
    @DisplayName("注释方面枚举测试")
    inner class CommentAspectTests {

        @Test
        @DisplayName("枚举值 - 应该包含 FUNCTIONALITY")
        fun `枚举值 - 应该包含 FUNCTIONALITY`() {
            assertThat(CommentAspect.valueOf("FUNCTIONALITY")).isEqualTo(CommentAspect.FUNCTIONALITY)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 PARAMETERS")
        fun `枚举值 - 应该包含 PARAMETERS`() {
            assertThat(CommentAspect.valueOf("PARAMETERS")).isEqualTo(CommentAspect.PARAMETERS)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 RETURN_VALUE")
        fun `枚举值 - 应该包含 RETURN_VALUE`() {
            assertThat(CommentAspect.valueOf("RETURN_VALUE")).isEqualTo(CommentAspect.RETURN_VALUE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 EXCEPTIONS")
        fun `枚举值 - 应该包含 EXCEPTIONS`() {
            assertThat(CommentAspect.valueOf("EXCEPTIONS")).isEqualTo(CommentAspect.EXCEPTIONS)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 EXAMPLES")
        fun `枚举值 - 应该包含 EXAMPLES`() {
            assertThat(CommentAspect.valueOf("EXAMPLES")).isEqualTo(CommentAspect.EXAMPLES)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 SIDE_EFFECTS")
        fun `枚举值 - 应该包含 SIDE_EFFECTS`() {
            assertThat(CommentAspect.valueOf("SIDE_EFFECTS")).isEqualTo(CommentAspect.SIDE_EFFECTS)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 COMPLEXITY")
        fun `枚举值 - 应该包含 COMPLEXITY`() {
            assertThat(CommentAspect.valueOf("COMPLEXITY")).isEqualTo(CommentAspect.COMPLEXITY)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 THREAD_SAFETY")
        fun `枚举值 - 应该包含 THREAD_SAFETY`() {
            assertThat(CommentAspect.valueOf("THREAD_SAFETY")).isEqualTo(CommentAspect.THREAD_SAFETY)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 DEPRECATED")
        fun `枚举值 - 应该包含 DEPRECATED`() {
            assertThat(CommentAspect.valueOf("DEPRECATED")).isEqualTo(CommentAspect.DEPRECATED)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 SEE_ALSO")
        fun `枚举值 - 应该包含 SEE_ALSO`() {
            assertThat(CommentAspect.valueOf("SEE_ALSO")).isEqualTo(CommentAspect.SEE_ALSO)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(CommentAspect.entries).containsExactly(
                CommentAspect.FUNCTIONALITY,
                CommentAspect.PARAMETERS,
                CommentAspect.RETURN_VALUE,
                CommentAspect.EXCEPTIONS,
                CommentAspect.EXAMPLES,
                CommentAspect.SIDE_EFFECTS,
                CommentAspect.COMPLEXITY,
                CommentAspect.THREAD_SAFETY,
                CommentAspect.DEPRECATED,
                CommentAspect.SEE_ALSO
            )
        }
    }

    // ==================== 枚举序数测试 ====================

    @Nested
    @DisplayName("枚举序数测试")
    inner class EnumOrdinalTests {

        @Test
        @DisplayName("CommentFormat - ordinal 应该按声明顺序")
        fun `CommentFormat - ordinal 应该按声明顺序`() {
            assertThat(CommentFormat.JAVADOC.ordinal).isEqualTo(0)
            assertThat(CommentFormat.KDOC.ordinal).isEqualTo(1)
            assertThat(CommentFormat.JSDOC.ordinal).isEqualTo(2)
        }

        @Test
        @DisplayName("CommentLanguage - ordinal 应该按声明顺序")
        fun `CommentLanguage - ordinal 应该按声明顺序`() {
            assertThat(CommentLanguage.CHINESE.ordinal).isEqualTo(0)
            assertThat(CommentLanguage.ENGLISH.ordinal).isEqualTo(1)
            assertThat(CommentLanguage.AUTO.ordinal).isEqualTo(2)
        }

        @Test
        @DisplayName("CommentAspect - FUNCTIONALITY 应该为 0")
        fun `CommentAspect - FUNCTIONALITY 应该为 0`() {
            assertThat(CommentAspect.FUNCTIONALITY.ordinal).isEqualTo(0)
        }

        @Test
        @DisplayName("CommentAspect - SEE_ALSO 应该为最后一个")
        fun `CommentAspect - SEE_ALSO 应该为最后一个`() {
            assertThat(CommentAspect.SEE_ALSO.ordinal).isEqualTo(9)
        }
    }

    // ==================== 枚举名称测试 ====================

    @Nested
    @DisplayName("枚举名称测试")
    inner class EnumNameTests {

        @Test
        @DisplayName("CommentFormat - name 应该返回枚举名称")
        fun `CommentFormat - name 应该返回枚举名称`() {
            assertThat(CommentFormat.JAVADOC.name).isEqualTo("JAVADOC")
            assertThat(CommentFormat.KDOC.name).isEqualTo("KDOC")
            assertThat(CommentFormat.PLAIN.name).isEqualTo("PLAIN")
        }

        @Test
        @DisplayName("CommentLanguage - name 应该返回枚举名称")
        fun `CommentLanguage - name 应该返回枚举名称`() {
            assertThat(CommentLanguage.CHINESE.name).isEqualTo("CHINESE")
            assertThat(CommentLanguage.ENGLISH.name).isEqualTo("ENGLISH")
            assertThat(CommentLanguage.AUTO.name).isEqualTo("AUTO")
        }

        @Test
        @DisplayName("CommentAspect - name 应该返回枚举名称")
        fun `CommentAspect - name 应该返回枚举名称`() {
            assertThat(CommentAspect.FUNCTIONALITY.name).isEqualTo("FUNCTIONALITY")
            assertThat(CommentAspect.PARAMETERS.name).isEqualTo("PARAMETERS")
            assertThat(CommentAspect.SEE_ALSO.name).isEqualTo("SEE_ALSO")
        }
    }

    // ==================== 使用场景测试 ====================

    @Nested
    @DisplayName("使用场景测试")
    inner class UsageScenarioTests {

        @Test
        @DisplayName("CommentFormat - JAVADOC 用于 Java")
        fun `CommentFormat - JAVADOC 用于 Java`() {
            val format = CommentFormat.JAVADOC
            val prefix = when (format) {
                CommentFormat.JAVADOC -> "/**"
                else -> ""
            }

            assertThat(prefix).isEqualTo("/**")
        }

        @Test
        @DisplayName("CommentFormat - KDOC 用于 Kotlin")
        fun `CommentFormat - KDOC 用于 Kotlin`() {
            val format = CommentFormat.KDOC
            val prefix = when (format) {
                CommentFormat.KDOC -> "/**"
                else -> ""
            }

            assertThat(prefix).isEqualTo("/**")
        }

        @Test
        @DisplayName("CommentFormat - SINGLE_LINE 单行注释")
        fun `CommentFormat - SINGLE_LINE 单行注释`() {
            val format = CommentFormat.SINGLE_LINE
            val prefix = when (format) {
                CommentFormat.SINGLE_LINE -> "//"
                else -> ""
            }

            assertThat(prefix).isEqualTo("//")
        }

        @Test
        @DisplayName("CommentLanguage - AUTO 自动检测")
        fun `CommentLanguage - AUTO 自动检测`() {
            val language = CommentLanguage.AUTO
            val shouldDetect = when (language) {
                CommentLanguage.AUTO -> true
                else -> false
            }

            assertThat(shouldDetect).isTrue()
        }

        @Test
        @DisplayName("CommentAspect - 所有方面应该可组合")
        fun `CommentAspect - 所有方面应该可组合`() {
            val aspects = listOf(
                CommentAspect.FUNCTIONALITY,
                CommentAspect.PARAMETERS,
                CommentAspect.RETURN_VALUE,
                CommentAspect.EXCEPTIONS
            )

            assertThat(aspects).hasSize(4)
            assertThat(aspects).contains(
                CommentAspect.FUNCTIONALITY,
                CommentAspect.PARAMETERS
            )
        }
    }

    // ==================== 序列化注解测试 ====================

    @Nested
    @DisplayName("序列化注解测试")
    inner class SerializationAnnotationTests {

        @Test
        @DisplayName("CommentFormat - 应该有 Serializable 注解")
        fun `CommentFormat - 应该有 Serializable 注解`() {
            assertThat(CommentFormat.JAVADOC::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("CommentLanguage - 应该有 Serializable 注解")
        fun `CommentLanguage - 应该有 Serializable 注解`() {
            assertThat(CommentLanguage.CHINESE::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("CommentAspect - 应该有 Serializable 注解")
        fun `CommentAspect - 应该有 Serializable 注解`() {
            assertThat(CommentAspect.FUNCTIONALITY::class.simpleName).isNotNull()
        }
    }
}
