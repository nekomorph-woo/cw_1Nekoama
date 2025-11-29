package com.cw2.nekoama.core.unusedcode

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Document
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * 未使用代码分析器
 *
 * 功能：
 * - 扫描项目中未使用的类、方法、属性
 * - 基于PSI进行静态分析
 * - 支持进度报告
 */
class UnusedCodeAnalyzer(private val project: Project) {

    private val logger = NekoamaLogger
    private val usageTracker = UsageTracker()

    /**
     * 执行未使用代码分析
     */
    fun analyzeUnusedCode(indicator: ProgressIndicator): UnusedCodeAnalysisResult {
        logger.info("UnusedCodeAnalyzer", "开始未使用代码分析")

        indicator.text = "正在扫描项目文件..."
        indicator.fraction = 0.1

        // 获取所有Java/Kotlin文件
        val psiFiles = com.intellij.openapi.application.ReadAction.compute<List<PsiFile>, com.intellij.openapi.progress.ProcessCanceledException> {
            getAllJavaKotlinFiles(indicator)
        }

        indicator.text = "正在构建使用关系..."
        indicator.fraction = 0.3

        // 构建使用关系
        val usageInfo = com.intellij.openapi.application.ReadAction.compute<UsageInfo, com.intellij.openapi.progress.ProcessCanceledException> {
            usageTracker.buildUsageInfo(psiFiles)
        }

        indicator.text = "正在分析未使用代码..."
        indicator.fraction = 0.6

        // 分析未使用的元素
        val (unusedClasses, unusedMethods, unusedFields) = com.intellij.openapi.application.ReadAction.compute<Triple<List<UnusedClass>, List<UnusedMethod>, List<UnusedField>>, com.intellij.openapi.progress.ProcessCanceledException> {
            Triple(
                findUnusedClasses(usageInfo),
                findUnusedMethods(usageInfo),
                findUnusedFields(usageInfo)
            )
        }

        indicator.text = "正在生成分析结果..."
        indicator.fraction = 0.9

        val result = UnusedCodeAnalysisResult(
            unusedClasses = unusedClasses,
            unusedMethods = unusedMethods,
            unusedFields = unusedFields,
            totalClasses = usageInfo.allClasses.size,
            totalMethods = usageInfo.allMethods.size,
            totalFields = usageInfo.allFields.size
        )

        logger.info("UnusedCodeAnalyzer", "未使用代码分析完成: ${result.unusedClasses.size} 个类, ${result.unusedMethods.size} 个方法, ${result.unusedFields.size} 个属性")
        return result
    }

    /**
     * 获取所有Java/Kotlin文件
     */
    private fun getAllJavaKotlinFiles(indicator: ProgressIndicator): List<PsiFile> {
        val allFiles = mutableListOf<PsiFile>()

        // 获取Java文件
        val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))
            .mapNotNull { virtualFile -> PsiManager.getInstance(project).findFile(virtualFile) }

        // 获取Kotlin文件
        val kotlinFiles = FilenameIndex.getAllFilesByExt(project, "kt", GlobalSearchScope.projectScope(project))
            .mapNotNull { virtualFile -> PsiManager.getInstance(project).findFile(virtualFile) }

        allFiles.addAll(javaFiles)
        allFiles.addAll(kotlinFiles)

        indicator.fraction = 0.25
        return allFiles
    }

    /**
     * 查找未使用的类
     */
    private fun findUnusedClasses(usageInfo: UsageInfo): List<UnusedClass> {
        return usageInfo.allClasses.filter { psiClass ->
            val className = psiClass.qualifiedName ?: return@filter false

            // 跳过特殊类
            if (isSpecialClass(className, psiClass)) return@filter false

            // 检查是否被使用
            !usageInfo.isClassUsed(className)
        }.map { psiClass ->
            val className = psiClass.qualifiedName ?: ""
            val location = "${psiClass.containingFile.virtualFile.path}:${psiClass.textOffset}"
            val filePath = psiClass.containingFile.virtualFile.path

            // 计算类行数
            val lineCount = psiClass.text.lines().size

            UnusedClass(
                className = className,
                location = location,
                filePath = filePath,
                lineCount = lineCount
            )
        }
    }

    /**
     * 查找未使用的方法
     */
    private fun findUnusedMethods(usageInfo: UsageInfo): List<UnusedMethod> {
        return usageInfo.allMethods.filter { psiMethod ->
            val methodKey = getMethodKey(psiMethod) ?: return@filter false

            // 跳过特殊方法
            if (isSpecialMethod(psiMethod)) return@filter false

            // 检查是否被使用
            !usageInfo.isMethodUsed(methodKey)
        }.map { psiMethod ->
            val methodKey = getMethodKey(psiMethod) ?: ""
            val className = psiMethod.containingClass?.qualifiedName ?: ""
            val methodName = psiMethod.name
            val location = "${psiMethod.containingFile.virtualFile.path}:${psiMethod.textOffset}"
            val filePath = psiMethod.containingFile.virtualFile.path
            val lineNumber = getLineNumber(psiMethod)

            // 简单的复杂度计算
            val complexity = calculateMethodComplexity(psiMethod)

            UnusedMethod(
                className = className,
                methodName = methodName,
                location = location,
                filePath = filePath,
                lineNumber = lineNumber,
                complexity = complexity
            )
        }
    }

    /**
     * 查找未使用的属性
     */
    private fun findUnusedFields(usageInfo: UsageInfo): List<UnusedField> {
        return usageInfo.allFields.filter { psiField ->
            val fieldKey = getFieldKey(psiField) ?: return@filter false

            // 跳过特殊属性
            if (isSpecialField(psiField)) return@filter false

            // 检查是否被使用
            !usageInfo.isFieldUsed(fieldKey)
        }.map { psiField ->
            val fieldKey = getFieldKey(psiField) ?: ""
            val className = psiField.containingClass?.qualifiedName ?: ""
            val fieldName = psiField.name
            val location = "${psiField.containingFile.virtualFile.path}:${psiField.textOffset}"
            val filePath = psiField.containingFile.virtualFile.path
            val lineNumber = getLineNumber(psiField)
            val fieldType = psiField.type.presentableText

            UnusedField(
                className = className,
                fieldName = fieldName,
                location = location,
                filePath = filePath,
                lineNumber = lineNumber,
                fieldType = fieldType
            )
        }
    }

    /**
     * 判断是否为特殊类（应跳过分析）
     */
    private fun isSpecialClass(className: String, psiClass: PsiClass): Boolean {
        return psiClass.isInterface ||
                psiClass.isEnum ||
                psiClass.isAnnotationType ||
                psiClass.hasModifierProperty(PsiModifier.ABSTRACT) ||
                className.startsWith("java.") ||
                className.startsWith("kotlin.") ||
                className.startsWith("javax.") ||
                className.contains("Test") ||
                className.endsWith("Test") ||
                className.endsWith("Tests") ||
                psiClass.name?.startsWith("Main") == true
    }

    /**
     * 判断是否为特殊方法（应跳过分析）
     */
    private fun isSpecialMethod(psiMethod: PsiMethod): Boolean {
        return psiMethod.hasModifierProperty(PsiModifier.STATIC) ||
                psiMethod.hasModifierProperty(PsiModifier.PRIVATE) ||
                psiMethod.hasModifierProperty(PsiModifier.PROTECTED) ||
                psiMethod.name == "main" ||
                psiMethod.name.startsWith("test") ||
                psiMethod.hasAnnotation("Test") ||
                psiMethod.hasAnnotation("org.junit.Test") ||
                psiMethod.hasAnnotation("org.junit.jupiter.api.Test") ||
                psiMethod.isConstructor ||
                psiMethod.findSuperMethods().isNotEmpty()
    }

    /**
     * 判断是否为特殊属性（应跳过分析）
     */
    private fun isSpecialField(psiField: PsiField): Boolean {
        return psiField.hasModifierProperty(PsiModifier.STATIC) ||
                psiField.hasModifierProperty(PsiModifier.PRIVATE) ||
                psiField.hasModifierProperty(PsiModifier.PROTECTED) ||
                psiField.hasModifierProperty(PsiModifier.FINAL) ||
                psiField.hasAnnotation("Test") ||
                psiField.name.startsWith("test")
    }

    /**
     * 获取方法的唯一标识符
     */
    private fun getMethodKey(psiMethod: PsiMethod): String? {
        val className = psiMethod.containingClass?.qualifiedName ?: return null
        val methodName = psiMethod.name
        val paramTypes = psiMethod.parameterList.parameters.joinToString(",") { it.type.presentableText }
        return "$className.$methodName($paramTypes)"
    }

    /**
     * 获取属性的唯一标识符
     */
    private fun getFieldKey(psiField: PsiField): String? {
        val className = psiField.containingClass?.qualifiedName ?: return null
        val fieldName = psiField.name
        return "$className.$fieldName"
    }

    /**
     * 获取元素的行号
     */
    private fun getLineNumber(element: PsiElement): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
        return document?.getLineNumber(element.textOffset)?.plus(1) ?: 0
    }

    /**
     * 简单计算方法复杂度
     */
    private fun calculateMethodComplexity(psiMethod: PsiMethod): Int {
        var complexity = 1 // 基础复杂度

        val visitor = object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                complexity += 2
                super.visitIfStatement(statement)
            }

            override fun visitForStatement(statement: PsiForStatement) {
                complexity += 2
                super.visitForStatement(statement)
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                complexity += 2
                super.visitWhileStatement(statement)
            }

            override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
                complexity += 2
                super.visitDoWhileStatement(statement)
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                complexity += 2
                super.visitSwitchStatement(statement)
            }

            override fun visitConditionalExpression(expression: PsiConditionalExpression) {
                complexity += 1
                super.visitConditionalExpression(expression)
            }
        }

        psiMethod.accept(visitor)
        return complexity
    }

    /**
     * 使用信息追踪器
     */
    private inner class UsageTracker {
        val allClasses = mutableListOf<PsiClass>()
        val allMethods = mutableListOf<PsiMethod>()
        val allFields = mutableListOf<PsiField>()

        private val classUsage = ConcurrentHashMap<String, Int>()
        private val methodUsage = ConcurrentHashMap<String, Int>()
        private val fieldUsage = ConcurrentHashMap<String, Int>()

        fun buildUsageInfo(psiFiles: List<PsiFile>): UsageInfo {
            // 收集所有元素
            psiFiles.forEach { file ->
                PsiTreeUtil.processElements(file) { element ->
                    when (element) {
                        is PsiClass -> allClasses.add(element)
                        is PsiMethod -> allMethods.add(element)
                        is PsiField -> allFields.add(element)
                        is PsiReferenceExpression -> trackReference(element)
                    }
                    true // 继续处理
                }
            }

            return UsageInfo(allClasses, allMethods, allFields, classUsage, methodUsage, fieldUsage)
        }

        private fun trackReference(expression: PsiReferenceExpression) {
            val resolved = expression.resolve() ?: return

            when (resolved) {
                is PsiClass -> {
                    val className = resolved.qualifiedName ?: return
                    classUsage.merge(className, 1) { old, _ -> old + 1 }
                }
                is PsiMethod -> {
                    val methodKey = getMethodKey(resolved) ?: return
                    methodUsage.merge(methodKey, 1) { old, _ -> old + 1 }
                }
                is PsiField -> {
                    val fieldKey = getFieldKey(resolved) ?: return
                    fieldUsage.merge(fieldKey, 1) { old, _ -> old + 1 }
                }
            }
        }
    }

    /**
     * 使用信息数据类
     */
    private data class UsageInfo(
        val allClasses: List<PsiClass>,
        val allMethods: List<PsiMethod>,
        val allFields: List<PsiField>,
        val classUsage: Map<String, Int>,
        val methodUsage: Map<String, Int>,
        val fieldUsage: Map<String, Int>
    ) {
        fun isClassUsed(className: String): Boolean = classUsage.containsKey(className)
        fun isMethodUsed(methodKey: String): Boolean = methodUsage.containsKey(methodKey)
        fun isFieldUsed(fieldKey: String): Boolean = fieldUsage.containsKey(fieldKey)
    }
}