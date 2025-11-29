package com.cw2.nekoama.core.reporting

import com.cw2.nekoama.ai.model.dependency.DependencyAnalysisResult
import com.cw2.nekoama.core.analysis.AnalysisResult
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.serialization.JsonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.cw2.nekoama.ai.model.dependency.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * 代码依赖分析报告生成器
 *
 * 生成包含AntV G6可视化组件的HTML报告，支持：
 * - 交互式依赖图谱
 * - 多维度数据展示
 * - 响应式设计
 * - 主题适配
 */
class DependencyReportGenerator {

    private val logger = NekoamaLogger
    private val jsonConfig = JsonConfig

    /**
     * 生成完整的HTML报告 (新版本 - 支持M2重构后的数据结构)
     */
    suspend fun generateReport(
        analysisResult: AnalysisResult,
        outputPath: Path
    ): ReportGenerationResult = withContext(Dispatchers.IO) {
        try {
            logger.info("ReportGeneration", "开始生成依赖分析报告: ${outputPath.fileName}")

            // 确保输出目录存在
            Files.createDirectories(outputPath.parent)

            // 读取HTML模板
            val template = loadHtmlTemplate()

            // 将新的AnalysisResult转换为前端需要的JSON格式
            val jsonData = convertAnalysisResultToJson(analysisResult)

            // 生成报告标题和元信息
            val reportTitle = generateReportTitle(analysisResult)
            val reportMetadata = generateReportMetadata(analysisResult)

            // 替换模板占位符
            val htmlContent = template
                .replace("__TITLE__", reportTitle)
                .replace("__METADATA__", reportMetadata)
                .replace("__DATA_PLACEHOLDER__", jsonData)
                .replace("__GENERATION_TIME__", getCurrentTimestamp())

            // 写入文件
            Files.writeString(
                outputPath,
                htmlContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            // 复制静态资源文件
            copyStaticResources(outputPath.parent)

            logger.info("ReportGeneration", "HTML报告生成完成: ${outputPath}")
            ReportGenerationResult(true, outputPath, "报告生成成功")

        } catch (e: Exception) {
            logger.error("ReportGeneration", "生成HTML报告失败", error = e)
            ReportGenerationResult(false, outputPath, "生成失败: ${e.message}", e)
        }
    }

    /**
     * 将新的AnalysisResult转换为前端需要的JSON格式
     */
    private suspend fun convertAnalysisResultToJson(analysisResult: AnalysisResult): String = withContext(Dispatchers.Default) {
        buildString {
            appendLine("{")

            // Project Info
            appendLine("  \"projectInfo\": {")
            appendLine("    \"name\": \"${analysisResult.projectInfo.name}\",")
            appendLine("    \"location\": \"${analysisResult.projectInfo.location}\",")
            appendLine("    \"totalFiles\": ${analysisResult.projectInfo.totalFiles},")
            appendLine("    \"totalClasses\": ${analysisResult.projectInfo.totalClasses},")
            appendLine("    \"totalMethods\": ${analysisResult.projectInfo.totalMethods},")
            appendLine("    \"analysisTime\": ${analysisResult.projectInfo.analysisTime}")
            appendLine("  },")

            // Class Graph
            appendLine("  \"classGraph\": {")
            appendLine("    \"nodes\": [")
            analysisResult.classGraph.nodes.forEachIndexed { index, node ->
                if (index > 0) appendLine(",")
                append("      {")
                append("\"id\": \"${node.id}\", ")
                append("\"name\": \"${node.name}\", ")
                append("\"packagePath\": \"${node.packagePath}\", ")
                append("\"complexity\": ${node.complexity}, ")
                append("\"isController\": ${node.isController}, ")
                append("\"isService\": ${node.isService}, ")
                val type = when {
                    node.isController -> "controller"
                    node.isService -> "service"
                    else -> "class"
                }
                append("\"type\": \"$type\"")
                append("}")
            }
            appendLine("],")
            appendLine("    \"edges\": [")
            analysisResult.classGraph.edges.forEachIndexed { index, edge ->
                if (index > 0) appendLine(",")
                append("      {")
                append("\"source\": \"${edge.source}\", ")
                append("\"target\": \"${edge.target}\", ")
                append("\"type\": \"${edge.type}\", ")
                append("\"strength\": ${edge.strength}")
                append("}")
            }
            appendLine("]")
            appendLine("  },")

            // Method Call Graph
            appendLine("  \"methodCallGraph\": {")
            appendLine("    \"nodes\": [")
            analysisResult.methodCallGraph.nodes.forEachIndexed { index, node ->
                if (index > 0) appendLine(",")
                append("      {")
                append("\"id\": \"${node.id}\", ")
                append("\"name\": \"${node.name}\", ")
                append("\"className\": \"${node.className}\", ")
                append("\"complexity\": ${node.complexity}, ")
                append("\"fanIn\": ${node.fanIn}, ")
                append("\"fanOut\": ${node.fanOut}, ")
                append("\"type\": \"method\"")
                append("}")
            }
            appendLine("],")
            appendLine("    \"edges\": [")
            analysisResult.methodCallGraph.edges.forEachIndexed { index, edge ->
                if (index > 0) appendLine(",")
                append("      {")
                append("\"source\": \"${edge.source}\", ")
                append("\"target\": \"${edge.target}\", ")
                append("\"type\": \"${edge.type}\", ")
                append("\"callType\": \"${edge.type}\", ")
                append("\"line\": 0")
                append("}")
            }
            appendLine("],")

            // Method calls mapping for call chain analysis
            appendLine("    \"methodCalls\": {")
            val methodCallsMap = analysisResult.methodCallGraph.edges.groupBy { it.source }
            methodCallsMap.entries.forEachIndexed { index, (source, edges) ->
                if (index > 0) appendLine(",")
                append("      \"${source}\": [")
                edges.forEachIndexed { edgeIndex, edge ->
                    if (edgeIndex > 0) appendLine(",")
                    append("        {")
                    append("\"toMethod\": \"${edge.target}\", ")
                    append("\"callType\": \"${edge.type}\", ")
                    append("\"line\": 0")
                    append("}")
                }
                append("]")
            }
            appendLine("    },")

            // Method call targets (fanIn stats)
            appendLine("    \"methodCallTargets\": {")
            val methodCallTargets = analysisResult.methodCallGraph.edges.groupBy { it.target }
            methodCallTargets.entries.forEachIndexed { index, (target, edges) ->
                if (index > 0) appendLine(",")
                append("      \"${target}\": ${edges.size}")
            }
            appendLine("    }")
            appendLine("  },")

            // Entry Points
            appendLine("  \"entryPoints\": [")
            analysisResult.entryPoints.forEachIndexed { index, entryPoint ->
                if (index > 0) appendLine(",")
                append("      {")
                append("\"className\": \"${entryPoint.className}\", ")
                append("\"methodName\": \"${entryPoint.methodName}\", ")
                append("\"entryType\": \"${entryPoint.entryType}\", ")
                append("\"annotations\": [${entryPoint.annotations.joinToString(", ") { "\"$it\"" }}], ")
                append("\"businessScenario\": \"${entryPoint.businessScenario}\", ")
                if (entryPoint.httpMapping != null) {
                    append("\"httpMapping\": \"${entryPoint.httpMapping}\", ")
                }
                append("\"parameters\": [")
                entryPoint.parameters.forEachIndexed { paramIndex, param ->
                    if (paramIndex > 0) append(", ")
                    append("{")
                    append("\"name\": \"${param.name}\", ")
                    append("\"type\": \"${param.type}\"")
                    append("}")
                }
                append("]")
                append("}")
            }
            appendLine("],")

            // Code Smells
            appendLine("  \"codeSmells\": [")
            analysisResult.codeSmells.forEachIndexed { index, smell ->
                if (index > 0) appendLine(",")
                append("      {")
                append("\"type\": \"${smell.type}\", ")
                append("\"element\": \"${smell.element}\", ")
                append("\"severity\": \"${smell.severity}\", ")
                append("\"description\": \"${smell.description}\"")
                append("}")
            }
            appendLine("],")

            // Complexity Metrics
            appendLine("  \"complexityMetrics\": {")
            analysisResult.complexityMetrics.entries.forEachIndexed { index, (key, metrics) ->
                if (index > 0) appendLine(",")
                append("    \"${key}\": {")
                append("\"cyclomaticComplexity\": ${metrics.cyclomaticComplexity}, ")
                append("\"cognitiveComplexity\": ${metrics.cognitiveComplexity}, ")
                append("\"linesOfCode\": ${metrics.linesOfCode}, ")
                append("\"parameters\": ${metrics.parameters}")
                append("}")
            }
            appendLine("  },")

            // Statistics
            appendLine("  \"statistics\": {")
            appendLine("    \"totalClasses\": ${analysisResult.stats.totalClasses},")
            appendLine("    \"totalMethods\": ${analysisResult.stats.totalMethods},")
            appendLine("    \"entryPointsCount\": ${analysisResult.stats.entryPointsCount},")
            appendLine("    \"totalComplexity\": ${analysisResult.stats.complexityStats.totalComplexity},")
            appendLine("    \"averageComplexity\": ${analysisResult.stats.complexityStats.averageComplexity},")
            appendLine("    \"highComplexityMethods\": ${analysisResult.stats.complexityStats.highComplexityMethods},")
            appendLine("    \"totalCodeSmells\": ${analysisResult.stats.codeSmellStats.totalSmells},")
            appendLine("    \"criticalCodeSmells\": ${analysisResult.stats.codeSmellStats.criticalSmells},")
            appendLine("    \"highCodeSmells\": ${analysisResult.stats.codeSmellStats.highSmells}")
            appendLine("  }")

            appendLine("}")
        }
    }

    /**
     * 生成报告标题 (新版本)
     */
    private fun generateReportTitle(analysisResult: AnalysisResult): String {
        return "${analysisResult.projectInfo.name} - 代码依赖分析报告"
    }

    /**
     * 生成报告元信息 (新版本)
     */
    private fun generateReportMetadata(analysisResult: AnalysisResult): String {
        return buildString {
            appendLine("项目名称: ${analysisResult.projectInfo.name}")
            appendLine("项目路径: ${analysisResult.projectInfo.location}")
            appendLine("总文件数: ${analysisResult.projectInfo.totalFiles}")
            appendLine("总类数: ${analysisResult.projectInfo.totalClasses}")
            appendLine("总方法数: ${analysisResult.projectInfo.totalMethods}")
            appendLine("分析耗时: ${analysisResult.projectInfo.analysisTime}ms")
            appendLine("入口点数: ${analysisResult.stats.entryPointsCount}")
            appendLine("平均复杂度: ${"%.2f".format(analysisResult.stats.complexityStats.averageComplexity)}")
            appendLine("高复杂度方法: ${analysisResult.stats.complexityStats.highComplexityMethods}")
            appendLine("代码问题总数: ${analysisResult.stats.codeSmellStats.totalSmells}")
        }
    }

    /**
     * 生成完整的HTML报告 (旧版本 - 保持向后兼容)
     */
    suspend fun generateLegacyReport(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path
    ): ReportGenerationResult = withContext(Dispatchers.IO) {
        try {
            logger.info("ReportGeneration", "开始生成依赖分析报告: ${outputPath.fileName}")

            // 确保输出目录存在
            Files.createDirectories(outputPath.parent)

            // 读取HTML模板
            val template = loadHtmlTemplate()

            // 验证入口点数据的完整性
            validateEntryPointData(analysisResult)

            // 序列化分析数据为JSON
            val jsonData = jsonConfig.json.encodeToString(
                DependencyAnalysisResult.serializer(),
                analysisResult
            )

            // 生成报告标题和元信息
            val reportTitle = generateReportTitle(analysisResult)
            val reportMetadata = generateReportMetadata(analysisResult)

            // 替换模板占位符
            val htmlContent = template
                .replace("__TITLE__", reportTitle)
                .replace("__METADATA__", reportMetadata)
                .replace("__DATA_PLACEHOLDER__", jsonData)
                .replace("__GENERATION_TIME__", getCurrentTimestamp())

            // 写入文件
            Files.writeString(
                outputPath,
                htmlContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            // 复制静态资源文件
            copyStaticResources(outputPath.parent)

            logger.info("ReportGeneration", "依赖分析报告生成完成: ${outputPath.toAbsolutePath()}")

            ReportGenerationResult(
                success = true,
                outputPath = outputPath,
                message = "报告生成成功"
            )

        } catch (e: Exception) {
            logger.error("ReportGeneration", "生成依赖分析报告失败", error = e)
            ReportGenerationResult(
                success = false,
                outputPath = outputPath,
                message = "报告生成失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 加载HTML模板
     */
    private fun loadHtmlTemplate(): String {
        val templateResource = this::class.java.classLoader.getResourceAsStream(
            "templates/reports/dependency-analysis-template.html"
        )

        return templateResource?.use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } ?: throw IllegalStateException("找不到HTML模板文件: dependency-analysis-template.html")
    }

    /**
     * 生成报告标题
     */
    private fun generateReportTitle(result: DependencyAnalysisResult): String {
        return "代码依赖分析报告 - ${result.metadata.projectName}"
    }

    /**
     * 生成报告元信息
     */
    private fun generateReportMetadata(result: DependencyAnalysisResult): String {
        return buildString {
            appendLine("项目名称: ${result.metadata.projectName}")
            appendLine("模块名称: ${result.metadata.moduleName}")
            appendLine("分析时间: ${result.metadata.analysisTime}")
            appendLine("分析范围: ${result.metadata.scope.rootPackage}")
            appendLine("总包数: ${result.metadata.statistics.totalPackages}")
            appendLine("总类数: ${result.metadata.statistics.totalClasses}")
            appendLine("总方法数: ${result.metadata.statistics.totalMethods}")
            appendLine("调用边数: ${result.metadata.statistics.totalCallEdges}")
        }
    }

    /**
     * 获取当前时间戳
     */
    private fun getCurrentTimestamp(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')} " +
                "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}:${now.second.toString().padStart(2, '0')}"
    }

    /**
     * 复制静态资源文件
     */
    private fun copyStaticResources(outputDir: Path) {
        val resourcesDir = outputDir.resolve("static")
        Files.createDirectories(resourcesDir)

        // 复制CSS文件
        copyResourceFile(
            "static/css/dependency-analysis.css",
            resourcesDir.resolve("css").resolve("dependency-analysis.css")
        )

        // 复制JavaScript文件
        copyResourceFile(
            "static/js/g6-visualizer.js",
            resourcesDir.resolve("js").resolve("g6-visualizer.js")
        )

        logger.debug("ReportGeneration", "静态资源文件复制完成: ${resourcesDir.toAbsolutePath()}")
    }

    /**
     * 复制单个资源文件
     */
    private fun copyResourceFile(resourcePath: String, targetPath: Path) {
        try {
            val resourceStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
            if (resourceStream != null) {
                Files.createDirectories(targetPath.parent)
                Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
                logger.debug("ReportGeneration", "资源文件复制成功: $resourcePath -> $targetPath")
            } else {
                logger.warn("ReportGeneration", "找不到资源文件: $resourcePath")
                // 创建一个空的占位文件，避免HTML加载失败
                createFallbackResource(targetPath, resourcePath)
            }
        } catch (e: Exception) {
            logger.warn("ReportGeneration", "复制资源文件失败: $resourcePath", error = e)
            // 创建一个空的占位文件，避免HTML加载失败
            createFallbackResource(targetPath, resourcePath)
        }
    }

    /**
     * 创建备用资源文件
     */
    private fun createFallbackResource(targetPath: Path, originalPath: String) {
        try {
            Files.createDirectories(targetPath.parent)
            val fallbackContent = when {
                originalPath.endsWith(".css") -> """
                    /* 备用CSS文件 - 原文件缺失: $originalPath */
                    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; }
                    .error { color: #ff6b6b; text-align: center; padding: 20px; }
                """.trimIndent()
                originalPath.endsWith(".js") -> """
                    // 备用JavaScript文件 - 原文件缺失: $originalPath
                    console.warn('JavaScript文件缺失: $originalPath');

                    // 提供基本的占位功能
                    window.DependencyVisualizer = window.DependencyVisualizer || {
                        init: function() {
                            console.warn('使用备用可视化器，功能受限');
                        }
                    };
                """.trimIndent()
                else -> "/* 备用资源文件: $originalPath */"
            }

            Files.writeString(targetPath, fallbackContent, StandardCharsets.UTF_8)
            logger.info("ReportGeneration", "创建备用资源文件: $targetPath")
        } catch (e: Exception) {
            logger.error("ReportGeneration", "创建备用资源文件失败: $targetPath", error = e)
        }
    }

    /**
     * 生成迷你报告（用于快速预览）
     */
    suspend fun generateMiniReport(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path
    ): ReportGenerationResult = withContext(Dispatchers.IO) {
        try {
            val miniTemplate = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>__TITLE__</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
                        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 30px; }
                        .metric-card { background: #f8f9fa; padding: 15px; border-radius: 6px; border-left: 4px solid #007bff; }
                        .metric-value { font-size: 24px; font-weight: bold; color: #007bff; }
                        .metric-label { color: #666; margin-top: 5px; }
                        .problems { margin-top: 20px; }
                        .problem-item { padding: 10px; margin: 5px 0; background: #fff3cd; border-left: 4px solid #ffc107; border-radius: 4px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>__TITLE__</h1>
                            <p>生成时间: __GENERATION_TIME__</p>
                        </div>

                        <div class="metrics">
                            __METRICS_CARDS__
                        </div>

                        <div class="problems">
                            <h3>主要问题</h3>
                            __PROBLEMS_LIST__
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val metricsCards = generateMetricsCards(analysisResult)
            val problemsList = generateProblemsList(analysisResult)

            val htmlContent = miniTemplate
                .replace("__TITLE__", generateReportTitle(analysisResult))
                .replace("__GENERATION_TIME__", getCurrentTimestamp())
                .replace("__METRICS_CARDS__", metricsCards)
                .replace("__PROBLEMS_LIST__", problemsList)

            Files.writeString(
                outputPath,
                htmlContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            ReportGenerationResult(
                success = true,
                outputPath = outputPath,
                message = "迷你报告生成成功"
            )

        } catch (e: Exception) {
            logger.error("ReportGeneration", "生成迷你报告失败", error = e)
            ReportGenerationResult(
                success = false,
                outputPath = outputPath,
                message = "迷你报告生成失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 生成指标卡片
     */
    private fun generateMetricsCards(result: DependencyAnalysisResult): String {
        return buildString {
            appendLine("<div class=\"metric-card\">")
            appendLine("<div class=\"metric-value\">${result.metadata.statistics.totalClasses}</div>")
            appendLine("<div class=\"metric-label\">总类数</div>")
            appendLine("</div>")

            appendLine("<div class=\"metric-card\">")
            appendLine("<div class=\"metric-value\">${result.metadata.statistics.totalPackages}</div>")
            appendLine("<div class=\"metric-label\">总包数</div>")
            appendLine("</div>")

            appendLine("<div class=\"metric-card\">")
            appendLine("<div class=\"metric-value\">${result.metadata.statistics.totalMethods}</div>")
            appendLine("<div class=\"metric-label\">总方法数</div>")
            appendLine("</div>")

            appendLine("<div class=\"metric-card\">")
            appendLine("<div class=\"metric-value\">${result.codeSmells.size}</div>")
            appendLine("<div class=\"metric-label\">代码问题</div>")
            appendLine("</div>")

            val highComplexityClasses = result.complexityMetrics.values.count {
                it.cyclomaticComplexity > 10
            }
            appendLine("<div class=\"metric-card\">")
            appendLine("<div class=\"metric-value\">$highComplexityClasses</div>")
            appendLine("<div class=\"metric-label\">高复杂度类</div>")
            appendLine("</div>")
        }
    }

    /**
     * 生成问题列表
     */
    private fun generateProblemsList(result: DependencyAnalysisResult): String {
        val criticalProblems = result.codeSmells.filter {
            it.severity == Severity.CRITICAL || it.severity == Severity.HIGH
        }.take(10)

        return if (criticalProblems.isEmpty()) {
            "<p>暂未发现严重问题。</p>"
        } else {
            buildString {
                criticalProblems.forEach { smell ->
                    appendLine("<div class=\"problem-item\">")
                    appendLine("<strong>[${smell.severity}] ${smell.type}</strong>")
                    appendLine("<br>${smell.className}${smell.methodName?.let { ".$it" } ?: ""}")
                    appendLine("<br>${smell.description}")
                    appendLine("</div>")
                }
            }
        }
    }

    /**
     * 验证入口点数据的完整性
     */
    private fun validateEntryPointData(result: DependencyAnalysisResult) {
        val businessEntryCount = result.businessEntryPoints.size
        val methodEntryCount = result.methods.count { it.tags.isEntryPoint }

        logger.info("DependencyReportGenerator", "入口点数据验证:")
        logger.info("DependencyReportGenerator", "  BusinessEntryPoints: $businessEntryCount 个")
        logger.info("DependencyReportGenerator", "  Method.isEntryPoint: $methodEntryCount 个")

        // 按类型统计入口点
        val entryPointsByType = result.businessEntryPoints.groupBy { it.entryType }
        entryPointsByType.forEach { (type, entries) ->
            logger.info("DependencyReportGenerator", "  ${type.name}: ${entries.size} 个")
        }

        if (methodEntryCount < businessEntryCount) {
            logger.warn("DependencyReportGenerator",
                "入口点匹配不完整：$methodEntryCount/$businessEntryCount")

            // 记录未匹配的业务入口点
            val matchedMethodSignatures = result.methods
                .filter { it.tags.isEntryPoint }
                .map { "${it.className}.${it.name}" }
                .toSet()

            val unmatchedEntryPoints = result.businessEntryPoints
                .filter { entry ->
                    !matchedMethodSignatures.contains("${entry.className}.${entry.methodName}")
                }

            if (unmatchedEntryPoints.isNotEmpty()) {
                logger.warn("DependencyReportGenerator",
                    "未匹配的业务入口点示例：${unmatchedEntryPoints.take(5).map { "${it.className}.${it.methodName}" }}")
            }
        } else {
            logger.info("DependencyReportGenerator", "入口点匹配完整 ✓")
        }

        // 验证方法的入口点标签
        val totalMethodCount = result.methods.size
        val entryPointRatio = if (totalMethodCount > 0) {
            (methodEntryCount.toDouble() / totalMethodCount * 100).toInt()
        } else 0

        logger.info("DependencyReportGenerator", "入口方法占总方法比例: ${entryPointRatio}% ($methodEntryCount/$totalMethodCount)")
    }
}

/**
 * 报告生成结果
 */
data class ReportGenerationResult(
    val success: Boolean,
    val outputPath: Path,
    val message: String,
    val error: Throwable? = null
)