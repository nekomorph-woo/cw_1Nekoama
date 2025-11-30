package com.cw2.nekoama.integrations.psi

import com.cw2.nekoama.ai.model.dependency.MethodSource
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass

/**
 * 方法来源分析器
 * 基于项目包路径判断方法来源，支持智能递归策略
 */
class MethodSourceAnalyzer {
    companion object {
        /**
         * 分析方法来源
         * @param psiMethod 要分析的PSI方法对象
         * @param projectPackage 项目根包名
         * @return 方法来源枚举值
         */
        fun analyzeMethodSource(psiMethod: PsiMethod, projectPackage: String): MethodSource {
            val className = psiMethod.containingClass?.qualifiedName ?: return MethodSource.EXTERNAL

            // 基于项目包路径判断方法来源
            return if (className.startsWith(projectPackage)) {
                MethodSource.INTERNAL
            } else {
                MethodSource.EXTERNAL
            }
        }

        /**
         * 分析类来源
         * @param psiClass 要分析的PSI类对象
         * @param projectPackage 项目根包名
         * @return 方法来源枚举值
         */
        fun analyzeClassSource(psiClass: PsiClass, projectPackage: String): MethodSource {
            val className = psiClass.qualifiedName ?: return MethodSource.EXTERNAL

            // 基于项目包路径判断类来源
            return if (className.startsWith(projectPackage)) {
                MethodSource.INTERNAL
            } else {
                MethodSource.EXTERNAL
            }
        }

        /**
         * 通过类全名分析方法来源
         * @param className 类的全限定名
         * @param projectPackage 项目根包名
         * @return 方法来源枚举值
         */
        fun analyzeMethodSourceByClassName(className: String, projectPackage: String): MethodSource {
            return if (className.startsWith(projectPackage)) {
                MethodSource.INTERNAL
            } else {
                MethodSource.EXTERNAL
            }
        }

        /**
         * 提取包名
         * @param className 类的全限定名
         * @return 包名
         */
        fun extractPackageName(className: String): String {
            val lastDotIndex = className.lastIndexOf('.')
            return if (lastDotIndex > 0) {
                className.substring(0, lastDotIndex)
            } else {
                "" // 默认包
            }
        }

        /**
         * 提取类名（不含包名）
         * @param className 类的全限定名
         * @return 简单类名
         */
        fun extractSimpleClassName(className: String): String {
            val lastDotIndex = className.lastIndexOf('.')
            return if (lastDotIndex >= 0 && lastDotIndex < className.length - 1) {
                className.substring(lastDotIndex + 1)
            } else {
                className
            }
        }

        /**
         * 提取方法名（不含类名）
         * @param methodSignature 方法签名（格式：ClassName.methodName）
         * @return 方法名
         */
        fun extractMethodName(methodSignature: String): String {
            val lastDotIndex = methodSignature.lastIndexOf('.')
            return if (lastDotIndex >= 0 && lastDotIndex < methodSignature.length - 1) {
                methodSignature.substring(lastDotIndex + 1)
            } else {
                methodSignature
            }
        }

        /**
         * 提取类名（从方法签名中）
         * @param methodSignature 方法签名（格式：ClassName.methodName）
         * @return 类名
         */
        fun extractClassNameFromMethodSignature(methodSignature: String): String {
            val lastDotIndex = methodSignature.lastIndexOf('.')
            return if (lastDotIndex > 0) {
                methodSignature.substring(0, lastDotIndex)
            } else {
                "" // 无法提取类名
            }
        }
    }
}