package com.cw2.nekoama.platform.lifecycle

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.JsonMetricsStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.runBlocking

/**
 * 指标系统初始化器
 * 在项目完全可用时初始化增强版指标收集器
 */
class MetricsInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            runBlocking {
                EnhancedMetricsCollector.initialize(JsonMetricsStorage())
            }
        } catch (e: Exception) {
            // 初始化失败不应该阻止插件启动
            com.cw2.nekoama.core.logging.NekoamaLogger.error("METRICS_INIT", "指标系统初始化失败: ${e.message}")
        }
    }
}