package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.core.logging.NekoamaLogger
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
 * 报告查看器（简化版本）
 *
 * 功能：
 * - 提供报告查看的基本界面
 */
class ReportViewer private constructor(
    private val project: Project,
    private val reportInfo: String
) : DialogWrapper(project) {

    private val mainPanel = JPanel(BorderLayout())

    init {
        title = NekoamaBundle.message("reportViewer.title")
        setModal(false)
        setupUI()
        init()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.border = JBUI.Borders.empty(10, 10, 10, 10)

        val infoLabel = JBLabel(NekoamaBundle.message("reportViewer.title", reportInfo))
        infoLabel.foreground = UIUtil.getLabelForeground()

        mainPanel.add(infoLabel, BorderLayout.CENTER)
    }

    override fun createCenterPanel(): JComponent = mainPanel

    companion object {
        /**
         * 显示报告查看器
         */
        fun showReports(project: Project, reportInfo: String) {
            try {
                val viewer = ReportViewer(project, reportInfo)
                viewer.show()
            } catch (t: Throwable) {
                NekoamaLogger.logError(
                    "showReports",
                    com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(NekoamaBundle.message("reportViewer.startFailed", t.message ?: "")),
                    mapOf("exception" to (t.message ?: "unknown"))
                )
            }
        }
    }
}