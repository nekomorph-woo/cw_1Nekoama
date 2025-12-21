package com.cw2.nekoama.shared.lifecycle

import com.cw2.nekoama.application.metrics.service.MetricsCollector
import com.cw2.nekoama.infra.storage.metrics.JsonMetricsStorage
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 指标系统初始化器
 * 在项目完全可用时初始化增强版指标收集器
 */
class MetricsInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            MetricsCollector.initialize(JsonMetricsStorage())
        } catch (e: Exception) {
            // 初始化失败不应该阻止插件启动
            NekoamaLogger.error("METRICS_INIT", "指标系统初始化失败: ${e.message}")
        }
    }
}