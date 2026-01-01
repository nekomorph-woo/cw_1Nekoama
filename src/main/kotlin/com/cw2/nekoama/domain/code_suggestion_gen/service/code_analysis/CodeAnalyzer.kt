package com.cw2.nekoama.domain.code_suggestion_gen.service.code_analysis

import com.intellij.psi.*
import com.cw2.nekoama.shared.model.Result
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.NamingConvention
import com.cw2.nekoama.domain.code_suggestion_gen.model.NamingPatternAnalysis
import com.cw2.nekoama.domain.code_suggestion_gen.model.ProgrammingLanguage
import com.cw2.nekoama.domain.code_suggestion_gen.model.ProjectMetadata
import com.cw2.nekoama.domain.code_suggestion_gen.model.SurroundingContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtParameter

/**
 * 代码分析器接口
 *
 * 定义了代码元素分析的通用接口，支持分析类、方法、变量等对象。
 * 实现了安全的 PSI 访问和对象支持。
 */
interface CodeAnalyzer {
    
    /**
     * 分析方法信息
     */
    fun analyzeMethod(method: PsiElement): Result<MethodContext>

    /**
     * 分析类信息
     */
    fun analyzeClass(clazz: PsiElement): Result<ClassContext>

    /**
     * 分析变量信息
     */
    fun analyzeVariable(variable: PsiElement): Result<VariableContext>

    /**
     * 获取周围代码
     */
    fun extractSurroundingContext(element: PsiElement, radius: Int = 5): Result<SurroundingContext>

    /**
     * 检测编程语言
     */
    fun detectLanguage(element: PsiElement): ProgrammingLanguage
}

/**
 * 通用代码分析器实现
 *
 * 提供了对 Java 和 Kotlin 代码的统一分析功能，
 * 包括详细的代码分析和日志记录。
 */
class UniversalCodeAnalyzer(
    private val project: Project
) : CodeAnalyzer {
    
    private val javaAnalyzer = JavaCodeAnalyzer(project)
    private val kotlinAnalyzer = KotlinCodeAnalyzer(project)
    
    override fun analyzeMethod(method: PsiElement): Result<MethodContext> {
        return try {
            when (val language = detectLanguage(method)) {
                ProgrammingLanguage.JAVA -> {
                    if (method is PsiMethod) {
                        javaAnalyzer.analyzeJavaMethod(method)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Java 方法"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    if (method is KtFunction) {
                        kotlinAnalyzer.analyzeKotlinFunction(method)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Kotlin 函数"))
                    }
                }
                else -> {
                    Result.error(NekoamaError.PlatformError.EditorUnavailable("不支持的编程语言: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeMethod", NekoamaError.Unknown("方法分析失败: ${e.message}"))
            Result.error(NekoamaError.Unknown("方法分析失败: ${e.message}"))
        }
    }
    
    override fun analyzeClass(clazz: PsiElement): Result<ClassContext> {
        return try {
            when (val language = detectLanguage(clazz)) {
                ProgrammingLanguage.JAVA -> {
                    if (clazz is PsiClass) {
                        javaAnalyzer.analyzeJavaClass(clazz)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Java 类"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    if (clazz is KtClass) {
                        kotlinAnalyzer.analyzeKotlinClass(clazz)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Kotlin 类"))
                    }
                }
                else -> {
                    Result.error(NekoamaError.PlatformError.EditorUnavailable("不支持的编程语言: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeClass", NekoamaError.Unknown("类分析失败: ${e.message}"))
            Result.error(NekoamaError.Unknown("类分析失败: ${e.message}"))
        }
    }
    
    override fun analyzeVariable(variable: PsiElement): Result<VariableContext> {
        return try {
            when (val language = detectLanguage(variable)) {
                ProgrammingLanguage.JAVA -> {
                    when (variable) {
                        is PsiVariable -> javaAnalyzer.analyzeJavaVariable(variable)
                        else -> Result.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Java 变量"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    when (variable) {
                        is KtProperty -> kotlinAnalyzer.analyzeKotlinProperty(variable)
                        is KtParameter -> kotlinAnalyzer.analyzeKotlinParameter(variable)
                        else -> Result.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Kotlin 变量"))
                    }
                }
                else -> {
                    Result.error(NekoamaError.PlatformError.EditorUnavailable("不支持的编程语言: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeVariable", NekoamaError.Unknown("变量分析失败: ${e.message}"))
            Result.error(NekoamaError.Unknown("变量分析失败: ${e.message}"))
        }
    }
    
    override fun extractSurroundingContext(element: PsiElement, radius: Int): Result<SurroundingContext> {
        return try {
            val containingFile = element.containingFile
            val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                ?: return Result.error(NekoamaError.PlatformError.EditorUnavailable("无法获取文档"))
            
            val elementStartOffset = element.textRange.startOffset
            val elementEndOffset = element.textRange.endOffset
            
            val startLine = document.getLineNumber(elementStartOffset)
            val endLine = document.getLineNumber(elementEndOffset)
            
            // 获取前置代码
            val precedingLines = mutableListOf<String>()
            for (i in maxOf(0, startLine - radius) until startLine) {
                val lineStartOffset = document.getLineStartOffset(i)
                val lineEndOffset = document.getLineEndOffset(i)
                precedingLines.add(document.text.substring(lineStartOffset, lineEndOffset).trim())
            }
            
            // 获取后续代码
            val followingLines = mutableListOf<String>()
            val totalLines = document.lineCount
            for (i in (endLine + 1)..minOf(totalLines - 1, endLine + radius)) {
                val lineStartOffset = document.getLineStartOffset(i)
                val lineEndOffset = document.getLineEndOffset(i)
                followingLines.add(document.text.substring(lineStartOffset, lineEndOffset).trim())
            }
            
            // 获取导入语句
            val imports = extractImportStatements(containingFile)
            
            // 获取包名
            val packageName = extractPackageDeclaration(containingFile)

            // 分析命名模式
            val namingPatterns = analyzeNamingPatterns(containingFile)

            val surroundingContext = SurroundingContext(
                precedingCode = precedingLines.filter { it.isNotBlank() },
                followingCode = followingLines.filter { it.isNotBlank() },
                imports = imports,
                packageDeclaration = packageName,
                namingPatterns = namingPatterns
            )
            
            Result.success(surroundingContext)
            
        } catch (e: Exception) {
            NekoamaLogger.logError("extractSurroundingContext", NekoamaError.Unknown("上下文提取失败: ${e.message}"))
            Result.error(NekoamaError.Unknown("上下文提取失败: ${e.message}"))
        }
    }
    
    override fun detectLanguage(element: PsiElement): ProgrammingLanguage {
        val file = element.containingFile
        return when {
            file.language.id == "JAVA" || file.name.endsWith(".java") -> ProgrammingLanguage.JAVA
            file.language.id == "kotlin" || file.name.endsWith(".kt") -> ProgrammingLanguage.KOTLIN
            else -> ProgrammingLanguage.OTHER
        }
    }
    
    /**
     * 获取导入语句
     */
    private fun extractImportStatements(file: PsiFile): List<String> {
        val imports = mutableListOf<String>()
        
        when (file) {
            is PsiJavaFile -> {
                file.importList?.allImportStatements?.forEach { importStatement ->
                    importStatement.importReference?.qualifiedName?.let { qualifiedName ->
                        imports.add("import $qualifiedName")
                    }
                }
            }
            is KtFile -> {
                file.importDirectives.forEach { importDirective ->
                    // K2-compatible approach: extract import from the directive text directly
                    importDirective.text.let { importText ->
                        if (importText.startsWith("import ") && !importText.contains("*")) {
                            imports.add(importText.trim())
                        }
                    }
                }
            }
        }
        
        return imports
    }
    
    /**
     * 获取包名
     */
    private fun extractPackageDeclaration(file: PsiFile): String? {
        return when (file) {
            is PsiJavaFile -> file.packageName.takeIf { it.isNotEmpty() }
            is KtFile -> file.packageDirective?.fqName?.asString()?.takeIf { it.isNotEmpty() }
            else -> null
        }
    }
    
    /**
     * 分析命名模式
     */
    private fun analyzeNamingPatterns(file: PsiFile): NamingPatternAnalysis {
        val names = mutableListOf<String>()

        // 收集文件中的标识符
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is PsiMethod -> names.add(element.name)
                    is PsiField -> names.add(element.name)
                    is PsiClass -> names.add(element.name ?: "")
                    is KtFunction -> names.add(element.name ?: "")
                    is KtProperty -> names.add(element.name ?: "")
                    is KtClass -> names.add(element.name ?: "")
                }
                super.visitElement(element)
            }
        })

        // 统计命名规范
        val camelCaseCount = names.count { name -> name.matches(Regex("[a-z][a-zA-Z0-9]*")) }
        val pascalCaseCount = names.count { name -> name.matches(Regex("[A-Z][a-zA-Z0-9]*")) }
        val snakeCaseCount = names.count { name -> name.contains("_") }

        val convention = when {
            pascalCaseCount > camelCaseCount && pascalCaseCount > snakeCaseCount -> NamingConvention.PASCAL_CASE
            snakeCaseCount > camelCaseCount && snakeCaseCount > pascalCaseCount -> NamingConvention.SNAKE_CASE
            camelCaseCount > 0 -> NamingConvention.CAMEL_CASE
            else -> NamingConvention.MIXED
        }

        return NamingPatternAnalysis(
            conventionType = convention
        )
    }

    /**
     * 获取项目信息
     */
    fun getProjectInfo(): ProjectMetadata {
        return ProjectMetadata(
            projectName = project.name
        )
    }
}
