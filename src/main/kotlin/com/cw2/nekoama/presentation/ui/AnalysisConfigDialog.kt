package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.core.analysis.SimpleAnalysisConfig
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 代码依赖分析配置对话框（M2重构版本）
 *
 * 根据M2阶段重构要求，简化分析配置界面：
 * - 移除入口检测方式的选择（因为是必须步骤）
 * - 项目范围固定为全项目分析
 * - 简化为基本分析选项：复杂度分析、坏味道检测、调用关系图
 */
class AnalysisConfigDialog(private val project: Project) : DialogWrapper(project) {

    // 基本分析选项（核心功能始终启用，仅提供显示选项）
    private val includeTestCode = JBCheckBox(NekoamaBundle.message("analysis.config.includeTest"))

    // 分析结果显示选项
    private val showComplexityAnalysis = JBCheckBox(NekoamaBundle.message("analysis.config.showComplexity"))
    private val showCodeSmellDetection = JBCheckBox(NekoamaBundle.message("analysis.config.showCodeSmells"))
    private val showCallRelationships = JBCheckBox(NekoamaBundle.message("analysis.config.showCallRelationships"))

    init {
        title = NekoamaBundle.message("analysis.config.title")
        setOKButtonText(NekoamaBundle.message("analysis.config.start"))
        setCancelButtonText(NekoamaBundle.message("common.cancel"))

        // 初始化默认状态
        includeTestCode.isSelected = false
        showComplexityAnalysis.isSelected = true
        showCodeSmellDetection.isSelected = true
        showCallRelationships.isSelected = true

        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.background = UIUtil.getPanelBackground()

        // 分析说明标签
        val descriptionLabel = JBLabel("基于M2重构版本，将执行全项目分析并自动检测业务入口点")
        descriptionLabel.border = JBUI.Borders.empty(5, 10, 15, 10)

        val formBuilder = FormBuilder.createFormBuilder()
            .addComponent(descriptionLabel)
            .addVerticalGap(5)
            .addSeparator()
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("分析选项:"), includeTestCode, 1, false)
            .addVerticalGap(10)
            .addSeparator()
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("结果显示:"), showComplexityAnalysis, 1, false)
            .addLabeledComponent(JBLabel(""), showCodeSmellDetection, 1, false)
            .addLabeledComponent(JBLabel(""), showCallRelationships, 1, false)
            .addVerticalGap(15)
            .addVerticalGap(5)

        mainPanel.add(formBuilder.panel, BorderLayout.CENTER)
        return mainPanel
    }

    /**
     * 获取简化的分析配置
     */
    fun getSimpleConfig(): SimpleAnalysisConfig {
        return SimpleAnalysisConfig(
            includeTestCode = includeTestCode.isSelected,
            enableComplexityAnalysis = showComplexityAnalysis.isSelected,
            enableCodeSmellDetection = showCodeSmellDetection.isSelected,
            enableDependencyAnalysis = showCallRelationships.isSelected
        )
    }

    /**
     * 保持向后兼容的配置方法
     */
    fun getConfig(): AnalysisConfig {
        return AnalysisConfig(
            scopeType = ScopeType.PROJECT, // 固定为全项目分析
            includeTestCode = includeTestCode.isSelected,
            enableComplexityAnalysis = showComplexityAnalysis.isSelected,
            enableCodeSmellDetection = showCodeSmellDetection.isSelected,
            enableSceneAnalysis = false // M2阶段已移除场景分析
        )
    }

    /**
     * 分析配置数据类（保持向后兼容）
     */
    data class AnalysisConfig(
        val scopeType: ScopeType,
        val includeTestCode: Boolean = false,
        val enableComplexityAnalysis: Boolean = true,
        val enableCodeSmellDetection: Boolean = true,
        val enableSceneAnalysis: Boolean = false // M2阶段移除
    )

    /**
     * 分析范围类型枚举（保持向后兼容，但仅支持PROJECT）
     */
    enum class ScopeType {
        PROJECT, // 仅支持全项目分析
        MODULE,  // 已禁用
        PACKAGE, // 已禁用
        FILE     // 已禁用
    }
}