package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.TokenStatistics
import com.cw2.nekoama.domain.statistics.model.UsageStatistics

/**
 * 统计服务接口
 */
interface StatisticsService {
    /**
     * 记录功能使用
     */
    suspend fun recordUsage(actionType: ActionType)

    /**
     * 记录 Token 使用
     */
    suspend fun recordTokenUsage(usage: TokenUsageData)

    /**
     * 获取功能使用统计
     */
    fun getUsageStatistics(): UsageStatistics

    /**
     * 获取 Token 统计
     */
    fun getTokenStatistics(): TokenStatistics
}

/**
 * Token 使用数据
 */
data class TokenUsageData(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
