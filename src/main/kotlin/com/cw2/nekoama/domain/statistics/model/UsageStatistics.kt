package com.cw2.nekoama.domain.statistics.model

import kotlinx.serialization.Serializable

/**
 * 功能使用统计数据
 *
 * @property namingCount 命名建议使用次数
 * @property commentCount 注释生成使用次数
 * @property customGenerateCount 自定义生成使用次数
 * @property lastUpdated 最后更新时间戳
 */
@Serializable
data class UsageStatistics(
    val namingCount: Int = 0,
    val commentCount: Int = 0,
    val customGenerateCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * 获取总使用次数
     */
    val totalCount: Int
        get() = namingCount + commentCount + customGenerateCount

    /**
     * 获取指定功能的使用次数
     */
    fun getCount(actionType: ActionType): Int = when (actionType) {
        ActionType.NAMING -> namingCount
        ActionType.COMMENT -> commentCount
        ActionType.CUSTOM_GENERATE -> customGenerateCount
    }

    /**
     * 获取指定功能占总数的百分比
     *
     * Edge Case: 当总次数为 0 时返回 0%（避免除零）
     */
    fun getPercentage(actionType: ActionType): Float {
        val total = totalCount
        return if (total > 0) {
            getCount(actionType).toFloat() / total * 100
        } else {
            0f
        }
    }

    /**
     * 增加指定功能的使用次数
     */
    fun increment(actionType: ActionType): UsageStatistics = when (actionType) {
        ActionType.NAMING -> copy(namingCount = namingCount + 1)
        ActionType.COMMENT -> copy(commentCount = commentCount + 1)
        ActionType.CUSTOM_GENERATE -> copy(customGenerateCount = customGenerateCount + 1)
    }
}
