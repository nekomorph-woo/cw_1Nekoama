package com.cw2.nekoama.core.reporting

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Markdown报告生成器
 *
 * 生成结构化的代码依赖分析Markdown报告，支持：
 * - 项目概览和统计信息
 * - 代码质量分析
 * - 依赖关系分析
 * - 重构建议
 * - 问题排行榜
 */
class MarkdownReportGenerator {

    private val logger = NekoamaLogger

    /**
     * 生成完整的Markdown报告
     */
    suspend fun generateReport(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path
    ): MarkdownExportResult = withContext(Dispatchers.IO) {
        try {
            logger.info("MarkdownGeneration", "开始生成Markdown报告: ${outputPath.fileName}")

            // 确保输出目录存在
            Files.createDirectories(outputPath.parent)

            // 生成报告内容
            val markdownContent = buildMarkdownReport(analysisResult)

            // 写入文件
            Files.writeString(
                outputPath,
                markdownContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            logger.info("MarkdownGeneration", "Markdown报告生成完成: ${outputPath.toAbsolutePath()}")

            MarkdownExportResult(
                success = true,
                outputPath = outputPath,
                fileSize = Files.size(outputPath),
                message = "Markdown报告生成成功"
            )

        } catch (e: Exception) {
            logger.error("MarkdownGeneration", "生成Markdown报告失败", error = e)
            MarkdownExportResult(
                success = false,
                outputPath = outputPath,
                message = "Markdown报告生成失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 生成简化版Markdown报告
     */
    suspend fun generateSummaryReport(
        analysisResult: DependencyAnalysisResult,
        outputPath: Path
    ): MarkdownExportResult = withContext(Dispatchers.IO) {
        try {
            logger.info("MarkdownGeneration", "开始生成简化Markdown报告: ${outputPath.fileName}")

            Files.createDirectories(outputPath.parent)

            val summaryContent = buildSummaryReport(analysisResult)

            Files.writeString(
                outputPath,
                summaryContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            MarkdownExportResult(
                success = true,
                outputPath = outputPath,
                fileSize = Files.size(outputPath),
                message = "简化Markdown报告生成成功"
            )

        } catch (e: Exception) {
            logger.error("MarkdownGeneration", "生成简化Markdown报告失败", error = e)
            MarkdownExportResult(
                success = false,
                outputPath = outputPath,
                message = "简化Markdown报告生成失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 构建完整的Markdown报告
     */
    private fun buildMarkdownReport(result: DependencyAnalysisResult): String {
        return buildString {
            // 报告头部
            appendLine("# 代码依赖分析报告")
            appendLine()

            // 项目概览
            appendProjectOverview(result)

            // 统计概览
            appendStatisticsOverview(result)

            // 代码质量分析
            appendCodeQualityAnalysis(result)

            // 依赖关系分析
            appendDependencyAnalysis(result)

            // POJO使用分析
            appendPojoUsageAnalysis(result)

            // 业务场景分析
            appendBusinessScenarioAnalysis(result)

            // 问题排行榜
            appendProblemRanking(result)

            // 重构建议
            appendRefactoringSuggestions(result)

            // 详细分析数据
            appendDetailedAnalysis(result)

            // 报告尾部
            appendReportFooter()
        }
    }

    /**
     * 构建简化版报告
     */
    private fun buildSummaryReport(result: DependencyAnalysisResult): String {
        return buildString {
            appendLine("# 代码依赖分析摘要")
            appendLine()

            appendProjectOverview(result)
            appendStatisticsOverview(result)
            appendTopIssues(result)
            appendRecommendations(result)
            appendReportFooter()
        }
    }

    /**
     * 添加项目概览
     */
    private fun StringBuilder.appendProjectOverview(result: DependencyAnalysisResult) {
        appendLine("## 项目概览")
        appendLine()
        appendLine("| 项目信息 | 内容 |")
        appendLine("|---------|------|")
        appendLine("| 项目名称 | ${result.metadata.projectName} |")
        appendLine("| 模块名称 | ${result.metadata.moduleName} |")
        appendLine("| 分析时间 | ${result.metadata.analysisTime} |")
        appendLine("| 分析范围 | ${result.metadata.scope.rootPackage} |")
        appendLine("| 最大深度 | ${result.metadata.scope.maxDepth} |")
        appendLine()
    }

    /**
     * 添加统计概览
     */
    private fun StringBuilder.appendStatisticsOverview(result: DependencyAnalysisResult) {
        appendLine("## 统计概览")
        appendLine()

        val stats = result.metadata.statistics
        appendLine("| 统计项 | 数量 |")
        appendLine("|-------|------|")
        appendLine("| 总包数 | ${stats.totalPackages} |")
        appendLine("| 总类数 | ${stats.totalClasses} |")
        appendLine("| 总方法数 | ${stats.totalMethods} |")
        appendLine("| 调用关系数 | ${stats.totalCallEdges} |")
        appendLine("| 代码问题数 | ${result.codeSmells.size} |")
        appendLine("| 业务场景数 | ${result.sceneDefinitions.size} |")
        appendLine("| POJO数量 | ${result.pojos.size} |")
        appendLine()

        // 添加复杂度分布
        appendComplexityDistribution(result)
    }

    /**
     * 添加复杂度分布
     */
    private fun StringBuilder.appendComplexityDistribution(result: DependencyAnalysisResult) {
        appendLine("### 复杂度分布")
        appendLine()

        val complexityDistribution = calculateComplexityDistribution(result)

        appendLine("| 复杂度等级 | 类数量 | 占比 |")
        appendLine("|-----------|--------|------|")

        val totalClasses = result.classes.size.toDouble()
        complexityDistribution.forEach { (level, count) ->
            val percentage = if (totalClasses > 0) (count / totalClasses * 100).toInt() else 0
            appendLine("| ${level.description} | $count | ${percentage}% |")
        }
        appendLine()
    }

    /**
     * 添加代码质量分析
     */
    private fun StringBuilder.appendCodeQualityAnalysis(result: DependencyAnalysisResult) {
        appendLine("## 代码质量分析")
        appendLine()

        // 整体质量评分
        val qualityScore = calculateQualityScore(result)
        appendLine("### 整体质量评分")
        appendLine()
        appendLine("**质量评分: ${qualityScore.score}/100**")
        appendLine()
        appendLine("${qualityScore.description}")
        appendLine()

        // 代码坏味道分析
        appendCodeSmellsAnalysis(result)

        // 复杂度热点分析
        appendComplexityHotspots(result)

        // 耦合度分析
        appendCouplingAnalysis(result)
    }

    /**
     * 添加代码坏味道分析
     */
    private fun StringBuilder.appendCodeSmellsAnalysis(result: DependencyAnalysisResult) {
        appendLine("### 代码坏味道分析")
        appendLine()

        val smellsByType = result.codeSmells.groupBy { it.type }

        appendLine("| 坏味道类型 | 数量 | 严重程度分布 |")
        appendLine("|-----------|------|---------------|")

        smellsByType.forEach { (type, smells) ->
            val severityDistribution = smells.groupBy { it.severity }
                .map { (severity, list) -> "${severity.name}(${list.size})" }
                .joinToString(", ")

            appendLine("| ${type.name.replace('_', ' ')} | ${smells.size} | $severityDistribution |")
        }
        appendLine()

        // 严重问题详情
        val criticalIssues = result.codeSmells.filter {
            it.severity == Severity.CRITICAL || it.severity == Severity.HIGH
        }.take(10)

        if (criticalIssues.isNotEmpty()) {
            appendLine("#### 严重问题详情")
            appendLine()
            criticalIssues.forEach { smell ->
                appendLine("- **[${smell.severity}] ${smell.type.name.replace('_', ' ')}**")
                appendLine("  - 位置: `${smell.className}${smell.methodName?.let { ".$it" } ?: ""}`")
                appendLine("  - 描述: ${smell.description}")
                appendLine("  - 文件: ${smell.location.filePath}:${smell.location.lineNumber}")
                appendLine()
            }
        }
    }

    /**
     * 添加复杂度热点
     */
    private fun StringBuilder.appendComplexityHotspots(result: DependencyAnalysisResult) {
        appendLine("### 复杂度热点")
        appendLine()

        // 最高复杂度的类
        val highComplexityClasses = result.classes
            .sortedByDescending { it.metrics.complexityScore }
            .take(10)

        appendLine("#### 高复杂度类 TOP 10")
        appendLine()
        appendLine("| 排名 | 类名 | 复杂度评分 | 方法数 | 代码行数 | 重构优先级 |")
        appendLine("|------|------|-----------|--------|----------|-----------|")

        highComplexityClasses.forEachIndexed { index, cls ->
            appendLine("| ${index + 1} | `${cls.qualifiedName}` | ${cls.metrics.complexityScore} | ${cls.metrics.methodCount} | ${cls.metrics.linesOfCode} | ${cls.metrics.refactoringPriority.level} |")
        }
        appendLine()

        // 高复杂度方法
        val highComplexityMethods = result.methods
            .sortedByDescending { it.metrics.complexityScore }
            .take(10)

        appendLine("#### 高复杂度方法 TOP 10")
        appendLine()
        appendLine("| 排名 | 方法名 | 复杂度评分 | 认知复杂度 | 代码行数 | 参数数量 |")
        appendLine("|------|--------|-----------|-----------|----------|----------|")

        highComplexityMethods.forEachIndexed { index, method ->
            appendLine("| ${index + 1} | `${method.className}.${method.name}` | ${method.metrics.complexityScore} | ${method.metrics.cognitiveComplexity} | ${method.metrics.linesOfCode} | ${method.metrics.parameterCount} |")
        }
        appendLine()
    }

    /**
     * 添加耦合度分析
     */
    private fun StringBuilder.appendCouplingAnalysis(result: DependencyAnalysisResult) {
        appendLine("### 耦合度分析")
        appendLine()

        // 高耦合类
        val highCouplingClasses = result.classes
            .filter { it.metrics.coupling > 10 }
            .sortedByDescending { it.metrics.coupling }
            .take(10)

        if (highCouplingClasses.isNotEmpty()) {
            appendLine("#### 高耦合类")
            appendLine()
            appendLine("| 类名 | 耦合度 | 内聚度 | 不稳定性 |")
            appendLine("|------|--------|--------|----------|")

            highCouplingClasses.forEach { cls ->
                appendLine("| `${cls.qualifiedName}` | ${cls.metrics.coupling} | ${"%.2f".format(cls.metrics.cohesion)} | |")
            }
            appendLine()
        }

        // 循环依赖分析
        val cyclicDependencies = result.packageDependencies.filter { it.cycles.isNotEmpty() }
        if (cyclicDependencies.isNotEmpty()) {
            appendLine("#### 循环依赖")
            appendLine()
            cyclicDependencies.forEach { pkgDep ->
                appendLine("**包: ${pkgDep.packageName}**")
                pkgDep.cycles.forEach { cycle ->
                    appendLine("- 循环: ${cycle.joinToString(" -> ")}")
                }
                appendLine()
            }
        }
    }

    /**
     * 添加依赖关系分析
     */
    private fun StringBuilder.appendDependencyAnalysis(result: DependencyAnalysisResult) {
        appendLine("## 依赖关系分析")
        appendLine()

        // 包级依赖
        appendPackageDependencyAnalysis(result)

        // 类级依赖
        appendClassDependencyAnalysis(result)

        // 架构层次分析
        appendArchitecturalAnalysis(result)
    }

    /**
     * 添加包级依赖分析
     */
    private fun StringBuilder.appendPackageDependencyAnalysis(result: DependencyAnalysisResult) {
        appendLine("### 包级依赖分析")
        appendLine()

        val packageStats = result.packageDependencies
            .sortedByDescending { it.dependencyCount }
            .take(10)

        if (packageStats.isNotEmpty()) {
            appendLine("#### 依赖度最高的包 TOP 10")
            appendLine()
            appendLine("| 包名 | 依赖数量 | 被依赖数量 | 循环依赖 | 不稳定性 |")
            appendLine("|------|----------|------------|----------|----------|")

            packageStats.forEach { pkgDep ->
                val hasCycles = if (pkgDep.cycles.isNotEmpty()) "是" else "否"
                appendLine("| `${pkgDep.packageName}` | ${pkgDep.dependencyCount} | ${pkgDep.dependents.size} | $hasCycles | ${"%.2f".format(calculateInstability(pkgDep))} |")
            }
            appendLine()
        }
    }

    /**
     * 添加类级依赖分析
     */
    private fun StringBuilder.appendClassDependencyAnalysis(result: DependencyAnalysisResult) {
        appendLine("### 类级依赖分析")
        appendLine()

        // 按类型统计类
        val classTypeStats = result.classes.groupBy { getClassType(it) }

        appendLine("#### 类类型分布")
        appendLine()
        appendLine("| 类型 | 数量 | 占比 |")
        appendLine("|------|------|------|")

        val totalClasses = result.classes.size.toDouble()
        classTypeStats.forEach { (type, classes) ->
            val percentage = (classes.size / totalClasses * 100).toInt()
            appendLine("| $type | ${classes.size} | ${percentage}% |")
        }
        appendLine()

        // 依赖最多的类
        val mostDependentClasses = result.classDependencies
            .sortedByDescending { it.dependencyCount }
            .take(10)

        if (mostDependentClasses.isNotEmpty()) {
            appendLine("#### 依赖最多的类 TOP 10")
            appendLine()
            appendLine("| 类名 | 依赖数量 | 主要依赖类型 |")
            appendLine("|------|----------|--------------|")

            mostDependentClasses.forEach { clsDep ->
                val mainDependencyTypes = clsDep.dependencies
                    .groupBy { it.referenceType }
                    .map { (type, refs) -> "${type.name}(${refs.size})" }
                    .joinToString(", ")

                appendLine("| `${clsDep.className}` | ${clsDep.dependencyCount} | $mainDependencyTypes |")
            }
            appendLine()
        }
    }

    /**
     * 添加架构分析
     */
    private fun StringBuilder.appendArchitecturalAnalysis(result: DependencyAnalysisResult) {
        appendLine("### 架构层次分析")
        appendLine()

        // 分析分层架构
        val layerAnalysis = analyzeArchitectureLayers(result)

        appendLine("#### 分层架构识别")
        appendLine()

        layerAnalysis.forEach { (layer, classes) ->
            if (classes.isNotEmpty()) {
                appendLine("**${layer.name}**")
                appendLine("- 类数量: ${classes.size}")
                appendLine("- 主要包: ${classes.map { it.qualifiedName.split(".").take(2).joinToString(".") }.distinct().take(5).joinToString(", ")}")
                appendLine()
            }
        }
    }

    /**
     * 添加POJO使用分析
     */
    private fun StringBuilder.appendPojoUsageAnalysis(result: DependencyAnalysisResult) {
        appendLine("## POJO使用分析")
        appendLine()

        // POJO类型分布
        val pojoTypeStats = result.pojos.groupBy { it.category }

        appendLine("### POJO类型分布")
        appendLine()
        appendLine("| 类型 | 数量 | 占比 |")
        appendLine("|------|------|------|")

        val totalPojos = result.pojos.size.toDouble()
        pojoTypeStats.forEach { (type, pojos) ->
            val percentage = if (totalPojos > 0) (pojos.size / totalPojos * 100).toInt() else 0
            appendLine("| ${type.name} | ${pojos.size} | ${percentage}% |")
        }
        appendLine()

        // 跨边界使用问题
        val crossBoundaryIssues = result.pojos.filter { pojo ->
            pojo.crossBoundaryUsage.any { !it.isExpected }
        }

        if (crossBoundaryIssues.isNotEmpty()) {
            appendLine("### 跨边界使用问题")
            appendLine()
            crossBoundaryIssues.take(10).forEach { pojo ->
                appendLine("#### `${pojo.qualifiedName}`")
                appendLine("- 类型: ${pojo.category}")
                appendLine("- 使用次数: ${pojo.usage.usedByClassesCount}")

                val issues = pojo.crossBoundaryUsage.filter { !it.isExpected }
                if (issues.isNotEmpty()) {
                    appendLine("- 异常跨边界使用:")
                    issues.forEach { usage ->
                        appendLine("  - ${usage.fromPackage} -> ${usage.toPackage} (${usage.usageCount}次)")
                    }
                }
                appendLine()
            }
        }

        // 未被有效使用的POJO
        val unusedPojos = result.pojos.filter { pojo ->
            pojo.usage.usedByClassesCount == 0 || pojo.usage.usedByMethodsCount == 0
        }

        if (unusedPojos.isNotEmpty()) {
            appendLine("### 未被有效使用的POJO")
            appendLine()
            unusedPojos.take(10).forEach { pojo ->
                appendLine("- `${pojo.qualifiedName}` (类型: ${pojo.category})")
            }
            appendLine()
        }
    }

    /**
     * 添加业务场景分析
     */
    private fun StringBuilder.appendBusinessScenarioAnalysis(result: DependencyAnalysisResult) {
        appendLine("## 业务场景分析")
        appendLine()

        val scenarios = result.sceneDefinitions

        if (scenarios.isNotEmpty()) {
            // 场景分类统计
            val scenarioTypeStats = scenarios.groupBy { it.category }

            appendLine("### 场景分类统计")
            appendLine()
            appendLine("| 场景类型 | 数量 |")
            appendLine("|----------|------|")

            scenarioTypeStats.forEach { (type, typeScenarios) ->
                appendLine("| ${type.name} | ${typeScenarios.size} |")
            }
            appendLine()

            // 复杂场景
            val complexScenarios = scenarios
                .sortedByDescending { it.coverage.methodCount + it.coverage.classCount }
                .take(10)

            if (complexScenarios.isNotEmpty()) {
                appendLine("### 复杂场景 TOP 10")
                appendLine()
                appendLine("| 场景名称 | 类型 | 方法数 | 类数 | 包数 | 最大深度 |")
                appendLine("|----------|------|--------|------|------|----------|")

                complexScenarios.forEach { scenario ->
                    appendLine("| ${scenario.name} | ${scenario.category} | ${scenario.coverage.methodCount} | ${scenario.coverage.classCount} | ${scenario.coverage.packageCount} | ${scenario.coverage.maxDepth} |")
                }
                appendLine()
            }

            // 业务入口点分析
            appendBusinessEntryPointsAnalysis(result)
        } else {
            appendLine("项目中未识别到业务场景入口点。")
            appendLine()
        }
    }

    /**
     * 添加业务入口点分析
     */
    private fun StringBuilder.appendBusinessEntryPointsAnalysis(result: DependencyAnalysisResult) {
        appendLine("### 业务入口点分析")
        appendLine()

        val entryPoints = result.businessEntryPoints
        if (entryPoints.isNotEmpty()) {
            // 按类型统计
            val entryTypeStats = entryPoints.groupBy { it.entryType }

            appendLine("#### 入口点类型分布")
            appendLine()
            entryTypeStats.forEach { (type, points) ->
                appendLine("- **${type.name}**: ${points.size} 个")
            }
            appendLine()

            // 复杂的入口点
            val complexEntryPoints = entryPoints
                .sortedByDescending { it.parameters.size }
                .take(10)

            if (complexEntryPoints.isNotEmpty()) {
                appendLine("#### 复杂入口点 TOP 10")
                appendLine()
                appendLine("| 入口点 | 类型 | 参数数量 | 业务场景 |")
                appendLine("|--------|------|----------|----------|")

                complexEntryPoints.forEach { entryPoint ->
                    appendLine("| `${entryPoint.className}.${entryPoint.methodName}` | ${entryPoint.entryType} | ${entryPoint.parameters.size} | ${entryPoint.businessScenario} |")
                }
                appendLine()
            }
        }
    }

    /**
     * 添加问题排行榜
     */
    private fun StringBuilder.appendProblemRanking(result: DependencyAnalysisResult) {
        appendLine("## 问题排行榜")
        appendLine()

        // 代码坏味道排行榜
        appendCodeSmellsRanking(result)

        // 复杂度排行榜
        appendComplexityRanking(result)

        // 耦合度排行榜
        appendCouplingRanking(result)
    }

    /**
     * 添加代码坏味道排行榜
     */
    private fun StringBuilder.appendCodeSmellsRanking(result: DependencyAnalysisResult) {
        appendLine("### 代码坏味道排行榜")
        appendLine()

        val smellRanking = result.codeSmells
            .groupBy { it.type }
            .map { (type, smells) -> type to smells.size }
            .sortedByDescending { it.second }
            .take(10)

        appendLine("| 排名 | 坏味道类型 | 数量 |")
        appendLine("|------|-----------|------|")

        smellRanking.forEachIndexed { index, (type, count) ->
            appendLine("| ${index + 1} | ${type.name.replace('_', ' ')} | $count |")
        }
        appendLine()
    }

    /**
     * 添加复杂度排行榜
     */
    private fun StringBuilder.appendComplexityRanking(result: DependencyAnalysisResult) {
        appendLine("### 复杂度排行榜")
        appendLine()

        // 类复杂度排行
        appendLine("#### 类复杂度 TOP 10")
        appendLine()
        val classComplexityRanking = result.classes
            .sortedByDescending { it.metrics.complexityScore }
            .take(10)

        appendLine("| 排名 | 类名 | 复杂度评分 |")
        appendLine("|------|------|-----------|")

        classComplexityRanking.forEachIndexed { index, cls ->
            appendLine("| ${index + 1} | `${cls.qualifiedName}` | ${cls.metrics.complexityScore} |")
        }
        appendLine()
    }

    /**
     * 添加耦合度排行榜
     */
    private fun StringBuilder.appendCouplingRanking(result: DependencyAnalysisResult) {
        appendLine("### 耦合度排行榜")
        appendLine()

        val couplingRanking = result.classes
            .sortedByDescending { it.metrics.coupling }
            .take(10)

        appendLine("| 排名 | 类名 | 耦合度 |")
        appendLine("|------|------|--------|")

        couplingRanking.forEachIndexed { index, cls ->
            appendLine("| ${index + 1} | `${cls.qualifiedName}` | ${cls.metrics.coupling} |")
        }
        appendLine()
    }

    /**
     * 添加重构建议
     */
    private fun StringBuilder.appendRefactoringSuggestions(result: DependencyAnalysisResult) {
        appendLine("## 重构建议")
        appendLine()

        // 优先级分类的建议
        val suggestions = generateRefactoringSuggestions(result)

        suggestions.forEach { (priority, prioritySuggestions) ->
            if (prioritySuggestions.isNotEmpty()) {
                appendLine("### ${priority.name}优先级")
                appendLine()
                prioritySuggestions.forEach { suggestion ->
                    appendLine("- **${suggestion.title}**")
                    appendLine("  - ${suggestion.description}")
                    if (suggestion.affectedClasses.isNotEmpty()) {
                        appendLine("  - 涉及类: ${suggestion.affectedClasses.joinToString(", ") { "`$it`" }}")
                    }
                    appendLine("  - 预期收益: ${suggestion.expectedBenefit}")
                    appendLine()
                }
            }
        }
    }

    /**
     * 添加详细分析数据
     */
    private fun StringBuilder.appendDetailedAnalysis(result: DependencyAnalysisResult) {
        appendLine("## 详细分析数据")
        appendLine()

        // 包详细信息
        appendLine("### 包详细信息")
        appendLine()
        result.packages.forEach { pkg ->
            appendLine("#### `${pkg.fullName}`")
            appendLine("- 层级: ${pkg.level}")
            appendLine("- 类数量: ${pkg.classCount}")
            appendLine("- 传入依赖: ${pkg.metrics.fanIn}")
            appendLine("- 传出依赖: ${pkg.metrics.fanOut}")
            appendLine("- 不稳定性: ${"%.2f".format(pkg.metrics.instability)}")
            appendLine()
        }
    }

    /**
     * 添加主要问题摘要
     */
    private fun StringBuilder.appendTopIssues(result: DependencyAnalysisResult) {
        appendLine("## 主要问题")
        appendLine()

        val criticalIssues = result.codeSmells.filter {
            it.severity == Severity.CRITICAL || it.severity == Severity.HIGH
        }.take(5)

        if (criticalIssues.isNotEmpty()) {
            criticalIssues.forEach { smell ->
                appendLine("- **[${smell.severity}] ${smell.type.name.replace('_', ' ')}**")
                appendLine("  - ${smell.className}${smell.methodName?.let { ".$it" } ?: ""}: ${smell.description}")
            }
            appendLine()
        } else {
            appendLine("暂未发现严重问题。")
            appendLine()
        }
    }

    /**
     * 添加建议
     */
    private fun StringBuilder.appendRecommendations(result: DependencyAnalysisResult) {
        appendLine("## 主要建议")
        appendLine()

        val suggestions = generateQuickRecommendations(result)
        suggestions.forEach { suggestion ->
            appendLine("- $suggestion")
        }
        appendLine()
    }

    /**
     * 添加报告尾部
     */
    private fun StringBuilder.appendReportFooter() {
        appendLine("---")
        appendLine()
        appendLine("*此报告由 Nekoama 代码分析工具生成*")
        appendLine()
        appendLine("生成时间: ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())}")
    }

    /**
     * 计算复杂度分布
     */
    private fun calculateComplexityDistribution(result: DependencyAnalysisResult): Map<ComplexityLevel, Int> {
        val distribution = mutableMapOf<ComplexityLevel, Int>()

        result.classes.forEach { cls ->
            val complexity = cls.metrics.complexityScore
            val level = when {
                complexity >= 80 -> ComplexityLevel.VERY_HIGH
                complexity >= 50 -> ComplexityLevel.HIGH
                complexity >= 30 -> ComplexityLevel.MEDIUM
                complexity >= 15 -> ComplexityLevel.LOW
                else -> ComplexityLevel.VERY_LOW
            }
            distribution[level] = distribution.getOrDefault(level, 0) + 1
        }

        return distribution
    }

    /**
     * 计算质量评分
     */
    private fun calculateQualityScore(result: DependencyAnalysisResult): QualityScore {
        var score = 100

        // 根据代码坏味道扣分
        result.codeSmells.forEach { smell ->
            score -= when (smell.severity) {
                Severity.CRITICAL -> 10
                Severity.HIGH -> 5
                Severity.MEDIUM -> 2
                Severity.LOW -> 1
                Severity.INFO -> 0
            }
        }

        // 根据高复杂度类扣分
        val highComplexityClasses = result.classes.count { it.metrics.complexityScore > 70 }
        score -= highComplexityClasses * 3

        // 根据循环依赖扣分
        val cyclicPackages = result.packageDependencies.count { it.cycles.isNotEmpty() }
        score -= cyclicPackages * 5

        score = maxOf(0, score)

        return QualityScore(
            score = score,
            description = when {
                score >= 90 -> "优秀 - 代码质量很高，只有少量问题"
                score >= 80 -> "良好 - 代码质量较好，有一些可以改进的地方"
                score >= 70 -> "一般 - 代码质量一般，需要一些重构"
                score >= 60 -> "较差 - 代码质量较差，建议进行重构"
                else -> "很差 - 代码质量很差，急需重构"
            }
        )
    }

    /**
     * 计算不稳定性
     */
    private fun calculateInstability(pkgDep: PackageDependency): Double {
        val ce = pkgDep.dependencyCount.toDouble() // 传出耦合
        val ca = pkgDep.dependents.size.toDouble()   // 传入耦合
        return if (ca + ce > 0) ce / (ca + ce) else 0.0
    }

    /**
     * 获取类类型
     */
    private fun getClassType(cls: ClassInfo): String {
        return when {
            cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> "Controller"
            cls.annotations.any { it.contains("Service", ignoreCase = true) } -> "Service"
            cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> "Repository"
            cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> "POJO"
            cls.type == ClassType.INTERFACE -> "Interface"
            cls.type == ClassType.ABSTRACT_CLASS -> "Abstract Class"
            cls.type == ClassType.ENUM -> "Enum"
            else -> "Class"
        }
    }

    /**
     * 分析架构层次
     */
    private fun analyzeArchitectureLayers(result: DependencyAnalysisResult): Map<ArchitectureLayer, List<ClassInfo>> {
        val layers = mutableMapOf<ArchitectureLayer, MutableList<ClassInfo>>()

        result.classes.forEach { cls ->
            val layer = when {
                cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> ArchitectureLayer.PRESENTATION
                cls.annotations.any { it.contains("Service", ignoreCase = true) } -> ArchitectureLayer.BUSINESS
                cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> ArchitectureLayer.PERSISTENCE
                cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> ArchitectureLayer.MODEL
                cls.qualifiedName.contains("config", ignoreCase = true) -> ArchitectureLayer.CONFIGURATION
                cls.qualifiedName.contains("util", ignoreCase = true) -> ArchitectureLayer.UTILITY
                else -> ArchitectureLayer.COMMON
            }

            layers.getOrPut(layer) { mutableListOf() }.add(cls)
        }

        return layers
    }

    /**
     * 生成重构建议
     */
    private fun generateRefactoringSuggestions(result: DependencyAnalysisResult): Map<RefactoringPriority, List<RefactoringSuggestion>> {
        val suggestions = mutableMapOf<RefactoringPriority, MutableList<RefactoringSuggestion>>()

        result.codeSmells.forEach { smell ->
            val priority = when (smell.severity) {
                Severity.CRITICAL -> RefactoringPriority.HIGH
                Severity.HIGH -> RefactoringPriority.HIGH
                Severity.MEDIUM -> RefactoringPriority.MEDIUM
                Severity.LOW -> RefactoringPriority.LOW
                Severity.INFO -> RefactoringPriority.LOW
            }

            val suggestion = RefactoringSuggestion(
                title = "修复${smell.type.name.replace('_', ' ')}",
                description = smell.description,
                affectedClasses = listOf(smell.className),
                expectedBenefit = "提高代码可读性和维护性"
            )

            suggestions.getOrPut(priority) { mutableListOf() }.add(suggestion)
        }

        return suggestions
    }

    /**
     * 生成快速建议
     */
    private fun generateQuickRecommendations(result: DependencyAnalysisResult): List<String> {
        val recommendations = mutableListOf<String>()

        // 基于复杂度的建议
        val highComplexityClasses = result.classes.count { it.metrics.complexityScore > 70 }
        if (highComplexityClasses > 0) {
            recommendations.add("优先重构 $highComplexityClasses 个高复杂度类")
        }

        // 基于代码坏味道的建议
        val criticalSmells = result.codeSmells.count { it.severity == Severity.CRITICAL }
        if (criticalSmells > 0) {
            recommendations.add("立即修复 $criticalSmells 个严重代码问题")
        }

        // 基于循环依赖的建议
        val cyclicPackages = result.packageDependencies.count { it.cycles.isNotEmpty() }
        if (cyclicPackages > 0) {
            recommendations.add("解决 $cyclicPackages 个包的循环依赖问题")
        }

        return recommendations
    }
}

/**
 * Markdown导出结果
 */
data class MarkdownExportResult(
    val success: Boolean,
    val outputPath: Path,
    val fileSize: Long = 0,
    val message: String,
    val error: Throwable? = null
)

/**
 * 复杂度等级
 */
enum class ComplexityLevel(val description: String) {
    VERY_LOW("很低 (0-14)"),
    LOW("低 (15-29)"),
    MEDIUM("中等 (30-49)"),
    HIGH("高 (50-79)"),
    VERY_HIGH("很高 (80+)")
}

/**
 * 质量评分
 */
data class QualityScore(
    val score: Int,
    val description: String
)


/**
 * 重构优先级
 */
enum class RefactoringPriority {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 重构建议
 */
data class RefactoringSuggestion(
    val title: String,
    val description: String,
    val affectedClasses: List<String>,
    val expectedBenefit: String
)