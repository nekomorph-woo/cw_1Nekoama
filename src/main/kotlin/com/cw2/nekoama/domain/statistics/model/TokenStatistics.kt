package com.cw2.nekoama.domain.statistics.model

/**
 * Token 统计数据
 *
 * @property totalTokens 累计总 Token 数（所有月份）
 * @property currentMonthData 当月数据
 * @property lastMonthData 上月数据
 * @property history 历史月度数据
 */
data class TokenStatistics(
    val totalTokens: Int = 0,
    val currentMonthData: MonthlyTokenData = MonthlyTokenData(MonthlyTokenData.currentYearMonth()),
    val lastMonthData: MonthlyTokenData? = null,
    val history: Map<String, MonthlyTokenData> = emptyMap()
) {
    companion object {
        /**
         * 默认基准 Token 数（当无历史数据时使用）
         */
        private const val DEFAULT_BASELINE_TOKENS = 1_000_000
    }

    /**
     * 获取环比增长率（相对于上月）
     *
     * Edge Case 处理：
     * - 上月无数据且当月有数据：使用 100 万作为基准计算
     * - 上月无数据且当月无数据：返回 null
     * - 上月有数据但为 0：返回 null（避免除零）
     *
     * @return 增长率百分比，正数表示增长，负数表示下降。无有效基数时返回 null
     */
    fun getMonthOverMonthGrowth(): Float? {
        val currentMonthTokens = currentMonthData.totalTokens
        val lastMonthTokens = lastMonthData?.totalTokens

        return when {
            // 上月有数据且大于 0：正常计算环比
            lastMonthTokens != null && lastMonthTokens > 0 -> {
                ((currentMonthTokens - lastMonthTokens).toFloat() / lastMonthTokens) * 100
            }
            // 上月无数据且当月有数据：使用 100 万作为基准
            lastMonthTokens == null && currentMonthTokens > 0 -> {
                ((currentMonthTokens - DEFAULT_BASELINE_TOKENS).toFloat() / DEFAULT_BASELINE_TOKENS) * 100
            }
            // 其他情况：返回 null
            else -> null
        }
    }

    /**
     * 格式化 Token 数量（>10w 显示为 M 单位）
     */
    fun formatTokenCount(count: Int): String {
        return when {
            count >= 100_000 -> {
                val millions = count.toFloat() / 1_000_000
                String.format("%.2fM", millions)
            }
            else -> count.toString()
        }
    }
}
