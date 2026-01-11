package com.cw2.nekoama.domain.statistics.service

import com.cw2.nekoama.domain.statistics.model.ActionType
import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.TokenStatistics
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.cw2.nekoama.domain.statistics.repository.StatisticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统计服务实现
 *
 * 职责：
 * - 编排使用统计的记录和查询
 * - 编排 Token 统计的记录和查询
 * - 处理月度数据的累加和聚合
 */
class StatisticsServiceImpl(
    private val repository: StatisticsRepository
) : StatisticsService {

    private val currentMonth = MonthlyTokenData.currentYearMonth()

    // ========== 使用次数统计 ==========

    override suspend fun recordUsage(actionType: ActionType) {
        withContext(Dispatchers.IO) {
            val currentStats = repository.loadUsageStatistics()
            val updatedStats = currentStats.increment(actionType)
            repository.saveUsageStatistics(updatedStats)
        }
    }

    override fun getUsageStatistics(): UsageStatistics {
        return repository.loadUsageStatistics()
    }

    // ========== Token 统计 ==========

    override suspend fun recordTokenUsage(usage: TokenUsageData) {
        withContext(Dispatchers.IO) {
            // 1. 加载当前历史
            val history = repository.loadTokenHistory().toMutableMap()

            // 2. 获取或创建当月数据
            val currentMonthData = history.getOrPut(currentMonth) {
                MonthlyTokenData(yearMonth = currentMonth)
            }

            // 3. 累加当月数据
            val updatedMonthData = currentMonthData.copy(
                totalTokens = currentMonthData.totalTokens + usage.totalTokens,
                promptTokens = currentMonthData.promptTokens + usage.promptTokens,
                completionTokens = currentMonthData.completionTokens + usage.completionTokens
            )
            history[currentMonth] = updatedMonthData

            // 4. 更新总计
            val totalTokens = repository.getTotalTokens() + usage.totalTokens

            // 5. 保存
            repository.saveTokenHistory(history)
            repository.saveTotalTokens(totalTokens)
        }
    }

    override fun getTokenStatistics(): TokenStatistics {
        val history = repository.loadTokenHistory()
        val currentMonthData = history[currentMonth] ?: MonthlyTokenData(currentMonth)
        val lastMonthKey = MonthlyTokenData.lastYearMonth()
        val lastMonthData = history[lastMonthKey]

        return TokenStatistics(
            totalTokens = repository.getTotalTokens(),
            currentMonthData = currentMonthData,
            lastMonthData = lastMonthData,
            history = history
        )
    }
}
