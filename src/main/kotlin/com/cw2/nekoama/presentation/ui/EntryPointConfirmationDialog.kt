package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 场景入口点确认对话框（简化版本）
 */
class EntryPointConfirmationDialog(
    private val project: Project
) : DialogWrapper(project) {

    init {
        title = NekoamaBundle.message("entryPoint.title")
        setOKButtonText(NekoamaBundle.message("entryPoint.confirm"))
        setCancelButtonText(NekoamaBundle.message("common.cancel"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.border = JBUI.Borders.empty(10, 10, 10, 10)

        val infoLabel = JBLabel(NekoamaBundle.message("entryPoint.info"))
        infoLabel.foreground = UIUtil.getLabelForeground()

        mainPanel.add(infoLabel, BorderLayout.CENTER)
        return mainPanel
    }

    fun getConfirmedEntryPoints(): List<String> {
        return emptyList() // 简化版本返回空列表
    }
}