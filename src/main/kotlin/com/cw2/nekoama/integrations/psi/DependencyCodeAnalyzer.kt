package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.name.FqName
import java.util.concurrent.ConcurrentHashMap

/**
 * 核心依赖代码分析器
 * 基于PSI技术分析Java/Kotlin代码的完整依赖关系
 */
class DependencyCodeAnalyzer(private val project: Project) {

    private val logger = NekoamaLogger

    private val javaPsiFacade = JavaPsiFacade.getInstance(project)
    private val psiManager = PsiManager.getInstance(project)

    // 缓存分析结果，避免重复计算
    private val classCache = ConcurrentHashMap<String, ClassDependency>()
    private val methodCallCache = ConcurrentHashMap<String, MutableList<MethodCall>>()
    private val complexityCache = ConcurrentHashMap<String, ClassComplexityMetrics>()

    /**
     * 执行完整的依赖分析
     */
    fun analyzeDependencies(
        config: AnalysisConfig,
        progressIndicator: ProgressIndicator? = null
    ): DependencyAnalysisResult {
        logger.info("DependencyCodeAnalyzer", "开始代码依赖分析，配置: $config")

        val startTime = System.currentTimeMillis()

        try {
            // 1. 收集所有Java/Kotlin文件
            val psiFiles = collectJavaKotlinFiles(config)
            progressIndicator?.text = "已找到 ${psiFiles.size} 个源文件"

            // 2. 提取所有类信息
            val classes = extractClasses(psiFiles, config)
            progressIndicator?.text = "已提取 ${classes.size} 个类信息"

            // 3. 分析包级依赖
            val packageDependencies = analyzePackageDependencies(classes)
            progressIndicator?.text = "已完成包级依赖分析"

            // 4. 分析类级依赖
            val classDependencies = analyzeClassDependencies(classes, config)
            progressIndicator?.text = "已完成类级依赖分析"

            // 5. 分析方法调用关系
            val methodCalls = analyzeMethodCalls(psiFiles, config)
            progressIndicator?.text = "已完成方法调用分析"

            // 6. 识别业务入口点
            val businessEntryPoints = identifyBusinessEntryPoints(classes)
            progressIndicator?.text = "已完成业务入口点识别"

            // 7. 计算复杂度指标
            val complexityMetrics = calculateComplexityMetrics(classes, config)
            progressIndicator?.text = "已完成复杂度指标计算"

            // 8. 检测代码坏味道
            val codeSmells = detectCodeSmells(complexityMetrics, config)
            progressIndicator?.text = "已完成代码坏味道检测"

            // 9. 构建项目信息
            val projectInfo = buildProjectInfo(classes, config)

            logger.info("DependencyCodeAnalyzer", "代码依赖分析完成，耗时: ${System.currentTimeMillis() - startTime}ms")

            // 构建真实的分析元数据
            val realMetadata = AnalysisMetadata(
                projectName = projectInfo.name,
                moduleName = project.name,
                analysisTime = java.time.LocalDateTime.now().toString(),
                scope = AnalysisScope(
                    rootPackage = config.excludePackages.firstOrNull() ?: "",
                    includedPackages = config.excludePackages, // 这里可能需要调整
                    excludedPackages = config.excludePackages,
                    maxDepth = config.maxDepth
                ),
                statistics = AnalysisStatistics(
                    totalPackages = packageDependencies.size,
                    totalClasses = classes.size,
                    totalMethods = classes.sumOf { it.methods.size },
                    totalCallEdges = methodCalls.size
                )
            )

            // 构建真实的调用图
            val realCallGraph = CallGraph(
                edges = methodCalls.map { methodCall ->
                    CallEdge(
                        id = "edge-${methodCall.hashCode()}",
                        source = "${methodCall.callerClass}#${methodCall.callerMethod}",
                        target = "${methodCall.calleeClass}#${methodCall.calleeMethod}",
                        type = CallEdgeType.METHOD_CALL,
                        callContext = CallContext(
                            callCount = 1, // 这里需要从分析中获取实际调用次数
                            callLocations = listOf(
                                CallLocation(
                                    line = methodCall.location.lineNumber,
                                    column = methodCall.location.columnNumber,
                                    context = "normal"
                                )
                            )
                        ),
                        depth = methodCall.callDepth,
                        weight = 1
                    )
                }
            )

            // 构建包信息
            val packageInfos = DependencyDataBuilders.buildPackageInfos(classes, packageDependencies)

            // 构建类信息
            val classInfos = DependencyDataBuilders.buildClassInfos(classes, classDependencies)

            // 构建方法信息 - 传递业务入口点信息用于正确设置isEntryPoint标志
            val methodInfos = DependencyDataBuilders.buildMethodInfos(classes, methodCalls, complexityMetrics, businessEntryPoints)

            // 构建字段信息
            val fieldInfos = DependencyDataBuilders.buildFieldInfos(classes)

            // 构建场景定义
            val sceneDefList = DependencyDataBuilders.buildSceneDefinitions(businessEntryPoints, methodCalls, complexityMetrics)

            return DependencyAnalysisResult(
                metadata = realMetadata,
                packages = packageInfos,
                classes = classInfos,
                methods = methodInfos,
                fields = fieldInfos,
                pojos = emptyList(), // 这里需要POJO分析器的支持，暂时为空
                callGraph = realCallGraph,
                sceneDefinitions = sceneDefList,
                projectInfo = projectInfo,
                packageDependencies = packageDependencies,
                classDependencies = classDependencies,
                methodCalls = methodCalls,
                businessEntryPoints = businessEntryPoints,
                complexityMetrics = complexityMetrics,
                codeSmells = codeSmells,
                analysisConfig = config,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            logger.error("DependencyCodeAnalyzer", "代码依赖分析失败", error = e)
            throw RuntimeException("代码依赖分析失败: ${e.message}", e)
        } finally {
            // 清理缓存
            clearCaches()
        }
    }

    /**
     * 收集所有Java/Kotlin文件
     */
    private fun collectJavaKotlinFiles(config: AnalysisConfig): List<PsiJavaFile> {
        return ReadAction.compute<List<PsiJavaFile>, Throwable> {
            val scope = if (config.includeTestClasses) {
                GlobalSearchScope.projectScope(project)
            } else {
                GlobalSearchScope.getScopeRestrictedByFileTypes(
                    GlobalSearchScope.projectScope(project),
                    com.intellij.openapi.fileTypes.StdFileTypes.JAVA
                )
            }

            val files = mutableListOf<PsiJavaFile>()

        // 收集Java文件
        FileTypeIndex.processFiles(
            com.intellij.openapi.fileTypes.StdFileTypes.JAVA,
            { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile is PsiJavaFile && isIncludedPackage(psiFile.packageName, config)) {
                    files.add(psiFile)
                }
                true
            },
            scope
        )

        // 收集Kotlin文件 - 暂时简化，避免编译错误
        /*
        val kotlinFiles = mutableListOf<PsiFile>()
        FileTypeIndex.processFiles(
            KotlinFileType.INSTANCE,
            { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile != null && psiFile.language == KotlinLanguage.INSTANCE) {
                    val packageName = extractKotlinPackageName(psiFile)
                    if (isIncludedPackage(packageName, config)) {
                        kotlinFiles.add(psiFile)
                        files.add(psiFile)
                    }
                }
                true
            },
            scope
        )

        if (kotlinFiles.isNotEmpty()) {
            logger.info("DependencyCodeAnalyzer", "已收集到 ${kotlinFiles.size} 个Kotlin文件，准备分析")

            // 处理Kotlin文件
            kotlinFiles.forEach { ktFile ->
                try {
                    processKotlinFile(ktFile, config, result)
                } catch (e: Exception) {
                    logger.error("DependencyCodeAnalyzer", "处理Kotlin文件失败: ${ktFile.name}", error = e)
                }
            }
        }
        */

            files
        }
    }

    /**
     * 提取所有类信息
     */
    private fun extractClasses(psiFiles: List<PsiJavaFile>, config: AnalysisConfig): List<PsiClass> {
        return ReadAction.compute<List<PsiClass>, Throwable> {
            val classes = mutableListOf<PsiClass>()

            psiFiles.forEach { file ->
                ProgressManager.checkCanceled()
                file.classes.forEach { psiClass ->
                    if (isIncludedClass(psiClass, config)) {
                        classes.add(psiClass)
                    }
                }
            }

            classes
        }
    }

    /**
     * 分析包级依赖
     */
    private fun analyzePackageDependencies(classes: List<PsiClass>): List<PackageDependency> {
        val packageMap = classes.groupBy { (it.containingFile as? PsiJavaFile)?.packageName ?: "" }
        val packageDependencies = mutableListOf<PackageDependency>()

        packageMap.forEach { (packageName, psiClasses) ->
            val dependencies = mutableSetOf<String>()
            val dependents = mutableSetOf<String>()

            psiClasses.forEach { psiClass ->
                // 分析依赖
                extractClassDependencies(psiClass).forEach { dep ->
                    val depPackageName = extractPackageNameFromClassName(dep.className)
                    if (depPackageName != packageName && !isExcludedPackage(depPackageName)) {
                        dependencies.add(depPackageName)
                    }
                }
            }

            // 找出依赖当前包的其他包
            packageMap.forEach { (otherPackageName, otherClasses) ->
                if (otherPackageName != packageName) {
                    otherClasses.forEach { otherClass ->
                        extractClassDependencies(otherClass).forEach { dep ->
                            if (extractPackageNameFromClassName(dep.className) == packageName) {
                                dependents.add(otherPackageName)
                            }
                        }
                    }
                }
            }

            // 检测循环依赖
            val cycles = detectCyclicDependencies(packageName, dependencies, packageMap.keys.toList())

            packageDependencies.add(
                PackageDependency(
                    packageName = packageName,
                    dependencies = dependencies.toList(),
                    dependents = dependents.toList(),
                    dependencyCount = dependencies.size,
                    cycles = cycles
                )
            )
        }

        return packageDependencies
    }

    /**
     * 分析类级依赖
     */
    private fun analyzeClassDependencies(
        classes: List<PsiClass>,
        config: AnalysisConfig
    ): List<ClassDependency> {
        return classes.map { psiClass ->
            classCache.computeIfAbsent(psiClass.qualifiedName!!) {
                buildClassDependency(psiClass, config)
            }
        }
    }

    /**
     * 构建类依赖信息
     */
    private fun buildClassDependency(psiClass: PsiClass, config: AnalysisConfig): ClassDependency {
        val dependencies = extractClassDependencies(psiClass)
        val dependents = findClassDependents(psiClass.qualifiedName!!, config)

        return ClassDependency(
            className = psiClass.qualifiedName!!,
            packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: "",
            superClass = psiClass.superClass?.qualifiedName,
            interfaces = psiClass.interfaces.mapNotNull { it.qualifiedName },
            dependencies = dependencies,
            dependents = dependents,
            dependencyCount = dependencies.size,
            isPojo = isPojoClass(psiClass),
            isController = isControllerClass(psiClass),
            isService = isServiceClass(psiClass),
            isRepository = isRepositoryClass(psiClass)
        )
    }

    /**
     * 提取类依赖
     */
    private fun extractClassDependencies(psiClass: PsiClass): List<ClassReference> {
        val dependencies = mutableListOf<ClassReference>()

        // 1. 继承关系
        psiClass.superClass?.let { superClass ->
            dependencies.add(
                ClassReference(
                    className = superClass.qualifiedName!!,
                    referenceType = ReferenceType.INHERITANCE,
                    location = SourceLocation(
                        filePath = psiClass.containingFile.virtualFile.path,
                        lineNumber = psiClass.containingFile.viewProvider.document.getLineNumber(psiClass.textRange.startOffset) + 1,
                        columnNumber = psiClass.textRange.startOffset - psiClass.containingFile.viewProvider.document.getLineStartOffset(psiClass.containingFile.viewProvider.document.getLineNumber(psiClass.textRange.startOffset)) + 1
                    )
                )
            )
        }

        // 2. 接口实现
        psiClass.interfaces.forEach { psiInterface ->
            dependencies.add(
                ClassReference(
                    className = psiInterface.qualifiedName!!,
                    referenceType = ReferenceType.IMPLEMENTATION,
                    location = SourceLocation(
                        filePath = psiClass.containingFile.virtualFile.path,
                        lineNumber = psiClass.containingFile.viewProvider.document.getLineNumber(psiClass.textRange.startOffset) + 1,
                        columnNumber = psiClass.textRange.startOffset - psiClass.containingFile.viewProvider.document.getLineStartOffset(psiClass.containingFile.viewProvider.document.getLineNumber(psiClass.textRange.startOffset)) + 1
                    )
                )
            )
        }

        // 3. 字段引用
        psiClass.fields.forEach { field ->
            extractTypeReferences(field.type, field).forEach { typeRef ->
                dependencies.add(
                    ClassReference(
                        className = typeRef,
                        referenceType = ReferenceType.COMPOSITION,
                        location = SourceLocation(
                            filePath = psiClass.containingFile.virtualFile.path,
                            lineNumber = field.containingFile.viewProvider.document.getLineNumber(field.textRange.startOffset) + 1,
                            columnNumber = 0
                        )
                    )
                )
            }
        }

        // 4. 方法参数和返回类型
        psiClass.methods.forEach { method ->
            // 参数类型
            method.parameterList.parameters.forEach { parameter ->
                extractTypeReferences(parameter.type, parameter).forEach { typeRef ->
                    dependencies.add(
                        ClassReference(
                            className = typeRef,
                            referenceType = ReferenceType.DEPENDENCY,
                            location = SourceLocation(
                                filePath = psiClass.containingFile.virtualFile.path,
                                lineNumber = method.containingFile.viewProvider.document.getLineNumber(method.textRange.startOffset) + 1,
                                columnNumber = 0
                            )
                        )
                    )
                }
            }

            // 返回类型
            method.returnType?.let { returnType ->
                extractTypeReferences(returnType, method).forEach { typeRef ->
                    dependencies.add(
                        ClassReference(
                            className = typeRef,
                            referenceType = ReferenceType.DEPENDENCY,
                            location = SourceLocation(
                                filePath = psiClass.containingFile.virtualFile.path,
                                lineNumber = method.containingFile.viewProvider.document.getLineNumber(method.textRange.startOffset) + 1,
                                columnNumber = 0
                            )
                        )
                    )
                }
            }
        }

        // 5. 注解引用
        extractAnnotationReferences(psiClass).forEach { annotation ->
            dependencies.add(
                ClassReference(
                    className = annotation,
                    referenceType = ReferenceType.ANNOTATION,
                    location = SourceLocation(
                        filePath = psiClass.containingFile.virtualFile.path,
                        lineNumber = psiClass.containingFile.viewProvider.document.getLineNumber(psiClass.textRange.startOffset) + 1,
                        columnNumber = psiClass.textRange.startOffset - psiClass.containingFile.viewProvider.document.getLineStartOffset(psiClass.containingFile.viewProvider.document.getLineNumber(psiClass.textRange.startOffset)) + 1
                    )
                )
            )
        }

        return dependencies.distinctBy { it.className }
    }

    /**
     * 提取类型引用
     */
    private fun extractTypeReferences(psiType: PsiType, context: PsiElement): List<String> {
        val references = mutableListOf<String>()

        when (psiType) {
            is PsiClassType -> {
                val className = psiType.resolve()?.qualifiedName
                if (className != null && !isExcludedType(className)) {
                    references.add(className)
                }

                // 处理泛型参数
                psiType.parameters.forEach { param ->
                    references.addAll(extractTypeReferences(param, context))
                }
            }
            is PsiArrayType -> {
                references.addAll(extractTypeReferences(psiType.componentType, context))
            }
            is PsiWildcardType -> {
                psiType.extendsBound?.let { bound ->
                    references.addAll(extractTypeReferences(bound, context))
                }
                psiType.superBound?.let { bound ->
                    references.addAll(extractTypeReferences(bound, context))
                }
            }
        }

        return references
    }

    /**
     * 提取注解引用
     */
    private fun extractAnnotationReferences(element: PsiElement): List<String> {
        val annotations = mutableListOf<String>()

        val psiAnnotations: List<PsiAnnotation> = when (element) {
            is PsiClass -> element.annotations.toList()
            is PsiMethod -> element.annotations.toList()
            is PsiField -> element.annotations.toList()
            is PsiParameter -> element.annotations.toList()
            else -> emptyList()
        }

        psiAnnotations.forEach { annotation ->
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && !isExcludedType(qualifiedName)) {
                annotations.add(qualifiedName)
            }
        }

        return annotations
    }

    /**
     * 分析方法调用关系
     */
    private fun analyzeMethodCalls(
        psiFiles: List<PsiJavaFile>,
        config: AnalysisConfig
    ): List<MethodCall> {
        val methodCalls = mutableListOf<MethodCall>()

        psiFiles.forEach { file ->
            ProgressManager.checkCanceled()

            file.classes.forEach { psiClass ->
                if (isIncludedClass(psiClass, config)) {
                    psiClass.methods.forEach { method ->
                        extractMethodCalls(psiClass, method, methodCalls, config, 0)
                    }
                }
            }
        }

        return methodCalls
    }

    /**
     * 提取方法调用
     */
    private fun extractMethodCalls(
        callerClass: PsiClass,
        method: PsiMethod,
        methodCalls: MutableList<MethodCall>,
        config: AnalysisConfig,
        currentDepth: Int
    ) {
        if (currentDepth >= config.maxDepth) {
            return
        }

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                try {
                    val resolveResult = expression.resolveMethod()
                    if (resolveResult != null) {
                        // 过滤掉外部框架方法
                        if (!isExternalFrameworkMethod(resolveResult, config)) {
                            val calleeClass = resolveResult.containingClass
                            if (calleeClass != null && isIncludedClass(calleeClass, config)) {
                                val methodCall = MethodCall(
                                    callerClass = callerClass.qualifiedName!!,
                                    callerMethod = method.name,
                                    calleeClass = calleeClass.qualifiedName!!,
                                    calleeMethod = resolveResult.name,
                                    callType = determineCallType(expression),
                                    location = SourceLocation(
                                        filePath = expression.containingFile.virtualFile.path,
                                        lineNumber = expression.containingFile.viewProvider.document.getLineNumber(expression.textRange.startOffset) + 1,
                                        columnNumber = 0
                                    ),
                                    callDepth = currentDepth
                                )
                                methodCalls.add(methodCall)
                            }
                        } else {
                            logger.debug("DependencyCodeAnalyzer", "过滤外部框架方法调用: ${resolveResult.containingClass?.qualifiedName}.${resolveResult.name}")
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("DependencyCodeAnalyzer", "无法解析方法调用: ${expression.text}", mapOf("error" to e.message))
                }
            }
        }

        method.accept(visitor)
    }

    /**
     * 确定调用类型
     */
    private fun determineCallType(expression: PsiMethodCallExpression): CallType {
        // 这里可以根据需要实现更复杂的调用类型判断逻辑
        return CallType.DIRECT
    }

    /**
     * 识别业务入口点
     */
    private fun identifyBusinessEntryPoints(classes: List<PsiClass>): List<BusinessEntryPoint> {
        val entryPoints = mutableListOf<BusinessEntryPoint>()

        classes.forEach { psiClass ->
            psiClass.methods.forEach { method ->
                val entryType = determineEntryType(psiClass, method)
                if (entryType != null) {
                    val annotations = method.annotations.mapNotNull { it.qualifiedName }
                    val httpMapping = extractHttpMapping(annotations)

                    entryPoints.add(
                        BusinessEntryPoint(
                            className = psiClass.qualifiedName!!,
                            methodName = method.name,
                            entryType = entryType,
                            annotations = annotations,
                            businessScenario = determineBusinessScenario(psiClass, method),
                            httpMapping = httpMapping,
                            parameters = method.parameterList.parameters.map { param ->
                                ParameterInfo(
                                    name = param.name ?: "param",
                                    type = param.type.canonicalText,
                                    annotations = param.annotations.mapNotNull { it.qualifiedName }
                                )
                            }
                        )
                    )
                }
            }
        }

        return entryPoints
    }

    /**
     * 确定入口点类型
     */
    private fun determineEntryType(psiClass: PsiClass, method: PsiMethod): EntryType? {
        val classAnnotations = psiClass.annotations.mapNotNull { it.qualifiedName }
        val methodAnnotations = method.annotations.mapNotNull { it.qualifiedName }
        val allAnnotations = classAnnotations + methodAnnotations

        return when {
            allAnnotations.any { it.contains("Controller") || it.contains("RestController") } -> {
                if (methodAnnotations.any {
                    it.contains("Mapping") ||
                    it.contains("GetMapping") ||
                    it.contains("PostMapping") ||
                    it.contains("PutMapping") ||
                    it.contains("DeleteMapping") ||
                    it.contains("PatchMapping")
                }) EntryType.CONTROLLER else null
            }
            allAnnotations.any { it.contains("Service") } -> EntryType.SERVICE
            allAnnotations.any { it.contains("Scheduled") } -> EntryType.SCHEDULED
            allAnnotations.any {
                it.contains("EventListener") ||
                it.contains("KafkaListener") ||
                it.contains("RabbitListener")
            } -> EntryType.EVENT_LISTENER
            method.name == "main" && method.hasModifierProperty(PsiModifier.STATIC) -> EntryType.MAIN
            else -> null
        }
    }

    /**
     * 提取HTTP映射
     */
    private fun extractHttpMapping(annotations: List<String>): String? {
        return annotations.find {
            it.contains("Mapping") ||
            it.contains("GetMapping") ||
            it.contains("PostMapping") ||
            it.contains("PutMapping") ||
            it.contains("DeleteMapping") ||
            it.contains("PatchMapping")
        }
    }

    /**
     * 确定业务场景
     */
    private fun determineBusinessScenario(psiClass: PsiClass, method: PsiMethod): String {
        // 简单的场景识别逻辑，可以根据需要扩展
        val className = psiClass.name ?: ""
        val methodName = method.name

        return when {
            className.contains("User") && methodName.contains("login") -> "用户登录"
            className.contains("Order") && methodName.contains("create") -> "订单创建"
            className.contains("Payment") && methodName.contains("process") -> "支付处理"
            className.contains("Product") && methodName.contains("search") -> "商品搜索"
            else -> "${className}-${methodName}"
        }
    }

    /**
     * 计算复杂度指标
     */
    private fun calculateComplexityMetrics(
        classes: List<PsiClass>,
        config: AnalysisConfig
    ): Map<String, ClassComplexityMetrics> {
        val metrics = mutableMapOf<String, ClassComplexityMetrics>()

        classes.forEach { psiClass ->
            metrics[psiClass.qualifiedName!!] = calculateClassComplexityMetrics(psiClass, config)
        }

        return metrics
    }

    /**
     * 计算单个类的复杂度指标
     */
    private fun calculateClassComplexityMetrics(
        psiClass: PsiClass,
        config: AnalysisConfig
    ): ClassComplexityMetrics {
        return complexityCache.computeIfAbsent(psiClass.qualifiedName!!) {
            val methods = psiClass.methods
            val fields = psiClass.fields

            // 计算各种复杂度指标
            val cyclomaticComplexity = calculateCyclomaticComplexity(psiClass)
            val cognitiveComplexity = calculateCognitiveComplexity(psiClass)
            val maxNestingDepth = calculateMaxNestingDepth(psiClass)
            val lineOfCode = countLinesOfCode(psiClass)
            val totalParameters = methods.sumOf { it.parameterList.parametersCount }

            // 找出最长和最复杂的方法
            val methodComplexities = methods.map { method ->
                MethodComplexityInfo(
                    methodName = method.name,
                    complexity = calculateMethodCyclomaticComplexity(method),
                    lineOfCode = countMethodLinesOfCode(method),
                    parameterCount = method.parameterList.parametersCount,
                    nestingDepth = calculateMethodNestingDepth(method)
                )
            }

            val longestMethod = methodComplexities.maxByOrNull { it.lineOfCode } ?: methodComplexities.first()
            val mostComplexMethod = methodComplexities.maxByOrNull { it.complexity } ?: methodComplexities.first()

            // 计算耦合度指标
            val couplingMetrics = calculateCouplingMetrics(psiClass)

            ClassComplexityMetrics(
                className = psiClass.qualifiedName!!,
                cyclomaticComplexity = cyclomaticComplexity,
                cognitiveComplexity = cognitiveComplexity,
                nestingDepth = maxNestingDepth,
                methodCount = methods.size,
                fieldCount = fields.size,
                lineOfCode = lineOfCode,
                parameterCount = totalParameters,
                longestMethod = longestMethod,
                mostComplexMethod = mostComplexMethod,
                couplingMetrics = couplingMetrics
            )
        }
    }

    /**
     * 计算圈复杂度
     */
    private fun calculateCyclomaticComplexity(psiClass: PsiClass): Int {
        return psiClass.methods.sumOf { calculateMethodCyclomaticComplexity(it) }
    }

    /**
     * 计算方法的圈复杂度
     */
    private fun calculateMethodCyclomaticComplexity(method: PsiMethod): Int {
        var complexity = 1 // 基础复杂度

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity += 1
                if (statement.elseBranch != null) {
                    complexity += 1
                }
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                complexity += 1
            }

            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                complexity += 1
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                super.visitForeachStatement(statement)
                complexity += 1
            }

            override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
                super.visitDoWhileStatement(statement)
                complexity += 1
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                complexity += statement.body?.children?.filterIsInstance<PsiSwitchLabelStatement>()?.size ?: 0
            }

            override fun visitCatchSection(section: PsiCatchSection) {
                super.visitCatchSection(section)
                complexity += 1
            }

            override fun visitConditionalExpression(expression: PsiConditionalExpression) {
                super.visitConditionalExpression(expression)
                complexity += 1
            }
        }

        method.accept(visitor)
        return complexity
    }

    /**
     * 计算认知复杂度
     */
    private fun calculateCognitiveComplexity(psiClass: PsiClass): Int {
        return psiClass.methods.sumOf { calculateMethodCognitiveComplexity(it) }
    }

    /**
     * 计算方法的认知复杂度
     */
    private fun calculateMethodCognitiveComplexity(method: PsiMethod): Int {
        var complexity = 0
        var nestingLevel = 0

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity += 1 + nestingLevel
                nestingLevel++
                statement.thenBranch?.accept(this)
                statement.elseBranch?.accept(this)
                nestingLevel--
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                complexity += 1 + nestingLevel
                nestingLevel++
                super.visitWhileStatement(statement)
                nestingLevel--
            }

            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                complexity += 1 + nestingLevel
                nestingLevel++
                super.visitForStatement(statement)
                nestingLevel--
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                super.visitForeachStatement(statement)
                complexity += 1 + nestingLevel
                nestingLevel++
                super.visitForeachStatement(statement)
                nestingLevel--
            }

            override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
                super.visitDoWhileStatement(statement)
                complexity += 1 + nestingLevel
                nestingLevel++
                super.visitDoWhileStatement(statement)
                nestingLevel--
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                complexity += 1 + nestingLevel
                nestingLevel++
                super.visitSwitchStatement(statement)
                nestingLevel--
            }

            override fun visitBinaryExpression(expression: PsiBinaryExpression) {
                super.visitBinaryExpression(expression)
                val operation = expression.operationTokenType
                if (operation == JavaTokenType.ANDAND || operation == JavaTokenType.OROR) {
                    complexity += 1
                }
            }
        }

        method.accept(visitor)
        return complexity
    }

    /**
     * 计算最大嵌套深度
     */
    private fun calculateMaxNestingDepth(psiClass: PsiClass): Int {
        return psiClass.methods.maxOfOrNull { calculateMethodNestingDepth(it) } ?: 0
    }

    /**
     * 计算方法的嵌套深度
     */
    private fun calculateMethodNestingDepth(method: PsiMethod): Int {
        var maxDepth = 0
        var currentDepth = 0

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                statement.thenBranch?.accept(this)
                statement.elseBranch?.accept(this)
                currentDepth--
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitWhileStatement(statement)
                currentDepth--
            }

            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitForStatement(statement)
                currentDepth--
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                super.visitForeachStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitForeachStatement(statement)
                currentDepth--
            }

            override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
                super.visitDoWhileStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitDoWhileStatement(statement)
                currentDepth--
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitSwitchStatement(statement)
                currentDepth--
            }

            override fun visitTryStatement(statement: PsiTryStatement) {
                super.visitTryStatement(statement)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitTryStatement(statement)
                currentDepth--
            }
        }

        method.accept(visitor)
        return maxDepth
    }

    /**
     * 统计代码行数
     */
    private fun countLinesOfCode(psiClass: PsiClass): Int {
        return psiClass.text.split('\n').count { it.trim().isNotEmpty() && !it.trim().startsWith("//") }
    }

    /**
     * 统计方法代码行数
     */
    private fun countMethodLinesOfCode(method: PsiMethod): Int {
        return method.body?.text?.split('\n')?.count { it.trim().isNotEmpty() && !it.trim().startsWith("//") } ?: 0
    }

    /**
     * 计算耦合度指标
     */
    private fun calculateCouplingMetrics(psiClass: PsiClass): CouplingMetrics {
        val className = psiClass.qualifiedName!!
        val dependencies = extractClassDependencies(psiClass).map { it.className }.toSet()
        val dependents = findClassDependents(className, AnalysisConfig.default())

        val afferentCoupling = dependents.size
        val efferentCoupling = dependencies.size
        val instability = if (afferentCoupling + efferentCoupling > 0) {
            efferentCoupling.toDouble() / (afferentCoupling + efferentCoupling)
        } else 0.0

        val abstractness = if (psiClass.isInterface || psiClass.isEnum || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            1.0
        } else {
            0.0
        }

        val distance = kotlin.math.abs(abstractness + instability - 1)

        return CouplingMetrics(
            afferentCoupling = afferentCoupling,
            efferentCoupling = efferentCoupling,
            instability = instability,
            abstractness = abstractness,
            distance = distance
        )
    }

    /**
     * 检测代码坏味道
     */
    private fun detectCodeSmells(
        complexityMetrics: Map<String, ClassComplexityMetrics>,
        config: AnalysisConfig
    ): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()

        complexityMetrics.forEach { (className, metrics) ->
            // 检测长方法
            if (metrics.longestMethod.lineOfCode > config.complexityThresholds.methodLength) {
                codeSmells.add(
                    CodeSmell(
                        type = CodeSmellType.LONG_METHOD,
                        severity = Severity.HIGH,
                        className = className,
                        methodName = metrics.longestMethod.methodName,
                        description = "方法过长: ${metrics.longestMethod.lineOfCode} 行，建议拆分",
                        location = SourceLocation("", 0, 0), // 需要更精确的位置信息
                        mapOf("lines" to metrics.longestMethod.lineOfCode)
                    )
                )
            }

            // 检测长参数列表
            if (metrics.mostComplexMethod.parameterCount > config.complexityThresholds.parameterCount) {
                codeSmells.add(
                    CodeSmell(
                        type = CodeSmellType.LONG_PARAMETER_LIST,
                        severity = Severity.MEDIUM,
                        className = className,
                        methodName = metrics.mostComplexMethod.methodName,
                        description = "参数过多: ${metrics.mostComplexMethod.parameterCount} 个参数",
                        location = SourceLocation("", 0, 0),
                        mapOf("parameters" to metrics.mostComplexMethod.parameterCount)
                    )
                )
            }

            // 检测大类
            if (metrics.lineOfCode > config.complexityThresholds.classLength) {
                codeSmells.add(
                    CodeSmell(
                        type = CodeSmellType.LARGE_CLASS,
                        severity = Severity.HIGH,
                        className = className,
                        methodName = null,
                        description = "类过大: ${metrics.lineOfCode} 行代码",
                        location = SourceLocation("", 0, 0),
                        mapOf("lines" to metrics.lineOfCode)
                    )
                )
            }

            // 检测深度嵌套
            if (metrics.nestingDepth > config.complexityThresholds.nestingDepth) {
                codeSmells.add(
                    CodeSmell(
                        type = CodeSmellType.DEEP_NESTING,
                        severity = Severity.HIGH,
                        className = className,
                        methodName = metrics.mostComplexMethod.methodName,
                        description = "嵌套过深: ${metrics.nestingDepth} 层",
                        location = SourceLocation("", 0, 0),
                        mapOf("depth" to metrics.nestingDepth)
                    )
                )
            }

            // 检测高复杂度
            if (metrics.cyclomaticComplexity > config.complexityThresholds.cyclomaticComplexity) {
                codeSmells.add(
                    CodeSmell(
                        type = CodeSmellType.SPAGHETTI_CODE,
                        severity = Severity.HIGH,
                        className = className,
                        methodName = metrics.mostComplexMethod.methodName,
                        description = "圈复杂度过高: ${metrics.cyclomaticComplexity}",
                        location = SourceLocation("", 0, 0),
                        mapOf("complexity" to metrics.cyclomaticComplexity)
                    )
                )
            }
        }

        return codeSmells
    }

    /**
     * 构建项目信息
     */
    private fun buildProjectInfo(classes: List<PsiClass>, config: AnalysisConfig): ProjectInfo {
        val packages = classes.map { (it.containingFile as? PsiJavaFile)?.packageName ?: "" }.distinct()
        val totalMethods = classes.sumOf { it.methods.size }

        return ProjectInfo(
            name = project.name,
            rootPackage = config.excludePackages.firstOrNull() ?: "",
            totalClasses = classes.size,
            totalPackages = packages.size,
            totalMethods = totalMethods
        )
    }

    /**
     * 找出类的依赖者（即哪些类依赖于给定的类）
     */
    private fun findClassDependents(className: String, config: AnalysisConfig): List<String> {
        val dependents = mutableListOf<String>()
        // 暂时使用项目范围，后续可以根据需要扩展配置
        val searchScope = GlobalSearchScope.projectScope(project)

        return ReadAction.compute<List<String>, Exception> {
            try {
                // 遍历所有Java文件
                FileTypeIndex.processFiles(com.intellij.openapi.fileTypes.StdFileTypes.JAVA, { virtualFile ->
                    val psiFile = psiManager.findFile(virtualFile)
                    if (psiFile is PsiJavaFile) {
                        psiFile.classes.forEach { psiClass ->
                            val qualifiedName = psiClass.qualifiedName
                            if (qualifiedName != null && qualifiedName != className) {
                                // 检查当前类是否依赖于目标类
                                if (isClassDependsOn(psiClass, className)) {
                                    dependents.add(qualifiedName)
                                }
                            }
                        }
                    }
                    true
                }, searchScope)

                // 暂时简化Kotlin文件处理
                /*
                // 遍历所有Kotlin文件
                val kotlinFileType = com.intellij.openapi.fileTypes.FileTypeRegistry.getInstance()
                    .getFileTypeByExtension("kt")
                if (kotlinFileType != null) {
                    FileTypeIndex.processFiles(kotlinFileType, { virtualFile ->
                        val psiFile = psiManager.findFile(virtualFile)
                        if (psiFile != null && psiFile.language == KotlinLanguage.INSTANCE) {
                            val kotlinClasses = PsiTreeUtil.getChildrenOfTypeAsList(
                                psiFile, org.jetbrains.kotlin.psi.KtClass::class.java
                            )
                            kotlinClasses.forEach { ktClass ->
                                val qualifiedName = ktClass.fqName?.asString()
                                if (qualifiedName != null && qualifiedName != className) {
                                    if (isKotlinClassDependsOn(ktClass, className)) {
                                        dependents.add(qualifiedName)
                                    }
                                }
                            }
                        }
                        true
                    }, searchScope)
                }
                */

                dependents.distinct()
            } catch (e: Exception) {
                logger.error("DependencyCodeAnalyzer", "查找类依赖者时出错: $className", error = e)
                emptyList()
            }
        }
    }

    /**
     * 检查Java类是否依赖于目标类
     */
    private fun isClassDependsOn(psiClass: PsiClass, targetClassName: String): Boolean {
        return try {
            // 1. 检查继承关系
            psiClass.superClass?.let { superClass ->
                if (superClass.qualifiedName == targetClassName) return true
            }

            // 2. 检查接口实现
            psiClass.interfaces.forEach { psiInterface ->
                if (psiInterface.qualifiedName == targetClassName) return true
            }

            // 3. 检查字段类型
            psiClass.fields.forEach { field ->
                if (isTypeDependsOn(field.type, targetClassName)) return true
            }

            // 4. 检查方法参数和返回值
            psiClass.methods.forEach { method ->
                // 检查参数类型
                method.parameterList.parameters.forEach { parameter ->
                    if (isTypeDependsOn(parameter.type, targetClassName)) return true
                }
                // 检查返回值类型
                if (isTypeDependsOn(method.returnType, targetClassName)) return true
            }

            // 5. 检查方法体中的使用 - 简化实现避免编译错误
            psiClass.methods.forEach { method ->
                if (method.body != null) {
                    try {
                        // 简化的依赖检查
                        val bodyText = method.body?.text ?: ""
                        if (bodyText.contains(targetClassName) ||
                            bodyText.contains("${targetClassName.split(".").lastOrNull()}")) {
                            return true
                        }
                    } catch (e: Exception) {
                        logger.debug("DependencyCodeAnalyzer", "检查方法体时出错: ${method.name}", mapOf("error" to e.message))
                    }
                }
            }

            false
        } catch (e: Exception) {
            logger.debug("DependencyCodeAnalyzer", "检查类依赖时出错: ${psiClass.qualifiedName}", mapOf("target" to targetClassName, "error" to e.message))
            false
        }
    }

    /**
     * 检查Kotlin类是否依赖于目标类 - 暂时简化
     */
    private fun isKotlinClassDependsOn(ktClass: org.jetbrains.kotlin.psi.KtClass, targetClassName: String): Boolean {
        return try {
            // 简化的依赖检查，基于文本匹配
            val classText = ktClass.text
            val simpleClassName = targetClassName.split(".").lastOrNull() ?: targetClassName

            classText.contains(simpleClassName)
        } catch (e: Exception) {
            logger.debug("DependencyCodeAnalyzer", "检查Kotlin类依赖时出错: ${ktClass.fqName?.asString()}", mapOf("target" to targetClassName, "error" to e.message))
            false
        }
    }

    /**
     * 检查PsiType是否依赖于目标类
     */
    private fun isTypeDependsOn(psiType: PsiType?, targetClassName: String): Boolean {
        if (psiType == null) return false

        return when (psiType) {
            is PsiArrayType -> isTypeDependsOn(psiType.componentType, targetClassName)
            is PsiClassType -> {
                val className = psiType.resolve()?.qualifiedName
                className == targetClassName
            }
            else -> {
                val canonicalText = psiType.canonicalText
                // 处理泛型、集合等复杂类型
                canonicalText.contains(targetClassName) ||
                canonicalText.contains("$targetClassName<") ||
                canonicalText.contains("<$targetClassName>") ||
                canonicalText.contains(", $targetClassName") ||
                canonicalText.contains("$targetClassName,")
            }
        }
    }

    /**
     * 用于快速跳出依赖检查的异常
     */
    private class FoundDependencyException : RuntimeException()

    /**
     * 检测循环依赖（基于DFS深度优先搜索算法）
     */
    private fun detectCyclicDependencies(
        packageName: String,
        dependencies: Set<String>,
        allPackages: List<String>
    ): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val currentPath = mutableListOf<String>()

        // 构建完整的依赖图
        val dependencyGraph = mutableMapOf<String, Set<String>>()
        allPackages.forEach { pkg ->
            dependencyGraph[pkg] = emptySet()
        }
        dependencyGraph[packageName] = dependencies

        fun dfs(node: String, graph: Map<String, Set<String>>): List<String>? {
            if (node in recursionStack) {
                // 找到循环，返回循环路径的开始位置
                val cycleStartIndex = currentPath.indexOf(node)
                return if (cycleStartIndex != -1) {
                    currentPath.subList(cycleStartIndex, currentPath.size) + node
                } else {
                    listOf(node)
                }
            }

            if (node in visited) {
                return null
            }

            visited.add(node)
            recursionStack.add(node)
            currentPath.add(node)

            // 遍历当前节点的所有依赖
            graph[node]?.forEach { dependent ->
                val cyclePath = dfs(dependent, graph)
                if (cyclePath != null) {
                    return cyclePath
                }
            }

            recursionStack.remove(node)
            currentPath.removeLast()
            return null
        }

        // 对每个未访问的节点进行DFS
        dependencyGraph.keys.forEach { node ->
            if (node !in visited) {
                val cyclePath = dfs(node, dependencyGraph)
                if (cyclePath != null && cyclePath.size >= 2) {
                    // 添加循环路径（确保不重复且有效）
                    val normalizedCycle = normalizeCyclePath(cyclePath)
                    if (normalizedCycle.size >= 2 && !cycles.contains(normalizedCycle)) {
                        cycles.add(normalizedCycle)
                    }
                }
            }
        }

        return cycles
    }

    /**
     * 标准化循环路径，确保路径的最小字典序表示
     */
    private fun normalizeCyclePath(cyclePath: List<String>): List<String> {
        if (cyclePath.isEmpty()) return cyclePath

        // 找到最小字符串作为起始点，确保循环路径的一致性
        val minIndex = cyclePath.indices.minByOrNull { cyclePath[it] } ?: 0

        // 重新排列路径，从最小的节点开始
        val normalized = cyclePath.drop(minIndex) + cyclePath.take(minIndex)

        // 移除重复的最后一个节点（如果存在）
        return if (normalized.size > 1 && normalized.first() == normalized.last()) {
            normalized.dropLast(1)
        } else {
            normalized
        }
    }

    /**
     * 判断是否为POJO类
     */
    private fun isPojoClass(psiClass: PsiClass): Boolean {
        return psiClass.fields.isNotEmpty() &&
                psiClass.methods.any { it.name.startsWith("get") || it.name.startsWith("set") } &&
                !psiClass.annotations.any { it.qualifiedName?.contains("Controller") == true } &&
                !psiClass.annotations.any { it.qualifiedName?.contains("Service") == true } &&
                !psiClass.annotations.any { it.qualifiedName?.contains("Repository") == true }
    }

    /**
     * 判断是否为Controller类
     */
    private fun isControllerClass(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            it.qualifiedName?.contains("Controller") == true
        }
    }

    /**
     * 判断是否为Service类
     */
    private fun isServiceClass(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            it.qualifiedName?.contains("Service") == true
        }
    }

    /**
     * 判断是否为Repository类
     */
    private fun isRepositoryClass(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            it.qualifiedName?.contains("Repository") == true
        }
    }

    /**
     * 判断包是否包含在分析范围内
     */
    private fun isIncludedPackage(packageName: String, config: AnalysisConfig): Boolean {
        return config.excludePackages.none { exclude ->
            packageName.startsWith(exclude)
        }
    }

    /**
     * 判断类是否包含在分析范围内
     */
    private fun isIncludedClass(psiClass: PsiClass, config: AnalysisConfig): Boolean {
        return isIncludedPackage((psiClass.containingFile as? PsiJavaFile)?.packageName ?: "", config) &&
                psiClass.qualifiedName != null &&
                !psiClass.isAnnotationType &&
                !psiClass.isEnum
    }

    /**
     * 判断类型是否应该排除
     */
    private fun isExcludedType(typeName: String): Boolean {
        return typeName.startsWith("java.") ||
                typeName.startsWith("javax.") ||
                typeName.startsWith("kotlin.") ||
                typeName.startsWith("org.springframework.") ||
                typeName.startsWith("lombok.")
    }

    /**
     * 判断方法是否为外部框架方法
     */
    private fun isExternalFrameworkMethod(psiMethod: PsiMethod, config: AnalysisConfig): Boolean {
        val className = psiMethod.containingClass?.qualifiedName ?: return true

        // 如果配置允许包含外部依赖，则不过滤
        if (config.includeExternalDependencies) {
            return false
        }

        // 检查是否属于配置中排除的框架包
        return config.excludedFrameworkPackages.any { packageName ->
            className.startsWith("$packageName.") || className == packageName
        }
    }

    /**
     * 判断包是否应该排除
     */
    private fun isExcludedPackage(packageName: String): Boolean {
        return packageName.startsWith("java.") ||
                packageName.startsWith("javax.") ||
                packageName.startsWith("kotlin.") ||
                packageName.startsWith("org.springframework.") ||
                packageName.startsWith("lombok.")
    }

    /**
     * 从类名提取包名
     */
    private fun extractPackageNameFromClassName(className: String): String {
        val lastDotIndex = className.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            className.substring(0, lastDotIndex)
        } else {
            ""
        }
    }

    /**
     * 处理Kotlin文件，提取类信息并添加到分析结果中 - 暂时简化
     */
    private fun processKotlinFile(ktFile: PsiFile, config: AnalysisConfig, result: DependencyAnalysisResult) {
        // 暂时留空，等待后续完整实现
        logger.debug("DependencyCodeAnalyzer", "Kotlin文件处理暂时简化: ${ktFile.name}")
    }

    /**
     * 提取Kotlin文件的包名
     */
    private fun extractKotlinPackageName(psiFile: PsiFile): String {
        if (psiFile.language == KotlinLanguage.INSTANCE) {
            val ktFile = psiFile as org.jetbrains.kotlin.psi.KtFile
            return ktFile.packageFqName.asString()
        }
        return ""
    }

    /**
     * 清理缓存
     */
    private fun clearCaches() {
        classCache.clear()
        methodCallCache.clear()
        complexityCache.clear()
    }
}

/**
 * AnalysisConfig 的扩展方法
 */
private fun AnalysisConfig.Companion.default(): AnalysisConfig {
    return AnalysisConfig(
        maxDepth = 10,
        excludePackages = listOf("java.", "javax.", "kotlin.", "org.springframework.", "lombok."),
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