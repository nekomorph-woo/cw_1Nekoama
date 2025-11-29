package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 批量分析处理器
 * 支持大型项目的分批处理和进度反馈
 */
class BatchAnalysisProcessor(private val project: Project) {

    private val logger = NekoamaLogger
    private val dependencyAnalyzer = DependencyCodeAnalyzer(project)
    private val complexityCalculator = ComplexityCalculator()
    private val scopeController = AnalysisScopeController(project)
    // POJO分析器已移除，简化分析流程
    private val javaDependencyExtractor = JavaDependencyExtractor()
    private val complexityScorer = ComplexityScorer()

    /**
     * 执行批量分析
     */
    suspend fun executeBatchAnalysis(
        config: AnalysisConfig,
        progressIndicator: ProgressIndicator
    ): DependencyAnalysisResult = withContext(Dispatchers.IO) {
        logger.info("BatchAnalysisProcessor", "Starting batch analysis with config: maxDepth=${config.maxDepth}")

        // 1. 收集所有需要分析的文件
        val psiFiles = collectPsiFiles(config)
        logger.info("BatchAnalysisProcessor", "Collected ${psiFiles.size} PSI files for analysis")
        progressIndicator.text = "已收集 ${psiFiles.size} 个文件"

        // 2. 创建批处理计划
        val batchPlan = createBatchPlan(psiFiles, config)
        progressIndicator.text = "将分为 ${batchPlan.batches.size} 个批次处理"

        // 3. 执行批量分析
        val results = mutableMapOf<String, Any>()
        val startTime = System.currentTimeMillis()

        try {
            // 分批处理类分析
            val allClasses = mutableListOf<PsiClass>()
            batchPlan.batches.forEachIndexed { batchIndex, batch ->
                ProgressManager.checkCanceled()
                progressIndicator.text = "处理第 ${batchIndex + 1}/${batchPlan.batches.size} 批次"
                progressIndicator.fraction = batchIndex.toDouble() / batchPlan.batches.size

                val batchResult = processBatch(batch, config, progressIndicator)
                results.putAll(batchResult)
                allClasses.addAll(batch.classes)

                // 添加进度详细信息
                progressIndicator.text2 = "已处理 ${allClasses.size} 个类"
            }

            // 4. 合并结果
            progressIndicator.text = "合并分析结果..."
            val mergedResult = mergeAnalysisResults(results, allClasses, config)

            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info("BatchAnalysisProcessor", "批量分析完成，耗时: ${elapsedTime}ms，处理类数: ${allClasses.size}")

            return@withContext mergedResult

        } catch (e: CancellationException) {
            logger.info("BatchAnalysisProcessor", "用户取消批量分析")
            throw e
        } catch (e: Exception) {
            logger.error("BatchAnalysisProcessor", "批量分析失败", error = e)
            throw RuntimeException("批量分析失败: ${e.message}", e)
        }
    }

    /**
     * 收集PSI文件
     */
    private suspend fun collectPsiFiles(config: AnalysisConfig): List<PsiJavaFile> = withContext(Dispatchers.IO) {
        val scope = scopeController.createSearchScope(config)

        // 在ReadAction中收集PSI文件，确保线程安全
        return@withContext com.intellij.openapi.application.ReadAction.compute<List<PsiJavaFile>, com.intellij.openapi.progress.ProcessCanceledException> {
            ProgressManager.checkCanceled()

            val files = mutableListOf<PsiJavaFile>()
            val psiManager = PsiManager.getInstance(project)

            // 使用FileTypeIndex搜索Java文件
            val javaFileType = StdFileTypes.JAVA
            FileTypeIndex.processFiles(
                javaFileType,
                { virtualFile ->
                    ProgressManager.checkCanceled()
                    val psiFile = psiManager.findFile(virtualFile)
                    if (psiFile is PsiJavaFile && scopeController.shouldIncludePackage(psiFile.packageName, config)) {
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
     * 创建批处理计划
     */
    private fun createBatchPlan(psiFiles: List<PsiJavaFile>, config: AnalysisConfig): BatchPlan {
        return try {
            com.intellij.openapi.application.ReadAction.compute<BatchPlan, com.intellij.openapi.progress.ProcessCanceledException> {
                ProgressManager.checkCanceled()

                val allClasses = psiFiles.flatMap { psiFile ->
                    ProgressManager.checkCanceled()
                    psiFile.classes.toList()
                }

                val filteredClasses = scopeController.filterClassesForAnalysis(allClasses, config)

                // 根据项目大小确定批次大小
                val batchSize = determineBatchSize(filteredClasses.size, config)
                val batches = filteredClasses.chunked(batchSize)

                BatchPlan(
                    totalFiles = psiFiles.size,
                    totalClasses = allClasses.size,
                    filteredClasses = filteredClasses.size,
                    batchSize = batchSize,
                    batchCount = batches.size,
                    batches = batches.mapIndexed { index, classes ->
                        Batch(
                            id = index + 1,
                            classes = classes,
                            estimatedComplexity = estimateBatchComplexity(classes)
                        )
                    }
                )
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "创建批处理计划失败", mapOf("error" to e.message))
            // 返回空的批处理计划
            BatchPlan(
                totalFiles = psiFiles.size,
                totalClasses = 0,
                filteredClasses = 0,
                batchSize = 1,
                batchCount = 0,
                batches = emptyList()
            )
        }
    }

    /**
     * 处理单个批次
     */
    private suspend fun processBatch(
        batch: Batch,
        config: AnalysisConfig,
        progressIndicator: ProgressIndicator
    ): Map<String, Any> = withContext(Dispatchers.Default) {
        val batchResults = mutableMapOf<String, Any>()

        // 并行处理批次的类
        val classResults = batch.classes.map { psiClass ->
            async {
                ProgressManager.checkCanceled()
                analyzeClass(psiClass, config)
            }
        }.awaitAll()

        // 合并批次结果
        batchResults["classes"] = classResults
        batchResults["batchComplexity"] = classResults.sumOf { (it["complexity"] as? Int) ?: 0 }
        batchResults["batchSize"] = batch.classes.size

        return@withContext batchResults
    }

    /**
     * 分析单个类
     */
    private suspend fun analyzeClass(psiClass: PsiClass, config: AnalysisConfig): Map<String, Any> =
        withContext(Dispatchers.Default) {
            try {
                // 在ReadAction中执行所有PSI访问操作
                return@withContext com.intellij.openapi.application.ReadAction.compute<Map<String, Any>, com.intellij.openapi.progress.ProcessCanceledException> {
                    ProgressManager.checkCanceled()

                    // 1. 基础复杂度分析
                    val complexityMetrics = complexityCalculator.calculateClassComplexityMetrics(psiClass)

                    // 2. 依赖关系分析
                    val dependencies = javaDependencyExtractor.extractClassDependencies(psiClass)

                    // 3. 方法级详细分析
                    val methodDetailedMetrics = psiClass.methods.mapNotNull { method ->
                        try {
                            ProgressManager.checkCanceled()
                            val methodMetrics = MethodMetrics(
                                linesOfCode = complexityCalculator.countMethodLinesOfCode(method),
                                cyclomaticComplexity = complexityCalculator.calculateMethodCyclomaticComplexity(method),
                                cognitiveComplexity = complexityCalculator.calculateMethodCognitiveComplexity(method),
                                nestingDepth = complexityCalculator.calculateMethodNestingDepth(method),
                                fanIn = 0, // 需要在全局分析中计算
                                fanOut = dependencies.count { dep -> dep.referenceType == ReferenceType.ASSOCIATION },
                                parameterCount = method.parameterList.parametersCount,
                                maxCallDepth = javaDependencyExtractor.calculateMaxCallDepth(method),
                                localVariableCount = complexityCalculator.countMethodLocalVariables(method),
                                magicNumberCount = complexityCalculator.countMethodMagicNumbers(method),
                                longLineCount = complexityCalculator.countMethodLongLines(method),
                                returnStatementCount = complexityCalculator.countMethodReturnStatements(method),
                                booleanParameterCount = complexityCalculator.countMethodBooleanParameters(method),
                                codeSmells = emptyList(), // 将在全局分析中计算
                                complexityScore = 0, // 将在全局分析中计算
                                refactoringPriority = RefactoringPriority("", "", "")
                            )
                            method.name to methodMetrics
                        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                            // 重新抛出取消异常
                            throw e
                        } catch (e: Exception) {
                            logger.debug(
                                "BatchAnalysisProcessor",
                                "分析方法失败: ${method.name}",
                                mapOf("error" to e.message)
                            )
                            null
                        }
                    }.toMap()

                    mapOf(
                        Pair("className", psiClass.qualifiedName ?: ""),
                        "complexity" to complexityMetrics.cyclomaticComplexity,
                        "dependencyCount" to dependencies.size,
                        "methodCount" to psiClass.methods.size,
                        "fieldCount" to psiClass.fields.size,
                        "complexityMetrics" to complexityMetrics,
                        "dependencies" to dependencies,
                        "methodDetailedMetrics" to methodDetailedMetrics,
                        // POJO分析功能已移除，简化分析流程
                        "packageMetrics" to PackageMetrics(
                            fanIn = 0, // 将在全局分析中计算
                            fanOut = dependencies.distinctBy { it.className }.size,
                            instability = 0.0 // 将在全局分析中计算
                        )
                    )
                }

            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                // 重新抛出取消异常
                throw e
            } catch (e: Exception) {
                logger.warn(
                    "BatchAnalysisProcessor",
                    "分析类失败: ${psiClass.qualifiedName}",
                    mapOf("error" to e.message)
                )
                mapOf(
                    Pair("className", psiClass.qualifiedName ?: ""),
                    "error" to (e.message ?: ""),
                    "complexity" to 0,
                    "dependencyCount" to 0
                )
            }
        }

    /**
     * 合并分析结果
     */
    private suspend fun mergeAnalysisResults(
        results: Map<String, Any>,
        allClasses: List<PsiClass>,
        config: AnalysisConfig
    ): DependencyAnalysisResult = withContext(Dispatchers.Default) {

        // 1. 提取基础分析结果
        val classResults = results.values.filterIsInstance<List<Map<String, Any>>>().flatten()
        val complexityMetrics = mutableMapOf<String, ClassComplexityMetrics>()
        val allDependencies = mutableMapOf<String, List<ClassReference>>()
        val methodMetricsMap = mutableMapOf<String, Map<String, MethodMetrics>>()
        val pojoClasses = mutableListOf<PsiClass>()

        classResults.forEach { classResult ->
            val className = classResult["className"] as? String
            val metrics = classResult["complexityMetrics"] as? ClassComplexityMetrics

            @Suppress("UNCHECKED_CAST")
            val dependencies = classResult["dependencies"] as? List<ClassReference>

            @Suppress("UNCHECKED_CAST")
            val methodDetailedMetrics = classResult["methodDetailedMetrics"] as? Map<String, Any>
            val isPojo = classResult["isPojo"] as? Boolean ?: false

            if (className != null) {
                metrics?.let { complexityMetrics[className] = it }
                dependencies?.let { allDependencies[className] = it }
                methodDetailedMetrics?.let { methodMetricsMap[className] = convertToMethodMetrics(it) }

                val psiClass = allClasses.find { it.qualifiedName == className }
                if (psiClass != null && isPojo) {
                    pojoClasses.add(psiClass)
                }
            }
        }

        // 2. 计算全局指标
        val enhancedComplexityMetrics = calculateGlobalComplexityMetrics(complexityMetrics, methodMetricsMap, config)

        // 3. 提取方法调用（POJO分析已移除，简化分析流程）
        val methodCalls = extractMethodCalls(allClasses, config.maxDepth)
        val pojoUsages = emptyList<PojoUsage>() // 保留空列表以维持API兼容性

        // 4. 构建包级依赖图
        val packageDependencyGraph = javaDependencyExtractor.buildPackageDependencyGraph(allClasses, allDependencies)
        val packageDependencies = buildPackageDependencies(packageDependencyGraph, allDependencies)

        // 5. 场景定义分析
        val businessEntryPoints = analyzeBusinessEntryPoints(allClasses)
        val sceneDefinitions = buildSceneDefinitions(businessEntryPoints, enhancedComplexityMetrics)

        // 6. 构建调用关系图
        val callGraph = buildCallGraph(methodCalls)

        // 7. 生成详细数据模型
        val packages = buildPackageInfos(packageDependencyGraph, enhancedComplexityMetrics)
        val classes = buildClassInfos(allClasses, enhancedComplexityMetrics, methodMetricsMap, pojoUsages)
        val methods = buildMethodInfos(allClasses, methodMetricsMap)
        val fields = buildFieldInfos(allClasses)

        // 8. 构建分析元数据
        val metadata = buildAnalysisMetadata(allClasses, config)

        // 9. 项目信息
        val projectInfo = ProjectInfo(
            name = project.name,
            rootPackage = config.excludePackages.firstOrNull() ?: "",
            totalClasses = allClasses.size,
            totalPackages = packages.size,
            totalMethods = methods.size
        )

        // 10. 检测代码坏味道
        val codeSmellDetector = CodeSmellDetector()
        val codeSmells = codeSmellDetector.detectCodeSmells(enhancedComplexityMetrics, config)

        return@withContext DependencyAnalysisResult(
            metadata = metadata,
            packages = packages,
            classes = classes,
            methods = methods,
            fields = fields,
            pojos = pojoUsages,
            callGraph = callGraph,
            sceneDefinitions = sceneDefinitions,
            projectInfo = projectInfo,
            packageDependencies = packageDependencies,
            classDependencies = buildClassDependencies(allDependencies),
            methodCalls = methodCalls,
            businessEntryPoints = businessEntryPoints,
            complexityMetrics = enhancedComplexityMetrics,
            codeSmells = codeSmells,
            analysisConfig = config,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 计算全局复杂度指标（包含评分和优先级）
     */
    private suspend fun calculateGlobalComplexityMetrics(
        baseMetrics: Map<String, ClassComplexityMetrics>,
        methodMetricsMap: Map<String, Map<String, MethodMetrics>>,
        config: AnalysisConfig
    ): Map<String, ClassComplexityMetrics> = withContext(Dispatchers.Default) {
        val thresholds = AnalysisThresholds.standardDetailedThresholds
        val enhancedMetrics = mutableMapOf<String, ClassComplexityMetrics>()

        baseMetrics.forEach { (className, metrics) ->
            val classCodeSmells = CodeSmellDetector().detectCodeSmells(mapOf(className to metrics), config)
            val methodMetrics = methodMetricsMap[className] ?: emptyMap()

            // 计算增强的复杂度指标
            val enhancedMetricsValue = metrics.copy(
                // 这里可以添加更多增强字段
            )
            enhancedMetrics[className] = enhancedMetricsValue
        }

        return@withContext enhancedMetrics
    }

    /**
     * 转换方法指标数据
     */
    private fun convertToMethodMetrics(methodDetailedMetrics: Map<String, Any>): Map<String, MethodMetrics> {
        return methodDetailedMetrics.mapValues { (_, value) ->
            value as MethodMetrics
        }
    }

    /**
     * 提取方法调用
     */
    private fun extractMethodCalls(allClasses: List<PsiClass>, maxDepth: Int): List<MethodCall> {
        return try {
            com.intellij.openapi.application.ReadAction.compute<List<MethodCall>, com.intellij.openapi.progress.ProcessCanceledException> {
                allClasses.flatMap { psiClass ->
                    ProgressManager.checkCanceled()
                    psiClass.methods.flatMap { method ->
                        ProgressManager.checkCanceled()
                        try {
                            javaDependencyExtractor.extractMethodCallChain(method, maxDepth)
                        } catch (e: Exception) {
                            logger.debug(
                                "BatchAnalysisProcessor",
                                "提取方法调用链失败: ${method.name}",
                                mapOf("error" to e.message)
                            )
                            emptyList()
                        }
                    }
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "提取方法调用失败", mapOf("error" to e.message))
            emptyList()
        }
    }

    /**
     * 分析业务入口点
     */
    private fun analyzeBusinessEntryPoints(allClasses: List<PsiClass>): List<BusinessEntryPoint> {
        return try {
            com.intellij.openapi.application.ReadAction.compute<List<BusinessEntryPoint>, com.intellij.openapi.progress.ProcessCanceledException> {
                allClasses.flatMap { psiClass ->
                    ProgressManager.checkCanceled()
                    psiClass.methods.filter { method ->
                        ProgressManager.checkCanceled()
                        // 检查是否有业务相关注解
                        method.annotations.any { annotation ->
                            val qualifiedName = annotation.qualifiedName
                            qualifiedName?.let { name ->
                                name.contains("Controller") ||
                                        name.contains("Service") ||
                                        name.contains("Scheduled") ||
                                        name.contains("RequestMapping") ||
                                        name.contains("GetMapping") ||
                                        name.contains("PostMapping") ||
                                        name.contains("KafkaListener") ||
                                        name.contains("EventListener")
                            } ?: false
                        }
                    }.map { method ->
                        BusinessEntryPoint(
                            className = psiClass.qualifiedName ?: "",
                            methodName = method.name,
                            entryType = determineEntryType(method),
                            annotations = method.annotations.mapNotNull { it.qualifiedName },
                            businessScenario = determineBusinessScenario(psiClass, method),
                            parameters = method.parameterList.parameters.map { param ->
                                ParameterInfo(
                                    name = param.name ?: "",
                                    type = param.type.canonicalText,
                                    annotations = param.annotations.mapNotNull { it.qualifiedName }
                                )
                            },
                            httpMapping = extractHttpMapping(method)
                        )
                    }
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "分析业务入口点失败", mapOf("error" to e.message))
            emptyList()
        }
    }

    /**
     * 确定入口类型
     */
    private fun determineEntryType(method: PsiMethod): EntryType {
        val annotations = method.annotations.mapNotNull { it.qualifiedName }
        return when {
            annotations.any { it.contains("Mapping") || it.contains("Controller") } -> EntryType.CONTROLLER
            annotations.any { it.contains("Scheduled") } -> EntryType.SCHEDULED
            annotations.any { it.contains("Listener") } -> EntryType.EVENT_LISTENER
            annotations.any { it.contains("Kafka") } -> EntryType.MESSAGE_CONSUMER
            method.name == "main" -> EntryType.MAIN
            else -> EntryType.SERVICE
        }
    }

    /**
     * 确定业务场景
     */
    private fun determineBusinessScenario(psiClass: PsiClass, method: PsiMethod): String {
        val className = psiClass.name?.lowercase() ?: ""
        val methodName = method.name.lowercase()

        return when {
            className.contains("user") || methodName.contains("user") -> "用户管理"
            className.contains("order") || methodName.contains("order") -> "订单处理"
            className.contains("payment") || methodName.contains("payment") -> "支付处理"
            className.contains("device") || methodName.contains("device") -> "设备管理"
            className.contains("scene") || methodName.contains("scene") -> "场景管理"
            else -> "通用业务"
        }
    }

    /**
     * 提取HTTP映射
     */
    private fun extractHttpMapping(method: PsiMethod): String? {
        val annotations = method.annotations.mapNotNull { it.qualifiedName }
        return when {
            annotations.any { it.contains("GetMapping") } -> "GET"
            annotations.any { it.contains("PostMapping") } -> "POST"
            annotations.any { it.contains("PutMapping") } -> "PUT"
            annotations.any { it.contains("DeleteMapping") } -> "DELETE"
            annotations.any { it.contains("RequestMapping") } -> "REQUEST"
            else -> null
        }
    }

    /**
     * 构建调用关系图
     */
    private fun buildCallGraph(methodCalls: List<MethodCall>): CallGraph {
        val edges = methodCalls.mapIndexed { index, call ->
            CallEdge(
                id = "edge-$index",
                source = "${call.callerClass}.${call.callerMethod}",
                target = "${call.calleeClass}.${call.calleeMethod}",
                type = CallEdgeType.METHOD_CALL,
                callContext = CallContext(
                    callCount = 1, // 简化处理
                    callLocations = listOf(
                        CallLocation(
                            line = call.location.lineNumber,
                            column = call.location.columnNumber,
                            context = "method_call"
                        )
                    )
                ),
                depth = call.callDepth,
                weight = 1
            )
        }

        return CallGraph(edges = edges)
    }

    /**
     * 构建场景定义
     */
    private fun buildSceneDefinitions(
        entryPoints: List<BusinessEntryPoint>,
        complexityMetrics: Map<String, ClassComplexityMetrics>
    ): List<SceneDefinition> {
        return entryPoints.groupBy { it.businessScenario }.map { (scenario, entries) ->
            val coverage = calculateSceneCoverage(entries, complexityMetrics)

            SceneDefinition(
                id = "scene-$scenario",
                name = scenario,
                description = "基于${entries.size}个入口点的 $scenario 场景",
                entryMethods = entries.map { "${it.className}.${it.methodName}" },
                category = determineSceneCategory(entries),
                tags = listOf(scenario),
                coverage = coverage
            )
        }
    }

    /**
     * 计算场景覆盖范围
     */
    private fun calculateSceneCoverage(
        entries: List<BusinessEntryPoint>,
        complexityMetrics: Map<String, ClassComplexityMetrics>
    ): SceneCoverage {
        val involvedClasses = entries.map { it.className }.toSet()
        val methodCount = entries.size
        val classCount = involvedClasses.size
        val packageCount = involvedClasses.map { it.substringBeforeLast(".") }.distinct().size
        val maxDepth = 10 // 简化处理

        return SceneCoverage(
            methodCount = methodCount,
            classCount = classCount,
            packageCount = packageCount,
            maxDepth = maxDepth
        )
    }

    /**
     * 确定场景类别
     */
    private fun determineSceneCategory(entries: List<BusinessEntryPoint>): SceneCategory {
        val entryTypes = entries.map { it.entryType }
        return when {
            entryTypes.any { it == EntryType.CONTROLLER } -> SceneCategory.USER_TRIGGER
            entryTypes.any { it == EntryType.SCHEDULED } -> SceneCategory.SCHEDULED
            entryTypes.any { it == EntryType.EVENT_LISTENER || it == EntryType.MESSAGE_CONSUMER } -> SceneCategory.EVENT_DRIVEN
            else -> SceneCategory.API
        }
    }

    /**
     * 构建包信息
     */
    private fun buildPackageInfos(
        packageDependencyGraph: PackageDependencyGraph,
        complexityMetrics: Map<String, ClassComplexityMetrics>
    ): List<PackageInfo> {
        return packageDependencyGraph.packages.map { packageName ->
            PackageInfo(
                id = packageName,
                name = packageName.substringAfterLast("."),
                fullName = packageName,
                parentPackage = packageName.substringBeforeLast(".", ""),
                level = packageName.count { it == '.' } + 1,
                classCount = complexityMetrics.keys.count { it.startsWith("$packageName.") },
                metrics = PackageMetrics(
                    fanIn = packageDependencyGraph.fanIn[packageName] ?: 0,
                    fanOut = packageDependencyGraph.fanOut[packageName] ?: 0,
                    instability = 0.0 // 简化处理
                )
            )
        }
    }

    /**
     * 构建类信息
     */
    private fun buildClassInfos(
        allClasses: List<PsiClass>,
        complexityMetrics: Map<String, ClassComplexityMetrics>,
        methodMetricsMap: Map<String, Map<String, MethodMetrics>>,
        pojoUsages: List<PojoUsage>
    ): List<ClassInfo> {
        return try {
            com.intellij.openapi.application.ReadAction.compute<List<ClassInfo>, com.intellij.openapi.progress.ProcessCanceledException> {
                allClasses.map { psiClass ->
                    ProgressManager.checkCanceled()
                    val className = psiClass.qualifiedName ?: ""
                    val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
                    val metrics = complexityMetrics[className]
                    val methodMetrics = methodMetricsMap[className] ?: emptyMap()
                    val pojoUsage = pojoUsages.find { it.qualifiedName == className }

                    // 安全获取源文件路径
                    val sourceFile = try {
                        psiClass.containingFile.virtualFile.path
                    } catch (e: Exception) {
                        logger.debug(
                            "BatchAnalysisProcessor",
                            "获取源文件路径失败: ${className}",
                            mapOf("error" to e.message)
                        )
                        ""
                    }

                    ClassInfo(
                        id = className,
                        name = psiClass.name ?: "",
                        qualifiedName = className,
                        packageId = packageName,
                        type = determineClassType(psiClass),
                        modifiers = extractModifiers(psiClass),
                        isTest = isTestClass(psiClass),
                        sourceFile = sourceFile,
                        annotations = psiClass.annotations.mapNotNull { it.qualifiedName },
                        superClass = psiClass.superClass?.qualifiedName,
                        interfaces = psiClass.interfaces.mapNotNull { it.qualifiedName },
                        metrics = buildClassDetailedMetrics(metrics, methodMetrics, pojoUsage)
                    )
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "构建类信息失败", mapOf("error" to e.message))
            emptyList()
        }
    }

    /**
     * 构建方法信息
     */
    private fun buildMethodInfos(
        allClasses: List<PsiClass>,
        methodMetricsMap: Map<String, Map<String, MethodMetrics>>
    ): List<MethodInfo> {
        return try {
            com.intellij.openapi.application.ReadAction.compute<List<MethodInfo>, com.intellij.openapi.progress.ProcessCanceledException> {
                allClasses.flatMap { psiClass ->
                    ProgressManager.checkCanceled()
                    val className = psiClass.qualifiedName ?: ""
                    val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
                    val methodMetrics = methodMetricsMap[className] ?: emptyMap()

                    psiClass.methods.map { method ->
                        ProgressManager.checkCanceled()
                        val methodName = method.name
                        val metrics = methodMetrics[methodName] ?: MethodMetrics(
                            linesOfCode = 0,
                            cyclomaticComplexity = 0,
                            cognitiveComplexity = 0,
                            nestingDepth = 0,
                            fanIn = 0,
                            fanOut = 0,
                            parameterCount = 0,
                            maxCallDepth = 0,
                            localVariableCount = 0,
                            magicNumberCount = 0,
                            longLineCount = 0,
                            returnStatementCount = 0,
                            booleanParameterCount = 0,
                            codeSmells = emptyList(),
                            complexityScore = 0,
                            refactoringPriority = RefactoringPriority("", "", "")
                        )

                        // 安全获取位置信息
                        val location = try {
                            val document = method.containingFile.viewProvider.document
                            if (document != null) {
                                val lineStart =
                                    document.getLineStartOffset(document.getLineNumber(method.textRange.startOffset))
                                SourceLocation(
                                    filePath = psiClass.containingFile.virtualFile.path,
                                    lineNumber = document.getLineNumber(method.textRange.startOffset) + 1,
                                    columnNumber = method.textRange.startOffset - lineStart + 1
                                )
                            } else {
                                SourceLocation(
                                    filePath = psiClass.containingFile.virtualFile.path,
                                    lineNumber = 0,
                                    columnNumber = 0
                                )
                            }
                        } catch (e: Exception) {
                            logger.debug(
                                "BatchAnalysisProcessor",
                                "获取方法位置信息失败: ${method.name}",
                                mapOf("error" to e.message)
                            )
                            SourceLocation(
                                filePath = psiClass.containingFile.virtualFile.path,
                                lineNumber = 0,
                                columnNumber = 0
                            )
                        }

                        MethodInfo(
                            id = "$className.$methodName",
                            name = methodName,
                            className = psiClass.name ?: "",
                            classId = className,
                            packageId = packageName,
                            signature = buildMethodSignature(method),
                            qualifiedSignature = "$className.$methodName",
                            modifiers = extractModifiers(method),
                            isStatic = method.hasModifierProperty("static"),
                            isConstructor = method.isConstructor,
                            isAbstract = method.hasModifierProperty("abstract"),
                            annotations = method.annotations.mapNotNull { it.qualifiedName },
                            parameters = method.parameterList.parameters.map { param ->
                                ParameterDetail(
                                    name = param.name ?: "",
                                    type = param.type.canonicalText,
                                    annotations = param.annotations.mapNotNull { it.qualifiedName }
                                )
                            },
                            returnType = method.returnType?.canonicalText ?: "void",
                            throwsExceptions = emptyList(), // 简化处理
                            metrics = metrics,
                            location = location,
                            usedTypes = extractUsedTypes(method),
                            tags = MethodTags(
                                isEntryPoint = hasEntryPointAnnotation(method),
                                isPublicApi = method.hasModifierProperty("public"),
                                isDeprecated = method.annotations.any { it.qualifiedName?.contains("Deprecated") == true },
                                sceneNames = emptyList() // 简化处理
                            )
                        )
                    }
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "构建方法信息失败", mapOf("error" to e.message))
            emptyList()
        }
    }

    /**
     * 构建字段信息
     */
    private fun buildFieldInfos(allClasses: List<PsiClass>): List<FieldInfo> {
        return try {
            com.intellij.openapi.application.ReadAction.compute<List<FieldInfo>, com.intellij.openapi.progress.ProcessCanceledException> {
                allClasses.flatMap { psiClass ->
                    ProgressManager.checkCanceled()
                    val className = psiClass.qualifiedName ?: ""

                    psiClass.fields.map { field ->
                        ProgressManager.checkCanceled()
                        FieldInfo(
                            id = "$className.${field.name}",
                            name = field.name ?: "",
                            classId = className,
                            type = field.type.canonicalText,
                            modifiers = extractModifiers(field),
                            isStatic = field.hasModifierProperty("static"),
                            isFinal = field.hasModifierProperty("final"),
                            annotations = field.annotations.mapNotNull { it.qualifiedName },
                            initializer = field.initializer?.text
                        )
                    }
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "构建字段信息失败", mapOf("error" to e.message))
            emptyList()
        }
    }

    // 辅助方法
    private fun determineClassType(psiClass: PsiClass): ClassType {
        return when {
            psiClass.isInterface -> ClassType.INTERFACE
            psiClass.hasModifierProperty("abstract") -> ClassType.ABSTRACT_CLASS
            psiClass.isEnum -> ClassType.ENUM
            psiClass.name?.contains("Record") == true -> ClassType.RECORD
            else -> ClassType.CLASS
        }
    }

    private fun extractModifiers(psiElement: PsiModifierListOwner): List<String> {
        val modifierList = psiElement.modifierList ?: return emptyList()
        return modifierList.text.split(" ").filter { it.isNotBlank() }
    }

    private fun isTestClass(psiClass: PsiClass): Boolean {
        val className = psiClass.name ?: return false
        return className.endsWith("Test") ||
                className.startsWith("Test") ||
                psiClass.annotations.any { it.qualifiedName?.contains("Test") == true }
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") {
            "${it.type.canonicalText} ${it.name}"
        }
        val returnType = method.returnType?.canonicalText ?: "void"
        return "$returnType ${method.name}($params)"
    }

    private fun extractUsedTypes(method: PsiMethod): List<String> {
        val types = mutableSetOf<String>()

        // 参数类型
        method.parameterList.parameters.forEach { param ->
            types.add(param.type.canonicalText)
        }

        // 返回类型
        method.returnType?.let { types.add(it.canonicalText) }

        // 简化处理：可以从方法体中提取更多类型
        return types.toList()
    }

    private fun hasEntryPointAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName?.let { name ->
                name.contains("Controller") ||
                        name.contains("Service") ||
                        name.contains("Scheduled") ||
                        name.contains("Mapping")
            } ?: false
        }
    }

    private fun buildClassDetailedMetrics(
        metrics: ClassComplexityMetrics?,
        methodMetrics: Map<String, MethodMetrics>,
        pojoUsage: PojoUsage?
    ): ClassDetailedMetrics {
        return ClassDetailedMetrics(
            methodCount = metrics?.methodCount ?: 0,
            fieldCount = metrics?.fieldCount ?: 0,
            linesOfCode = metrics?.lineOfCode ?: 0,
            fanIn = metrics?.couplingMetrics?.afferentCoupling ?: 0,
            fanOut = metrics?.couplingMetrics?.efferentCoupling ?: 0,
            coupling = (metrics?.couplingMetrics?.afferentCoupling ?: 0) + (metrics?.couplingMetrics?.efferentCoupling
                ?: 0),
            cohesion = 1.0, // 简化处理
            codeSmells = emptyList(), // 简化处理
            complexityScore = methodMetrics.values.map { it.complexityScore }.average().toInt(),
            refactoringPriority = RefactoringPriority("", "", ""),
            location = SourceLocation("", 0, 0),
            usedTypes = emptyList(), // 简化处理
            tags = MethodTags(false, false, false, emptyList())
        )
    }

    private fun buildClassDependencies(allDependencies: Map<String, List<ClassReference>>): List<ClassDependency> {
        return allDependencies.flatMap { (className, dependencies) ->
            dependencies.map { dep ->
                ClassDependency(
                    className = className,
                    packageName = getPackageName(dep.className) ?: "",
                    superClass = null,
                    interfaces = emptyList(),
                    dependencies = listOf(dep),
                    dependents = emptyList(),
                    dependencyCount = 1,
                    isPojo = false,
                    isController = false,
                    isService = false,
                    isRepository = false
                )
            }
        }
    }

    private fun buildPackageDependencies(
        packageDependencyGraph: PackageDependencyGraph,
        allDependencies: Map<String, List<ClassReference>>
    ): List<PackageDependency> {
        // 使用JavaDependencyExtractor检测循环依赖
        val javaExtractor = JavaDependencyExtractor()
        val cycles = javaExtractor.detectCircularDependencies(packageDependencyGraph)

        return packageDependencyGraph.packages.map { packageName ->
            PackageDependency(
                packageName = packageName,
                dependencies = packageDependencyGraph.dependencies[packageName] ?: emptyList(),
                dependents = calculateDependents(packageName, packageDependencyGraph), // 反向计算
                dependencyCount = (packageDependencyGraph.dependencies[packageName]?.size ?: 0),
                cycles = cycles // 使用真正的循环依赖检测结果
            )
        }
    }

    /**
     * 计算依赖当前包的其他包（反向依赖）
     */
    private fun calculateDependents(
        targetPackage: String,
        packageDependencyGraph: PackageDependencyGraph
    ): List<String> {
        val dependents = mutableListOf<String>()
        packageDependencyGraph.dependencies.forEach { (sourcePackage, dependencies) ->
            if (targetPackage in dependencies) {
                dependents.add(sourcePackage)
            }
        }
        return dependents
    }

    private fun getPackageName(qualifiedName: String): String? {
        val lastDotIndex = qualifiedName.lastIndexOf('.')
        return if (lastDotIndex > 0) qualifiedName.substring(0, lastDotIndex) else null
    }

    private fun buildAnalysisMetadata(allClasses: List<PsiClass>, config: AnalysisConfig): AnalysisMetadata {
        // 在ReadAction中执行所有PSI访问操作
        val statistics = try {
            com.intellij.openapi.application.ReadAction.compute<AnalysisStatistics, com.intellij.openapi.progress.ProcessCanceledException> {
                ProgressManager.checkCanceled()

                val totalPackages = allClasses.mapNotNull {
                    ProgressManager.checkCanceled()
                    (it.containingFile as? PsiJavaFile)?.packageName
                }.distinct().size

                val totalMethods = allClasses.sumOf {
                    ProgressManager.checkCanceled()
                    it.methods.size
                }

                AnalysisStatistics(
                    totalPackages = totalPackages,
                    totalClasses = allClasses.size,
                    totalMethods = totalMethods,
                    totalCallEdges = 0 // 简化处理
                )
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "构建分析元数据失败，使用默认值", mapOf("error" to e.message))
            // 提供默认的统计信息
            AnalysisStatistics(
                totalPackages = 0,
                totalClasses = allClasses.size,
                totalMethods = 0,
                totalCallEdges = 0
            )
        }

        return AnalysisMetadata(
            projectName = "Nekoama Analysis",
            moduleName = "Code Structure Analysis",
            analysisTime = java.time.Instant.now().toString(),
            scope = AnalysisScope(
                rootPackage = "",
                includedPackages = emptyList(),
                excludedPackages = config.excludePackages,
                maxDepth = config.maxDepth
            ),
            statistics = statistics
        )
    }

    /**
     * 确定批次大小
     */
    private fun determineBatchSize(classCount: Int, config: AnalysisConfig): Int {
        // 根据项目大小和复杂度阈值动态调整批次大小
        val baseBatchSize = when {
            classCount < 100 -> 20
            classCount < 500 -> 50
            classCount < 2000 -> 100
            classCount < 10000 -> 200
            else -> 300
        }

        // 根据复杂度阈值调整
        val complexityAdjustment = when {
            config.complexityThresholds.cyclomaticComplexity < 8 -> 0.8 // 更严格的阈值，小批次
            config.complexityThresholds.cyclomaticComplexity > 15 -> 1.5 // 更宽松的阈值，大批次
            else -> 1.0
        }

        return (baseBatchSize * complexityAdjustment).toInt()
    }

    /**
     * 估算批次复杂度
     */
    private fun estimateBatchComplexity(classes: List<PsiClass>): Int {
        return try {
            com.intellij.openapi.application.ReadAction.compute<Int, com.intellij.openapi.progress.ProcessCanceledException> {
                classes.sumOf { psiClass ->
                    ProgressManager.checkCanceled()
                    // 简单的复杂度估算：方法数 * 字段数 * 深度系数
                    val methodCount = psiClass.methods.size
                    val fieldCount = psiClass.fields.size
                    val depthFactor = calculateNestingDepth(psiClass).coerceAtLeast(1)

                    methodCount * fieldCount * depthFactor
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "估算批次复杂度失败", mapOf("error" to e.message))
            1 // 返回最小复杂度
        }
    }

    /**
     * 计算嵌套深度（简化版）
     */
    private fun calculateNestingDepth(psiClass: PsiClass): Int {
        return try {
            com.intellij.openapi.application.ReadAction.compute<Int, com.intellij.openapi.progress.ProcessCanceledException> {
                var maxDepth = 0
                psiClass.methods.forEach { method ->
                    ProgressManager.checkCanceled()
                    method.accept(object : JavaRecursiveElementVisitor() {
                        var currentDepth = 0
                        override fun visitIfStatement(statement: PsiIfStatement) {
                            currentDepth++
                            maxDepth = maxOf(maxDepth, currentDepth)
                            super.visitIfStatement(statement)
                            currentDepth--
                        }
                    })
                }
                maxDepth
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.debug("BatchAnalysisProcessor", "计算嵌套深度失败", mapOf("error" to e.message))
            1 // 返回最小深度
        }
    }

    /**
     * 带进度反馈的异步分析
     */
    fun executeWithProgress(
        config: AnalysisConfig,
        progressTitle: String = "代码依赖分析"
    ): DependencyAnalysisResult {
        val task = object : Task.Backgroundable(project, progressTitle, true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // 直接在后台任务中执行分析，避免嵌套runBlocking
                    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    val analysisResult = scope.async {
                        executeBatchAnalysis(config, indicator)
                    }

                    // 等待分析完成
                    runBlocking {
                        analysisResult.await()
                    }
                } catch (e: Exception) {
                    logger.error("BatchAnalysisProcessor", "带进度的分析失败", error = e)
                    throw e
                }
            }
        }

        // 启动后台任务并等待结果
        ProgressManager.getInstance().run(task)

        // 返回占位结果，实际结果应该在Task中处理
        throw UnsupportedOperationException("需要在Task中处理结果返回")
    }

    /**
     * 增量分析（只分析修改的类）
     */
    suspend fun executeIncrementalAnalysis(
        config: AnalysisConfig,
        modifiedClasses: List<PsiClass>,
        baselineResult: DependencyAnalysisResult?
    ): DependencyAnalysisResult = withContext(Dispatchers.IO) {
        logger.info("BatchAnalysisProcessor", "执行增量分析，修改类数: ${modifiedClasses.size}")

        if (baselineResult == null) {
            throw IllegalArgumentException("增量分析需要提供有效的基准结果或ProgressIndicator实例")
        }

        // 在ReadAction中分析修改的类
        val modifiedMetrics = try {
            com.intellij.openapi.application.ReadAction.compute<Map<String, ClassComplexityMetrics>, com.intellij.openapi.progress.ProcessCanceledException> {
                mutableMapOf<String, ClassComplexityMetrics>().apply {
                    modifiedClasses.forEach { psiClass ->
                        ProgressManager.checkCanceled()
                        val metrics = complexityCalculator.calculateClassComplexityMetrics(psiClass)
                        this[psiClass.qualifiedName ?: ""] = metrics
                    }
                }
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.warn("BatchAnalysisProcessor", "增量分析PSI操作失败", mapOf("error" to e.message))
            emptyMap()
        }

        // 合并到基线结果
        val mergedMetrics = baselineResult.complexityMetrics.toMutableMap()
        mergedMetrics.putAll(modifiedMetrics)

        // 重新检测代码坏味道
        val codeSmellDetector = CodeSmellDetector()
        val updatedCodeSmells = codeSmellDetector.detectCodeSmells(mergedMetrics, config)

        return@withContext baselineResult.copy(
            complexityMetrics = mergedMetrics,
            codeSmells = updatedCodeSmells,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 进度监听器接口
     */
    interface ProgressListener {
        fun onProgress(status: AnalysisStatus)
    }

    /**
     * 分析状态跟踪
     */
    class AnalysisStatusTracker {
        private val processedClasses = AtomicInteger(0)
        private val totalClasses = AtomicInteger(0)
        private val currentBatch = AtomicInteger(0)
        private val totalBatches = AtomicInteger(0)
        private val startTime = System.currentTimeMillis()
        private var progressListener: ProgressListener? = null

        fun setProgressListener(listener: ProgressListener) {
            this.progressListener = listener
        }

        fun initialize(totalClasses: Int, totalBatches: Int) {
            this.totalClasses.set(totalClasses)
            this.totalBatches.set(totalBatches)
            this.processedClasses.set(0)
            this.currentBatch.set(0)
            notifyProgress()
        }

        fun updateBatch(batchIndex: Int) {
            currentBatch.set(batchIndex + 1)
            notifyProgress()
        }

        fun updateProcessedClasses(count: Int) {
            processedClasses.addAndGet(count)
            notifyProgress()
        }

        private fun notifyProgress() {
            progressListener?.onProgress(getStatus())
        }

        fun getStatus(): AnalysisStatus {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = if (totalClasses.get() > 0) {
                processedClasses.get().toDouble() / totalClasses.get()
            } else 0.0

            return AnalysisStatus(
                totalClasses = totalClasses.get(),
                processedClasses = processedClasses.get(),
                currentBatch = currentBatch.get(),
                totalBatches = totalBatches.get(),
                progress = progress,
                elapsedTimeMs = elapsed,
                estimatedRemainingTimeMs = if (progress > 0) {
                    ((elapsed / progress) - elapsed).toLong()
                } else 0
            )
        }
    }

    /**
     * 分析配置优化器
     */
    fun optimizeConfigForPerformance(
        baseConfig: AnalysisConfig,
        classCount: Int,
        targetTimeMinutes: Int
    ): AnalysisConfig {
        val estimatedTime = scopeController.estimateAnalysisComplexity(baseConfig, classCount).estimatedTimeMinutes

        return when {
            estimatedTime <= targetTimeMinutes -> baseConfig // 无需优化
            estimatedTime > targetTimeMinutes * 2 -> {
                // 大幅优化
                baseConfig.copy(
                    maxDepth = baseConfig.maxDepth / 2,
                    complexityThresholds = baseConfig.complexityThresholds.copy(
                        cyclomaticComplexity = baseConfig.complexityThresholds.cyclomaticComplexity * 2,
                        methodLength = baseConfig.complexityThresholds.methodLength * 2,
                        classLength = baseConfig.complexityThresholds.classLength * 2
                    )
                )
            }

            else -> {
                // 适度优化
                baseConfig.copy(
                    maxDepth = (baseConfig.maxDepth * 0.75).toInt(),
                    complexityThresholds = baseConfig.complexityThresholds.copy(
                        cyclomaticComplexity = (baseConfig.complexityThresholds.cyclomaticComplexity * 1.5).toInt(),
                        methodLength = (baseConfig.complexityThresholds.methodLength * 1.5).toInt()
                    )
                )
            }
        }
    }

    /**
     * 批处理计划
     */
    data class BatchPlan(
        val totalFiles: Int,
        val totalClasses: Int,
        val filteredClasses: Int,
        val batchSize: Int,
        val batchCount: Int,
        val batches: List<Batch>
    )

    /**
     * 批次
     */
    data class Batch(
        val id: Int,
        val classes: List<PsiClass>,
        val estimatedComplexity: Int
    )

    /**
     * 分析状态
     */
    data class AnalysisStatus(
        val totalClasses: Int,
        val processedClasses: Int,
        val currentBatch: Int,
        val totalBatches: Int,
        val progress: Double,
        val elapsedTimeMs: Long,
        val estimatedRemainingTimeMs: Long
    )

    companion object {
        /**
         * 默认批次大小
         */
        const val DEFAULT_BATCH_SIZE = 100

        /**
         * 最大批次大小
         */
        const val MAX_BATCH_SIZE = 500

        /**
         * 最小批次大小
         */
        const val MIN_BATCH_SIZE = 10
    }
}