package com.cw2.nekoama.presentation.ui

import com.cw2.nekoama.ai.model.dependency.BusinessEntryPoint
import com.cw2.nekoama.ai.model.dependency.EntryType
import com.cw2.nekoama.core.logging.NekoamaLogger
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.openapi.application.ApplicationManager
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
        try {
            title = NekoamaBundle.message("entryPoint.title")
            setOKButtonText(NekoamaBundle.message("entryPoint.confirm"))
            setCancelButtonText(NekoamaBundle.message("common.cancel"))

            // 初始化数据
            if (entryPoints.isNotEmpty()) {
                allEntryPoints.addAll(entryPoints)
                filteredEntryPoints.addAll(entryPoints)
                NekoamaLogger.info("EntryPointConfirmationDialog",
                    "Dialog initialized with ${entryPoints.size} entry points")
            } else {
                NekoamaLogger.warn("EntryPointConfirmationDialog",
                    "Dialog initialized with empty entry points list")
                // 显示空状态提示信息而不是错误
                showEmptyStateMessage()
            }

            setupUI()
            setupEventListeners()
            updateSummary()
            init()

            NekoamaLogger.info("EntryPointConfirmationDialog", "Dialog successfully initialized")
        } catch (e: Exception) {
            NekoamaLogger.logError("EntryPointConfirmationDialog",
                com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(
                    "Failed to initialize entry point dialog: ${e.message}", e),
                mapOf("entryPointsCount" to entryPoints.size.toString()))
            throw e
        }
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

        if (allEntryPoints.isEmpty()) {
            // 显示空状态面板
            val emptyStatePanel = createEmptyStatePanel()
            tablePanel.add(titleLabel, BorderLayout.NORTH)
            tablePanel.add(emptyStatePanel, BorderLayout.CENTER)
        } else {
            // 显示正常的表格
            setupTable()
            val scrollPane = JBScrollPane(entryPointsTable)
            scrollPane.preferredSize = JBUI.size(600, 400)

            tablePanel.add(titleLabel, BorderLayout.NORTH)
            tablePanel.add(scrollPane, BorderLayout.CENTER)
        }

        return tablePanel
    }

    /**
     * 创建空状态面板
     */
    private fun createEmptyStatePanel(): JPanel {
        val emptyPanel = JPanel(BorderLayout())
        emptyPanel.background = UIUtil.getPanelBackground()
        emptyPanel.border = JBUI.Borders.empty(20, 20)

        // 创建提示信息
        val messagePanel = JPanel()
        messagePanel.layout = BoxLayout(messagePanel, BoxLayout.Y_AXIS)
        messagePanel.background = UIUtil.getPanelBackground()

        val titleLabel = JBLabel("未检测到业务入口点")
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD, titleLabel.font.size + 2f)

        val messageLabel = JBLabel("<html><div style='width: 400px;'>" +
                "可能的原因：<br/>" +
                "• 项目中未包含Spring Web或相关Web框架依赖<br/>" +
                "• IDEA索引尚未构建完成<br/>" +
                "• 入口点类不在当前搜索范围内<br/>" +
                "• 项目中确实没有定义Controller类" +
                "</div></html>")

        val suggestionLabel = JBLabel("<html><div style='width: 400px; color: #666;'>" +
                "建议：<br/>" +
                "1. 检查项目的pom.xml或build.gradle是否包含Spring依赖<br/>" +
                "2. 确保项目已正确导入IDEA并完成索引构建<br/>" +
                "3. 刷新项目或重新导入依赖" +
                "</div></html>")

        // 添加诊断按钮
        val diagnoseButton = JButton("诊断问题")
        diagnoseButton.addActionListener {
            showDiagnosticDialog()
        }

        messagePanel.add(titleLabel)
        messagePanel.add(Box.createVerticalStrut(10))
        messagePanel.add(messageLabel)
        messagePanel.add(Box.createVerticalStrut(15))
        messagePanel.add(suggestionLabel)
        messagePanel.add(Box.createVerticalStrut(20))
        messagePanel.add(diagnoseButton)

        emptyPanel.add(messagePanel, BorderLayout.CENTER)

        return emptyPanel
    }

    /**
     * 显示空状态消息
     */
    private fun showEmptyStateMessage() {
        NekoamaLogger.info("EntryPointConfirmationDialog", "显示空状态提示信息")
        // 这里可以添加更多的用户通知逻辑
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
        try {
            tableModel.setEntryPoints(filteredEntryPoints)
            NekoamaLogger.debug("EntryPointConfirmationDialog",
                "Table data updated with ${filteredEntryPoints.size} filtered entry points")
        } catch (e: Exception) {
            NekoamaLogger.logError("EntryPointConfirmationDialog",
                com.cw2.nekoama.core.exception.NekoamaError.UIError.DialogError(
                    "Failed to update table data: ${e.message}", e),
                mapOf(
                    "filteredCount" to filteredEntryPoints.size.toString(),
                    "totalCount" to allEntryPoints.size.toString()
                ))
        }
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
     * 显示诊断对话框
     */
    private fun showDiagnosticDialog() {
        NekoamaLogger.info("EntryPointConfirmationDialog", "用户点击诊断按钮")

        val diagnosticInfo = buildString {
            appendLine("=== 入口点检测诊断信息 ===")
            appendLine("项目名称: ${project.name}")
            appendLine("项目基础路径: ${project.basePath}")
            appendLine("检测到入口点数量: ${allEntryPoints.size}")
            appendLine()

            appendLine("=== 依赖检查 ===")
            checkAndReportDependencies()
            appendLine()

            appendLine("=== 索引状态 ===")
            checkAndReportIndexStatus()
            appendLine()

            appendLine("=== 搜索范围 ===")
            appendLine("当前使用: projectScope")
            appendLine("建议使用: allScope (包含依赖库)")
            appendLine()

            appendLine("=== 修复建议 ===")
            appendLine("1. 确保项目包含Spring Boot Web依赖")
            appendLine("2. 在Maven/Gradle中刷新项目依赖")
            appendLine("3. 等待IDEA完成索引构建")
            appendLine("4. 检查@RestController类的包路径是否正确")
            appendLine("5. 确保Controller类位于源代码目录中")
        }

        // 显示诊断结果对话框
        val diagnosticDialog = object : DialogWrapper(project) {
            init {
                title = "入口点检测诊断"
                setOKButtonText("关闭")
            }

            override fun createCenterPanel(): JComponent {
                val textArea = JTextArea(diagnosticInfo)
                textArea.isEditable = false
                textArea.font = textArea.font.deriveFont(textArea.font.size - 1f)
                textArea.background = UIUtil.getTextFieldBackground()
                textArea.foreground = UIUtil.getLabelForeground()

                val scrollPane = JBScrollPane(textArea)
                scrollPane.preferredSize = JBUI.size(600, 400)

                return scrollPane
            }
        }

        diagnosticDialog.show()
    }

    /**
     * 检查并报告依赖状态
     */
    private fun checkAndReportDependencies(): StringBuilder {
        val info = StringBuilder()
        val javaPsiFacade = com.intellij.psi.JavaPsiFacade.getInstance(project)
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)

        val springClasses = listOf(
            "org.springframework.context.ApplicationContext" to "Spring Context",
            "org.springframework.stereotype.Controller" to "Spring Stereotype",
            "org.springframework.web.bind.annotation.RestController" to "Spring Web MVC",
            "org.springframework.boot.autoconfigure.SpringBootApplication" to "Spring Boot"
        )

        springClasses.forEach { (className, displayName) ->
            val clazz = javaPsiFacade.findClass(className, scope)
            val status = if (clazz != null) "✓ 已加载" else "✗ 未找到"
            info.appendLine("$displayName: $status")
        }

        return info
    }

    /**
     * 检查并报告索引状态
     */
    private fun checkAndReportIndexStatus(): StringBuilder {
        val info = StringBuilder()

        try {
            // 使用更安全的索引状态检查方式
            val dumbService = com.intellij.openapi.project.DumbService.getInstance(project)
            if (dumbService.isDumb) {
                info.appendLine("文件索引: 正在构建中 (可能影响检测)")
            } else {
                info.appendLine("文件索引: 正常运行")
            }

            // 简化的项目状态检查
            if (!project.basePath.isNullOrEmpty()) {
                info.appendLine("项目基础路径: ${project.basePath}")
                info.appendLine("项目文件索引: 已启用")
            } else {
                info.appendLine("项目基础路径: 未配置")
            }

        } catch (e: Exception) {
            info.appendLine("索引状态检查失败: ${e.message}")
        }

        return info
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
        private lateinit var entryPointsList: MutableList<EntryPointWrapper>

        init {
            // 明确在init块中初始化，确保初始化顺序正确
            entryPointsList = mutableListOf()

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
            // 确保entryPointsList已初始化
            if (!::entryPointsList.isInitialized) {
                entryPointsList = mutableListOf()
            }
            entryPointsList.clear()
            entryPointsList.addAll(entryPoints.map { EntryPointWrapper(it) })
            fireTableDataChanged()
        }

        fun getEntryPoint(row: Int): BusinessEntryPoint? {
            // 添加空安全检查
            return if (::entryPointsList.isInitialized && row >= 0 && row < entryPointsList.size) {
                entryPointsList[row].entryPoint
            } else null
        }

        val entryPoints: List<EntryPointWrapper>
            get() = if (::entryPointsList.isInitialized) entryPointsList else emptyList()

        override fun getRowCount(): Int {
            // 添加空安全检查，防止NPE
            return if (::entryPointsList.isInitialized) entryPointsList.size else 0
        }

        override fun getColumnCount(): Int = 6

        override fun getValueAt(row: Int, col: Int): Any? {
            // 添加空安全检查
            if (!::entryPointsList.isInitialized) {
                return null
            }
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
            // 添加空安全检查
            if (::entryPointsList.isInitialized && col == 0 && row >= 0 && row < entryPointsList.size) {
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