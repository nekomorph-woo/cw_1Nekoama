package com.cw2.nekoama.domain.code_suggestion_gen.model

import kotlinx.serialization.Serializable

// ============================================================================
// 建议元数据
// ============================================================================

/**
 * 建议元数据信息
 */
@Serializable
data class SuggestionMetadata(
    /**
     * 生成来源（AI提供商名称）
     */
    val source: String? = null,

    /**
     * 使用的AI模型
     */
    val model: String? = null
)

// ============================================================================
// 命名建议模型
// ============================================================================

/**
 * 命名建议数据类
 *
 * 包含AI生成的命名建议及其相关信息，支持评分排序和元数据管理。
 */
@Serializable
data class NamingSuggestion(
    /**
     * 建议的名称
     */
    val name: String,

    /**
     * 命名的简单描述或解释
     */
    val description: String,

    /**
     * 建议的评分（0.0-1.0），分数越高越推荐
     */
    val score: Double,

    /**
     * 命名约定类型（驼峰、下划线等）
     */
    val namingConvention: NamingConvention,

    /**
     * 适用的上下文类型
     */
    val applicableFor: List<CodeElementType>,

    /**
     * 建议的置信度（0.0-1.0）
     */
    val confidence: Double,

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
     * 检查是否适用于指定的代码元素类型
     */
    fun isApplicableFor(elementType: CodeElementType): Boolean {
        return applicableFor.contains(elementType)
    }

    /**
     * 计算综合质量得分，结合评分和置信度
     */
    fun getQualityScore(): Double {
        return (score * 0.7 + confidence * 0.3).coerceIn(0.0, 1.0)
    }
}
