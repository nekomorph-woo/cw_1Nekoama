package com.cw2.nekoama.mock

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * PSI 元素 Mock 工厂
 *
 * 提供创建各种 PSI 元素 Mock 的工厂方法，用于测试中的 PSI 相关操作
 */
object PsiElementMock {

    /**
     * 创建 Mock KtFunction
     *
     * @param name 函数名
     * @param returnType 返回类型
     * @param parameters 参数列表
     * @param bodyText 函数体文本
     * @return Mock 的 KtFunction
     */
    fun mockKtFunction(
        name: String = "testFunction",
        returnType: String = "kotlin.String",
        parameters: List<String> = emptyList(),
        bodyText: String = "return \"test\""
    ): KtNamedFunction {
        val mockFunction = mockk<KtNamedFunction>(relaxed = true)

        every { mockFunction.name } returns name
        every { mockFunction.text } returns buildString {
            append("fun $name(")
            append(parameters.joinToString(", "))
            append("): $returnType {\n")
            append("    $bodyText\n")
            append("}")
        }
        every { mockFunction.docComment } returns null

        // 模拟函数体
        val mockBody = mockk<org.jetbrains.kotlin.psi.KtExpression>(relaxed = true)
        every { mockBody.text } returns bodyText
        every { mockFunction.bodyExpression } returns mockBody

        return mockFunction
    }

    /**
     * 创建 Mock PsiMethod
     *
     * @param name 方法名
     * @param returnType 返回类型
     * @param isConstructor 是否为构造函数
     * @param isAbstract 是否为抽象方法
     * @return Mock 的 PsiMethod
     */
    fun mockPsiMethod(
        name: String = "testMethod",
        returnType: String = "void",
        isConstructor: Boolean = false,
        isAbstract: Boolean = false
    ): PsiMethod {
        val mockMethod = mockk<PsiMethod>(relaxed = true)

        every { mockMethod.name } returns name
        every { mockMethod.isConstructor } returns isConstructor
        every { mockMethod.docComment } returns null

        // 模拟返回类型
        val mockType = mockk<com.intellij.psi.PsiType>(relaxed = true)
        every { mockType.presentableText } returns returnType
        every { mockMethod.returnType } returns mockType

        // 模拟修饰符
        every { mockMethod.hasModifierProperty(PsiModifier.ABSTRACT) } returns isAbstract
        every { mockMethod.modifierList } returns mockk(relaxed = true)

        // 模拟方法体
        if (!isAbstract) {
            val mockBody = mockk<com.intellij.psi.PsiCodeBlock>(relaxed = true)
            every { mockBody.text } returns "{ // method body }"
            every { mockMethod.body } returns mockBody
        }

        return mockMethod
    }

    /**
     * 创建 Mock KtProperty
     *
     * @param name 属性名
     * @param isVar 是否为 var（false 表示 val）
     * @param initializerText 初始化器文本
     * @return Mock 的 KtProperty
     */
    fun mockKtProperty(
        name: String = "testProperty",
        isVar: Boolean = true,
        initializerText: String? = null
    ): KtProperty {
        val mockProperty = mockk<KtProperty>(relaxed = true)

        every { mockProperty.name } returns name
        every { mockProperty.isVar } returns isVar
        every { mockProperty.docComment } returns null

        // 模拟初始化器
        if (initializerText != null) {
            val mockInitializer = mockk<org.jetbrains.kotlin.psi.KtExpression>(relaxed = true)
            every { mockInitializer.text } returns initializerText
            every { mockProperty.initializer } returns mockInitializer
        }

        return mockProperty
    }

    /**
     * 创建 Mock PsiField
     *
     * @param name 字段名
     * @param typeText 类型文本
     * @param isStatic 是否为静态字段
     * @param isFinal 是否为 final
     * @return Mock 的 PsiField
     */
    fun mockPsiField(
        name: String = "testField",
        typeText: String = "String",
        isStatic: Boolean = false,
        isFinal: Boolean = false
    ): PsiField {
        val mockField = mockk<PsiField>(relaxed = true)

        every { mockField.name } returns name
        every { mockField.docComment } returns null

        // 模拟类型
        val mockType = mockk<com.intellij.psi.PsiType>(relaxed = true)
        every { mockType.presentableText } returns typeText
        every { mockField.type } returns mockType

        // 模拟修饰符
        every { mockField.hasModifierProperty(PsiModifier.STATIC) } returns isStatic
        every { mockField.hasModifierProperty(PsiModifier.FINAL) } returns isFinal

        return mockField
    }

    /**
     * 创建 Mock KtClass
     *
     * @param name 类名
     * @param isInterface 是否为接口
     * @param isEnum 是否为枚举
     * @return Mock 的 KtClass
     */
    fun mockKtClass(
        name: String = "TestClass",
        isInterface: Boolean = false,
        isEnum: Boolean = false
    ): KtClass {
        val mockClass = mockk<KtClass>(relaxed = true)

        every { mockClass.name } returns name
        every { mockClass.isInterface() } returns isInterface
        every { mockClass.isEnum() } returns isEnum
        every { mockClass.docComment } returns null

        return mockClass
    }

    /**
     * 创建 Mock PsiClass
     *
     * @param name 类名
     * @param isInterface 是否为接口
     * @param isEnum 是否为枚举
     * @return Mock 的 PsiClass
     */
    fun mockPsiClass(
        name: String = "TestClass",
        isInterface: Boolean = false,
        isEnum: Boolean = false
    ): PsiClass {
        val mockClass = mockk<PsiClass>(relaxed = true)

        every { mockClass.name } returns name
        every { mockClass.isInterface } returns isInterface
        every { mockClass.isEnum } returns isEnum
        every { mockClass.docComment } returns null

        return mockClass
    }

    /**
     * 创建 Mock PsiFile
     *
     * @param fileName 文件名
     * @param content 文件内容
     * @return Mock 的 PsiFile
     */
    fun mockPsiFile(
        fileName: String = "TestFile.kt",
        content: String = ""
    ): PsiFile {
        val mockFile = mockk<PsiFile>(relaxed = true)

        every { mockFile.name } returns fileName
        every { mockFile.text } returns content

        return mockFile
    }
}
