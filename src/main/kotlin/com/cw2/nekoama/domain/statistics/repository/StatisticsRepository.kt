package com.cw2.nekoama.domain.statistics.repository

import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.UsageStatistics

/**
 * 统计数据持久化仓库接口
 */
interface StatisticsRepository {
    // ========== 使用次数统计 ==========

    /**
     * 保存使用统计数据
     */
    fun saveUsageStatistics(statistics: UsageStatistics)

    /**
     * 加载使用统计数据
     */
    fun loadUsageStatistics(): UsageStatistics

    // ========== Token 统计 ==========

    /**
     * 保存 Token 历史数据
     */
    fun saveTokenHistory(history: Map<String, MonthlyTokenData>)

    /**
     * 加载 Token 历史数据
     */
    fun loadTokenHistory(): Map<String, MonthlyTokenData>

    /**
     * 获取累计总 Token 数
     */
    fun getTotalTokens(): Int

    /**
     * 保存累计总 Token 数
     */
    fun saveTotalTokens(total: Int)
}
