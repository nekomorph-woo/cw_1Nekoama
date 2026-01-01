package com.cw2.nekoama.infrastructure.storage.metrics

import com.cw2.nekoama.application.metrics.service.addRecordToHistoricalMetrics
import com.cw2.nekoama.application.metrics.service.filterByDateRange
import com.cw2.nekoama.application.metrics.service.query
import com.cw2.nekoama.domain.metrics.model.ActionRecord
import com.cw2.nekoama.domain.metrics.model.AggregatedMetrics
import com.cw2.nekoama.domain.metrics.model.HistoricalMetrics
import com.cw2.nekoama.domain.metrics.model.MetricsQuery
import com.cw2.nekoama.domain.metrics.model.TotalStats
import com.intellij.openapi.application.PathManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 指标数据持久化存储接口
 */
interface IMetricsStorage {
    suspend fun saveMetrics(metrics: HistoricalMetrics): Boolean
    suspend fun loadMetrics(): HistoricalMetrics?
    suspend fun addRecord(record: ActionRecord): Boolean
    suspend fun queryMetrics(query: MetricsQuery): List<AggregatedMetrics>
    suspend fun exportData(startDate: LocalDate, endDate: LocalDate): String?
    fun getStorageStats(): StorageStats
}

/**
 * 存储统计信息
 */
data class StorageStats(
    val totalRecords: Int,
    val fileSize: Long,
    val lastModified: Long,
    val dataVersion: Int,
    val isHealthy: Boolean
)

/**
 * JSON文件持久化存储实现
 */
class JsonMetricsStorage : IMetricsStorage {

    companion object {
        private const val METRICS_FILE_NAME = "nekoama_metrics.json"
        private const val BACKUP_FILE_NAME = "nekoama_metrics_backup.json"
        private const val MAX_RECENT_RECORDS = 1000
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private val metricsDir: File by lazy {
        val configPath = PathManager.getConfigDir().toString()
        val dir = File(configPath, "nekoama")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val metricsFile: File by lazy {
        File(metricsDir, METRICS_FILE_NAME)
    }

    private val backupFile: File by lazy {
        File(metricsDir, BACKUP_FILE_NAME)
    }

    private val memoryCache = ConcurrentHashMap<String, Any>()
    private val cacheLock = ReentrantReadWriteLock()

    override suspend fun saveMetrics(metrics: HistoricalMetrics): Boolean {
        return try {
            // 创建备份
            createBackup()

            // 序列化数据
            val jsonString = json.encodeToString(metrics)

            // 写入临时文件
            val tempFile = File(metricsDir, "${METRICS_FILE_NAME}.tmp")
            tempFile.writeText(jsonString, Charsets.UTF_8)

            // 原子性替换原文件
            Files.move(
                tempFile.toPath(),
                metricsFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )

            // 更新缓存
            cacheLock.write {
                memoryCache.clear()
                memoryCache["last_update"] = System.currentTimeMillis()
            }

            true
        } catch (e: Exception) {
            // 尝试恢复备份
            restoreFromBackup()
            false
        }
    }

    override suspend fun loadMetrics(): HistoricalMetrics? {
        return try {
            // 先检查缓存
            cacheLock.read {
                val cachedTime = memoryCache["last_update"] as? Long
                if (cachedTime != null && metricsFile.lastModified() <= cachedTime) {
                    return@read memoryCache["metrics"] as? HistoricalMetrics
                }
            }

            if (!metricsFile.exists()) {
                return createEmptyMetrics()
            }

            val jsonString = metricsFile.readText(Charsets.UTF_8)
            val metrics = json.decodeFromString<HistoricalMetrics>(jsonString)

            // 更新缓存
            cacheLock.write {
                memoryCache["metrics"] = metrics
                memoryCache["last_update"] = System.currentTimeMillis()
            }

            metrics
        } catch (e: Exception) {
            // 尝试从备份恢复
            tryRestoreFromBackup() ?: createEmptyMetrics()
        }
    }

    override suspend fun addRecord(record: ActionRecord): Boolean {
        return try {
            val currentMetrics = loadMetrics() ?: createEmptyMetrics()
            val updatedMetrics = addRecordToHistoricalMetrics(currentMetrics, record)
            saveMetrics(updatedMetrics)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun queryMetrics(query: MetricsQuery): List<AggregatedMetrics> {
        return try {
            val metrics = loadMetrics() ?: return emptyList()
            metrics.query(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun exportData(startDate: LocalDate, endDate: LocalDate): String? {
        return try {
            val metrics = loadMetrics() ?: return null
            val filteredMetrics = metrics.filterByDateRange(startDate, endDate)
            json.encodeToString(filteredMetrics)
        } catch (e: Exception) {
            null
        }
    }

    override fun getStorageStats(): StorageStats {
        return try {
            val exists = metricsFile.exists()
            val size = if (exists) metricsFile.length() else 0
            val modified = if (exists) metricsFile.lastModified() else 0

            // 简化版本，不调用suspend方法
            StorageStats(
                totalRecords = 0,
                fileSize = size,
                lastModified = modified,
                dataVersion = 1,
                isHealthy = exists && size > 0
            )
        } catch (e: Exception) {
            StorageStats(
                totalRecords = 0,
                fileSize = 0,
                lastModified = 0,
                dataVersion = 0,
                isHealthy = false
            )
        }
    }

    private fun createEmptyMetrics(): HistoricalMetrics {
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

    private fun createBackup() {
        if (metricsFile.exists()) {
            try {
                Files.copy(
                    metricsFile.toPath(),
                    backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: IOException) {
                // 备份失败不应该阻止主要操作
            }
        }
    }

    private fun restoreFromBackup(): Boolean {
        return try {
            if (backupFile.exists()) {
                Files.copy(
                    backupFile.toPath(),
                    metricsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun tryRestoreFromBackup(): HistoricalMetrics? {
        return if (restoreFromBackup()) {
            loadMetrics()
        } else {
            null
        }
    }
}

