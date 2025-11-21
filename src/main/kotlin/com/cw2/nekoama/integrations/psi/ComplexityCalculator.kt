package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.*
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.fileTypes.StdFileTypes

/**
 * 代码复杂度计算器
 * 严格按照 docs/Nekoama新功能-代码结构梳理和质量分析-方案.md 中的指标计算公式实现
 */
class ComplexityCalculator {

    private val logger = NekoamaLogger

    /**
     * 计算类的完整复杂度指标
     */
    fun calculateClassComplexityMetrics(psiClass: PsiClass): ClassComplexityMetrics {
        val methods = psiClass.methods
        val fields = psiClass.fields

        // 1. 计算圈复杂度
        val cyclomaticComplexity = calculateCyclomaticComplexity(psiClass)

        // 2. 计算认知复杂度
        val cognitiveComplexity = calculateCognitiveComplexity(psiClass)

        // 3. 计算最大嵌套深度
        val maxNestingDepth = calculateMaxNestingDepth(psiClass)

        // 4. 统计基础指标
        val methodCount = methods.size
        val fieldCount = fields.size
        val lineOfCode = countLinesOfCode(psiClass)
        val totalParameters = methods.sumOf { it.parameterList.parametersCount }

        // 5. 分析方法复杂度
        val methodComplexities = methods.map { method ->
            MethodComplexityInfo(
                methodName = method.name,
                complexity = calculateMethodCyclomaticComplexity(method),
                lineOfCode = countMethodLinesOfCode(method),
                parameterCount = method.parameterList.parametersCount,
                nestingDepth = calculateMethodNestingDepth(method)
            )
        }

        // 6. 精确统计各种指标
        val methodDetailedMetrics = methods.map { method ->
            MethodDetailedMetrics(
                methodName = method.name,
                complexity = calculateMethodCyclomaticComplexity(method),
                cognitiveComplexity = calculateMethodCognitiveComplexity(method),
                lineOfCode = countMethodLinesOfCode(method),
                parameterCount = method.parameterList.parametersCount,
                nestingDepth = calculateMethodNestingDepth(method),
                magicNumberCount = countMethodMagicNumbers(method),
                longLineCount = countMethodLongLines(method),
                returnStatementCount = countMethodReturnStatements(method),
                booleanParameterCount = countMethodBooleanParameters(method),
                localVariableCount = countMethodLocalVariables(method)
            )
        }

        val longestMethod = methodDetailedMetrics.maxByOrNull { it.lineOfCode }
            ?.let { MethodComplexityInfo(it.methodName, it.complexity, it.lineOfCode, it.parameterCount, it.nestingDepth) }
            ?: MethodComplexityInfo("", 0, 0, 0, 0)

        val mostComplexMethod = methodDetailedMetrics.maxByOrNull { it.complexity }
            ?.let { MethodComplexityInfo(it.methodName, it.complexity, it.lineOfCode, it.parameterCount, it.nestingDepth) }
            ?: MethodComplexityInfo("", 0, 0, 0, 0)

        // 7. 计算耦合度指标
        val couplingMetrics = calculateCouplingMetrics(psiClass)

        return ClassComplexityMetrics(
            className = psiClass.qualifiedName ?: "",
            cyclomaticComplexity = cyclomaticComplexity,
            cognitiveComplexity = cognitiveComplexity,
            nestingDepth = maxNestingDepth,
            methodCount = methodCount,
            fieldCount = fieldCount,
            lineOfCode = lineOfCode,
            parameterCount = totalParameters,
            longestMethod = longestMethod,
            mostComplexMethod = mostComplexMethod,
            couplingMetrics = couplingMetrics
        )
    }

    /**
     * 计算类的圈复杂度
     * 公式: V(G) = E - N + 2P
     * 其中 E = 边数量, N = 节点数量, P = 连通组件数
     * 简化实现: 基础复杂度1 + 判定点数量
     */
    fun calculateCyclomaticComplexity(psiClass: PsiClass): Int {
        return psiClass.methods.sumOf { calculateMethodCyclomaticComplexity(it) }
    }

    /**
     * 计算方法的圈复杂度
     */
    fun calculateMethodCyclomaticComplexity(method: PsiMethod): Int {
        var complexity = 1 // 基础复杂度

        val visitor = object : JavaRecursiveElementVisitor() {
            var nestingLevel = 0

            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity += 1
                if (statement.elseBranch != null && statement.elseBranch !is PsiIfStatement) {
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
                // for循环的初始化和更新部分可能包含额外的判断
                statement.initialization?.let { init ->
                    if (init is PsiBinaryExpression) {
                        complexity += countBinaryExpressionComplexity(init)
                    }
                }
                statement.update?.let { update ->
                    if (update is PsiBinaryExpression) {
                        complexity += countBinaryExpressionComplexity(update)
                    }
                }
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
                // 每个case标签增加1个复杂度
                val caseCount = statement.body?.children?.filterIsInstance<PsiSwitchLabelStatement>()?.size ?: 0
                complexity += caseCount
            }

            override fun visitCatchSection(section: PsiCatchSection) {
                super.visitCatchSection(section)
                complexity += 1
            }

            override fun visitConditionalExpression(expression: PsiConditionalExpression) {
                super.visitConditionalExpression(expression)
                complexity += 1
            }

            override fun visitBinaryExpression(expression: PsiBinaryExpression) {
                super.visitBinaryExpression(expression)
                complexity += countBinaryExpressionComplexity(expression)
            }

            override fun visitBreakStatement(statement: PsiBreakStatement) {
                super.visitBreakStatement(statement)
                // break无条件增加复杂度（打断线性流程）
                complexity += 1
            }

            override fun visitContinueStatement(statement: PsiContinueStatement) {
                super.visitContinueStatement(statement)
                // continue无条件增加复杂度（打断线性流程）
                complexity += 1
            }

            override fun visitReturnStatement(statement: PsiReturnStatement) {
                super.visitReturnStatement(statement)
                // 返回值中的条件表达式也增加复杂度
                statement.returnValue?.let { returnValue ->
                    if (returnValue is PsiConditionalExpression) {
                        complexity += 1
                    } else if (returnValue is PsiBinaryExpression) {
                        complexity += countBinaryExpressionComplexity(returnValue)
                    }
                }
            }
        }

        method.accept(visitor)
        return complexity
    }

    /**
     * 计算二元表达式的复杂度
     */
    private fun countBinaryExpressionComplexity(expression: PsiBinaryExpression): Int {
        var count = 0
        val operation = expression.operationTokenType

        when (operation) {
            JavaTokenType.ANDAND, JavaTokenType.OROR -> count += 1
            JavaTokenType.NE, JavaTokenType.EQEQ,
            JavaTokenType.LT, JavaTokenType.LE,
            JavaTokenType.GT, JavaTokenType.GE -> count += 1
        }

        // 递归处理嵌套的二元表达式
        expression.lOperand?.let { left ->
            if (left is PsiBinaryExpression) {
                count += countBinaryExpressionComplexity(left)
            }
        }
        expression.rOperand?.let { right ->
            if (right is PsiBinaryExpression) {
                count += countBinaryExpressionComplexity(right)
            }
        }

        return count
    }

    /**
     * 判断语句是否在循环中
     */
    private fun isInLoop(statement: PsiStatement): Boolean {
        var parent = statement.parent
        while (parent != null) {
            when (parent) {
                is PsiWhileStatement, is PsiForStatement,
                is PsiForeachStatement, is PsiDoWhileStatement -> return true
                is PsiMethod -> break
            }
            parent = parent.parent
        }
        return false
    }

    /**
     * 计算认知复杂度
     * 基于SonarQube认知复杂度算法
     */
    fun calculateCognitiveComplexity(psiClass: PsiClass): Int {
        return psiClass.methods.sumOf { calculateMethodCognitiveComplexity(it) }
    }

    /**
     * 计算方法的认知复杂度
     */
    fun calculateMethodCognitiveComplexity(method: PsiMethod): Int {
        var complexity = 0
        var nestingLevel = 0
        var isNested = false

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity += 1 + nestingLevel
                val oldNestingLevel = nestingLevel
                nestingLevel++

                // 处理then分支
                statement.thenBranch?.accept(this)

                // 处理else分支
                statement.elseBranch?.let { elseBranch ->
                    if (elseBranch is PsiIfStatement) {
                        // else-if不增加额外的嵌套级别
                        nestingLevel = oldNestingLevel
                    }
                    elseBranch.accept(this)
                }

                nestingLevel = oldNestingLevel
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                complexity += 1 + nestingLevel
                val oldNestingLevel = nestingLevel
                nestingLevel++
                super.visitWhileStatement(statement)
                nestingLevel = oldNestingLevel
            }

            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                complexity += 1 + nestingLevel
                val oldNestingLevel = nestingLevel
                nestingLevel++
                super.visitForStatement(statement)
                nestingLevel = oldNestingLevel
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                super.visitForeachStatement(statement)
                complexity += 1 + nestingLevel
                val oldNestingLevel = nestingLevel
                nestingLevel++
                super.visitForeachStatement(statement)
                nestingLevel = oldNestingLevel
            }

            override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
                super.visitDoWhileStatement(statement)
                complexity += 1 + nestingLevel
                val oldNestingLevel = nestingLevel
                nestingLevel++
                super.visitDoWhileStatement(statement)
                nestingLevel = oldNestingLevel
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                complexity += 1 + nestingLevel
                val oldNestingLevel = nestingLevel
                nestingLevel++
                super.visitSwitchStatement(statement)
                nestingLevel = oldNestingLevel
            }

            override fun visitTryStatement(statement: PsiTryStatement) {
                super.visitTryStatement(statement)
                val oldNestingLevel = nestingLevel
                nestingLevel++

                // try块
                statement.tryBlock?.accept(this)

                // catch块
                statement.catchBlocks.forEach { catchBlock ->
                    complexity += 1
                    catchBlock.accept(this)
                }

                // finally块
                statement.finallyBlock?.accept(this)

                nestingLevel = oldNestingLevel
            }

            override fun visitLambdaExpression(expression: PsiLambdaExpression) {
                super.visitLambdaExpression(expression)
                val oldNestingLevel = nestingLevel
                nestingLevel++
                super.visitLambdaExpression(expression)
                nestingLevel = oldNestingLevel
            }

            override fun visitBinaryExpression(expression: PsiBinaryExpression) {
                super.visitBinaryExpression(expression)
                val operation = expression.operationTokenType
                if (operation == JavaTokenType.ANDAND || operation == JavaTokenType.OROR) {
                    // 连续的AND/OR只计算一次
                    if (!isSequentialLogicalOperator(expression)) {
                        complexity += 1
                    }
                }
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                // 递归调用增加复杂度
                val resolvedMethod = expression.resolveMethod()
                if (resolvedMethod != null && resolvedMethod.name == method.name) {
                    complexity += 1
                }
            }
        }

        method.accept(visitor)
        return complexity
    }

    /**
     * 判断是否为连续的逻辑运算符
     */
    private fun isSequentialLogicalOperator(expression: PsiBinaryExpression): Boolean {
        val operation = expression.operationTokenType
        if (operation != JavaTokenType.ANDAND && operation != JavaTokenType.OROR) {
            return false
        }

        // 检查左操作数是否也是逻辑运算符
        return expression.lOperand is PsiBinaryExpression &&
                (expression.lOperand as PsiBinaryExpression).operationTokenType in listOf(
                    JavaTokenType.ANDAND, JavaTokenType.OROR
                )
    }

    /**
     * 计算最大嵌套深度
     */
    fun calculateMaxNestingDepth(psiClass: PsiClass): Int {
        return psiClass.methods.maxOfOrNull { calculateMethodNestingDepth(it) } ?: 0
    }

    /**
     * 计算方法的嵌套深度
     */
    fun calculateMethodNestingDepth(method: PsiMethod): Int {
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

            override fun visitLambdaExpression(expression: PsiLambdaExpression) {
                super.visitLambdaExpression(expression)
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                super.visitLambdaExpression(expression)
                currentDepth--
            }
        }

        method.accept(visitor)
        return maxDepth
    }

    /**
     * 统计代码行数（不包括空行和注释）
     */
    fun countLinesOfCode(psiClass: PsiClass): Int {
        val lines = psiClass.text.split('\n')
        return lines.count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
        }
    }

    /**
     * 统计方法的代码行数
     */
    fun countMethodLinesOfCode(method: PsiMethod): Int {
        return method.body?.let { body ->
            val lines = body.text.split('\n')
            lines.count { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
            }
        } ?: 0
    }

    /**
     * 计算耦合度指标
     */
    fun calculateCouplingMetrics(psiClass: PsiClass): CouplingMetrics {
        val className = psiClass.qualifiedName ?: ""

        // 计算传入耦合(Ca) - 依赖于当前类的类数量
        val afferentCoupling = calculateAfferentCoupling(psiClass)

        // 计算传出耦合(Ce) - 当前类依赖的类数量
        val efferentCoupling = calculateEfferentCoupling(psiClass)

        // 计算不稳定性 I = Ce / (Ca + Ce)
        val instability = if (afferentCoupling + efferentCoupling > 0) {
            efferentCoupling.toDouble() / (afferentCoupling + efferentCoupling)
        } else 0.0

        // 计算抽象性 A = Na / Nc
        val abstractness = if (psiClass.isInterface || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            1.0
        } else {
            0.0
        }

        // 计算距离 D = |A + I - 1|
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
     * 计算传入耦合(Afferent Coupling - Ca)
     * 统计项目中依赖当前类的其他类数量
     */
    private fun calculateAfferentCoupling(psiClass: PsiClass): Int {
        val project = psiClass.project
        val targetClassName = psiClass.qualifiedName ?: return 0

        // 获取项目中所有Java类
        val searchScope = GlobalSearchScope.projectScope(project)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)

        var afferentCount = 0

        try {
            // 搜索项目中的所有类
            val allClasses = mutableListOf<PsiClass>()

            // 搜索Java文件中的类
            FileTypeIndex.processFiles(
                StdFileTypes.JAVA,
                { virtualFile ->
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile
                    psiFile?.classes?.let { classes ->
                        allClasses.addAll(classes)
                    }
                    true
                },
                searchScope
            )

            // 检查每个类是否依赖目标类
            for (sourceClass in allClasses) {
                // 跳过目标类本身和测试类
                if (sourceClass.qualifiedName == targetClassName ||
                    sourceClass.qualifiedName?.contains(".test.") == true ||
                    sourceClass.qualifiedName?.contains(".tests.") == true) {
                    continue
                }

                // 检查sourceClass是否引用了targetClass
                if (hasDependencyOn(sourceClass, targetClassName)) {
                    afferentCount++
                }
            }

        } catch (e: Exception) {
            logger.warn("ComplexityCalculator", "计算传入耦合时出错: ${e.message}")
        }

        return afferentCount
    }

    /**
     * 检查一个类是否依赖指定的目标类
     */
    private fun hasDependencyOn(sourceClass: PsiClass, targetClassName: String): Boolean {
        try {
            // 检查继承关系
            sourceClass.superClass?.let { superClass ->
                if (superClass.qualifiedName == targetClassName) {
                    return true
                }
            }

            // 检查接口实现
            for (interfaceClass in sourceClass.interfaces) {
                if (interfaceClass.qualifiedName == targetClassName) {
                    return true
                }
            }

            // 检查字段类型
            for (field in sourceClass.fields) {
                if (field.type.canonicalText == targetClassName) {
                    return true
                }
            }

            // 检查方法参数和返回类型
            for (method in sourceClass.methods) {
                // 返回类型检查
                if (method.returnType?.canonicalText == targetClassName) {
                    return true
                }

                // 参数类型检查
                for (parameter in method.parameterList.parameters) {
                    if (parameter.type.canonicalText == targetClassName) {
                        return true
                    }
                }
            }

            // 检查方法体中的局部变量和方法调用
            for (method in sourceClass.methods) {
                method.body?.let { body ->
                    val hasReference = body.accept(object : JavaRecursiveElementVisitor() {
                        override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                            super.visitReferenceExpression(expression)
                            // 检查类型引用
                            expression.type?.let { type ->
                                if (type.canonicalText == targetClassName) {
                                    throw FoundDependencyException()
                                }
                            }
                        }

                        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                            super.visitMethodCallExpression(expression)
                            // 检查方法调用
                            expression.resolveMethod()?.containingClass?.let { methodClass ->
                                if (methodClass.qualifiedName == targetClassName) {
                                    throw FoundDependencyException()
                                }
                            }
                        }
                    })

                    // 如果访问过程中发现依赖，会抛出FoundDependencyException
                }
            }

        } catch (e: FoundDependencyException) {
            return true
        } catch (e: Exception) {
            logger.warn("ComplexityCalculator", "检查类依赖时出错: ${e.message}")
        }

        return false
    }

    /**
     * 自定义异常用于提前终止遍历
     */
    private class FoundDependencyException : RuntimeException()

    /**
     * 计算传出耦合
     */
    private fun calculateEfferentCoupling(psiClass: PsiClass): Int {
        val extractor = JavaDependencyExtractor()
        val dependencies = extractor.extractClassDependencies(psiClass)

        // 排除基本类型和框架类型
        return dependencies.count { dep ->
            !isFrameworkType(dep.className)
        }
    }

    /**
     * 判断是否为框架类型
     */
    private fun isFrameworkType(typeName: String): Boolean {
        return typeName.startsWith("java.") ||
                typeName.startsWith("javax.") ||
                typeName.startsWith("kotlin.") ||
                typeName.startsWith("org.springframework.") ||
                typeName.startsWith("lombok.")
    }

    /**
     * 计算LCOM4内聚度
     * LCOM4 = (P - 1) / (M - 1) 其中P是独立的方法分组数，M是方法数
     */
    fun calculateLcom4(psiClass: PsiClass): Double {
        val methods = psiClass.methods.filter { !it.isConstructor }
        if (methods.size <= 1) return 0.0

        val fields = psiClass.fields.map { it.name }.toSet()
        val methodFieldsMap = mutableMapOf<String, Set<String>>()

        // 分析每个方法访问的字段
        methods.forEach { method ->
            val accessedFields = mutableSetOf<String>()
            method.body?.let { body ->
                body.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                        super.visitReferenceExpression(expression)
                        val fieldName = expression.referenceName
                        if (fieldName != null && fieldName in fields) {
                            accessedFields.add(fieldName)
                        }
                    }
                })
            }
            methodFieldsMap[method.name] = accessedFields
        }

        // 构建字段访问图，计算独立分组数
        val visited = mutableSetOf<String>()
        var groupCount = 0

        methodFieldsMap.forEach { (methodName, fieldSet) ->
            if (methodName !in visited && fieldSet.isNotEmpty()) {
                groupCount++
                val group = mutableSetOf<String>()
                val queue = mutableListOf<String>()

                // BFS查找相关方法
                queue.add(methodName)
                while (queue.isNotEmpty()) {
                    val current = queue.removeAt(0)
                    if (current !in visited) {
                        visited.add(current)
                        group.add(current)

                        val currentFields = methodFieldsMap[current] ?: emptySet()
                        methodFieldsMap.forEach { (otherMethod, otherFields) ->
                            if (otherMethod !in visited && currentFields.intersect(otherFields).isNotEmpty()) {
                                queue.add(otherMethod)
                            }
                        }
                    }
                }
            }
        }

        val m = methods.size.toDouble()
        val p = groupCount.toDouble()
        return if (m > 1.0) (p - 1.0) / (m - 1.0) else 0.0
    }

    /**
     * 计算类的方法级别复杂度分布
     */
    fun calculateMethodComplexityDistribution(psiClass: PsiClass): Map<String, Int> {
        val distribution = mutableMapOf(
            "1-5" to 0,
            "6-10" to 0,
            "11-15" to 0,
            "16-20" to 0,
            "20+" to 0
        )

        psiClass.methods.forEach { method ->
            val complexity = calculateMethodCyclomaticComplexity(method)
            when {
                complexity <= 5 -> distribution["1-5"] = distribution["1-5"]!! + 1
                complexity <= 10 -> distribution["6-10"] = distribution["6-10"]!! + 1
                complexity <= 15 -> distribution["11-15"] = distribution["11-15"]!! + 1
                complexity <= 20 -> distribution["16-20"] = distribution["16-20"]!! + 1
                else -> distribution["20+"] = distribution["20+"]!! + 1
            }
        }

        return distribution
    }

    /**
     * 计算类的方法长度分布
     */
    fun calculateMethodLengthDistribution(psiClass: PsiClass): Map<String, Int> {
        val distribution = mutableMapOf(
            "1-10" to 0,
            "11-20" to 0,
            "21-30" to 0,
            "31-50" to 0,
            "50+" to 0
        )

        psiClass.methods.forEach { method ->
            val length = countMethodLinesOfCode(method)
            when {
                length <= 10 -> distribution["1-10"] = distribution["1-10"]!! + 1
                length <= 20 -> distribution["11-20"] = distribution["11-20"]!! + 1
                length <= 30 -> distribution["21-30"] = distribution["21-30"]!! + 1
                length <= 50 -> distribution["31-50"] = distribution["31-50"]!! + 1
                else -> distribution["50+"] = distribution["50+"]!! + 1
            }
        }

        return distribution
    }

    /**
     * 统计方法中的魔法数字数量
     * 标准：硬编码的数字/字符串字面量（排除0, 1, -1, 空字符串）
     */
    fun countMethodMagicNumbers(method: PsiMethod): Int {
        var count = 0

        method.body?.accept(object : JavaRecursiveElementVisitor() {
            override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                super.visitLiteralExpression(expression)
                val value = expression.value

                when (value) {
                    is Number -> {
                        val num = value.toInt()
                        // 排除常见的数字：0, 1, -1
                        if (num != 0 && num != 1 && num != -1) {
                            count++
                        }
                    }
                    is String -> {
                        // 排除空字符串和常见配置项
                        if (value.isNotEmpty() &&
                            !value.equals("true", ignoreCase = true) &&
                            !value.equals("false", ignoreCase = true) &&
                            !value.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$"))) { // 非纯标识符字符串
                            count++
                        }
                    }
                }
            }
        })

        return count
    }

    /**
     * 统计方法中的长行代码数量
     * 标准：单行代码超过120字符
     */
    fun countMethodLongLines(method: PsiMethod): Int {
        val document = com.intellij.psi.PsiDocumentManager.getInstance(method.project)
            .getDocument(method.containingFile) ?: return 0

        val body = method.body ?: return 0
        val startLine = document.getLineNumber(body.textRange.startOffset)
        val endLine = document.getLineNumber(body.textRange.endOffset)

        var longLineCount = 0
        for (lineNum in startLine..endLine) {
            val lineStart = document.getLineStartOffset(lineNum)
            val lineEnd = document.getLineEndOffset(lineNum)
            val lineText = document.getText(
                com.intellij.openapi.util.TextRange(lineStart, lineEnd)
            ).trim()

            if (lineText.length > 120 && !lineText.startsWith("//") && !lineText.startsWith("/*")) {
                longLineCount++
            }
        }

        return longLineCount
    }

    /**
     * 统计方法中的return语句数量
     */
    fun countMethodReturnStatements(method: PsiMethod): Int {
        var count = 0

        method.body?.accept(object : JavaRecursiveElementVisitor() {
            override fun visitReturnStatement(statement: PsiReturnStatement) {
                super.visitReturnStatement(statement)
                count++
            }
        })

        return count
    }

    /**
     * 统计方法中的boolean类型参数数量
     */
    fun countMethodBooleanParameters(method: PsiMethod): Int {
        return method.parameterList.parameters.count { parameter ->
            parameter.type.canonicalText == "boolean" ||
            parameter.type.canonicalText == "java.lang.Boolean"
        }
    }

    /**
     * 统计方法中的局部变量数量
     * 标准：方法体内声明的局部变量（不包括参数）
     */
    fun countMethodLocalVariables(method: PsiMethod): Int {
        var count = 0

        method.body?.accept(object : JavaRecursiveElementVisitor() {
            override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
                super.visitDeclarationStatement(statement)
                count += statement.declaredElements.size
            }
        })

        return count
    }

    /**
     * 方法详细指标数据类
     */
    data class MethodDetailedMetrics(
        val methodName: String,
        val complexity: Int,
        val cognitiveComplexity: Int,
        val lineOfCode: Int,
        val parameterCount: Int,
        val nestingDepth: Int,
        val magicNumberCount: Int,
        val longLineCount: Int,
        val returnStatementCount: Int,
        val booleanParameterCount: Int,
        val localVariableCount: Int
    )
}