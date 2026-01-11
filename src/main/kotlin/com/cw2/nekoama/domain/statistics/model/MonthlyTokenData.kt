package com.cw2.nekoama.domain.statistics.model

import kotlinx.serialization.Serializable

/**
 * 月度 Token 数据
 *
 * @property yearMonth 年月标识（格式：YYYY-MM，如 "2025-01"）
 * @property totalTokens 总 Token 数
 * @property promptTokens 输入 Token 数
 * @property completionTokens 输出 Token 数
 */
@Serializable
data class MonthlyTokenData(
    val yearMonth: String,
    val totalTokens: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) {
    companion object {
        /**
         * 获取当前年月标识
         */
        fun currentYearMonth(): String {
            return java.time.YearMonth.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
            )
        }

        /**
         * 获取上月年月标识
         */
        fun lastYearMonth(): String {
            return java.time.YearMonth.now().minusMonths(1).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
            )
        }
    }
}
