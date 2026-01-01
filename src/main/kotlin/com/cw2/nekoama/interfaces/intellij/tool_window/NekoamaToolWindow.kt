package com.cw2.nekoama.interfaces.intellij.tool_window

import com.cw2.nekoama.application.metrics.service.MetricsCollector
import com.cw2.nekoama.application.metrics.service.MetricsUpdateListener
import com.cw2.nekoama.shared.i18n.NekoamaBundle
import com.cw2.nekoama.domain.metrics.model.ActionRecord
import com.cw2.nekoama.domain.metrics.model.ActionType
import com.cw2.nekoama.domain.metrics.model.EnhancedMetricsSnapshot
import com.cw2.nekoama.domain.metrics.model.ErrorType
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.ui.TitledSeparator
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.FlowLayout
import javax.swing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDate

/**
 * 增强版Nekoama工具窗口
 * 支持实时更新、详细统计和历史数据查看
 */
class NekoamaToolWindow : MetricsUpdateListener {

    private val mainPanel = JPanel(BorderLayout())
    private val statsPanel = JPanel(GridBagLayout())
    private val detailsPanel = JPanel(CardLayout())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 基础统计标签
    private val todayLabel = JBLabel()
    private val totalLabel = JBLabel()
    private val successLabel = JBLabel()
    private val latencyLabel = JBLabel()
    private val tokensTodayLabel = JBLabel()
    private val tokensWeekLabel = JBLabel()
    private val tokensMonthLabel = JBLabel()
    private val tokensTotalLabel = JBLabel()

    // 详细统计标签
    private val mostUsedActionLabel = JBLabel()
    private val peakHourLabel = JBLabel()
    private val avgPerDayLabel = JBLabel()
    private val todayByTypeLabel = JBLabel()
    private val errorsTodayLabel = JBLabel()

    // 按钮
    private val refreshButton = JButton(NekoamaBundle.message("toolwindow.stats.refresh"))
    private val showDetailsButton = JButton(NekoamaBundle.message("toolwindow.stats.showDetails"))
    private val exportButton = JButton(NekoamaBundle.message("toolwindow.stats.export"))
    private val resetButton = JButton(NekoamaBundle.message("toolwindow.stats.reset"))

    init {
        setupUI()
        setupEventListeners()
        registerMetricsListener()
        updateStats()
    }

    private fun setupUI() {
        mainPanel.border = JBEmptyBorder(12)

        // 顶部标题
        val titleLabel = JBLabel(NekoamaBundle.message("toolwindow.title"))
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(JBFont.BOLD)

        // 创建统计面板
        createStatsPanel()
        createDetailsPanel()
        createButtonPanel()

        // 主布局
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(statsPanel, BorderLayout.NORTH)
        centerPanel.add(detailsPanel, BorderLayout.CENTER)
        centerPanel.add(createButtonPanel(), BorderLayout.SOUTH)

        mainPanel.add(centerPanel, BorderLayout.CENTER)
    }

    private fun createStatsPanel() {
        val gbc = GridBagConstraints()
        gbc.insets = Insets(2, 5, 2, 5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        // 标题
        val sectionTitle = JBLabel(NekoamaBundle.message("toolwindow.stats.section"))
        sectionTitle.font = sectionTitle.font.deriveFont(JBFont.BOLD)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        statsPanel.add(sectionTitle, gbc)
        gbc.gridwidth = 1

        // 基础统计
        gbc.gridy = 1; gbc.gridx = 0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.today.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(todayLabel, gbc)

        gbc.gridy = 2; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.total.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(totalLabel, gbc)

        gbc.gridy = 3; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.success.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(successLabel, gbc)

        gbc.gridy = 4; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.latency.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(latencyLabel, gbc)

        // 分隔线
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        statsPanel.add(TitledSeparator(), gbc)
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.HORIZONTAL

        // Token统计
        val tokenSectionTitle = JBLabel(NekoamaBundle.message("toolwindow.stats.tokens.section"))
        tokenSectionTitle.font = tokenSectionTitle.font.deriveFont(JBFont.BOLD)
        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2
        statsPanel.add(tokenSectionTitle, gbc)
        gbc.gridwidth = 1

        gbc.gridy = 7; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.tokens.today.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensTodayLabel, gbc)

        gbc.gridy = 8; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.tokens.week.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensWeekLabel, gbc)

        gbc.gridy = 9; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.tokens.month.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensMonthLabel, gbc)

        gbc.gridy = 10; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.tokens.total.label")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensTotalLabel, gbc)
    }

    private fun createDetailsPanel() {
        val basicPanel = JPanel()
        basicPanel.layout = BorderLayout()
        basicPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.click.show.details")), BorderLayout.CENTER)

        val detailPanel = JPanel(GridBagLayout())
        val detailGbc = GridBagConstraints()
        detailGbc.insets = Insets(5, 5, 5, 5)
        detailGbc.anchor = GridBagConstraints.WEST
        detailGbc.fill = GridBagConstraints.HORIZONTAL

        // 详细统计标题
        val detailTitle = JBLabel(NekoamaBundle.message("enhanced.stats.detailed.title"))
        detailTitle.font = detailTitle.font.deriveFont(JBFont.BOLD)
        detailGbc.gridx = 0; detailGbc.gridy = 0; detailGbc.gridwidth = 2
        detailPanel.add(detailTitle, detailGbc)
        detailGbc.gridwidth = 1

        // 详细统计内容
        detailGbc.gridy = 1; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.most.used.label")), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(mostUsedActionLabel, detailGbc)

        detailGbc.gridy = 2; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.peak.hour.label")), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(peakHourLabel, detailGbc)

        detailGbc.gridy = 3; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.avg.per.day.label")), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(avgPerDayLabel, detailGbc)

        // 按类型统计
        detailGbc.gridy = 4; detailGbc.gridx = 0; detailGbc.gridwidth = 2
        detailPanel.add(TitledSeparator(), detailGbc)
        detailGbc.gridwidth = 1

        detailGbc.gridy = 5; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.today.classification.label")), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(todayByTypeLabel, detailGbc)

        detailGbc.gridy = 6; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel(NekoamaBundle.message("enhanced.stats.today.errors.label")), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(errorsTodayLabel, detailGbc)

        detailsPanel.add(basicPanel, "basic")
        detailsPanel.add(detailPanel, "details")
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout(FlowLayout.LEFT, 5, 5)

        refreshButton.addActionListener { updateStats() }
        showDetailsButton.addActionListener { toggleDetails() }
        exportButton.addActionListener { exportData() }
        resetButton.addActionListener { resetStats() }

        buttonPanel.add(refreshButton)
        buttonPanel.add(showDetailsButton)
        buttonPanel.add(exportButton)
        buttonPanel.add(resetButton)

        return buttonPanel
    }

    private fun setupEventListeners() {
        // 自动定时刷新（每30秒）
        scope.launch {
            while (true) {
                delay(30000)
                updateStats()
            }
        }
    }

    private fun registerMetricsListener() {
        MetricsCollector.addListener(this)
    }

    private fun updateStats() {
        scope.launch {
            try {
                val snapshot = MetricsCollector.getEnhancedSnapshot()
                updateBasicStats(snapshot)
                updateDetailedStats(snapshot)
            } catch (e: Exception) {
                // 更新失败时显示错误信息
                showError(NekoamaBundle.message("enhanced.stats.update.failed", e.message ?: ""))
            }
        }
    }

    private fun updateBasicStats(snapshot: EnhancedMetricsSnapshot) {
        todayLabel.text = "${snapshot.today}"
        totalLabel.text = "${snapshot.total}"
        successLabel.text = String.format("%.1f%%", snapshot.successRate * 100)
        latencyLabel.text = "${snapshot.averageLatencyMs}ms"

        tokensTodayLabel.text = "${snapshot.tokensToday}"
        tokensWeekLabel.text = "${snapshot.tokensWeek}"
        tokensMonthLabel.text = "${snapshot.tokensMonth}"
        tokensTotalLabel.text = "${snapshot.tokensTotal}"
    }

    private fun updateDetailedStats(snapshot: EnhancedMetricsSnapshot) {
        mostUsedActionLabel.text = formatActionType(snapshot.mostUsedAction)
        peakHourLabel.text = "${snapshot.peakUsageHour}:00"
        avgPerDayLabel.text = String.format("%.1f", snapshot.avgRequestsPerDay)

        // 按类型统计
        val typeStats = snapshot.todayByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        todayByTypeLabel.text = if (typeStats.isEmpty()) NekoamaBundle.message("enhanced.stats.no.data") else typeStats

        // 错误统计
        val errorStats = snapshot.errorsToday.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }
        errorsTodayLabel.text = if (errorStats.isEmpty()) NekoamaBundle.message("enhanced.stats.no.errors") else errorStats
    }

    private fun toggleDetails() {
        val cardLayout = detailsPanel.layout as CardLayout
        if (showDetailsButton.text == NekoamaBundle.message("toolwindow.stats.showDetails")) {
            cardLayout.show(detailsPanel, "details")
            showDetailsButton.text = NekoamaBundle.message("toolwindow.stats.hideDetails")
        } else {
            cardLayout.show(detailsPanel, "basic")
            showDetailsButton.text = NekoamaBundle.message("toolwindow.stats.showDetails")
        }
    }

    private fun exportData() {
        scope.launch {
            try {
                val endDate = LocalDate.now()
                val startDate = endDate.minusMonths(1) // 导出最近一个月的数据

                val data = MetricsCollector.exportData(startDate, endDate)
                if (data != null) {
                    showInfo(NekoamaBundle.message("enhanced.stats.export.success"))
                    // 复制到剪贴板
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val stringSelection = StringSelection(data)
                    clipboard.setContents(stringSelection, null)
                } else {
                    showError(NekoamaBundle.message("enhanced.stats.export.no.data"))
                }
            } catch (e: Exception) {
                showError(NekoamaBundle.message("enhanced.stats.export.failed", e.message ?: ""))
            }
        }
    }

    private fun resetStats() {
        val option = JOptionPane.showConfirmDialog(
            mainPanel,
            NekoamaBundle.message("enhanced.stats.reset.confirm"),
            NekoamaBundle.message("enhanced.stats.reset.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (option == JOptionPane.YES_OPTION) {
            scope.launch {
                try {
                    MetricsCollector.resetAll()
                    updateStats()
                    showInfo(NekoamaBundle.message("enhanced.stats.reset.success"))
                } catch (e: Exception) {
                    showError(NekoamaBundle.message("enhanced.stats.reset.failed", e.message ?: ""))
                }
            }
        }
    }

    private fun formatActionType(actionType: ActionType): String {
        return when (actionType) {
            ActionType.GENERATE_NAMING -> NekoamaBundle.message("action.type.naming")
            ActionType.GENERATE_COMMENT -> NekoamaBundle.message("action.type.comment")
            ActionType.CUSTOM_GENERATE -> NekoamaBundle.message("action.type.custom")
            ActionType.ANALYZE_CODE_DEPS -> NekoamaBundle.message("action.type.analysis")
        }
    }

    private fun formatErrorType(errorType: ErrorType): String {
        return when (errorType) {
            ErrorType.NETWORK_ERROR -> NekoamaBundle.message("error.type.network")
            ErrorType.API_ERROR -> NekoamaBundle.message("error.type.api")
            ErrorType.TIMEOUT_ERROR -> NekoamaBundle.message("error.type.timeout")
            ErrorType.PARSING_ERROR -> NekoamaBundle.message("error.type.parsing")
            ErrorType.UNKNOWN_ERROR -> NekoamaBundle.message("error.type.unknown")
        }
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(mainPanel, message, NekoamaBundle.message("common.dialog.error"), JOptionPane.ERROR_MESSAGE)
    }

    private fun showInfo(message: String) {
        JOptionPane.showMessageDialog(mainPanel, message, NekoamaBundle.message("common.dialog.info"), JOptionPane.INFORMATION_MESSAGE)
    }

    override fun onMetricsUpdated(record: ActionRecord) {
        // 当指标更新时，自动刷新显示
        SwingUtilities.invokeLater {
            updateStats()
        }
    }

    fun getComponent(): JComponent = mainPanel

    fun dispose() {
        scope.cancel()
        MetricsCollector.removeListener(this)
    }
}