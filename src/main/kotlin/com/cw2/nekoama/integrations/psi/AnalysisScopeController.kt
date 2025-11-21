package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * 分析范围控制器
 * 控制代码分析的深度、范围和排除规则
 */
class AnalysisScopeController(private val project: Project) {

    private val logger = NekoamaLogger

    /**
     * 创建默认分析配置
     */
    fun createDefaultConfig(): AnalysisConfig {
        return AnalysisConfig(
            maxDepth = 10,
            excludePackages = listOf(
                "java.",
                "javax.",
                "kotlin.",
                "org.springframework.",
                "org.springframework.boot.",
                "org.apache.",
                "com.google.",
                "lombok.",
                "org.junit.",
                "org.mockito.",
                "org.assertj.",
                "org.hamcrest."
            ),
            includeTestClasses = false,
            complexityThresholds = ComplexityThresholds(
                cyclomaticComplexity = 10,
                cognitiveComplexity = 15,
                nestingDepth = 3,
                methodLength = 50,
                classLength = 300,
                parameterCount = 5
            )
        )
    }

    /**
     * 创建快速分析配置
     */
    fun createQuickAnalysisConfig(rootPackage: String): AnalysisConfig {
        return AnalysisConfig(
            maxDepth = 5,
            excludePackages = listOf(
                "java.",
                "javax.",
                "kotlin.",
                "org.springframework.",
                "org.apache.",
                "com.google.",
                "lombok."
            ),
            includeTestClasses = false,
            complexityThresholds = ComplexityThresholds(
                cyclomaticComplexity = 15, // 更宽松的阈值
                cognitiveComplexity = 20,
                nestingDepth = 4,
                methodLength = 80,
                classLength = 500,
                parameterCount = 8
            )
        )
    }

    /**
     * 创建深度分析配置
     */
    fun createDeepAnalysisConfig(): AnalysisConfig {
        return AnalysisConfig(
            maxDepth = 20, // 更深的分析深度
            excludePackages = listOf(
                "java.lang.",
                "java.util.",
                "java.io.",
                "javax.sql.",
                "kotlin.",
                "kotlinx."
            ), // 更少的排除包
            includeTestClasses = true, // 包含测试类
            complexityThresholds = ComplexityThresholds(
                cyclomaticComplexity = 5, // 更严格的阈值
                cognitiveComplexity = 10,
                nestingDepth = 2,
                methodLength = 30,
                classLength = 200,
                parameterCount = 3
            )
        )
    }

    /**
     * 根据包结构创建配置
     */
    fun createConfigByPackageStructure(rootPackage: String): AnalysisConfig {
        val projectPackages = discoverProjectPackages()
        val businessPackages = projectPackages.filter { pkg ->
            pkg.startsWith(rootPackage) && !isFrameworkPackage(pkg)
        }

        return AnalysisConfig(
            maxDepth = 15,
            excludePackages = listOf(
                "java.",
                "javax.",
                "kotlin.",
                "org.springframework.",
                "org.apache.",
                "com.google.",
                "lombok."
            ),
            includeTestClasses = false,
            complexityThresholds = calculateThresholdsByProject(businessPackages)
        )
    }

    /**
     * 验证分析配置
     */
    fun validateConfig(config: AnalysisConfig): ValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 验证深度设置
        when {
            config.maxDepth <= 0 -> issues.add("分析深度必须大于0")
            config.maxDepth > 50 -> warnings.add("分析深度过大(${config.maxDepth})，可能导致性能问题")
        }

        // 验证排除包配置
        if (config.excludePackages.isEmpty()) {
            warnings.add("未配置排除包，可能包含大量框架代码")
        }

        // 验证复杂度阈值
        config.complexityThresholds.let { thresholds ->
            if (thresholds.cyclomaticComplexity <= 0) {
                issues.add("圈复杂度阈值必须大于0")
            }
            if (thresholds.methodLength <= 0) {
                issues.add("方法长度阈值必须大于0")
            }
            if (thresholds.classLength <= 0) {
                issues.add("类长度阈值必须大于0")
            }
        }

        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }

    /**
     * 过滤需要分析的类
     */
    fun filterClassesForAnalysis(
        classes: List<PsiClass>,
        config: AnalysisConfig
    ): List<PsiClass> {
        return classes.filter { psiClass ->
            shouldIncludeClass(psiClass, config)
        }
    }

    /**
     * 判断类是否应该包含在分析中
     */
    fun shouldIncludeClass(psiClass: PsiClass, config: AnalysisConfig): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        val packageName = psiClass.containingFile?.let { file ->
            (file as? PsiJavaFile)?.packageName ?: ""
        } ?: ""

        // 检查是否在排除包中
        if (isInExcludedPackage(packageName, config)) {
            return false
        }

        // 检查是否为测试类
        if (!config.includeTestClasses && isTestClass(psiClass)) {
            return false
        }

        // 检查是否为框架类
        if (isFrameworkClass(qualifiedName)) {
            return false
        }

        // 检查是否为生成类
        if (isGeneratedClass(psiClass)) {
            return false
        }

        return true
    }

    /**
     * 判断包是否应该包含在分析中
     */
    fun shouldIncludePackage(packageName: String, config: AnalysisConfig): Boolean {
        return !isInExcludedPackage(packageName, config)
    }

    /**
     * 创建搜索范围
     */
    fun createSearchScope(config: AnalysisConfig): GlobalSearchScope {
        return if (config.includeTestClasses) {
            GlobalSearchScope.projectScope(project)
        } else {
            GlobalSearchScope.getScopeRestrictedByFileTypes(
                GlobalSearchScope.projectScope(project),
                com.intellij.openapi.fileTypes.StdFileTypes.JAVA
            )
        }
    }

    /**
     * 估算分析复杂度
     */
    fun estimateAnalysisComplexity(config: AnalysisConfig, classCount: Int): ComplexityEstimate {
        val baseComplexity = classCount * 10 // 每个类的基础复杂度
        val depthMultiplier = config.maxDepth / 10.0 // 深度影响因子
        val thresholdMultiplier = calculateThresholdMultiplier(config.complexityThresholds)

        val estimatedComplexity = (baseComplexity * depthMultiplier * thresholdMultiplier).toInt()

        return ComplexityEstimate(
            estimatedClasses = classCount,
            estimatedComplexity = estimatedComplexity,
            estimatedTimeMinutes = estimateAnalysisTime(estimatedComplexity),
            memoryUsageMB = estimateMemoryUsage(classCount, config.maxDepth),
            recommendedAction = determineRecommendedAction(estimatedComplexity)
        )
    }

    /**
     * 发现项目包结构
     */
    fun discoverProjectPackages(): List<String> {
        return ReadAction.compute<List<String>, Throwable> {
            val packages = mutableSetOf<String>()
            val scope = GlobalSearchScope.projectScope(project)

            // 通过查找Java类来收集包名
            val javaPsiFacade = JavaPsiFacade.getInstance(project)
            val allClasses = javaPsiFacade.findClasses("*", scope)

            allClasses.forEach { psiClass ->
                val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
                if (packageName.isNotEmpty() && !isFrameworkPackage(packageName)) {
                    packages.add(packageName)
                }
            }

            packages.sorted()
        }
    }

    /**
     * 生成分析报告摘要
     */
    fun generateAnalysisSummary(config: AnalysisConfig, scope: AnalysisScope): AnalysisSummary {
        return AnalysisSummary(
            config = config,
            scope = scope,
            estimatedComplexity = estimateAnalysisComplexity(config, scope.classCount),
            recommendations = generateScopeRecommendations(config, scope)
        )
    }

    // 私有辅助方法

    private fun isInExcludedPackage(packageName: String, config: AnalysisConfig): Boolean {
        return config.excludePackages.any { exclude ->
            packageName.startsWith(exclude)
        }
    }

    private fun isTestClass(psiClass: PsiClass): Boolean {
        val className = psiClass.name ?: return false
        val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""

        return packageName.contains("test") ||
                packageName.contains("tests") ||
                className.endsWith("Test") ||
                className.endsWith("Tests") ||
                className.startsWith("Test") ||
                psiClass.annotations.any {
                    it.qualifiedName?.contains("Test") == true
                }
    }

    private fun isFrameworkClass(qualifiedName: String): Boolean {
        return qualifiedName.startsWith("java.") ||
                qualifiedName.startsWith("javax.") ||
                qualifiedName.startsWith("kotlin.") ||
                qualifiedName.startsWith("org.springframework.") ||
                qualifiedName.startsWith("org.apache.") ||
                qualifiedName.startsWith("com.google.") ||
                qualifiedName.startsWith("lombok.")
    }

    private fun isGeneratedClass(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "javax.annotation.Generated" ||
                    qualifiedName == "javax.annotation.processing.Generated" ||
                    qualifiedName?.contains("lombok") == true ||
                    qualifiedName?.contains("Generated") == true
        }
    }

    private fun isFrameworkPackage(packageName: String): Boolean {
        return packageName.startsWith("java.") ||
                packageName.startsWith("javax.") ||
                packageName.startsWith("kotlin.") ||
                packageName.startsWith("org.springframework.") ||
                packageName.startsWith("org.apache.") ||
                packageName.startsWith("com.google.")
    }

    private fun calculateThresholdsByProject(packages: List<String>): ComplexityThresholds {
        // 根据项目规模动态调整阈值
        val packageCount = packages.size

        return when {
            packageCount < 10 -> ComplexityThresholds( // 小型项目
                cyclomaticComplexity = 8,
                cognitiveComplexity = 12,
                nestingDepth = 3,
                methodLength = 40,
                classLength = 250,
                parameterCount = 4
            )
            packageCount < 50 -> ComplexityThresholds( // 中型项目
                cyclomaticComplexity = 10,
                cognitiveComplexity = 15,
                nestingDepth = 4,
                methodLength = 50,
                classLength = 300,
                parameterCount = 5
            )
            else -> ComplexityThresholds( // 大型项目
                cyclomaticComplexity = 12,
                cognitiveComplexity = 18,
                nestingDepth = 5,
                methodLength = 60,
                classLength = 400,
                parameterCount = 6
            )
        }
    }

    private fun calculateThresholdMultiplier(thresholds: ComplexityThresholds): Double {
        val defaultThresholds = ComplexityThresholds(
            cyclomaticComplexity = 10,
            cognitiveComplexity = 15,
            nestingDepth = 3,
            methodLength = 50,
            classLength = 300,
            parameterCount = 5
        )

        val cyclomaticRatio = thresholds.cyclomaticComplexity.toDouble() / defaultThresholds.cyclomaticComplexity
        val methodLengthRatio = thresholds.methodLength.toDouble() / defaultThresholds.methodLength

        return (cyclomaticRatio + methodLengthRatio) / 2.0
    }

    private fun estimateAnalysisTime(complexity: Int): Int {
        // 基于复杂度估算分析时间（分钟）
        return when {
            complexity < 1000 -> 1
            complexity < 5000 -> 5
            complexity < 20000 -> 15
            complexity < 100000 -> 30
            else -> 60
        }
    }

    private fun estimateMemoryUsage(classCount: Int, maxDepth: Int): Int {
        // 估算内存使用量（MB）
        val baseMemory = classCount * 2 // 每个类约2MB基础内存
        val depthMemory = maxDepth * 10 // 深度影响内存
        val analysisMemory = classCount * maxDepth * 0.5 // 分析过程内存

        return (baseMemory + depthMemory + analysisMemory).toInt().coerceAtLeast(100)
    }

    private fun determineRecommendedAction(complexity: Int): String {
        return when {
            complexity < 1000 -> "可以立即执行完整分析"
            complexity < 5000 -> "建议在空闲时间执行分析"
            complexity < 20000 -> "建议分批执行或缩小分析范围"
            else -> "强烈建议缩小分析范围或使用快速分析模式"
        }
    }

    private fun generateScopeRecommendations(
        config: AnalysisConfig,
        scope: AnalysisScope
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (scope.classCount > 1000) {
            recommendations.add("项目规模较大，建议缩小分析范围到核心业务包")
        }

        if (config.maxDepth > 15) {
            recommendations.add("分析深度较深，可能影响性能，建议控制在15层以内")
        }

        if (!config.includeTestClasses) {
            recommendations.add("当前排除测试类，如需分析测试代码质量，请包含测试类")
        }

        val excludedCount = config.excludePackages.size
        if (excludedCount < 5) {
            recommendations.add("排除包较少，建议添加更多框架包到排除列表")
        }

        return recommendations
    }

    /**
     * 分析范围信息
     */
    data class AnalysisScope(
        val classCount: Int,
        val packageCount: Int,
        val estimatedDependencies: Int,
        val rootPackages: List<String>
    )

    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val warnings: List<String>
    )

    /**
     * 复杂度估算
     */
    data class ComplexityEstimate(
        val estimatedClasses: Int,
        val estimatedComplexity: Int,
        val estimatedTimeMinutes: Int,
        val memoryUsageMB: Int,
        val recommendedAction: String
    )

    /**
     * 分析摘要
     */
    data class AnalysisSummary(
        val config: AnalysisConfig,
        val scope: AnalysisScope,
        val estimatedComplexity: ComplexityEstimate,
        val recommendations: List<String>
    )

    /**
     * 分析配置构建器
     */
    class ConfigBuilder {
        private var maxDepth: Int = 10
        private val excludePackages = mutableListOf<String>()
        private var includeTestClasses = false
        private var complexityThresholds = ComplexityThresholds(
            cyclomaticComplexity = 10,
            cognitiveComplexity = 15,
            nestingDepth = 3,
            methodLength = 50,
            classLength = 300,
            parameterCount = 5
        )

        fun maxDepth(depth: Int) = apply { this.maxDepth = depth }
        fun excludePackage(pkg: String) = apply { this.excludePackages.add(pkg) }
        fun excludePackages(packages: List<String>) = apply { this.excludePackages.addAll(packages) }
        fun includeTestClasses(include: Boolean) = apply { this.includeTestClasses = include }
        fun complexityThresholds(thresholds: ComplexityThresholds) = apply { this.complexityThresholds = thresholds }

        fun build(): AnalysisConfig {
            return AnalysisConfig(
                maxDepth = maxDepth,
                excludePackages = excludePackages.toList(),
                includeTestClasses = includeTestClasses,
                complexityThresholds = complexityThresholds
            )
        }
    }

    companion object {
        /**
         * 创建配置构建器
         */
        fun builder(): ConfigBuilder = ConfigBuilder()

        /**
         * 预定义的分析模式
         */
        enum class AnalysisMode {
            QUICK,      // 快速分析
            STANDARD,   // 标准分析
            DEEP,       // 深度分析
            CUSTOM      // 自定义
        }
    }
}