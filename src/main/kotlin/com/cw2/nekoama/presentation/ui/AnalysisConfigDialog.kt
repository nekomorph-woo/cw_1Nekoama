package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 代码依赖分析配置对话框（简化版本）
 */
class AnalysisConfigDialog(private val project: Project) : DialogWrapper(project) {

    // 分析范围选择
    private val projectScopeRadio = JBRadioButton(NekoamaBundle.message("analysis.config.scope.project"))
    private val moduleScopeRadio = JBRadioButton(NekoamaBundle.message("analysis.config.scope.module"))
    private val packageScopeRadio = JBRadioButton(NekoamaBundle.message("analysis.config.scope.package"))
    private val fileScopeRadio = JBRadioButton(NekoamaBundle.message("analysis.config.scope.file"))

    private val scopeButtonGroup = ButtonGroup().apply {
        add(projectScopeRadio)
        add(moduleScopeRadio)
        add(packageScopeRadio)
        add(fileScopeRadio)
    }

    // 分析参数设置
    private val includeTestCode = JBCheckBox(NekoamaBundle.message("analysis.config.includeTest"))
    private val enableComplexityAnalysis = JBCheckBox(NekoamaBundle.message("analysis.config.complexity"))
    private val enableCodeSmellDetection = JBCheckBox(NekoamaBundle.message("analysis.config.codeSmells"))
    private val enableSceneAnalysis = JBCheckBox(NekoamaBundle.message("analysis.config.sceneAnalysis"))

    init {
        title = NekoamaBundle.message("analysis.config.title")
        setOKButtonText(NekoamaBundle.message("analysis.config.start"))
        setCancelButtonText(NekoamaBundle.message("common.cancel"))

        // 初始化默认状态
        projectScopeRadio.isSelected = true
        enableComplexityAnalysis.isSelected = true
        enableCodeSmellDetection.isSelected = true
        enableSceneAnalysis.isSelected = true

        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.background = UIUtil.getPanelBackground()

        val formBuilder = FormBuilder.createFormBuilder()
            .addComponent(projectScopeRadio)
            .addComponent(moduleScopeRadio)
            .addComponent(packageScopeRadio)
            .addComponent(fileScopeRadio)
            .addVerticalGap(10)
            .addSeparator()
            .addComponent(includeTestCode)
            .addComponent(enableComplexityAnalysis)
            .addComponent(enableCodeSmellDetection)
            .addComponent(enableSceneAnalysis)

        mainPanel.add(formBuilder.panel, BorderLayout.CENTER)
        return mainPanel
    }

    fun getConfig(): AnalysisConfig {
        return AnalysisConfig(
            scopeType = when {
                projectScopeRadio.isSelected -> ScopeType.PROJECT
                moduleScopeRadio.isSelected -> ScopeType.MODULE
                packageScopeRadio.isSelected -> ScopeType.PACKAGE
                fileScopeRadio.isSelected -> ScopeType.FILE
                else -> ScopeType.PROJECT
            },
            includeTestCode = includeTestCode.isSelected,
            enableComplexityAnalysis = enableComplexityAnalysis.isSelected,
            enableCodeSmellDetection = enableCodeSmellDetection.isSelected,
            enableSceneAnalysis = enableSceneAnalysis.isSelected
        )
    }

    /**
     * 分析配置数据类（简化版本）
     */
    data class AnalysisConfig(
        val scopeType: ScopeType,
        val includeTestCode: Boolean = false,
        val enableComplexityAnalysis: Boolean = true,
        val enableCodeSmellDetection: Boolean = true,
        val enableSceneAnalysis: Boolean = true
    )

    /**
     * 分析范围类型枚举
     */
    enum class ScopeType {
        PROJECT,
        MODULE,
        PACKAGE,
        FILE
    }
}