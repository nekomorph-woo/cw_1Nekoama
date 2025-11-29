package com.cw2.nekoama.core.analysis

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.integrations.psi.*
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.*

/**
 * 全项目代码分析引擎
 *
 * 这个引擎负责执行完整的项目代码分析，包括：
 * 1. 全项目PSI扫描
 * 2. 类、方法、调用关系数据收集
 * 3. 入口点检测
 * 4. 复杂度计算和代码坏味道检测
 *
 * 设计原则：
 * - 不依赖入口点预选，先进行全项目分析
 * - 入口点检测作为分析流程的必须步骤
 * - 保留所有核心分析能力
 */
class CodeAnalysisEngine(private val project: Project) {

    private val logger = NekoamaLogger
    private val dependencyAnalyzer = DependencyCodeAnalyzer(project)
    private val complexityCalculator = ComplexityCalculator()
    private val javaDependencyExtractor = JavaDependencyExtractor()
    private val complexityScorer = ComplexityScorer()
    private val scopeController = AnalysisScopeController(project)
    private val entryPointDetector = BoundaryEntryPointDetector(project)

    /**
     * 执行全项目分析
     */
    suspend fun executeFullProjectAnalysis(
        progressIndicator: ProgressIndicator
    ): FullProjectAnalysisResult = withContext(Dispatchers.IO) {
        logger.info("CodeAnalysisEngine", "开始全项目代码分析")

        val startTime = System.currentTimeMillis()

        try {
            // 1. 全项目PSI扫描
            progressIndicator.text = "正在扫描项目文件..."
            val allPsiFiles = scanAllJavaFiles()
            logger.info("CodeAnalysisEngine", "扫描到 ${allPsiFiles.size} 个Java文件")

            // 2. 提取所有类和方法
            progressIndicator.text = "正在提取类和方法信息..."
            val allClasses = extractAllClasses(allPsiFiles)
            val allMethods = extractAllMethods(allClasses)
            logger.info("CodeAnalysisEngine", "提取到 ${allClasses.size} 个类，${allMethods.size} 个方法")

            // 3. 检测入口点（必须步骤）
            progressIndicator.text = "正在检测业务入口点..."
            val entryPoints = entryPointDetector.detectEntryPoints(allClasses)
            logger.info("CodeAnalysisEngine", "检测到 ${entryPoints.size} 个业务入口点")

            // 4. 构建方法调用关系图
            progressIndicator.text = "正在构建调用关系图..."
            val methodCallGraph = buildMethodCallGraph(allMethods)

            // 5. 计算复杂度指标
            progressIndicator.text = "正在计算复杂度指标..."
            val complexityMetrics = calculateComplexityMetrics(allClasses, allMethods)

            // 6. 检测代码坏味道
            progressIndicator.text = "正在检测代码坏味道..."
            val codeSmells = detectCodeSmells(allClasses, allMethods, complexityMetrics)

            // 7. 构建依赖关系图
            progressIndicator.text = "正在构建依赖关系图..."
            val dependencyGraph = buildDependencyGraph(allClasses)

            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info("CodeAnalysisEngine", "全项目分析完成，耗时: ${elapsedTime}ms")

            FullProjectAnalysisResult(
                projectInfo = ProjectInfo(
                    name = project.name,
                    location = project.basePath ?: "",
                    totalFiles = allPsiFiles.size,
                    totalClasses = allClasses.size,
                    totalMethods = allMethods.size,
                    analysisTime = elapsedTime
                ),
                allClasses = allClasses,
                allMethods = allMethods,
                entryPoints = entryPoints,
                methodCallGraph = methodCallGraph,
                dependencyGraph = dependencyGraph,
                complexityMetrics = complexityMetrics,
                codeSmells = codeSmells
            )

        } catch (e: CancellationException) {
            logger.info("CodeAnalysisEngine", "用户取消全项目分析")
            throw e
        } catch (e: Exception) {
            logger.error("CodeAnalysisEngine", "全项目分析失败", error = e)
            throw RuntimeException("全项目分析失败: ${e.message}", e)
        }
    }

    /**
     * 扫描项目中所有Java文件
     */
    private suspend fun scanAllJavaFiles(): List<PsiJavaFile> = withContext(Dispatchers.IO) {
        return@withContext com.intellij.openapi.application.ReadAction.compute<List<PsiJavaFile>, com.intellij.openapi.progress.ProcessCanceledException> {
            ProgressManager.checkCanceled()

            val files = mutableListOf<PsiJavaFile>()
            val psiManager = PsiManager.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)

            FileTypeIndex.processFiles(
                StdFileTypes.JAVA,
                { virtualFile ->
                    ProgressManager.checkCanceled()
                    val psiFile = psiManager.findFile(virtualFile)
                    if (psiFile is PsiJavaFile) {
                        files.add(psiFile)
                    }
                    true
                },
                scope
            )

            files.distinctBy { it.virtualFile.path }
        }
    }

    /**
     * 提取所有类信息
     */
    private suspend fun extractAllClasses(psiFiles: List<PsiJavaFile>): List<PsiClass> = withContext(Dispatchers.Default) {
        return@withContext com.intellij.openapi.application.ReadAction.compute<List<PsiClass>, com.intellij.openapi.progress.ProcessCanceledException> {
            ProgressManager.checkCanceled()

            psiFiles.flatMap { psiFile ->
                ProgressManager.checkCanceled()
                psiFile.classes.toList()
            }
        }
    }

    /**
     * 提取所有方法信息
     */
    private suspend fun extractAllMethods(classes: List<PsiClass>): List<PsiMethod> = withContext(Dispatchers.Default) {
        return@withContext com.intellij.openapi.application.ReadAction.compute<List<PsiMethod>, com.intellij.openapi.progress.ProcessCanceledException> {
            ProgressManager.checkCanceled()

            classes.flatMap { psiClass ->
                ProgressManager.checkCanceled()
                psiClass.allMethods.toList()
            }.distinctBy { "${it.containingClass?.qualifiedName}.${it.name}" }
        }
    }

    /**
     * 构建方法调用关系图
     */
    private suspend fun buildMethodCallGraph(allMethods: List<PsiMethod>): MethodCallGraph = withContext(Dispatchers.Default) {
        val methodCalls = mutableMapOf<String, MutableList<MethodCall>>()
        val methodCallTargets = mutableMapOf<String, Int>() // fanIn统计

        allMethods.forEach { method ->
            ProgressManager.checkCanceled()

            val methodKey = getMethodKey(method)
            methodCalls[methodKey] = mutableListOf()

            // 分析方法体内的调用关系
            method.body?.accept(object : JavaRecursiveElementVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)

                    val targetMethod = expression.resolveMethod()
                    if (targetMethod != null) {
                        val targetKey = getMethodKey(targetMethod)

                        val call = MethodCall(
                            fromMethod = methodKey,
                            toMethod = targetKey,
                            callType = "method_call",
                            line = getLineNumber(expression)
                        )

                        methodCalls[methodKey]?.add(call)
                        methodCallTargets[targetKey] = methodCallTargets.getOrDefault(targetKey, 0) + 1
                    }
                }
            })
        }

        MethodCallGraph(
            methodCalls = methodCalls,
            methodCallTargets = methodCallTargets
        )
    }

    /**
     * 计算复杂度指标
     */
    private suspend fun calculateComplexityMetrics(
        classes: List<PsiClass>,
        methods: List<PsiMethod>
    ): Map<String, ComplexityMetrics> = withContext(Dispatchers.Default) {
        val metrics = mutableMapOf<String, ComplexityMetrics>()

        // 计算每个方法的复杂度
        methods.forEach { method ->
            ProgressManager.checkCanceled()

            val methodKey = getMethodKey(method)
            val cyclomaticComplexity = complexityCalculator.calculateMethodCyclomaticComplexity(method)
            val cognitiveComplexity = complexityCalculator.calculateMethodCognitiveComplexity(method)

            metrics[methodKey] = ComplexityMetrics(
                cyclomaticComplexity = cyclomaticComplexity,
                cognitiveComplexity = cognitiveComplexity,
                linesOfCode = countLinesOfCode(method),
                parameters = method.parameters.size
            )
        }

        // 计算每个类的复杂度（方法复杂度的聚合）
        classes.forEach { psiClass ->
            ProgressManager.checkCanceled()

            val classKey = psiClass.qualifiedName ?: ""
            val classMethods = metrics.keys.filter { it.startsWith("$classKey.") }

            if (classMethods.isNotEmpty()) {
                val totalComplexity = classMethods.sumOf { metrics[it]?.cyclomaticComplexity ?: 0 }
                val avgComplexity = totalComplexity.toDouble() / classMethods.size

                metrics[classKey] = ComplexityMetrics(
                    cyclomaticComplexity = totalComplexity,
                    cognitiveComplexity = classMethods.sumOf { metrics[it]?.cognitiveComplexity ?: 0 },
                    linesOfCode = classMethods.sumOf { metrics[it]?.linesOfCode ?: 0 },
                    parameters = classMethods.size
                )
            }
        }

        metrics
    }

    /**
     * 检测代码坏味道
     */
    private suspend fun detectCodeSmells(
        classes: List<PsiClass>,
        methods: List<PsiMethod>,
        complexityMetrics: Map<String, ComplexityMetrics>
    ): List<CodeSmell> = withContext(Dispatchers.Default) {
        val codeSmells = mutableListOf<CodeSmell>()

        // 检测高复杂度方法
        complexityMetrics.forEach { (methodKey, metrics) ->
            ProgressManager.checkCanceled()

            if (metrics.cyclomaticComplexity > 30) {
                codeSmells.add(CodeSmell(
                    type = "HIGH_COMPLEXITY",
                    element = methodKey,
                    severity = "HIGH",
                    description = "方法复杂度过高: ${metrics.cyclomaticComplexity}"
                ))
            }

            if (metrics.parameters > 8) {
                codeSmells.add(CodeSmell(
                    type = "TOO_MANY_PARAMETERS",
                    element = methodKey,
                    severity = "MEDIUM",
                    description = "方法参数过多: ${metrics.parameters}"
                ))
            }
        }

        // 检测大类问题
        classes.forEach { psiClass ->
            ProgressManager.checkCanceled()

            val classMethods = psiClass.allMethods.size
            val classFields = psiClass.allFields.size

            if (classMethods > 20) {
                codeSmells.add(CodeSmell(
                    type = "TOO_MANY_METHODS",
                    element = psiClass.qualifiedName ?: "",
                    severity = "MEDIUM",
                    description = "类方法过多: $classMethods"
                ))
            }

            if (classFields > 15) {
                codeSmells.add(CodeSmell(
                    type = "TOO_MANY_FIELDS",
                    element = psiClass.qualifiedName ?: "",
                    severity = "MEDIUM",
                    description = "类字段过多: $classFields"
                ))
            }
        }

        codeSmells
    }

    /**
     * 构建依赖关系图
     */
    private suspend fun buildDependencyGraph(classes: List<PsiClass>): List<ClassDependency> = withContext(Dispatchers.Default) {
        val dependencies = mutableListOf<ClassDependency>()

        classes.forEach { sourceClass ->
            ProgressManager.checkCanceled()

            sourceClass.accept(object : JavaRecursiveElementVisitor() {
                override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                    super.visitReferenceElement(reference)

                    val targetClass = reference.resolve() as? PsiClass
                    if (targetClass != null && targetClass != sourceClass) {
                        val sourceQualifiedName = sourceClass?.qualifiedName ?: ""
                        val targetQualifiedName = targetClass?.qualifiedName ?: ""

                        val dependency = ClassDependency(
                            className = sourceQualifiedName,
                            packageName = sourceQualifiedName.substringBeforeLast(".", ""),
                            superClass = sourceClass?.superClass?.qualifiedName,
                            interfaces = sourceClass?.interfaces?.mapNotNull { it.qualifiedName } ?: emptyList(),
                            dependencies = listOf(
                                ClassReference(
                                    className = targetQualifiedName,
                                    referenceType = ReferenceType.ASSOCIATION,
                                    location = SourceLocation("", 0, 0)
                                )
                            ),
                            dependents = emptyList(),
                            dependencyCount = 1,
                            isPojo = false,
                            isController = false,
                            isService = false,
                            isRepository = false
                        )
                        dependencies.add(dependency)
                    }
                }
            })
        }

        dependencies.distinctBy { "${it.className}->${it.dependencies.firstOrNull()?.className}" }
    }

    // 辅助方法
    private fun getMethodKey(method: PsiMethod): String {
        val className = method.containingClass?.qualifiedName ?: ""
        return "$className.${method.name}"
    }

    private fun getLineNumber(element: PsiElement): Int {
        val file = element.containingFile.viewProvider.document
        return file?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: 0
    }

    private fun countLinesOfCode(method: PsiMethod): Int {
        return method.body?.text?.lines()?.count { it.trim().isNotEmpty() } ?: 0
    }

    private fun determineDependencyType(reference: PsiJavaCodeReferenceElement): String {
        return when (reference.parent) {
            is PsiImportStatement -> "import"
            is PsiClassObjectAccessExpression -> "static"
            is PsiNewExpression -> "instantiation"
            is PsiMethodCallExpression -> "method_call"
            else -> "reference"
        }
    }

    private fun calculateDependencyStrength(reference: PsiJavaCodeReferenceElement): Double {
        return when (determineDependencyType(reference)) {
            "import" -> 1.0
            "instantiation" -> 0.8
            "method_call" -> 0.6
            "static" -> 0.4
            else -> 0.2
        }
    }
}

/**
 * 数据类定义
 */
data class FullProjectAnalysisResult(
    val projectInfo: ProjectInfo,
    val allClasses: List<PsiClass>,
    val allMethods: List<PsiMethod>,
    val entryPoints: List<BusinessEntryPoint>,
    val methodCallGraph: MethodCallGraph,
    val dependencyGraph: List<ClassDependency>,
    val complexityMetrics: Map<String, ComplexityMetrics>,
    val codeSmells: List<CodeSmell>
)

data class ProjectInfo(
    val name: String,
    val location: String,
    val totalFiles: Int,
    val totalClasses: Int,
    val totalMethods: Int,
    val analysisTime: Long
)

data class MethodCallGraph(
    val methodCalls: Map<String, List<MethodCall>>,
    val methodCallTargets: Map<String, Int> // fanIn统计
)

data class ComplexityMetrics(
    val cyclomaticComplexity: Int,
    val cognitiveComplexity: Int,
    val linesOfCode: Int,
    val parameters: Int
)

data class MethodCall(
    val fromMethod: String,
    val toMethod: String,
    val callType: String,
    val line: Int
)

data class CodeSmell(
    val type: String,
    val element: String,
    val severity: String,
    val description: String
)