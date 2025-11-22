package com.cw2.nekoama.core.reporting

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 类级依赖分析器
 *
 * 提供类级别的深度依赖分析功能，包括：
 * - 类关系图构建
 * - 设计模式识别
 * - 代码坏味道检测
 * - 类职责分析
 * - 继承层次分析
 * - 耦合度和内聚度计算
 */
class ClassLevelAnalyzer {

    private val logger = NekoamaLogger

    /**
     * 执行类级依赖分析
     */
    suspend fun analyzeClassDependencies(
        analysisResult: DependencyAnalysisResult
    ): ClassAnalysisResult = withContext(Dispatchers.Default) {
        logger.info("ClassLevelAnalysis", "开始类级依赖分析")

        try {
            // 构建类依赖图
            val dependencyGraph = buildClassDependencyGraph(analysisResult)

            // 识别设计模式
            val designPatterns = identifyDesignPatterns(dependencyGraph, analysisResult)

            // 分析类职责
            val classResponsibilities = analyzeClassResponsibilities(analysisResult)

            // 计算类级别度量
            val classMetrics = calculateClassMetrics(dependencyGraph, analysisResult)

            // 检测设计问题
            val designIssues = detectDesignIssues(dependencyGraph, analysisResult)

            // 分析继承层次
            val inheritanceHierarchy = analyzeInheritanceHierarchy(analysisResult)

            // 计算类耦合度
            val couplingAnalysis = analyzeClassCoupling(dependencyGraph, analysisResult)

            // 识别核心类
            val coreClasses = identifyCoreClasses(dependencyGraph, classMetrics)

            // 分析类聚簇
            val classClusters = analyzeClassClusters(dependencyGraph, analysisResult)

            // 检测潜在重构点
            val refactoringOpportunities = identifyRefactoringOpportunities(dependencyGraph, analysisResult)

            logger.info("ClassLevelAnalysis", "类级依赖分析完成")

            ClassAnalysisResult(
                dependencyGraph = dependencyGraph,
                designPatterns = designPatterns,
                classResponsibilities = classResponsibilities,
                classMetrics = classMetrics,
                designIssues = designIssues,
                inheritanceHierarchy = inheritanceHierarchy,
                couplingAnalysis = couplingAnalysis,
                coreClasses = coreClasses,
                classClusters = classClusters,
                refactoringOpportunities = refactoringOpportunities,
                analysisSummary = generateAnalysisSummary(dependencyGraph, classMetrics, designIssues)
            )

        } catch (e: Exception) {
            logger.error("ClassLevelAnalysis", "类级依赖分析失败", error = e)
            throw e
        }
    }

    /**
     * 构建类依赖图
     */
    private fun buildClassDependencyGraph(analysisResult: DependencyAnalysisResult): ClassDependencyGraph {
        val nodes = mutableMapOf<String, ClassNode>()
        val edges = mutableListOf<ClassEdge>()

        // 创建节点
        analysisResult.classes.forEach { cls ->
            nodes[cls.id] = ClassNode(
                id = cls.id,
                name = cls.name,
                qualifiedName = cls.qualifiedName,
                packageName = analysisResult.packages.find { it.id == cls.packageId }?.name ?: "",
                type = cls.type,
                isInterface = cls.type == ClassType.INTERFACE,
                isAbstract = cls.type == ClassType.ABSTRACT_CLASS,
                modifiers = cls.modifiers,
                annotations = cls.annotations,
                superClass = cls.superClass,
                interfaces = cls.interfaces,
                metrics = ClassNodeMetrics(
                    methodCount = cls.metrics.methodCount,
                    fieldCount = cls.metrics.fieldCount,
                    linesOfCode = cls.metrics.linesOfCode,
                    complexityScore = cls.metrics.complexityScore,
                    cohesion = cls.metrics.cohesion,
                    fanIn = cls.metrics.fanIn,
                    fanOut = cls.metrics.fanOut,
                    coupling = cls.metrics.coupling
                ),
                stereotypes = determineClassStereotypes(cls)
            )
        }

        // 创建边
        analysisResult.classDependencies.forEach { classDep ->
            classDep.dependencies.forEach { ref ->
                val sourceClass = analysisResult.classes.find { it.qualifiedName == classDep.className }
                val targetClass = analysisResult.classes.find { it.qualifiedName == ref.className }

                if (sourceClass != null && targetClass != null) {
                    edges.add(
                        ClassEdge(
                            source = sourceClass.id,
                            target = targetClass.id,
                            type = ref.referenceType,
                            weight = calculateDependencyWeight(sourceClass, targetClass, ref.referenceType),
                            location = ref.location
                        )
                    )
                }
            }
        }

        return ClassDependencyGraph(nodes, edges)
    }

    /**
     * 确定类原型
     */
    private fun determineClassStereotypes(cls: ClassInfo): Set<ClassStereotype> {
        val stereotypes = mutableSetOf<ClassStereotype>()

        when {
            cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> stereotypes.add(ClassStereotype.CONTROLLER)
            cls.annotations.any { it.contains("Service", ignoreCase = true) } -> stereotypes.add(ClassStereotype.SERVICE)
            cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> stereotypes.add(ClassStereotype.REPOSITORY)
            cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> stereotypes.add(ClassStereotype.POJO)
        }

        // 基于注解识别
        cls.annotations.forEach { annotation ->
            when {
                annotation.contains("Component", ignoreCase = true) -> stereotypes.add(ClassStereotype.COMPONENT)
                annotation.contains("Configuration", ignoreCase = true) -> stereotypes.add(ClassStereotype.CONFIGURATION)
                annotation.contains("Utility", ignoreCase = true) || cls.name.contains("Util") -> stereotypes.add(ClassStereotype.UTILITY)
                annotation.contains("Test", ignoreCase = true) -> stereotypes.add(ClassStereotype.TEST)
            }
        }

        // 基于命名约定识别
        when {
            cls.name.endsWith("Exception") -> stereotypes.add(ClassStereotype.EXCEPTION)
            cls.name.endsWith("Builder") -> stereotypes.add(ClassStereotype.BUILDER)
            cls.name.endsWith("Factory") -> stereotypes.add(ClassStereotype.FACTORY)
            cls.name.endsWith("Manager") -> stereotypes.add(ClassStereotype.MANAGER)
            cls.name.contains("DTO") || cls.name.contains("Vo") -> stereotypes.add(ClassStereotype.DATA_TRANSFER)
        }

        return stereotypes
    }

    /**
     * 识别设计模式
     */
    private fun identifyDesignPatterns(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        // 识别单例模式
        patterns.addAll(identifySingletonPattern(graph))

        // 识别工厂模式
        patterns.addAll(identifyFactoryPattern(graph))

        // 识别策略模式
        patterns.addAll(identifyStrategyPattern(graph))

        // 识别观察者模式
        patterns.addAll(identifyObserverPattern(graph))

        // 识别装饰器模式
        patterns.addAll(identifyDecoratorPattern(graph))

        // 识别适配器模式
        patterns.addAll(identifyAdapterPattern(graph))

        return patterns
    }

    /**
     * 识别单例模式
     */
    private fun identifySingletonPattern(graph: ClassDependencyGraph): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        graph.nodes.values.forEach { node ->
            val hasPrivateConstructor = node.modifiers.contains("private")
            val hasStaticInstance = node.annotations.any { it.contains("static", ignoreCase = true) }
            val hasGetInstanceMethod = graph.nodes.values.any { otherNode ->
                graph.edges.any { edge ->
                    edge.source == node.id && otherNode.name.contains("getInstance", ignoreCase = true)
                }
            }

            if (hasPrivateConstructor && (hasStaticInstance || hasGetInstanceMethod)) {
                patterns.add(
                    DesignPattern(
                        type = PatternType.SINGLETON,
                        name = "Singleton Pattern",
                        participants = listOf(node.id),
                        confidence = calculatePatternConfidence(listOf(hasPrivateConstructor, hasStaticInstance, hasGetInstanceMethod)),
                        description = "${node.name} 实现了单例模式"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * 识别工厂模式
     */
    private fun identifyFactoryPattern(graph: ClassDependencyGraph): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        graph.nodes.values.filter { node ->
            node.name.contains("Factory", ignoreCase = true) ||
            node.stereotypes.contains(ClassStereotype.FACTORY)
        }.forEach { factory ->
            val createdClasses = graph.edges
                .filter { edge ->
                    edge.source == factory.id && edge.type == ReferenceType.COMPOSITION
                }
                .map { it.target }

            if (createdClasses.isNotEmpty()) {
                patterns.add(
                    DesignPattern(
                        type = PatternType.FACTORY,
                        name = "Factory Pattern",
                        participants = listOf(factory.id) + createdClasses,
                        confidence = if (factory.name.contains("Factory", ignoreCase = true)) 0.9 else 0.6,
                        description = "${factory.name} 实现了工厂模式，创建了 ${createdClasses.size} 个类"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * 识别策略模式
     */
    private fun identifyStrategyPattern(graph: ClassDependencyGraph): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        // 查找策略接口
        val strategyInterfaces = graph.nodes.values.filter { node ->
            node.isInterface && (
                node.name.contains("Strategy", ignoreCase = true) ||
                node.name.contains("Algorithm", ignoreCase = true) ||
                node.name.contains("Policy", ignoreCase = true)
            )
        }

        strategyInterfaces.forEach { strategyInterface ->
            val implementations = graph.edges
                .filter { edge ->
                    edge.target == strategyInterface.id && edge.type == ReferenceType.IMPLEMENTATION
                }
                .map { it.source }

            if (implementations.size >= 2) {
                // 查找上下文类
                val contextClasses = graph.nodes.values.filter { context ->
                    graph.edges.any { edge ->
                        edge.source == context.id && implementations.contains(edge.target) &&
                        edge.type == ReferenceType.ASSOCIATION
                    }
                }

                patterns.add(
                    DesignPattern(
                        type = PatternType.STRATEGY,
                        name = "Strategy Pattern",
                        participants = listOf(strategyInterface.id) + implementations + contextClasses.map { it.id },
                        confidence = if (strategyInterface.name.contains("Strategy", ignoreCase = true)) 0.9 else 0.7,
                        description = "发现策略模式，接口: ${strategyInterface.name}，实现: ${implementations.size} 个"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * 识别观察者模式
     */
    private fun identifyObserverPattern(graph: ClassDependencyGraph): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        // 查找观察者接口
        val observerInterfaces = graph.nodes.values.filter { node ->
            node.isInterface && (
                node.name.contains("Observer", ignoreCase = true) ||
                node.name.contains("Listener", ignoreCase = true)
            )
        }

        observerInterfaces.forEach { observerInterface ->
            val observers = graph.edges
                .filter { edge ->
                    edge.target == observerInterface.id && edge.type == ReferenceType.IMPLEMENTATION
                }
                .map { it.source }

            if (observers.isNotEmpty()) {
                // 查找被观察者/主题类
                val subjects = graph.nodes.values.filter { subject ->
                    graph.edges.any { edge ->
                        edge.source == subject.id && observers.contains(edge.target) &&
                        (edge.type == ReferenceType.ASSOCIATION || edge.type == ReferenceType.AGGREGATION)
                    }
                }

                patterns.add(
                    DesignPattern(
                        type = PatternType.OBSERVER,
                        name = "Observer Pattern",
                        participants = listOf(observerInterface.id) + observers + subjects.map { it.id },
                        confidence = if (observerInterface.name.contains("Observer", ignoreCase = true)) 0.9 else 0.6,
                        description = "发现观察者模式，观察者: ${observers.size} 个，被观察者: ${subjects.size} 个"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * 识别装饰器模式
     */
    private fun identifyDecoratorPattern(graph: ClassDependencyGraph): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        graph.nodes.values.filter { node ->
            node.name.contains("Decorator", ignoreCase = true)
        }.forEach { decorator ->
            // 装饰器应该实现与被装饰对象相同的接口
            val decoratedInterfaces = graph.edges
                .filter { edge ->
                    edge.source == decorator.id && edge.type == ReferenceType.IMPLEMENTATION
                }
                .map { it.target }

            // 装饰器应该包含对被装饰对象的引用
            val componentReferences = graph.edges
                .filter { edge ->
                    edge.source == decorator.id && edge.type == ReferenceType.COMPOSITION
                }
                .map { it.target }

            if (decoratedInterfaces.isNotEmpty() && componentReferences.isNotEmpty()) {
                patterns.add(
                    DesignPattern(
                        type = PatternType.DECORATOR,
                        name = "Decorator Pattern",
                        participants = listOf(decorator.id) + decoratedInterfaces + componentReferences,
                        confidence = 0.8,
                        description = "${decorator.name} 实现了装饰器模式"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * 识别适配器模式
     */
    private fun identifyAdapterPattern(graph: ClassDependencyGraph): List<DesignPattern> {
        val patterns = mutableListOf<DesignPattern>()

        graph.nodes.values.filter { node ->
            node.name.contains("Adapter", ignoreCase = true)
        }.forEach { adapter ->
            // 适配器应该实现目标接口
            val targetInterfaces = graph.edges
                .filter { edge ->
                    edge.source == adapter.id && edge.type == ReferenceType.IMPLEMENTATION
                }
                .map { it.target }

            // 适配器应该包含对被适配对象的引用
            val adapteeReferences = graph.edges
                .filter { edge ->
                    edge.source == adapter.id && edge.type == ReferenceType.COMPOSITION
                }
                .map { it.target }

            if (targetInterfaces.isNotEmpty() && adapteeReferences.isNotEmpty()) {
                patterns.add(
                    DesignPattern(
                        type = PatternType.ADAPTER,
                        name = "Adapter Pattern",
                        participants = listOf(adapter.id) + targetInterfaces + adapteeReferences,
                        confidence = 0.8,
                        description = "${adapter.name} 实现了适配器模式"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * 计算模式置信度
     */
    private fun calculatePatternConfidence(indicators: List<Boolean>): Double {
        val trueCount = indicators.count { it }
        return trueCount.toDouble() / indicators.size
    }

    /**
     * 分析类职责
     */
    private fun analyzeClassResponsibilities(analysisResult: DependencyAnalysisResult): Map<String, ClassResponsibility> {
        val responsibilities = mutableMapOf<String, ClassResponsibility>()

        analysisResult.classes.forEach { cls ->
            val methodAnalysis = analyzeMethods(cls, analysisResult)
            val fieldAnalysis = analyzeFields(cls, analysisResult)
            val responsibilityScore = calculateResponsibilityScore(cls, methodAnalysis, fieldAnalysis)
            val cohesionScore = calculateClassCohesion(cls, analysisResult)

            responsibilities[cls.id] = ClassResponsibility(
                classId = cls.id,
                className = cls.qualifiedName,
                primaryResponsibility = identifyPrimaryResponsibility(cls),
                secondaryResponsibilities = identifySecondaryResponsibilities(cls),
                responsibilityScore = responsibilityScore,
                cohesionScore = cohesionScore,
                violations = detectResponsibilityViolations(cls, analysisResult)
            )
        }

        return responsibilities
    }

    /**
     * 分析方法
     */
    private fun analyzeMethods(cls: ClassInfo, analysisResult: DependencyAnalysisResult): MethodAnalysis {
        val methods = analysisResult.methods.filter { it.classId == cls.id }

        val publicMethods = methods.count { "public" in it.modifiers }
        val privateMethods = methods.count { "private" in it.modifiers }
        val staticMethods = methods.count { it.isStatic }
        val highComplexityMethods = methods.count { (it.metrics.complexityScore ?: 0) > 20 }

        return MethodAnalysis(
            totalMethods = methods.size,
            publicMethods = publicMethods,
            privateMethods = privateMethods,
            staticMethods = staticMethods,
            highComplexityMethods = highComplexityMethods,
            averageComplexity = if (methods.isNotEmpty()) {
                methods.map { it.metrics.complexityScore ?: 0 }.average()
            } else 0.0
        )
    }

    /**
     * 分析字段
     */
    private fun analyzeFields(cls: ClassInfo, analysisResult: DependencyAnalysisResult): FieldAnalysis {
        val fields = analysisResult.fields.filter { it.classId == cls.id }

        val publicFields = fields.count { "public" in it.modifiers }
        val staticFields = fields.count { it.isStatic }
        val finalFields = fields.count { it.isFinal }

        return FieldAnalysis(
            totalFields = fields.size,
            publicFields = publicFields,
            staticFields = staticFields,
            finalFields = finalFields
        )
    }

    /**
     * 计算职责评分
     */
    private fun calculateResponsibilityScore(
        cls: ClassInfo,
        methodAnalysis: MethodAnalysis,
        fieldAnalysis: FieldAnalysis
    ): Double {
        // 基于方法数量、字段数量和复杂度计算职责评分
        val methodScore = when {
            methodAnalysis.totalMethods > 20 -> 0.3
            methodAnalysis.totalMethods > 10 -> 0.6
            methodAnalysis.totalMethods > 5 -> 0.8
            else -> 1.0
        }

        val fieldScore = when {
            fieldAnalysis.totalFields > 15 -> 0.3
            fieldAnalysis.totalFields > 8 -> 0.6
            fieldAnalysis.totalFields > 4 -> 0.8
            else -> 1.0
        }

        val complexityScore = when {
            methodAnalysis.averageComplexity > 15 -> 0.3
            methodAnalysis.averageComplexity > 8 -> 0.6
            methodAnalysis.averageComplexity > 4 -> 0.8
            else -> 1.0
        }

        return (methodScore + fieldScore + complexityScore) / 3
    }

    /**
     * 计算类内聚度
     */
    private fun calculateClassCohesion(cls: ClassInfo, analysisResult: DependencyAnalysisResult): Double {
        val methods = analysisResult.methods.filter { it.classId == cls.id }
        if (methods.size <= 1) return 1.0

        // LCOM (Lack of Cohesion of Methods) 的反向计算
        val fieldAccesses = mutableMapOf<String, MutableSet<String>>()

        methods.forEach { method ->
            val accessedFields = mutableSetOf<String>()
            // 这里简化实现，实际应该分析方法体中的字段访问
            method.parameters.forEach { param ->
                accessedFields.add(param.type)
            }
            fieldAccesses[method.id] = accessedFields
        }

        val totalPairs = methods.size * (methods.size - 1) / 2
        var sharedFieldsPairs = 0

        methodIndices@ for (i in methods.indices) {
            for (j in i + 1 until methods.size) {
                val fields1 = fieldAccesses[methods[i].id] ?: emptySet()
                val fields2 = fieldAccesses[methods[j].id] ?: emptySet()

                if (fields1.intersect(fields2).isNotEmpty()) {
                    sharedFieldsPairs++
                }
            }
        }

        return if (totalPairs > 0) sharedFieldsPairs.toDouble() / totalPairs else 0.0
    }

    /**
     * 识别主要职责
     */
    private fun identifyPrimaryResponsibility(cls: ClassInfo): String {
        return when {
            cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> "处理HTTP请求和响应"
            cls.annotations.any { it.contains("Service", ignoreCase = true) } -> "实现业务逻辑和规则"
            cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> "数据访问和持久化"
            cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> "数据传输和存储"
            cls.type == ClassType.INTERFACE -> "定义契约和抽象"
            cls.type == ClassType.ABSTRACT_CLASS -> "提供部分实现和模板"
            cls.name.contains("Config", ignoreCase = true) -> "应用配置管理"
            cls.name.contains("Util", ignoreCase = true) -> "提供通用工具方法"
            cls.name.contains("Exception") -> "异常处理"
            else -> "通用功能实现"
        }
    }

    /**
     * 识别次要职责
     */
    private fun identifySecondaryResponsibilities(cls: ClassInfo): List<String> {
        val responsibilities = mutableListOf<String>()

        if (cls.annotations.any { it.contains("Component", ignoreCase = true) }) {
            responsibilities.add("依赖注入组件")
        }

        if (cls.annotations.any { it.contains("Transactional", ignoreCase = true) }) {
            responsibilities.add("事务管理")
        }

        if (cls.interfaces.isNotEmpty()) {
            responsibilities.add("接口实现")
        }

        if (cls.superClass != null) {
            responsibilities.add("继承扩展")
        }

        return responsibilities
    }

    /**
     * 检测职责违规
     */
    private fun detectResponsibilityViolations(cls: ClassInfo, analysisResult: DependencyAnalysisResult): List<ResponsibilityViolation> {
        val violations = mutableListOf<ResponsibilityViolation>()

        val methods = analysisResult.methods.filter { it.classId == cls.id }

        // 检查方法数量是否过多
        if (methods.size > 20) {
            violations.add(
                ResponsibilityViolation(
                    type = ViolationType.TOO_MANY_METHODS,
                    description = "类包含过多方法 (${methods.size} 个)",
                    severity = Severity.HIGH
                )
            )
        }

        // 检查是否混合了不同层次的职责
        val hasControllerMethods = methods.any { method ->
            method.annotations.any { ann ->
                ann.contains("Mapping", ignoreCase = true) ||
                ann.contains("Request", ignoreCase = true)
            }
        }

        val hasRepositoryMethods = methods.any { method ->
            method.annotations.any { ann ->
                ann.contains("Query", ignoreCase = true) ||
                ann.contains("Select", ignoreCase = true)
            }
        }

        if (hasControllerMethods && hasRepositoryMethods) {
            violations.add(
                ResponsibilityViolation(
                    type = ViolationType.MIXED_RESPONSIBILITIES,
                    description = "混合了表现层和数据访问层的职责",
                    severity = Severity.HIGH
                )
            )
        }

        return violations
    }

    /**
     * 计算类级别度量
     */
    private fun calculateClassMetrics(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): Map<String, ClassMetrics> {
        val metrics = mutableMapOf<String, ClassMetrics>()

        graph.nodes.forEach { (classId, node) ->
            val incomingEdges = graph.edges.filter { it.target == classId }
            val outgoingEdges = graph.edges.filter { it.source == classId }

            metrics[classId] = ClassMetrics(
                size = calculateClassSize(node),
                complexity = node.metrics.complexityScore,
                coupling = node.metrics.coupling,
                cohesion = node.metrics.cohesion,
                instability = calculateInstability(incomingEdges.size, outgoingEdges.size),
                abstractness = calculateAbstractness(node),
                distanceFromMainSequence = Math.abs(
                    calculateAbstractness(node) + calculateInstability(incomingEdges.size, outgoingEdges.size) - 1.0
                ),
                specialty = calculateSpecialty(incomingEdges, outgoingEdges),
                designQuality = calculateDesignQuality(node, incomingEdges, outgoingEdges)
            )
        }

        return metrics
    }

    /**
     * 计算类大小
     */
    private fun calculateClassSize(node: ClassNode): Int {
        return node.metrics.methodCount + node.metrics.fieldCount
    }

    /**
     * 计算不稳定性
     */
    private fun calculateInstability(incomingCount: Int, outgoingCount: Int): Double {
        val total = incomingCount + outgoingCount
        return if (total > 0) outgoingCount.toDouble() / total else 0.0
    }

    /**
     * 计算抽象度
     */
    private fun calculateAbstractness(node: ClassNode): Double {
        return if (node.isInterface) 1.0 else if (node.isAbstract) 0.5 else 0.0
    }

    /**
     * 计算特殊性指标
     */
    private fun calculateSpecialty(incomingEdges: List<ClassEdge>, outgoingEdges: List<ClassEdge>): Double {
        // 特殊性衡量类的依赖程度，越高说明越特殊
        return (incomingEdges.size + outgoingEdges.size).toDouble() / 10.0
    }

    /**
     * 计算设计质量
     */
    private fun calculateDesignQuality(
        node: ClassNode,
        incomingEdges: List<ClassEdge>,
        outgoingEdges: List<ClassEdge>
    ): Double {
        var score = 100.0

        // 基于复杂度扣分
        score -= node.metrics.complexityScore * 0.5

        // 基于耦合度扣分
        score -= node.metrics.coupling * 2.0

        // 基于大小扣分
        val classSize = node.metrics.methodCount + node.metrics.fieldCount
        score -= (classSize - 10) * 0.5

        // 基于内聚度加分
        score += node.metrics.cohesion * 20.0

        return score.coerceIn(0.0, 100.0)
    }

    /**
     * 检测设计问题
     */
    private fun detectDesignIssues(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<DesignIssue> {
        val issues = mutableListOf<DesignIssue>()

        // 检测上帝类
        issues.addAll(detectGodClasses(graph))

        // 检测数据类
        issues.addAll(detectDataClasses(graph))

        // 检测特性嫉妒
        issues.addAll(detectFeatureEnvy(graph, analysisResult))

        // 检测不适当的亲密
        issues.addAll(detectInappropriateIntimacy(graph))

        // 检测拒绝继承
        issues.addAll(detectRefusedBequest(graph))

        return issues
    }

    /**
     * 检测上帝类
     */
    private fun detectGodClasses(graph: ClassDependencyGraph): List<DesignIssue> {
        val issues = mutableListOf<DesignIssue>()

        graph.nodes.values.forEach { node ->
            val isGodClass = (
                node.metrics.methodCount > 30 ||
                node.metrics.fieldCount > 20 ||
                node.metrics.complexityScore > 80 ||
                node.metrics.coupling > 15
            )

            if (isGodClass) {
                issues.add(
                    DesignIssue(
                        type = DesignIssueType.GOD_CLASS,
                        className = node.qualifiedName,
                        description = "类过于庞大和复杂，承担了过多职责",
                        severity = Severity.HIGH,
                        metrics = mapOf(
                            "methodCount" to node.metrics.methodCount,
                            "fieldCount" to node.metrics.fieldCount,
                            "complexity" to node.metrics.complexityScore,
                            "coupling" to node.metrics.coupling
                        ),
                        suggestions = listOf(
                            "将类拆分为多个职责单一的类",
                            "使用组合模式重构",
                            "提取公共功能到工具类"
                        )
                    )
                )
            }
        }

        return issues
    }

    /**
     * 检测数据类
     */
    private fun detectDataClasses(graph: ClassDependencyGraph): List<DesignIssue> {
        val issues = mutableListOf<DesignIssue>()

        graph.nodes.values.forEach { node ->
            val isDataClass = (
                node.metrics.methodCount <= 3 &&
                node.metrics.fieldCount > 5 &&
                node.metrics.complexityScore < 10 &&
                !node.isInterface &&
                !node.isAbstract
            )

            if (isDataClass) {
                issues.add(
                    DesignIssue(
                        type = DesignIssueType.DATA_CLASS,
                        className = node.qualifiedName,
                        description = "类主要包含数据而缺乏行为",
                        severity = Severity.MEDIUM,
                        metrics = mapOf(
                            "methodCount" to node.metrics.methodCount,
                            "fieldCount" to node.metrics.fieldCount
                        ),
                        suggestions = listOf(
                            "为数据类添加相关业务行为",
                            "考虑使用记录类型(Record)替代",
                            "将行为移动到适当的服务类中"
                        )
                    )
                )
            }
        }

        return issues
    }

    /**
     * 检测特性嫉妒
     */
    private fun detectFeatureEnvy(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<DesignIssue> {
        val issues = mutableListOf<DesignIssue>()

        // 简化实现：检查类是否过度使用其他类的字段
        analysisResult.methods.forEach { method ->
            val methodClass = graph.nodes[method.classId]
            if (methodClass != null) {
                // 这里应该分析方法体中的字段访问，简化处理
                if (method.name.startsWith("get") && method.name.contains("Other")) {
                    issues.add(
                        DesignIssue(
                            type = DesignIssueType.FEATURE_ENVY,
                            className = methodClass.qualifiedName,
                            description = "方法 ${method.name} 可能过度使用其他类的功能",
                            severity = Severity.MEDIUM,
                            metrics = mapOf(
                                "method" to method.name,
                                "parameters" to method.parameters.size
                            ),
                            suggestions = listOf(
                                "考虑将方法移动到被使用的类中",
                                "使用委托模式重构",
                                "重新设计类之间的职责分配"
                            )
                        )
                    )
                }
            }
        }

        return issues
    }

    /**
     * 检测不适当的亲密
     */
    private fun detectInappropriateIntimacy(graph: ClassDependencyGraph): List<DesignIssue> {
        val issues = mutableListOf<DesignIssue>()

        graph.nodes.values.forEach { node ->
            val closeDependencies = graph.edges
                .filter { edge ->
                    edge.source == node.id && (
                        edge.type == ReferenceType.ASSOCIATION ||
                        edge.type == ReferenceType.AGGREGATION
                    )
                }
                .groupBy { it.target }
                .filter { (_, edges) -> edges.size > 5 }
                .keys

            closeDependencies.forEach { targetId ->
                val targetNode = graph.nodes[targetId]
                if (targetNode != null) {
                    issues.add(
                        DesignIssue(
                            type = DesignIssueType.INAPPROPRIATE_INTIMACY,
                            className = node.qualifiedName,
                            description = "类 ${node.name} 与 ${targetNode.name} 关系过于紧密",
                            severity = Severity.MEDIUM,
                            metrics = mapOf(
                                "dependencyCount" to graph.edges.count { edge ->
                                    edge.source == node.id && edge.target == targetId
                                }
                            ),
                            suggestions = listOf(
                                "引入接口解耦",
                                "使用依赖注入",
                                "重新设计类之间的关系"
                            )
                        )
                    )
                }
            }
        }

        return issues
    }

    /**
     * 检测拒绝继承
     */
    private fun detectRefusedBequest(graph: ClassDependencyGraph): List<DesignIssue> {
        val issues = mutableListOf<DesignIssue>()

        graph.nodes.values.filter { node ->
            node.superClass != null
        }.forEach { node ->
            val parentClass = graph.nodes.values.find { it.qualifiedName == node.superClass }
            if (parentClass != null) {
                // 简化实现：检查子类是否重写了父类的大部分方法
                val parentMethods = graph.edges
                    .filter { edge ->
                        edge.source == node.id && edge.target == parentClass.id &&
                        edge.type == ReferenceType.INHERITANCE
                    }

                // 这里简化处理，实际应该分析方法重写情况
                if (parentMethods.isNotEmpty() && node.metrics.methodCount < parentClass.metrics.methodCount * 0.5) {
                    issues.add(
                        DesignIssue(
                            type = DesignIssueType.REFUSED_BEQUEST,
                            className = node.qualifiedName,
                            description = "类可能不适合继承自 ${parentClass.name}",
                            severity = Severity.MEDIUM,
                            metrics = mapOf(
                                "childMethods" to node.metrics.methodCount,
                                "parentMethods" to parentClass.metrics.methodCount
                            ),
                            suggestions = listOf(
                                "使用组合替代继承",
                                "重新设计继承层次",
                                "将共同功能提取到接口中"
                            )
                        )
                    )
                }
            }
        }

        return issues
    }

    /**
     * 分析继承层次
     */
    private fun analyzeInheritanceHierarchy(analysisResult: DependencyAnalysisResult): InheritanceHierarchy {
        val roots = mutableListOf<String>()
        val children = mutableMapOf<String, MutableList<String>>()
        val depthMap = mutableMapOf<String, Int>()

        // 构建继承树
        analysisResult.classes.forEach { cls ->
            if (cls.superClass == null) {
                roots.add(cls.id)
            } else {
                val parentId = analysisResult.classes.find { it.qualifiedName == cls.superClass }?.id
                if (parentId != null) {
                    children.getOrPut(parentId) { mutableListOf() }.add(cls.id)
                }
            }
        }

        // 计算继承深度
        fun calculateDepth(classId: String, currentDepth: Int = 0): Int {
            if (classId in depthMap && depthMap[classId]!! >= currentDepth) {
                return depthMap[classId]!!
            }

            depthMap[classId] = currentDepth
            val childIds = children[classId] ?: emptyList()

            return if (childIds.isEmpty()) {
                currentDepth
            } else {
                childIds.maxOf { calculateDepth(it, currentDepth + 1) }
            }
        }

        roots.forEach { calculateDepth(it) }

        // 计算统计信息
        val maxDepth = depthMap.values.maxOrNull() ?: 0
        val averageDepth = if (depthMap.isNotEmpty()) depthMap.values.average() else 0.0
        val classesWithInheritance = analysisResult.classes.count { it.superClass != null }

        return InheritanceHierarchy(
            rootClasses = roots,
            inheritanceTree = children,
            maxDepth = maxDepth,
            averageDepth = averageDepth,
            classesWithInheritance = classesWithInheritance,
            inheritancePercentage = if (analysisResult.classes.isNotEmpty()) {
                classesWithInheritance.toDouble() / analysisResult.classes.size * 100
            } else 0.0
        )
    }

    /**
     * 分析类耦合度
     */
    private fun analyzeClassCoupling(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): ClassCouplingAnalysis {
        val couplingMetrics = mutableMapOf<String, ClassCouplingMetrics>()
        val tightCouplingPairs = mutableListOf<CouplingPair>()
        val circularDependencies = mutableListOf<CircularDependency>()

        graph.nodes.forEach { (classId, node) ->
            val afferentCoupling = graph.edges.count { it.target == classId }
            val efferentCoupling = graph.edges.count { it.source == classId }

            couplingMetrics[classId] = ClassCouplingMetrics(
                afferentCoupling = afferentCoupling,
                efferentCoupling = efferentCoupling,
                instability = if (afferentCoupling + efferentCoupling > 0) {
                    efferentCoupling.toDouble() / (afferentCoupling + efferentCoupling)
                } else 0.0,
                contentCoupling = calculateContentCoupling(graph, classId),
                commonCoupling = calculateCommonCoupling(graph, classId),
                stampCoupling = calculateStampCoupling(graph, classId),
                dataCoupling = calculateDataCoupling(graph, classId)
            )
        }

        // 识别紧耦合对
        graph.edges.forEach { edge ->
            val sourceMetrics = couplingMetrics[edge.source]
            val targetMetrics = couplingMetrics[edge.target]

            if (sourceMetrics != null && targetMetrics != null) {
                val couplingScore = calculateCouplingScore(sourceMetrics, targetMetrics, edge)
                if (couplingScore > 0.8) {
                    tightCouplingPairs.add(
                        CouplingPair(
                            class1 = edge.source,
                            class2 = edge.target,
                            couplingScore = couplingScore,
                            couplingTypes = setOf(edge.type),
                            recommendations = generateCouplingRecommendations(edge)
                        )
                    )
                }
            }
        }

        // 检测循环依赖
        detectClassCyclicDependencies(graph).forEach { cycle ->
            circularDependencies.add(
                CircularDependency(
                    classes = cycle,
                    severity = when (cycle.size) {
                        2 -> CycleSeverity.TWO_WAY
                        3, 4 -> CycleSeverity.SMALL
                        5, 6 -> CycleSeverity.MEDIUM
                        else -> CycleSeverity.LARGE
                    },
                    impact = when (cycle.size) {
                        in 2..3 -> CycleImpact.LOW
                        in 4..6 -> CycleImpact.MEDIUM
                        else -> CycleImpact.HIGH
                    }
                )
            )
        }

        return ClassCouplingAnalysis(
            couplingMetrics = couplingMetrics,
            tightCouplingPairs = tightCouplingPairs,
            circularDependencies = circularDependencies
        )
    }

    /**
     * 计算内容耦合
     */
    private fun calculateContentCoupling(graph: ClassDependencyGraph, classId: String): Double {
        // 内容耦合：一个模块直接修改另一个模块的数据
        return 0.0 // 简化实现
    }

    /**
     * 计算公共耦合
     */
    private fun calculateCommonCoupling(graph: ClassDependencyGraph, classId: String): Double {
        // 公共耦合：多个模块共享全局数据
        return 0.0 // 简化实现
    }

    /**
     * 计算印记耦合
     */
    private fun calculateStampCoupling(graph: ClassDependencyGraph, classId: String): Double {
        // 印记耦合：模块间通过传递复杂数据结构耦合
        val complexDependencies = graph.edges
            .filter { edge ->
                edge.source == classId && (
                    edge.type == ReferenceType.AGGREGATION ||
                    edge.type == ReferenceType.COMPOSITION
                )
            }
            .size

        return complexDependencies.toDouble()
    }

    /**
     * 计算数据耦合
     */
    private fun calculateDataCoupling(graph: ClassDependencyGraph, classId: String): Double {
        // 数据耦合：模块间通过简单参数传递耦合
        val dataDependencies = graph.edges
            .filter { edge ->
                edge.source == classId && edge.type == ReferenceType.ASSOCIATION
            }
            .size

        return dataDependencies.toDouble()
    }

    /**
     * 计算耦合评分
     */
    private fun calculateCouplingScore(
        sourceMetrics: ClassCouplingMetrics,
        targetMetrics: ClassCouplingMetrics,
        edge: ClassEdge
    ): Double {
        var score = 0.0

        // 基于耦合类型评分
        score += when (edge.type) {
            ReferenceType.INHERITANCE -> 0.4
            ReferenceType.IMPLEMENTATION -> 0.3
            ReferenceType.COMPOSITION -> 0.3
            ReferenceType.AGGREGATION -> 0.2
            ReferenceType.ASSOCIATION -> 0.1
            else -> 0.05
        }

        // 基于双向依赖评分
        val hasReverseDependency = sourceMetrics.efferentCoupling > 0 && targetMetrics.afferentCoupling > 0
        if (hasReverseDependency) score += 0.3

        // 基于不稳定性评分
        if (sourceMetrics.instability > 0.7 && targetMetrics.instability > 0.7) {
            score += 0.2
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 生成耦合建议
     */
    private fun generateCouplingRecommendations(edge: ClassEdge): List<String> {
        return when (edge.type) {
            ReferenceType.INHERITANCE -> listOf(
                "考虑使用组合替代继承",
                "引入接口降低耦合"
            )
            ReferenceType.COMPOSITION -> listOf(
                "使用依赖注入",
                "引入接口解耦"
            )
            ReferenceType.ASSOCIATION -> listOf(
                "使用依赖注入",
                "考虑事件驱动架构"
            )
            else -> listOf("重新设计类之间的关系")
        }
    }

    /**
     * 检测类循环依赖
     */
    private fun detectClassCyclicDependencies(graph: ClassDependencyGraph): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(nodeId: String): Boolean {
            if (nodeId in recursionStack) {
                // 找到循环
                val cycleStart = path.indexOf(nodeId)
                if (cycleStart != -1) {
                    cycles.add(path.subList(cycleStart, path.size) + nodeId)
                }
                return true
            }

            if (nodeId in visited) {
                return false
            }

            visited.add(nodeId)
            recursionStack.add(nodeId)
            path.add(nodeId)

            // 遍历依赖
            graph.edges.filter { it.source == nodeId }.forEach { edge ->
                if (dfs(edge.target)) {
                    return true
                }
            }

            recursionStack.remove(nodeId)
            path.removeLast()
            return false
        }

        graph.nodes.keys.forEach { nodeId ->
            if (nodeId !in visited) {
                dfs(nodeId)
            }
        }

        return cycles.distinctBy { it.sorted() }
    }

    /**
     * 识别核心类
     */
    private fun identifyCoreClasses(
        graph: ClassDependencyGraph,
        classMetrics: Map<String, ClassMetrics>
    ): List<CoreClass> {
        val coreClasses = mutableListOf<CoreClass>()

        classMetrics.forEach { (classId, metrics) ->
            val node = graph.nodes[classId] ?: return@forEach

            // 核心类特征：高内聚、低耦合、被广泛使用
            val isCore = (
                metrics.cohesion > 0.7 &&
                metrics.coupling < 10 &&
                metrics.designQuality > 70 &&
                (graph.edges.count { it.target == classId }) >= 3
            )

            if (isCore) {
                val coreScore = calculateCoreClassScore(metrics, graph, classId)
                coreClasses.add(
                    CoreClass(
                        classId = classId,
                        className = node.qualifiedName,
                        coreScore = coreScore,
                        reasons = determineCoreClassReasons(metrics, node, graph, classId),
                        dependents = graph.edges.filter { it.target == classId }.map { it.source }
                    )
                )
            }
        }

        return coreClasses.sortedByDescending { it.coreScore }
    }

    /**
     * 计算核心类评分
     */
    private fun calculateCoreClassScore(
        metrics: ClassMetrics,
        graph: ClassDependencyGraph,
        classId: String
    ): Double {
        val dependents = graph.edges.count { it.target == classId }

        return metrics.cohesion * 0.3 +
               (1.0 - metrics.instability) * 0.2 +
               metrics.designQuality * 0.2 +
               (dependents.toDouble() / 10).coerceAtMost(1.0) * 0.3
    }

    /**
     * 确定核心类原因
     */
    private fun determineCoreClassReasons(
        metrics: ClassMetrics,
        node: ClassNode,
        graph: ClassDependencyGraph,
        classId: String
    ): List<String> {
        val reasons = mutableListOf<String>()

        if (metrics.cohesion > 0.8) {
            reasons.add("高内聚度 (${String.format("%.2f", metrics.cohesion)})")
        }

        if (metrics.instability < 0.3) {
            reasons.add("高稳定性 (${String.format("%.2f", 1.0 - metrics.instability)})")
        }

        val dependents = graph.edges.count { it.target == classId }
        if (dependents >= 5) {
            reasons.add("被${dependents}个类依赖")
        }

        if (metrics.designQuality > 80) {
            reasons.add("优秀的设计质量")
        }

        if (node.stereotypes.contains(ClassStereotype.SERVICE)) {
            reasons.add("核心业务服务")
        }

        return reasons
    }

    /**
     * 分析类聚簇
     */
    private fun analyzeClassClusters(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<ClassCluster> {
        val clusters = mutableListOf<ClassCluster>()

        // 基于包分析聚簇
        val packages = analysisResult.packages
        packages.forEach { pkg ->
            val packageClasses = analysisResult.classes.filter { it.packageId == pkg.id }
            if (packageClasses.size >= 2) {
                val classIds = packageClasses.map { it.id }.toSet()
                val internalEdges = graph.edges.filter { edge ->
                    edge.source in classIds && edge.target in classIds
                }
                val externalEdges = graph.edges.filter { edge ->
                    (edge.source in classIds) xor (edge.target in classIds)
                }

                val cohesion = if (classIds.size > 1) {
                    internalEdges.size.toDouble() / (classIds.size * (classIds.size - 1) / 2)
                } else 0.0

                val coupling = externalEdges.size.toDouble() / classIds.size

                clusters.add(
                    ClassCluster(
                        id = pkg.id,
                        name = pkg.name,
                        classes = classIds,
                        cohesion = cohesion,
                        coupling = coupling,
                        type = ClusterType.PACKAGE_BASED,
                        responsibilities = analyzeClusterResponsibilities(packageClasses)
                    )
                )
            }
        }

        return clusters
    }

    /**
     * 分析聚簇职责
     */
    private fun analyzeClusterResponsibilities(classes: List<ClassInfo>): List<String> {
        val responsibilities = mutableSetOf<String>()

        classes.forEach { cls ->
            when {
                cls.annotations.any { it.contains("Controller", ignoreCase = true) } -> responsibilities.add("Web请求处理")
                cls.annotations.any { it.contains("Service", ignoreCase = true) } -> responsibilities.add("业务逻辑")
                cls.annotations.any { it.contains("Repository", ignoreCase = true) } -> responsibilities.add("数据访问")
                cls.name.contains("DTO", ignoreCase = true) || cls.name.contains("VO", ignoreCase = true) -> responsibilities.add("数据模型")
                cls.type == ClassType.INTERFACE -> responsibilities.add("接口定义")
            }
        }

        return responsibilities.toList()
    }

    /**
     * 识别重构机会
     */
    private fun identifyRefactoringOpportunities(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<RefactoringOpportunity> {
        val opportunities = mutableListOf<RefactoringOpportunity>()

        // 提取方法机会
        opportunities.addAll(identifyExtractMethodOpportunities(analysisResult))

        // 提取类机会
        opportunities.addAll(identifyExtractClassOpportunities(graph, analysisResult))

        // 引入参数对象机会
        opportunities.addAll(identifyIntroduceParameterObjectOpportunities(analysisResult))

        // 替换条件表达式机会
        opportunities.addAll(identifyReplaceConditionalWithPolymorphismOpportunities(graph, analysisResult))

        return opportunities.sortedByDescending { it.priority }
    }

    /**
     * 识别提取方法机会
     */
    private fun identifyExtractMethodOpportunities(analysisResult: DependencyAnalysisResult): List<RefactoringOpportunity> {
        val opportunities = mutableListOf<RefactoringOpportunity>()

        analysisResult.methods.forEach { method ->
            if ((method.metrics.linesOfCode ?: 0) > 30) {
                opportunities.add(
                    RefactoringOpportunity(
                        type = RefactoringType.EXTRACT_METHOD,
                        target = method.classId,
                        description = "方法 ${method.name} 过长 (${method.metrics.linesOfCode} 行)，建议提取子方法",
                        priority = if (method.metrics.linesOfCode > 50) 0.9 else 0.7,
                        estimatedEffort = if (method.metrics.linesOfCode > 50) "高" else "中",
                        benefits = listOf("提高代码可读性", "增强可维护性", "提高复用性")
                    )
                )
            }
        }

        return opportunities
    }

    /**
     * 识别提取类机会
     */
    private fun identifyExtractClassOpportunities(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<RefactoringOpportunity> {
        val opportunities = mutableListOf<RefactoringOpportunity>()

        graph.nodes.values.forEach { node ->
            val shouldExtract = (
                node.metrics.methodCount > 25 ||
                node.metrics.fieldCount > 15 ||
                node.metrics.complexityScore > 70
            )

            if (shouldExtract) {
                opportunities.add(
                    RefactoringOpportunity(
                        type = RefactoringType.EXTRACT_CLASS,
                        target = node.id,
                        description = "类 ${node.name} 过于复杂，建议拆分为多个职责单一的类",
                        priority = 0.8,
                        estimatedEffort = "高",
                        benefits = listOf("降低复杂度", "提高内聚性", "改善可维护性")
                    )
                )
            }
        }

        return opportunities
    }

    /**
     * 识别引入参数对象机会
     */
    private fun identifyIntroduceParameterObjectOpportunities(analysisResult: DependencyAnalysisResult): List<RefactoringOpportunity> {
        val opportunities = mutableListOf<RefactoringOpportunity>()

        analysisResult.methods.forEach { method ->
            if (method.parameters.size > 5) {
                opportunities.add(
                    RefactoringOpportunity(
                        type = RefactoringType.INTRODUCE_PARAMETER_OBJECT,
                        target = method.classId,
                        description = "方法 ${method.name} 参数过多 (${method.parameters.size} 个)，建议引入参数对象",
                        priority = 0.6,
                        estimatedEffort = "中",
                        benefits = listOf("简化方法签名", "提高参数内聚性", "便于扩展")
                    )
                )
            }
        }

        return opportunities
    }

    /**
     * 识别替换条件表达式为多态的机会
     */
    private fun identifyReplaceConditionalWithPolymorphismOpportunities(
        graph: ClassDependencyGraph,
        analysisResult: DependencyAnalysisResult
    ): List<RefactoringOpportunity> {
        val opportunities = mutableListOf<RefactoringOpportunity>()

        // 简化实现：基于方法名识别可能的条件表达式
        analysisResult.methods.forEach { method ->
            val hasConditionals = method.name.contains("check", ignoreCase = true) ||
                               method.name.contains("process", ignoreCase = true) ||
                               method.annotations.any { it.contains("Switch", ignoreCase = true) }

            if (hasConditionals && (method.metrics.linesOfCode ?: 0) > 20) {
                opportunities.add(
                    RefactoringOpportunity(
                        type = RefactoringType.REPLACE_CONDITIONAL_WITH_POLYMORPHISM,
                        target = method.classId,
                        description = "方法 ${method.name} 可能包含复杂的条件逻辑，建议使用多态替代",
                        priority = 0.5,
                        estimatedEffort = "高",
                        benefits = listOf("消除条件逻辑", "提高扩展性", "符合开闭原则")
                    )
                )
            }
        }

        return opportunities
    }

    /**
     * 计算依赖权重
     */
    private fun calculateDependencyWeight(
        sourceClass: ClassInfo,
        targetClass: ClassInfo,
        referenceType: ReferenceType
    ): Int {
        val baseWeight = when (referenceType) {
            ReferenceType.INHERITANCE -> 5
            ReferenceType.IMPLEMENTATION -> 4
            ReferenceType.COMPOSITION -> 3
            ReferenceType.AGGREGATION -> 2
            ReferenceType.ASSOCIATION -> 1
            else -> 1
        }

        // 基于类的复杂度调整权重
        val complexityFactor = (sourceClass.metrics.complexityScore + targetClass.metrics.complexityScore) / 20.0

        return (baseWeight * complexityFactor).toInt()
    }

    /**
     * 生成分析摘要
     */
    private fun generateAnalysisSummary(
        graph: ClassDependencyGraph,
        classMetrics: Map<String, ClassMetrics>,
        designIssues: List<DesignIssue>
    ): ClassAnalysisSummary {
        val totalClasses = graph.nodes.size
        val totalDependencies = graph.edges.size
        val averageComplexity = classMetrics.values.map { it.complexity }.average()
        val averageCohesion = classMetrics.values.map { it.cohesion }.average()
        val averageCoupling = classMetrics.values.map { it.coupling }.average()

        val highComplexityClasses = classMetrics.values.count { it.complexity > 50 }
        val lowCohesionClasses = classMetrics.values.count { it.cohesion < 0.5 }
        val highCouplingClasses = classMetrics.values.count { it.coupling > 10 }

        val criticalIssues = designIssues.count { it.severity == Severity.CRITICAL || it.severity == Severity.HIGH }

        return ClassAnalysisSummary(
            totalClasses = totalClasses,
            totalDependencies = totalDependencies,
            averageComplexity = averageComplexity,
            averageCohesion = averageCohesion,
            averageCoupling = averageCoupling,
            highComplexityClasses = highComplexityClasses,
            lowCohesionClasses = lowCohesionClasses,
            highCouplingClasses = highCouplingClasses,
            criticalDesignIssues = criticalIssues,
            qualityScore = calculateOverallQualityScore(
                averageComplexity, averageCohesion, averageCoupling, criticalIssues, totalClasses
            )
        )
    }

    /**
     * 计算整体质量评分
     */
    private fun calculateOverallQualityScore(
        averageComplexity: Double,
        averageCohesion: Double,
        averageCoupling: Double,
        criticalIssues: Int,
        totalClasses: Int
    ): Int {
        var score = 100

        // 基于复杂度扣分
        score -= (averageComplexity - 20).coerceAtLeast(0.0).toInt()

        // 基于内聚度扣分
        score -= ((1.0 - averageCohesion) * 50).toInt()

        // 基于耦合度扣分
        score -= (averageCoupling - 5).coerceAtLeast(0.0).toInt()

        // 基于严重问题扣分
        score -= criticalIssues * 10

        return score.coerceIn(0, 100)
    }
}

/**
 * 类分析结果
 */
data class ClassAnalysisResult(
    val dependencyGraph: ClassDependencyGraph,
    val designPatterns: List<DesignPattern>,
    val classResponsibilities: Map<String, ClassResponsibility>,
    val classMetrics: Map<String, ClassMetrics>,
    val designIssues: List<DesignIssue>,
    val inheritanceHierarchy: InheritanceHierarchy,
    val couplingAnalysis: ClassCouplingAnalysis,
    val coreClasses: List<CoreClass>,
    val classClusters: List<ClassCluster>,
    val refactoringOpportunities: List<RefactoringOpportunity>,
    val analysisSummary: ClassAnalysisSummary
)

/**
 * 类依赖图
 */
data class ClassDependencyGraph(
    val nodes: Map<String, ClassNode>,
    val edges: List<ClassEdge>
)

/**
 * 类节点
 */
data class ClassNode(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val packageName: String,
    val type: ClassType,
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val modifiers: List<String>,
    val annotations: List<String>,
    val superClass: String?,
    val interfaces: List<String>,
    val metrics: ClassNodeMetrics,
    val stereotypes: Set<ClassStereotype>
)

/**
 * 类节点指标
 */
data class ClassNodeMetrics(
    val methodCount: Int,
    val fieldCount: Int,
    val linesOfCode: Int,
    val complexityScore: Int,
    val cohesion: Double,
    val fanIn: Int,
    val fanOut: Int,
    val coupling: Int
)

/**
 * 类边
 */
data class ClassEdge(
    val source: String,
    val target: String,
    val type: ReferenceType,
    val weight: Int,
    val location: SourceLocation
)

/**
 * 类原型
 */
enum class ClassStereotype {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    POJO,
    COMPONENT,
    CONFIGURATION,
    UTILITY,
    TEST,
    EXCEPTION,
    BUILDER,
    FACTORY,
    MANAGER,
    DATA_TRANSFER
}

/**
 * 设计模式
 */
data class DesignPattern(
    val type: PatternType,
    val name: String,
    val participants: List<String>,
    val confidence: Double,
    val description: String
)

/**
 * 模式类型
 */
enum class PatternType {
    SINGLETON,
    FACTORY,
    STRATEGY,
    OBSERVER,
    DECORATOR,
    ADAPTER
}

/**
 * 类职责分析
 */
data class ClassResponsibility(
    val classId: String,
    val className: String,
    val primaryResponsibility: String,
    val secondaryResponsibilities: List<String>,
    val responsibilityScore: Double,
    val cohesionScore: Double,
    val violations: List<ResponsibilityViolation>
)

/**
 * 方法分析
 */
data class MethodAnalysis(
    val totalMethods: Int,
    val publicMethods: Int,
    val privateMethods: Int,
    val staticMethods: Int,
    val highComplexityMethods: Int,
    val averageComplexity: Double
)

/**
 * 字段分析
 */
data class FieldAnalysis(
    val totalFields: Int,
    val publicFields: Int,
    val staticFields: Int,
    val finalFields: Int
)

/**
 * 职责违规
 */
data class ResponsibilityViolation(
    val type: ViolationType,
    val description: String,
    val severity: Severity
)

/**
 * 违规类型
 */
enum class ViolationType {
    TOO_MANY_METHODS,
    TOO_MANY_FIELDS,
    MIXED_RESPONSIBILITIES,
    LOW_COHESION
}

/**
 * 类度量
 */
data class ClassMetrics(
    val size: Int,
    val complexity: Int,
    val coupling: Int,
    val cohesion: Double,
    val instability: Double,
    val abstractness: Double,
    val distanceFromMainSequence: Double,
    val specialty: Double,
    val designQuality: Double
)

/**
 * 设计问题
 */
data class DesignIssue(
    val type: DesignIssueType,
    val className: String,
    val description: String,
    val severity: Severity,
    val metrics: Map<String, Any>,
    val suggestions: List<String>
)

/**
 * 设计问题类型
 */
enum class DesignIssueType {
    GOD_CLASS,
    DATA_CLASS,
    FEATURE_ENVY,
    INAPPROPRIATE_INTIMACY,
    REFUSED_BEQUEST
}

/**
 * 继承层次
 */
data class InheritanceHierarchy(
    val rootClasses: List<String>,
    val inheritanceTree: Map<String, List<String>>,
    val maxDepth: Int,
    val averageDepth: Double,
    val classesWithInheritance: Int,
    val inheritancePercentage: Double
)

/**
 * 类耦合度分析
 */
data class ClassCouplingAnalysis(
    val couplingMetrics: Map<String, ClassCouplingMetrics>,
    val tightCouplingPairs: List<CouplingPair>,
    val circularDependencies: List<CircularDependency>
)

/**
 * 类耦合度指标
 */
data class ClassCouplingMetrics(
    val afferentCoupling: Int,
    val efferentCoupling: Int,
    val instability: Double,
    val contentCoupling: Double,
    val commonCoupling: Double,
    val stampCoupling: Double,
    val dataCoupling: Double
)

/**
 * 耦合对
 */
data class CouplingPair(
    val class1: String,
    val class2: String,
    val couplingScore: Double,
    val couplingTypes: Set<ReferenceType>,
    val recommendations: List<String>
)

/**
 * 循环依赖
 */
data class CircularDependency(
    val classes: List<String>,
    val severity: CycleSeverity,
    val impact: CycleImpact
)

/**
 * 核心类
 */
data class CoreClass(
    val classId: String,
    val className: String,
    val coreScore: Double,
    val reasons: List<String>,
    val dependents: List<String>
)

/**
 * 类聚簇
 */
data class ClassCluster(
    val id: String,
    val name: String,
    val classes: Set<String>,
    val cohesion: Double,
    val coupling: Double,
    val type: ClusterType,
    val responsibilities: List<String>
)

/**
 * 聚簇类型
 */
enum class ClusterType {
    PACKAGE_BASED,
    FUNCTIONAL_BASED,
    HIERARCHICAL_BASED
}

/**
 * 重构机会
 */
data class RefactoringOpportunity(
    val type: RefactoringType,
    val target: String,
    val description: String,
    val priority: Double,
    val estimatedEffort: String,
    val benefits: List<String>
)

/**
 * 重构类型
 */
enum class RefactoringType {
    EXTRACT_METHOD,
    EXTRACT_CLASS,
    INTRODUCE_PARAMETER_OBJECT,
    REPLACE_CONDITIONAL_WITH_POLYMORPHISM
}

/**
 * 类分析摘要
 */
data class ClassAnalysisSummary(
    val totalClasses: Int,
    val totalDependencies: Int,
    val averageComplexity: Double,
    val averageCohesion: Double,
    val averageCoupling: Double,
    val highComplexityClasses: Int,
    val lowCohesionClasses: Int,
    val highCouplingClasses: Int,
    val criticalDesignIssues: Int,
    val qualityScore: Int
)