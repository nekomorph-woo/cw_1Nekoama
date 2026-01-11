package com.cw2.nekoama.infrastructure.code_suggestion_gen.code_analysis

import com.intellij.psi.*
import com.cw2.nekoama.shared.model.NekoamaResult
import com.cw2.nekoama.shared.exception.NekoamaError
import com.cw2.nekoama.shared.logging.NekoamaLogger
import com.cw2.nekoama.domain.code_suggestion_gen.model.*
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeElementAnalyzer
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtParameter

/**
 * 通用代码元素分析器实现
 *
 * 提供了对 Java 和 Kotlin 代码的统一分析功能，
 * 包括详细的代码分析和日志记录。
 *
 * 这是 infrastructure 层的实现，通过 PSI 完成具体的代码分析工作。
 */
class UniversalCodeElementAnalyzer(
    private val project: Project
) : CodeElementAnalyzer {

    private val javaAnalyzer = JavaCodeAnalyzer(project)
    private val kotlinAnalyzer = KotlinCodeAnalyzer(project)

    override fun analyzeMethod(method: PsiElement): NekoamaResult<MethodContext> {
        return try {
            when (val language = detectLanguage(method)) {
                ProgrammingLanguage.JAVA -> {
                    if (method is PsiMethod) {
                        javaAnalyzer.analyzeJavaMethod(method)
                    } else {
                        NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Java 方法"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    if (method is KtFunction) {
                        kotlinAnalyzer.analyzeKotlinFunction(method)
                    } else {
                        NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Kotlin 函数"))
                    }
                }
                else -> {
                    NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("不支持的编程语言: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeMethod", NekoamaError.Unknown("方法分析失败: ${e.message}"))
            NekoamaResult.error(NekoamaError.Unknown("方法分析失败: ${e.message}"))
        }
    }

    override fun analyzeClass(clazz: PsiElement): NekoamaResult<ClassContext> {
        return try {
            when (val language = detectLanguage(clazz)) {
                ProgrammingLanguage.JAVA -> {
                    if (clazz is PsiClass) {
                        javaAnalyzer.analyzeJavaClass(clazz)
                    } else {
                        NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Java 类"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    if (clazz is KtClass) {
                        kotlinAnalyzer.analyzeKotlinClass(clazz)
                    } else {
                        NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Kotlin 类"))
                    }
                }
                else -> {
                    NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("不支持的编程语言: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeClass", NekoamaError.Unknown("类分析失败: ${e.message}"))
            NekoamaResult.error(NekoamaError.Unknown("类分析失败: ${e.message}"))
        }
    }

    override fun analyzeVariable(variable: PsiElement): NekoamaResult<VariableContext> {
        return try {
            when (val language = detectLanguage(variable)) {
                ProgrammingLanguage.JAVA -> {
                    when (variable) {
                        is PsiVariable -> javaAnalyzer.analyzeJavaVariable(variable)
                        else -> NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Java 变量"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    when (variable) {
                        is KtProperty -> kotlinAnalyzer.analyzeKotlinProperty(variable)
                        is KtParameter -> kotlinAnalyzer.analyzeKotlinParameter(variable)
                        else -> NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("元素不是有效的 Kotlin 变量"))
                    }
                }
                else -> {
                    NekoamaResult.error(NekoamaError.PlatformError.EditorUnavailable("不支持的编程语言: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeVariable", NekoamaError.Unknown("变量分析失败: ${e.message}"))
            NekoamaResult.error(NekoamaError.Unknown("变量分析失败: ${e.message}"))
        }
    }

    override fun extractSurroundingContext(element: PsiElement, radius: Int): NekoamaResult<SurroundingContext> {
        return try {
            // 使用 runReadAction 显式包装 PSI 访问，确保线程安全
            val surroundingContext = com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .runReadAction<SurroundingContext> {
                    val containingFile = element.containingFile
                    val namingPatterns = analyzeNamingPatterns(containingFile)
                    SurroundingContext(namingPatterns = namingPatterns)
                }

            NekoamaResult.success(surroundingContext)

        } catch (e: Exception) {
            NekoamaLogger.logError("extractSurroundingContext", NekoamaError.Unknown("上下文提取失败: ${e.message}"))
            NekoamaResult.error(NekoamaError.Unknown("上下文提取失败: ${e.message}"))
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

    override fun getProjectMetadata(): ProjectMetadata {
        return ProjectMetadata(
            projectName = project.name
        )
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
}
