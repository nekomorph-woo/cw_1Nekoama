package com.cw2.nekoama.presentation.toolwindow

import com.cw2.nekoama.core.metrics.EnhancedMetricsCollector
import com.cw2.nekoama.core.metrics.GroupBy
import com.cw2.nekoama.core.metrics.MetricsQuery
import com.cw2.nekoama.core.metrics.ActionType
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.DefaultTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

/**
 * 历史数据查看器
 * 提供历史统计数据的查看和分析功能
 */
class HistoryViewer {
    private val mainPanel = JPanel(BorderLayout())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 查询控件
    private val startDatePicker = JDatePicker()
    private val endDatePicker = JDatePicker()
    private val groupByCombo = JComboBox<GroupBy>()
    private val actionTypeCheckboxes = mutableMapOf<ActionType, JCheckBox>()
    private val showSuccessCheckBox = JCheckBox(NekoamaBundle.message("historyViewer.successOperations"))
    private val showErrorsCheckBox = JCheckBox(NekoamaBundle.message("historyViewer.failedOperations"))

    // 结果表格
    private val tableModel = DefaultTableModel()
    private val resultsTable = JBTable(tableModel)

    init {
        setupUI()
        setupEventListeners()
        loadRecentData()
    }

    private fun setupUI() {
        // 设置默认日期范围（最近30天）
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)
        startDatePicker.setDate(startDate)
        endDatePicker.setDate(endDate)

        // 查询参数面板
        val queryPanel = createQueryPanel()

        // 结果面板
        val resultsPanel = createResultsPanel()

        // 主布局
        mainPanel.border = JBEmptyBorder(10)
        mainPanel.add(queryPanel, BorderLayout.NORTH)
        mainPanel.add(resultsPanel, BorderLayout.CENTER)
    }

    private fun createQueryPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        // 日期范围
        gbc.gridy = 0; gbc.gridx = 0
        panel.add(JLabel(NekoamaBundle.message("historyViewer.startDate")), gbc)
        gbc.gridx = 1
        panel.add(startDatePicker, gbc)

        gbc.gridy = 1; gbc.gridx = 0
        panel.add(JLabel(NekoamaBundle.message("historyViewer.endDate")), gbc)
        gbc.gridx = 1
        panel.add(endDatePicker, gbc)

        // 分组方式
        gbc.gridy = 2; gbc.gridx = 0
        panel.add(JLabel(NekoamaBundle.message("historyViewer.groupBy")), gbc)
        gbc.gridx = 1
        panel.add(groupByCombo, gbc)

        // 操作类型
        gbc.gridy = 3; gbc.gridx = 0
        panel.add(JLabel(NekoamaBundle.message("historyViewer.operationType")), gbc)
        gbc.gridx = 1

        val actionTypesPanel = JPanel()
        ActionType.values().forEach { actionType ->
            val checkBox = JCheckBox(formatActionType(actionType), true)
            actionTypeCheckboxes[actionType] = checkBox
            actionTypesPanel.add(checkBox)
        }
        panel.add(actionTypesPanel, gbc)

        // 成功/失败过滤
        gbc.gridy = 4; gbc.gridx = 0
        panel.add(JLabel(NekoamaBundle.message("historyViewer.statusFilter")), gbc)
        gbc.gridx = 1

        val statusPanel = JPanel()
        showSuccessCheckBox.isSelected = true
        showErrorsCheckBox.isSelected = true
        statusPanel.add(showSuccessCheckBox)
        statusPanel.add(showErrorsCheckBox)
        panel.add(statusPanel, gbc)

        // 查询按钮
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2
        val queryButton = JButton(NekoamaBundle.message("historyViewer.queryHistory"))
        queryButton.addActionListener { executeQuery() }
        panel.add(queryButton, gbc)

        return panel
    }

    private fun createResultsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 设置表格列
        setupTableColumns()

        val scrollPane = JBScrollPane(resultsTable)
        scrollPane.border = JBEmptyBorder(5, 0, 0, 0)

        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun setupTableColumns() {
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.operationTime"))
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.operationType"))
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.filePath"))
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.tokenConsumption"))
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.duration"))
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.status"))
        tableModel.addColumn(NekoamaBundle.message("historyViewer.column.details"))
    }

    private fun setupEventListeners() {
        // 初始化分组选项
        GroupBy.values().forEach { groupByCombo.addItem(it) }

        // 设置表格列宽
        resultsTable.columnModel.getColumn(0).preferredWidth = 120 // 时间周期
        resultsTable.columnModel.getColumn(1).preferredWidth = 80  // 请求数
        resultsTable.columnModel.getColumn(2).preferredWidth = 80  // 成功数
        resultsTable.columnModel.getColumn(3).preferredWidth = 80  // 成功率
        resultsTable.columnModel.getColumn(4).preferredWidth = 100 // 平均延迟
        resultsTable.columnModel.getColumn(5).preferredWidth = 100 // Token使用
        resultsTable.columnModel.getColumn(6).preferredWidth = 150 // 详细分布

        // 表格不可编辑
        resultsTable.setDefaultEditor(Object::class.java, null)
    }

    private fun loadRecentData() {
        // 默认加载最近30天的数据
        executeQuery()
    }

    private fun executeQuery() {
        scope.launch {
            try {
                // 显示加载状态
                showLoadingState()

                // 构建查询参数
                val query = buildQuery()

                // 执行查询
                val results = withContext(Dispatchers.IO) {
                    EnhancedMetricsCollector.queryMetrics(query)
                }

                // 更新UI
                updateResultsTable(results)

            } catch (e: Exception) {
                showError(NekoamaBundle.message("historyViewer.queryFailed", e.message ?: ""))
            }
        }
    }

    private fun buildQuery(): MetricsQuery {
        val selectedActionTypes = actionTypeCheckboxes.entries
            .filter { it.value.isSelected }
            .map { it.key }
            .toSet()

        return MetricsQuery(
            startDate = startDatePicker.getDate(),
            endDate = endDatePicker.getDate(),
            actionTypes = if (selectedActionTypes.isEmpty()) null else selectedActionTypes,
            includeSuccess = showSuccessCheckBox.isSelected,
            includeErrors = showErrorsCheckBox.isSelected,
            groupBy = groupByCombo.selectedItem as GroupBy
        )
    }

    private fun showLoadingState() {
        tableModel.rowCount = 0
        tableModel.addRow(arrayOf(NekoamaBundle.message("common.querying"), "", "", "", "", "", ""))
    }

    private fun updateResultsTable(results: List<com.cw2.nekoama.core.metrics.AggregatedMetrics>) {
        tableModel.rowCount = 0

        if (results.isEmpty()) {
            tableModel.addRow(arrayOf(NekoamaBundle.message("common.noData"), "", "", "", "", "", ""))
            return
        }

        results.forEach { result ->
            val breakdown = result.breakdown.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            tableModel.addRow(arrayOf(
                result.period,
                result.totalRequests,
                result.successRequests,
                String.format("%.1f%%", result.successRate * 100),
                result.avgLatencyMs,
                result.totalTokens,
                breakdown
            ))
        }
    }

    private fun showError(message: String) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(mainPanel, message, NekoamaBundle.message("historyViewer.errorDialog"), JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun formatActionType(actionType: ActionType): String {
        return when (actionType) {
            ActionType.GENERATE_NAMING -> NekoamaBundle.message("action.type.naming")
            ActionType.GENERATE_COMMENT -> NekoamaBundle.message("action.type.comment")
            ActionType.CUSTOM_GENERATE -> NekoamaBundle.message("action.type.custom")
            ActionType.ANALYZE_UNUSED_CODE -> NekoamaBundle.message("action.type.analyze")
            ActionType.ANALYZE_CODE_DEPS -> NekoamaBundle.message("action.type.analyze")
        }
    }

    fun getComponent(): JComponent = mainPanel

    fun dispose() {
        scope.cancel()
    }
}

/**
 * 简单的日期选择器
 */
private class JDatePicker : JPanel() {
    private val yearSpinner = JSpinner(SpinnerNumberModel(LocalDate.now().year, 2020, 2100, 1))
    private val monthSpinner = JSpinner(SpinnerNumberModel(LocalDate.now().monthValue, 1, 12, 1))
    private val daySpinner = JSpinner(SpinnerNumberModel(LocalDate.now().dayOfMonth, 1, 31, 1))

    init {
        layout = java.awt.FlowLayout()
        add(yearSpinner)
        add(JLabel("-"))
        add(monthSpinner)
        add(JLabel("-"))
        add(daySpinner)

        // 监听月份变化来调整天数
        monthSpinner.addChangeListener {
            updateDayRange()
        }

        // 监听年份变化来调整天数（考虑闰年）
        yearSpinner.addChangeListener {
            updateDayRange()
        }
    }

    private fun updateDayRange() {
        val year = yearSpinner.value as Int
        val month = monthSpinner.value as Int
        val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()

        daySpinner.model = SpinnerNumberModel(
            minOf(daySpinner.value as Int, maxDay),
            1,
            maxDay,
            1
        )
    }

    fun setDate(date: LocalDate) {
        yearSpinner.value = date.year
        monthSpinner.value = date.monthValue
        daySpinner.value = date.dayOfMonth
    }

    fun getDate(): LocalDate {
        return LocalDate.of(
            yearSpinner.value as Int,
            monthSpinner.value as Int,
            daySpinner.value as Int
        )
    }
}