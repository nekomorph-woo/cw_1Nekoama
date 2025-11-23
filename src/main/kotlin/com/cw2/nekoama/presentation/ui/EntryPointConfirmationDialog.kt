package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.ai.model.dependency.BusinessEntryPoint
import com.cw2.nekoama.ai.model.dependency.EntryType
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

/**
 * 场景入口点确认对话框（完整版本）
 *
 * 功能：
 * - 可排序、可过滤的入口点表格组件
 * - 按业务场景分组显示（用户管理、订单处理、支付处理等）
 * - 支持按类型筛选（Controller、Service、Scheduled、EventListener等）
 * - 提供单选、多选、全选操作和详细信息预览
 */
class EntryPointConfirmationDialog(
    private val project: Project,
    private val entryPoints: List<BusinessEntryPoint>
) : DialogWrapper(project) {

    // UI组件
    private val mainPanel = JPanel(BorderLayout())
    private val searchField = JBTextField()
    private val typeFilterComboBox = JComboBox<EntryType>()
    private val scenarioFilterComboBox = JComboBox<String>()
    private val entryPointsTable = JTable()
    private val detailsTextArea = JTextArea()
    private val selectAllCheckBox = JCheckBox(NekoamaBundle.message("entryPoint.selectAll"))
    private val summaryLabel = JBLabel()

    // 数据
    private val tableModel = EntryPointTableModel()
    private val allEntryPoints = mutableListOf<BusinessEntryPoint>()
    private val filteredEntryPoints = mutableListOf<BusinessEntryPoint>()

    init {
        title = NekoamaBundle.message("entryPoint.title")
        setOKButtonText(NekoamaBundle.message("entryPoint.confirm"))
        setCancelButtonText(NekoamaBundle.message("common.cancel"))

        // 初始化数据
        allEntryPoints.addAll(entryPoints)
        filteredEntryPoints.addAll(entryPoints)

        setupUI()
        setupEventListeners()
        updateSummary()
        init()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.border = JBUI.Borders.empty(10)

        // 创建顶部面板（搜索和过滤）
        val topPanel = createTopPanel()

        // 创建中部面板（表格和详情）
        val centerPanel = createMainCenterPanel()

        // 创建底部面板（操作和摘要）
        val bottomPanel = createBottomPanel()

        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(centerPanel, BorderLayout.CENTER)
        mainPanel.add(bottomPanel, BorderLayout.SOUTH)
    }

    /**
     * 创建顶部面板（搜索和过滤）
     */
    private fun createTopPanel(): JPanel {
        val topPanel = JPanel(GridBagLayout())
        topPanel.background = UIUtil.getPanelBackground()
        topPanel.border = JBUI.Borders.empty(0, 0, 10, 0)

        val gbc = GridBagConstraints()

        // 搜索框
        val searchLabel = JBLabel(NekoamaBundle.message("entryPoint.search"))
        searchLabel.font = searchLabel.font.deriveFont(searchLabel.font.style or java.awt.Font.BOLD)

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.insets = JBUI.insets(0, 0, 5, 5)
        gbc.anchor = GridBagConstraints.WEST
        topPanel.add(searchLabel, gbc)

        searchField.toolTipText = NekoamaBundle.message("entryPoint.searchPlaceholder")
        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(0, 0, 5, 10)
        topPanel.add(searchField, gbc)

        // 类型过滤器
        val typeLabel = JBLabel(NekoamaBundle.message("entryPoint.filterByType"))
        typeLabel.font = typeLabel.font.deriveFont(typeLabel.font.style or java.awt.Font.BOLD)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = JBUI.insets(5, 0, 5, 5)
        topPanel.add(typeLabel, gbc)

        setupTypeFilter()
        gbc.gridx = 1
        gbc.gridy = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(5, 0, 5, 10)
        topPanel.add(typeFilterComboBox, gbc)

        // 场景过滤器
        val scenarioLabel = JBLabel(NekoamaBundle.message("entryPoint.filterByScenario"))
        scenarioLabel.font = scenarioLabel.font.deriveFont(scenarioLabel.font.style or java.awt.Font.BOLD)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = JBUI.insets(5, 0, 0, 5)
        topPanel.add(scenarioLabel, gbc)

        setupScenarioFilter()
        gbc.gridx = 1
        gbc.gridy = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(5, 0, 0, 10)
        topPanel.add(scenarioFilterComboBox, gbc)

        return topPanel
    }

    /**
     * 创建中部面板（表格和详情）
     */
    private fun createMainCenterPanel(): JPanel {
        val centerPanel = JPanel(BorderLayout())
        centerPanel.background = UIUtil.getPanelBackground()

        // 左侧：入口点表格
        val tablePanel = createTablePanel()

        // 右侧：详细信息
        val detailsPanel = createDetailsPanel()

        // 使用分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePanel, detailsPanel)
        splitPane.dividerLocation = 600
        splitPane.resizeWeight = 0.7

        centerPanel.add(splitPane, BorderLayout.CENTER)
        return centerPanel
    }

    /**
     * 创建表格面板
     */
    private fun createTablePanel(): JPanel {
        val tablePanel = JPanel(BorderLayout())
        tablePanel.background = UIUtil.getPanelBackground()
        tablePanel.border = JBUI.Borders.empty(0, 0, 0, 10)

        val titleLabel = JBLabel(NekoamaBundle.message("entryPoint.listTitle"))
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD)
        titleLabel.border = JBUI.Borders.empty(0, 0, 5, 0)

        setupTable()

        val scrollPane = JBScrollPane(entryPointsTable)
        scrollPane.preferredSize = JBUI.size(600, 400)

        tablePanel.add(titleLabel, BorderLayout.NORTH)
        tablePanel.add(scrollPane, BorderLayout.CENTER)

        return tablePanel
    }

    /**
     * 设置表格
     */
    private fun setupTable() {
        entryPointsTable.model = tableModel
        entryPointsTable.rowHeight = JBUI.scale(25)
        entryPointsTable.font = entryPointsTable.font.deriveFont(entryPointsTable.font.size - 1f)

        // 设置列宽
        val columnModel = entryPointsTable.columnModel
        columnModel.getColumn(0).preferredWidth = 30  // 选择框
        columnModel.getColumn(1).preferredWidth = 200 // 类名
        columnModel.getColumn(2).preferredWidth = 150 // 方法名
        columnModel.getColumn(3).preferredWidth = 100 // 类型
        columnModel.getColumn(4).preferredWidth = 150 // 业务场景
        columnModel.getColumn(5).preferredWidth = 100 // HTTP映射

        // 设置排序器
        entryPointsTable.rowSorter = TableRowSorter(tableModel)

        // 设置选择监听器
        entryPointsTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                showEntryDetails()
            }
        }

        // 更新表格数据
        updateTableData()
    }

    /**
     * 创建详情面板
     */
    private fun createDetailsPanel(): JPanel {
        val detailsPanel = JPanel(BorderLayout())
        detailsPanel.background = UIUtil.getPanelBackground()

        val titleLabel = JBLabel(NekoamaBundle.message("entryPoint.detailsTitle"))
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD)
        titleLabel.border = JBUI.Borders.empty(0, 0, 5, 0)

        detailsTextArea.isEditable = false
        detailsTextArea.font = detailsTextArea.font.deriveFont(detailsTextArea.font.size - 1f)
        detailsTextArea.background = UIUtil.getTextFieldBackground()
        detailsTextArea.foreground = UIUtil.getLabelForeground()
        detailsTextArea.border = JBUI.Borders.empty(5)

        val scrollPane = JBScrollPane(detailsTextArea)
        scrollPane.preferredSize = JBUI.size(300, 400)

        detailsPanel.add(titleLabel, BorderLayout.NORTH)
        detailsPanel.add(scrollPane, BorderLayout.CENTER)

        return detailsPanel
    }

    /**
     * 创建底部面板（操作和摘要）
     */
    private fun createBottomPanel(): JPanel {
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.background = UIUtil.getPanelBackground()
        bottomPanel.border = JBUI.Borders.empty(10, 0, 0, 0)

        // 左侧：全选和摘要
        val leftPanel = JPanel(BorderLayout())
        leftPanel.background = UIUtil.getPanelBackground()

        leftPanel.add(selectAllCheckBox, BorderLayout.WEST)
        leftPanel.add(summaryLabel, BorderLayout.CENTER)

        // 右侧：统计信息
        val statsPanel = createStatsPanel()

        bottomPanel.add(leftPanel, BorderLayout.WEST)
        bottomPanel.add(statsPanel, BorderLayout.EAST)

        return bottomPanel
    }

    /**
     * 创建统计面板
     */
    private fun createStatsPanel(): JPanel {
        val statsPanel = JPanel()
        statsPanel.background = UIUtil.getPanelBackground()
        statsPanel.layout = BoxLayout(statsPanel, BoxLayout.Y_AXIS)

        val totalLabel = JBLabel(NekoamaBundle.message("entryPoint.total", allEntryPoints.size))
        val selectedLabel = JBLabel(NekoamaBundle.message("entryPoint.selected", 0))
        val filteredLabel = JBLabel(NekoamaBundle.message("entryPoint.filtered", filteredEntryPoints.size))

        totalLabel.font = totalLabel.font.deriveFont(totalLabel.font.size - 1f)
        selectedLabel.font = selectedLabel.font.deriveFont(selectedLabel.font.size - 1f)
        filteredLabel.font = filteredLabel.font.deriveFont(filteredLabel.font.size - 1f)

        totalLabel.foreground = UIUtil.getLabelForeground().darker()
        selectedLabel.foreground = UIUtil.getLabelForeground().darker()
        filteredLabel.foreground = UIUtil.getLabelForeground().darker()

        statsPanel.add(totalLabel)
        statsPanel.add(selectedLabel)
        statsPanel.add(filteredLabel)

        return statsPanel
    }

    /**
     * 设置类型过滤器
     */
    private fun setupTypeFilter() {
        typeFilterComboBox.addItem(null) // 全部
        EntryType.values().distinct().sortedBy { it.name }.forEach { type ->
            typeFilterComboBox.addItem(type)
        }
        typeFilterComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = when (value) {
                    null -> NekoamaBundle.message("entryPoint.allTypes")
                    is EntryType -> getEntryTypeDisplayName(value)
                    else -> value.toString()
                }
                return this
            }
        }
    }

    /**
     * 设置场景过滤器
     */
    private fun setupScenarioFilter() {
        scenarioFilterComboBox.addItem(null) // 全部
        val scenarios = allEntryPoints.map { it.businessScenario }.distinct().sorted()
        scenarios.forEach { scenario ->
            scenarioFilterComboBox.addItem(scenario)
        }
        scenarioFilterComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? String) ?: NekoamaBundle.message("entryPoint.allScenarios")
                return this
            }
        }
    }

    /**
     * 设置事件监听器
     */
    private fun setupEventListeners() {
        // 搜索框监听
        searchField.addActionListener { applyFilters() }
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = applyFilters()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = applyFilters()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = applyFilters()
        })

        // 过滤器监听
        typeFilterComboBox.addActionListener { applyFilters() }
        scenarioFilterComboBox.addActionListener { applyFilters() }

        // 全选框监听
        selectAllCheckBox.addActionListener { e: ActionEvent ->
            val selected = selectAllCheckBox.isSelected
            for (i in 0 until tableModel.rowCount) {
                tableModel.setValueAt(selected, i, 0)
            }
            updateSummary()
        }
    }

    /**
     * 应用过滤器
     */
    private fun applyFilters() {
        val searchText = searchField.text.lowercase().trim()
        val selectedType = typeFilterComboBox.selectedItem as? EntryType
        val selectedScenario = scenarioFilterComboBox.selectedItem as? String

        filteredEntryPoints.clear()
        filteredEntryPoints.addAll(allEntryPoints.filter { entryPoint ->
            val matchesSearch = searchText.isEmpty() ||
                entryPoint.className.lowercase().contains(searchText) ||
                entryPoint.methodName.lowercase().contains(searchText) ||
                entryPoint.businessScenario.lowercase().contains(searchText)

            val matchesType = selectedType == null || entryPoint.entryType == selectedType
            val matchesScenario = selectedScenario == null || entryPoint.businessScenario == selectedScenario

            matchesSearch && matchesType && matchesScenario
        })

        updateTableData()
        updateSummary()
    }

    /**
     * 更新表格数据
     */
    private fun updateTableData() {
        tableModel.setEntryPoints(filteredEntryPoints)
    }

    /**
     * 更新摘要信息
     */
    private fun updateSummary() {
        val selectedCount = tableModel.entryPoints.count { it.selected }
        summaryLabel.text = NekoamaBundle.message("entryPoint.summary", selectedCount, filteredEntryPoints.size, allEntryPoints.size)

        // 更新统计信息
        val statsPanel = mainPanel.components.find { it is JPanel && it.border == JBUI.Borders.empty(10, 0, 0, 0) } as? JPanel
        statsPanel?.components?.filterIsInstance<JBLabel>()?.let { labels ->
            labels.find { it.text.contains("Total") }?.text = NekoamaBundle.message("entryPoint.total", allEntryPoints.size)
            labels.find { it.text.contains("Selected") }?.text = NekoamaBundle.message("entryPoint.selected", selectedCount)
            labels.find { it.text.contains("Filtered") }?.text = NekoamaBundle.message("entryPoint.filtered", filteredEntryPoints.size)
        }

        // 更新全选框状态
        selectAllCheckBox.isSelected = filteredEntryPoints.isNotEmpty() &&
            tableModel.entryPoints.all { it.selected }
    }

    /**
     * 显示入口点详情
     */
    private fun showEntryDetails() {
        val selectedRow = entryPointsTable.selectedRow
        if (selectedRow >= 0) {
            val entryPoint = tableModel.getEntryPoint(entryPointsTable.convertRowIndexToModel(selectedRow))
            if (entryPoint != null) {
                val details = buildString {
                    appendLine(NekoamaBundle.message("entryPoint.detail.className", entryPoint.className))
                    appendLine(NekoamaBundle.message("entryPoint.detail.methodName", entryPoint.methodName))
                    appendLine(NekoamaBundle.message("entryPoint.detail.type", getEntryTypeDisplayName(entryPoint.entryType)))
                    appendLine(NekoamaBundle.message("entryPoint.detail.scenario", entryPoint.businessScenario))

                    if (entryPoint.httpMapping != null) {
                        appendLine(NekoamaBundle.message("entryPoint.detail.httpMapping", entryPoint.httpMapping))
                    }

                    if (entryPoint.annotations.isNotEmpty()) {
                        appendLine(NekoamaBundle.message("entryPoint.detail.annotations", entryPoint.annotations.joinToString(", ")))
                    }

                    if (entryPoint.parameters.isNotEmpty()) {
                        appendLine()
                        appendLine(NekoamaBundle.message("entryPoint.detail.parameters"))
                        entryPoint.parameters.forEachIndexed { index, param ->
                            appendLine("  ${index + 1}. ${param.type} ${param.name}")
                        }
                    }
                }
                detailsTextArea.text = details
            }
        } else {
            detailsTextArea.text = ""
        }
    }

    /**
     * 获取入口类型显示名称
     */
    private fun getEntryTypeDisplayName(entryType: EntryType): String {
        return when (entryType) {
            EntryType.CONTROLLER -> NekoamaBundle.message("entryPoint.type.controller")
            EntryType.SERVICE -> NekoamaBundle.message("entryPoint.type.service")
            EntryType.SCHEDULED -> NekoamaBundle.message("entryPoint.type.scheduled")
            EntryType.EVENT_LISTENER -> NekoamaBundle.message("entryPoint.type.eventListener")
            EntryType.MESSAGE_CONSUMER -> NekoamaBundle.message("entryPoint.type.messageConsumer")
            EntryType.MAIN -> NekoamaBundle.message("entryPoint.type.main")
            // API类型已从枚举中移除，不需要处理
        }
    }

    /**
     * 获取确认的入口点
     */
    fun getConfirmedEntryPoints(): List<String> {
        return tableModel.entryPoints
            .filter { it.selected }
            .map { "${it.entryPoint.className}.${it.entryPoint.methodName}" }
    }

    override fun createCenterPanel(): JComponent = mainPanel

    /**
     * 入口点表格模型
     */
    private inner class EntryPointTableModel : DefaultTableModel() {
        private val entryPointsList = mutableListOf<EntryPointWrapper>()

        init {
            columnIdentifiers = Vector<Any>(listOf(
                NekoamaBundle.message("entryPoint.column.select"),
                NekoamaBundle.message("entryPoint.column.className"),
                NekoamaBundle.message("entryPoint.column.methodName"),
                NekoamaBundle.message("entryPoint.column.type"),
                NekoamaBundle.message("entryPoint.column.scenario"),
                NekoamaBundle.message("entryPoint.column.httpMapping")
            ))
        }

        fun setEntryPoints(entryPoints: List<BusinessEntryPoint>) {
            entryPointsList.clear()
            entryPointsList.addAll(entryPoints.map { EntryPointWrapper(it) })
            fireTableDataChanged()
        }

        fun getEntryPoint(row: Int): BusinessEntryPoint? {
            return if (row >= 0 && row < entryPointsList.size) {
                entryPointsList[row].entryPoint
            } else null
        }

        val entryPoints: List<EntryPointWrapper>
            get() = entryPointsList

        override fun getRowCount(): Int = entryPointsList.size

        override fun getColumnCount(): Int = 6

        override fun getValueAt(row: Int, col: Int): Any? {
            val wrapper = entryPointsList.getOrNull(row) ?: return null
            return when (col) {
                0 -> wrapper.selected
                1 -> wrapper.entryPoint.className.substringAfterLast(".")
                2 -> wrapper.entryPoint.methodName
                3 -> getEntryTypeDisplayName(wrapper.entryPoint.entryType)
                4 -> wrapper.entryPoint.businessScenario
                5 -> wrapper.entryPoint.httpMapping ?: "-"
                else -> null
            }
        }

        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (col == 0 && row >= 0 && row < entryPointsList.size) {
                entryPointsList[row].selected = value as? Boolean ?: false
                fireTableCellUpdated(row, col)
                updateSummary()
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> java.lang.Boolean::class.java
                else -> java.lang.String::class.java
            }
        }

        override fun isCellEditable(row: Int, column: Int): Boolean = column == 0
    }

    /**
     * 入口点包装器
     */
    private data class EntryPointWrapper(
        val entryPoint: BusinessEntryPoint,
        var selected: Boolean = true
    )
}