package com.cw2.nekoama.core.metrics

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * 简易指标采集器
 *
 * 收集核心使用统计：总请求数、成功数、累计耗时、Token使用量。
 */
object MetricsCollector {
    // ===== Token 统计 =====
    private val tokensTotal = AtomicLong(0)
    private val tokensToday = AtomicLong(0)
    private val tokensWeek = AtomicLong(0)
    private val tokensMonth = AtomicLong(0)

    private var todayEpochDayForTokens: Long = currentEpochDay()
    private var currentYearWeek: Int = currentWeekOfYear()
    private var currentYearMonth: Int = currentYearMonth()
    private val totalRequests = AtomicLong(0)
    private val successRequests = AtomicLong(0)
    private val totalLatencyMs = AtomicLong(0)

    // 当天计数（简单按日期切换重置）
    private val todayRequests = AtomicLong(0)
    private var todayEpochDay: Long = currentEpochDay()

    /** 记录一次请求结果 */
    fun record(success: Boolean, latencyMs: Long) {
        rolloverIfNewDay()
        totalRequests.incrementAndGet()
        todayRequests.incrementAndGet()
        if (success) successRequests.incrementAndGet()
        totalLatencyMs.addAndGet(max(0, latencyMs))
    }

    /** Token 计数记录 */
    fun recordTokens(count: Int) {
        if (count <= 0) return
        rolloverTokensIfBoundaryChanged()
        tokensTotal.addAndGet(count.toLong())
        tokensToday.addAndGet(count.toLong())
        tokensWeek.addAndGet(count.toLong())
        tokensMonth.addAndGet(count.toLong())
    }

    /** 生成当前快照（用于 UI 展示） */
    fun snapshot(): MetricsSnapshot {
        rolloverIfNewDay()
        rolloverTokensIfBoundaryChanged()
        val total = totalRequests.get()
        val success = successRequests.get()
        val latency = totalLatencyMs.get()
        val avgLatency = if (total > 0) latency / total else 0
        val successRate = if (total > 0) success.toDouble() / total.toDouble() else 0.0
        return MetricsSnapshot(
            today = todayRequests.get().toInt(),
            total = total.toInt(),
            successRate = successRate,
            averageLatencyMs = avgLatency.toInt(),
            tokensToday = tokensToday.get().toInt(),
            tokensWeek = tokensWeek.get().toInt(),
            tokensMonth = tokensMonth.get().toInt(),
            tokensTotal = tokensTotal.get().toInt()
        )
    }

    /** 手动重置（测试或调试用） */
    fun resetAll() {
        totalRequests.set(0)
        successRequests.set(0)
        totalLatencyMs.set(0)
        todayRequests.set(0)
        todayEpochDay = currentEpochDay()

        tokensTotal.set(0)
        tokensToday.set(0)
        tokensWeek.set(0)
        tokensMonth.set(0)
        todayEpochDayForTokens = currentEpochDay()
        currentYearWeek = currentWeekOfYear()
        currentYearMonth = currentYearMonth()
    }

    private fun rolloverIfNewDay() {
        val now = currentEpochDay()
        if (now != todayEpochDay) {
            todayEpochDay = now
            todayRequests.set(0)
        }
    }

    private fun rolloverTokensIfBoundaryChanged() {
        val day = currentEpochDay()
        if (day != todayEpochDayForTokens) {
            todayEpochDayForTokens = day
            tokensToday.set(0)
        }
        val week = currentWeekOfYear()
        if (week != currentYearWeek) {
            currentYearWeek = week
            tokensWeek.set(0)
        }
        val month = currentYearMonth()
        if (month != currentYearMonth) {
            currentYearMonth = month
            tokensMonth.set(0)
        }
    }

    private fun currentEpochDay(): Long = System.currentTimeMillis() / (24L * 60L * 60L * 1000L)
    private fun currentWeekOfYear(): Int {
        val wf = WeekFields.of(Locale.getDefault())
        val now = LocalDate.now()
        return now.get(wf.weekOfWeekBasedYear()) + now.year * 100
    }
    private fun currentYearMonth(): Int {
        val now = LocalDate.now()
        return now.year * 100 + now.monthValue
    }
}

/**
 * 指标快照
 */
 data class MetricsSnapshot(
    val today: Int,
    val total: Int,
    val successRate: Double,
    val averageLatencyMs: Int,
    val tokensToday: Int,
    val tokensWeek: Int,
    val tokensMonth: Int,
    val tokensTotal: Int,
)
