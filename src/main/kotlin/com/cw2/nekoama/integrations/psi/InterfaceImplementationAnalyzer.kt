package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.MethodCall
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * 接口实现映射分析器
 * 负责检测接口调用并找到对应的实际实现类
 */
class InterfaceImplementationAnalyzer(private val project: Project) {

    private val logger = NekoamaLogger
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    /**
     * 分析方法调用，增强接口实现映射信息
     */
    fun analyzeMethodCallWithInterfaceMapping(
        methodCall: MethodCall
    ): MethodCall {
        return try {
            // 检查被调用的类是否为接口
            val calleeClass = javaPsiFacade.findClass(methodCall.calleeClass, GlobalSearchScope.projectScope(project))

            if (calleeClass != null && calleeClass.isInterface) {
                logger.info("InterfaceImplementationAnalyzer", "检测到接口调用: ${methodCall.calleeClass}")

                // 查找所有实现类
                val implementingClasses = findImplementingClasses(calleeClass)

                if (implementingClasses.isNotEmpty()) {
                    // 如果只有一个实现类，直接映射
                    val actualImplementingClass = if (implementingClasses.size == 1) {
                        implementingClasses.first()
                    } else {
                        // 多个实现类的情况，返回第一个并记录所有
                        logger.info("InterfaceImplementationAnalyzer", "接口 ${methodCall.calleeClass} 有 ${implementingClasses.size} 个实现类")
                        implementingClasses.first()
                    }

                    return methodCall.copy(
                        isInterfaceCall = true,
                        actualImplementingClass = actualImplementingClass.qualifiedName ?: methodCall.calleeClass,
                        implementingClasses = implementingClasses.map { it.qualifiedName ?: "" }
                    )
                }
            }

            methodCall
        } catch (e: Exception) {
            logger.warn("InterfaceImplementationAnalyzer", "分析接口映射时出错: ${e.message}")
            methodCall
        }
    }

    /**
     * 查找指定接口的所有实现类
     */
    private fun findImplementingClasses(interfaceClass: PsiClass): List<PsiClass> {
        val implementingClasses = mutableListOf<PsiClass>()

        try {
            // 获取项目中的所有Java类
            val scope = GlobalSearchScope.projectScope(project)
            val allClasses = javaPsiFacade.findClasses("*", scope)

            allClasses.forEach { psiClass ->
                if (psiClass != interfaceClass && !psiClass.isInterface) {
                    // 检查是否实现了指定接口
                    val interfaces = psiClass.interfaces
                    if (interfaces.any { it.qualifiedName == interfaceClass.qualifiedName }) {
                        implementingClasses.add(psiClass)
                    }
                }
            }

        } catch (e: Exception) {
            logger.warn("InterfaceImplementationAnalyzer", "查找接口实现类时出错: ${e.message}")
        }

        return implementingClasses
    }

    /**
     * 检查类是否为接口
     */
    fun isInterface(className: String): Boolean {
        return try {
            val psiClass = javaPsiFacade.findClass(className, GlobalSearchScope.projectScope(project))
            psiClass?.isInterface == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取接口的主要实现类（基于命名约定和启发式规则）
     */
    fun getPrimaryImplementingClass(interfaceName: String): String? {
        return try {
            val interfaceClass = javaPsiFacade.findClass(interfaceName, GlobalSearchScope.projectScope(project))
                ?: return null

            if (!interfaceClass.isInterface) return null

            val implementingClasses = findImplementingClasses(interfaceClass)

            if (implementingClasses.isEmpty()) return null

            // 启发式规则选择主实现类：
            // 1. 只有一个实现类，直接返回
            if (implementingClasses.size == 1) {
                return implementingClasses.first().qualifiedName
            }

            // 2. 查找以Impl结尾的实现类
            val implClass = implementingClasses.find {
                it.qualifiedName?.endsWith("Impl") == true ||
                it.qualifiedName?.endsWith("impl") == true
            }
            if (implClass != null) {
                return implClass.qualifiedName
            }

            // 3. 查找与接口名称最相似的实现类
            val interfaceSimpleName = interfaceClass.name
            val similarClass = implementingClasses.find { implClass ->
                implClass.name?.contains(interfaceSimpleName ?: "") == true
            }
            if (similarClass != null) {
                return similarClass.qualifiedName
            }

            // 4. 返回第一个实现类
            implementingClasses.first().qualifiedName

        } catch (e: Exception) {
            logger.warn("InterfaceImplementationAnalyzer", "获取主实现类时出错: ${e.message}")
            null
        }
    }
}