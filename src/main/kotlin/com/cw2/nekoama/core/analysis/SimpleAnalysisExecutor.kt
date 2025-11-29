package com.cw2.nekoama.core.analysis

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.core.reporting.DependencyReportGenerator
import com.cw2.nekoama.core.reporting.ReportGenerationResult
import com.cw2.nekoama.integrations.psi.BatchAnalysisProcessor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import kotlinx.coroutines.*

/**
 * 简化的代码分析执行器
 *
 * 根据M2阶段重构要求，实现简化的分析执行流程：
 * 1. 使用新的全项目分析引擎
 * 2. 确保入口点检测作为必须步骤
 * 3. 保留所有核心分析能力
 * 4. 与现有报告生成系统兼容
 */
class SimpleAnalysisExecutor(private val project: Project) {

    private val logger = NekoamaLogger
    private val codeAnalysisEngine = CodeAnalysisEngine(project)
    private val reportGenerator = DependencyReportGenerator()

    /**
     * 执行完整的代码分析流程
     *
     * @param config 分析配置（现在简化为仅包含基本选项）
     * @param progressIndicator 进度指示器
     */
    suspend fun executeAnalysis(
        config: SimpleAnalysisConfig,
        progressIndicator: ProgressIndicator
    ): AnalysisResult = com.intellij.openapi.application.ReadAction.compute<AnalysisResult, com.intellij.openapi.progress.ProcessCanceledException> {
        return@compute kotlinx.coroutines.runBlocking {
            executeAnalysisInternal(config, progressIndicator)
        }
    }

    private suspend fun executeAnalysisInternal(
        config: SimpleAnalysisConfig,
        progressIndicator: ProgressIndicator
    ): AnalysisResult = withContext(Dispatchers.IO) {
        logger.info("SimpleAnalysisExecutor", "开始执行简化分析流程")

        val startTime = System.currentTimeMillis()

        try {
            // 1. 使用新的全项目分析引擎执行分析
            progressIndicator.text = "正在执行全项目代码分析..."
            progressIndicator.fraction = 0.1

            val fullProjectResult = codeAnalysisEngine.executeFullProjectAnalysis(progressIndicator)
            logger.info("SimpleAnalysisExecutor", "全项目分析完成，检测到 ${fullProjectResult.entryPoints.size} 个入口点")

            // 2. 根据配置过滤分析结果
            progressIndicator.text = "正在处理分析结果..."
            progressIndicator.fraction = 0.8

            val filteredResult = filterAnalysisResult(fullProjectResult, config)

            // 3. 生成兼容的分析结果
            progressIndicator.text = "正在生成最终报告..."
            progressIndicator.fraction = 0.9

            val analysisResult = convertToLegacyAnalysisResult(filteredResult)

            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info("SimpleAnalysisExecutor", "简化分析流程完成，总耗时: ${elapsedTime}ms")

            analysisResult

        } catch (e: CancellationException) {
            logger.info("SimpleAnalysisExecutor", "用户取消分析")
            throw e
        } catch (e: Exception) {
            logger.error("SimpleAnalysisExecutor", "分析执行失败", error = e)
            throw RuntimeException("分析执行失败: ${e.message}", e)
        }
    }

    /**
     * 根据配置过滤分析结果
     */
    private suspend fun filterAnalysisResult(
        fullProjectResult: FullProjectAnalysisResult,
        config: SimpleAnalysisConfig
    ): FullProjectAnalysisResult = withContext(Dispatchers.Default) {
        ProgressManager.checkCanceled()

        // 目前只根据是否包含测试代码进行过滤
        // 其他核心分析功能（复杂度计算、代码坏味道检测、依赖分析）始终启用

        var filteredClasses = fullProjectResult.allClasses
        var filteredMethods = fullProjectResult.allMethods

        // 过滤测试代码（如果配置要求）
        if (!config.includeTestCode) {
            filteredClasses = com.intellij.openapi.application.ReadAction.compute<List<PsiClass>, com.intellij.openapi.progress.ProcessCanceledException> {
                fullProjectResult.allClasses.filter { psiClass ->
                    ProgressManager.checkCanceled()
                    !isTestClass(psiClass)
                }
            }

            filteredMethods = com.intellij.openapi.application.ReadAction.compute<List<PsiMethod>, com.intellij.openapi.progress.ProcessCanceledException> {
                fullProjectResult.allMethods.filter { psiMethod ->
                    ProgressManager.checkCanceled()
                    !isTestMethod(psiMethod)
                }
            }
        }

        fullProjectResult.copy(
            allClasses = filteredClasses,
            allMethods = filteredMethods
        )
    }

    /**
     * 转换为兼容的分析结果格式
     */
    private suspend fun convertToLegacyAnalysisResult(
        fullProjectResult: FullProjectAnalysisResult
    ): AnalysisResult = withContext(Dispatchers.Default) {
        ProgressManager.checkCanceled()

        // 构建兼容的统计信息
        val stats = AnalysisStats(
            totalClasses = fullProjectResult.allClasses.size,
            totalMethods = fullProjectResult.allMethods.size,
            entryPointsCount = fullProjectResult.entryPoints.size,
            complexityStats = ComplexityStats(
                totalComplexity = fullProjectResult.complexityMetrics.values.sumOf { it.cyclomaticComplexity },
                averageComplexity = if (fullProjectResult.complexityMetrics.isNotEmpty()) {
                    fullProjectResult.complexityMetrics.values.map { it.cyclomaticComplexity }.average()
                } else 0.0,
                highComplexityMethods = fullProjectResult.complexityMetrics.count { it.value.cyclomaticComplexity > 30 }
            ),
            codeSmellStats = CodeSmellStats(
                totalSmells = fullProjectResult.codeSmells.size,
                criticalSmells = fullProjectResult.codeSmells.count { it.severity == "HIGH" },
                highSmells = fullProjectResult.codeSmells.count { it.severity == "MEDIUM" }
            )
        )

        // 构建类图数据
        val classGraph = buildClassGraphData(fullProjectResult)

        // 构建方法调用图数据
        val methodCallGraph = buildMethodCallGraphData(fullProjectResult)

        AnalysisResult(
            projectInfo = fullProjectResult.projectInfo,
            stats = stats,
            entryPoints = fullProjectResult.entryPoints,
            classGraph = classGraph,
            methodCallGraph = methodCallGraph,
            codeSmells = fullProjectResult.codeSmells,
            complexityMetrics = fullProjectResult.complexityMetrics
        )
    }

    /**
     * 构建类图数据
     */
    private suspend fun buildClassGraphData(fullProjectResult: FullProjectAnalysisResult): ClassGraphData = withContext(Dispatchers.Default) {
        ProgressManager.checkCanceled()

        val nodes = com.intellij.openapi.application.ReadAction.compute<List<ClassNode>, com.intellij.openapi.progress.ProcessCanceledException> {
            fullProjectResult.allClasses.map { psiClass ->
                ProgressManager.checkCanceled()

                val className = psiClass.qualifiedName ?: ""
                val annotations = com.intellij.openapi.application.ReadAction.compute<List<com.intellij.psi.PsiAnnotation>, com.intellij.openapi.progress.ProcessCanceledException> {
                    psiClass.annotations.toList()
                }

                ClassNode(
                    id = className,
                    name = psiClass.name ?: "",
                    packagePath = className.substringBeforeLast(".", ""),
                    complexity = fullProjectResult.complexityMetrics[className]?.cyclomaticComplexity ?: 0,
                    isController = annotations.any { annotation ->
                        annotation.qualifiedName in setOf(
                            "org.springframework.web.bind.annotation.RestController",
                            "org.springframework.stereotype.Controller"
                        )
                    },
                    isService = annotations.any { annotation ->
                        annotation.qualifiedName?.contains("Service") == true
                    }
                )
            }
        }

        val edges = fullProjectResult.dependencyGraph.map { dep ->
            ClassEdge(
                source = dep.className,
                target = dep.dependencies.firstOrNull()?.className ?: "",
                type = "ASSOCIATION",
                strength = 1.0
            )
        }

        ClassGraphData(
            nodes = nodes,
            edges = edges
        )
    }

    /**
     * 构建方法调用图数据
     */
    private suspend fun buildMethodCallGraphData(fullProjectResult: FullProjectAnalysisResult): MethodCallGraphData = withContext(Dispatchers.Default) {
        ProgressManager.checkCanceled()

        val nodes = com.intellij.openapi.application.ReadAction.compute<List<MethodNode>, com.intellij.openapi.progress.ProcessCanceledException> {
            fullProjectResult.allMethods.map { psiMethod ->
                ProgressManager.checkCanceled()

                val methodKey = "${psiMethod.containingClass?.qualifiedName}.${psiMethod.name}"
                MethodNode(
                    id = methodKey,
                    name = psiMethod.name,
                    className = psiMethod.containingClass?.qualifiedName ?: "",
                    complexity = fullProjectResult.complexityMetrics[methodKey]?.cyclomaticComplexity ?: 0,
                    fanIn = fullProjectResult.methodCallGraph.methodCallTargets[methodKey] ?: 0,
                    fanOut = fullProjectResult.methodCallGraph.methodCalls[methodKey]?.size ?: 0
                )
            }
        }

        val edges = fullProjectResult.methodCallGraph.methodCalls.flatMap { (fromMethod, calls) ->
            calls.map { call ->
                MethodEdge(
                    source = fromMethod,
                    target = call.toMethod,
                    type = call.callType
                )
            }
        }

        MethodCallGraphData(
            nodes = nodes,
            edges = edges
        )
    }

    /**
     * 判断是否为测试类
     */
    private fun isTestClass(psiClass: PsiClass): Boolean {
        return com.intellij.openapi.application.ReadAction.compute<Boolean, com.intellij.openapi.progress.ProcessCanceledException> {
            ProgressManager.checkCanceled()

            val className = psiClass.name ?: return@compute false
            val packageName = psiClass.qualifiedName ?: return@compute false

            className.endsWith("Test") ||
                   className.endsWith("Tests") ||
                   packageName.contains(".test.") ||
                   packageName.contains(".tests.")
        }
    }

    /**
     * 判断是否为测试方法
     */
    private fun isTestMethod(psiMethod: PsiMethod): Boolean {
        return com.intellij.openapi.application.ReadAction.compute<Boolean, com.intellij.openapi.progress.ProcessCanceledException> {
            ProgressManager.checkCanceled()

            val methodName = psiMethod.name
            val containingClass = psiMethod.containingClass

            // 如果所在类是测试类，则所有public方法都可能是测试方法
            if (containingClass != null && isTestClass(containingClass)) {
                return@compute psiMethod.hasModifierProperty(PsiModifier.PUBLIC) &&
                       (methodName.startsWith("test") ||
                        psiMethod.annotations.any { annotation ->
                            annotation.qualifiedName in setOf(
                                "org.junit.Test",
                                "org.junit.jupiter.api.Test",
                                "org.testng.annotations.Test"
                            )
                        })
            }

            return@compute false
        }
    }

    /**
     * 生成HTML报告（新版本 - 使用M2重构后的数据结构）
     */
    suspend fun generateHtmlReport(
        analysisResult: AnalysisResult,
        outputPath: java.nio.file.Path
    ): com.cw2.nekoama.core.reporting.ReportGenerationResult {
        logger.info("SimpleAnalysisExecutor", "开始生成HTML报告: ${outputPath.fileName}")
        return com.intellij.openapi.application.ReadAction.compute<com.cw2.nekoama.core.reporting.ReportGenerationResult, com.intellij.openapi.progress.ProcessCanceledException> {
            kotlinx.coroutines.runBlocking {
                reportGenerator.generateReport(analysisResult, outputPath)
            }
        }
    }
}

/**
 * 简化的分析配置
 */
data class SimpleAnalysisConfig(
    val includeTestCode: Boolean = false,
    val enableComplexityAnalysis: Boolean = true, // 始终启用
    val enableCodeSmellDetection: Boolean = true, // 始终启用
    val enableDependencyAnalysis: Boolean = true   // 始终启用
)

/**
 * 分析结果数据类
 */
data class AnalysisResult(
    val projectInfo: ProjectInfo,
    val stats: AnalysisStats,
    val entryPoints: List<BusinessEntryPoint>,
    val classGraph: ClassGraphData,
    val methodCallGraph: MethodCallGraphData,
    val codeSmells: List<CodeSmell>,
    val complexityMetrics: Map<String, ComplexityMetrics>
)

data class AnalysisStats(
    val totalClasses: Int,
    val totalMethods: Int,
    val entryPointsCount: Int,
    val complexityStats: ComplexityStats,
    val codeSmellStats: CodeSmellStats
)

data class ComplexityStats(
    val totalComplexity: Int,
    val averageComplexity: Double,
    val highComplexityMethods: Int
)

data class CodeSmellStats(
    val totalSmells: Int,
    val criticalSmells: Int,
    val highSmells: Int
)

data class ClassGraphData(
    val nodes: List<ClassNode>,
    val edges: List<ClassEdge>
)

data class ClassNode(
    val id: String,
    val name: String,
    val packagePath: String,
    val complexity: Int,
    val isController: Boolean,
    val isService: Boolean
)

data class ClassEdge(
    val source: String,
    val target: String,
    val type: String,
    val strength: Double
)

data class MethodCallGraphData(
    val nodes: List<MethodNode>,
    val edges: List<MethodEdge>
)

data class MethodNode(
    val id: String,
    val name: String,
    val className: String,
    val complexity: Int,
    val fanIn: Int,
    val fanOut: Int
)

data class MethodEdge(
    val source: String,
    val target: String,
    val type: String
)