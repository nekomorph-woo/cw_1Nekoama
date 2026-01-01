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
    val model: String? = null,

    /**
     * 生成耗时（毫秒）
     */
    val generationTimeMs: Long? = null,

    /**
     * 使用的Token数量
     */
    val tokenCount: Int? = null,

    /**
     * 提示模板版本
     */
    val promptVersion: String? = null,

    /**
     * 上下文哈希值（用于缓存）
     */
    val contextHash: String? = null,

    /**
     * 生成参数
     */
    val generationParams: Map<String, String> = emptyMap(),
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
    val metadata: SuggestionMetadata = SuggestionMetadata(),

    /**
     * 语义标签（如：business-logic, utility, data-access等）
     */
    val semanticTags: List<String> = emptyList(),

    /**
     * 推理过程说明（可选，用于调试和用户理解）
     */
    val reasoning: String? = null
) {
    /**
     * 获取显示格式：名称 - 描述
     */
    fun getDisplayText(): String = "$name - $description"

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

// ============================================================================
// 建议排序器
// ============================================================================

/**
 * 建议排序器
 */
object SuggestionSorter {

    /**
     * 按质量得分排序（默认）
     */
    fun sortByQuality(suggestions: List<NamingSuggestion>): List<NamingSuggestion> {
        return suggestions.sortedByDescending { it.getQualityScore() }
    }

    /**
     * 按评分排序
     */
    fun sortByScore(suggestions: List<NamingSuggestion>): List<NamingSuggestion> {
        return suggestions.sortedByDescending { it.score }
    }

    /**
     * 按置信度排序
     */
    fun sortByConfidence(suggestions: List<NamingSuggestion>): List<NamingSuggestion> {
        return suggestions.sortedByDescending { it.confidence }
    }

    /**
     * 按生成时间排序（最新优先）
     */
    fun sortByTime(suggestions: List<NamingSuggestion>): List<NamingSuggestion> {
        return suggestions.sortedByDescending { it.generatedAt }
    }

    /**
     * 自定义排序
     */
    fun sortBy(
        suggestions: List<NamingSuggestion>,
        comparator: Comparator<NamingSuggestion>
    ): List<NamingSuggestion> {
        return suggestions.sortedWith(comparator)
    }
}

// ============================================================================
// 建议过滤器
// ============================================================================

/**
 * 建议过滤器
 */
object SuggestionFilter {

    /**
     * 按最小质量得分过滤
     */
    fun filterByMinQuality(
        suggestions: List<NamingSuggestion>,
        minQuality: Double
    ): List<NamingSuggestion> {
        return suggestions.filter { it.getQualityScore() >= minQuality }
    }

    /**
     * 按代码元素类型过滤
     */
    fun filterByElementType(
        suggestions: List<NamingSuggestion>,
        elementType: CodeElementType
    ): List<NamingSuggestion> {
        return suggestions.filter { it.isApplicableFor(elementType) }
    }

    /**
     * 按命名约定过滤
     */
    fun filterByNamingConvention(
        suggestions: List<NamingSuggestion>,
        convention: NamingConvention
    ): List<NamingSuggestion> {
        return suggestions.filter { it.namingConvention == convention }
    }

    /**
     * 按语义标签过滤
     */
    fun filterBySemanticTags(
        suggestions: List<NamingSuggestion>,
        tags: List<String>
    ): List<NamingSuggestion> {
        return suggestions.filter { suggestion ->
            tags.any { tag -> suggestion.semanticTags.contains(tag) }
        }
    }

    /**
     * 去除重复建议
     */
    fun removeDuplicates(suggestions: List<NamingSuggestion>): List<NamingSuggestion> {
        return suggestions.distinctBy { it.name.lowercase() }
    }

    /**
     * 限制结果数量
     */
    fun limitResults(suggestions: List<NamingSuggestion>, limit: Int): List<NamingSuggestion> {
        return suggestions.take(limit)
    }
}
