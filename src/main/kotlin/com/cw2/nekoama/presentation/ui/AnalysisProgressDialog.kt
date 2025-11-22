package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JProgressBar
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 分析进度对话框（简化版本）
 *
 * 功能：
 * - 显示代码依赖分析的实时进度
 * - 提供分析过程的详细反馈
 * - 支持用户取消分析操作
 */
object AnalysisProgressDialog {

    /**
     * 显示进度对话框并处理分析结果
     */
    fun showAndHandleResult(
        project: Project,
        analysisConfig: AnalysisConfigDialog.AnalysisConfig
    ) {
        try {
            val progressHandler = AnalysisProgressHandler(project, analysisConfig)
            progressHandler.show()

        } catch (t: Throwable) {
            NekoamaLogger.logError(
                "AnalysisProgressDialog",
                com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(NekoamaBundle.message("reportViewer.processingFailed", t.message ?: "")),
                mapOf("exception" to (t.message ?: "unknown"))
            )
            NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.reportFailed", t.message ?: ""))
        }
    }

    /**
     * 分析进度处理器（简化版本）
     */
    private class AnalysisProgressHandler(
        private val project: Project,
        private val analysisConfig: AnalysisConfigDialog.AnalysisConfig
    ) : DialogWrapper(project) {

        private val mainPanel = JPanel(BorderLayout())
        private val statusLabel = JBLabel(NekoamaBundle.message("progress.analysis.complete"))
        private val progressBar = JProgressBar(0, 100)
        private val detailsPanel = createDetailsPanel()

        init {
            title = NekoamaBundle.message("progress.analysis.title")
            setModal(false) // 非模态对话框，允许用户继续工作
            setupUI()
            init()
        }

        /**
         * 设置UI
         */
        private fun setupUI() {
            // 使用主题感知的背景色
            mainPanel.background = UIUtil.getPanelBackground()

            // 设置进度条
            progressBar.isStringPainted = true
            progressBar.string = NekoamaBundle.message("reportViewer.percentComplete", "100")
            progressBar.value = 100

            // 创建状态面板
            val statusPanel = JPanel(BorderLayout())
            statusPanel.background = UIUtil.getPanelBackground()
            statusPanel.border = JBUI.Borders.empty(10, 10, 5, 10)
            statusPanel.add(statusLabel, BorderLayout.WEST)

            // 创建进度面板
            val progressPanel = JPanel(BorderLayout())
            progressPanel.background = UIUtil.getPanelBackground()
            progressPanel.border = JBUI.Borders.empty(0, 10, 10, 10)
            progressPanel.add(progressBar, BorderLayout.CENTER)

            // 组装主面板
            val contentPanel = JPanel(BorderLayout())
            contentPanel.background = UIUtil.getPanelBackground()
            contentPanel.add(statusPanel, BorderLayout.NORTH)
            contentPanel.add(progressPanel, BorderLayout.CENTER)
            contentPanel.add(detailsPanel, BorderLayout.SOUTH)

            mainPanel.add(contentPanel, BorderLayout.CENTER)
        }

        /**
         * 创建详情面板
         */
        private fun createDetailsPanel(): JPanel {
            val detailsPanel = JPanel(BorderLayout())
            detailsPanel.background = UIUtil.getPanelBackground()
            detailsPanel.border = JBUI.Borders.empty(10, 10, 10, 10)

            val titleLabel = JBLabel(NekoamaBundle.message("progress.details.title"))
            titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD)
            titleLabel.border = JBUI.Borders.empty(10, 10, 5, 10)

            val infoLabel = JBLabel(NekoamaBundle.message("progress.analysis.info"))
            infoLabel.foreground = UIUtil.getLabelForeground()

            detailsPanel.add(titleLabel, BorderLayout.NORTH)
            detailsPanel.add(infoLabel, BorderLayout.CENTER)

            return detailsPanel
        }

        /**
         * 完成分析
         */
        fun completeAnalysis() {
            statusLabel.text = NekoamaBundle.message("progress.analysis.success")
            NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.success"))
        }

        /**
         * 取消分析
         */
        fun cancelAnalysis() {
            statusLabel.text = NekoamaBundle.message("progress.analysis.cancelled")
            NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.cancelled"))
        }

        /**
         * 显示错误
         */
        fun showError(errorMessage: String) {
            statusLabel.text = NekoamaBundle.message("progress.analysis.failed")
            statusLabel.foreground = UIUtil.getErrorForeground()
            NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.failed", errorMessage))
        }

        override fun createCenterPanel(): JComponent = mainPanel

        override fun getPreferredFocusedComponent(): JComponent = progressBar
    }
}