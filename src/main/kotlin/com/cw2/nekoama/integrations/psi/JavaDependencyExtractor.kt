package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType

/**
 * Java代码依赖关系提取器
 * 专门用于提取Java代码中的各种依赖关系
 */
class JavaDependencyExtractor {

    private val logger = NekoamaLogger

    /**
     * 提取类的所有依赖关系
     */
    fun extractClassDependencies(psiClass: PsiClass): List<ClassReference> {
        return ReadAction.compute<List<ClassReference>, Exception> {
            val dependencies = mutableListOf<ClassReference>()

            try {
                // 1. 继承关系
                extractInheritanceDependencies(psiClass, dependencies)

                // 2. 接口实现
                extractInterfaceDependencies(psiClass, dependencies)

                // 3. 字段依赖
                extractFieldDependencies(psiClass, dependencies)

                // 4. 方法依赖
                extractMethodDependencies(psiClass, dependencies)

                // 5. 构造器依赖
                extractConstructorDependencies(psiClass, dependencies)

                // 6. 内部类依赖
                extractInnerClassDependencies(psiClass, dependencies)

                // 7. 静态导入和注解依赖
                extractAnnotationDependencies(psiClass, dependencies)

            } catch (e: Exception) {
                logger.error("JavaDependencyExtractor", "提取类依赖失败: ${psiClass.qualifiedName}", error = e)
            }

            dependencies.distinctBy { it.className }
        }
    }

    /**
     * 提取继承关系
     */
    private fun extractInheritanceDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        psiClass.superClass?.let { superClass ->
            if (superClass.qualifiedName != null && !isExcludedType(superClass.qualifiedName!!)) {
                dependencies.add(
                    ClassReference(
                        className = superClass.qualifiedName!!,
                        referenceType = ReferenceType.INHERITANCE,
                        location = createSourceLocation(psiClass)
                    )
                )
            }
        }
    }

    /**
     * 提取接口实现关系
     */
    private fun extractInterfaceDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        psiClass.interfaces.forEach { psiInterface ->
            if (psiInterface.qualifiedName != null && !isExcludedType(psiInterface.qualifiedName!!)) {
                dependencies.add(
                    ClassReference(
                        className = psiInterface.qualifiedName!!,
                        referenceType = ReferenceType.IMPLEMENTATION,
                        location = createSourceLocation(psiClass)
                    )
                )
            }
        }
    }

    /**
     * 提取字段依赖
     */
    private fun extractFieldDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        psiClass.fields.forEach { field ->
            val fieldTypes = extractTypesFromPsiType(field.type)
            fieldTypes.forEach { typeName ->
                if (!isExcludedType(typeName) && typeName != psiClass.qualifiedName) {
                    val referenceType = determineFieldReferenceType(field)
                    dependencies.add(
                        ClassReference(
                            className = typeName,
                            referenceType = referenceType,
                            location = createSourceLocation(field)
                        )
                    )
                }
            }
        }
    }

    /**
     * 提取方法依赖
     */
    private fun extractMethodDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        psiClass.methods.forEach { method ->
            // 返回类型依赖
            method.returnType?.let { returnType ->
                val returnTypes = extractTypesFromPsiType(returnType)
                returnTypes.forEach { typeName ->
                    if (!isExcludedType(typeName) && typeName != psiClass.qualifiedName) {
                        dependencies.add(
                            ClassReference(
                                className = typeName,
                                referenceType = ReferenceType.DEPENDENCY,
                                location = createSourceLocation(method)
                            )
                        )
                    }
                }
            }

            // 参数类型依赖
            method.parameterList.parameters.forEach { parameter ->
                parameter.type?.let { paramType ->
                    val paramTypes = extractTypesFromPsiType(paramType)
                    paramTypes.forEach { typeName ->
                        if (!isExcludedType(typeName) && typeName != psiClass.qualifiedName) {
                            dependencies.add(
                                ClassReference(
                                    className = typeName,
                                    referenceType = ReferenceType.DEPENDENCY,
                                    location = createSourceLocation(parameter)
                                )
                            )
                        }
                    }
                }
            }

            // 方法体中的局部变量和调用
            extractMethodBodyDependencies(method, dependencies)
        }
    }

    /**
     * 提取方法体中的依赖
     */
    private fun extractMethodBodyDependencies(
        method: PsiMethod,
        dependencies: MutableList<ClassReference>
    ) {
        if (method.body == null) return

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitLocalVariable(variable: PsiLocalVariable) {
                super.visitLocalVariable(variable)
                val types = extractTypesFromPsiType(variable.type)
                types.forEach { typeName ->
                    if (!isExcludedType(typeName)) {
                        dependencies.add(
                            ClassReference(
                                className = typeName,
                                referenceType = ReferenceType.DEPENDENCY,
                                location = createSourceLocation(variable)
                            )
                        )
                    }
                }
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                try {
                    val resolvedMethod = expression.resolveMethod()
                    if (resolvedMethod != null) {
                        val containingClass = resolvedMethod.containingClass
                        if (containingClass != null &&
                            containingClass.qualifiedName != null &&
                            !isExcludedType(containingClass.qualifiedName!!)) {

                            dependencies.add(
                                ClassReference(
                                    className = containingClass.qualifiedName!!,
                                    referenceType = ReferenceType.ASSOCIATION,
                                    location = createSourceLocation(expression)
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("JavaDependencyExtractor", "无法解析方法调用: ${expression.text}", mapOf("error" to e.message))
                }
            }

            override fun visitNewExpression(expression: PsiNewExpression) {
                super.visitNewExpression(expression)
                expression.type?.let { type ->
                    val types = extractTypesFromPsiType(type)
                    types.forEach { typeName ->
                        if (!isExcludedType(typeName)) {
                            dependencies.add(
                                ClassReference(
                                    className = typeName,
                                    referenceType = ReferenceType.COMPOSITION,
                                    location = createSourceLocation(expression)
                                )
                            )
                        }
                    }
                }
            }

            override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
                super.visitTypeCastExpression(expression)
                expression.castType?.let { castTypeElement ->
                    castTypeElement.type?.let { castType ->
                        val types = extractTypesFromPsiType(castType)
                        types.forEach { typeName ->
                            if (!isExcludedType(typeName)) {
                                dependencies.add(
                                    ClassReference(
                                        className = typeName,
                                        referenceType = ReferenceType.DEPENDENCY,
                                        location = createSourceLocation(expression)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
                super.visitInstanceOfExpression(expression)
                expression.checkType?.let { checkTypeElement ->
                    checkTypeElement.type?.let { checkType ->
                        val types = extractTypesFromPsiType(checkType)
                        types.forEach { typeName ->
                            if (!isExcludedType(typeName)) {
                                dependencies.add(
                                    ClassReference(
                                        className = typeName,
                                        referenceType = ReferenceType.DEPENDENCY,
                                        location = createSourceLocation(expression)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        method.body!!.accept(visitor)
    }

    /**
     * 提取构造器依赖
     */
    private fun extractConstructorDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        psiClass.constructors.forEach { constructor ->
            // 构造器参数依赖
            constructor.parameterList.parameters.forEach { parameter ->
                parameter.type?.let { paramType ->
                    val paramTypes = extractTypesFromPsiType(paramType)
                    paramTypes.forEach { typeName ->
                        if (!isExcludedType(typeName) && typeName != psiClass.qualifiedName) {
                            dependencies.add(
                                ClassReference(
                                    className = typeName,
                                    referenceType = ReferenceType.DEPENDENCY,
                                    location = createSourceLocation(parameter)
                                )
                            )
                        }
                    }
                }
            }

            // 构造器体中的依赖
            if (constructor.body != null) {
                extractMethodBodyDependencies(constructor, dependencies)
            }
        }
    }

    /**
     * 提取内部类依赖
     */
    private fun extractInnerClassDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        psiClass.innerClasses.forEach { innerClass ->
            // 内部类与外部类的关系
            dependencies.add(
                ClassReference(
                    className = psiClass.qualifiedName!!,
                    referenceType = ReferenceType.ASSOCIATION,
                    location = createSourceLocation(innerClass)
                )
            )

            // 递归提取内部类的依赖
            dependencies.addAll(extractClassDependencies(innerClass))
        }
    }

    /**
     * 提取注解依赖
     */
    private fun extractAnnotationDependencies(
        psiClass: PsiClass,
        dependencies: MutableList<ClassReference>
    ) {
        // 类级别的注解
        psiClass.annotations.forEach { annotation ->
            extractAnnotationType(annotation, dependencies, createSourceLocation(psiClass))
        }

        // 字段注解
        psiClass.fields.forEach { field ->
            field.annotations.forEach { annotation ->
                extractAnnotationType(annotation, dependencies, createSourceLocation(field))
            }
        }

        // 方法注解
        psiClass.methods.forEach { method ->
            method.annotations.forEach { annotation ->
                extractAnnotationType(annotation, dependencies, createSourceLocation(method))
            }

            // 参数注解
            method.parameterList.parameters.forEach { parameter ->
                parameter.annotations.forEach { annotation ->
                    extractAnnotationType(annotation, dependencies, createSourceLocation(parameter))
                }
            }
        }
    }

    /**
     * 提取注解类型
     */
    private fun extractAnnotationType(
        annotation: PsiAnnotation,
        dependencies: MutableList<ClassReference>,
        location: SourceLocation
    ) {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName != null && !isExcludedType(qualifiedName)) {
            dependencies.add(
                ClassReference(
                    className = qualifiedName,
                    referenceType = ReferenceType.ANNOTATION,
                    location = location
                )
            )
        }

        // 注解属性的值也可能包含类型依赖
        annotation.parameterList.attributes.forEach { attribute ->
            extractAnnotationAttributeValue(attribute.value, dependencies, location)
        }
    }

    /**
     * 提取注解属性值中的类型依赖
     */
    private fun extractAnnotationAttributeValue(
        value: PsiAnnotationMemberValue?,
        dependencies: MutableList<ClassReference>,
        location: SourceLocation
    ) {
        if (value == null) return

        when (value) {
            is PsiAnnotation -> {
                extractAnnotationType(value, dependencies, location)
            }
            is PsiArrayInitializerMemberValue -> {
                value.initializers.forEach { initializer ->
                    extractAnnotationAttributeValue(initializer, dependencies, location)
                }
            }
            is PsiClassObjectAccessExpression -> {
                val types = extractTypesFromPsiType(value.type)
                types.forEach { typeName ->
                    if (!isExcludedType(typeName)) {
                        dependencies.add(
                            ClassReference(
                                className = typeName,
                                referenceType = ReferenceType.DEPENDENCY,
                                location = location
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * 从PsiType提取类型名称
     */
    private fun extractTypesFromPsiType(psiType: PsiType): List<String> {
        val types = mutableListOf<String>()

        when (psiType) {
            is PsiClassType -> {
                val className = psiType.resolve()?.qualifiedName
                if (className != null) {
                    types.add(className)
                }

                // 处理泛型参数
                psiType.parameters.forEach { param ->
                    types.addAll(extractTypesFromPsiType(param))
                }
            }
            is PsiArrayType -> {
                types.addAll(extractTypesFromPsiType(psiType.componentType))
            }
            is PsiWildcardType -> {
                psiType.extendsBound?.let { bound ->
                    types.addAll(extractTypesFromPsiType(bound))
                }
                psiType.superBound?.let { bound ->
                    types.addAll(extractTypesFromPsiType(bound))
                }
            }
            is PsiDisjunctionType -> {
                psiType.disjunctions.forEach { type ->
                    types.addAll(extractTypesFromPsiType(type))
                }
            }
            is PsiIntersectionType -> {
                psiType.conjuncts.forEach { type ->
                    types.addAll(extractTypesFromPsiType(type))
                }
            }
        }

        return types.distinct()
    }

    /**
     * 确定字段引用类型
     */
    private fun determineFieldReferenceType(field: PsiField): ReferenceType {
        return when {
            field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL) -> {
                ReferenceType.AGGREGATION
            }
            field.hasModifierProperty(PsiModifier.STATIC) -> {
                ReferenceType.ASSOCIATION
            }
            field.type is PsiArrayType -> {
                ReferenceType.AGGREGATION
            }
            else -> {
                ReferenceType.COMPOSITION
            }
        }
    }

    /**
     * 创建源码位置
     */
    private fun createSourceLocation(element: PsiElement): SourceLocation {
        val file = element.containingFile
        val lineNumber = file.viewProvider.document?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: 0
        val columnNumber = element.textRange.startOffset -
                (file.viewProvider.document?.getLineStartOffset(lineNumber - 1) ?: 0)

        return SourceLocation(
            filePath = file.virtualFile?.path ?: "",
            lineNumber = lineNumber,
            columnNumber = columnNumber
        )
    }

    /**
     * 判断类型是否应该排除
     */
    private fun isExcludedType(typeName: String): Boolean {
        return typeName.startsWith("java.") ||
                typeName.startsWith("javax.") ||
                typeName.startsWith("kotlin.") ||
                typeName.startsWith("org.springframework.") ||
                typeName.startsWith("lombok.") ||
                typeName.startsWith("org.apache.") ||
                typeName.startsWith("com.google.") ||
                typeName.startsWith("org.junit.") ||
                typeName.startsWith("org.mockito.") ||
                typeName == "void" ||
                typeName == "boolean" ||
                typeName == "byte" ||
                typeName == "short" ||
                typeName == "int" ||
                typeName == "long" ||
                typeName == "float" ||
                typeName == "double" ||
                typeName == "char" ||
                typeName.startsWith("java.lang.") && (
                        typeName == "java.lang.String" ||
                        typeName == "java.lang.Object" ||
                        typeName == "java.lang.Integer" ||
                        typeName == "java.lang.Long" ||
                        typeName == "java.lang.Double" ||
                        typeName == "java.lang.Float" ||
                        typeName == "java.lang.Boolean" ||
                        typeName == "java.lang.Character" ||
                        typeName == "java.lang.Byte" ||
                        typeName == "java.lang.Short"
                        )
    }

    /**
     * 提取方法调用链
     */
    fun extractMethodCallChain(
        startMethod: PsiMethod,
        maxDepth: Int = 10,
        visitedMethods: MutableSet<String> = mutableSetOf()
    ): List<MethodCall> {
        if (maxDepth <= 0) return emptyList()

        val methodCalls = mutableListOf<MethodCall>()
        val methodKey = "${startMethod.containingClass?.qualifiedName}.${startMethod.name}"

        if (methodKey in visitedMethods) {
            return methodCalls // 避免循环调用
        }
        visitedMethods.add(methodKey)

        if (startMethod.body == null) return methodCalls

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                try {
                    val resolvedMethod = expression.resolveMethod()
                    if (resolvedMethod != null) {
                        val calleeClass = resolvedMethod.containingClass
                        if (calleeClass != null && calleeClass.qualifiedName != null && !isExcludedType(calleeClass.qualifiedName!!)) {
                            val methodCall = MethodCall(
                                callerClass = startMethod.containingClass?.qualifiedName ?: "",
                                callerMethod = startMethod.name,
                                calleeClass = calleeClass.qualifiedName!!,
                                calleeMethod = resolvedMethod.name,
                                callType = determineCallType(expression),
                                location = createSourceLocation(expression),
                                callDepth = 10 - maxDepth
                            )
                            methodCalls.add(methodCall)

                            // 递归提取被调用方法的调用链
                            methodCalls.addAll(extractMethodCallChain(resolvedMethod, maxDepth - 1, visitedMethods))
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("JavaDependencyExtractor", "无法解析方法调用: ${expression.text}", mapOf("error" to e.message))
                }
            }
        }

        startMethod.body!!.accept(visitor)
        return methodCalls
    }

    /**
     * 确定调用类型
     */
    private fun determineCallType(expression: PsiMethodCallExpression): CallType {
        return when {
            expression.methodExpression.qualifier is PsiReferenceExpression -> {
                val qualifier = expression.methodExpression.qualifier as PsiReferenceExpression
                if (qualifier.resolve() is PsiClass) {
                    CallType.DIRECT
                } else {
                    CallType.INDIRECT
                }
            }
            expression.methodExpression.text == "forEach" ||
            expression.methodExpression.text == "map" ||
            expression.methodExpression.text == "filter" -> {
                CallType.STREAM
            }
            expression.methodExpression.text.matches(Regex("get|getMethod|invoke")) -> {
                CallType.REFLECTION
            }
            expression.parent is PsiLambdaExpression -> {
                CallType.LAMBDA
            }
            else -> CallType.DIRECT
        }
    }

    /**
     * 提取泛型类型信息
     */
    fun extractGenericTypeInfo(psiClass: PsiClass): Map<String, String> {
        return ReadAction.compute<Map<String, String>, Exception> {
            val genericTypes = mutableMapOf<String, String>()

            psiClass.typeParameters.forEach { typeParameter ->
                val typeName = typeParameter.name ?: return@forEach
                val extendsBound = typeParameter.extendsList
                    .referencedTypes
                    .firstOrNull()
                    ?.canonicalText
                    ?: "java.lang.Object"

                genericTypes[typeName] = extendsBound
            }

            genericTypes
        }
    }

    /**
     * 分析字段的使用情况
     */
    fun analyzeFieldUsage(psiClass: PsiClass): Map<String, List<PsiReference>> {
        return ReadAction.compute<Map<String, List<PsiReference>>, Exception> {
            val fieldUsage = mutableMapOf<String, MutableList<PsiReference>>()

            psiClass.fields.forEach { field ->
                fieldUsage[field.name] = mutableListOf()
            }

            // 搜索字段引用
            psiClass.methods.forEach { method ->
                method.body?.let { body ->
                    body.accept(object : JavaRecursiveElementVisitor() {
                        override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                            super.visitReferenceExpression(expression)
                            val fieldName = expression.referenceName
                            if (fieldName != null && fieldUsage.containsKey(fieldName)) {
                                fieldUsage[fieldName]?.add(expression)
                            }
                        }
                    })
                }
            }

            fieldUsage
        }
    }

    /**
     * 精确计算包级fanIn（传入耦合）
     * 统计有多少个其他包的类依赖当前包的类
     */
    fun calculatePackageFanIn(
        targetPackage: String,
        allClasses: List<PsiClass>,
        dependencies: Map<String, List<ClassReference>>
    ): Int {
        val targetPackageClasses = allClasses.filter { (it.containingFile as? PsiJavaFile)?.packageName == targetPackage }
        val targetPackageClassNames = targetPackageClasses.mapNotNull { it.qualifiedName }.toSet()

        var fanIn = 0

        allClasses.forEach { sourceClass ->
            val sourcePackage = (sourceClass.containingFile as? PsiJavaFile)?.packageName ?: ""

            if (sourcePackage != targetPackage) {
                val sourceDependencies = dependencies[sourceClass.qualifiedName] ?: emptyList()

                // 检查源类是否依赖目标包中的任何类
                val dependsOnTargetPackage = sourceDependencies.any { dependency ->
                    dependency.className in targetPackageClassNames
                }

                if (dependsOnTargetPackage) {
                    fanIn++
                }
            }
        }

        return fanIn
    }

    /**
     * 精确计算包级fanOut（传出耦合）
     * 统计当前包的类依赖多少个其他包的类
     */
    fun calculatePackageFanOut(
        targetPackage: String,
        allClasses: List<PsiClass>,
        dependencies: Map<String, List<ClassReference>>
    ): Int {
        val targetPackageClasses = allClasses.filter { (it.containingFile as? PsiJavaFile)?.packageName == targetPackage }
        val targetPackageClassNames = targetPackageClasses.mapNotNull { it.qualifiedName }.toSet()

        val dependentPackages = mutableSetOf<String>()

        targetPackageClassNames.forEach { className ->
            val classDependencies = dependencies[className] ?: emptyList()

            classDependencies.forEach { dependency ->
                val dependencyPackage = getPackageName(dependency.className)

                if (dependencyPackage != targetPackage && dependencyPackage != null) {
                    dependentPackages.add(dependencyPackage)
                }
            }
        }

        return dependentPackages.size
    }

    /**
     * 从类全限定名获取包名
     */
    private fun getPackageName(qualifiedName: String): String? {
        val lastDotIndex = qualifiedName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            qualifiedName.substring(0, lastDotIndex)
        } else {
            null
        }
    }

    /**
     * 优化的调用深度统计
     * 计算从给定方法开始的最大调用深度
     */
    fun calculateMaxCallDepth(
        startMethod: PsiMethod,
        visitedMethods: MutableSet<String> = mutableSetOf(),
        currentDepth: Int = 0
    ): Int {
        val methodKey = "${startMethod.containingClass?.qualifiedName}.${startMethod.name}"

        if (methodKey in visitedMethods || currentDepth > 20) {
            return currentDepth // 避免循环调用和过深递归
        }

        visitedMethods.add(methodKey)

        var maxDepth = currentDepth
        val methodCalls = extractDirectMethodCalls(startMethod)

        methodCalls.forEach { methodCall ->
            val calledMethod = resolveMethodFromCall(methodCall)
            if (calledMethod != null) {
                val depth = calculateMaxCallDepth(calledMethod, visitedMethods.toMutableSet(), currentDepth + 1)
                maxDepth = maxOf(maxDepth, depth)
            }
        }

        return maxDepth
    }

    /**
     * 提取方法中的直接方法调用（不递归）
     */
    private fun extractDirectMethodCalls(method: PsiMethod): List<PsiMethodCallExpression> {
        val methodCalls = mutableListOf<PsiMethodCallExpression>()

        method.body?.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                methodCalls.add(expression)
            }
        })

        return methodCalls
    }

    /**
     * 从方法调用表达式解析目标方法
     */
    private fun resolveMethodFromCall(callExpression: PsiMethodCallExpression): PsiMethod? {
        return try {
            callExpression.resolveMethod()
        } catch (e: Exception) {
            logger.debug("JavaDependencyExtractor", "无法解析方法调用", mapOf("error" to e.message))
            null
        }
    }

    /**
     * 分析调用链复杂度
     * 返回平均调用深度、最大调用深度、调用总数等指标
     */
    fun analyzeCallChainComplexity(
        startMethods: List<PsiMethod>,
        maxDepth: Int = 10
    ): CallChainComplexity {
        var totalCallDepth = 0
        var maxCallDepth = 0
        var totalMethodCalls = 0
        val uniqueMethodsCalled = mutableSetOf<String>()

        startMethods.forEach { startMethod ->
            val methodCalls = extractMethodCallChain(startMethod, maxDepth)

            totalMethodCalls += methodCalls.size
            uniqueMethodsCalled.addAll(methodCalls.map { "${it.calleeClass}.${it.calleeMethod}" })

            methodCalls.forEach { call ->
                totalCallDepth += call.callDepth
                maxCallDepth = maxOf(maxCallDepth, call.callDepth)
            }
        }

        val avgCallDepth = if (totalMethodCalls > 0) {
            totalCallDepth.toDouble() / totalMethodCalls
        } else {
            0.0
        }

        return CallChainComplexity(
            averageCallDepth = avgCallDepth,
            maxCallDepth = maxCallDepth,
            totalMethodCalls = totalMethodCalls,
            uniqueMethodsCalled = uniqueMethodsCalled.size
        )
    }

    /**
     * 构建包级依赖图
     */
    fun buildPackageDependencyGraph(
        allClasses: List<PsiClass>,
        dependencies: Map<String, List<ClassReference>>
    ): PackageDependencyGraph {
        return try {
            com.intellij.openapi.application.ReadAction.compute<PackageDependencyGraph, com.intellij.openapi.progress.ProcessCanceledException> {
                ProgressManager.checkCanceled()

                val packageDependencies = mutableMapOf<String, MutableSet<String>>()
                val packages = allClasses.mapNotNull {
                    ProgressManager.checkCanceled()
                    (it.containingFile as? PsiJavaFile)?.packageName
                }.distinct()

                packages.forEach { packageName ->
                    packageDependencies[packageName] = mutableSetOf()
                }

                dependencies.forEach { (sourceClass, classDeps) ->
                    ProgressManager.checkCanceled()
                    val sourcePackage = getPackageName(sourceClass)
                    if (sourcePackage != null) {
                        classDeps.forEach { dependency ->
                            ProgressManager.checkCanceled()
                            val targetPackage = getPackageName(dependency.className)
                            if (targetPackage != null && targetPackage != sourcePackage) {
                                packageDependencies[sourcePackage]?.add(targetPackage)
                            }
                        }
                    }
                }

                return@compute PackageDependencyGraph(
                    packages = packages,
                    dependencies = packageDependencies.mapValues { it.value.toList() },
                    fanIn = packages.associateWith { pkg ->
                        calculatePackageFanIn(pkg, allClasses, dependencies)
                    },
                    fanOut = packages.associateWith { pkg ->
                        calculatePackageFanOut(pkg, allClasses, dependencies)
                    }
                )
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            // 重新抛出取消异常
            throw e
        } catch (e: Exception) {
            logger.error("JavaDependencyExtractor", "构建包依赖图失败", error = e)
            // 返回空的包依赖图
            PackageDependencyGraph(
                packages = emptyList(),
                dependencies = emptyMap(),
                fanIn = emptyMap(),
                fanOut = emptyMap()
            )
        }
    }

    /**
     * 检测循环依赖
     */
    fun detectCircularDependencies(
        packageDependencies: PackageDependencyGraph
    ): List<List<String>> {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val cycles = mutableListOf<List<String>>()

        packageDependencies.dependencies.forEach { (sourcePackage, _) ->
            if (sourcePackage !in visited) {
                detectCircularDependenciesDFS(
                    sourcePackage,
                    packageDependencies.dependencies,
                    visited,
                    recursionStack,
                    mutableListOf(),
                    cycles
                )
            }
        }

        return cycles
    }

    /**
     * 使用DFS检测循环依赖
     */
    private fun detectCircularDependenciesDFS(
        currentPackage: String,
        dependencies: Map<String, List<String>>,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        currentPath: MutableList<String>,
        cycles: MutableList<List<String>>
    ) {
        visited.add(currentPackage)
        recursionStack.add(currentPackage)
        currentPath.add(currentPackage)

        dependencies[currentPackage]?.forEach { dependentPackage ->
            if (dependentPackage !in visited) {
                detectCircularDependenciesDFS(
                    dependentPackage,
                    dependencies,
                    visited,
                    recursionStack,
                    currentPath.toMutableList(),
                    cycles
                )
            } else if (dependentPackage in recursionStack) {
                // 找到循环依赖
                val cycleStartIndex = currentPath.indexOf(dependentPackage)
                val cycle = currentPath.subList(cycleStartIndex, currentPath.size) + dependentPackage
                cycles.add(cycle.distinct())
            }
        }

        recursionStack.remove(currentPackage)
        currentPath.removeLastOrNull()
    }
}

/**
 * 调用链复杂度
 */
data class CallChainComplexity(
    val averageCallDepth: Double,
    val maxCallDepth: Int,
    val totalMethodCalls: Int,
    val uniqueMethodsCalled: Int
)

/**
 * 包级依赖图
 */
data class PackageDependencyGraph(
    val packages: List<String>,
    val dependencies: Map<String, List<String>>,
    val fanIn: Map<String, Int>,
    val fanOut: Map<String, Int>
)