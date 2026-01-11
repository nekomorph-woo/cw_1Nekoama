package com.cw2.nekoama.infrastructure.statistics

import com.cw2.nekoama.domain.statistics.model.MonthlyTokenData
import com.cw2.nekoama.domain.statistics.model.UsageStatistics
import com.cw2.nekoama.domain.statistics.repository.StatisticsRepository
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 统计数据状态（用于持久化）
 */
@Service(Service.Level.PROJECT)
@State(name = "StatisticsData", storages = [Storage("nekoama_statistics.xml")])
class StatisticsData : PersistentStateComponent<StatisticsData> {
    var namingCount: Int = 0
    var commentCount: Int = 0
    var customGenerateCount: Int = 0
    var lastUpdated: Long = 0L
    var tokenHistoryJson: String = ""
    var totalTokens: Int = 0

    override fun getState(): StatisticsData = this
    override fun loadState(state: StatisticsData) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

/**
 * 基于 PersistentStateComponent 的统计持久化实现
 *
 * 存储策略：
 * 1. 使用统计：通过 StatisticsData 持久化
 * 2. Token 历史：JSON 序列化存储在 StatisticsData 中
 */
@Service(Service.Level.PROJECT)
class PropertiesStatisticsRepository(
    private val project: Project
) : StatisticsRepository {

    private val state: StatisticsData
        get() = project.getService(StatisticsData::class.java)

    // JSON 序列化配置
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = false
    }

    // ========== 使用次数统计 ==========

    override fun saveUsageStatistics(statistics: UsageStatistics) {
        state.namingCount = statistics.namingCount
        state.commentCount = statistics.commentCount
        state.customGenerateCount = statistics.customGenerateCount
        state.lastUpdated = statistics.lastUpdated
    }

    override fun loadUsageStatistics(): UsageStatistics {
        return UsageStatistics(
            namingCount = state.namingCount,
            commentCount = state.commentCount,
            customGenerateCount = state.customGenerateCount,
            lastUpdated = state.lastUpdated
        )
    }

    // ========== Token 统计 ==========

    override fun saveTokenHistory(history: Map<String, MonthlyTokenData>) {
        try {
            val jsonStr = json.encodeToString(history)
            state.tokenHistoryJson = jsonStr
        } catch (e: Exception) {
            NekoamaLogger.error("StatisticsRepository", "保存 Token 历史失败: ${e.message}")
        }
    }

    override fun loadTokenHistory(): Map<String, MonthlyTokenData> {
        val jsonStr = state.tokenHistoryJson
        return if (jsonStr.isNullOrBlank()) {
            emptyMap()
        } else {
            try {
                json.decodeFromString<Map<String, MonthlyTokenData>>(jsonStr)
            } catch (e: Exception) {
                NekoamaLogger.error("StatisticsRepository", "加载 Token 历史失败: ${e.message}")
                emptyMap()
            }
        }
    }

    override fun getTotalTokens(): Int {
        return state.totalTokens
    }

    override fun saveTotalTokens(total: Int) {
        state.totalTokens = total
    }
}
