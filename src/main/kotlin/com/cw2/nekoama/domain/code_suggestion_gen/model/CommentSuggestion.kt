package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

// ============================================================================
// 注释组成部分
// ============================================================================

/**
 * 参数注释信息
 */
@Serializable
data class ParameterComment(
    val name: String,
    val description: String,
    val type: String? = null,
    val isOptional: Boolean = false,
    val defaultValue: String? = null
)

/**
 * 异常注释信息
 */
@Serializable
data class ExceptionComment(
    val type: String,
    val description: String
)

// ============================================================================
// 注释结构
// ============================================================================

/**
 * 注释结构化信息
 */
@Serializable
data class CommentStructure(
    /**
     * 参数说明列表
     */
    val parameters: List<ParameterComment> = emptyList(),

    /**
     * 返回值描述
     */
    val returnDescription: String? = null,

    /**
     * 异常说明列表
     */
    val exceptions: List<ExceptionComment> = emptyList()
)

// ============================================================================
// 注释建议
// ============================================================================

/**
 * 注释建议数据类
 *
 * 包含AI生成的代码注释内容，支持多种注释格式和结构化信息。
 */
@Serializable
data class CommentSuggestion(
    /**
     * 主要注释内容
     */
    val content: String,

    /**
     * 注释格式类型
     */
    val format: CommentFormat,

    /**
     * 注释的结构化信息
     */
    val structure: CommentStructure? = null,

    /**
     * 注释语言（中文/英文）
     */
    val language: CommentLanguage,

    /**
     * 生成时间戳
     */
    val generatedAt: Long = System.currentTimeMillis(),

    /**
     * 建议的元数据信息
     */
    val metadata: SuggestionMetadata = SuggestionMetadata()
) {
    /**
     * 获取格式化后的完整注释文本
     */
    fun getFormattedComment(): String {
        return when (format) {
            CommentFormat.JAVADOC -> formatAsJavaDoc()
            CommentFormat.KDOC -> formatAsKDoc()
            CommentFormat.JSDOC -> formatAsJSDoc()
            CommentFormat.SINGLE_LINE -> "// $content"
            CommentFormat.MULTI_LINE -> "/* $content */"
            CommentFormat.PLAIN -> content
        }
    }

    /**
     * 格式化为JavaDoc格式
     */
    private fun formatAsJavaDoc(): String {
        if (structure == null) return "/**\n * $content\n */"

        return buildString {
            appendLine("/**")
            appendLine(" * $content")

            if (structure.parameters.isNotEmpty()) {
                appendLine(" *")
                structure.parameters.forEach { param ->
                    appendLine(" * @param ${param.name} ${param.description}")
                }
            }

            structure.returnDescription?.let { returnDesc ->
                appendLine(" * @return $returnDesc")
            }

            if (structure.exceptions.isNotEmpty()) {
                structure.exceptions.forEach { exc ->
                    appendLine(" * @throws ${exc.type} ${exc.description}")
                }
            }

            append(" */")
        }
    }

    /**
     * 格式化为KDoc格式
     */
    private fun formatAsKDoc(): String {
        return formatAsJavaDoc() // KDoc使用相同的格式
    }

    /**
     * 格式化为JSDoc格式
     */
    private fun formatAsJSDoc(): String {
        if (structure == null) return "/**\n * $content\n */"

        return buildString {
            appendLine("/**")
            appendLine(" * $content")

            if (structure.parameters.isNotEmpty()) {
                appendLine(" *")
                structure.parameters.forEach { param ->
                    appendLine(" * @param {${param.type ?: "any"}} ${param.name} ${param.description}")
                }
            }

            structure.returnDescription?.let { returnDesc ->
                appendLine(" * @returns $returnDesc")
            }

            if (structure.exceptions.isNotEmpty()) {
                structure.exceptions.forEach { exc ->
                    appendLine(" * @throws {${exc.type}} ${exc.description}")
                }
            }

            append(" */")
        }
    }
}
