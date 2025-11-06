package com.cw2.nekoama.core.metrics

import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * 增强版指标采集器
 * 支持持久化存储、详细分类统计和实时事件通知
 */
object EnhancedMetricsCollector {

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

    // 分类统计
    private val todayByType = mutableMapOf<ActionType, AtomicLong>()
    private val weekByType = mutableMapOf<ActionType, AtomicLong>()
    private val monthByType = mutableMapOf<ActionType, AtomicLong>()
    private val totalByType = mutableMapOf<ActionType, AtomicLong>()

    private val todayErrorsByType = mutableMapOf<ErrorType, AtomicLong>()
    private val weekErrorsByType = mutableMapOf<ErrorType, AtomicLong>()
    private val monthErrorsByType = mutableMapOf<ErrorType, AtomicLong>()
    private val totalErrorsByType = mutableMapOf<ErrorType, AtomicLong>()

    // 时间边界跟踪
    private var todayEpochDay: Long = currentEpochDay()
    private var currentWeekKey: String = currentWeekKey()
    private var currentMonthKey: String = currentMonthKey()

    // 事件监听器
    private val listeners = mutableSetOf<MetricsUpdateListener>()

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
    suspend fun recordTokens(count: Int) {
        // 这个方法为了兼容性保留，但推荐使用record方法
        record(
            actionType = ActionType.CUSTOM_GENERATE, // 默认类型
            success = true,
            latencyMs = 0,
            tokensUsed = count
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
     * 获取兼容版快照（保持向后兼容）
     */
    fun getSnapshot(): MetricsSnapshot {
        rolloverIfNeeded()

        val total = totalRequests.get()
        val success = totalSuccess.get()
        val latency = totalLatency.get()

        return MetricsSnapshot(
            today = todayRequests.get().toInt(),
            total = total.toInt(),
            successRate = if (total > 0) success.toDouble() / total.toDouble() else 0.0,
            averageLatencyMs = if (total > 0) (latency / total).toInt() else 0,
            tokensToday = todayTokens.get().toInt(),
            tokensWeek = weekTokens.get().toInt(),
            tokensMonth = monthTokens.get().toInt(),
            tokensTotal = totalTokens.get().toInt()
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
        }

        if (monthMetrics != null) {
            monthRequests.set(monthMetrics.totalRequests.toLong())
            monthSuccess.set(monthMetrics.successRequests.toLong())
            monthLatency.set(monthMetrics.totalLatencyMs)
            monthTokens.set(monthMetrics.totalTokens.toLong())
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
}

/**
 * 指标更新监听器接口
 */
interface MetricsUpdateListener {
    fun onMetricsUpdated(record: ActionRecord)
}