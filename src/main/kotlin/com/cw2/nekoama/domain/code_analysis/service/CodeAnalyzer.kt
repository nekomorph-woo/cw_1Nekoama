package com.cw2.nekoama.domain.code_analysis.service

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
 * ����������ӿ�
 * 
 * �����˴���Ԫ�ط����ĺ��Ľӿڣ�֧�ַ������ࡢ��������ȷ�����
 * ʵ���˰�ȫ�� PSI �����Ͷ�����֧�֡�
 */
interface CodeAnalyzer {
    
    /**
     * ����������Ϣ
     */
    fun analyzeMethod(method: PsiElement): Result<MethodContext>
    
    /**
     * ��������Ϣ
     */
    fun analyzeClass(clazz: PsiElement): Result<ClassContext>
    
    /**
     * ����������Ϣ
     */
    fun analyzeVariable(variable: PsiElement): Result<VariableContext>
    
    /**
     * ��ȡ��Χ������
     */
    fun extractSurroundingContext(element: PsiElement, radius: Int = 5): Result<SurroundingContext>
    
    /**
     * ���������
     */
    fun detectLanguage(element: PsiElement): ProgrammingLanguage
}

/**
 * ͨ�ô��������ʵ��
 * 
 * �ṩ�˶� Java �� Kotlin �����ͳһ�������ܣ�
 * ������ϸ�Ĵ���������־��¼��
 */
class UniversalCodeAnalyzer(
    private val project: Project
) : CodeAnalyzer {
    
    private val javaAnalyzer = JavaCodeAnalyzer(project)
    private val kotlinAnalyzer = KotlinCodeAnalyzer(project)
    
    override fun analyzeMethod(method: PsiElement): Result<MethodContext> {
        return try {
            val language = detectLanguage(method)
            
            when (language) {
                ProgrammingLanguage.JAVA -> {
                    if (method is PsiMethod) {
                        javaAnalyzer.analyzeJavaMethod(method)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("Ԫ�ز�����Ч�� Java ����"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    if (method is KtFunction) {
                        kotlinAnalyzer.analyzeKotlinFunction(method)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("Ԫ�ز�����Ч�� Kotlin ����"))
                    }
                }
                else -> {
                    Result.error(NekoamaError.PlatformError.EditorUnavailable("��֧�ֵ���������: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeMethod", NekoamaError.Unknown("��������ʧ��: ${e.message}"))
            Result.error(NekoamaError.Unknown("��������ʧ��: ${e.message}"))
        }
    }
    
    override fun analyzeClass(clazz: PsiElement): Result<ClassContext> {
        return try {
            val language = detectLanguage(clazz)
            
            when (language) {
                ProgrammingLanguage.JAVA -> {
                    if (clazz is PsiClass) {
                        javaAnalyzer.analyzeJavaClass(clazz)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("Ԫ�ز�����Ч�� Java ��"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    if (clazz is KtClass) {
                        kotlinAnalyzer.analyzeKotlinClass(clazz)
                    } else {
                        Result.error(NekoamaError.PlatformError.EditorUnavailable("Ԫ�ز�����Ч�� Kotlin ��"))
                    }
                }
                else -> {
                    Result.error(NekoamaError.PlatformError.EditorUnavailable("��֧�ֵ���������: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeClass", NekoamaError.Unknown("�����ʧ��: ${e.message}"))
            Result.error(NekoamaError.Unknown("�����ʧ��: ${e.message}"))
        }
    }
    
    override fun analyzeVariable(variable: PsiElement): Result<VariableContext> {
        return try {
            val language = detectLanguage(variable)
            
            when (language) {
                ProgrammingLanguage.JAVA -> {
                    when (variable) {
                        is PsiVariable -> javaAnalyzer.analyzeJavaVariable(variable)
                        is PsiField -> javaAnalyzer.analyzeJavaField(variable)
                        else -> Result.error(NekoamaError.PlatformError.EditorUnavailable("Ԫ�ز�����Ч�� Java ����"))
                    }
                }
                ProgrammingLanguage.KOTLIN -> {
                    when (variable) {
                        is KtProperty -> kotlinAnalyzer.analyzeKotlinProperty(variable)
                        is KtParameter -> kotlinAnalyzer.analyzeKotlinParameter(variable)
                        else -> Result.error(NekoamaError.PlatformError.EditorUnavailable("Ԫ�ز�����Ч�� Kotlin ����"))
                    }
                }
                else -> {
                    Result.error(NekoamaError.PlatformError.EditorUnavailable("��֧�ֵ���������: $language"))
                }
            }
        } catch (e: Exception) {
            NekoamaLogger.logError("analyzeVariable", NekoamaError.Unknown("��������ʧ��: ${e.message}"))
            Result.error(NekoamaError.Unknown("��������ʧ��: ${e.message}"))
        }
    }
    
    override fun extractSurroundingContext(element: PsiElement, radius: Int): Result<SurroundingContext> {
        return try {
            val containingFile = element.containingFile
            val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                ?: return Result.error(NekoamaError.PlatformError.EditorUnavailable("�޷���ȡ�ĵ�"))
            
            val elementStartOffset = element.textRange.startOffset
            val elementEndOffset = element.textRange.endOffset
            
            val startLine = document.getLineNumber(elementStartOffset)
            val endLine = document.getLineNumber(elementEndOffset)
            
            // ��ȡǰ�ô���
            val precedingLines = mutableListOf<String>()
            for (i in maxOf(0, startLine - radius) until startLine) {
                val lineStartOffset = document.getLineStartOffset(i)
                val lineEndOffset = document.getLineEndOffset(i)
                precedingLines.add(document.getText().substring(lineStartOffset, lineEndOffset).trim())
            }
            
            // ��ȡ��������
            val followingLines = mutableListOf<String>()
            val totalLines = document.lineCount
            for (i in (endLine + 1)..minOf(totalLines - 1, endLine + radius)) {
                val lineStartOffset = document.getLineStartOffset(i)
                val lineEndOffset = document.getLineEndOffset(i)
                followingLines.add(document.getText().substring(lineStartOffset, lineEndOffset).trim())
            }
            
            // ��ȡ�������
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
            NekoamaLogger.logError("extractSurroundingContext", NekoamaError.Unknown("��������ȡʧ��: ${e.message}"))
            Result.error(NekoamaError.Unknown("��������ȡʧ��: ${e.message}"))
        }
    }
    
    override fun detectLanguage(element: PsiElement): ProgrammingLanguage {
        val file = element.containingFile
        return when {
            file.language.id == "JAVA" || file.name.endsWith(".java") -> ProgrammingLanguage.JAVA
            file.language.id == "kotlin" || file.name.endsWith(".kt") -> ProgrammingLanguage.KOTLIN
            file.name.endsWith(".py") -> ProgrammingLanguage.PYTHON
            file.name.endsWith(".js") -> ProgrammingLanguage.JAVASCRIPT
            file.name.endsWith(".ts") -> ProgrammingLanguage.TYPESCRIPT
            file.name.endsWith(".cs") -> ProgrammingLanguage.C_SHARP
            file.name.endsWith(".cpp") || file.name.endsWith(".cc") -> ProgrammingLanguage.CPP
            file.name.endsWith(".go") -> ProgrammingLanguage.GO
            file.name.endsWith(".rs") -> ProgrammingLanguage.RUST
            file.name.endsWith(".swift") -> ProgrammingLanguage.SWIFT
            else -> ProgrammingLanguage.OTHER
        }
    }
    
    /**
     * ��ȡ�������
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
     * ��ȡ������
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
        
        // ��������Լ��
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
    
    private fun detectProjectType(): String? {
        // �����ļ��ṹ�����Ŀ����
        val baseDir = project.baseDir
        return when {
            baseDir?.findChild("pom.xml") != null -> "Maven"
            baseDir?.findChild("build.gradle") != null || baseDir?.findChild("build.gradle.kts") != null -> "Gradle"
            else -> null
        }
    }
    
    private fun detectFramework(): String? {
        // ����ͨ��������ע������
        return null // ��ʵ��
    }
    
    private fun detectBuildTool(): String? {
        return detectProjectType() // ��ʵ��
    }
    
    private fun detectJavaVersion(): String? {
        // ����ͨ��ģ�����ü��Java�汾
        return null // ��ʵ��
    }
    
    private fun detectKotlinVersion(): String? {
        // ����ͨ��Kotlin������汾
        return null // ��ʵ��
    }
}
