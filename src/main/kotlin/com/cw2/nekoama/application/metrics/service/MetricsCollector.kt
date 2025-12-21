package com.cw2.nekoama.application.metrics.service

import com.cw2.nekoama.infra.storage.metrics.IMetricsStorage
import com.cw2.nekoama.infra.storage.metrics.JsonMetricsStorage
import com.cw2.nekoama.infra.storage.metrics.StorageStats
import com.cw2.nekoama.domain.metrics.model.ActionRecord
import com.cw2.nekoama.domain.metrics.model.ActionType
import com.cw2.nekoama.domain.metrics.model.AggregatedMetrics
import com.cw2.nekoama.domain.metrics.model.DailyMetrics
import com.cw2.nekoama.domain.metrics.model.DailyTrendPoint
import com.cw2.nekoama.domain.metrics.model.EnhancedMetricsSnapshot
import com.cw2.nekoama.domain.metrics.model.ErrorType
import com.cw2.nekoama.domain.metrics.model.HistoricalMetrics
import com.cw2.nekoama.domain.metrics.model.MetricsQuery
import com.cw2.nekoama.domain.metrics.model.MonthlyMetrics
import com.cw2.nekoama.domain.metrics.model.TotalStats
import com.cw2.nekoama.domain.metrics.model.WeeklyMetrics
import com.cw2.nekoama.domain.metrics.model.WeeklyTrendPoint
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 增强版指标采集器
 * 支持持久化存储、详细分类统计和实时事件通知
 */
object MetricsCollector {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 存储接口
    private lateinit var storage: IMetricsStorage

    // 内存中的快速访问数据（用于实时展示）
    private val todayRequests = AtomicLong(0)
    private val todaySuccess = AtomicLong(0)
    private val todayLatency = AtomicLong(0)
    private val todayTokens = AtomicLong(0)

    private val weekRequests = AtomicLong(0)
    private val weekSuccess = AtomicLong(0)
    private val weekLatency = AtomicLong(0)
    private val weekTokens = AtomicLong(0)

    private val monthRequests = AtomicLong(0)
    private val monthSuccess = AtomicLong(0)
    private val monthLatency = AtomicLong(0)
    private val monthTokens = AtomicLong(0)

    private val totalRequests = AtomicLong(0)
    private val totalSuccess = AtomicLong(0)
    private val totalLatency = AtomicLong(0)
    private val totalTokens = AtomicLong(0)

    // 分类统计 - 使用线程安全的ConcurrentHashMap
    private val todayByType = ConcurrentHashMap<ActionType, AtomicLong>()
    private val weekByType = ConcurrentHashMap<ActionType, AtomicLong>()
    private val monthByType = ConcurrentHashMap<ActionType, AtomicLong>()
    private val totalByType = ConcurrentHashMap<ActionType, AtomicLong>()

    private val todayErrorsByType = ConcurrentHashMap<ErrorType, AtomicLong>()
    private val weekErrorsByType = ConcurrentHashMap<ErrorType, AtomicLong>()
    private val monthErrorsByType = ConcurrentHashMap<ErrorType, AtomicLong>()
    private val totalErrorsByType = ConcurrentHashMap<ErrorType, AtomicLong>()

    // 时间边界跟踪
    private var todayEpochDay: Long = currentEpochDay()
    private var currentWeekKey: String = currentWeekKey()
    private var currentMonthKey: String = currentMonthKey()

    // 事件监听器 - 使用线程安全的ConcurrentHashMap.newKeySet()
    private val listeners = ConcurrentHashMap.newKeySet<MetricsUpdateListener>()

    // 是否已初始化
    private var isInitialized = false

    /**
     * 初始化采集器
     */
    suspend fun initialize(storage: IMetricsStorage = JsonMetricsStorage()) {
        if (isInitialized) return

        this.storage = storage
        loadHistoricalData()
        isInitialized = true
    }

    /**
     * 记录操作执行
     */
    suspend fun record(
        actionType: ActionType,
        success: Boolean,
        latencyMs: Long,
        tokensUsed: Int = 0,
        errorMessage: String? = null,
        errorCode: String? = null,
        project: Project? = null,
        fileName: String? = null
    ) {
        ensureInitialized()

        val timestamp = System.currentTimeMillis()
        val record = ActionRecord(
            timestamp = timestamp,
            actionType = actionType,
            success = success,
            latencyMs = max(0, latencyMs),
            tokensUsed = tokensUsed,
            errorMessage = errorMessage,
            errorCode = errorCode,
            projectId = project?.locationHash?.toString(),
            fileName = fileName
        )

        // 更新内存统计
        updateMemoryStats(record)

        // 异步持久化
        scope.launch {
            try {
                storage.addRecord(record)
                notifyListeners(record)
            } catch (e: Exception) {
                // 持久化失败不应该影响主要功能
            }
        }
    }

    /**
     * 记录Token使用（兼容现有接口）
     */
    suspend fun recordTokens(count: Int, actionType: ActionType = ActionType.CUSTOM_GENERATE) {
        // 修复：添加actionType参数，允许调用者指定正确的操作类型
        // 保持向后兼容性，默认值仍为CUSTOM_GENERATE
        record(
            actionType = actionType,
            success = true,
            latencyMs = 0,
            tokensUsed = count
        )
    }

    /**
     * 强制同步数据：将内存中的统计数据立即持久化并重新加载
     * 增强版本：确保同步完成并验证数据一致性
     */
    suspend fun forceSync() {
        ensureInitialized()
        val syncStartTime = System.currentTimeMillis()

        try {
            NekoamaLogger.debug("EnhancedMetricsCollector", "Force sync started", mapOf(
                "todayRequests" to todayRequests.get(),
                "totalRequests" to totalRequests.get()
            ))

            // 立即持久化当前内存统计数据
            persistCurrentData()
            NekoamaLogger.debug("EnhancedMetricsCollector", "Current data persisted")

            // 重新加载历史数据以确保一致性
            loadHistoricalData()
            NekoamaLogger.debug("EnhancedMetricsCollector", "Historical data reloaded")

            // 验证数据一致性
            val persistedData = storage.loadMetrics()
            val consistencyVerified = verifyDataConsistency(persistedData)

            val syncDuration = System.currentTimeMillis() - syncStartTime
            NekoamaLogger.info("EnhancedMetricsCollector", "Data force sync completed", mapOf(
                "duration" to "${syncDuration}ms",
                "consistencyVerified" to consistencyVerified,
                "todayRequests" to todayRequests.get()
            ))

        } catch (e: Exception) {
            val syncDuration = System.currentTimeMillis() - syncStartTime
            NekoamaLogger.error("EnhancedMetricsCollector", "Force sync failed", mapOf(
                "duration" to "${syncDuration}ms",
                "error" to (e.message ?: "unknown")
            ), e)
            throw e // 重新抛出异常，让调用方知道同步失败
        }
    }

    /**
     * 持久化当前内存统计数据
     */
    private suspend fun persistCurrentData() {
        try {
            val existingMetrics = storage.loadMetrics() ?: createEmptyHistoricalMetrics()

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val currentWeek = currentWeekKey()
            val currentMonth = currentMonthKey()

            // 计算平均值和成功率
            val todayTotal = todayRequests.get()
            val todayAvgLatency = if (todayTotal > 0) todayLatency.get() / todayTotal else 0L
            val todaySuccessRate = if (todayTotal > 0) todaySuccess.get().toDouble() / todayTotal else 0.0

            val weekTotal = weekRequests.get()
            val weekAvgLatency = if (weekTotal > 0) weekLatency.get() / weekTotal else 0L
            val weekSuccessRate = if (weekTotal > 0) weekSuccess.get().toDouble() / weekTotal else 0.0

            val monthTotal = monthRequests.get()
            val monthAvgLatency = if (monthTotal > 0) monthLatency.get() / monthTotal else 0L
            val monthSuccessRate = if (monthTotal > 0) monthSuccess.get().toDouble() / monthTotal else 0.0

            // 更新今日数据
            val todayMetrics = DailyMetrics(
                date = today,
                totalRequests = todayTotal.toInt(),
                successRequests = todaySuccess.get().toInt(),
                totalLatencyMs = todayLatency.get(),
                totalTokens = todayTokens.get().toInt(),
                actionsByType = todayByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                errorsByType = todayErrorsByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                avgLatencyMs = todayAvgLatency,
                successRate = todaySuccessRate
            )

            // 计算周的起止日期
            val now = LocalDate.now()
            val wf = WeekFields.of(Locale.getDefault())
            val weekOfYear = now.get(wf.weekOfWeekBasedYear())
            val firstDayOfWeek = now.with(wf.dayOfWeek(), 1L)
            val lastDayOfWeek = firstDayOfWeek.plusDays(6)

            // 更新周数据
            val weekMetrics = WeeklyMetrics(
                weekKey = currentWeek,
                startDate = firstDayOfWeek.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = lastDayOfWeek.format(DateTimeFormatter.ISO_LOCAL_DATE),
                totalRequests = weekTotal.toInt(),
                successRequests = weekSuccess.get().toInt(),
                totalLatencyMs = weekLatency.get(),
                totalTokens = weekTokens.get().toInt(),
                actionsByType = weekByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                errorsByType = weekErrorsByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                avgLatencyMs = weekAvgLatency,
                successRate = weekSuccessRate
            )

            // 更新月数据
            val monthMetrics = MonthlyMetrics(
                monthKey = currentMonth,
                totalRequests = monthTotal.toInt(),
                successRequests = monthSuccess.get().toInt(),
                totalLatencyMs = monthLatency.get(),
                totalTokens = monthTokens.get().toInt(),
                actionsByType = monthByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                errorsByType = monthErrorsByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                avgLatencyMs = monthAvgLatency,
                successRate = monthSuccessRate
            )

            // 更新总体数据
            val totalStats = TotalStats(
                totalRequests = totalRequests.get().toInt(),
                successRequests = totalSuccess.get().toInt(),
                totalTokens = totalTokens.get().toInt(),
                totalLatencyMs = totalLatency.get(),
                firstRecordTime = existingMetrics.totalStats.firstRecordTime,
                lastRecordTime = System.currentTimeMillis(),
                actionsByType = totalByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name },
                errorsByType = totalErrorsByType.mapValues { it.value.get().toInt() }.mapKeys { it.key.name }
            )

            val updatedMetrics = existingMetrics.copy(
                lastUpdated = System.currentTimeMillis(),
                dailyMetrics = existingMetrics.dailyMetrics + (today to todayMetrics),
                weeklyMetrics = existingMetrics.weeklyMetrics + (currentWeek to weekMetrics),
                monthlyMetrics = existingMetrics.monthlyMetrics + (currentMonth to monthMetrics),
                totalStats = totalStats
            )

            storage.saveMetrics(updatedMetrics)
            NekoamaLogger.debug("EnhancedMetricsCollector", "Current data persisted successfully")
        } catch (e: Exception) {
            NekoamaLogger.error("EnhancedMetricsCollector", "Failed to persist current data", error = e)
            throw e
        }
    }

    /**
     * 验证数据一致性
     */
    fun validateDataConsistency(): DataConsistencyResult {
        return try {
            // 检查基础统计数据的一致性
            val todayTotal = todayRequests.get()
            val todayTypeSum = todayByType.values.sumOf { it.get() }
            val todayErrorSum = todayErrorsByType.values.sumOf { it.get() }

            val weekTotal = weekRequests.get()
            val weekTypeSum = weekByType.values.sumOf { it.get() }
            val weekErrorSum = weekErrorsByType.values.sumOf { it.get() }

            val monthTotal = monthRequests.get()
            val monthTypeSum = monthByType.values.sumOf { it.get() }
            val monthErrorSum = monthErrorsByType.values.sumOf { it.get() }

            val totalTotal = totalRequests.get()
            val totalTypeSum = totalByType.values.sumOf { it.get() }
            val totalErrorSum = totalErrorsByType.values.sumOf { it.get() }

            val issues = mutableListOf<String>()

            // 检查分类统计是否等于总请求数
            if (todayTypeSum != todayTotal) {
                issues.add("Daily type count mismatch: type sum=$todayTypeSum, total=$todayTotal")
            }

            if (weekTypeSum != weekTotal) {
                issues.add("Weekly type count mismatch: type sum=$weekTypeSum, total=$weekTotal")
            }

            if (monthTypeSum != monthTotal) {
                issues.add("Monthly type count mismatch: type sum=$monthTypeSum, total=$monthTotal")
            }

            if (totalTypeSum != totalTotal) {
                issues.add("Total type count mismatch: type sum=$totalTypeSum, total=$totalTotal")
            }

            // 检查错误统计是否合理（错误数不应该超过总数）
            if (todayErrorSum > todayTotal) {
                issues.add("Daily error count exceeds total: errors=$todayErrorSum, total=$todayTotal")
            }

            if (weekErrorSum > weekTotal) {
                issues.add("Weekly error count exceeds total: errors=$weekErrorSum, total=$weekTotal")
            }

            if (monthErrorSum > monthTotal) {
                issues.add("Monthly error count exceeds total: errors=$monthErrorSum, total=$monthTotal")
            }

            if (totalErrorSum > totalTotal) {
                issues.add("Total error count exceeds total: errors=$totalErrorSum, total=$totalTotal")
            }

            // 检查时间边界的合理性
            val now = currentEpochDay()
            if (todayEpochDay > now) {
                issues.add("Today epoch day is in the future: today=$todayEpochDay, now=$now")
            }

            if (issues.isEmpty()) {
                DataConsistencyResult(true, "Data consistency check passed", emptyList())
            } else {
                DataConsistencyResult(false, "Data consistency issues found", issues)
            }

        } catch (e: Exception) {
            DataConsistencyResult(false, "Data consistency check failed: ${e.message}", listOf(e.stackTraceToString()))
        }
    }

    /**
     * 创建空的HistoricalMetrics
     */
    private fun createEmptyHistoricalMetrics(): HistoricalMetrics {
        return HistoricalMetrics(
            version = 1,
            lastUpdated = System.currentTimeMillis(),
            dailyMetrics = emptyMap(),
            weeklyMetrics = emptyMap(),
            monthlyMetrics = emptyMap(),
            totalStats = TotalStats(
                totalRequests = 0,
                successRequests = 0,
                totalTokens = 0,
                totalLatencyMs = 0,
                firstRecordTime = 0,
                lastRecordTime = 0,
                actionsByType = emptyMap(),
                errorsByType = emptyMap()
            ),
            recentRecords = emptyList()
        )
    }

    /**
     * 获取增强版快照
     */
    suspend fun getEnhancedSnapshot(): EnhancedMetricsSnapshot {
        ensureInitialized()

        rolloverIfNeeded()

        val historicalMetrics = storage.loadMetrics() ?: return createEmptyEnhancedSnapshot()

        // 获取历史趋势数据
        val dailyTrend = historicalMetrics.getDailyMetricsForLastDays(7).map { daily ->
            DailyTrendPoint(
                date = daily.date,
                requests = daily.totalRequests,
                successRate = daily.successRate,
                avgLatencyMs = daily.avgLatencyMs
            )
        }

        val weeklyTrend = historicalMetrics.getWeeklyMetricsForWeeks(4).map { weekly ->
            WeeklyTrendPoint(
                weekKey = weekly.weekKey,
                requests = weekly.totalRequests,
                successRate = weekly.successRate,
                avgLatencyMs = weekly.avgLatencyMs
            )
        }

        return EnhancedMetricsSnapshot(
            // 基础统计
            today = todayRequests.get().toInt(),
            total = totalRequests.get().toInt(),
            successRate = if (totalRequests.get() > 0) {
                totalSuccess.get().toDouble() / totalRequests.get().toDouble()
            } else 0.0,
            averageLatencyMs = if (totalRequests.get() > 0) {
                (totalLatency.get() / totalRequests.get()).toInt()
            } else 0,
            tokensToday = todayTokens.get().toInt(),
            tokensWeek = weekTokens.get().toInt(),
            tokensMonth = monthTokens.get().toInt(),
            tokensTotal = totalTokens.get().toInt(),

            // 详细分类统计
            todayByType = todayByType.mapValues { it.value.get().toInt() },
            weeklyByType = weekByType.mapValues { it.value.get().toInt() },
            monthlyByType = monthByType.mapValues { it.value.get().toInt() },
            errorsToday = todayErrorsByType.mapValues { it.value.get().toInt() },
            errorsWeek = weekErrorsByType.mapValues { it.value.get().toInt() },

            // 历史趋势数据
            dailyTrend = dailyTrend,
            weeklyTrend = weeklyTrend,

            // 其他统计信息
            avgRequestsPerDay = historicalMetrics.getAverageRequestsPerDay(),
            mostUsedAction = ActionType.GENERATE_NAMING, // 暂时使用默认值
            peakUsageHour = historicalMetrics.getPeakUsageHour()
        )
    }

    /**
     * 查询历史数据
     */
    suspend fun queryMetrics(query: MetricsQuery): List<AggregatedMetrics> {
        ensureInitialized()
        return storage.queryMetrics(query)
    }

    /**
     * 导出数据
     */
    suspend fun exportData(startDate: LocalDate, endDate: LocalDate): String? {
        ensureInitialized()
        return storage.exportData(startDate, endDate)
    }

    /**
     * 获取存储统计
     */
    fun getStorageStats(): StorageStats {
        return if (::storage.isInitialized) {
            storage.getStorageStats()
        } else {
            StorageStats(0, 0, 0, 0, false)
        }
    }

    /**
     * 添加事件监听器
     */
    fun addListener(listener: MetricsUpdateListener) {
        listeners.add(listener)
    }

    /**
     * 移除事件监听器
     */
    fun removeListener(listener: MetricsUpdateListener) {
        listeners.remove(listener)
    }

    /**
     * 重置所有数据（危险操作）
     */
    suspend fun resetAll() {
        // 重置内存数据
        resetMemoryStats()

        // 重置持久化数据
        scope.launch {
            try {
                val emptyMetrics = HistoricalMetrics(
                    version = 1,
                    lastUpdated = System.currentTimeMillis(),
                    dailyMetrics = emptyMap(),
                    weeklyMetrics = emptyMap(),
                    monthlyMetrics = emptyMap(),
                    totalStats = TotalStats(
                        totalRequests = 0,
                        successRequests = 0,
                        totalTokens = 0,
                        totalLatencyMs = 0,
                        firstRecordTime = 0,
                        lastRecordTime = 0,
                        actionsByType = emptyMap(),
                        errorsByType = emptyMap()
                    ),
                    recentRecords = emptyList()
                )
                storage.saveMetrics(emptyMetrics)
            } catch (e: Exception) {
                // 重置失败处理
            }
        }
    }

    // 私有方法

    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }

    private suspend fun loadHistoricalData() {
        try {
            val historicalMetrics = storage.loadMetrics()
            if (historicalMetrics != null) {
                // 更新时间边界以匹配加载的数据
                todayEpochDay = currentEpochDay()
                currentWeekKey = currentWeekKey()
                currentMonthKey = currentMonthKey()

                // 从历史数据恢复当前统计
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val todayMetrics = historicalMetrics.dailyMetrics[today]

                if (todayMetrics != null) {
                    todayRequests.set(todayMetrics.totalRequests.toLong())
                    todaySuccess.set(todayMetrics.successRequests.toLong())
                    todayLatency.set(todayMetrics.totalLatencyMs)
                    todayTokens.set(todayMetrics.totalTokens.toLong())

                    // 恢复分类统计
                    todayMetrics.actionsByType.forEach { (type, count) ->
                        try {
                            val actionType = ActionType.valueOf(type)
                            todayByType.getOrPut(actionType) { AtomicLong(0) }.set(count.toLong())
                        } catch (e: IllegalArgumentException) {
                            // 忽略未知类型
                        }
                    }

                    todayMetrics.errorsByType.forEach { (type, count) ->
                        try {
                            val errorType = ErrorType.valueOf(type)
                            todayErrorsByType.getOrPut(errorType) { AtomicLong(0) }.set(count.toLong())
                        } catch (e: IllegalArgumentException) {
                            // 忽略未知类型
                        }
                    }
                }

                // 恢复总体统计
                totalRequests.set(historicalMetrics.totalStats.totalRequests.toLong())
                totalSuccess.set(historicalMetrics.totalStats.successRequests.toLong())
                totalLatency.set(historicalMetrics.totalStats.totalLatencyMs)
                totalTokens.set(historicalMetrics.totalStats.totalTokens.toLong())

                // 恢复分类统计
                historicalMetrics.totalStats.actionsByType.forEach { (type, count) ->
                    try {
                        val actionType = ActionType.valueOf(type)
                        totalByType.getOrPut(actionType) { AtomicLong(0) }.set(count.toLong())
                    } catch (e: IllegalArgumentException) {
                        // 忽略未知类型
                    }
                }

                // 计算周和月统计（简化处理）
                calculateWeekAndMonthStats(historicalMetrics)
            }
        } catch (e: Exception) {
            // 加载失败，使用空数据
            resetMemoryStats()
        }
    }

    private fun calculateWeekAndMonthStats(historicalMetrics: HistoricalMetrics) {
        val currentWeek = currentWeekKey()
        val currentMonth = currentMonthKey()

        val weekMetrics = historicalMetrics.weeklyMetrics[currentWeek]
        val monthMetrics = historicalMetrics.monthlyMetrics[currentMonth]

        if (weekMetrics != null) {
            weekRequests.set(weekMetrics.totalRequests.toLong())
            weekSuccess.set(weekMetrics.successRequests.toLong())
            weekLatency.set(weekMetrics.totalLatencyMs)
            weekTokens.set(weekMetrics.totalTokens.toLong())

            // 恢复周分类统计
            weekMetrics.actionsByType.forEach { (type, count) ->
                try {
                    val actionType = ActionType.valueOf(type)
                    weekByType.getOrPut(actionType) { AtomicLong(0) }.set(count.toLong())
                } catch (e: IllegalArgumentException) {
                    // 忽略未知类型
                }
            }

            // 恢复周错误统计
            weekMetrics.errorsByType.forEach { (type, count) ->
                try {
                    val errorType = ErrorType.valueOf(type)
                    weekErrorsByType.getOrPut(errorType) { AtomicLong(0) }.set(count.toLong())
                } catch (e: IllegalArgumentException) {
                    // 忽略未知类型
                }
            }
        }

        if (monthMetrics != null) {
            monthRequests.set(monthMetrics.totalRequests.toLong())
            monthSuccess.set(monthMetrics.successRequests.toLong())
            monthLatency.set(monthMetrics.totalLatencyMs)
            monthTokens.set(monthMetrics.totalTokens.toLong())

            // 恢复月分类统计
            monthMetrics.actionsByType.forEach { (type, count) ->
                try {
                    val actionType = ActionType.valueOf(type)
                    monthByType.getOrPut(actionType) { AtomicLong(0) }.set(count.toLong())
                } catch (e: IllegalArgumentException) {
                    // 忽略未知类型
                }
            }

            // 恢复月错误统计
            monthMetrics.errorsByType.forEach { (type, count) ->
                try {
                    val errorType = ErrorType.valueOf(type)
                    monthErrorsByType.getOrPut(errorType) { AtomicLong(0) }.set(count.toLong())
                } catch (e: IllegalArgumentException) {
                    // 忽略未知类型
                }
            }
        }
    }

    private fun updateMemoryStats(record: ActionRecord) {
        rolloverIfNeeded()

        // 基础统计
        todayRequests.incrementAndGet()
        totalRequests.incrementAndGet()

        if (record.success) {
            todaySuccess.incrementAndGet()
            totalSuccess.incrementAndGet()
        }

        todayLatency.addAndGet(record.latencyMs)
        totalLatency.addAndGet(record.latencyMs)

        todayTokens.addAndGet(record.tokensUsed.toLong())
        totalTokens.addAndGet(record.tokensUsed.toLong())

        // 周统计
        weekRequests.incrementAndGet()
        if (record.success) weekSuccess.incrementAndGet()
        weekLatency.addAndGet(record.latencyMs)
        weekTokens.addAndGet(record.tokensUsed.toLong())

        // 月统计
        monthRequests.incrementAndGet()
        if (record.success) monthSuccess.incrementAndGet()
        monthLatency.addAndGet(record.latencyMs)
        monthTokens.addAndGet(record.tokensUsed.toLong())

        // 分类统计
        todayByType.getOrPut(record.actionType) { AtomicLong(0) }.incrementAndGet()
        weekByType.getOrPut(record.actionType) { AtomicLong(0) }.incrementAndGet()
        monthByType.getOrPut(record.actionType) { AtomicLong(0) }.incrementAndGet()
        totalByType.getOrPut(record.actionType) { AtomicLong(0) }.incrementAndGet()

        // 错误统计
        if (!record.success) {
            val errorType = determineErrorType(record.errorMessage)
            todayErrorsByType.getOrPut(errorType) { AtomicLong(0) }.incrementAndGet()
            weekErrorsByType.getOrPut(errorType) { AtomicLong(0) }.incrementAndGet()
            monthErrorsByType.getOrPut(errorType) { AtomicLong(0) }.incrementAndGet()
            totalErrorsByType.getOrPut(errorType) { AtomicLong(0) }.incrementAndGet()
        }
    }

    private fun rolloverIfNeeded() {
        val now = currentEpochDay()
        if (now != todayEpochDay) {
            todayEpochDay = now
            resetDayStats()
        }

        val currentWeek = currentWeekKey()
        if (currentWeek != currentWeekKey) {
            currentWeekKey = currentWeek
            resetWeekStats()
        }

        val currentMonth = currentMonthKey()
        if (currentMonth != currentMonthKey) {
            currentMonthKey = currentMonth
            resetMonthStats()
        }
    }

    private fun resetDayStats() {
        todayRequests.set(0)
        todaySuccess.set(0)
        todayLatency.set(0)
        todayTokens.set(0)
        todayByType.values.forEach { it.set(0) }
        todayErrorsByType.values.forEach { it.set(0) }
    }

    private fun resetWeekStats() {
        weekRequests.set(0)
        weekSuccess.set(0)
        weekLatency.set(0)
        weekTokens.set(0)
        weekByType.values.forEach { it.set(0) }
        weekErrorsByType.values.forEach { it.set(0) }
    }

    private fun resetMonthStats() {
        monthRequests.set(0)
        monthSuccess.set(0)
        monthLatency.set(0)
        monthTokens.set(0)
        monthByType.values.forEach { it.set(0) }
        monthErrorsByType.values.forEach { it.set(0) }
    }

    private fun resetMemoryStats() {
        resetDayStats()
        resetWeekStats()
        resetMonthStats()

        totalRequests.set(0)
        totalSuccess.set(0)
        totalLatency.set(0)
        totalTokens.set(0)
        totalByType.values.forEach { it.set(0) }
        totalErrorsByType.values.forEach { it.set(0) }
    }

    private fun notifyListeners(record: ActionRecord) {
        listeners.forEach { listener ->
            try {
                listener.onMetricsUpdated(record)
            } catch (e: Exception) {
                // 监听器异常不应该影响其他监听器
            }
        }
    }

    private fun createEmptyEnhancedSnapshot(): EnhancedMetricsSnapshot {
        return EnhancedMetricsSnapshot(
            today = 0,
            total = 0,
            successRate = 0.0,
            averageLatencyMs = 0,
            tokensToday = 0,
            tokensWeek = 0,
            tokensMonth = 0,
            tokensTotal = 0,
            todayByType = emptyMap(),
            weeklyByType = emptyMap(),
            monthlyByType = emptyMap(),
            errorsToday = emptyMap(),
            errorsWeek = emptyMap(),
            dailyTrend = emptyList(),
            weeklyTrend = emptyList(),
            avgRequestsPerDay = 0.0,
            mostUsedAction = ActionType.GENERATE_NAMING,
            peakUsageHour = 0
        )
    }

    // 时间计算辅助方法
    private fun currentEpochDay(): Long = System.currentTimeMillis() / (24L * 60L * 60L * 1000L)

    private fun currentWeekKey(): String {
        val wf = WeekFields.of(Locale.getDefault())
        val now = LocalDate.now()
        val week = now.get(wf.weekOfWeekBasedYear())
        return "${now.year}-W${week.toString().padStart(2, '0')}"
    }

    private fun currentMonthKey(): String {
        val now = LocalDate.now()
        return "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
    }

    /**
     * 验证内存数据与持久化数据的一致性
     * @return true 如果数据一致，false 如果存在不一致
     */
    private fun verifyDataConsistency(persistedData: HistoricalMetrics?): Boolean {
        try {
            if (persistedData == null) {
                NekoamaLogger.warn("EnhancedMetricsCollector", "No persisted data available for consistency check")
                return false
            }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val todayInMemory = todayRequests.get()
            val todayInStorage = persistedData.dailyMetrics[today]?.totalRequests ?: 0

            val isConsistent = todayInMemory == todayInStorage.toLong()

            if (!isConsistent) {
                NekoamaLogger.warn("EnhancedMetricsCollector", "Data inconsistency detected", mapOf(
                    "todayInMemory" to todayInMemory,
                    "todayInStorage" to todayInStorage,
                    "difference" to (todayInMemory - todayInStorage)
                ))
            } else {
                NekoamaLogger.debug("EnhancedMetricsCollector", "Data consistency verified", mapOf(
                    "todayRequests" to todayInMemory
                ))
            }

            return isConsistent

        } catch (e: Exception) {
            NekoamaLogger.error("EnhancedMetricsCollector", "Failed to verify data consistency", error = e)
            return false
        }
    }
}

/**
 * 指标更新监听器接口
 */
interface MetricsUpdateListener {
    fun onMetricsUpdated(record: ActionRecord)
}

/**
 * 数据一致性检查结果
 */
data class DataConsistencyResult(
    val isConsistent: Boolean,
    val message: String,
    val issues: List<String>
)