package com.cw2.nekoama.core.reporting

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 场景重叠详情
 */
internal data class SceneOverlapDetail(
    val overlapScore: Double,
    val sharedClasses: Set<String>,
    val sharedMethods: Set<String>
)

/**
 * 场景交叉分析器
 *
 * 提供业务场景之间的交叉分析功能，包括：
 * - 场景重叠度分析
 * - 共享代码路径识别
 * - 场景性能影响评估
 * - 数据访问模式分析
 * - 场景间依赖关系
 * - 业务边界识别
 */
class SceneOverlapAnalyzer {

    private val logger = NekoamaLogger

    /**
     * 执行场景交叉分析
     */
    suspend fun analyzeSceneOverlap(
        analysisResult: DependencyAnalysisResult
    ): SceneOverlapResult = withContext(Dispatchers.Default) {
        logger.info("SceneAnalysis", "开始场景交叉分析")

        try {
            // 构建场景依赖图
            val sceneGraph = buildSceneDependencyGraph(analysisResult)

            // 分析场景重叠度
            val overlapAnalysis = analyzeSceneOverlap(sceneGraph, analysisResult)

            // 识别共享代码路径
            val sharedCodePaths = identifySharedCodePaths(sceneGraph, analysisResult)

            // 分析场景性能影响
            val performanceImpact = analyzeScenePerformanceImpact(sceneGraph, analysisResult)

            // 分析数据访问模式
            val dataAccessPatterns = analyzeDataAccessPatterns(sceneGraph, analysisResult)

            // 识别业务边界
            val businessBoundaries = identifyBusinessBoundaries(sceneGraph, analysisResult)

            // 检测场景冲突
            val sceneConflicts = detectSceneConflicts(sceneGraph, analysisResult)

            // 分析场景隔离度
            val isolationAnalysis = analyzeSceneIsolation(sceneGraph, analysisResult)

            // 识别重构机会
            val refactoringOpportunities = identifySceneRefactoringOpportunities(sceneGraph, analysisResult)

            // 计算场景健康度
            val sceneHealthScores = calculateSceneHealthScores(sceneGraph, analysisResult)

            logger.info("SceneAnalysis", "场景交叉分析完成")

            SceneOverlapResult(
                sceneGraph = sceneGraph,
                overlapAnalysis = overlapAnalysis,
                sharedCodePaths = sharedCodePaths,
                performanceImpact = performanceImpact,
                dataAccessPatterns = dataAccessPatterns,
                businessBoundaries = businessBoundaries,
                sceneConflicts = sceneConflicts,
                isolationAnalysis = isolationAnalysis,
                refactoringOpportunities = refactoringOpportunities,
                sceneHealthScores = sceneHealthScores,
                summary = generateAnalysisSummary(sceneGraph, overlapAnalysis, sceneConflicts)
            )

        } catch (e: Exception) {
            logger.error("SceneAnalysis", "场景交叉分析失败", error = e)
            throw e
        }
    }

    /**
     * 构建场景依赖图
     */
    private fun buildSceneDependencyGraph(analysisResult: DependencyAnalysisResult): SceneDependencyGraph {
        val nodes = mutableMapOf<String, SceneNode>()
        val edges = mutableListOf<SceneEdge>()

        // 创建场景节点
        analysisResult.sceneDefinitions.forEach { scene ->
            nodes[scene.id] = SceneNode(
                id = scene.id,
                name = scene.name,
                category = scene.category,
                description = scene.description,
                entryMethods = scene.entryMethods,
                coverage = scene.coverage,
                tags = scene.tags,
                metrics = SceneNodeMetrics(
                    methodCount = scene.coverage.methodCount,
                    classCount = scene.coverage.classCount,
                    packageCount = scene.coverage.packageCount,
                    maxDepth = scene.coverage.maxDepth,
                    complexity = calculateSceneComplexity(scene, analysisResult)
                )
            )
        }

        // 创建场景间边（基于共享类和方法）
        val scenePairs = nodes.keys.toList().let { scenes ->
            scenes.flatMapIndexed { i, scene1 ->
                scenes.drop(i + 1).map { scene2 -> scene1 to scene2 }
            }
        }

        scenePairs.forEach { (scene1Id, scene2Id) ->
            val overlap = calculateSceneOverlap(scene1Id, scene2Id, analysisResult)
            if (overlap.overlapScore > 0.1) { // 重叠度大于10%才创建边
                edges.add(
                    SceneEdge(
                        source = scene1Id,
                        target = scene2Id,
                        overlapScore = overlap.overlapScore,
                        sharedClasses = overlap.sharedClasses,
                        sharedMethods = overlap.sharedMethods,
                        overlapType = determineOverlapType(overlap)
                    )
                )
            }
        }

        return SceneDependencyGraph(nodes, edges)
    }

    /**
     * 分析场景重叠度
     */
    private fun analyzeSceneOverlap(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): SceneOverlapAnalysis {
        val overlaps = mutableListOf<SceneOverlap>()

        graph.edges.forEach { edge ->
            val sourceNode = graph.nodes[edge.source]!!
            val targetNode = graph.nodes[edge.target]!!

            overlaps.add(
                SceneOverlap(
                    scene1 = edge.source,
                    scene2 = edge.target,
                    scene1Name = sourceNode.name,
                    scene2Name = targetNode.name,
                    overlapScore = edge.overlapScore,
                    sharedClasses = edge.sharedClasses,
                    sharedMethods = edge.sharedMethods,
                    overlapType = edge.overlapType,
                    riskLevel = calculateOverlapRisk(edge),
                    recommendations = generateOverlapRecommendations(edge)
                )
            )
        }

        val highOverlaps = overlaps.filter { it.overlapScore > 0.5 }
        val mediumOverlaps = overlaps.filter { it.overlapScore in 0.3..0.5 }
        val lowOverlaps = overlaps.filter { it.overlapScore < 0.3 }

        return SceneOverlapAnalysis(
            totalOverlaps = overlaps.size,
            highOverlaps = highOverlaps,
            mediumOverlaps = mediumOverlaps,
            lowOverlaps = lowOverlaps,
            averageOverlapScore = if (overlaps.isNotEmpty()) {
                overlaps.map { it.overlapScore }.average()
            } else 0.0
        )
    }

    /**
     * 计算场景重叠
     */
    private fun calculateSceneOverlap(
        scene1Id: String,
        scene2Id: String,
        analysisResult: DependencyAnalysisResult
    ): SceneOverlapDetail {
        val scene1 = analysisResult.sceneDefinitions.find { it.id == scene1Id }!!
        val scene2 = analysisResult.sceneDefinitions.find { it.id == scene2Id }!!

        // 获取场景1和场景2的所有类
        val scene1Classes = getSceneClasses(scene1, analysisResult)
        val scene2Classes = getSceneClasses(scene2, analysisResult)

        // 计算共享类
        val sharedClasses = scene1Classes intersect scene2Classes
        val sharedMethods = getSharedMethods(scene1, scene2, analysisResult)

        // 计算重叠度
        val totalUniqueClasses = (scene1Classes union scene2Classes).size
        val totalUniqueMethods = (scene1.entryMethods union scene2.entryMethods).size

        val classOverlapScore = if (totalUniqueClasses > 0) {
            sharedClasses.size.toDouble() / totalUniqueClasses
        } else 0.0

        val methodOverlapScore = if (totalUniqueMethods > 0) {
            sharedMethods.size.toDouble() / totalUniqueMethods
        } else 0.0

        val overallOverlapScore = (classOverlapScore + methodOverlapScore) / 2

        return SceneOverlapDetail(
            overlapScore = overallOverlapScore,
            sharedClasses = sharedClasses,
            sharedMethods = sharedMethods
        )
    }

    /**
     * 获取场景涉及的类
     */
    private fun getSceneClasses(scene: SceneDefinition, analysisResult: DependencyAnalysisResult): Set<String> {
        val sceneClasses = mutableSetOf<String>()

        scene.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                sceneClasses.add(method.className)
                // 添加该类依赖的所有类
                val classDependencies = analysisResult.classDependencies.find {
                    it.className == method.className
                }
                classDependencies?.dependencies?.forEach { dep ->
                    sceneClasses.add(dep.className)
                }
            }
        }

        return sceneClasses
    }

    /**
     * 获取共享方法
     */
    private fun getSharedMethods(
        scene1: SceneDefinition,
        scene2: SceneDefinition,
        analysisResult: DependencyAnalysisResult
    ): Set<String> {
        // 简化实现：基于方法调用关系分析
        val scene1MethodCalls = mutableSetOf<String>()
        val scene2MethodCalls = mutableSetOf<String>()

        scene1.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                // 获取该方法调用的所有其他方法
                analysisResult.methodCalls
                    .filter { it.callerClass == method.className && it.callerMethod == method.name }
                    .forEach { call ->
                        scene1MethodCalls.add("${call.calleeClass}.${call.calleeMethod}")
                    }
            }
        }

        scene2.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                analysisResult.methodCalls
                    .filter { it.callerClass == method.className && it.callerMethod == method.name }
                    .forEach { call ->
                        scene2MethodCalls.add("${call.calleeClass}.${call.calleeMethod}")
                    }
            }
        }

        return scene1MethodCalls intersect scene2MethodCalls
    }

    /**
     * 确定重叠类型
     */
    private fun determineOverlapType(overlap: SceneOverlapDetail): SceneOverlapType {
        return when {
            overlap.sharedClasses.size > 5 -> SceneOverlapType.HEAVY_OVERLAP
            overlap.sharedMethods.size > 10 -> SceneOverlapType.METHOD_LEVEL_OVERLAP
            overlap.sharedClasses.isNotEmpty() -> SceneOverlapType.CLASS_LEVEL_OVERLAP
            overlap.sharedMethods.isNotEmpty() -> SceneOverlapType.LIGHT_OVERLAP
            else -> SceneOverlapType.MINIMAL_OVERLAP
        }
    }

    /**
     * 计算重叠风险
     */
    private fun calculateOverlapRisk(edge: SceneEdge): OverlapRisk {
        return when {
            edge.overlapScore > 0.7 -> OverlapRisk.HIGH
            edge.overlapScore > 0.4 -> OverlapRisk.MEDIUM
            else -> OverlapRisk.LOW
        }
    }

    /**
     * 生成重叠建议
     */
    private fun generateOverlapRecommendations(edge: SceneEdge): List<String> {
        val recommendations = mutableListOf<String>()

        when (edge.overlapType) {
            SceneOverlapType.HEAVY_OVERLAP -> {
                recommendations.add("考虑将重叠部分提取为独立的共享模块")
                recommendations.add("重新审视场景的边界定义")
                recommendations.add("评估是否可以合并相关场景")
            }
            SceneOverlapType.METHOD_LEVEL_OVERLAP -> {
                recommendations.add("提取共享方法到工具类或服务类")
                recommendations.add("考虑使用模板方法模式")
            }
            SceneOverlapType.CLASS_LEVEL_OVERLAP -> {
                recommendations.add("确保共享类的职责单一")
                recommendations.add("考虑引入接口隔离共享类")
            }
            SceneOverlapType.LIGHT_OVERLAP -> {
                recommendations.add("监控共享代码的变化")
                recommendations.add("确保共享代码的稳定性")
            }
            else -> {
                recommendations.add("保持适当的场景隔离")
            }
        }

        return recommendations
    }

    /**
     * 识别共享代码路径
     */
    private fun identifySharedCodePaths(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SharedCodePath> {
        val sharedPaths = mutableListOf<SharedCodePath>()

        // 分析方法调用路径中的共享部分
        graph.nodes.forEach { (sceneId, sceneNode) ->
            sceneNode.entryMethods.forEach { methodId ->
                val callPaths = traceCallPaths(methodId, analysisResult)

                // 查找被多个场景使用的路径
                callPaths.forEach { path ->
                    val usageScenes = findScenesUsingPath(path, graph, analysisResult)
                    if (usageScenes.size > 1) {
                        sharedPaths.add(
                            SharedCodePath(
                                path = path,
                                usageScenes = usageScenes,
                                frequency = usageScenes.size,
                                complexity = calculatePathComplexity(path, analysisResult),
                                criticality = calculatePathCriticality(path, usageScenes, analysisResult),
                                recommendation = generatePathRecommendation(path, usageScenes)
                            )
                        )
                    }
                }
            }
        }

        return sharedPaths.distinctBy { it.path }.sortedByDescending { it.frequency }
    }

    /**
     * 追踪调用路径
     */
    private fun traceCallPaths(
        methodId: String,
        analysisResult: DependencyAnalysisResult,
        maxDepth: Int = 5
    ): List<String> {
        val paths = mutableListOf<String>()
        val method = analysisResult.methods.find { it.id == methodId } ?: return paths

        fun traceRecursive(currentMethodId: String, currentPath: List<String>, depth: Int) {
            if (depth >= maxDepth) return

            val currentMethod = analysisResult.methods.find { it.id == currentMethodId } ?: return
            val newPath = currentPath + "${currentMethod.className}.${currentMethod.name}"

            // 获取该方法的调用
            val calls = analysisResult.methodCalls.filter {
                it.callerClass == currentMethod.className && it.callerMethod == currentMethod.name
            }

            if (calls.isEmpty()) {
                paths.add(newPath.joinToString(" -> "))
            } else {
                calls.forEach { call ->
                    traceRecursive(
                        "${call.calleeClass}.${call.calleeMethod}",
                        newPath,
                        depth + 1
                    )
                }
            }
        }

        traceRecursive(methodId, emptyList(), 0)
        return paths
    }

    /**
     * 查找使用路径的场景
     */
    private fun findScenesUsingPath(
        path: String,
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): Set<String> {
        val pathMethods = path.split(" -> ").toSet()
        val usingScenes = mutableSetOf<String>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            val sceneMethods = sceneNode.entryMethods.mapNotNull { methodId ->
                val method = analysisResult.methods.find { it.id == methodId }
                "${method?.className}.${method?.name}"
            }.toSet()

            if (sceneMethods.intersect(pathMethods).isNotEmpty()) {
                usingScenes.add(sceneId)
            }
        }

        return usingScenes
    }

    /**
     * 计算路径复杂度
     */
    private fun calculatePathComplexity(path: String, analysisResult: DependencyAnalysisResult): Double {
        val methodNames = path.split(" -> ")
        var totalComplexity = 0.0
        var methodCount = 0

        methodNames.forEach { methodName ->
            val parts = methodName.split(".")
            if (parts.size >= 2) {
                val className = parts.dropLast(1).joinToString(".")
                val methodNameOnly = parts.last()

                val method = analysisResult.methods.find {
                    it.className == className && it.name == methodNameOnly
                }

                if (method != null) {
                    totalComplexity += method.metrics.complexityScore
                    methodCount++
                }
            }
        }

        return if (methodCount > 0) totalComplexity / methodCount else 0.0
    }

    /**
     * 计算路径关键性
     */
    private fun calculatePathCriticality(
        path: String,
        usageScenes: Set<String>,
        analysisResult: DependencyAnalysisResult
    ): PathCriticality {
        val complexity = calculatePathComplexity(path, analysisResult)
        val usageFrequency = usageScenes.size

        return when {
            complexity > 30 && usageFrequency >= 3 -> PathCriticality.HIGH
            complexity > 20 || usageFrequency >= 2 -> PathCriticality.MEDIUM
            else -> PathCriticality.LOW
        }
    }

    /**
     * 生成路径建议
     */
    private fun generatePathRecommendation(
        path: String,
        usageScenes: Set<String>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (usageScenes.size) {
            in 4..Int.MAX_VALUE -> {
                recommendations.add("考虑将此路径提取为核心服务")
                recommendations.add("优化路径性能以提升整体系统性能")
            }
            in 2..3 -> {
                recommendations.add("确保此路径的稳定性和可靠性")
                recommendations.add("添加适当的监控和日志")
            }
            else -> {
                recommendations.add("保持路径的简洁性")
            }
        }

        return recommendations
    }

    /**
     * 分析场景性能影响
     */
    private fun analyzeScenePerformanceImpact(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): ScenePerformanceImpact {
        val scenePerformances = mutableMapOf<String, ScenePerformance>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            val performance = calculateScenePerformance(sceneNode, analysisResult)
            scenePerformances[sceneId] = performance
        }

        // 分析性能瓶颈
        val bottlenecks = scenePerformances.filter { (_, performance) ->
            performance.averageResponseTime > 1000 || // 1秒
            performance.complexityScore > 50
        }.map { (sceneId, performance) ->
            PerformanceBottleneck(
                sceneId = sceneId,
                sceneName = graph.nodes[sceneId]?.name ?: "",
                bottleneckType = if (performance.averageResponseTime > 1000) {
                    BottleneckType.RESPONSE_TIME
                } else {
                    BottleneckType.COMPLEXITY
                },
                severity = if (performance.averageResponseTime > 3000 || performance.complexityScore > 80) {
                    BottleneckSeverity.HIGH
                } else {
                    BottleneckSeverity.MEDIUM
                },
                impact = calculateBottleneckImpact(performance),
                recommendations = generatePerformanceRecommendations(performance)
            )
        }

        return ScenePerformanceImpact(
            scenePerformances = scenePerformances,
            bottlenecks = bottlenecks,
            averagePerformance = if (scenePerformances.isNotEmpty()) {
                scenePerformances.values.map { it.overallScore }.average()
            } else 0.0
        )
    }

    /**
     * 计算场景性能
     */
    private fun calculateScenePerformance(
        sceneNode: SceneNode,
        analysisResult: DependencyAnalysisResult
    ): ScenePerformance {
        var totalComplexity = 0
        var methodCount = 0
        var totalLinesOfCode = 0
        var highComplexityMethods = 0

        sceneNode.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                totalComplexity += method.metrics.complexityScore
                totalLinesOfCode += method.metrics.linesOfCode
                methodCount++

                if (method.metrics.complexityScore > 20) {
                    highComplexityMethods++
                }
            }
        }

        val averageComplexity = if (methodCount > 0) totalComplexity.toDouble() / methodCount else 0.0
        val averageLinesOfCode = if (methodCount > 0) totalLinesOfCode.toDouble() / methodCount else 0.0

        // 估算响应时间（基于复杂度的简化模型）
        val estimatedResponseTime = (averageComplexity * 10).coerceAtMost(5000.0) // 最大5秒

        return ScenePerformance(
            methodCount = methodCount,
            averageComplexity = averageComplexity,
            complexityScore = totalComplexity,
            averageLinesOfCode = averageLinesOfCode,
            highComplexityMethods = highComplexityMethods,
            estimatedResponseTime = estimatedResponseTime,
            averageResponseTime = estimatedResponseTime, // 简化：使用估算值
            overallScore = calculateOverallPerformanceScore(averageComplexity, averageLinesOfCode, estimatedResponseTime)
        )
    }

    /**
     * 计算整体性能评分
     */
    private fun calculateOverallPerformanceScore(
        complexity: Double,
        linesOfCode: Double,
        responseTime: Double
    ): Double {
        var score = 100.0

        // 基于复杂度扣分
        score -= (complexity - 10).coerceAtLeast(0.0) * 2

        // 基于代码行数扣分
        score -= (linesOfCode - 50).coerceAtLeast(0.0) * 0.5

        // 基于响应时间扣分
        score -= (responseTime - 500).coerceAtLeast(0.0) * 0.01

        return score.coerceIn(0.0, 100.0)
    }

    /**
     * 计算瓶颈影响
     */
    private fun calculateBottleneckImpact(performance: ScenePerformance): BottleneckImpact {
        return when {
            performance.complexityScore > 100 || performance.averageResponseTime > 5000 -> BottleneckImpact.CRITICAL
            performance.complexityScore > 50 || performance.averageResponseTime > 2000 -> BottleneckImpact.HIGH
            performance.complexityScore > 30 || performance.averageResponseTime > 1000 -> BottleneckImpact.MEDIUM
            else -> BottleneckImpact.LOW
        }
    }

    /**
     * 生成性能建议
     */
    private fun generatePerformanceRecommendations(performance: ScenePerformance): List<String> {
        val recommendations = mutableListOf<String>()

        if (performance.averageComplexity > 30) {
            recommendations.add("优化复杂度高的方法")
            recommendations.add("考虑使用缓存机制")
        }

        if (performance.highComplexityMethods > 0) {
            recommendations.add("重构${performance.highComplexityMethods}个高复杂度方法")
        }

        if (performance.averageResponseTime > 2000) {
            recommendations.add("优化数据库查询")
            recommendations.add("考虑异步处理")
            recommendations.add("添加适当缓存")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("性能表现良好，继续保持")
        }

        return recommendations
    }

    /**
     * 分析数据访问模式
     */
    private fun analyzeDataAccessPatterns(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): DataAccessPatterns {
        val patterns = mutableMapOf<String, SceneDataPattern>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            val dataPattern = analyzeSceneDataPattern(sceneNode, analysisResult)
            patterns[sceneId] = dataPattern
        }

        // 分析跨场景数据访问冲突
        val conflicts = identifyDataAccessConflicts(patterns, analysisResult)

        return DataAccessPatterns(
            scenePatterns = patterns,
            conflicts = conflicts,
            recommendations = generateDataAccessRecommendations(patterns, conflicts)
        )
    }

    /**
     * 分析场景数据模式
     */
    private fun analyzeSceneDataPattern(
        sceneNode: SceneNode,
        analysisResult: DependencyAnalysisResult
    ): SceneDataPattern {
        val accessedTables = mutableSetOf<String>()
        val accessedEntities = mutableSetOf<String>()
        val readOperations = mutableListOf<String>()
        val writeOperations = mutableListOf<String>()

        sceneNode.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                // 分析方法的数据访问（简化实现）
                if (method.name.startsWith("get", ignoreCase = true) ||
                    method.name.startsWith("find", ignoreCase = true) ||
                    method.name.startsWith("query", ignoreCase = true)) {
                    readOperations.add(method.name)
                }

                if (method.name.startsWith("save", ignoreCase = true) ||
                    method.name.startsWith("update", ignoreCase = true) ||
                    method.name.startsWith("delete", ignoreCase = true) ||
                    method.name.startsWith("create", ignoreCase = true)) {
                    writeOperations.add(method.name)
                }

                // 识别访问的实体（基于方法参数）
                method.parameters.forEach { param ->
                    if (param.type.contains("Entity", ignoreCase = true) ||
                        param.type.contains("DTO", ignoreCase = true) ||
                        param.type.contains("Model", ignoreCase = true)) {
                        accessedEntities.add(param.type)
                    }
                }
            }
        }

        // 基于类名推断访问的表
        sceneNode.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                val repositoryClasses = analysisResult.classes.filter { cls ->
                    cls.annotations.any { it.contains("Repository", ignoreCase = true) } &&
                    cls.qualifiedName.contains(method.className.split(".").first(), ignoreCase = true)
                }
                repositoryClasses.forEach { repo ->
                    // 假设Repository名称对应实体表
                    val entityName = repo.name.replace("Repository", "")
                    accessedTables.add(entityName)
                }
            }
        }

        return SceneDataPattern(
            accessedTables = accessedTables,
            accessedEntities = accessedEntities,
            readOperations = readOperations,
            writeOperations = writeOperations,
            accessFrequency = calculateDataAccessFrequency(sceneNode),
            consistency = calculateDataConsistency(readOperations, writeOperations)
        )
    }

    /**
     * 计算数据访问频率
     */
    private fun calculateDataAccessFrequency(sceneNode: SceneNode): DataAccessFrequency {
        // 基于场景方法数量和调用频率的简化估算
        return when {
            sceneNode.entryMethods.size > 20 -> DataAccessFrequency.HIGH
            sceneNode.entryMethods.size > 10 -> DataAccessFrequency.MEDIUM
            else -> DataAccessFrequency.LOW
        }
    }

    /**
     * 计算数据一致性
     */
    private fun calculateDataConsistency(
        readOperations: List<String>,
        writeOperations: List<String>
    ): DataConsistency {
        val ratio = if (readOperations.isNotEmpty()) {
            writeOperations.size.toDouble() / readOperations.size
        } else 0.0

        return when {
            ratio > 0.5 -> DataConsistency.WRITE_HEAVY
            ratio > 0.2 -> DataConsistency.BALANCED
            else -> DataConsistency.READ_HEAVY
        }
    }

    /**
     * 识别数据访问冲突
     */
    private fun identifyDataAccessConflicts(
        patterns: Map<String, SceneDataPattern>,
        analysisResult: DependencyAnalysisResult
    ): List<DataAccessConflict> {
        val conflicts = mutableListOf<DataAccessConflict>()

        val tableUsage = mutableMapOf<String, MutableList<String>>()

        // 统计每个表的访问场景
        patterns.forEach { (sceneId, pattern) ->
            pattern.accessedTables.forEach { table ->
                tableUsage.getOrPut(table) { mutableListOf() }.add(sceneId)
            }
        }

        // 识别冲突
        tableUsage.forEach { (table, accessingScenes) ->
            if (accessingScenes.size > 1) {
                val tablePatterns = accessingScenes.map { patterns[it]!! }
                val hasWriteConflicts = tablePatterns.any { it.writeOperations.isNotEmpty() }

                if (hasWriteConflicts) {
                    conflicts.add(
                        DataAccessConflict(
                            resource = table,
                            resourceType = ResourceType.TABLE,
                            conflictingScenes = accessingScenes,
                            conflictType = ConflictType.WRITE_CONFLICT,
                            severity = if (accessingScenes.size > 3) ConflictSeverity.HIGH else ConflictSeverity.MEDIUM,
                            recommendations = listOf(
                                "考虑使用分布式事务",
                                "实现乐观锁机制",
                                "添加数据版本控制"
                            )
                        )
                    )
                }
            }
        }

        return conflicts
    }

    /**
     * 生成数据访问建议
     */
    private fun generateDataAccessRecommendations(
        patterns: Map<String, SceneDataPattern>,
        conflicts: List<DataAccessConflict>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (conflicts.isNotEmpty()) {
            recommendations.add("解决数据访问冲突以避免数据不一致")
        }

        val writeHeavyScenes = patterns.values.count { it.consistency == DataConsistency.WRITE_HEAVY }
        if (writeHeavyScenes > 0) {
            recommendations.add("优化写操作频繁的场景，考虑使用缓存")
        }

        val highFrequencyScenes = patterns.values.count { it.accessFrequency == DataAccessFrequency.HIGH }
        if (highFrequencyScenes > 0) {
            recommendations.add("为高频访问场景添加适当的缓存策略")
        }

        return recommendations
    }

    /**
     * 识别业务边界
     */
    private fun identifyBusinessBoundaries(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<BusinessBoundary> {
        val boundaries = mutableListOf<BusinessBoundary>()

        // 基于场景类别识别边界
        val categoryGroups = graph.nodes.values.groupBy { it.category }

        categoryGroups.forEach { (category, scenes) ->
            if (scenes.size >= 2) {
                val sharedResources = identifySharedResources(scenes, analysisResult)
                val boundaryType = when (category) {
                    SceneCategory.USER_TRIGGER -> BoundaryType.USER_INTERACTION
                    SceneCategory.API -> BoundaryType.API_GATEWAY
                    SceneCategory.SCHEDULED -> BoundaryType.BACKGROUND_PROCESS
                    SceneCategory.EVENT_DRIVEN -> BoundaryType.EVENT_PROCESSING
                    else -> BoundaryType.INTERNAL_PROCESS
                }

                boundaries.add(
                    BusinessBoundary(
                        id = "boundary_${category.name.lowercase()}",
                        name = "${category.name}边界",
                        type = boundaryType,
                        scenes = scenes.map { it.id },
                        sharedResources = sharedResources,
                        isolationLevel = calculateBoundaryIsolation(scenes, graph),
                        recommendations = generateBoundaryRecommendations(boundaryType, scenes)
                    )
                )
            }
        }

        return boundaries
    }

    /**
     * 识别共享资源
     */
    private fun identifySharedResources(
        scenes: List<SceneNode>,
        analysisResult: DependencyAnalysisResult
    ): Set<String> {
        val sharedResources = mutableSetOf<String>()

        // 收集所有场景访问的类
        val allClasses = mutableSetOf<String>()
        scenes.forEach { scene ->
            scene.entryMethods.forEach { methodId ->
                val method = analysisResult.methods.find { it.id == methodId }
                if (method != null) {
                    allClasses.add(method.className)
                }
            }
        }

        // 识别被多个场景使用的共享资源
        scenes.forEach { scene ->
            scene.entryMethods.forEach { methodId ->
                val method = analysisResult.methods.find { it.id == methodId }
                if (method != null) {
                    // 检查是否是共享服务类
                    if (method.className.contains("Service", ignoreCase = true) ||
                        method.className.contains("Repository", ignoreCase = true) ||
                        method.className.contains("Util", ignoreCase = true)) {
                        sharedResources.add(method.className)
                    }
                }
            }
        }

        return sharedResources
    }

    /**
     * 计算边界隔离度
     */
    private fun calculateBoundaryIsolation(scenes: List<SceneNode>, graph: SceneDependencyGraph): BoundaryIsolationLevel {
        val internalEdges = scenes.flatMap { scene ->
            graph.edges.filter { edge ->
                (edge.source in scenes.map { it.id }) && (edge.target in scenes.map { it.id })
            }
        }

        val externalEdges = scenes.flatMap { scene ->
            graph.edges.filter { edge ->
                (edge.source == scene.id || edge.target == scene.id) &&
                (edge.source !in scenes.map { it.id }) && (edge.target !in scenes.map { it.id })
            }
        }

        val ratio = if (internalEdges.isNotEmpty()) {
            externalEdges.size.toDouble() / internalEdges.size
        } else 0.0

        return when {
            ratio < 0.2 -> BoundaryIsolationLevel.HIGH
            ratio < 0.5 -> BoundaryIsolationLevel.MEDIUM
            else -> BoundaryIsolationLevel.LOW
        }
    }

    /**
     * 生成边界建议
     */
    private fun generateBoundaryRecommendations(
        boundaryType: BoundaryType,
        scenes: List<SceneNode>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (boundaryType) {
            BoundaryType.USER_INTERACTION -> {
                recommendations.add("确保用户边界的安全性和可访问性")
                recommendations.add("实现适当的身份验证和授权")
            }
            BoundaryType.API_GATEWAY -> {
                recommendations.add("实现API版本管理")
                recommendations.add("添加适当的限流和熔断机制")
            }
            BoundaryType.BACKGROUND_PROCESS -> {
                recommendations.add("确保后台任务的可靠性和幂等性")
                recommendations.add("实现适当的任务调度和监控")
            }
            BoundaryType.EVENT_PROCESSING -> {
                recommendations.add("确保事件的顺序性和重复处理防护")
                recommendations.add("实现事件溯源和补偿机制")
            }
            else -> {
                recommendations.add("确保内部边界的一致性和可维护性")
            }
        }

        return recommendations
    }

    /**
     * 检测场景冲突
     */
    private fun detectSceneConflicts(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SceneConflict> {
        val conflicts = mutableListOf<SceneConflict>()

        // 资源冲突
        conflicts.addAll(detectResourceConflicts(graph, analysisResult))

        // 逻辑冲突
        conflicts.addAll(detectLogicalConflicts(graph, analysisResult))

        // 性能冲突
        conflicts.addAll(detectPerformanceConflicts(graph, analysisResult))

        return conflicts
    }

    /**
     * 检测资源冲突
     */
    private fun detectResourceConflicts(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SceneConflict> {
        val conflicts = mutableListOf<SceneConflict>()

        // 分析共享资源的使用情况
        val resourceUsage = mutableMapOf<String, MutableList<String>>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            sceneNode.entryMethods.forEach { methodId ->
                val method = analysisResult.methods.find { it.id == methodId }
                if (method != null) {
                    // 识别可能冲突的资源（简化实现）
                    if (method.annotations.any { it.contains("Transactional", ignoreCase = true) }) {
                        resourceUsage.getOrPut("database_transaction") { mutableListOf() }.add(sceneId)
                    }
                    if (method.name.contains("lock", ignoreCase = true)) {
                        resourceUsage.getOrPut("distributed_lock") { mutableListOf() }.add(sceneId)
                    }
                }
            }
        }

        resourceUsage.forEach { (resource, usingScenes) ->
            if (usingScenes.size > 1) {
                conflicts.add(
                    SceneConflict(
                        id = "conflict_${resource}_${usingScenes.hashCode()}",
                        type = ConflictType.RESOURCE,
                        description = "多个场景同时使用资源: $resource",
                        involvedScenes = usingScenes,
                        severity = ConflictSeverity.MEDIUM,
                        impact = ConflictImpact.PERFORMANCE_DEGRADATION,
                        resolutions = listOf(
                            "实现资源池化",
                            "使用异步处理减少竞争",
                            "添加适当的锁机制"
                        )
                    )
                )
            }
        }

        return conflicts
    }

    /**
     * 检测逻辑冲突
     */
    private fun detectLogicalConflicts(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SceneConflict> {
        val conflicts = mutableListOf<SceneConflict>()

        // 简化实现：基于场景重叠检测逻辑冲突
        graph.edges.forEach { edge ->
            if (edge.overlapScore > 0.6) {
                conflicts.add(
                    SceneConflict(
                        id = "logical_conflict_${edge.source}_${edge.target}",
                        type = ConflictType.LOGIC,
                        description = "场景 ${graph.nodes[edge.source]?.name} 与 ${graph.nodes[edge.target]?.name} 逻辑重叠严重",
                        involvedScenes = listOf(edge.source, edge.target),
                        severity = ConflictSeverity.HIGH,
                        impact = ConflictImpact.MAINTENANCE_DIFFICULTY,
                        resolutions = listOf(
                            "重新设计场景边界",
                            "提取共享逻辑到独立模块",
                            "考虑场景合并"
                        )
                    )
                )
            }
        }

        return conflicts
    }

    /**
     * 检测性能冲突
     */
    private fun detectPerformanceConflicts(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SceneConflict> {
        val conflicts = mutableListOf<SceneConflict>()

        // 识别高资源消耗的场景
        val highResourceScenes = graph.nodes.filter { (_, sceneNode) ->
            sceneNode.metrics.complexity > 100 || sceneNode.metrics.methodCount > 50
        }

        if (highResourceScenes.size > 1) {
            conflicts.add(
                SceneConflict(
                    id = "performance_conflict_multiple",
                    type = ConflictType.PERFORMANCE,
                    description = "多个高资源消耗场景可能导致系统性能问题",
                    involvedScenes = highResourceScenes.keys.toList(),
                    severity = ConflictSeverity.MEDIUM,
                    impact = ConflictImpact.SYSTEM_STABILITY,
                    resolutions = listOf(
                        "实现场景优先级管理",
                        "添加资源限制机制",
                        "考虑场景解耦和分布式部署"
                    )
                )
            )
        }

        return conflicts
    }

    /**
     * 分析场景隔离度
     */
    private fun analyzeSceneIsolation(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): SceneIsolationAnalysis {
        val isolationMetrics = mutableMapOf<String, SceneIsolationMetrics>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            val metrics = calculateSceneIsolationMetrics(sceneId, graph)
            isolationMetrics[sceneId] = metrics
        }

        val averageIsolation = if (isolationMetrics.isNotEmpty()) {
            isolationMetrics.values.map { it.isolationScore }.average()
        } else 0.0

        val wellIsolatedScenes = isolationMetrics.values.count { it.isolationScore > 0.8 }
        val poorlyIsolatedScenes = isolationMetrics.values.count { it.isolationScore < 0.4 }

        return SceneIsolationAnalysis(
            isolationMetrics = isolationMetrics,
            averageIsolation = averageIsolation,
            wellIsolatedScenes = wellIsolatedScenes,
            poorlyIsolatedScenes = poorlyIsolatedScenes,
            recommendations = generateIsolationRecommendations(isolationMetrics)
        )
    }

    /**
     * 计算场景隔离指标
     */
    private fun calculateSceneIsolationMetrics(
        sceneId: String,
        graph: SceneDependencyGraph
    ): SceneIsolationMetrics {
        val outgoingEdges = graph.edges.filter { it.source == sceneId }
        val incomingEdges = graph.edges.filter { it.target == sceneId }

        // 计算隔离评分（基于依赖关系）
        val totalEdges = outgoingEdges.size + incomingEdges.size
        val isolationScore = if (totalEdges > 0) {
            (1.0 - (totalEdges.toDouble() / (graph.nodes.size - 1))).coerceIn(0.0, 1.0)
        } else 1.0

        // 计算依赖强度
        val dependencyStrength = outgoingEdges.sumOf { it.overlapScore } +
                               incomingEdges.sumOf { it.overlapScore }

        return SceneIsolationMetrics(
            isolationScore = isolationScore,
            dependencyCount = totalEdges,
            dependencyStrength = dependencyStrength,
            sharedResourceCount = outgoingEdges.count { it.sharedClasses.isNotEmpty() },
            isolationLevel = when {
                isolationScore > 0.8 -> IsolationLevel.HIGH
                isolationScore > 0.5 -> IsolationLevel.MEDIUM
                else -> IsolationLevel.LOW
            }
        )
    }

    /**
     * 生成隔离建议
     */
    private fun generateIsolationRecommendations(
        isolationMetrics: Map<String, SceneIsolationMetrics>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        val lowIsolationScenes = isolationMetrics.filter { (_, metrics) ->
            metrics.isolationScore < 0.4
        }

        if (lowIsolationScenes.isNotEmpty()) {
            recommendations.add("提高${lowIsolationScenes.size}个低隔离度场景的独立性")
            recommendations.add("考虑使用微服务架构改善场景隔离")
        }

        return recommendations
    }

    /**
     * 识别场景重构机会
     */
    private fun identifySceneRefactoringOpportunities(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SceneRefactoringOpportunity> {
        val opportunities = mutableListOf<SceneRefactoringOpportunity>()

        // 重叠场景合并机会
        opportunities.addAll(identifySceneMergeOpportunities(graph))

        // 共享逻辑提取机会
        opportunities.addAll(identifySharedLogicExtractionOpportunities(graph))

        // 场景拆分机会
        opportunities.addAll(identifySceneSplitOpportunities(graph, analysisResult))

        return opportunities.sortedByDescending { it.priority }
    }

    /**
     * 识别场景合并机会
     */
    private fun identifySceneMergeOpportunities(graph: SceneDependencyGraph): List<SceneRefactoringOpportunity> {
        val opportunities = mutableListOf<SceneRefactoringOpportunity>()

        graph.edges.filter { edge ->
            edge.overlapScore > 0.7 && edge.overlapType == SceneOverlapType.HEAVY_OVERLAP
        }.forEach { edge ->
            val sourceNode = graph.nodes[edge.source]!!
            val targetNode = graph.nodes[edge.target]!!

            opportunities.add(
                SceneRefactoringOpportunity(
                    type = SceneRefactoringType.MERGE_SCENES,
                    targetScenes = listOf(edge.source, edge.target),
                    description = "场景 ${sourceNode.name} 与 ${targetNode.name} 重叠度很高，考虑合并",
                    priority = 0.8,
                    estimatedEffort = "高",
                    benefits = listOf(
                        "减少代码重复",
                        "简化系统架构",
                        "提高一致性"
                    ),
                    risks = listOf(
                        "增加场景复杂度",
                        "可能违反单一职责原则"
                    )
                )
            )
        }

        return opportunities
    }

    /**
     * 识别共享逻辑提取机会
     */
    private fun identifySharedLogicExtractionOpportunities(graph: SceneDependencyGraph): List<SceneRefactoringOpportunity> {
        val opportunities = mutableListOf<SceneRefactoringOpportunity>()

        // 分析被多个场景使用的类
        val classUsage = mutableMapOf<String, MutableList<String>>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            // 简化实现：假设场景节点中包含了使用的信息
            // 实际应该分析具体的类使用情况
        }

        classUsage.filter { (_, usingScenes) -> usingScenes.size >= 3 }.forEach { (className, usingScenes) ->
            opportunities.add(
                SceneRefactoringOpportunity(
                    type = SceneRefactoringType.EXTRACT_SHARED_LOGIC,
                    targetScenes = usingScenes,
                    description = "类 $className 被${usingScenes.size}个场景使用，考虑提取为共享服务",
                    priority = 0.6,
                    estimatedEffort = "中",
                    benefits = listOf(
                        "减少代码重复",
                        "提高可维护性",
                        "增强一致性"
                    ),
                    risks = listOf(
                        "增加系统复杂性",
                        "可能引入新的依赖关系"
                    )
                )
            )
        }

        return opportunities
    }

    /**
     * 识别场景拆分机会
     */
    private fun identifySceneSplitOpportunities(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<SceneRefactoringOpportunity> {
        val opportunities = mutableListOf<SceneRefactoringOpportunity>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            // 识别过于复杂的场景
            if (sceneNode.metrics.methodCount > 30 || sceneNode.metrics.complexity > 100) {
                opportunities.add(
                    SceneRefactoringOpportunity(
                        type = SceneRefactoringType.SPLIT_SCENE,
                        targetScenes = listOf(sceneId),
                        description = "场景 ${sceneNode.name} 过于复杂，考虑拆分为多个子场景",
                        priority = 0.7,
                        estimatedEffort = "高",
                        benefits = listOf(
                            "降低复杂度",
                            "提高可维护性",
                            "增强场景的单一职责"
                        ),
                        risks = listOf(
                            "增加场景管理复杂性",
                            "可能引入额外的协调开销"
                        )
                    )
                )
            }
        }

        return opportunities
    }

    /**
     * 计算场景健康度评分
     */
    private fun calculateSceneHealthScores(
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): Map<String, SceneHealthScore> {
        val healthScores = mutableMapOf<String, SceneHealthScore>()

        graph.nodes.forEach { (sceneId, sceneNode) ->
            val score = calculateSceneHealthScore(sceneNode, graph, analysisResult)
            healthScores[sceneId] = score
        }

        return healthScores
    }

    /**
     * 计算单个场景健康度
     */
    private fun calculateSceneHealthScore(
        sceneNode: SceneNode,
        graph: SceneDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): SceneHealthScore {
        var score = 100.0

        // 基于复杂度扣分
        score -= (sceneNode.metrics.complexity - 30.0).coerceAtLeast(0.0) * 0.5

        // 基于重叠度扣分
        val overlapScore = graph.edges
            .filter { edge -> edge.source == sceneNode.id || edge.target == sceneNode.id }
            .sumOf { it.overlapScore }
        score -= overlapScore * 20

        // 基于方法数量扣分
        score -= (sceneNode.metrics.methodCount - 15.0).coerceAtLeast(0.0) * 0.3

        score = score.coerceIn(0.0, 100.0)

        return SceneHealthScore(
            overallScore = score.toInt(),
            complexityScore = maxOf(0, 100 - sceneNode.metrics.complexity).toInt(),
            isolationScore = calculateSceneIsolationScore(sceneNode.id, graph),
            maintainabilityScore = calculateMaintainabilityScore(sceneNode),
            recommendations = generateHealthRecommendations(score, sceneNode)
        )
    }

    /**
     * 计算场景隔离评分
     */
    private fun calculateSceneIsolationScore(sceneId: String, graph: SceneDependencyGraph): Int {
        val outgoingEdges = graph.edges.filter { it.source == sceneId }
        val incomingEdges = graph.edges.filter { it.target == sceneId }
        val totalEdges = outgoingEdges.size + incomingEdges.size

        return if (totalEdges > 0) {
            (100 * (1.0 - (totalEdges.toDouble() / (graph.nodes.size - 1)))).toInt()
        } else 100
    }

    /**
     * 计算可维护性评分
     */
    private fun calculateMaintainabilityScore(sceneNode: SceneNode): Int {
        var score = 100

        // 基于方法数量的扣分
        score -= (sceneNode.metrics.methodCount - 20).coerceAtLeast(0) * 2

        // 基于深度的扣分
        score -= (sceneNode.metrics.maxDepth - 5).coerceAtLeast(0) * 3

        return score.coerceIn(0, 100)
    }

    /**
     * 生成健康建议
     */
    private fun generateHealthRecommendations(score: Double, sceneNode: SceneNode): List<String> {
        val recommendations = mutableListOf<String>()

        when {
            score < 60 -> {
                recommendations.add("场景健康状况较差，需要全面重构")
                recommendations.add("优先解决复杂度过高的问题")
                recommendations.add("考虑拆分为多个更小的场景")
            }
            score < 80 -> {
                recommendations.add("场景健康状况一般，建议进行优化")
                recommendations.add("减少与其他场景的重叠")
            }
            else -> {
                recommendations.add("场景健康状况良好，继续保持")
            }
        }

        return recommendations
    }

    /**
     * 计算场景复杂度
     */
    private fun calculateSceneComplexity(scene: SceneDefinition, analysisResult: DependencyAnalysisResult): Int {
        var totalComplexity = 0
        var methodCount = 0

        scene.entryMethods.forEach { methodId ->
            val method = analysisResult.methods.find { it.id == methodId }
            if (method != null) {
                totalComplexity += method.metrics.complexityScore
                methodCount++
            }
        }

        return if (methodCount > 0) totalComplexity else scene.coverage.methodCount * 10 // 估算值
    }

    /**
     * 生成分析摘要
     */
    private fun generateAnalysisSummary(
        graph: SceneDependencyGraph,
        overlapAnalysis: SceneOverlapAnalysis,
        sceneConflicts: List<SceneConflict>
    ): SceneAnalysisSummary {
        val totalScenes = graph.nodes.size
        val highRiskConflicts = sceneConflicts.count { it.severity == ConflictSeverity.HIGH }
        val averageIsolation = calculateAverageIsolation(graph)

        return SceneAnalysisSummary(
            totalScenes = totalScenes,
            totalOverlaps = overlapAnalysis.totalOverlaps,
            highRiskConflicts = highRiskConflicts,
            averageIsolation = averageIsolation,
            overallHealthScore = calculateOverallSceneHealth(totalScenes, overlapAnalysis, sceneConflicts),
            keyInsights = generateKeyInsights(graph, overlapAnalysis, sceneConflicts)
        )
    }

    /**
     * 计算平均隔离度
     */
    private fun calculateAverageIsolation(graph: SceneDependencyGraph): Double {
        if (graph.nodes.isEmpty()) return 0.0

        val totalEdges = graph.edges.size
        val maxPossibleEdges = graph.nodes.size * (graph.nodes.size - 1) / 2

        return if (maxPossibleEdges > 0) {
            (1.0 - (totalEdges.toDouble() / maxPossibleEdges)) * 100
        } else 100.0
    }

    /**
     * 计算整体场景健康度
     */
    private fun calculateOverallSceneHealth(
        totalScenes: Int,
        overlapAnalysis: SceneOverlapAnalysis,
        sceneConflicts: List<SceneConflict>
    ): Int {
        var score = 100

        // 基于重叠度扣分
        score -= (overlapAnalysis.averageOverlapScore * 50).toInt()

        // 基于冲突扣分
        score -= sceneConflicts.count { it.severity == ConflictSeverity.HIGH } * 15
        score -= sceneConflicts.count { it.severity == ConflictSeverity.MEDIUM } * 8

        return score.coerceIn(0, 100)
    }

    /**
     * 生成关键洞察
     */
    private fun generateKeyInsights(
        graph: SceneDependencyGraph,
        overlapAnalysis: SceneOverlapAnalysis,
        sceneConflicts: List<SceneConflict>
    ): List<String> {
        val insights = mutableListOf<String>()

        if (overlapAnalysis.highOverlaps.isNotEmpty()) {
            insights.add("发现${overlapAnalysis.highOverlaps.size}个高度重叠的场景，需要重新设计边界")
        }

        if (sceneConflicts.isNotEmpty()) {
            insights.add("存在${sceneConflicts.size}个场景冲突，可能影响系统稳定性")
        }

        val avgSceneComplexity = graph.nodes.values.map { it.metrics.complexity }.average()
        if (avgSceneComplexity > 50) {
            insights.add("平均场景复杂度较高（${String.format("%.1f", avgSceneComplexity)}），建议简化")
        }

        val isolatedScenes = graph.nodes.count { (_, sceneNode) ->
            graph.edges.none { edge -> edge.source == sceneNode.id || edge.target == sceneNode.id }
        }
        if (isolatedScenes > 0) {
            insights.add("发现${isolatedScenes}个完全隔离的场景，可能存在冗余")
        }

        return insights
    }
}

/**
 * 场景交叉分析结果
 */
data class SceneOverlapResult(
    val sceneGraph: SceneDependencyGraph,
    val overlapAnalysis: SceneOverlapAnalysis,
    val sharedCodePaths: List<SharedCodePath>,
    val performanceImpact: ScenePerformanceImpact,
    val dataAccessPatterns: DataAccessPatterns,
    val businessBoundaries: List<BusinessBoundary>,
    val sceneConflicts: List<SceneConflict>,
    val isolationAnalysis: SceneIsolationAnalysis,
    val refactoringOpportunities: List<SceneRefactoringOpportunity>,
    val sceneHealthScores: Map<String, SceneHealthScore>,
    val summary: SceneAnalysisSummary
)

/**
 * 场景依赖图
 */
data class SceneDependencyGraph(
    val nodes: Map<String, SceneNode>,
    val edges: List<SceneEdge>
)

/**
 * 场景节点
 */
data class SceneNode(
    val id: String,
    val name: String,
    val category: SceneCategory,
    val description: String,
    val entryMethods: List<String>,
    val coverage: SceneCoverage,
    val tags: List<String>,
    val metrics: SceneNodeMetrics
)

/**
 * 场景节点指标
 */
data class SceneNodeMetrics(
    val methodCount: Int,
    val classCount: Int,
    val packageCount: Int,
    val maxDepth: Int,
    val complexity: Int
)

/**
 * 场景边
 */
data class SceneEdge(
    val source: String,
    val target: String,
    val overlapScore: Double,
    val sharedClasses: Set<String>,
    val sharedMethods: Set<String>,
    val overlapType: SceneOverlapType
)

/**
 * 场景重叠分析
 */
data class SceneOverlapAnalysis(
    val totalOverlaps: Int,
    val highOverlaps: List<SceneOverlap>,
    val mediumOverlaps: List<SceneOverlap>,
    val lowOverlaps: List<SceneOverlap>,
    val averageOverlapScore: Double
)

/**
 * 场景重叠
 */
data class SceneOverlap(
    val scene1: String,
    val scene2: String,
    val scene1Name: String,
    val scene2Name: String,
    val overlapScore: Double,
    val sharedClasses: Set<String>,
    val sharedMethods: Set<String>,
    val overlapType: SceneOverlapType,
    val riskLevel: OverlapRisk,
    val recommendations: List<String>
)

/**
 * 场景重叠类型
 */
enum class SceneOverlapType {
    HEAVY_OVERLAP,
    METHOD_LEVEL_OVERLAP,
    CLASS_LEVEL_OVERLAP,
    LIGHT_OVERLAP,
    MINIMAL_OVERLAP
}

/**
 * 重叠风险
 */
enum class OverlapRisk {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 共享代码路径
 */
data class SharedCodePath(
    val path: String,
    val usageScenes: Set<String>,
    val frequency: Int,
    val complexity: Double,
    val criticality: PathCriticality,
    val recommendation: List<String>
)

/**
 * 路径关键性
 */
enum class PathCriticality {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 场景性能影响
 */
data class ScenePerformanceImpact(
    val scenePerformances: Map<String, ScenePerformance>,
    val bottlenecks: List<PerformanceBottleneck>,
    val averagePerformance: Double
)

/**
 * 场景性能
 */
data class ScenePerformance(
    val methodCount: Int,
    val averageComplexity: Double,
    val complexityScore: Int,
    val averageLinesOfCode: Double,
    val highComplexityMethods: Int,
    val estimatedResponseTime: Double,
    val averageResponseTime: Double,
    val overallScore: Double
)

/**
 * 性能瓶颈
 */
data class PerformanceBottleneck(
    val sceneId: String,
    val sceneName: String,
    val bottleneckType: BottleneckType,
    val severity: BottleneckSeverity,
    val impact: BottleneckImpact,
    val recommendations: List<String>
)

/**
 * 瓶颈类型
 */
enum class BottleneckType {
    RESPONSE_TIME,
    COMPLEXITY,
    MEMORY_USAGE,
    DATABASE_ACCESS
}

/**
 * 瓶颈严重程度
 */
enum class BottleneckSeverity {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 瓶颈影响
 */
enum class BottleneckImpact {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 数据访问模式
 */
data class DataAccessPatterns(
    val scenePatterns: Map<String, SceneDataPattern>,
    val conflicts: List<DataAccessConflict>,
    val recommendations: List<String>
)

/**
 * 场景数据模式
 */
data class SceneDataPattern(
    val accessedTables: Set<String>,
    val accessedEntities: Set<String>,
    val readOperations: List<String>,
    val writeOperations: List<String>,
    val accessFrequency: DataAccessFrequency,
    val consistency: DataConsistency
)

/**
 * 数据访问频率
 */
enum class DataAccessFrequency {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 数据一致性
 */
enum class DataConsistency {
    READ_HEAVY,
    WRITE_HEAVY,
    BALANCED
}

/**
 * 数据访问冲突
 */
data class DataAccessConflict(
    val resource: String,
    val resourceType: ResourceType,
    val conflictingScenes: List<String>,
    val conflictType: ConflictType,
    val severity: ConflictSeverity,
    val recommendations: List<String>
)

/**
 * 资源类型
 */
enum class ResourceType {
    TABLE,
    ENTITY,
    CACHE,
    EXTERNAL_SERVICE
}

/**
 * 业务边界
 */
data class BusinessBoundary(
    val id: String,
    val name: String,
    val type: BoundaryType,
    val scenes: List<String>,
    val sharedResources: Set<String>,
    val isolationLevel: BoundaryIsolationLevel,
    val recommendations: List<String>
)

/**
 * 边界类型
 */
enum class BoundaryType {
    USER_INTERACTION,
    API_GATEWAY,
    BACKGROUND_PROCESS,
    EVENT_PROCESSING,
    INTERNAL_PROCESS
}

/**
 * 边界隔离级别
 */
enum class BoundaryIsolationLevel {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 场景冲突
 */
data class SceneConflict(
    val id: String,
    val type: ConflictType,
    val description: String,
    val involvedScenes: List<String>,
    val severity: ConflictSeverity,
    val impact: ConflictImpact,
    val resolutions: List<String>
)

/**
 * 冲突类型
 */
enum class ConflictType {
    RESOURCE,
    LOGIC,
    PERFORMANCE,
    TIMING,
    WRITE_CONFLICT
}

/**
 * 冲突严重程度
 */
enum class ConflictSeverity {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 冲突影响
 */
enum class ConflictImpact {
    SYSTEM_STABILITY,
    PERFORMANCE_DEGRADATION,
    MAINTENANCE_DIFFICULTY,
    USER_EXPERIENCE
}

/**
 * 场景隔离分析
 */
data class SceneIsolationAnalysis(
    val isolationMetrics: Map<String, SceneIsolationMetrics>,
    val averageIsolation: Double,
    val wellIsolatedScenes: Int,
    val poorlyIsolatedScenes: Int,
    val recommendations: List<String>
)

/**
 * 场景隔离指标
 */
data class SceneIsolationMetrics(
    val isolationScore: Double,
    val dependencyCount: Int,
    val dependencyStrength: Double,
    val sharedResourceCount: Int,
    val isolationLevel: IsolationLevel
)

/**
 * 隔离级别
 */
enum class IsolationLevel {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 场景重构机会
 */
data class SceneRefactoringOpportunity(
    val type: SceneRefactoringType,
    val targetScenes: List<String>,
    val description: String,
    val priority: Double,
    val estimatedEffort: String,
    val benefits: List<String>,
    val risks: List<String>
)

/**
 * 场景重构类型
 */
enum class SceneRefactoringType {
    MERGE_SCENES,
    SPLIT_SCENE,
    EXTRACT_SHARED_LOGIC,
    REDUCE_OVERLAP,
    IMPROVE_ISOLATION
}

/**
 * 场景健康度评分
 */
data class SceneHealthScore(
    val overallScore: Int,
    val complexityScore: Int,
    val isolationScore: Int,
    val maintainabilityScore: Int,
    val recommendations: List<String>
)

/**
 * 场景分析摘要
 */
data class SceneAnalysisSummary(
    val totalScenes: Int,
    val totalOverlaps: Int,
    val highRiskConflicts: Int,
    val averageIsolation: Double,
    val overallHealthScore: Int,
    val keyInsights: List<String>
)