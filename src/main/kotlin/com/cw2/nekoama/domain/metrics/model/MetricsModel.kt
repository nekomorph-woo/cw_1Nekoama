package com.cw2.nekoama.domain.metrics.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * 持久化指标数据模型
 * 支持历史数据存储和详细分类统计
 */

/**
 * 操作类型枚举
 */
@Serializable
enum class ActionType {
    GENERATE_NAMING,
    GENERATE_COMMENT,
    CUSTOM_GENERATE,
    ANALYZE_CODE_DEPS
}

/**
 * 错误类型枚举
 */
@Serializable
enum class ErrorType {
    NETWORK_ERROR,
    API_ERROR,
    TIMEOUT_ERROR,
    PARSING_ERROR,
    UNKNOWN_ERROR
}

/**
 * 单次操作记录
 */
@Serializable
data class ActionRecord(
    val timestamp: Long,           // 操作时间戳
    val actionType: ActionType,    // 操作类型
    val success: Boolean,          // 是否成功
    val latencyMs: Long,           // 延迟(毫秒)
    val tokensUsed: Int,           // 使用的Token数量
    val errorMessage: String?,     // 错误信息(如果有)
    val errorCode: String?,        // 错误代码(如果有)
    val projectId: String?,        // 项目标识
    val fileName: String?          // 文件名(如果可获取)
)

/**
 * 日统计数据
 */
@Serializable
data class DailyMetrics(
    val date: String,              // 日期 yyyy-MM-dd
    val totalRequests: Int,        // 总请求数
    val successRequests: Int,      // 成功请求数
    val totalLatencyMs: Long,      // 总延迟
    val totalTokens: Int,          // 总Token数
    val actionsByType: Map<String, Int>, // 按类型分组的操作数
    val errorsByType: Map<String, Int>,  // 按类型分组的错误数
    val avgLatencyMs: Long,        // 平均延迟
    val successRate: Double        // 成功率
)

/**
 * 周统计数据
 */
@Serializable
data class WeeklyMetrics(
    val weekKey: String,           // 周标识 yyyy-Www
    val startDate: String,         // 开始日期
    val endDate: String,           // 结束日期
    val totalRequests: Int,
    val successRequests: Int,
    val totalLatencyMs: Long,
    val totalTokens: Int,
    val actionsByType: Map<String, Int>,
    val errorsByType: Map<String, Int>,
    val avgLatencyMs: Long,
    val successRate: Double
)

/**
 * 月统计数据
 */
@Serializable
data class MonthlyMetrics(
    val monthKey: String,          // 月标识 yyyy-MM
    val totalRequests: Int,
    val successRequests: Int,
    val totalLatencyMs: Long,
    val totalTokens: Int,
    val actionsByType: Map<String, Int>,
    val errorsByType: Map<String, Int>,
    val avgLatencyMs: Long,
    val successRate: Double
)

/**
 * 完整的历史统计数据
 */
@Serializable
data class HistoricalMetrics(
    val version: Int = 1,                              // 数据版本
    val lastUpdated: Long,                              // 最后更新时间
    val dailyMetrics: Map<String, DailyMetrics>,        // 日统计数据
    val weeklyMetrics: Map<String, WeeklyMetrics>,      // 周统计数据
    val monthlyMetrics: Map<String, MonthlyMetrics>,    // 月统计数据
    val totalStats: TotalStats,                         // 总体统计
    val recentRecords: List<ActionRecord>               // 最近操作记录(限制数量)
)

/**
 * 总体统计数据
 */
@Serializable
data class TotalStats(
    val totalRequests: Int,
    val successRequests: Int,
    val totalTokens: Int,
    val totalLatencyMs: Long,
    val firstRecordTime: Long,
    val lastRecordTime: Long,
    val actionsByType: Map<String, Int>,
    val errorsByType: Map<String, Int>
)

/**
 * 统计查询参数
 */
data class MetricsQuery(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val actionTypes: Set<ActionType>? = null,
    val includeErrors: Boolean = true,
    val includeSuccess: Boolean = true,
    val groupBy: GroupBy = GroupBy.DAY
)

/**
 * 分组类型
 */
enum class GroupBy {
    DAY, WEEK, MONTH, ACTION_TYPE
}

/**
 * 聚合统计结果
 */
data class AggregatedMetrics(
    val totalRequests: Int,
    val successRequests: Int,
    val totalTokens: Int,
    val avgLatencyMs: Long,
    val successRate: Double,
    val breakdown: Map<String, Int>,   // 按分组条件的详细数据
    val period: String                 // 时间周期描述
)

/**
 * 扩展的指标快照，包含更多详细信息
 */
data class EnhancedMetricsSnapshot(
    // 基础统计
    val today: Int,
    val total: Int,
    val successRate: Double,
    val averageLatencyMs: Int,
    val tokensToday: Int,
    val tokensWeek: Int,
    val tokensMonth: Int,
    val tokensTotal: Int,

    // 详细分类统计
    val todayByType: Map<ActionType, Int>,
    val weeklyByType: Map<ActionType, Int>,
    val monthlyByType: Map<ActionType, Int>,
    val errorsToday: Map<ErrorType, Int>,
    val errorsWeek: Map<ErrorType, Int>,

    // 历史趋势数据
    val dailyTrend: List<DailyTrendPoint>,
    val weeklyTrend: List<WeeklyTrendPoint>,

    // 其他统计信息
    val avgRequestsPerDay: Double,
    val mostUsedAction: ActionType,
    val peakUsageHour: Int
)

/**
 * 日趋势数据点
 */
data class DailyTrendPoint(
    val date: String,
    val requests: Int,
    val successRate: Double,
    val avgLatencyMs: Long
)

/**
 * 周趋势数据点
 */
data class WeeklyTrendPoint(
    val weekKey: String,
    val requests: Int,
    val successRate: Double,
    val avgLatencyMs: Long
)