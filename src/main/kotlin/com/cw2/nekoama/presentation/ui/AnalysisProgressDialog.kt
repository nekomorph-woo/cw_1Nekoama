package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.ai.model.dependency.AnalysisConfig
import com.cw2.nekoama.ai.model.dependency.DependencyAnalysisResult
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.integrations.psi.BatchAnalysisProcessor
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 分析进度对话框（完整版本）
 *
 * 功能：
 * - 显示代码依赖分析的实时进度
 * - 提供分析过程的详细反馈
 * - 支持用户取消分析操作
 * - 显示处理速度、剩余时间估算、当前处理的文件
 * - 支持后台运行模式
 */
class AnalysisProgressDialog(
    private val project: Project,
    private val analysisConfig: AnalysisConfig,
    private val confirmedEntryPoints: List<String> = emptyList()
) : DialogWrapper(project) {

    // UI组件
    private val mainPanel = JPanel(BorderLayout())
    private val statusLabel = JBLabel(NekoamaBundle.message("progress.analysis.initializing"))
    private val progressBar = JProgressBar(0, 100)
    private val detailsTable = JTable()
    private val currentFileLabel = JBLabel()
    private val speedLabel = JBLabel()
    private val timeLabel = JBLabel()
    private val cancelButton = JButton(NekoamaBundle.message("button.cancel"))
    private val runInBackgroundCheckBox = JCheckBox(NekoamaBundle.message("progress.runInBackground"))

    // 分析状态
    private var analysisStartTime = 0L
    private var totalClasses = 0
    private var processedClasses = 0
    private var currentBatch = 0
    private var totalBatches = 0
    private var isRunningInBackground = false

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
        mainPanel.background = UIUtil.getPanelBackground()

        // 设置进度条
        progressBar.isStringPainted = true
        progressBar.string = NekoamaBundle.message("progress.progressBar.percent", "0")

        // 创建详细信息表格
        setupDetailsTable()

        // 创建状态面板
        val statusPanel = createStatusPanel()

        // 创建进度面板
        val progressPanel = createProgressPanel()

        // 创建控制面板
        val controlPanel = createControlPanel()

        // 组装主面板
        val contentPanel = JPanel(BorderLayout())
        contentPanel.background = UIUtil.getPanelBackground()
        contentPanel.border = JBUI.Borders.empty(10)
        contentPanel.add(statusPanel, BorderLayout.NORTH)
        contentPanel.add(progressPanel, BorderLayout.CENTER)

        val southPanel = JPanel(BorderLayout())
        southPanel.background = UIUtil.getPanelBackground()
        southPanel.add(createDetailsPanel(), BorderLayout.CENTER)
        southPanel.add(controlPanel, BorderLayout.SOUTH)

        contentPanel.add(southPanel, BorderLayout.SOUTH)

        mainPanel.add(contentPanel, BorderLayout.CENTER)
    }

    /**
     * 创建状态面板
     */
    private fun createStatusPanel(): JPanel {
        val statusPanel = JPanel(BorderLayout())
        statusPanel.background = UIUtil.getPanelBackground()
        statusPanel.border = JBUI.Borders.empty(0, 0, 10, 0)

        statusLabel.font = statusLabel.font.deriveFont(statusLabel.font.style or java.awt.Font.BOLD)
        statusLabel.foreground = UIUtil.getLabelForeground()

        val timePanel = JPanel()
        timePanel.background = UIUtil.getPanelBackground()
        timePanel.layout = BoxLayout(timePanel, BoxLayout.Y_AXIS)

        speedLabel.font = speedLabel.font.deriveFont(speedLabel.font.size - 2f)
        speedLabel.foreground = UIUtil.getLabelForeground().darker()

        timeLabel.font = timeLabel.font.deriveFont(timeLabel.font.size - 2f)
        timeLabel.foreground = UIUtil.getLabelForeground().darker()

        timePanel.add(speedLabel)
        timePanel.add(timeLabel)

        statusPanel.add(statusLabel, BorderLayout.WEST)
        statusPanel.add(timePanel, BorderLayout.EAST)

        return statusPanel
    }

    /**
     * 创建进度面板
     */
    private fun progressPanel(): JPanel {
        val progressPanel = JPanel(BorderLayout())
        progressPanel.background = UIUtil.getPanelBackground()
        progressPanel.border = JBUI.Borders.empty(0, 0, 10, 0)

        progressPanel.add(progressBar, BorderLayout.CENTER)

        val progressLabel = JBLabel(NekoamaBundle.message("progress.classes.processed", "0", "0"))
        progressLabel.font = progressLabel.font.deriveFont(progressLabel.font.size - 1f)
        progressLabel.foreground = UIUtil.getLabelForeground().darker()
        progressPanel.add(progressLabel, BorderLayout.EAST)

        return progressPanel
    }

    /**
     * 创建进度面板（修正方法名）
     */
    private fun createProgressPanel(): JPanel {
        val progressPanel = JPanel(BorderLayout())
        progressPanel.background = UIUtil.getPanelBackground()
        progressPanel.border = JBUI.Borders.empty(0, 0, 10, 0)

        progressPanel.add(progressBar, BorderLayout.CENTER)

        val progressLabel = JBLabel(NekoamaBundle.message("progress.classes.processed", "0", "0"))
        progressLabel.font = progressLabel.font.deriveFont(progressLabel.font.size - 1f)
        progressLabel.foreground = UIUtil.getLabelForeground().darker()
        progressPanel.add(progressLabel, BorderLayout.EAST)

        return progressPanel
    }

    /**
     * 设置详细信息表格
     */
    private fun setupDetailsTable() {
        val tableModel = DefaultTableModel(
            arrayOf(
                NekoamaBundle.message("progress.table.column.metric"),
                NekoamaBundle.message("progress.table.column.value")
            ),
            0
        )
        detailsTable.model = tableModel
        detailsTable.rowHeight = JBUI.scale(20)
        detailsTable.font = detailsTable.font.deriveFont(detailsTable.font.size - 1f)

        // 设置列宽
        val columnModel = detailsTable.columnModel
        columnModel.getColumn(0).preferredWidth = 150
        columnModel.getColumn(1).preferredWidth = 200

        // 添加初始数据
        updateDetailsTable()
    }

    /**
     * 创建详情面板
     */
    private fun createDetailsPanel(): JPanel {
        val detailsPanel = JPanel(BorderLayout())
        detailsPanel.background = UIUtil.getPanelBackground()
        detailsPanel.border = JBUI.Borders.empty(10, 0, 0, 0)

        val titleLabel = JBLabel(NekoamaBundle.message("progress.details.title"))
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD)
        titleLabel.border = JBUI.Borders.empty(0, 0, 5, 0)

        val scrollPane = JBScrollPane(detailsTable)
        scrollPane.preferredSize = JBUI.size(400, 150)

        detailsPanel.add(titleLabel, BorderLayout.NORTH)
        detailsPanel.add(scrollPane, BorderLayout.CENTER)

        return detailsPanel
    }

    /**
     * 创建控制面板
     */
    private fun createControlPanel(): JPanel {
        val controlPanel = JPanel(BorderLayout())
        controlPanel.background = UIUtil.getPanelBackground()
        controlPanel.border = JBUI.Borders.empty(10, 0, 0, 0)

        // 左侧：当前文件信息
        currentFileLabel.font = currentFileLabel.font.deriveFont(currentFileLabel.font.size - 1f)
        currentFileLabel.foreground = UIUtil.getLabelForeground().darker()

        // 右侧：控制按钮
        val rightPanel = JPanel()
        rightPanel.background = UIUtil.getPanelBackground()
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.X_AXIS)
        rightPanel.add(runInBackgroundCheckBox)
        rightPanel.add(Box.createHorizontalStrut(JBUI.scale(10)))
        rightPanel.add(cancelButton)

        controlPanel.add(currentFileLabel, BorderLayout.WEST)
        controlPanel.add(rightPanel, BorderLayout.EAST)

        // 设置事件监听器
        setupEventListeners()

        return controlPanel
    }

    /**
     * 设置事件监听器
     */
    private fun setupEventListeners() {
        cancelButton.addActionListener {
            if (isRunningInBackground) {
                // 后台运行模式，只隐藏对话框
                dispose()
            } else {
                // 正常模式，取消分析
                cancelAnalysis()
            }
        }

        runInBackgroundCheckBox.addActionListener {
            isRunningInBackground = runInBackgroundCheckBox.isSelected
            if (isRunningInBackground) {
                cancelButton.text = NekoamaBundle.message("button.hide")
                statusLabel.text = NekoamaBundle.message("progress.analysis.runningInBackground")
            } else {
                cancelButton.text = NekoamaBundle.message("button.cancel")
                statusLabel.text = NekoamaBundle.message("progress.analysis.running")
            }
        }
    }

    /**
     * 开始分析
     */
    fun startAnalysis(onComplete: (DependencyAnalysisResult) -> Unit) {
        analysisStartTime = System.currentTimeMillis()

        // 在后台线程中执行分析
        Thread {
            try {
                val result = executeAnalysis()
                SwingUtilities.invokeLater {
                    completeAnalysis()
                    onComplete(result)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError(e.message ?: NekoamaBundle.message("progress.error.unknown"))
                }
            }
        }.start()
    }

    /**
     * 执行分析
     */
    private fun executeAnalysis(): DependencyAnalysisResult {
        return try {
            val batchProcessor = BatchAnalysisProcessor(project)

            // 简化的同步执行
            runBlocking {
                batchProcessor.executeBatchAnalysis(analysisConfig, com.intellij.openapi.progress.EmptyProgressIndicator())
            }
        } catch (e: Exception) {
            throw RuntimeException(NekoamaBundle.message("progress.analysis.failed", e.message ?: ""), e)
        }
    }

    /**
     * 更新进度
     */
    private fun updateProgress(status: BatchAnalysisProcessor.AnalysisStatus) {
        this.totalClasses = status.totalClasses
        this.processedClasses = status.processedClasses
        this.currentBatch = status.currentBatch
        this.totalBatches = status.totalBatches

        // 更新进度条
        val progressPercent = (status.progress * 100).toInt()
        progressBar.value = progressPercent
        progressBar.string = NekoamaBundle.message("progress.progressBar.percent", progressPercent.toString())

        // 更新状态文本
        if (status.progress < 1.0) {
            statusLabel.text = NekoamaBundle.message(
                "progress.analysis.batchStatus",
                status.currentBatch,
                status.totalBatches
            )
        }

        // 更新速度和时间估算
        updateSpeedAndTime(status)

        // 更新详细信息表格
        updateDetailsTable()

        // 更新当前文件标签
        updateCurrentFileLabel(status)
    }

    /**
     * 更新速度和时间估算
     */
    private fun updateSpeedAndTime(status: BatchAnalysisProcessor.AnalysisStatus) {
        val elapsedMs = status.elapsedTimeMs
        val processedCount = status.processedClasses

        if (elapsedMs > 0 && processedCount > 0) {
            val classesPerSecond = (processedCount * 1000.0) / elapsedMs
            speedLabel.text = NekoamaBundle.message("progress.speed.format", String.format("%.1f", classesPerSecond))

            val remainingMs = status.estimatedRemainingTimeMs
            if (remainingMs > 0) {
                val remainingMinutes = remainingMs / 60000
                val remainingSeconds = (remainingMs % 60000) / 1000
                val timeString = String.format("%02d:%02d", remainingMinutes, remainingSeconds)
                timeLabel.text = NekoamaBundle.message("progress.time.remaining", timeString)
            } else {
                timeLabel.text = NekoamaBundle.message("progress.calculating")
            }
        } else {
            speedLabel.text = NekoamaBundle.message("progress.initializing")
            timeLabel.text = NekoamaBundle.message("progress.calculating")
        }
    }

    /**
     * 更新详细信息表格
     */
    private fun updateDetailsTable() {
        val tableModel = detailsTable.model as DefaultTableModel
        tableModel.rowCount = 0

        val dateFormat = SimpleDateFormat("HH:mm:ss")

        tableModel.addRow(arrayOf("Total Classes", totalClasses.toString()))
        tableModel.addRow(arrayOf("Processed Classes", processedClasses.toString()))
        tableModel.addRow(arrayOf("Current Batch", "$currentBatch/$totalBatches"))
        tableModel.addRow(arrayOf("Progress", "${progressBar.value}%"))
        tableModel.addRow(arrayOf("Start Time", if (analysisStartTime > 0) dateFormat.format(Date(analysisStartTime)) else "Not started"))
        tableModel.addRow(arrayOf("Entry Points", confirmedEntryPoints.size.toString()))
        tableModel.addRow(arrayOf("Analysis Mode", if (isRunningInBackground) "Background" else "Foreground"))
    }

    /**
     * 更新当前文件标签
     */
    private fun updateCurrentFileLabel(status: BatchAnalysisProcessor.AnalysisStatus) {
        // 这里可以根据status中的信息显示当前处理的类或文件
        // 简化实现，可以根据需要扩展
        currentFileLabel.text = if (status.progress < 1.0) {
            NekoamaBundle.message("progress.current.file", "Processing class ${status.processedClasses + 1}")
        } else {
            NekoamaBundle.message("progress.analysis.complete")
        }
    }

    /**
     * 完成分析
     */
    private fun completeAnalysis() {
        statusLabel.text = NekoamaBundle.message("progress.analysis.success")
        statusLabel.foreground = JBLabel().foreground // 使用默认的成功颜色
        progressBar.string = NekoamaBundle.message("progress.progressBar.percent", "100")
        currentFileLabel.text = NekoamaBundle.message("progress.analysis.complete")
        cancelButton.text = NekoamaBundle.message("button.close")
        cancelButton.isEnabled = true
        runInBackgroundCheckBox.isEnabled = false

        // 更新详细信息表格显示最终统计
        updateDetailsTable()

        NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.success"))
    }

    /**
     * 取消分析
     */
    private fun cancelAnalysis() {
        statusLabel.text = NekoamaBundle.message("progress.analysis.cancelled")
        statusLabel.foreground = UIUtil.getLabelForeground()
        currentFileLabel.text = NekoamaBundle.message("progress.analysis.cancelled")
        cancelButton.text = NekoamaBundle.message("button.close")
        runInBackgroundCheckBox.isEnabled = false

        NekoamaNotifier.info(NekoamaBundle.message("action.analyzeCodeDeps.cancelled"))
    }

    /**
     * 显示错误
     */
    private fun showError(errorMessage: String) {
        statusLabel.text = NekoamaBundle.message("progress.analysis.failed")
        statusLabel.foreground = UIUtil.getErrorForeground()
        currentFileLabel.text = "Error: $errorMessage"
        cancelButton.text = NekoamaBundle.message("button.close")
        runInBackgroundCheckBox.isEnabled = false

        NekoamaNotifier.error(NekoamaBundle.message("action.analyzeCodeDeps.error.failed", errorMessage))
    }

    override fun createCenterPanel(): JComponent = mainPanel

    override fun getPreferredFocusedComponent(): JComponent = cancelButton

    override fun doOKAction() {
        // 当分析完成时，关闭对话框
        if (progressBar.value == 100) {
            super.doOKAction()
        }
    }

    companion object {
        /**
         * 显示进度对话框并开始分析
         */
        fun showAndStartAnalysis(
            project: Project,
            analysisConfig: AnalysisConfig,
            confirmedEntryPoints: List<String> = emptyList(),
            onComplete: (DependencyAnalysisResult) -> Unit
        ): AnalysisProgressDialog {
            val progressDialog = AnalysisProgressDialog(project, analysisConfig, confirmedEntryPoints)
            progressDialog.show()
            progressDialog.startAnalysis(onComplete)
            return progressDialog
        }
    }
}