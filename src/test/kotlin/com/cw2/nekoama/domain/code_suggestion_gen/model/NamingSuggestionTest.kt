package com.cw2.nekoama.domain.code_suggestion_gen.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 命名建议模型测试
 *
 * 验证命名建议数据类的功能，包括评分计算、适用性检查等
 */
@DisplayName("命名建议模型测试")
class NamingSuggestionTest {

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("基本属性测试")
    inner class BasicPropertiesTests {

        @Test
        @DisplayName("创建命名建议 - 应该包含所有必需属性")
        fun `创建命名建议 - 应该包含所有必需属性`() {
            // 准备测试数据
            val suggestion = NamingSuggestion(
                name = "calculateTotal",
                description = "计算订单总金额",
                score = 0.95,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD, CodeElementType.CLASS),
                confidence = 0.90
            )

            // 验证结果
            assertThat(suggestion.name).isEqualTo("calculateTotal")
            assertThat(suggestion.description).isEqualTo("计算订单总金额")
            assertThat(suggestion.score).isEqualTo(0.95)
            assertThat(suggestion.namingConvention).isEqualTo(NamingConvention.CAMEL_CASE)
            assertThat(suggestion.confidence).isEqualTo(0.90)
        }

        @Test
        @DisplayName("创建命名建议 - 应该使用默认元数据")
        fun `创建命名建议 - 应该使用默认元数据`() {
            // 执行测试
            val suggestion = NamingSuggestion(
                name = "testName",
                description = "测试描述",
                score = 0.8,
                namingConvention = NamingConvention.SNAKE_CASE,
                applicableFor = listOf(CodeElementType.VARIABLE),
                confidence = 0.85
            )

            // 验证结果
            assertThat(suggestion.metadata.source).isNull()
            assertThat(suggestion.metadata.model).isNull()
        }

        @Test
        @DisplayName("创建命名建议 - 应该自动设置生成时间戳")
        fun `创建命名建议 - 应该自动设置生成时间戳`() {
            // 执行测试
            val beforeTime = System.currentTimeMillis()
            val suggestion = NamingSuggestion(
                name = "processData",
                description = "处理数据",
                score = 0.88,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.82
            )
            val afterTime = System.currentTimeMillis()

            // 验证结果
            assertThat(suggestion.generatedAt).isBetween(beforeTime, afterTime)
        }

        @Test
        @DisplayName("创建命名建议 - 应该支持自定义元数据")
        fun `创建命名建议 - 应该支持自定义元数据`() {
            // 准备测试数据
            val customMetadata = SuggestionMetadata(
                source = "OpenAI",
                model = "gpt-4"
            )

            // 执行测试
            val suggestion = NamingSuggestion(
                name = "fetchUserData",
                description = "获取用户数据",
                score = 0.92,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.88,
                metadata = customMetadata
            )

            // 验证结果
            assertThat(suggestion.metadata.source).isEqualTo("OpenAI")
            assertThat(suggestion.metadata.model).isEqualTo("gpt-4")
        }
    }

    // ==================== 适用性检查测试 ====================

    @Nested
    @DisplayName("适用性检查测试")
    inner class ApplicabilityTests {

        @Test
        @DisplayName("检查适用性 - 适用的类型应该返回 true")
        fun `检查适用性 - 适用的类型应该返回 true`() {
            // 准备测试数据
            val suggestion = NamingSuggestion(
                name = "userName",
                description = "用户名",
                score = 0.9,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.VARIABLE, CodeElementType.FIELD),
                confidence = 0.85
            )

            // 执行测试
            val isApplicableForVariable = suggestion.isApplicableFor(CodeElementType.VARIABLE)
            val isApplicableForField = suggestion.isApplicableFor(CodeElementType.FIELD)

            // 验证结果
            assertThat(isApplicableForVariable).isTrue()
            assertThat(isApplicableForField).isTrue()
        }

        @Test
        @DisplayName("检查适用性 - 不适用的类型应该返回 false")
        fun `检查适用性 - 不适用的类型应该返回 false`() {
            // 准备测试数据
            val suggestion = NamingSuggestion(
                name = "userName",
                description = "用户名",
                score = 0.9,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.VARIABLE),
                confidence = 0.85
            )

            // 执行测试
            val isApplicableForMethod = suggestion.isApplicableFor(CodeElementType.METHOD)
            val isApplicableForClass = suggestion.isApplicableFor(CodeElementType.CLASS)

            // 验证结果
            assertThat(isApplicableForMethod).isFalse()
            assertThat(isApplicableForClass).isFalse()
        }

        @Test
        @DisplayName("检查适用性 - 空列表应该不适用于任何类型")
        fun `检查适用性 - 空列表应该不适用于任何类型`() {
            // 准备测试数据
            val suggestion = NamingSuggestion(
                name = "temp",
                description = "临时变量",
                score = 0.7,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = emptyList(),
                confidence = 0.6
            )

            // 执行测试
            val isApplicableForVariable = suggestion.isApplicableFor(CodeElementType.VARIABLE)

            // 验证结果
            assertThat(isApplicableForVariable).isFalse()
        }
    }

    // ==================== 质量评分测试 ====================

    @Nested
    @DisplayName("质量评分测试")
    inner class QualityScoreTests {

        @Test
        @DisplayName("计算质量得分 - 应该综合评分和置信度")
        fun `计算质量得分 - 应该综合评分和置信度`() {
            // 准备测试数据（评分 0.8，置信度 0.9）
            val suggestion = NamingSuggestion(
                name = "calculateSum",
                description = "计算总和",
                score = 0.8,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.9
            )

            // 执行测试
            val qualityScore = suggestion.getQualityScore()

            // 验证结果（0.8 * 0.7 + 0.9 * 0.3 = 0.56 + 0.27 = 0.83）
            assertThat(qualityScore).isEqualTo(0.83)
        }

        @Test
        @DisplayName("计算质量得分 - 高评分低置信度应该降低总分")
        fun `计算质量得分 - 高评分低置信度应该降低总分`() {
            // 准备测试数据（评分 0.95，置信度 0.5）
            val suggestion = NamingSuggestion(
                name = "processData",
                description = "处理数据",
                score = 0.95,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.5
            )

            // 执行测试
            val qualityScore = suggestion.getQualityScore()

            // 验证结果（0.95 * 0.7 + 0.5 * 0.3 = 0.665 + 0.15 = 0.815）
            assertThat(qualityScore).isEqualTo(0.815)
            assertThat(qualityScore).isLessThan(suggestion.score)
        }

        @Test
        @DisplayName("计算质量得分 - 低评分高置信度应该提高总分")
        fun `计算质量得分 - 低评分高置信度应该提高总分`() {
            // 准备测试数据（评分 0.7，置信度 0.95）
            val suggestion = NamingSuggestion(
                name = "helperFunction",
                description = "辅助函数",
                score = 0.7,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.95
            )

            // 执行测试
            val qualityScore = suggestion.getQualityScore()

            // 验证结果（0.7 * 0.7 + 0.95 * 0.3 = 0.49 + 0.285 = 0.775）
            assertThat(qualityScore).isEqualTo(0.775)
            assertThat(qualityScore).isGreaterThan(suggestion.score)
        }

        @Test
        @DisplayName("计算质量得分 - 应该限制在零到一之间")
        fun `计算质量得分 - 应该限制在零到一之间`() {
            // 准备测试数据（边界情况）
            val suggestion1 = NamingSuggestion(
                name = "perfect",
                description = "完美命名",
                score = 1.0,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 1.0
            )

            val suggestion2 = NamingSuggestion(
                name = "bad",
                description = "糟糕命名",
                score = 0.0,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.0
            )

            // 执行测试
            val qualityScore1 = suggestion1.getQualityScore()
            val qualityScore2 = suggestion2.getQualityScore()

            // 验证结果
            assertThat(qualityScore1).isBetween(0.0, 1.0)
            assertThat(qualityScore2).isBetween(0.0, 1.0)
        }
    }

    // ==================== 命名约定测试 ====================

    @Nested
    @DisplayName("命名约定测试")
    inner class NamingConventionTests {

        @Test
        @DisplayName("驼峰命名 - 应该正确识别")
        fun `驼峰命名 - 应该正确识别`() {
            // 执行测试
            val suggestion = NamingSuggestion(
                name = "calculateTotalAmount",
                description = "计算总金额",
                score = 0.9,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.85
            )

            // 验证结果
            assertThat(suggestion.namingConvention).isEqualTo(NamingConvention.CAMEL_CASE)
        }

        @Test
        @DisplayName("帕斯卡命名 - 应该正确识别")
        fun `帕斯卡命名 - 应该正确识别`() {
            // 执行测试
            val suggestion = NamingSuggestion(
                name = "UserService",
                description = "用户服务",
                score = 0.92,
                namingConvention = NamingConvention.PASCAL_CASE,
                applicableFor = listOf(CodeElementType.CLASS),
                confidence = 0.88
            )

            // 验证结果
            assertThat(suggestion.namingConvention).isEqualTo(NamingConvention.PASCAL_CASE)
        }

        @Test
        @DisplayName("蛇形命名 - 应该正确识别")
        fun `蛇形命名 - 应该正确识别`() {
            // 执行测试
            val suggestion = NamingSuggestion(
                name = "user_name",
                description = "用户名",
                score = 0.85,
                namingConvention = NamingConvention.SNAKE_CASE,
                applicableFor = listOf(CodeElementType.VARIABLE),
                confidence = 0.80
            )

            // 验证结果
            assertThat(suggestion.namingConvention).isEqualTo(NamingConvention.SNAKE_CASE)
        }

        @Test
        @DisplayName("短横线命名 - 应该正确识别")
        fun `短横线命名 - 应该正确识别`() {
            // 执行测试
            val suggestion = NamingSuggestion(
                name = "user-name",
                description = "用户名",
                score = 0.80,
                namingConvention = NamingConvention.KEBAB_CASE,
                applicableFor = listOf(CodeElementType.PACKAGE),
                confidence = 0.75
            )

            // 验证结果
            assertThat(suggestion.namingConvention).isEqualTo(NamingConvention.KEBAB_CASE)
        }

        @Test
        @DisplayName("大写蛇形命名 - 应该正确识别")
        fun `大写蛇形命名 - 应该正确识别`() {
            // 执行测试
            val suggestion = NamingSuggestion(
                name = "MAX_CONNECTION_COUNT",
                description = "最大连接数",
                score = 0.90,
                namingConvention = NamingConvention.UPPER_SNAKE_CASE,
                applicableFor = listOf(CodeElementType.FIELD),
                confidence = 0.88
            )

            // 验证结果
            assertThat(suggestion.namingConvention).isEqualTo(NamingConvention.UPPER_SNAKE_CASE)
        }
    }

    // ==================== 序列化测试 ====================

    @Nested
    @DisplayName("序列化测试")
    inner class SerializationTests {

        @Test
        @DisplayName("数据类 - 应该支持序列化")
        fun `数据类 - 应该支持序列化`() {
            // 准备测试数据
            val suggestion = NamingSuggestion(
                name = "testName",
                description = "测试描述",
                score = 0.85,
                namingConvention = NamingConvention.CAMEL_CASE,
                applicableFor = listOf(CodeElementType.METHOD),
                confidence = 0.82
            )

            // 执行测试（通过 toString 验证可以正常工作）
            val stringRepresentation = suggestion.toString()

            // 验证结果
            assertThat(stringRepresentation).contains("testName")
            assertThat(stringRepresentation).contains("测试描述")
        }
    }
}
