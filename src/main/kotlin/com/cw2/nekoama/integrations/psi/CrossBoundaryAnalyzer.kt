package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.psi.*

/**
 * 跨边界使用分析器
 * 分析不同业务场景、层级、模块之间的交叉使用情况
 * 增强版本：支持配置化的架构层级规则和复杂模式匹配
 */
class CrossBoundaryAnalyzer {

    private val logger = NekoamaLogger

    /**
     * 配置化的架构层级定义
     */
    data class ArchitecturalLayerConfig(
        val name: String,
        val patterns: List<String>,
        val allowedDependencies: Set<String>,
        val forbiddenDependencies: Set<String> = emptySet(),
        val description: String = ""
    )

    /**
     * 预定义的架构层级配置
     */
    private val layerConfigs = listOf(
        ArchitecturalLayerConfig(
            name = "Controller",
            patterns = listOf(".*Controller", ".*Rest", ".*Api", ".*Endpoint", ".*Resource"),
            allowedDependencies = setOf("Service", "DTO", "Configuration", "Utility"),
            forbiddenDependencies = setOf("Repository", "Entity", "DAO"),
            description = "控制层：处理HTTP请求，协调业务逻辑"
        ),
        ArchitecturalLayerConfig(
            name = "Service",
            patterns = listOf(".*Service", ".*Manager", ".*Business", ".*Logic"),
            allowedDependencies = setOf("Repository", "Entity", "DTO", "Utility", "Configuration"),
            forbiddenDependencies = setOf("Controller"),
            description = "业务层：实现业务逻辑和规则"
        ),
        ArchitecturalLayerConfig(
            name = "Repository",
            patterns = listOf(".*Repository", ".*DAO", ".*Persistence"),
            allowedDependencies = setOf("Entity", "Utility", "Configuration"),
            forbiddenDependencies = setOf("Controller", "Service", "DTO"),
            description = "数据访问层：数据持久化和查询"
        ),
        ArchitecturalLayerConfig(
            name = "Entity",
            patterns = listOf(".*Entity", ".*Model", ".*Domain", ".*POJO"),
            allowedDependencies = setOf("Utility"),
            forbiddenDependencies = setOf("Controller", "Service", "Repository", "DTO"),
            description = "实体层：业务领域数据模型"
        ),
        ArchitecturalLayerConfig(
            name = "DTO",
            patterns = listOf(".*DTO", ".*VO", ".*DataTransfer", ".*Response", ".*Request"),
            allowedDependencies = setOf("Utility", "Entity"),
            forbiddenDependencies = setOf("Controller", "Service", "Repository"),
            description = "数据传输对象：系统间数据交换"
        ),
        ArchitecturalLayerConfig(
            name = "Configuration",
            patterns = listOf(".*Config", ".*Configuration", ".*Properties"),
            allowedDependencies = setOf("Utility", "Entity", "DTO"),
            forbiddenDependencies = setOf("Controller", "Service", "Repository"),
            description = "配置层：系统配置和参数"
        ),
        ArchitecturalLayerConfig(
            name = "Utility",
            patterns = listOf(".*Util", ".*Helper", ".*Tool", ".*Common", ".*Shared"),
            allowedDependencies = setOf("Utility", "Configuration"),
            forbiddenDependencies = emptySet(),
            description = "工具层：通用工具和辅助功能"
        )
    )

    /**
     * 层级统计信息
     */
    data class LayerStatistics(
        var classCount: Int = 0,
        var violationCount: Int = 0,
        var dependencyCount: Int = 0
    )

    /**
     * 分析跨边界使用情况
     */
    fun analyzeCrossBoundaryUsage(
        classDependencies: List<ClassDependency>,
        methodCalls: List<MethodCall>,
        businessEntryPoints: List<BusinessEntryPoint>
    ): CrossBoundaryAnalysisResult {
        val businessScenarios = groupEntryPointsByScenario(businessEntryPoints)
        val scenarioBoundaries = analyzeScenarioBoundaries(businessScenarios, methodCalls)
        val architecturalLayers = analyzeArchitecturalLayerViolations(classDependencies)
        val moduleBoundaries = analyzeModuleBoundaries(classDependencies)
        val dataAccessBoundaries = analyzeDataAccessBoundaries(classDependencies, methodCalls)

        return CrossBoundaryAnalysisResult(
            scenarioCrossUsage = scenarioBoundaries,
            architecturalLayerViolations = architecturalLayers,
            moduleBoundaryViolations = moduleBoundaries,
            dataAccessViolations = dataAccessBoundaries,
            boundaryViolationCount = scenarioBoundaries.violations.size +
                    architecturalLayers.violations.size +
                    moduleBoundaries.violations.size +
                    dataAccessBoundaries.violations.size,
            recommendations = generateBoundaryRecommendations(
                scenarioBoundaries,
                architecturalLayers,
                moduleBoundaries,
                dataAccessBoundaries
            )
        )
    }

    /**
     * 分析业务场景边界
     */
    private fun analyzeScenarioBoundaries(
        businessScenarios: Map<String, List<BusinessEntryPoint>>,
        methodCalls: List<MethodCall>
    ): ScenarioBoundaryAnalysis {
        val violations = mutableListOf<ScenarioBoundaryViolation>()
        val crossUsages = mutableMapOf<String, MutableSet<String>>()

        // 构建场景到类的映射
        val scenarioToClasses = mutableMapOf<String, MutableSet<String>>()
        businessScenarios.forEach { (scenario, entryPoints) ->
            val classes = entryPoints.map { it.className }.toMutableSet()
            scenarioToClasses[scenario] = classes
        }

        // 分析方法调用中的跨场景使用
        methodCalls.forEach { methodCall ->
            val callerScenario = findScenarioForClass(methodCall.callerClass, scenarioToClasses)
            val calleeScenario = findScenarioForClass(methodCall.calleeClass, scenarioToClasses)

            if (callerScenario != null && calleeScenario != null && callerScenario != calleeScenario) {
                // 记录跨场景使用
                crossUsages.getOrPut(callerScenario) { mutableSetOf() }.add(calleeScenario)

                // 检查是否为违规
                if (isScenarioBoundaryViolation(callerScenario, calleeScenario, methodCall)) {
                    violations.add(
                        ScenarioBoundaryViolation(
                            callerScenario = callerScenario,
                            calleeScenario = calleeScenario,
                            callerClass = methodCall.callerClass,
                            calleeClass = methodCall.calleeClass,
                            methodCall = methodCall,
                            violationType = determineScenarioViolationType(callerScenario, calleeScenario),
                            severity = determineViolationSeverity(callerScenario, calleeScenario)
                        )
                    )
                }
            }
        }

        return ScenarioBoundaryAnalysis(
            scenarioCount = scenarioToClasses.size,
            crossScenarioUsages = crossUsages.mapValues { it.value.toList() },
            violations = violations,
            mostCoupledScenarios = findMostCoupledScenarios(crossUsages)
        )
    }

    /**
     * 分析架构层次违规
     * 增强版本：使用配置化规则和复杂模式匹配
     */
    private fun analyzeArchitecturalLayerViolations(
        classDependencies: List<ClassDependency>
    ): ArchitecturalLayerAnalysis {
        val violations = mutableListOf<LayerViolation>()
        val layerStatistics = mutableMapOf<String, LayerStatistics>()

        classDependencies.forEach { classDep ->
            val callerLayer = determineArchitecturalLayerEnhanced(classDep.className, classDep)

            // 记录层级统计信息
            layerStatistics.getOrPut(callerLayer) { LayerStatistics() }.classCount++

            classDep.dependencies.forEach { dependency ->
                val calleeLayer = determineArchitecturalLayerEnhanced(dependency.className, null)

                val violationResult = checkArchitecturalLayerViolation(callerLayer, calleeLayer, classDep.className, dependency.className)

                if (violationResult.isViolation) {
                    violations.add(
                        LayerViolation(
                            callerClass = classDep.className,
                            calleeClass = dependency.className,
                            callerLayer = callerLayer,
                            calleeLayer = calleeLayer,
                            violationType = violationResult.violationType,
                            severity = Severity.valueOf(violationResult.severity),
                            description = violationResult.description
                        )
                    )
                }
            }
        }

        return ArchitecturalLayerAnalysis(
            totalViolations = violations.size,
            violations = violations,
            layerDistribution = calculateLayerDistribution(violations),
            mostViolatedLayers = findMostViolatedLayers(violations)
        )
    }

    /**
     * 增强的架构层级确定方法
     * 使用配置化模式匹配和复杂规则
     */
    private fun determineArchitecturalLayerEnhanced(
        className: String,
        classDependency: ClassDependency?
    ): String {
        // 1. 首先使用配置化的模式匹配
        for (config in layerConfigs) {
            for (pattern in config.patterns) {
                if (className.matches(Regex(pattern))) {
                    return config.name
                }
            }
        }

        // 2. 使用传统规则作为后备
        return when {
            classDependency?.isController == true -> "Controller"
            classDependency?.isService == true -> "Service"
            classDependency?.isRepository == true -> "Repository"
            classDependency?.isPojo == true -> "Entity"
            else -> "Unknown"
        }
    }

    /**
     * 违规检查结果
     */
    data class ViolationResult(
        val isViolation: Boolean,
        val violationType: String,
        val severity: String,
        val description: String,
        val suggestion: String
    )

    /**
     * 检查架构层次违规
     * 使用配置化规则进行判断
     */
    private fun checkArchitecturalLayerViolation(
        callerLayer: String,
        calleeLayer: String,
        callerClass: String,
        calleeClass: String
    ): ViolationResult {
        if (callerLayer == "Unknown" || calleeLayer == "Unknown") {
            return ViolationResult(
                isViolation = false,
                violationType = "",
                severity = "INFO",
                description = "",
                suggestion = ""
            )
        }

        val callerConfig = layerConfigs.find { it.name == callerLayer }
        val calleeConfig = layerConfigs.find { it.name == calleeLayer }

        return when {
            // 检查是否在禁止依赖列表中
            callerConfig?.forbiddenDependencies?.contains(calleeLayer) == true -> {
                ViolationResult(
                    isViolation = true,
                    violationType = "FORBIDDEN_DEPENDENCY",
                    severity = "HIGH",
                    description = "禁止依赖：${callerConfig.description} 不应直接依赖 ${calleeConfig?.description ?: calleeLayer}",
                    suggestion = "考虑通过${callerConfig.allowedDependencies.intersect(calleeConfig?.allowedDependencies ?: emptySet()).firstOrNull() ?: "Service"}层进行间接访问"
                )
            }

            // 检查是否在允许依赖列表中
            callerConfig?.allowedDependencies?.contains(calleeLayer) == false -> {
                ViolationResult(
                    isViolation = true,
                    violationType = "UNEXPECTED_DEPENDENCY",
                    severity = "MEDIUM",
                    description = "意外依赖：${callerConfig.description} 依赖了未预期的 ${calleeConfig?.description ?: calleeLayer}",
                    suggestion = "验证此依赖的合理性，或考虑重构以符合架构规范"
                )
            }

            // 同层依赖通常是可以的，但给出建议
            callerLayer == calleeLayer -> {
                ViolationResult(
                    isViolation = false,
                    violationType = "SAME_LAYER",
                    severity = "LOW",
                    description = "同层依赖：${callerConfig?.description ?: callerLayer} 依赖同层组件",
                    suggestion = "同层依赖是正常的，但需注意避免循环依赖"
                )
            }

            else -> ViolationResult(
                isViolation = false,
                violationType = "ALLOWED_DEPENDENCY",
                severity = "LOW",
                description = "允许依赖：符合架构规范的层级调用",
                suggestion = ""
            )
        }
    }

    /**
     * 分析模块边界违规
     */
    private fun analyzeModuleBoundaries(
        classDependencies: List<ClassDependency>
    ): ModuleBoundaryAnalysis {
        val violations = mutableListOf<ModuleBoundaryViolation>()
        val moduleDependencies = mutableMapOf<String, MutableSet<String>>()

        classDependencies.forEach { classDep ->
            val callerModule = extractModuleName(classDep.className)
            classDep.dependencies.forEach { dependency ->
                val calleeModule = extractModuleName(dependency.className)

                if (callerModule != calleeModule) {
                    // 记录模块依赖
                    moduleDependencies.getOrPut(callerModule) { mutableSetOf() }.add(calleeModule)

                    // 检查是否为违规
                    if (isModuleBoundaryViolation(callerModule, calleeModule)) {
                        violations.add(
                            ModuleBoundaryViolation(
                                callerModule = callerModule,
                                calleeModule = calleeModule,
                                callerClass = classDep.className,
                                calleeClass = dependency.className,
                                violationType = determineModuleViolationType(callerModule, calleeModule),
                                severity = determineModuleViolationSeverity(callerModule, calleeModule)
                            )
                        )
                    }
                }
            }
        }

        return ModuleBoundaryAnalysis(
            moduleCount = moduleDependencies.size,
            moduleDependencies = moduleDependencies.mapValues { it.value.toList() },
            violations = violations,
            circularDependencies = detectCircularModuleDependencies(moduleDependencies),
            mostCoupledModules = findMostCoupledModules(moduleDependencies)
        )
    }

    /**
     * 分析数据访问边界违规
     */
    private fun analyzeDataAccessBoundaries(
        classDependencies: List<ClassDependency>,
        methodCalls: List<MethodCall>
    ): DataAccessBoundaryAnalysis {
        val violations = mutableListOf<DataAccessViolation>()
        val directDataAccess = mutableListOf<DirectDataAccess>()

        classDependencies.forEach { classDep ->
            val isDataAccessClass = isDataAccessClass(classDep)
            val isBusinessClass = isBusinessClass(classDep)
            val isControllerClass = isControllerClass(classDep)

            classDep.dependencies.forEach { dependency ->
                val isDataDependency = isDataAccessDependency(dependency.className)

                when {
                    // 业务类直接访问数据库
                    isBusinessClass && isDataDependency && !isDataAccessClass -> {
                        violations.add(
                            DataAccessViolation(
                                violatorClass = classDep.className,
                                dataAccessClass = dependency.className,
                                violationType = DataAccessViolationType.BUSINESS_DIRECT_DATA_ACCESS,
                                severity = Severity.HIGH,
                                description = "业务类直接访问数据访问层，违反分层架构原则",
                                recommendation = "应该通过Repository或Service层访问数据"
                            )
                        )
                    }

                    // Controller直接访问数据库
                    isControllerClass && isDataDependency && !isDataAccessClass -> {
                        violations.add(
                            DataAccessViolation(
                                violatorClass = classDep.className,
                                dataAccessClass = dependency.className,
                                violationType = DataAccessViolationType.CONTROLLER_DIRECT_DATA_ACCESS,
                                severity = Severity.CRITICAL,
                                description = "Controller直接访问数据访问层，严重违反分层架构",
                                recommendation = "Controller只能调用Service层，数据访问应该委托给Service"
                            )
                        )
                    }

                    // 记录直接数据访问
                    isDataDependency -> {
                        directDataAccess.add(
                            DirectDataAccess(
                                accessorClass = classDep.className,
                                dataClass = dependency.className,
                                accessType = if (isDataAccessClass) "Repository" else "Direct",
                                location = dependency.location
                            )
                        )
                    }
                }
            }
        }

        // 分析方法调用中的数据访问模式
        methodCalls.forEach { methodCall ->
            if (isDataAccessMethod(methodCall.calleeClass, methodCall.calleeMethod)) {
                val callerLayer = determineArchitecturalLayer(methodCall.callerClass, null)

                if (callerLayer == "Controller") {
                    violations.add(
                        DataAccessViolation(
                            violatorClass = methodCall.callerClass,
                            dataAccessClass = methodCall.calleeClass,
                            violationType = DataAccessViolationType.CONTROLLER_DIRECT_DATA_ACCESS,
                            severity = Severity.CRITICAL,
                            description = "Controller直接调用数据访问方法: ${methodCall.calleeMethod}",
                            recommendation = "所有数据访问应该通过Service层进行"
                        )
                    )
                }
            }
        }

        return DataAccessBoundaryAnalysis(
            violations = violations,
            directDataAccess = directDataAccess,
            dataAccessPatterns = analyzeDataAccessPatterns(directDataAccess),
            mostDataAccessingClasses = findMostDataAccessingClasses(directDataAccess)
        )
    }

    /**
     * 查找类所属的业务场景
     */
    private fun findScenarioForClass(
        className: String,
        scenarioToClasses: Map<String, Set<String>>
    ): String? {
        return scenarioToClasses.entries.find { (_, classes) ->
            classes.any { scenarioClass ->
                className.startsWith(scenarioClass.substringBeforeLast(".")) ||
                scenarioClass.startsWith(className.substringBeforeLast("."))
            }
        }?.key
    }

    /**
     * 判断是否为场景边界违规
     */
    private fun isScenarioBoundaryViolation(
        callerScenario: String,
        calleeScenario: String,
        methodCall: MethodCall
    ): Boolean {
        // 业务规则：某些场景不应该调用其他场景的方法
        val forbiddenCalls = mapOf(
            "用户登录" to setOf("订单创建", "支付处理"),
            "支付处理" to setOf("用户登录"),
            "通知发送" to setOf("用户认证", "支付处理")
        )

        val forbiddenTargets = forbiddenCalls[callerScenario] ?: emptySet()
        return calleeScenario in forbiddenTargets
    }

    /**
     * 确定场景违规类型
     */
    private fun determineScenarioViolationType(
        callerScenario: String,
        calleeScenario: String
    ): String {
        return when {
            callerScenario.contains("用户") && calleeScenario.contains("订单") -> "用户模块调用订单模块"
            callerScenario.contains("支付") && calleeScenario.contains("用户") -> "支付模块调用用户模块"
            callerScenario.contains("通知") && calleeScenario.contains("认证") -> "通知模块调用认证模块"
            else -> "${callerScenario} -> ${calleeScenario}"
        }
    }

    /**
     * 确定架构层次
     */
    private fun determineArchitecturalLayer(
        className: String,
        classDependency: ClassDependency?
    ): String {
        return when {
            className.contains("Controller") || className.contains("Rest") || classDependency?.isController == true -> "Controller"
            className.contains("Service") || classDependency?.isService == true -> "Service"
            className.contains("Repository") || className.contains("DAO") || classDependency?.isRepository == true -> "Repository"
            className.contains("Entity") || className.contains("Model") || classDependency?.isPojo == true -> "Entity"
            className.contains("Util") || className.contains("Helper") -> "Utility"
            className.contains("Config") || className.contains("Configuration") -> "Configuration"
            className.contains("DTO") || className.contains("VO") -> "DTO"
            else -> "Unknown"
        }
    }

    /**
     * 计算增强的层级分布
     */
    private fun calculateEnhancedLayerDistribution(
        layerStatistics: Map<String, LayerStatistics>
    ): Map<String, Int> {
        return layerStatistics.mapValues { it.value.classCount }
    }

    /**
     * 增强的最违规层级查找
     */
    private fun findMostViolatedLayersEnhanced(violations: List<LayerViolation>): List<String> {
        return violations.groupBy { it.callerLayer }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    /**
     * 判断是否为架构层次违规
     * 保留原方法以兼容现有代码，但内部使用新的增强方法
     */
    private fun isArchitecturalLayerViolation(callerLayer: String, calleeLayer: String): Boolean {
        val result = checkArchitecturalLayerViolation(callerLayer, calleeLayer, "", "")
        return result.isViolation
    }

    /**
     * 提取模块名称
     */
    private fun extractModuleName(className: String): String {
        val parts = className.split(".")
        return when {
            parts.size >= 3 -> parts[2] // 通常格式: com.company.module.ClassName
            parts.size >= 2 -> parts[1]
            else -> parts[0]
        }
    }

    /**
     * 判断是否为模块边界违规
     */
    private fun isModuleBoundaryViolation(callerModule: String, calleeModule: String): Boolean {
        // 简单的模块边界规则
        val moduleDependencies = mapOf(
            "web" to setOf("service", "common"),
            "service" to setOf("repository", "common", "integration"),
            "repository" to setOf("common"),
            "integration" to setOf("common")
        )

        val allowedDependencies = moduleDependencies[callerModule] ?: emptySet()
        return calleeModule !in allowedDependencies && callerModule != calleeModule
    }

    /**
     * 判断是否为数据访问类
     */
    private fun isDataAccessClass(classDep: ClassDependency): Boolean {
        return classDep.isRepository ||
                classDep.className.contains("DAO") ||
                classDep.className.contains("Repository") ||
                classDep.className.contains("Mapper")
    }

    /**
     * 判断是否为业务类
     */
    private fun isBusinessClass(classDep: ClassDependency): Boolean {
        return classDep.isService ||
                classDep.className.contains("Service") ||
                classDep.className.contains("Manager") ||
                classDep.className.contains("Handler")
    }

    /**
     * 判断是否为Controller类
     */
    private fun isControllerClass(classDep: ClassDependency): Boolean {
        return classDep.isController ||
                classDep.className.contains("Controller") ||
                classDep.className.contains("RestController")
    }

    /**
     * 判断是否为数据访问依赖
     */
    private fun isDataAccessDependency(className: String): Boolean {
        return className.contains("Repository") ||
                className.contains("DAO") ||
                className.contains("Mapper") ||
                className.contains("Entity") ||
                className.contains("Model") ||
                className.contains("Table")
    }

    /**
     * 判断是否为数据访问方法
     */
    private fun isDataAccessMethod(className: String, methodName: String): Boolean {
        val isDataAccessClass = isDataAccessDependency(className)
        val isDataAccessMethod = methodName.startsWith("find") ||
                methodName.startsWith("save") ||
                methodName.startsWith("delete") ||
                methodName.startsWith("update") ||
                methodName.startsWith("insert") ||
                methodName.startsWith("query") ||
                methodName.startsWith("select")

        return isDataAccessClass && isDataAccessMethod
    }

    /**
     * 检测循环模块依赖
     */
    private fun detectCircularModuleDependencies(
        moduleDependencies: Map<String, Set<String>>
    ): List<CircularDependency> {
        val circularDeps = mutableListOf<CircularDependency>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        moduleDependencies.forEach { module ->
            if (module.key !in visited) {
                detectCycleInModuleDependencies(
                    module.key,
                    moduleDependencies,
                    visited,
                    recursionStack,
                    mutableListOf(),
                    circularDeps
                )
            }
        }

        return circularDeps
    }

    /**
     * 递归检测模块依赖循环
     */
    private fun detectCycleInModuleDependencies(
        currentModule: String,
        moduleDependencies: Map<String, Set<String>>,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        path: MutableList<String>,
        circularDeps: MutableList<CircularDependency>
    ) {
        visited.add(currentModule)
        recursionStack.add(currentModule)
        path.add(currentModule)

        moduleDependencies[currentModule]?.forEach { dependentModule ->
            if (dependentModule in recursionStack) {
                // 找到循环
                val cycleStart = path.indexOf(dependentModule)
                val cyclePath = path.subList(cycleStart, path.size) + dependentModule
                circularDeps.add(
                    CircularDependency(
                        involvedModules = cyclePath,
                        cycleLength = cyclePath.size - 1
                    )
                )
            } else if (dependentModule !in visited) {
                detectCycleInModuleDependencies(
                    dependentModule,
                    moduleDependencies,
                    visited,
                    recursionStack,
                    path,
                    circularDeps
                )
            }
        }

        recursionStack.remove(currentModule)
        path.removeAt(path.size - 1)
    }

    /**
     * 按业务场景分组入口点
     */
    private fun groupEntryPointsByScenario(
        entryPoints: List<BusinessEntryPoint>
    ): Map<String, List<BusinessEntryPoint>> {
        return entryPoints.groupBy { it.businessScenario }
    }

    /**
     * 生成边界建议
     */
    private fun generateBoundaryRecommendations(
        scenarioBoundaries: ScenarioBoundaryAnalysis,
        architecturalLayers: ArchitecturalLayerAnalysis,
        moduleBoundaries: ModuleBoundaryAnalysis,
        dataAccessBoundaries: DataAccessBoundaryAnalysis
    ): List<BoundaryRecommendation> {
        val recommendations = mutableListOf<BoundaryRecommendation>()

        // 场景边界建议
        if (scenarioBoundaries.violations.isNotEmpty()) {
            recommendations.add(
                BoundaryRecommendation(
                    type = "场景边界",
                    description = "发现 ${scenarioBoundaries.violations.size} 个业务场景边界违规，建议重新梳理业务边界",
                    priority = if (scenarioBoundaries.violations.any { it.severity == Severity.CRITICAL }) "高" else "中",
                    actionItems = listOf(
                        "重新定义业务场景边界",
                        "引入领域事件解耦场景依赖",
                        "考虑使用API网关进行场景隔离"
                    )
                )
            )
        }

        // 架构层次建议
        if (architecturalLayers.totalViolations > 0) {
            recommendations.add(
                BoundaryRecommendation(
                    type = "架构层次",
                    description = "发现 ${architecturalLayers.totalViolations} 个层次违规，需要重新设计分层架构",
                    priority = if (architecturalLayers.violations.any { it.severity == Severity.CRITICAL }) "高" else "中",
                    actionItems = listOf(
                        "明确各层职责边界",
                        "引入依赖倒置原则",
                        "使用接口和抽象类降低耦合"
                    )
                )
            )
        }

        // 模块边界建议
        if (moduleBoundaries.violations.isNotEmpty() || moduleBoundaries.circularDependencies.isNotEmpty()) {
            recommendations.add(
                BoundaryRecommendation(
                    type = "模块边界",
                    description = "发现模块边界问题：${moduleBoundaries.violations.size} 个违规，${moduleBoundaries.circularDependencies.size} 个循环依赖",
                    priority = "中",
                    actionItems = listOf(
                        "重新设计模块依赖关系",
                        "消除循环依赖",
                        "引入公共模块减少重复依赖"
                    )
                )
            )
        }

        // 数据访问边界建议
        if (dataAccessBoundaries.violations.isNotEmpty()) {
            recommendations.add(
                BoundaryRecommendation(
                    type = "数据访问边界",
                    description = "发现 ${dataAccessBoundaries.violations.size} 个数据访问违规，需要重新设计数据访问模式",
                    priority = if (dataAccessBoundaries.violations.any { it.severity == Severity.CRITICAL }) "高" else "中",
                    actionItems = listOf(
                        "统一数据访问通过Repository层",
                        "Controller只能调用Service层",
                        "引入缓存层减少直接数据库访问"
                    )
                )
            )
        }

        return recommendations
    }

    // 辅助方法
    private fun determineViolationSeverity(callerScenario: String, calleeScenario: String): Severity {
        return when {
            callerScenario.contains("支付") && calleeScenario.contains("用户") -> Severity.HIGH
            callerScenario.contains("用户认证") && calleeScenario.contains("通知") -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    private fun determineLayerViolationSeverity(callerLayer: String, calleeLayer: String): Severity {
        return when {
            callerLayer == "Controller" && calleeLayer == "Repository" -> Severity.CRITICAL
            callerLayer == "Controller" && calleeLayer == "Entity" -> Severity.HIGH
            callerLayer == "Service" && calleeLayer == "Controller" -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    private fun findMostCoupledScenarios(
        crossUsages: Map<String, Set<String>>
    ): List<Pair<String, Int>> {
        return crossUsages.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    private fun calculateLayerDistribution(violations: List<LayerViolation>): Map<String, Int> {
        return violations.groupBy { "${it.callerLayer}->${it.calleeLayer}" }
            .mapValues { it.value.size }
    }

    private fun findMostViolatedLayers(violations: List<LayerViolation>): List<Pair<String, Int>> {
        return violations.groupBy { it.calleeLayer }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    private fun determineModuleViolationType(callerModule: String, calleeModule: String): String {
        return "${callerModule} -> ${calleeModule}"
    }

    private fun determineModuleViolationSeverity(callerModule: String, calleeModule: String): Severity {
        return when {
            callerModule == "web" && calleeModule == "repository" -> Severity.HIGH
            callerModule == "controller" && calleeModule == "dao" -> Severity.HIGH
            else -> Severity.MEDIUM
        }
    }

    private fun findMostCoupledModules(
        moduleDependencies: Map<String, Set<String>>
    ): List<Pair<String, Int>> {
        return moduleDependencies.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    private fun analyzeDataAccessPatterns(directDataAccess: List<DirectDataAccess>): Map<String, Int> {
        return directDataAccess.groupBy { it.accessType }
            .mapValues { it.value.size }
    }

    private fun findMostDataAccessingClasses(directDataAccess: List<DirectDataAccess>): List<Pair<String, Int>> {
        return directDataAccess.groupBy { it.accessorClass }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }
}

/**
 * 跨边界分析结果
 */
data class CrossBoundaryAnalysisResult(
    val scenarioCrossUsage: ScenarioBoundaryAnalysis,
    val architecturalLayerViolations: ArchitecturalLayerAnalysis,
    val moduleBoundaryViolations: ModuleBoundaryAnalysis,
    val dataAccessViolations: DataAccessBoundaryAnalysis,
    val boundaryViolationCount: Int,
    val recommendations: List<BoundaryRecommendation>
)

/**
 * 场景边界分析
 */
data class ScenarioBoundaryAnalysis(
    val scenarioCount: Int,
    val crossScenarioUsages: Map<String, List<String>>,
    val violations: List<ScenarioBoundaryViolation>,
    val mostCoupledScenarios: List<Pair<String, Int>>
)

/**
 * 场景边界违规
 */
data class ScenarioBoundaryViolation(
    val callerScenario: String,
    val calleeScenario: String,
    val callerClass: String,
    val calleeClass: String,
    val methodCall: MethodCall,
    val violationType: String,
    val severity: Severity
)

/**
 * 架构层次分析
 */
data class ArchitecturalLayerAnalysis(
    val totalViolations: Int,
    val violations: List<LayerViolation>,
    val layerDistribution: Map<String, Int>,
    val mostViolatedLayers: List<Pair<String, Int>>
)

/**
 * 层次违规
 */
data class LayerViolation(
    val callerClass: String,
    val calleeClass: String,
    val callerLayer: String,
    val calleeLayer: String,
    val violationType: String,
    val severity: Severity,
    val description: String
)

/**
 * 模块边界分析
 */
data class ModuleBoundaryAnalysis(
    val moduleCount: Int,
    val moduleDependencies: Map<String, List<String>>,
    val violations: List<ModuleBoundaryViolation>,
    val circularDependencies: List<CircularDependency>,
    val mostCoupledModules: List<Pair<String, Int>>
)

/**
 * 模块边界违规
 */
data class ModuleBoundaryViolation(
    val callerModule: String,
    val calleeModule: String,
    val callerClass: String,
    val calleeClass: String,
    val violationType: String,
    val severity: Severity
)

/**
 * 循环依赖
 */
data class CircularDependency(
    val involvedModules: List<String>,
    val cycleLength: Int
)

/**
 * 数据访问边界分析
 */
data class DataAccessBoundaryAnalysis(
    val violations: List<DataAccessViolation>,
    val directDataAccess: List<DirectDataAccess>,
    val dataAccessPatterns: Map<String, Int>,
    val mostDataAccessingClasses: List<Pair<String, Int>>
)

/**
 * 数据访问违规
 */
data class DataAccessViolation(
    val violatorClass: String,
    val dataAccessClass: String,
    val violationType: DataAccessViolationType,
    val severity: Severity,
    val description: String,
    val recommendation: String
)

/**
 * 数据访问违规类型
 */
enum class DataAccessViolationType {
    BUSINESS_DIRECT_DATA_ACCESS,
    CONTROLLER_DIRECT_DATA_ACCESS,
    CROSS_LAYER_DATA_ACCESS,
    DIRECT_DATABASE_ACCESS
}

/**
 * 直接数据访问
 */
data class DirectDataAccess(
    val accessorClass: String,
    val dataClass: String,
    val accessType: String,
    val location: SourceLocation
)

/**
 * 边界建议
 */
data class BoundaryRecommendation(
    val type: String,
    val description: String,
    val priority: String,
    val actionItems: List<String>
)