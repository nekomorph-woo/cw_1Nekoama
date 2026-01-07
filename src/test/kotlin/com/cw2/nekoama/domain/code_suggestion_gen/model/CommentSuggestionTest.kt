package com.cw2.nekoama.domain.code_suggestion_gen.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 注释建议模型测试
 *
 * 验证注释建议数据类的功能，包括格式化、多格式支持等
 */
@DisplayName("注释建议模型测试")
class CommentSuggestionTest {

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("基本属性测试")
    inner class BasicPropertiesTests {

        @Test
        @DisplayName("创建注释建议 - 应该包含所有必需属性")
        fun `创建注释建议 - 应该包含所有必需属性`() {
            // 准备测试数据
            val suggestion = CommentSuggestion(
                content = "计算订单总金额",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE
            )

            // 验证结果
            assertThat(suggestion.content).isEqualTo("计算订单总金额")
            assertThat(suggestion.format).isEqualTo(CommentFormat.KDOC)
            assertThat(suggestion.language).isEqualTo(CommentLanguage.CHINESE)
        }

        @Test
        @DisplayName("创建注释建议 - 应该使用默认元数据")
        fun `创建注释建议 - 应该使用默认元数据`() {
            // 执行测试
            val suggestion = CommentSuggestion(
                content = "处理用户输入",
                format = CommentFormat.JAVADOC,
                language = CommentLanguage.CHINESE
            )

            // 验证结果
            assertThat(suggestion.metadata.source).isNull()
            assertThat(suggestion.metadata.model).isNull()
        }

        @Test
        @DisplayName("创建注释建议 - 应该自动设置生成时间戳")
        fun `创建注释建议 - 应该自动设置生成时间戳`() {
            // 执行测试
            val beforeTime = System.currentTimeMillis()
            val suggestion = CommentSuggestion(
                content = "验证数据有效性",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE
            )
            val afterTime = System.currentTimeMillis()

            // 验证结果
            assertThat(suggestion.generatedAt).isBetween(beforeTime, afterTime)
        }

        @Test
        @DisplayName("创建注释建议 - 应该支持自定义结构化信息")
        fun `创建注释建议 - 应该支持自定义结构化信息`() {
            // 准备测试数据
            val structure = CommentStructure(
                parameters = listOf(
                    ParameterComment(
                        name = "userId",
                        description = "用户ID",
                        type = "String",
                        isOptional = false
                    ),
                    ParameterComment(
                        name = "includeInactive",
                        description = "是否包含非活跃用户",
                        type = "Boolean",
                        isOptional = true,
                        defaultValue = "false"
                    )
                ),
                returnDescription = "用户信息列表",
                exceptions = listOf(
                    ExceptionComment(
                        type = "UserNotFoundException",
                        description = "用户不存在时抛出"
                    )
                )
            )

            // 执行测试
            val suggestion = CommentSuggestion(
                content = "根据ID获取用户信息",
                format = CommentFormat.JAVADOC,
                language = CommentLanguage.CHINESE,
                structure = structure
            )

            // 验证结果
            assertThat(suggestion.structure).isNotNull()
            assertThat(suggestion.structure!!.parameters).hasSize(2)
            assertThat(suggestion.structure!!.returnDescription).isEqualTo("用户信息列表")
            assertThat(suggestion.structure!!.exceptions).hasSize(1)
        }
    }

    // ==================== 格式化测试 ====================

    @Nested
    @DisplayName("注释格式化测试")
    inner class FormattingTests {

        @Test
        @DisplayName("格式化注释 - KDOC 无结构应该返回简单格式")
        fun `格式化注释 - KDOC 无结构应该返回简单格式`() {
            // 准备测试数据
            val suggestion = CommentSuggestion(
                content = "计算订单总金额",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).contains("/**")
            assertThat(formatted).contains(" * 计算订单总金额")
            assertThat(formatted).contains(" */")
        }

        @Test
        @DisplayName("格式化注释 - KDOC 有结构应该返回完整格式")
        fun `格式化注释 - KDOC 有结构应该返回完整格式`() {
            // 准备测试数据
            val structure = CommentStructure(
                parameters = listOf(
                    ParameterComment(
                        name = "orderId",
                        description = "订单ID",
                        type = "String"
                    )
                ),
                returnDescription = "订单总金额"
            )

            val suggestion = CommentSuggestion(
                content = "计算订单总金额",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE,
                structure = structure
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).contains("/**")
            assertThat(formatted).contains(" * 计算订单总金额")
            assertThat(formatted).contains(" * @param orderId 订单ID")
            assertThat(formatted).contains(" * @return 订单总金额")
            assertThat(formatted).contains(" */")
        }

        @Test
        @DisplayName("格式化注释 - JAVADOC 应该使用正确格式")
        fun `格式化注释 - JAVADOC 应该使用正确格式`() {
            // 准备测试数据
            val suggestion = CommentSuggestion(
                content = "验证用户权限",
                format = CommentFormat.JAVADOC,
                language = CommentLanguage.CHINESE
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).isEqualTo("/**\n * 验证用户权限\n */")
        }

        @Test
        @DisplayName("格式化注释 - JSDOC 应该使用类型标注")
        fun `格式化注释 - JSDOC 应该使用类型标注`() {
            // 准备测试数据
            val structure = CommentStructure(
                parameters = listOf(
                    ParameterComment(
                        name = "callback",
                        description = "回调函数",
                        type = "Function"
                    )
                ),
                returnDescription = "void"
            )

            val suggestion = CommentSuggestion(
                content = "执行异步操作",
                format = CommentFormat.JSDOC,
                language = CommentLanguage.ENGLISH,
                structure = structure
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果（JSDoc 使用 @returns 而不是 @return）
            assertThat(formatted).contains("/**")
            assertThat(formatted).contains(" * @param {Function} callback 回调函数")
            assertThat(formatted).contains(" * @returns void")
            assertThat(formatted).contains(" */")
        }

        @Test
        @DisplayName("格式化注释 - SINGLE_LINE 应该使用双斜杠前缀")
        fun `格式化注释 - SINGLE_LINE 应该使用双斜杠前缀`() {
            // 准备测试数据
            val suggestion = CommentSuggestion(
                content = "临时变量，用于存储中间结果",
                format = CommentFormat.SINGLE_LINE,
                language = CommentLanguage.CHINESE
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).isEqualTo("// 临时变量，用于存储中间结果")
        }

        @Test
        @DisplayName("格式化注释 - MULTI_LINE 应该使用星号包裹")
        fun `格式化注释 - MULTI_LINE 应该使用星号包裹`() {
            // 准备测试数据
            val suggestion = CommentSuggestion(
                content = "复杂的多行注释说明",
                format = CommentFormat.MULTI_LINE,
                language = CommentLanguage.CHINESE
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).isEqualTo("/* 复杂的多行注释说明 */")
        }

        @Test
        @DisplayName("格式化注释 - PLAIN 应该返回原始内容")
        fun `格式化注释 - PLAIN 应该返回原始内容`() {
            // 准备测试数据
            val suggestion = CommentSuggestion(
                content = "这是纯文本注释，不添加任何格式",
                format = CommentFormat.PLAIN,
                language = CommentLanguage.CHINESE
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).isEqualTo("这是纯文本注释，不添加任何格式")
        }

        @Test
        @DisplayName("格式化注释 - KDOC 多个参数应该全部包含")
        fun `格式化注释 - KDOC 多个参数应该全部包含`() {
            // 准备测试数据
            val structure = CommentStructure(
                parameters = listOf(
                    ParameterComment(name = "param1", description = "第一个参数"),
                    ParameterComment(name = "param2", description = "第二个参数"),
                    ParameterComment(name = "param3", description = "第三个参数")
                )
            )

            val suggestion = CommentSuggestion(
                content = "多参数函数示例",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE,
                structure = structure
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).contains("@param param1 第一个参数")
            assertThat(formatted).contains("@param param2 第二个参数")
            assertThat(formatted).contains("@param param3 第三个参数")
        }

        @Test
        @DisplayName("格式化注释 - KDOC 异常列表应该包含")
        fun `格式化注释 - KDOC 异常列表应该包含`() {
            // 准备测试数据
            val structure = CommentStructure(
                exceptions = listOf(
                    ExceptionComment(type = "IllegalArgumentException", description = "参数非法时抛出"),
                    ExceptionComment(type = "IOException", description = "IO异常时抛出")
                )
            )

            val suggestion = CommentSuggestion(
                content = "可能抛出多个异常的函数",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE,
                structure = structure
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).contains("@throws IllegalArgumentException 参数非法时抛出")
            assertThat(formatted).contains("@throws IOException IO异常时抛出")
        }

        @Test
        @DisplayName("格式化注释 - JSDOC 异常应该使用类型标注")
        fun `格式化注释 - JSDOC 异常应该使用类型标注`() {
            // 准备测试数据
            val structure = CommentStructure(
                exceptions = listOf(
                    ExceptionComment(type = "Error", description = "发生错误")
                )
            )

            val suggestion = CommentSuggestion(
                content = "可能抛出错误的函数",
                format = CommentFormat.JSDOC,
                language = CommentLanguage.ENGLISH,
                structure = structure
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).contains("@throws {Error} 发生错误")
        }

        @Test
        @DisplayName("格式化注释 - 完整结构应该包含所有部分")
        fun `格式化注释 - 完整结构应该包含所有部分`() {
            // 准备测试数据
            val structure = CommentStructure(
                parameters = listOf(
                    ParameterComment(name = "input", description = "输入数据", type = "String"),
                    ParameterComment(name = "options", description = "配置选项", type = "Options", isOptional = true)
                ),
                returnDescription = "处理结果",
                exceptions = listOf(
                    ExceptionComment(type = "ValidationException", description = "数据验证失败")
                )
            )

            val suggestion = CommentSuggestion(
                content = "处理输入数据并返回结果",
                format = CommentFormat.JAVADOC,
                language = CommentLanguage.CHINESE,
                structure = structure
            )

            // 执行测试
            val formatted = suggestion.getFormattedComment()

            // 验证结果
            assertThat(formatted).contains("/**")
            assertThat(formatted).contains(" * 处理输入数据并返回结果")
            assertThat(formatted).contains(" * @param input 输入数据")
            assertThat(formatted).contains(" * @param options 配置选项")
            assertThat(formatted).contains(" * @return 处理结果")
            assertThat(formatted).contains(" * @throws ValidationException 数据验证失败")
            assertThat(formatted).contains(" */")
        }
    }

    // ==================== 注释语言测试 ====================

    @Nested
    @DisplayName("注释语言测试")
    inner class LanguageTests {

        @Test
        @DisplayName("中文注释 - 应该正确设置")
        fun `中文注释 - 应该正确设置`() {
            // 执行测试
            val suggestion = CommentSuggestion(
                content = "计算订单总金额",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE
            )

            // 验证结果
            assertThat(suggestion.language).isEqualTo(CommentLanguage.CHINESE)
        }

        @Test
        @DisplayName("英文注释 - 应该正确设置")
        fun `英文注释 - 应该正确设置`() {
            // 执行测试
            val suggestion = CommentSuggestion(
                content = "Calculate the total order amount",
                format = CommentFormat.JAVADOC,
                language = CommentLanguage.ENGLISH
            )

            // 验证结果
            assertThat(suggestion.language).isEqualTo(CommentLanguage.ENGLISH)
        }

        @Test
        @DisplayName("自动检测语言 - 应该正确设置")
        fun `自动检测语言 - 应该正确设置`() {
            // 执行测试
            val suggestion = CommentSuggestion(
                content = "根据上下文自动判断语言",
                format = CommentFormat.KDOC,
                language = CommentLanguage.AUTO
            )

            // 验证结果
            assertThat(suggestion.language).isEqualTo(CommentLanguage.AUTO)
        }
    }

    // ==================== 结构化信息测试 ====================

    @Nested
    @DisplayName("结构化信息测试")
    inner class StructureTests {

        @Test
        @DisplayName("参数注释 - 应该包含所有属性")
        fun `参数注释 - 应该包含所有属性`() {
            // 准备测试数据
            val paramComment = ParameterComment(
                name = "timeout",
                description = "请求超时时间（毫秒）",
                type = "Int",
                isOptional = true,
                defaultValue = "5000"
            )

            // 验证结果
            assertThat(paramComment.name).isEqualTo("timeout")
            assertThat(paramComment.description).isEqualTo("请求超时时间（毫秒）")
            assertThat(paramComment.type).isEqualTo("Int")
            assertThat(paramComment.isOptional).isTrue()
            assertThat(paramComment.defaultValue).isEqualTo("5000")
        }

        @Test
        @DisplayName("异常注释 - 应该包含类型和描述")
        fun `异常注释 - 应该包含类型和描述`() {
            // 准备测试数据
            val exceptionComment = ExceptionComment(
                type = "NetworkException",
                description = "网络连接失败时抛出"
            )

            // 验证结果
            assertThat(exceptionComment.type).isEqualTo("NetworkException")
            assertThat(exceptionComment.description).isEqualTo("网络连接失败时抛出")
        }

        @Test
        @DisplayName("注释结构 - 空列表应该使用默认值")
        fun `注释结构 - 空列表应该使用默认值`() {
            // 执行测试
            val structure = CommentStructure()

            // 验证结果
            assertThat(structure.parameters).isEmpty()
            assertThat(structure.returnDescription).isNull()
            assertThat(structure.exceptions).isEmpty()
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
            val suggestion = CommentSuggestion(
                content = "测试注释内容",
                format = CommentFormat.KDOC,
                language = CommentLanguage.CHINESE
            )

            // 执行测试（通过 toString 验证可以正常工作）
            val stringRepresentation = suggestion.toString()

            // 验证结果
            assertThat(stringRepresentation).contains("测试注释内容")
        }
    }
}
