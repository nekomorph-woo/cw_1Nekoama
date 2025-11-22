package com.cw2.nekoama.core.reporting

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 包级依赖分析器
 *
 * 提供包级别的深度依赖分析功能，包括：
 * - 依赖层次结构分析
 * - 循环依赖检测
 * - 架构层次识别
 * - 稳定性分析
 * - 依赖距离计算
 */
class PackageLevelAnalyzer {

    private val logger = NekoamaLogger

    /**
     * 执行包级依赖分析
     */
    suspend fun analyzePackageDependencies(
        analysisResult: DependencyAnalysisResult
    ): PackageAnalysisResult = withContext(Dispatchers.Default) {
        logger.info("PackageAnalysis", "开始包级依赖分析")

        try {
            // 构建依赖图
            val dependencyGraph = buildPackageDependencyGraph(analysisResult)

            // 检测循环依赖
            val cycles = detectCyclicDependencies(dependencyGraph)

            // 分析架构层次
            val architectureLayers = analyzeArchitectureLayers(dependencyGraph, analysisResult)

            // 计算稳定性指标
            val stabilityMetrics = calculateStabilityMetrics(dependencyGraph)

            // 计算依赖距离
            val dependencyDistances = calculateDependencyDistances(dependencyGraph)

            // 识别核心包
            val corePackages = identifyCorePackages(dependencyGraph, stabilityMetrics)

            // 生成包层次结构
            val packageHierarchy = buildPackageHierarchy(analysisResult.packages)

            // 分析包的职责
            val packageResponsibilities = analyzePackageResponsibilities(analysisResult)

            // 计算耦合度指标
            val couplingMetrics = calculatePackageCoupling(dependencyGraph, analysisResult)

            logger.info("PackageAnalysis", "包级依赖分析完成")

            PackageAnalysisResult(
                dependencyGraph = dependencyGraph,
                cyclicDependencies = cycles,
                architectureLayers = architectureLayers,
                stabilityMetrics = stabilityMetrics,
                dependencyDistances = dependencyDistances,
                corePackages = corePackages,
                packageHierarchy = packageHierarchy,
                packageResponsibilities = packageResponsibilities,
                couplingMetrics = couplingMetrics,
                analysisSummary = generateAnalysisSummary(dependencyGraph, cycles, stabilityMetrics)
            )

        } catch (e: Exception) {
            logger.error("PackageAnalysis", "包级依赖分析失败", error = e)
            throw e
        }
    }

    /**
     * 构建包依赖图
     */
    private fun buildPackageDependencyGraph(analysisResult: DependencyAnalysisResult): PackageDependencyGraph {
        val nodes = mutableMapOf<String, PackageNode>()
        val edges = mutableListOf<PackageEdge>()

        // 创建节点
        analysisResult.packages.forEach { pkg ->
            nodes[pkg.name] = PackageNode(
                name = pkg.name,
                fullName = pkg.fullName,
                level = pkg.level,
                classCount = pkg.classCount,
                metrics = PackageNodeMetrics(
                    fanIn = pkg.metrics.fanIn,
                    fanOut = pkg.metrics.fanOut,
                    instability = pkg.metrics.instability,
                    complexity = calculatePackageComplexity(pkg, analysisResult)
                )
            )
        }

        // 创建边
        analysisResult.packageDependencies.forEach { pkgDep ->
            pkgDep.dependencies.forEach { targetPkg ->
                edges.add(
                    PackageEdge(
                        source = pkgDep.packageName,
                        target = targetPkg,
                        weight = calculateDependencyWeight(pkgDep.packageName, targetPkg, analysisResult),
                        dependencyTypes = getDependencyTypes(pkgDep.packageName, targetPkg, analysisResult)
                    )
                )
            }
        }

        return PackageDependencyGraph(nodes, edges)
    }

    /**
     * 检测循环依赖
     */
    private fun detectCyclicDependencies(graph: PackageDependencyGraph): List<CyclicDependency> {
        val cycles = mutableListOf<CyclicDependency>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(nodeName: String): List<String>? {
            if (nodeName in recursionStack) {
                // 找到循环，返回循环开始的位置
                return listOf(nodeName)
            }

            if (nodeName in visited) {
                return null
            }

            visited.add(nodeName)
            recursionStack.add(nodeName)
            path.add(nodeName)

            // 遍历所有出边
            graph.edges.filter { it.source == nodeName }.forEach { edge ->
                val cycleStart = dfs(edge.target)
                if (cycleStart != null) {
                    return cycleStart + nodeName
                }
            }

            recursionStack.remove(nodeName)
            path.removeLast()
            return null
        }

        // 对每个节点进行DFS
        graph.nodes.keys.forEach { nodeName ->
            if (nodeName !in visited) {
                val cyclePath = dfs(nodeName)
                if (cyclePath != null) {
                    // 构建完整的循环路径
                    val fullCycle = path.takeLastWhile { it != cyclePath.first() } + cyclePath
                    cycles.add(
                        CyclicDependency(
                            packages = fullCycle.reversed().distinct(),
                            severity = calculateCycleSeverity(fullCycle),
                            impact = calculateCycleImpact(fullCycle, graph)
                        )
                    )
                }
            }
        }

        return cycles.distinctBy { it.packages.sorted() }
    }

    /**
     * 分析架构层次
     */
    private fun analyzeArchitectureLayers(
        graph: PackageDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): Map<ArchitectureLayer, Set<String>> {
        val layerMapping = mutableMapOf<ArchitectureLayer, MutableSet<String>>()

        // 初始化层次映射
        ArchitectureLayer.values().forEach { layer ->
            layerMapping[layer] = mutableSetOf()
        }

        // 根据命名约定和内容分析层次
        analysisResult.packages.forEach { pkg ->
            val layer = determineArchitectureLayer(pkg, analysisResult)
            layerMapping[layer]?.add(pkg.name)
        }

        // 基于依赖关系调整层次
        adjustLayersByDependencies(layerMapping, graph)

        return layerMapping
    }

    /**
     * 确定架构层次
     */
    private fun determineArchitectureLayer(
        pkg: PackageInfo,
        analysisResult: DependencyAnalysisResult
    ): ArchitectureLayer {
        val packageName = pkg.fullName.lowercase()

        // 基于命名约定
        return when {
            packageName.contains("controller") || packageName.contains("web") ||
            packageName.contains("api") || packageName.contains("rest") -> ArchitectureLayer.PRESENTATION

            packageName.contains("service") || packageName.contains("business") ||
            packageName.contains("logic") -> ArchitectureLayer.BUSINESS

            packageName.contains("repository") || packageName.contains("dao") ||
            packageName.contains("persistence") || packageName.contains("database") -> ArchitectureLayer.PERSISTENCE

            packageName.contains("model") || packageName.contains("entity") ||
            packageName.contains("dto") || packageName.contains("vo") -> ArchitectureLayer.MODEL

            packageName.contains("config") || packageName.contains("setting") -> ArchitectureLayer.CONFIGURATION

            packageName.contains("util") || packageName.contains("helper") ||
            packageName.contains("common") -> ArchitectureLayer.UTILITY

            else -> ArchitectureLayer.COMMON
        }
    }

    /**
     * 根据依赖关系调整层次
     */
    private fun adjustLayersByDependencies(
        layerMapping: Map<ArchitectureLayer, MutableSet<String>>,
        graph: PackageDependencyGraph
    ) {
        // 检查层次违规
        layerMapping.forEach { (layer, packages) ->
            packages.forEach { pkg ->
                graph.edges.filter { it.source == pkg }.forEach { edge ->
                    val targetLayer = findPackageLayer(edge.target, layerMapping)
                    if (targetLayer != null && isLayerViolation(layer, targetLayer)) {
                        logger.warn("PackageAnalysis", "检测到层次违规: ${layer.displayName} -> ${targetLayer.displayName}")
                    }
                }
            }
        }
    }

    /**
     * 查找包所在的层次
     */
    private fun findPackageLayer(
        packageName: String,
        layerMapping: Map<ArchitectureLayer, Set<String>>
    ): ArchitectureLayer? {
        return layerMapping.entries.find { (_, packages) ->
            packageName in packages
        }?.key
    }

    /**
     * 检查是否为层次违规
     */
    private fun isLayerViolation(sourceLayer: ArchitectureLayer, targetLayer: ArchitectureLayer): Boolean {
        // 定义允许的依赖方向
        val allowedDependencies = mapOf(
            ArchitectureLayer.PRESENTATION to setOf(ArchitectureLayer.BUSINESS, ArchitectureLayer.MODEL, ArchitectureLayer.COMMON),
            ArchitectureLayer.BUSINESS to setOf(ArchitectureLayer.PERSISTENCE, ArchitectureLayer.MODEL, ArchitectureLayer.COMMON),
            ArchitectureLayer.PERSISTENCE to setOf(ArchitectureLayer.MODEL, ArchitectureLayer.COMMON),
            ArchitectureLayer.CONFIGURATION to setOf(ArchitectureLayer.BUSINESS, ArchitectureLayer.PERSISTENCE, ArchitectureLayer.COMMON),
            ArchitectureLayer.UTILITY to setOf(ArchitectureLayer.COMMON),
            ArchitectureLayer.COMMON to emptySet(),
            ArchitectureLayer.MODEL to setOf(ArchitectureLayer.COMMON)
        )

        return targetLayer !in (allowedDependencies[sourceLayer] ?: emptySet())
    }

    /**
     * 计算稳定性指标
     */
    private fun calculateStabilityMetrics(graph: PackageDependencyGraph): Map<String, StabilityMetrics> {
        val metrics = mutableMapOf<String, StabilityMetrics>()

        graph.nodes.forEach { (packageName, node) ->
            val afferentCoupling = graph.edges.count { it.target == packageName } // Ca
            val efferentCoupling = graph.edges.count { it.source == packageName } // Ce

            val instability = if (afferentCoupling + efferentCoupling > 0) {
                efferentCoupling.toDouble() / (afferentCoupling + efferentCoupling)
            } else 0.0

            val abstractness = calculateAbstractness(packageName, node)

            val distance = Math.abs(abstractness + instability - 1.0)

            metrics[packageName] = StabilityMetrics(
                afferentCoupling = afferentCoupling,
                efferentCoupling = efferentCoupling,
                instability = instability,
                abstractness = abstractness,
                distance = distance,
                stabilityLevel = determineStabilityLevel(instability, distance)
            )
        }

        return metrics
    }

    /**
     * 计算抽象度
     */
    private fun calculateAbstractness(packageName: String, node: PackageNode): Double {
        // 这里简化实现，实际应该统计抽象类和接口的数量
        return 0.3 // 假设30%的抽象度
    }

    /**
     * 确定稳定性级别
     */
    private fun determineStabilityLevel(instability: Double, distance: Double): StabilityLevel {
        return when {
            distance > 0.5 -> StabilityLevel.VIOLATES_STABILITY_PRINCIPLE
            instability > 0.8 -> StabilityLevel.VERY_UNSTABLE
            instability > 0.6 -> StabilityLevel.UNSTABLE
            instability > 0.4 -> StabilityLevel.MODERATELY_STABLE
            instability > 0.2 -> StabilityLevel.STABLE
            else -> StabilityLevel.VERY_STABLE
        }
    }

    /**
     * 计算依赖距离
     */
    private fun calculateDependencyDistances(graph: PackageDependencyGraph): Map<String, Map<String, Int>> {
        val distances = mutableMapOf<String, MutableMap<String, Int>>()
        val nodeKeys = graph.nodes.keys.toList()

        // 初始化距离矩阵
        nodeKeys.forEach { source ->
            distances[source] = mutableMapOf()
            nodeKeys.forEach { target ->
                val initialValue = if (source == target) 0 else Int.MAX_VALUE
                distances[source]?.put(target, initialValue)
            }
        }

        // 使用Floyd-Warshall算法计算最短路径
        nodeKeys.forEach { k ->
            nodeKeys.forEach { i ->
                nodeKeys.forEach { j ->
                    val currentDistance = distances[i]?.get(j) ?: Int.MAX_VALUE
                    val distanceViaK = (distances[i]?.get(k) ?: Int.MAX_VALUE) + (distances[k]?.get(j) ?: Int.MAX_VALUE)

                    if (distanceViaK < currentDistance) {
                        distances[i]?.put(j, distanceViaK)
                    }
                }
            }
        }

        return distances
    }

    /**
     * 识别核心包
     */
    private fun identifyCorePackages(
        graph: PackageDependencyGraph,
        stabilityMetrics: Map<String, StabilityMetrics>
    ): List<CorePackage> {
        val corePackages = mutableListOf<CorePackage>()

        stabilityMetrics.forEach { (packageName, metrics) ->
            val node = graph.nodes[packageName] ?: return@forEach

            // 核心包的特征：
            // 1. 高传入耦合（被很多包依赖）
            // 2. 相对稳定（低不稳定性）
            // 3. 重要的业务功能
            val isCore = metrics.afferentCoupling >= 3 &&
                       metrics.instability <= 0.6 &&
                       node.classCount >= 5

            if (isCore) {
                val coreScore = calculateCoreScore(metrics, node)
                corePackages.add(
                    CorePackage(
                        name = packageName,
                        fullName = node.fullName,
                        coreScore = coreScore,
                        reasons = determineCoreReasons(metrics, node),
                        dependents = graph.edges.filter { it.target == packageName }.map { it.source }
                    )
                )
            }
        }

        return corePackages.sortedByDescending { it.coreScore }
    }

    /**
     * 计算核心评分
     */
    private fun calculateCoreScore(metrics: StabilityMetrics, node: PackageNode): Double {
        return metrics.afferentCoupling * 0.4 +
               (1.0 - metrics.instability) * 0.3 +
               node.classCount * 0.2 +
               node.metrics.complexity * 0.1
    }

    /**
     * 确定核心原因
     */
    private fun determineCoreReasons(metrics: StabilityMetrics, node: PackageNode): List<String> {
        val reasons = mutableListOf<String>()

        if (metrics.afferentCoupling >= 5) {
            reasons.add("被${metrics.afferentCoupling}个包依赖")
        }

        if (metrics.instability <= 0.3) {
            reasons.add("稳定性高")
        }

        if (node.classCount >= 10) {
            reasons.add("包含${node.classCount}个类")
        }

        if (reasons.isEmpty()) {
            reasons.add("具有一定的重要性")
        }

        return reasons
    }

    /**
     * 构建包层次结构
     */
    private fun buildPackageHierarchy(packages: List<PackageInfo>): PackageHierarchy {
        val rootPackages = packages.filter { it.parentPackage.isEmpty() || !packages.any { pkg -> pkg.name == it.parentPackage } }
        val children = mutableMapOf<String, MutableList<PackageNode>>()

        // 构建父子关系
        packages.forEach { pkg ->
            if (pkg.parentPackage.isNotEmpty()) {
                children.getOrPut(pkg.parentPackage) { mutableListOf() }.add(
                    PackageNode(
                        name = pkg.name,
                        fullName = pkg.fullName,
                        level = pkg.level,
                        classCount = pkg.classCount,
                        metrics = PackageNodeMetrics(
                            fanIn = pkg.metrics.fanIn,
                            fanOut = pkg.metrics.fanOut,
                            instability = pkg.metrics.instability,
                            complexity = 0
                        )
                    )
                )
            }
        }

        return PackageHierarchy(
            rootPackages = rootPackages.map { it.name },
            children = children
        )
    }

    /**
     * 分析包职责
     */
    private fun analyzePackageResponsibilities(analysisResult: DependencyAnalysisResult): Map<String, PackageResponsibility> {
        val responsibilities = mutableMapOf<String, PackageResponsibility>()

        analysisResult.packages.forEach { pkg ->
            val classes = analysisResult.classes.filter { it.packageId == pkg.id }
            val responsibilitiesList = analyzePackageClassesResponsibilities(classes)

            responsibilities[pkg.name] = PackageResponsibility(
                packageName = pkg.name,
                responsibilities = responsibilitiesList,
                cohesion = calculateCohesion(classes),
                violations = detectResponsibilityViolations(classes)
            )
        }

        return responsibilities
    }

    /**
     * 分析包中类的职责
     */
    private fun analyzePackageClassesResponsibilities(classes: List<ClassInfo>): List<String> {
        val responsibilities = mutableListOf<String>()

        // 基于类名和类型推断职责
        val hasControllers = classes.any { cls -> cls.annotations.any { it.contains("Controller", ignoreCase = true) } }
        val hasServices = classes.any { cls -> cls.annotations.any { it.contains("Service", ignoreCase = true) } }
        val hasRepositories = classes.any { cls -> cls.annotations.any { it.contains("Repository", ignoreCase = true) } }
        val hasPojos = classes.any { cls -> cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) }
        val hasConfigs = classes.any { cls -> cls.annotations.any { it.contains("Configuration", ignoreCase = true) } }

        when {
            hasControllers -> responsibilities.add("处理Web请求和响应")
            hasServices -> responsibilities.add("实现业务逻辑")
            hasRepositories -> responsibilities.add("数据访问和持久化")
            hasPojos -> responsibilities.add("数据传输和模型定义")
            hasConfigs -> responsibilities.add("应用配置管理")
        }

        if (responsibilities.isEmpty()) {
            responsibilities.add("通用功能支持")
        }

        return responsibilities
    }

    /**
     * 计算内聚度
     */
    private fun calculateCohesion(classes: List<ClassInfo>): Double {
        // 简化实现：基于类之间的相似性计算内聚度
        if (classes.size <= 1) return 1.0

        // 基于命名约定计算相似性
        val commonPrefixes = classes
            .map { it.name.split("_")[0] }
            .groupBy { it }
            .mapValues { it.value.size }

        val maxSamePrefix = commonPrefixes.values.maxOrNull() ?: 1
        return maxSamePrefix.toDouble() / classes.size
    }

    /**
     * 检测职责违规
     */
    private fun detectResponsibilityViolations(classes: List<ClassInfo>): List<String> {
        val violations = mutableListOf<String>()

        val hasControllers = classes.any { cls -> cls.annotations.any { it.contains("Controller", ignoreCase = true) } }
        val hasRepositories = classes.any { cls -> cls.annotations.any { it.contains("Repository", ignoreCase = true) } }
        val hasServices = classes.any { cls -> cls.annotations.any { it.contains("Service", ignoreCase = true) } }

        // 检查是否混合了不同层次的类
        if (hasControllers && hasRepositories) {
            violations.add("混合了表现层和持久化层的类")
        }

        if (hasControllers && hasServices) {
            violations.add("混合了表现层和业务层的类")
        }

        // 检查类数量是否过多
        if (classes.size > 20) {
            violations.add("包含过多类 (${classes.size}个)，可能违反单一职责原则")
        }

        return violations
    }

    /**
     * 计算包耦合度指标
     */
    private fun calculatePackageCoupling(
        graph: PackageDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): Map<String, PackageCouplingMetrics> {
        val couplingMetrics = mutableMapOf<String, PackageCouplingMetrics>()

        graph.nodes.forEach { (packageName, node) ->
            val outgoingEdges = graph.edges.filter { it.source == packageName }
            val incomingEdges = graph.edges.filter { it.target == packageName }

            couplingMetrics[packageName] = PackageCouplingMetrics(
                outgoingDependencies = outgoingEdges.size,
                incomingDependencies = incomingEdges.size,
                totalDependencies = outgoingEdges.size + incomingEdges.size,
                dependencyRatio = calculateDependencyRatio(outgoingEdges, incomingEdges),
                heavyDependencies = identifyHeavyDependencies(outgoingEdges),
                fragileDependencies = identifyFragileDependencies(incomingEdges, graph)
            )
        }

        return couplingMetrics
    }

    /**
     * 计算依赖比率
     */
    private fun calculateDependencyRatio(
        outgoingEdges: List<PackageEdge>,
        incomingEdges: List<PackageEdge>
    ): Double {
        val total = outgoingEdges.size + incomingEdges.size
        return if (total > 0) outgoingEdges.size.toDouble() / total else 0.0
    }

    /**
     * 识别重度依赖
     */
    private fun identifyHeavyDependencies(edges: List<PackageEdge>): List<HeavyDependency> {
        return edges
            .filter { it.weight > 5 } // 权重大于5认为是重度依赖
            .map { HeavyDependency(target = it.target, weight = it.weight, types = it.dependencyTypes) }
            .sortedByDescending { it.weight }
    }

    /**
     * 识别脆弱依赖
     */
    private fun identifyFragileDependencies(
        incomingEdges: List<PackageEdge>,
        graph: PackageDependencyGraph
    ): List<FragileDependency> {
        // 脆弱依赖是指那些对不稳定的包的依赖
        return incomingEdges.mapNotNull { edge ->
            val targetNode = graph.nodes[edge.target]
            if (targetNode != null && targetNode.metrics.instability > 0.7) {
                FragileDependency(
                    source = edge.source,
                    unstableTarget = edge.target,
                    risk = calculateDependencyRisk(edge, targetNode)
                )
            } else null
        }
    }

    /**
     * 计算依赖风险
     */
    private fun calculateDependencyRisk(edge: PackageEdge, targetNode: PackageNode): DependencyRisk {
        return when {
            targetNode.metrics.instability > 0.9 -> DependencyRisk.HIGH
            targetNode.metrics.instability > 0.7 -> DependencyRisk.MEDIUM
            else -> DependencyRisk.LOW
        }
    }

    /**
     * 计算包复杂度
     */
    private fun calculatePackageComplexity(pkg: PackageInfo, analysisResult: DependencyAnalysisResult): Int {
        val packageClasses = analysisResult.classes.filter { it.packageId == pkg.id }
        return packageClasses.sumOf { it.metrics.complexityScore }
    }

    /**
     * 计算依赖权重
     */
    private fun calculateDependencyWeight(
        sourcePkg: String,
        targetPkg: String,
        analysisResult: DependencyAnalysisResult
    ): Int {
        // 基于类之间的依赖数量计算权重
        val sourceClasses = analysisResult.classes.filter {
            analysisResult.packages.find { pkg -> pkg.name == sourcePkg }?.id == it.packageId
        }

        return sourceClasses.sumOf { cls ->
            analysisResult.classDependencies
                .filter { it.className == cls.qualifiedName }
                .count { dep ->
                    analysisResult.classes.find { c -> c.qualifiedName == dep.className }?.let {
                        analysisResult.packages.find { pkg -> pkg.id == it.packageId }?.name == targetPkg
                    } ?: false
                }
        }
    }

    /**
     * 获取依赖类型
     */
    private fun getDependencyTypes(
        sourcePkg: String,
        targetPkg: String,
        analysisResult: DependencyAnalysisResult
    ): Set<ReferenceType> {
        val sourceClasses = analysisResult.classes.filter {
            analysisResult.packages.find { pkg -> pkg.name == sourcePkg }?.id == it.packageId
        }

        return sourceClasses.flatMap { cls ->
            analysisResult.classDependencies
                .filter { it.className == cls.qualifiedName }
                .flatMap { dep -> dep.dependencies.map { it.referenceType } }
                .distinct()
        }.toSet()
    }

    /**
     * 计算循环严重程度
     */
    private fun calculateCycleSeverity(cycle: List<String>): CycleSeverity {
        return when (cycle.size) {
            2 -> CycleSeverity.TWO_WAY
            3, 4 -> CycleSeverity.SMALL
            5, 6 -> CycleSeverity.MEDIUM
            else -> CycleSeverity.LARGE
        }
    }

    /**
     * 计算循环影响
     */
    private fun calculateCycleImpact(cycle: List<String>, graph: PackageDependencyGraph): CycleImpact {
        val affectedPackages = cycle.size
        val totalConnections = cycle.sumOf { pkg ->
            graph.edges.count { it.source == pkg || it.target == pkg }
        }

        return when {
            affectedPackages >= 5 || totalConnections >= 20 -> CycleImpact.HIGH
            affectedPackages >= 3 || totalConnections >= 10 -> CycleImpact.MEDIUM
            else -> CycleImpact.LOW
        }
    }

    /**
     * 生成分析摘要
     */
    private fun generateAnalysisSummary(
        graph: PackageDependencyGraph,
        cycles: List<CyclicDependency>,
        stabilityMetrics: Map<String, StabilityMetrics>
    ): PackageAnalysisSummary {
        val totalPackages = graph.nodes.size
        val totalDependencies = graph.edges.size
        val cycleCount = cycles.size
        val stablePackages = stabilityMetrics.values.count { it.stabilityLevel == StabilityLevel.STABLE || it.stabilityLevel == StabilityLevel.VERY_STABLE }
        val unstablePackages = stabilityMetrics.values.count { it.stabilityLevel == StabilityLevel.UNSTABLE || it.stabilityLevel == StabilityLevel.VERY_UNSTABLE }

        return PackageAnalysisSummary(
            totalPackages = totalPackages,
            totalDependencies = totalDependencies,
            cyclicDependencyCount = cycleCount,
            averageDependencyPerPackage = if (totalPackages > 0) totalDependencies.toDouble() / totalPackages else 0.0,
            stablePackagesCount = stablePackages,
            unstablePackagesCount = unstablePackages,
            stabilityRatio = if (totalPackages > 0) stablePackages.toDouble() / totalPackages else 0.0,
            healthScore = calculateHealthScore(cycleCount, totalPackages, stablePackages, unstablePackages)
        )
    }

    /**
     * 计算健康评分
     */
    private fun calculateHealthScore(
        cycleCount: Int,
        totalPackages: Int,
        stablePackages: Int,
        unstablePackages: Int
    ): Int {
        var score = 100

        // 循环依赖扣分
        score -= cycleCount * 10

        // 不稳定包扣分
        score -= unstablePackages * 5

        // 稳定包加分
        score += stablePackages * 2

        return score.coerceIn(0, 100)
    }
}

/**
 * 包分析结果
 */
data class PackageAnalysisResult(
    val dependencyGraph: PackageDependencyGraph,
    val cyclicDependencies: List<CyclicDependency>,
    val architectureLayers: Map<ArchitectureLayer, Set<String>>,
    val stabilityMetrics: Map<String, StabilityMetrics>,
    val dependencyDistances: Map<String, Map<String, Int>>,
    val corePackages: List<CorePackage>,
    val packageHierarchy: PackageHierarchy,
    val packageResponsibilities: Map<String, PackageResponsibility>,
    val couplingMetrics: Map<String, PackageCouplingMetrics>,
    val analysisSummary: PackageAnalysisSummary
)

/**
 * 包依赖图
 */
data class PackageDependencyGraph(
    val nodes: Map<String, PackageNode>,
    val edges: List<PackageEdge>
)

/**
 * 包节点
 */
data class PackageNode(
    val name: String,
    val fullName: String,
    val level: Int,
    val classCount: Int,
    val metrics: PackageNodeMetrics
)

/**
 * 包节点指标
 */
data class PackageNodeMetrics(
    val fanIn: Int,
    val fanOut: Int,
    val instability: Double,
    val complexity: Int
)

/**
 * 包边
 */
data class PackageEdge(
    val source: String,
    val target: String,
    val weight: Int,
    val dependencyTypes: Set<ReferenceType>
)

/**
 * 循环依赖
 */
data class CyclicDependency(
    val packages: List<String>,
    val severity: CycleSeverity,
    val impact: CycleImpact
)

/**
 * 架构层次
 */
enum class ArchitectureLayer(val displayName: String) {
    PRESENTATION("表现层"),
    BUSINESS("业务层"),
    PERSISTENCE("持久化层"),
    MODEL("模型层"),
    CONFIGURATION("配置层"),
    UTILITY("工具层"),
    COMMON("通用层")
}

/**
 * 稳定性指标
 */
data class StabilityMetrics(
    val afferentCoupling: Int,    // Ca - 传入耦合
    val efferentCoupling: Int,    // Ce - 传出耦合
    val instability: Double,      // I = Ce / (Ca + Ce)
    val abstractness: Double,     // A = Na / Nc
    val distance: Double,         // D = |A + I - 1|
    val stabilityLevel: StabilityLevel
)

/**
 * 稳定性级别
 */
enum class StabilityLevel {
    VERY_STABLE,
    STABLE,
    MODERATELY_STABLE,
    UNSTABLE,
    VERY_UNSTABLE,
    VIOLATES_STABILITY_PRINCIPLE
}

/**
 * 核心包
 */
data class CorePackage(
    val name: String,
    val fullName: String,
    val coreScore: Double,
    val reasons: List<String>,
    val dependents: List<String>
)

/**
 * 包层次结构
 */
data class PackageHierarchy(
    val rootPackages: List<String>,
    val children: Map<String, List<PackageNode>>
)

/**
 * 包职责
 */
data class PackageResponsibility(
    val packageName: String,
    val responsibilities: List<String>,
    val cohesion: Double,
    val violations: List<String>
)

/**
 * 包耦合度指标
 */
data class PackageCouplingMetrics(
    val outgoingDependencies: Int,
    val incomingDependencies: Int,
    val totalDependencies: Int,
    val dependencyRatio: Double,
    val heavyDependencies: List<HeavyDependency>,
    val fragileDependencies: List<FragileDependency>
)

/**
 * 重度依赖
 */
data class HeavyDependency(
    val target: String,
    val weight: Int,
    val types: Set<ReferenceType>
)

/**
 * 脆弱依赖
 */
data class FragileDependency(
    val source: String,
    val unstableTarget: String,
    val risk: DependencyRisk
)

/**
 * 依赖风险
 */
enum class DependencyRisk {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * 循环严重程度
 */
enum class CycleSeverity {
    TWO_WAY,
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * 循环影响
 */
enum class CycleImpact {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * 包分析摘要
 */
data class PackageAnalysisSummary(
    val totalPackages: Int,
    val totalDependencies: Int,
    val cyclicDependencyCount: Int,
    val averageDependencyPerPackage: Double,
    val stablePackagesCount: Int,
    val unstablePackagesCount: Int,
    val stabilityRatio: Double,
    val healthScore: Int
)