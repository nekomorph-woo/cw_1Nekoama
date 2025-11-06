package com.cw2.nekoama.platform.lifecycle

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.JsonMetricsStorage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import kotlinx.coroutines.runBlocking

/**
 * 指标系统初始化器
 * 在插件启动时初始化增强版指标收集器
 */
class MetricsInitializer : StartupActivity {
    override fun runActivity(project: Project) {
        // 在后台线程初始化指标收集器
        ApplicationManager.getApplication().executeOnPooledThread {
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
}