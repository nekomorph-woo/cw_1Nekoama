package com.cw2.nekoama.application.metrics.service

import com.cw2.nekoama.domain.metrics.model.ActionRecord
import com.cw2.nekoama.domain.metrics.model.AggregatedMetrics
import com.cw2.nekoama.domain.metrics.model.DailyMetrics
import com.cw2.nekoama.domain.metrics.model.ErrorType
import com.cw2.nekoama.domain.metrics.model.GroupBy
import com.cw2.nekoama.domain.metrics.model.HistoricalMetrics
import com.cw2.nekoama.domain.metrics.model.MetricsQuery
import com.cw2.nekoama.domain.metrics.model.MonthlyMetrics
import com.cw2.nekoama.domain.metrics.model.TotalStats
import com.cw2.nekoama.domain.metrics.model.WeeklyMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

/**
 * 数据模型扩展函数 - 修复版本
 * 提供各种聚合和计算功能
 */

// 为DailyMetrics创建扩展函数
fun DailyMetrics.addRecord(record: ActionRecord): DailyMetrics {
    return copy(
        totalRequests = totalRequests + 1,
        successRequests = if (record.success) successRequests + 1 else successRequests,
        totalLatencyMs = totalLatencyMs + record.latencyMs,
        totalTokens = totalTokens + record.tokensUsed,
        actionsByType = actionsByType.toMutableMap().apply {
            put(record.actionType.name, getOrDefault(record.actionType.name, 0) + 1)
        },
        errorsByType = if (!record.success) {
            errorsByType.toMutableMap().apply {
                val errorType = determineErrorType(record.errorMessage)
                put(errorType.name, getOrDefault(errorType.name, 0) + 1)
            }
        } else errorsByType,
        avgLatencyMs = (totalLatencyMs + record.latencyMs) / (totalRequests + 1),
        successRate = (successRequests + if (record.success) 1 else 0).toDouble() / (totalRequests + 1)
    )
}

fun createDailyMetricsFromRecord(record: ActionRecord, date: String): DailyMetrics {
    val actionsByType = mapOf(record.actionType.name to 1)
    val errorsByType = if (!record.success) {
        mapOf(determineErrorType(record.errorMessage).name to 1)
    } else emptyMap()

    return DailyMetrics(
        date = date,
        totalRequests = 1,
        successRequests = if (record.success) 1 else 0,
        totalLatencyMs = record.latencyMs,
        totalTokens = record.tokensUsed,
        actionsByType = actionsByType,
        errorsByType = errorsByType,
        avgLatencyMs = record.latencyMs,
        successRate = if (record.success) 1.0 else 0.0
    )
}

// 为WeeklyMetrics创建扩展函数
fun WeeklyMetrics.addRecord(record: ActionRecord): WeeklyMetrics {
    return copy(
        totalRequests = totalRequests + 1,
        successRequests = if (record.success) successRequests + 1 else successRequests,
        totalLatencyMs = totalLatencyMs + record.latencyMs,
        totalTokens = totalTokens + record.tokensUsed,
        actionsByType = actionsByType.toMutableMap().apply {
            put(record.actionType.name, getOrDefault(record.actionType.name, 0) + 1)
        },
        errorsByType = if (!record.success) {
            errorsByType.toMutableMap().apply {
                val errorType = determineErrorType(record.errorMessage)
                put(errorType.name, getOrDefault(errorType.name, 0) + 1)
            }
        } else errorsByType,
        avgLatencyMs = (totalLatencyMs + record.latencyMs) / (totalRequests + 1),
        successRate = (successRequests + if (record.success) 1 else 0).toDouble() / (totalRequests + 1)
    )
}

fun createWeeklyMetricsFromRecord(record: ActionRecord, weekKey: String): WeeklyMetrics {
    val date = LocalDate.ofEpochDay(record.timestamp / (24 * 60 * 60 * 1000))
    val startDate = date.minusDays(date.dayOfWeek.value - 1L)
    val endDate = startDate.plusDays(6)

    val actionsByType = mapOf(record.actionType.name to 1)
    val errorsByType = if (!record.success) {
        mapOf(determineErrorType(record.errorMessage).name to 1)
    } else emptyMap()

    return WeeklyMetrics(
        weekKey = weekKey,
        startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        totalRequests = 1,
        successRequests = if (record.success) 1 else 0,
        totalLatencyMs = record.latencyMs,
        totalTokens = record.tokensUsed,
        actionsByType = actionsByType,
        errorsByType = errorsByType,
        avgLatencyMs = record.latencyMs,
        successRate = if (record.success) 1.0 else 0.0
    )
}

// 为MonthlyMetrics创建扩展函数
fun MonthlyMetrics.addRecord(record: ActionRecord): MonthlyMetrics {
    return copy(
        totalRequests = totalRequests + 1,
        successRequests = if (record.success) successRequests + 1 else successRequests,
        totalLatencyMs = totalLatencyMs + record.latencyMs,
        totalTokens = totalTokens + record.tokensUsed,
        actionsByType = actionsByType.toMutableMap().apply {
            put(record.actionType.name, getOrDefault(record.actionType.name, 0) + 1)
        },
        errorsByType = if (!record.success) {
            errorsByType.toMutableMap().apply {
                val errorType = determineErrorType(record.errorMessage)
                put(errorType.name, getOrDefault(errorType.name, 0) + 1)
            }
        } else errorsByType,
        avgLatencyMs = (totalLatencyMs + record.latencyMs) / (totalRequests + 1),
        successRate = (successRequests + if (record.success) 1 else 0).toDouble() / (totalRequests + 1)
    )
}

fun createMonthlyMetricsFromRecord(record: ActionRecord, monthKey: String): MonthlyMetrics {
    val actionsByType = mapOf(record.actionType.name to 1)
    val errorsByType = if (!record.success) {
        mapOf(determineErrorType(record.errorMessage).name to 1)
    } else emptyMap()

    return MonthlyMetrics(
        monthKey = monthKey,
        totalRequests = 1,
        successRequests = if (record.success) 1 else 0,
        totalLatencyMs = record.latencyMs,
        totalTokens = record.tokensUsed,
        actionsByType = actionsByType,
        errorsByType = errorsByType,
        avgLatencyMs = record.latencyMs,
        successRate = if (record.success) 1.0 else 0.0
    )
}

// 为TotalStats创建扩展函数
fun TotalStats.addRecord(record: ActionRecord): TotalStats {
    val newActionsByType = actionsByType.toMutableMap()
    newActionsByType[record.actionType.name] = newActionsByType.getOrDefault(record.actionType.name, 0) + 1

    val newErrorsByType = if (!record.success) {
        errorsByType.toMutableMap().apply {
            val errorType = determineErrorType(record.errorMessage)
            put(errorType.name, getOrDefault(errorType.name, 0) + 1)
        }
    } else errorsByType

    return copy(
        totalRequests = totalRequests + 1,
        successRequests = if (record.success) successRequests + 1 else successRequests,
        totalTokens = totalTokens + record.tokensUsed,
        totalLatencyMs = totalLatencyMs + record.latencyMs,
        firstRecordTime = if (firstRecordTime == 0L) record.timestamp else minOf(firstRecordTime, record.timestamp),
        lastRecordTime = maxOf(lastRecordTime, record.timestamp),
        actionsByType = newActionsByType,
        errorsByType = newErrorsByType
    )
}

// 为AggregatedMetrics创建扩展函数
fun createAggregatedMetricsFromRecords(records: List<ActionRecord>, period: String): AggregatedMetrics {
    if (records.isEmpty()) {
        return AggregatedMetrics(
            totalRequests = 0,
            successRequests = 0,
            totalTokens = 0,
            avgLatencyMs = 0,
            successRate = 0.0,
            breakdown = emptyMap(),
            period = period
        )
    }

    val totalRequests = records.size
    val successRequests = records.count { it.success }
    val totalTokens = records.sumOf { it.tokensUsed }
    val totalLatency = records.sumOf { it.latencyMs }

    val breakdown = records.groupBy { it.actionType.name }
        .mapValues { it.value.size }

    return AggregatedMetrics(
        totalRequests = totalRequests,
        successRequests = successRequests,
        totalTokens = totalTokens,
        avgLatencyMs = totalLatency / totalRequests,
        successRate = successRequests.toDouble() / totalRequests,
        breakdown = breakdown,
        period = period
    )
}

// 为HistoricalMetrics创建扩展函数
fun HistoricalMetrics.addRecord(record: ActionRecord): HistoricalMetrics {
    val dateKey = LocalDate.ofEpochDay(record.timestamp / (24 * 60 * 60 * 1000)).format(DateTimeFormatter.ISO_LOCAL_DATE)
    val weekKey = getWeekKey(record.timestamp)
    val monthKey = getMonthKey(record.timestamp)

    // 更新日统计
    val updatedDaily = dailyMetrics[dateKey]?.addRecord(record) ?:
        createDailyMetricsFromRecord(record, dateKey)

    // 更新周统计
    val updatedWeekly = weeklyMetrics[weekKey]?.addRecord(record) ?:
        createWeeklyMetricsFromRecord(record, weekKey)

    // 更新月统计
    val updatedMonthly = monthlyMetrics[monthKey]?.addRecord(record) ?:
        createMonthlyMetricsFromRecord(record, monthKey)

    // 更新总体统计
    val updatedTotal = totalStats.addRecord(record)

    // 更新最近记录
    val updatedRecent = (listOf(record) + recentRecords).take(1000)

    return copy(
        lastUpdated = System.currentTimeMillis(),
        dailyMetrics = dailyMetrics + (dateKey to updatedDaily),
        weeklyMetrics = weeklyMetrics + (weekKey to updatedWeekly),
        monthlyMetrics = monthlyMetrics + (monthKey to updatedMonthly),
        totalStats = updatedTotal,
        recentRecords = updatedRecent
    )
}

fun HistoricalMetrics.filterByDateRange(startDate: LocalDate, endDate: LocalDate): HistoricalMetrics {
    val filteredDaily = dailyMetrics.filter { (date, _) ->
        val d = LocalDate.parse(date)
        !d.isBefore(startDate) && !d.isAfter(endDate)
    }

    return copy(
        dailyMetrics = filteredDaily,
        weeklyMetrics = weeklyMetrics.filter { (week, _) ->
            filteredDaily.keys.any { date ->
                getWeekKey(LocalDate.parse(date).toEpochDay() * 24 * 60 * 60 * 1000) == week
            }
        },
        monthlyMetrics = monthlyMetrics.filter { (month, _) ->
            filteredDaily.keys.any { date ->
                getMonthKey(LocalDate.parse(date).toEpochDay() * 24 * 60 * 60 * 1000) == month
            }
        }
    )
}

fun HistoricalMetrics.query(query: MetricsQuery): List<AggregatedMetrics> {
    val filteredRecords = recentRecords.filter { record ->
        val date = LocalDate.ofEpochDay(record.timestamp / (24 * 60 * 60 * 1000))

        // 日期范围过滤
        val dateMatch = (query.startDate == null || !date.isBefore(query.startDate)) &&
                       (query.endDate == null || !date.isAfter(query.endDate))

        // 操作类型过滤
        val typeMatch = query.actionTypes == null || record.actionType in query.actionTypes

        // 成功/失败过滤
        val statusMatch = (query.includeSuccess && record.success) ||
                         (query.includeErrors && !record.success)

        dateMatch && typeMatch && statusMatch
    }

    return when (query.groupBy) {
        GroupBy.DAY -> groupByDay(filteredRecords)
        GroupBy.WEEK -> groupByWeek(filteredRecords)
        GroupBy.MONTH -> groupByMonth(filteredRecords)
        GroupBy.ACTION_TYPE -> groupByActionType(filteredRecords)
    }
}

// 私有辅助函数
private fun groupByDay(records: List<ActionRecord>): List<AggregatedMetrics> {
    return records.groupBy { record ->
        LocalDate.ofEpochDay(record.timestamp / (24 * 60 * 60 * 1000))
    }.map { (date, dayRecords) ->
        createAggregatedMetricsFromRecords(dayRecords, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
}

private fun groupByWeek(records: List<ActionRecord>): List<AggregatedMetrics> {
    return records.groupBy { record -> getWeekKey(record.timestamp) }
        .map { (weekKey, weekRecords) ->
            createAggregatedMetricsFromRecords(weekRecords, weekKey)
        }
}

private fun groupByMonth(records: List<ActionRecord>): List<AggregatedMetrics> {
    return records.groupBy { record -> getMonthKey(record.timestamp) }
        .map { (monthKey, monthRecords) ->
            createAggregatedMetricsFromRecords(monthRecords, monthKey)
        }
}

private fun groupByActionType(records: List<ActionRecord>): List<AggregatedMetrics> {
    return records.groupBy { it.actionType }
        .map { (actionType, typeRecords) ->
            createAggregatedMetricsFromRecords(typeRecords, actionType.name)
        }
}

// 辅助函数：根据错误消息确定错误类型
fun determineErrorType(errorMessage: String?): ErrorType {
    if (errorMessage.isNullOrBlank()) {
        return ErrorType.UNKNOWN_ERROR
    }

    return when {
        errorMessage.contains("network", ignoreCase = true) ||
        errorMessage.contains("connection", ignoreCase = true) ||
        errorMessage.contains("timeout", ignoreCase = true) &&
        errorMessage.contains("read", ignoreCase = true) -> ErrorType.NETWORK_ERROR

        errorMessage.contains("timeout", ignoreCase = true) -> ErrorType.TIMEOUT_ERROR

        errorMessage.contains("parse", ignoreCase = true) ||
        errorMessage.contains("format", ignoreCase = true) ||
        errorMessage.contains("json", ignoreCase = true) -> ErrorType.PARSING_ERROR

        errorMessage.contains("api", ignoreCase = true) ||
        errorMessage.contains("key", ignoreCase = true) ||
        errorMessage.contains("authentication", ignoreCase = true) ||
        errorMessage.contains("authorization", ignoreCase = true) -> ErrorType.API_ERROR

        else -> ErrorType.UNKNOWN_ERROR
    }
}

// 历史数据查询和扩展函数
fun HistoricalMetrics.getDailyMetricsForLastDays(days: Int): List<DailyMetrics> {
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(days.toLong() - 1)

    return (0 until days).map { i ->
        val date = startDate.plusDays(i.toLong())
        val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        dailyMetrics[dateKey] ?: DailyMetrics(
            date = dateKey,
            totalRequests = 0,
            successRequests = 0,
            totalLatencyMs = 0,
            totalTokens = 0,
            actionsByType = emptyMap(),
            errorsByType = emptyMap(),
            avgLatencyMs = 0,
            successRate = 0.0
        )
    }
}

fun HistoricalMetrics.getWeeklyMetricsForWeeks(weeks: Int): List<WeeklyMetrics> {
    val currentWeek = getWeekKey(System.currentTimeMillis())
    val (currentYear, currentWeekNum) = currentWeek.split("-W").let { (year, week) ->
        year.toInt() to week.toInt()
    }

    return (0 until weeks).map { i ->
        val weekNum = currentWeekNum - i
        val year = if (weekNum <= 0) currentYear - 1 else currentYear
        val adjustedWeekNum = if (weekNum <= 0) weekNum + 52 else weekNum
        val weekKey = "${year}-W${adjustedWeekNum.toString().padStart(2, '0')}"

        weeklyMetrics[weekKey] ?: WeeklyMetrics(
            weekKey = weekKey,
            startDate = "",
            endDate = "",
            totalRequests = 0,
            successRequests = 0,
            totalLatencyMs = 0,
            totalTokens = 0,
            actionsByType = emptyMap(),
            errorsByType = emptyMap(),
            avgLatencyMs = 0,
            successRate = 0.0
        )
    }.reversed()
}

fun HistoricalMetrics.getMonthlyMetricsForMonths(months: Int): List<MonthlyMetrics> {
    val currentDate = LocalDate.now()

    return (0 until months).map { i ->
        val date = currentDate.minusMonths(i.toLong())
        val monthKey = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))

        monthlyMetrics[monthKey] ?: MonthlyMetrics(
            monthKey = monthKey,
            totalRequests = 0,
            successRequests = 0,
            totalLatencyMs = 0,
            totalTokens = 0,
            actionsByType = emptyMap(),
            errorsByType = emptyMap(),
            avgLatencyMs = 0,
            successRate = 0.0
        )
    }.reversed()
}

// 获取最高使用时段
fun HistoricalMetrics.getPeakUsageHour(): Int {
    return recentRecords.groupBy {
        (it.timestamp / (60 * 60 * 1000)) % 24
    }.maxByOrNull { it.value.size }?.key?.toInt() ?: 0
}

// 计算平均每日请求数
fun HistoricalMetrics.getAverageRequestsPerDay(): Double {
    val days = totalStats.let { stats ->
        if (stats.firstRecordTime == 0L || stats.lastRecordTime == 0L) return@let 1
        val daysDiff = (stats.lastRecordTime - stats.firstRecordTime) / (24 * 60 * 60 * 1000) + 1
        maxOf(daysDiff, 1)
    }

    return totalStats.totalRequests.toDouble() / days
}

// 辅助函数
fun getWeekKey(timestamp: Long): String {
    val date = LocalDate.ofEpochDay(timestamp / (24 * 60 * 60 * 1000))
    val weekFields = WeekFields.of(Locale.getDefault())
    val week = date.get(weekFields.weekOfWeekBasedYear())
    return "${date.year}-W${week.toString().padStart(2, '0')}"
}

fun getMonthKey(timestamp: Long): String {
    val date = LocalDate.ofEpochDay(timestamp / (24 * 60 * 60 * 1000))
    return "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
}

// 公开函数，供其他模块调用
fun addRecordToHistoricalMetrics(metrics: HistoricalMetrics, record: ActionRecord): HistoricalMetrics {
    return metrics.addRecord(record)
}