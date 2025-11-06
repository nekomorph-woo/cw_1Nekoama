package com.cw2.nekoama.presentation.toolwindow

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.MetricsUpdateListener
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.ui.Gray
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import java.awt.Component

/**
 * 增强版Nekoama工具窗口
 * 支持实时更新、详细统计和历史数据查看
 */
class EnhancedNekoamaToolWindow : MetricsUpdateListener {

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
        statsPanel.add(JBLabel("今日使用:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(todayLabel, gbc)

        gbc.gridy = 2; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel("总计使用:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(totalLabel, gbc)

        gbc.gridy = 3; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel("成功率:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(successLabel, gbc)

        gbc.gridy = 4; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel("平均延迟:"), gbc)
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
        statsPanel.add(JBLabel("今日Token:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensTodayLabel, gbc)

        gbc.gridy = 8; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel("本周Token:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensWeekLabel, gbc)

        gbc.gridy = 9; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel("本月Token:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensMonthLabel, gbc)

        gbc.gridy = 10; gbc.gridx = 0; gbc.weightx = 0.0
        statsPanel.add(JBLabel("累计Token:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        statsPanel.add(tokensTotalLabel, gbc)
    }

    private fun createDetailsPanel() {
        val basicPanel = JPanel()
        basicPanel.layout = BorderLayout()
        basicPanel.add(JBLabel("点击'显示详情'查看详细统计"), BorderLayout.CENTER)

        val detailPanel = JPanel(GridBagLayout())
        val detailGbc = GridBagConstraints()
        detailGbc.insets = Insets(5, 5, 5, 5)
        detailGbc.anchor = GridBagConstraints.WEST
        detailGbc.fill = GridBagConstraints.HORIZONTAL

        // 详细统计标题
        val detailTitle = JBLabel("详细统计")
        detailTitle.font = detailTitle.font.deriveFont(JBFont.BOLD)
        detailGbc.gridx = 0; detailGbc.gridy = 0; detailGbc.gridwidth = 2
        detailPanel.add(detailTitle, detailGbc)
        detailGbc.gridwidth = 1

        // 详细统计内容
        detailGbc.gridy = 1; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel("最常用功能:"), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(mostUsedActionLabel, detailGbc)

        detailGbc.gridy = 2; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel("高峰时段:"), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(peakHourLabel, detailGbc)

        detailGbc.gridy = 3; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel("日均使用:"), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(avgPerDayLabel, detailGbc)

        // 按类型统计
        detailGbc.gridy = 4; detailGbc.gridx = 0; detailGbc.gridwidth = 2
        detailPanel.add(TitledSeparator(), detailGbc)
        detailGbc.gridwidth = 1

        detailGbc.gridy = 5; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel("今日分类:"), detailGbc)
        detailGbc.gridx = 1; detailGbc.weightx = 1.0
        detailPanel.add(todayByTypeLabel, detailGbc)

        detailGbc.gridy = 6; detailGbc.gridx = 0; detailGbc.weightx = 0.0
        detailPanel.add(JBLabel("今日错误:"), detailGbc)
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
        EnhancedMetricsCollector.addListener(this)
    }

    private fun updateStats() {
        scope.launch {
            try {
                val snapshot = EnhancedMetricsCollector.getEnhancedSnapshot()
                updateBasicStats(snapshot)
                updateDetailedStats(snapshot)
            } catch (e: Exception) {
                // 更新失败时显示错误信息
                showError("更新统计信息失败: ${e.message}")
            }
        }
    }

    private fun updateBasicStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot) {
        todayLabel.text = "${snapshot.today}"
        totalLabel.text = "${snapshot.total}"
        successLabel.text = String.format("%.1f%%", snapshot.successRate * 100)
        latencyLabel.text = "${snapshot.averageLatencyMs}ms"

        tokensTodayLabel.text = "${snapshot.tokensToday}"
        tokensWeekLabel.text = "${snapshot.tokensWeek}"
        tokensMonthLabel.text = "${snapshot.tokensMonth}"
        tokensTotalLabel.text = "${snapshot.tokensTotal}"
    }

    private fun updateDetailedStats(snapshot: com.cw2.nekoama.core.metrics.EnhancedMetricsSnapshot) {
        mostUsedActionLabel.text = formatActionType(snapshot.mostUsedAction)
        peakHourLabel.text = "${snapshot.peakUsageHour}:00"
        avgPerDayLabel.text = String.format("%.1f", snapshot.avgRequestsPerDay)

        // 按类型统计
        val typeStats = snapshot.todayByType.entries.joinToString(", ") {
            "${formatActionType(it.key)}: ${it.value}"
        }
        todayByTypeLabel.text = if (typeStats.isEmpty()) "无数据" else typeStats

        // 错误统计
        val errorStats = snapshot.errorsToday.entries.joinToString(", ") {
            "${formatErrorType(it.key)}: ${it.value}"
        }
        errorsTodayLabel.text = if (errorStats.isEmpty()) "无错误" else errorStats
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
                val endDate = java.time.LocalDate.now()
                val startDate = endDate.minusMonths(1) // 导出最近一个月的数据

                val data = EnhancedMetricsCollector.exportData(startDate, endDate)
                if (data != null) {
                    showInfo("数据已导出到剪贴板")
                    // 复制到剪贴板
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val stringSelection = java.awt.datatransfer.StringSelection(data)
                    clipboard.setContents(stringSelection, null)
                } else {
                    showError("导出失败：无数据")
                }
            } catch (e: Exception) {
                showError("导出失败: ${e.message}")
            }
        }
    }

    private fun resetStats() {
        val option = JOptionPane.showConfirmDialog(
            mainPanel,
            "确定要重置所有统计数据吗？此操作不可恢复。",
            "确认重置",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (option == JOptionPane.YES_OPTION) {
            scope.launch {
                try {
                    EnhancedMetricsCollector.resetAll()
                    updateStats()
                    showInfo("统计数据已重置")
                } catch (e: Exception) {
                    showError("重置失败: ${e.message}")
                }
            }
        }
    }

    private fun formatActionType(actionType: com.cw2.nekoama.core.metrics.ActionType): String {
        return when (actionType) {
            com.cw2.nekoama.core.metrics.ActionType.GENERATE_NAMING -> "命名生成"
            com.cw2.nekoama.core.metrics.ActionType.GENERATE_COMMENT -> "注释生成"
            com.cw2.nekoama.core.metrics.ActionType.CUSTOM_GENERATE -> "自定义生成"
            com.cw2.nekoama.core.metrics.ActionType.ANALYZE_UNUSED_CODE -> "代码分析"
        }
    }

    private fun formatErrorType(errorType: com.cw2.nekoama.core.metrics.ErrorType): String {
        return when (errorType) {
            com.cw2.nekoama.core.metrics.ErrorType.NETWORK_ERROR -> "网络错误"
            com.cw2.nekoama.core.metrics.ErrorType.API_ERROR -> "API错误"
            com.cw2.nekoama.core.metrics.ErrorType.TIMEOUT_ERROR -> "超时错误"
            com.cw2.nekoama.core.metrics.ErrorType.PARSING_ERROR -> "解析错误"
            com.cw2.nekoama.core.metrics.ErrorType.UNKNOWN_ERROR -> "未知错误"
        }
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(mainPanel, message, "错误", JOptionPane.ERROR_MESSAGE)
    }

    private fun showInfo(message: String) {
        JOptionPane.showMessageDialog(mainPanel, message, "信息", JOptionPane.INFORMATION_MESSAGE)
    }

    override fun onMetricsUpdated(record: com.cw2.nekoama.core.metrics.ActionRecord) {
        // 当指标更新时，自动刷新显示
        SwingUtilities.invokeLater {
            updateStats()
        }
    }

    fun getComponent(): JComponent = mainPanel

    fun dispose() {
        scope.cancel()
        EnhancedMetricsCollector.removeListener(this)
    }
}