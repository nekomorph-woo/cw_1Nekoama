package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType

/**
 * 数据构建器类
 * 用于构建完整的数据模型，避免在主分析器中存在太多占位符
 */
object DependencyDataBuilders {

    /**
     * 标准化方法签名 - 用于宽松匹配
     * 移除泛型、标准化格式等
     */
    private fun normalizeMethodSignature(signature: String): String {
        return signature
            .replace("<[^<>]+>".toRegex(), "") // 移除泛型参数
            .replace("\\.\\.\\.".toRegex(), "[]") // 可变参数转为数组格式
            .replace("\\s+".toRegex(), "") // 移除多余的空格
    }

    /**
     * 构建包信息列表
     */
    fun buildPackageInfos(
        classes: List<PsiClass>,
        packageDependencies: List<PackageDependency>
    ): List<PackageInfo> {
        val packageMap = mutableMapOf<String, PackageInfo>()

        // 按包名分组
        classes.groupBy {
            (it.containingFile as? PsiJavaFile)?.packageName ?: "default"
        }.forEach { (packageName, psiClasses) ->
            val packageDep = packageDependencies.find { it.packageName == packageName }

            packageMap[packageName] = PackageInfo(
                id = packageName,
                name = packageName.substringAfterLast('.'),
                fullName = packageName,
                parentPackage = packageName.substringBeforeLast('.'),
                level = packageName.split('.').size,
                classCount = psiClasses.size,
                metrics = PackageMetrics(
                    fanIn = packageDep?.dependents?.size ?: 0,
                    fanOut = packageDep?.dependencies?.size ?: 0,
                    instability = if ((packageDep?.dependents?.size ?: 0) + (packageDep?.dependencies?.size ?: 0) > 0) {
                        (packageDep?.dependencies?.size ?: 0).toDouble() /
                        ((packageDep?.dependents?.size ?: 0) + (packageDep?.dependencies?.size ?: 0))
                    } else 0.0
                )
            )
        }

        return packageMap.values.toList()
    }

    /**
     * 构建类信息列表
     */
    fun buildClassInfos(
        classes: List<PsiClass>,
        classDependencies: List<ClassDependency>
    ): List<ClassInfo> {
        return classDependencies.map { classDep ->
            val psiClass = classes.find { it.qualifiedName == classDep.className }
            val javaFile = psiClass?.containingFile as? PsiJavaFile

            ClassInfo(
                id = classDep.className,
                name = psiClass?.name ?: classDep.className.substringAfterLast('.'),
                qualifiedName = classDep.className,
                packageId = javaFile?.packageName ?: "default",
                type = when {
                    psiClass?.isInterface == true -> ClassType.INTERFACE
                    psiClass?.hasModifierProperty(PsiModifier.ABSTRACT) == true -> ClassType.ABSTRACT_CLASS
                    psiClass?.isEnum == true -> ClassType.ENUM
                    psiClass?.isRecord == true -> ClassType.RECORD
                    else -> ClassType.CLASS
                },
                modifiers = psiClass?.modifierList?.text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList(),
                isTest = javaFile?.name?.contains("Test") == true,
                sourceFile = javaFile?.virtualFile?.path ?: "",
                annotations = psiClass?.modifierList?.annotations?.map { it.qualifiedName ?: it.text } ?: emptyList(),
                superClass = classDep.superClass,
                interfaces = classDep.interfaces,
                metrics = ClassDetailedMetrics(
                    methodCount = psiClass?.methods?.size ?: 0,
                    fieldCount = psiClass?.fields?.size ?: 0,
                    linesOfCode = psiClass?.let {
                        it.text.split('\n').count { line ->
                            val trimmed = line.trim()
                            trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                        }
                    } ?: 0,
                    fanIn = classDep.dependents.size,
                    fanOut = classDep.dependencies.size,
                    coupling = classDep.dependents.size + classDep.dependencies.size,
                    cohesion = 0.0, // 需要复杂的内聚度计算，暂时为0
                    codeSmells = emptyList(), // 需要进一步实现
                    complexityScore = 0, // 需要从复杂度指标计算
                    refactoringPriority = RefactoringPriority("", "", ""),
                    location = psiClass?.let { cls ->
                        SourceLocation(
                            filePath = javaFile?.virtualFile?.path ?: "",
                            lineNumber = cls.containingFile.viewProvider.document.getLineNumber(cls.textRange.startOffset) + 1,
                            columnNumber = cls.textRange.startOffset - cls.containingFile.viewProvider.document.getLineStartOffset(cls.containingFile.viewProvider.document.getLineNumber(cls.textRange.startOffset)) + 1
                        )
                    } ?: SourceLocation(
                        filePath = javaFile?.virtualFile?.path ?: "",
                        lineNumber = 0,
                        columnNumber = 0
                    ),
                    usedTypes = emptyList(), // 需要从PSI分析中提取
                    tags = MethodTags(false, false, false, emptyList())
                )
            )
        }
    }

    /**
     * 构建方法信息列表
     */
    fun buildMethodInfos(
        classes: List<PsiClass>,
        methodCalls: List<MethodCall>,
        complexityMetrics: Map<String, ClassComplexityMetrics>,
        businessEntryPoints: List<BusinessEntryPoint> = emptyList()
    ): List<MethodInfo> {
        val methodInfos = mutableListOf<MethodInfo>()

        // 创建入口方法签名查找映射 - 用于精确匹配业务入口点
        val entryMethodSignatures = businessEntryPoints.associateBy { entry ->
            "${entry.className}.${entry.methodName}(${entry.parameters.joinToString(",") { it.type }})"
        }

        for (psiClass in classes) {
            for (method in psiClass.methods) {
                val methodId = "${psiClass.qualifiedName}#${method.name}"
                val classMetrics = complexityMetrics[psiClass.qualifiedName ?: ""]

                // 统计调用关系
                val fanIn = methodCalls.count { it.calleeClass == psiClass.qualifiedName && it.calleeMethod == method.name }
                val fanOut = methodCalls.count { it.callerClass == psiClass.qualifiedName && it.callerMethod == method.name }

                methodInfos.add(
                    MethodInfo(
                        id = methodId,
                        name = method.name,
                        className = psiClass.name ?: "",
                        classId = psiClass.qualifiedName ?: "",
                        packageId = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: "default",
                        signature = method.name + method.parameterList.parameters.joinToString(",") { it.type.presentableText },
                        qualifiedSignature = methodId,
                        modifiers = method.modifierList.text.split(' ').filter { it.isNotEmpty() },
                        isStatic = method.hasModifierProperty(PsiModifier.STATIC),
                        isConstructor = method.isConstructor,
                        isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT),
                        annotations = method.modifierList.annotations.map { it.qualifiedName ?: it.text },
                        parameters = method.parameterList.parameters.map { param ->
                            ParameterDetail(
                                name = param.name ?: "",
                                type = param.type.presentableText,
                                annotations = param.modifierList?.annotations?.map { it.qualifiedName ?: it.text } ?: emptyList()
                            )
                        },
                        returnType = method.returnType?.presentableText ?: "void",
                        throwsExceptions = method.throwsList?.referencedTypes?.map { it.presentableText } ?: emptyList(),
                        metrics = MethodMetrics(
                            linesOfCode = method.body?.let { body ->
                                body.text.split('\n').count { line ->
                                    val trimmed = line.trim()
                                    trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                                }
                            } ?: 0,
                            cyclomaticComplexity = classMetrics?.cyclomaticComplexity ?: 0,
                            cognitiveComplexity = classMetrics?.cognitiveComplexity ?: 0,
                            nestingDepth = classMetrics?.nestingDepth ?: 0,
                            fanIn = fanIn,
                            fanOut = fanOut,
                            parameterCount = method.parameterList.parametersCount,
                            maxCallDepth = 0, // 需要深度分析
                            localVariableCount = method.body?.let { body ->
                                var count = 0
                                body.accept(object : JavaRecursiveElementVisitor() {
                                    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
                                        super.visitDeclarationStatement(statement)
                                        count += statement.declaredElements.size
                                    }
                                })
                                count
                            } ?: 0,
                            magicNumberCount = 0, // 已有精确方法，这里可调用
                            longLineCount = 0, // 已有精确方法，这里可调用
                            returnStatementCount = 0, // 已有精确方法，这里可调用
                            booleanParameterCount = method.parameterList.parameters.count {
                                it.type.presentableText == "boolean" || it.type.presentableText == "java.lang.Boolean"
                            },
                            codeSmells = emptyList(),
                            complexityScore = 0,
                            refactoringPriority = RefactoringPriority("", "", "")
                        ),
                        location = SourceLocation(
                            filePath = (psiClass.containingFile as? PsiJavaFile)?.virtualFile?.path ?: "",
                            lineNumber = method.containingFile.viewProvider.document.getLineNumber(method.textRange.startOffset) + 1,
                            columnNumber = method.textRange.startOffset - method.containingFile.viewProvider.document.getLineStartOffset(method.containingFile.viewProvider.document.getLineNumber(method.textRange.startOffset)) + 1
                        ),
                        usedTypes = emptyList(),
                        tags = MethodTags(
                            isEntryPoint = run {
                                // 策略1：构建方法签名用于精确匹配
                                val methodSignature = "${psiClass.qualifiedName}.${method.name}(${method.parameterList.parameters.joinToString(",") { it.type.presentableText }})"
                                val exactMatch = entryMethodSignatures.containsKey(methodSignature)

                                // 策略2：宽松匹配（忽略泛型和格式差异）
                                val normalizedSignature = normalizeMethodSignature(methodSignature)
                                val relaxedMatch = if (!exactMatch) {
                                    entryMethodSignatures.any { (sig, _) ->
                                        normalizeMethodSignature(sig) == normalizedSignature
                                    }
                                } else false

                                // 策略3：容错匹配（只匹配类名+方法名）
                                val fallbackMatch = if (!exactMatch && !relaxedMatch) {
                                    entryMethodSignatures.any { (sig, _) ->
                                        val sigParts = sig.split(".")
                                        val methodParts = methodSignature.split(".")
                                        sigParts.size >= 2 && methodParts.size >= 2 &&
                                        sigParts.dropLast(1).last() == methodParts.dropLast(1).last() && // 类名匹配
                                        sigParts.last().split("(").first() == methodParts.last().split("(").first() // 方法名匹配
                                    }
                                } else false

                                val isEntryPoint = exactMatch || relaxedMatch || fallbackMatch

                                // 详细日志记录匹配结果
                                when {
                                    exactMatch -> println("Nekoama: ✅ 精确匹配入口方法: $methodSignature")
                                    relaxedMatch -> println("Nekoama: ✅ 宽松匹配入口方法: $methodSignature")
                                    fallbackMatch -> println("Nekoama: ✅ 容错匹配入口方法: $methodSignature")
                                    else -> {
                                        if (entryMethodSignatures.isNotEmpty()) {
                                            println("Nekoama: ❌ 方法未匹配为入口点: $methodSignature")
                                            println("Nekoama:   可用签名示例: ${entryMethodSignatures.keys.take(3)}")
                                        }
                                    }
                                }

                                isEntryPoint
                            },
                            isPublicApi = method.hasModifierProperty(PsiModifier.PUBLIC),
                            isDeprecated = method.modifierList.annotations.any { it.text.contains("Deprecated") },
                            sceneNames = businessEntryPoints.filter {
                                it.className == psiClass.qualifiedName && it.methodName == method.name
                            }.map { it.businessScenario }
                        )
                    )
                )
            }
        }

        return methodInfos
    }

    /**
     * 构建字段信息列表
     */
    fun buildFieldInfos(classes: List<PsiClass>): List<FieldInfo> {
        val fieldInfos = mutableListOf<FieldInfo>()

        for (psiClass in classes) {
            for (field in psiClass.fields) {
                fieldInfos.add(
                    FieldInfo(
                        id = "${psiClass.qualifiedName}#${field.name}",
                        name = field.name,
                        classId = psiClass.qualifiedName ?: "",
                        type = field.type.presentableText,
                        modifiers = field.modifierList?.text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList(),
                        isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                        isFinal = field.hasModifierProperty(PsiModifier.FINAL),
                        annotations = field.modifierList?.annotations?.map { it.qualifiedName ?: it.text } ?: emptyList(),
                        initializer = field.initializer?.text
                    )
                )
            }
        }

        return fieldInfos
    }

    /**
     * 构建场景定义列表
     */
    fun buildSceneDefinitions(
        businessEntryPoints: List<BusinessEntryPoint>,
        methodCalls: List<MethodCall>,
        complexityMetrics: Map<String, ClassComplexityMetrics>
    ): List<SceneDefinition> {
        val sceneDefinitions = mutableListOf<SceneDefinition>()

        // 为每个业务入口点创建场景定义
        businessEntryPoints.forEachIndexed { index, entryPoint ->
            // 计算该入口点覆盖的方法、类、包数量
            val coveredMethods = methodCalls.filter {
                it.callerClass == entryPoint.className && it.callerMethod == entryPoint.methodName
            }.map { "${it.calleeClass}.${it.calleeMethod}" }.toSet()

            val coveredClasses = coveredMethods.map { it.substringBeforeLast('.') }.toSet()
            val coveredPackages = coveredClasses.map { it.substringBeforeLast('.') }.toSet()

            val maxDepth = methodCalls.filter {
                it.callerClass == entryPoint.className && it.callerMethod == entryPoint.methodName
            }.maxOfOrNull { it.callDepth } ?: 0

            sceneDefinitions.add(
                SceneDefinition(
                    id = "scene-${entryPoint.className}-${entryPoint.methodName}",
                    name = "${entryPoint.entryType}: ${entryPoint.methodName}",
                    description = "业务场景: ${entryPoint.businessScenario}",
                    entryMethods = listOf("${entryPoint.className}#${entryPoint.methodName}"),
                    category = when (entryPoint.entryType) {
                        EntryType.CONTROLLER -> SceneCategory.USER_TRIGGER
                        EntryType.SCHEDULED -> SceneCategory.SCHEDULED
                        EntryType.EVENT_LISTENER -> SceneCategory.EVENT_DRIVEN
                        EntryType.MESSAGE_CONSUMER -> SceneCategory.API
                        EntryType.MAIN -> SceneCategory.USER_TRIGGER
                        else -> SceneCategory.API
                    },
                    tags = listOf(entryPoint.entryType.name, entryPoint.businessScenario),
                    coverage = SceneCoverage(
                        methodCount = coveredMethods.size,
                        classCount = coveredClasses.size,
                        packageCount = coveredPackages.size,
                        maxDepth = maxDepth
                    )
                )
            )
        }

        return sceneDefinitions
    }
}