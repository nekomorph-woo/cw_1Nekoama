package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.psi.*

/**
 * 代码坏味道检测器
 * 基于业界最佳实践和设计原则检测各种代码问题
 */
class CodeSmellDetector {

    private val logger = NekoamaLogger
    private val complexityCalculator = ComplexityCalculator()

    /**
     * 检测所有代码坏味道
     */
    fun detectCodeSmells(
        complexityMetrics: Map<String, ClassComplexityMetrics>,
        config: AnalysisConfig
    ): List<CodeSmell> {
        // 兼容性方法，调用新的精确分析方法
        return detectCodeSmells(complexityMetrics, emptyMap(), config)
    }

    /**
     * 精确检测所有代码坏味道（包含PSI分析）
     */
    fun detectCodeSmells(
        complexityMetrics: Map<String, ClassComplexityMetrics>,
        psiClasses: Map<String, PsiClass>,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        try {
            complexityMetrics.forEach { (className, metrics) ->
                val psiClass = psiClasses[className]

                // 检测长方法
                codeSmells.addAll(detectLongMethods(className, metrics, config))

                // 检测长参数列表
                codeSmells.addAll(detectLongParameterLists(className, metrics, config))

                // 检测大类
                codeSmells.addAll(detectLargeClasses(className, metrics, config))

                // 检测深度嵌套
                codeSmells.addAll(detectDeepNesting(className, metrics, config))

                // 检测上帝类
                codeSmells.addAll(detectGodClasses(className, metrics, config))

                // 检测数据类
                codeSmells.addAll(detectDataClasses(className, metrics, config))

                // 检测意大利面条代码
                codeSmells.addAll(detectSpaghettiCode(className, metrics, config))

                // 检测特性嫉妒
                codeSmells.addAll(detectFeatureEnvy(className, metrics))

                // 检测高耦合
                codeSmells.addAll(detectHighCoupling(className, metrics))

                // 检测魔法数字密度 - 使用精确PSI分析
                codeSmells.addAll(detectMagicNumberDensity(className, metrics, psiClass, config))

                // 检测长表达式 - 使用精确PSI分析
                codeSmells.addAll(detectLongExpressions(className, metrics, psiClass, config))

                // 检测布尔参数过多 - 使用精确PSI分析
                codeSmells.addAll(detectBooleanParameterIssues(className, metrics, psiClass, config))

                // 检测多个return语句 - 使用精确PSI分析
                codeSmells.addAll(detectMultipleReturns(className, metrics, psiClass, config))

                // 检测长方法+高复杂度组合
                codeSmells.addAll(detectLongAndComplexMethods(className, metrics, config))

                // 检测深度嵌套+多分支组合
                codeSmells.addAll(detectDeeplyNestedBranching(className, metrics, config))
            }

            logger.info("CodeSmellDetector", "检测到 ${codeSmells.size} 个代码坏味道")

        } catch (e: Exception) {
            logger.error("CodeSmellDetector", "代码坏味道检测失败", error = e)
        }

        return codeSmells.sortedByDescending { it.severity.ordinal }
    }

    /**
     * 检测长方法
     * 标准：方法行数超过阈值
     */
    private fun detectLongMethods(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        if (metrics.longestMethod.lineOfCode > config.complexityThresholds.methodLength) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.LONG_METHOD,
                    severity = determineSeverity(metrics.longestMethod.lineOfCode, config.complexityThresholds.methodLength),
                    className = className,
                    methodName = metrics.longestMethod.methodName,
                    description = "方法过长: ${metrics.longestMethod.lineOfCode} 行 (阈值: ${config.complexityThresholds.methodLength})，建议拆分为多个小方法",
                    location = SourceLocation("", 0, 0), // 需要更精确的位置信息
                    mapOf<String, Int>(
                        "lines" to metrics.longestMethod.lineOfCode,
                        "threshold" to config.complexityThresholds.methodLength,
                        "complexity" to metrics.longestMethod.complexity
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测长参数列表
     * 标准：参数数量超过阈值
     */
    private fun detectLongParameterLists(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        if (metrics.mostComplexMethod.parameterCount > config.complexityThresholds.parameterCount) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.LONG_PARAMETER_LIST,
                    severity = determineSeverity(metrics.mostComplexMethod.parameterCount, config.complexityThresholds.parameterCount),
                    className = className,
                    methodName = metrics.mostComplexMethod.methodName,
                    description = "参数过多: ${metrics.mostComplexMethod.parameterCount} 个参数 (阈值: ${config.complexityThresholds.parameterCount})，建议使用参数对象",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "parameters" to metrics.mostComplexMethod.parameterCount,
                        "threshold" to config.complexityThresholds.parameterCount
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测大类
     * 标准：类代码行数或方法数超过阈值
     */
    private fun detectLargeClasses(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        if (metrics.lineOfCode > config.complexityThresholds.classLength) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.LARGE_CLASS,
                    severity = determineSeverity(metrics.lineOfCode, config.complexityThresholds.classLength),
                    className = className,
                    methodName = null,
                    description = "类过大: ${metrics.lineOfCode} 行代码 (阈值: ${config.complexityThresholds.classLength})，建议拆分为多个职责单一的类",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "lines" to metrics.lineOfCode,
                        "threshold" to config.complexityThresholds.classLength,
                        "methods" to metrics.methodCount,
                        "fields" to metrics.fieldCount
                    )
                )
            )
        }

        // 同时检测方法数过多的类
        if (metrics.methodCount > 20) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.LARGE_CLASS,
                    severity = Severity.HIGH,
                    className = className,
                    methodName = null,
                    description = "类方法过多: ${metrics.methodCount} 个方法，建议根据功能拆分类",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "methods" to metrics.methodCount,
                        "fields" to metrics.fieldCount
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测深度嵌套
     * 标准：嵌套层级超过阈值
     */
    private fun detectDeepNesting(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        if (metrics.nestingDepth > config.complexityThresholds.nestingDepth) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.DEEP_NESTING,
                    severity = determineSeverity(metrics.nestingDepth, config.complexityThresholds.nestingDepth),
                    className = className,
                    methodName = metrics.mostComplexMethod.methodName,
                    description = "嵌套过深: ${metrics.nestingDepth} 层 (阈值: ${config.complexityThresholds.nestingDepth})，建议使用早期返回或提取方法",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "depth" to metrics.nestingDepth,
                        "threshold" to config.complexityThresholds.nestingDepth,
                        "cognitiveComplexity" to metrics.cognitiveComplexity
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测上帝类
     * 标准：类过大、方法过多、职责不单一
     */
    private fun detectGodClasses(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        // 上帝类的特征：类过大 + 方法过多 + 高耦合
        val isTooLarge = metrics.lineOfCode > config.complexityThresholds.classLength * 2
        val hasTooManyMethods = metrics.methodCount > 30
        val hasHighCoupling = metrics.couplingMetrics.efferentCoupling > 20
        val hasLowCohesion = metrics.couplingMetrics.distance > 0.7

        if ((isTooLarge && hasTooManyMethods) || (hasTooManyMethods && hasHighCoupling)) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.GOD_CLASS,
                    severity = Severity.CRITICAL,
                    className = className,
                    methodName = null,
                    description = "上帝类: 类承担了过多职责，${metrics.methodCount} 个方法，${metrics.lineOfCode} 行代码，建议拆分为多个小类",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "methods" to metrics.methodCount,
                        "lines" to metrics.lineOfCode,
                        "efferentCoupling" to metrics.couplingMetrics.efferentCoupling,
                        "distance" to (metrics.couplingMetrics.distance * 100).toInt()
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测数据类
     * 标准：主要包含getter/setter，缺乏业务逻辑
     */
    private fun detectDataClasses(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        // 数据类的特征：主要是getter/setter方法，缺乏业务逻辑方法
        val hasManyFields = metrics.fieldCount > 5
        val hasManyAccessors = metrics.methodCount > metrics.fieldCount * 2
        val hasLowComplexity = metrics.cyclomaticComplexity < metrics.methodCount
        val isNotUtility = !className.lowercase().contains("util") && !className.lowercase().contains("helper")

        if (hasManyFields && hasManyAccessors && hasLowComplexity && isNotUtility) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.DATA_CLASS,
                    severity = Severity.MEDIUM,
                    className = className,
                    methodName = null,
                    description = "数据类: 缺乏业务逻辑，主要是getter/setter方法，建议增加行为或将数据与行为分离",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "fields" to metrics.fieldCount,
                        "methods" to metrics.methodCount,
                        "complexity" to metrics.cyclomaticComplexity
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测意大利面条代码
     * 标准：高圈复杂度 + 高认知复杂度
     */
    private fun detectSpaghettiCode(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        val hasHighCyclomaticComplexity = metrics.cyclomaticComplexity > config.complexityThresholds.cyclomaticComplexity * 2
        val hasHighCognitiveComplexity = metrics.cognitiveComplexity > config.complexityThresholds.cognitiveComplexity * 2

        if (hasHighCyclomaticComplexity && hasHighCognitiveComplexity) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.SPAGHETTI_CODE,
                    severity = Severity.CRITICAL,
                    className = className,
                    methodName = metrics.mostComplexMethod.methodName,
                    description = "意大利面条代码: 圈复杂度 ${metrics.cyclomaticComplexity}，认知复杂度 ${metrics.cognitiveComplexity}，控制流混乱，需要重构",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "cyclomaticComplexity" to metrics.cyclomaticComplexity,
                        "cognitiveComplexity" to metrics.cognitiveComplexity,
                        "threshold" to config.complexityThresholds.cyclomaticComplexity
                    )
                )
            )
        } else if (hasHighCyclomaticComplexity) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.SPAGHETTI_CODE,
                    severity = Severity.HIGH,
                    className = className,
                    methodName = metrics.mostComplexMethod.methodName,
                    description = "高圈复杂度: ${metrics.cyclomaticComplexity} (阈值: ${config.complexityThresholds.cyclomaticComplexity})，建议简化控制流",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "cyclomaticComplexity" to metrics.cyclomaticComplexity,
                        "threshold" to config.complexityThresholds.cyclomaticComplexity
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测特性嫉妒
     * 标准：类大量使用其他类的数据和方法
     */
    private fun detectFeatureEnvy(
        className: String,
        metrics: ClassComplexityMetrics
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        // 特性嫉妒的特征：高传出耦合 + 低传入耦合
        val hasHighEfferentCoupling = metrics.couplingMetrics.efferentCoupling > 15
        val hasLowAfferentCoupling = metrics.couplingMetrics.afferentCoupling < 5
        val hasHighInstability = metrics.couplingMetrics.instability > 0.8

        if (hasHighEfferentCoupling && hasLowAfferentCoupling && hasHighInstability) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.FEATURE_ENVY,
                    severity = Severity.MEDIUM,
                    className = className,
                    methodName = null,
                    description = "特性嫉妒: 过度依赖其他类的方法和数据，建议将相关功能移到被依赖的类中",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "efferentCoupling" to metrics.couplingMetrics.efferentCoupling,
                        "afferentCoupling" to metrics.couplingMetrics.afferentCoupling,
                        "instability" to (metrics.couplingMetrics.instability * 100).toInt()
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测高耦合
     * 标准：传出耦合或传入耦合过高
     */
    private fun detectHighCoupling(
        className: String,
        metrics: ClassComplexityMetrics
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        // 传出耦合过高
        if (metrics.couplingMetrics.efferentCoupling > 20) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.INAPPROPRIATE_INTIMACY,
                    severity = Severity.HIGH,
                    className = className,
                    methodName = null,
                    description = "高传出耦合: 依赖 ${metrics.couplingMetrics.efferentCoupling} 个其他类，建议减少依赖或引入抽象层",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "efferentCoupling" to metrics.couplingMetrics.efferentCoupling,
                        "instability" to (metrics.couplingMetrics.instability * 100).toInt()
                    )
                )
            )
        }

        // 传入耦合过高
        if (metrics.couplingMetrics.afferentCoupling > 30) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.INAPPROPRIATE_INTIMACY,
                    severity = Severity.MEDIUM,
                    className = className,
                    methodName = null,
                    description = "高传入耦合: 被 ${metrics.couplingMetrics.afferentCoupling} 个其他类依赖，修改风险较高",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "afferentCoupling" to metrics.couplingMetrics.afferentCoupling,
                        "instability" to (metrics.couplingMetrics.instability * 100).toInt()
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 根据超出阈值的程度确定严重性
     */
    private fun determineSeverity(actualValue: Int, threshold: Int): Severity {
        val ratio = actualValue.toDouble() / threshold
        return when {
            ratio >= 3.0 -> Severity.CRITICAL
            ratio >= 2.0 -> Severity.HIGH
            ratio >= 1.5 -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    /**
     * 生成代码坏味道统计报告
     */
    fun generateCodeSmellReport(codeSmells: List<CodeSmell>): CodeSmellReport {
        val byType = codeSmells.groupBy { it.type }
        val bySeverity = codeSmells.groupBy { it.severity }
        val byPackage = codeSmells.groupBy {
            val packageName = it.className.substringBeforeLast(".", "")
            packageName.ifEmpty { "default" }
        }

        return CodeSmellReport(
            totalCodeSmells = codeSmells.size,
            codeSmellsByType = byType.mapValues { it.value.size },
            codeSmellsBySeverity = bySeverity.mapValues { it.value.size },
            codeSmellsByPackage = byPackage.mapValues { it.value.size },
            criticalIssues = codeSmells.count { it.severity == Severity.CRITICAL },
            highPriorityIssues = codeSmells.count { it.severity == Severity.HIGH },
            mostProblematicClasses = codeSmells.groupBy { it.className }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(10)
        )
    }

    /**
     * 生成重构建议
     */
    fun generateRefactoringSuggestions(codeSmells: List<CodeSmell>): List<RefactoringSuggestion> {
        val suggestions = mutableListOf<RefactoringSuggestion>()

        codeSmells.forEach { smell ->
            val suggestion = when (smell.type) {
                CodeSmellType.LONG_METHOD -> RefactoringSuggestion(
                    targetElement = "${smell.className}.${smell.methodName}",
                    smellType = smell.type,
                    refactoringTechnique = "Extract Method",
                    description = "将长方法拆分为多个小的、职责单一的方法",
                    priority = smell.severity,
                    effort = "Low",
                    benefit = "提高代码可读性和可维护性"
                )

                CodeSmellType.LONG_PARAMETER_LIST -> RefactoringSuggestion(
                    targetElement = "${smell.className}.${smell.methodName}",
                    smellType = smell.type,
                    refactoringTechnique = "Introduce Parameter Object",
                    description = "使用参数对象替换长参数列表",
                    priority = smell.severity,
                    effort = "Medium",
                    benefit = "减少参数传递复杂度，提高扩展性"
                )

                CodeSmellType.LARGE_CLASS -> RefactoringSuggestion(
                    targetElement = smell.className,
                    smellType = smell.type,
                    refactoringTechnique = "Extract Class",
                    description = "将大类拆分为多个职责单一的小类",
                    priority = smell.severity,
                    effort = "High",
                    benefit = "提高内聚性，降低耦合度"
                )

                CodeSmellType.DEEP_NESTING -> RefactoringSuggestion(
                    targetElement = "${smell.className}.${smell.methodName}",
                    smellType = smell.type,
                    refactoringTechnique = "Replace Nested Conditional with Guard Clauses",
                    description = "使用保护语句替换嵌套条件",
                    priority = smell.severity,
                    effort = "Medium",
                    benefit = "降低认知复杂度，提高可读性"
                )

                CodeSmellType.GOD_CLASS -> RefactoringSuggestion(
                    targetElement = smell.className,
                    smellType = smell.type,
                    refactoringTechnique = "Extract Class / Decompose Conditional",
                    description = "将上帝类拆分为多个协作的小类",
                    priority = Severity.CRITICAL,
                    effort = "Very High",
                    benefit = "显著提高代码质量和可维护性"
                )

                CodeSmellType.DATA_CLASS -> RefactoringSuggestion(
                    targetElement = smell.className,
                    smellType = smell.type,
                    refactoringTechnique = "Move Method / Extract Class",
                    description = "为数据类添加业务行为或将数据与行为分离",
                    priority = smell.severity,
                    effort = "Medium",
                    benefit = "遵循面向对象设计原则"
                )

                CodeSmellType.SPAGHETTI_CODE -> RefactoringSuggestion(
                    targetElement = "${smell.className}.${smell.methodName}",
                    smellType = smell.type,
                    refactoringTechnique = "Replace Conditional with Polymorphism / Extract Method",
                    description = "使用多态或方法提取简化复杂的控制流",
                    priority = smell.severity,
                    effort = "High",
                    benefit = "大幅降低复杂度，提高代码质量"
                )

                CodeSmellType.FEATURE_ENVY -> RefactoringSuggestion(
                    targetElement = smell.className,
                    smellType = smell.type,
                    refactoringTechnique = "Move Method",
                    description = "将过度使用其他类数据的方法移到被使用的类中",
                    priority = smell.severity,
                    effort = "Medium",
                    benefit = "提高内聚性，减少不必要的耦合"
                )

                CodeSmellType.INAPPROPRIATE_INTIMACY -> RefactoringSuggestion(
                    targetElement = smell.className,
                    smellType = smell.type,
                    refactoringTechnique = "Extract Class / Introduce Delegation",
                    description = "提取公共类或使用委托模式减少直接耦合",
                    priority = smell.severity,
                    effort = "High",
                    benefit = "降低耦合度，提高模块独立性"
                )

                else -> null
            }

            suggestion?.let { suggestions.add(it) }
        }

        return suggestions.distinctBy { "${it.targetElement}-${it.refactoringTechnique}" }
    }

    /**
     * 代码坏味道报告
     */
    data class CodeSmellReport(
        val totalCodeSmells: Int,
        val codeSmellsByType: Map<CodeSmellType, Int>,
        val codeSmellsBySeverity: Map<Severity, Int>,
        val codeSmellsByPackage: Map<String, Int>,
        val criticalIssues: Int,
        val highPriorityIssues: Int,
        val mostProblematicClasses: List<Pair<String, Int>>
    )

    /**
     * 精确检测魔法数字密度
     * 标准：方法中硬编码数字/字符串数量超过阈值
     */
    private fun detectMagicNumberDensity(
        className: String,
        metrics: ClassComplexityMetrics,
        psiClass: PsiClass?,
        config: AnalysisConfig
    ): List<CodeSmell> {
        if (psiClass == null)
            return emptyList()

        val codeSmells = mutableListOf<CodeSmell>()

        var totalMagicNumbers = 0
        var worstMethodInfo: Pair<String, Int> = "" to 0

        // 精确统计每个方法的魔法数字
        for (method in psiClass.methods) {
            val magicCount = complexityCalculator.countMethodMagicNumbers(method)
            totalMagicNumbers += magicCount

            if (magicCount > worstMethodInfo.second) {
                worstMethodInfo = method.name to magicCount
            }
        }

        val threshold = 5 // 文档建议的阈值

        if (totalMagicNumbers > threshold) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.MAGIC_NUMBERS,
                    severity = determineSeverity(totalMagicNumbers, threshold),
                    className = className,
                    methodName = worstMethodInfo.first,
                    description = "魔法数字过多: 精确检测到 ${totalMagicNumbers} 个硬编码数字/字符串 (阈值: ${threshold})，建议提取为常量",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "magicNumbers" to totalMagicNumbers,
                        "threshold" to threshold,
                        "worstMethod" to worstMethodInfo.second
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 精确检测长表达式
     * 标准：单行代码字符数超过120字符
     */
    private fun detectLongExpressions(
        className: String,
        metrics: ClassComplexityMetrics,
        psiClass: PsiClass?,
        config: AnalysisConfig
    ): List<CodeSmell> {
        if (psiClass == null)
            return emptyList()

        val codeSmells = mutableListOf<CodeSmell>()

        var totalLongLines = 0
        var worstMethodInfo: Pair<String, Int> = "" to 0

        // 精确统计每个方法的长行
        for (method in psiClass.methods) {
            val longLineCount = complexityCalculator.countMethodLongLines(method)
            totalLongLines += longLineCount

            if (longLineCount > worstMethodInfo.second) {
                worstMethodInfo = method.name to longLineCount
            }
        }

        val threshold = 1 // 有长行就算问题

        if (totalLongLines > threshold) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.LONG_EXPRESSIONS,
                    severity = Severity.MEDIUM,
                    className = className,
                    methodName = worstMethodInfo.first,
                    description = "长表达式: 精确检测到 ${totalLongLines} 行超过120字符的代码，建议拆分为多行",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "longLines" to totalLongLines,
                        "threshold" to 120,
                        "worstMethod" to worstMethodInfo.second
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 精确检测布尔参数过多
     * 标准：boolean类型参数数量超过2个
     */
    private fun detectBooleanParameterIssues(
        className: String,
        metrics: ClassComplexityMetrics,
        psiClass: PsiClass?,
        config: AnalysisConfig
    ): List<CodeSmell> {
        if (psiClass == null)
            return emptyList()

        val codeSmells = mutableListOf<CodeSmell>()

        var totalBooleanParams = 0
        var worstMethodInfo: Pair<String, Int> = "" to 0

        // 精确统计每个方法的布尔参数
        for (method in psiClass.methods) {
            val booleanParamCount = complexityCalculator.countMethodBooleanParameters(method)
            totalBooleanParams += booleanParamCount

            if (booleanParamCount > worstMethodInfo.second) {
                worstMethodInfo = method.name to booleanParamCount
            }
        }

        val threshold = 2 // 文档建议的阈值

        if (totalBooleanParams > threshold) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.BOOLEAN_PARAMETER_SMELL,
                    severity = determineSeverity(totalBooleanParams, threshold),
                    className = className,
                    methodName = worstMethodInfo.first,
                    description = "布尔参数过多: 精确检测到 ${totalBooleanParams} 个boolean参数 (阈值: ${threshold})，建议使用枚举或参数对象",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "booleanParameters" to totalBooleanParams,
                        "threshold" to threshold,
                        "worstMethod" to worstMethodInfo.second
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 精确检测多个return语句
     * 标准：方法中return语句数量超过5个
     */
    private fun detectMultipleReturns(
        className: String,
        metrics: ClassComplexityMetrics,
        psiClass: PsiClass?,
        config: AnalysisConfig
    ): List<CodeSmell> {
        if (psiClass == null)
            return emptyList()

        val codeSmells = mutableListOf<CodeSmell>()

        var totalReturnStatements = 0
        var worstMethodInfo: Pair<String, Int> = "" to 0

        // 精确统计每个方法的return语句
        for (method in psiClass.methods) {
            val returnCount = complexityCalculator.countMethodReturnStatements(method)
            totalReturnStatements += returnCount

            if (returnCount > worstMethodInfo.second) {
                worstMethodInfo = method.name to returnCount
            }
        }

        val threshold = 5 // 文档建议的阈值

        if (totalReturnStatements > threshold) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.MULTIPLE_RETURNS,
                    severity = Severity.LOW,
                    className = className,
                    methodName = worstMethodInfo.first,
                    description = "多个return语句: 精确检测到 ${totalReturnStatements} 个return语句 (阈值: ${threshold})，建议考虑合并为单一出口",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "returnStatements" to totalReturnStatements,
                        "threshold" to threshold,
                        "worstMethod" to worstMethodInfo.second
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测长方法+高复杂度组合
     * 标准：LOC > 50 && CyclomaticComplexity > 10
     */
    private fun detectLongAndComplexMethods(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        val isLong = metrics.longestMethod.lineOfCode > 50
        val isComplex = metrics.longestMethod.complexity > 10

        if (isLong && isComplex) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.SPAGHETTI_CODE, // 复用现有类型
                    severity = Severity.CRITICAL,
                    className = className,
                    methodName = metrics.longestMethod.methodName,
                    description = "长方法+高复杂度组合: 方法长度 ${metrics.longestMethod.lineOfCode} 行，圈复杂度 ${metrics.longestMethod.complexity}，强烈建议重构",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "lines" to metrics.longestMethod.lineOfCode,
                        "complexity" to metrics.longestMethod.complexity,
                        "lengthThreshold" to 50,
                        "complexityThreshold" to 10
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 检测深度嵌套+多分支组合
     * 标准：NestingDepth > 4 && CyclomaticComplexity > 15
     */
    private fun detectDeeplyNestedBranching(
        className: String,
        metrics: ClassComplexityMetrics,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        val isDeeplyNested = metrics.nestingDepth > 4
        val hasHighComplexity = metrics.cyclomaticComplexity > 15

        if (isDeeplyNested && hasHighComplexity) {
            codeSmells.add(
                CodeSmell(
                    type = CodeSmellType.DEEP_NESTING, // 复用现有类型
                    severity = Severity.CRITICAL,
                    className = className,
                    methodName = metrics.mostComplexMethod.methodName,
                    description = "深度嵌套+多分支组合: 嵌套深度 ${metrics.nestingDepth} 层，圈复杂度 ${metrics.cyclomaticComplexity}，控制流极其复杂",
                    location = SourceLocation("", 0, 0),
                    mapOf<String, Int>(
                        "depth" to metrics.nestingDepth,
                        "complexity" to metrics.cyclomaticComplexity,
                        "depthThreshold" to 4,
                        "complexityThreshold" to 15
                    )
                )
            )
        }

        return codeSmells
    }

    /**
     * 重构建议
     */
    data class RefactoringSuggestion(
        val targetElement: String,
        val smellType: CodeSmellType,
        val refactoringTechnique: String,
        val description: String,
        val priority: Severity,
        val effort: String,
        val benefit: String
    )
}