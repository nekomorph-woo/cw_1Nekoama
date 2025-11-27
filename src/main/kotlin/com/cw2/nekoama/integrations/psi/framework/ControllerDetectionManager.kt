package com.cw2.nekoama.integrations.psi.framework

import com.cw2.nekoama.integrations.psi.framework.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project

/**
 * 检测统计数据
 */
data class DetectionStats(
    val controllersFound: Int,
    val entryPointsFound: Int,
    val detectionTimeMs: Long,
    val confidence: Double,
    val error: String? = null
)

/**
 * 检测结果
 */
data class DetectionResult(
    val entryPoints: List<BusinessEntryPoint>,
    val detectionStats: Map<String, DetectionStats>,
    val totalTimeMs: Long = 0
)

/**
 * Controller检测管理器
 * 协调多个框架检测器，实现智能降级策略
 */
class ControllerDetectionManager(private val project: Project) {

    private val logger = com.cw2.nekoama.core.logging.NekoamaLogger

    // 框架检测器列表，按置信度从高到低排序
    private val detectors = listOf(
        SpringWebDetector(project),
        JaxRsDetector(project),
        GenericWebDetector(project)
    ).sortedByDescending { it.getDetectionConfidence() }

    /**
     * 检测所有Controller和HTTP入口点
     */
    fun detectAllControllers(): DetectionResult {
        val startTime = System.currentTimeMillis()
        val allEntryPoints = mutableListOf<BusinessEntryPoint>()
        val detectionStats = mutableMapOf<String, DetectionStats>()

        try {
            val scope = GlobalSearchScope.projectScope(project)
            logger.info("ControllerDetectionManager", "开始Controller检测，共${detectors.size}个检测器")

            for (detector in detectors) {
                val detectorStartTime = System.currentTimeMillis()
                var stats: DetectionStats

                try {
                    logger.info("ControllerDetectionManager", "使用${detector.getFrameworkName()}检测器开始检测")

                    // 检测Controller类
                    val controllers = detector.detectControllers(scope)
                    logger.info("ControllerDetectionManager", "${detector.getFrameworkName()}检测到${controllers.size}个Controller类")

                    // 提取HTTP入口点
                    val entryPoints = controllers.flatMap { controller ->
                        extractEntryPointsFromController(controller, detector)
                    }

                    allEntryPoints.addAll(entryPoints)

                    val detectionTime = System.currentTimeMillis() - detectorStartTime
                    stats = DetectionStats(
                        controllersFound = controllers.size,
                        entryPointsFound = entryPoints.size,
                        detectionTimeMs = detectionTime,
                        confidence = detector.getDetectionConfidence()
                    )

                    logger.info("ControllerDetectionManager",
                        "${detector.getFrameworkName()}检测完成: ${controllers.size}个控制器, ${entryPoints.size}个入口点, 耗时${detectionTime}ms, 置信度${detector.getDetectionConfidence()}")

                } catch (e: Exception) {
                    val detectionTime = System.currentTimeMillis() - detectorStartTime
                    logger.error("ControllerDetectionManager",
                        "${detector.getFrameworkName()}检测失败", error = e)

                    stats = DetectionStats(
                        controllersFound = 0,
                        entryPointsFound = 0,
                        detectionTimeMs = detectionTime,
                        confidence = 0.0,
                        error = e.message
                    )
                }

                detectionStats[detector.getFrameworkName()] = stats
            }

            // 去重：相同类+方法名的只保留置信度最高的
            val uniqueEntryPoints = deduplicateEntryPoints(allEntryPoints)

            val totalTime = System.currentTimeMillis() - startTime
            logger.info("ControllerDetectionManager", "Controller检测完成: 总共${uniqueEntryPoints.size}个入口点, 总耗时${totalTime}ms")

            return DetectionResult(
                entryPoints = uniqueEntryPoints,
                detectionStats = detectionStats,
                totalTimeMs = totalTime
            )

        } catch (e: Exception) {
            logger.error("ControllerDetectionManager", "Controller检测过程中发生异常", error = e)
            return DetectionResult(
                entryPoints = emptyList(),
                detectionStats = detectionStats,
                totalTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * 从单个Controller类中提取所有入口点
     */
    private fun extractEntryPointsFromController(
        controller: PsiClass,
        detector: FrameworkDetector
    ): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        try {
            controller.methods.forEach { method ->
                val mapping = detector.extractHttpMapping(method)
                if (mapping != null) {
                    val entryPoint = detector.createBusinessEntryPoint(controller, method, mapping)
                    entryPoints.add(entryPoint)
                }
            }
        } catch (e: Exception) {
            logger.error("ControllerDetectionManager",
                "从Controller ${controller.qualifiedName} 提取入口点失败", error = e)
        }

        return entryPoints
    }

    /**
     * 去重：相同类+方法名的只保留置信度最高的
     */
    private fun deduplicateEntryPoints(entryPoints: List<BusinessEntryPoint>): List<BusinessEntryPoint> {
        return entryPoints
            .groupBy { "${it.className}.${it.methodName}" }
            .mapNotNull { (_, entries) ->
                entries.maxByOrNull { it.confidence }
            }
    }

    /**
     * 获取检测统计摘要
     */
    fun getDetectionSummary(result: DetectionResult): String {
        val summary = StringBuilder()
        summary.appendLine("=== Controller检测摘要 ===")
        summary.appendLine("总入口点数: ${result.entryPoints.size}")
        summary.appendLine("总检测时间: ${result.totalTimeMs}ms")
        summary.appendLine()

        result.detectionStats.forEach { (framework, stats) ->
            summary.appendLine("${framework}:")
            summary.appendLine("  - Controller数量: ${stats.controllersFound}")
            summary.appendLine("  - 入口点数量: ${stats.entryPointsFound}")
            summary.appendLine("  - 检测耗时: ${stats.detectionTimeMs}ms")
            summary.appendLine("  - 置信度: ${stats.confidence}")
            if (stats.error != null) {
                summary.appendLine("  - 错误: ${stats.error}")
            }
            summary.appendLine()
        }

        return summary.toString()
    }

    /**
     * 按框架分组入口点
     */
    fun groupEntryPointsByFramework(result: DetectionResult): Map<String, List<BusinessEntryPoint>> {
        return result.entryPoints.groupBy { it.detectedBy }
    }

    /**
     * 按HTTP方法分组入口点
     */
    fun groupEntryPointsByHttpMethod(result: DetectionResult): Map<String, List<BusinessEntryPoint>> {
        return result.entryPoints.groupBy { entryPoint ->
            try {
                val mapping = entryPoint.httpMapping ?: "UNKNOWN"
                mapping.split(" ").firstOrNull() ?: "UNKNOWN"
            } catch (e: Exception) {
                "UNKNOWN"
            }
        }
    }

    /**
     * 按业务场景分组入口点
     */
    fun groupEntryPointsByScenario(result: DetectionResult): Map<String, List<BusinessEntryPoint>> {
        return result.entryPoints.groupBy { it.businessScenario }
    }

    /**
     * 获取检测质量评估
     */
    fun getDetectionQuality(result: DetectionResult): Map<String, Any> {
        val totalControllers = result.detectionStats.values.sumOf { it.controllersFound }
        val totalEntryPoints = result.entryPoints.size
        val averageConfidence = result.detectionStats.values.mapNotNull { stats ->
            if (stats.controllersFound > 0) stats.confidence else null
        }.average()

        return mapOf(
            "totalControllers" to totalControllers,
            "totalEntryPoints" to totalEntryPoints,
            "totalDetectionTime" to result.totalTimeMs,
            "averageConfidence" to averageConfidence,
            "frameworkCount" to result.detectionStats.size,
            "successfulDetections" to result.detectionStats.values.count { it.error == null },
            "qualityScore" to calculateQualityScore(result)
        )
    }

    /**
     * 计算检测质量分数 (0-100)
     */
    private fun calculateQualityScore(result: DetectionResult): Int {
        var score = 0

        // 入口点数量评分 (30分)
        val entryPointCount = result.entryPoints.size
        when {
            entryPointCount == 0 -> score += 0
            entryPointCount < 10 -> score += 10
            entryPointCount < 50 -> score += 20
            else -> score += 30
        }

        // 检测器成功率评分 (30分)
        val successfulDetections = result.detectionStats.values.count { it.error == null }
        score += (successfulDetections * 10).coerceAtMost(30)

        // 平均置信度评分 (20分)
        val avgConfidence = result.detectionStats.values.mapNotNull { stats ->
            if (stats.controllersFound > 0) stats.confidence else null
        }.average()
        score += (avgConfidence * 20).toInt().coerceAtMost(20)

        // 检测时间评分 (20分)
        val totalTime = result.totalTimeMs
        when {
            totalTime < 1000 -> score += 20
            totalTime < 5000 -> score += 15
            totalTime < 10000 -> score += 10
            totalTime < 30000 -> score += 5
            else -> score += 0
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 验证检测结果的完整性
     */
    fun validateDetectionResult(result: DetectionResult): List<String> {
        val issues = mutableListOf<String>()

        // 检查是否有任何入口点
        if (result.entryPoints.isEmpty()) {
            issues.add("未检测到任何入口点")
        }

        // 检查是否有检测失败
        val failedDetections = result.detectionStats.filter { it.value.error != null }
        if (failedDetections.isNotEmpty()) {
            issues.addAll(failedDetections.map { (framework, stats) ->
                "${framework}检测失败: ${stats.error}"
            })
        }

        // 检查数据一致性
        val totalStatsControllers = result.detectionStats.values.sumOf { it.controllersFound }
        val totalStatsEntryPoints = result.detectionStats.values.sumOf { it.entryPointsFound }

        if (totalStatsEntryPoints != result.entryPoints.size) {
            issues.add("数据不一致: 统计入口点数(${totalStatsEntryPoints})与实际入口点数(${result.entryPoints.size})不匹配")
        }

        return issues
    }

    /**
     * 获取推荐的检测器配置
     */
    fun getRecommendedConfiguration(): Map<String, Any> {
        return mapOf(
            "recommendedDetectorOrder" to detectors.map { it.getFrameworkName() },
            "maxDetectionTime" to 30000, // 30秒
            "minConfidence" to 0.5,
            "enableParallelDetection" to true,
            "enableCaching" to true
        )
    }
}