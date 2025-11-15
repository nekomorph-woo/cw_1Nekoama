package com.cw2.nekoama.presentation.settings

import com.cw2.nekoama.data.settings.NekoamaSettings
import com.cw2.nekoama.data.settings.NekoamaSecureStorage
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.cw2.nekoama.presentation.notifications.NekoamaNotifier
import com.cw2.nekoama.core.network.ProxyConnectionTester
import com.cw2.nekoama.core.network.ProxyInitializationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.JBColor
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * 设置页面（最简实现）
 *
 * 说明：
 * - 遵循即时校验与数据绑定的基本模式
 * - 并未引入复杂的表单框架，控制在最小变更
 * - 本次补充 AI 服务配置区（提供商/端点/API Key/温度）与“连接测试”按钮
 */
class NekoamaConfigurable : Configurable {

    private val panel = JPanel(BorderLayout())
    private val form = JPanel(GridBagLayout())

    // 功能开关区域
    private val enableNaming = JCheckBox(NekoamaBundle.message("settings.enable.naming"))
    private val enableComment = JCheckBox(NekoamaBundle.message("settings.enable.comment"))
    private val cacheEnabled = JCheckBox(NekoamaBundle.message("settings.cache.enabled"))
    private val autoTrigger = JCheckBox(NekoamaBundle.message("settings.auto.trigger"))
    private val depthLabel = JLabel(NekoamaBundle.message("settings.context.depth"))
    private val depthSlider = JSlider(1, 3, 2)
    private val depthValueLabel = JLabel(NekoamaBundle.message("settings.depth.value", "2", "1", "3")) // 显示当前值和范围

    // AI 服务配置区域
    private val endpointLabel = JLabel(NekoamaBundle.message("settings.ai.endpoint"))
    private val endpointField = JTextField(24)
    private val endpointHelpLabel = JLabel(NekoamaBundle.message("settings.ai.endpoint.hint"))
    private val modelLabel = JLabel(NekoamaBundle.message("settings.ai.model"))
    private val modelField = JTextField(24)
    private val apiKeyLabel = JLabel(NekoamaBundle.message("settings.ai.apikey"))
    private val apiKeyField = JPasswordField(24)
    // 显示/隐藏密钥切换按钮（避免在 EDT 泄露真实值，仅切换回显）
    private val toggleSecretButton = JButton(NekoamaBundle.message("settings.ai.apikey.show"))
    // 清除已保存密钥按钮
    private val clearSecretButton = JButton(NekoamaBundle.message("settings.ai.apikey.clear"))
    private val tempLabel = JLabel(NekoamaBundle.message("settings.ai.temperature"))
    private val tempSlider = JSlider(0, 100, 70)
    private val tempValueLabel = JLabel(NekoamaBundle.message("settings.temperature.value", "0.70", "0.00", "1.00")) // 显示当前值和范围
    private val testButton = JButton(NekoamaBundle.message("settings.ai.test"))
    private val testResultLabel = JLabel("")

    // 性能优化区域
    private val perfSectionLabel = JLabel(NekoamaBundle.message("settings.perf.section"))
    private val timeoutLabel = JLabel(NekoamaBundle.message("settings.perf.timeout"))
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30000, 1000, 1200000, 1000))

    // 偏好设置区域
    private val prefSectionLabel = JLabel(NekoamaBundle.message("settings.pref.section"))
    private val langPrefLabel = JLabel(NekoamaBundle.message("settings.pref.language"))
    private val langPrefCombo = JComboBox(arrayOf("AUTO", "EN", "ZH"))
    private val namingStyleLabel = JLabel(NekoamaBundle.message("settings.pref.namingStyle"))
    private val namingStyleCombo = JComboBox(arrayOf("CAMEL_CASE", "SNAKE_CASE"))
    private val commentFormatLabel = JLabel(NekoamaBundle.message("settings.pref.commentFormat"))
    private val commentFormatCombo = JComboBox(arrayOf("LINE", "JAVADOC", "JSDOC"))

    private val settings: NekoamaSettings = NekoamaSettings.getInstance()

    // 密钥显示状态（仅影响回显，不改变真实存储）
    private var secretVisible: Boolean = false
    private var defaultEchoChar: Char = '\u2022'
    
    // 缓存的 API Key 值，避免重复调用安全存储
    private var cachedApiKey: String = ""

    // API KEY 加载状态标记，确保数据已准备就绪
    private var isApiKeyLoaded: Boolean = false

    init {
        // 设置区域标题的字体为粗体，使其更加醒目
        val boldFont = perfSectionLabel.font.deriveFont(java.awt.Font.BOLD)
        perfSectionLabel.font = boldFont
        prefSectionLabel.font = boldFont

        // 表单布局（左标签右控件），添加合理的间距使界面更美观
        val c = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = java.awt.Insets(5, 5, 5, 5) // 默认间距：上、左、下、右各5像素
        }
        // 功能开关
        form.add(enableNaming, c)
        c.gridy++
        form.add(enableComment, c)
        c.gridy++
        form.add(cacheEnabled, c)
        c.gridy++
        form.add(autoTrigger, c)
        c.gridy++
        form.add(depthLabel, c)
        c.gridx = 1
        form.add(depthSlider, c)
        c.gridx = 2
        form.add(depthValueLabel, c)

        // ===== AI 服务配置区域 =====
        c.gridx = 0
        c.gridy++
        c.insets = java.awt.Insets(20, 5, 5, 5) // 区域顶部增加间距
        form.add(endpointLabel, c)
        c.insets = java.awt.Insets(5, 5, 5, 5) // 恢复默认间距
        c.gridx = 1
        form.add(endpointField, c)
        c.gridx = 2
        form.add(endpointHelpLabel, c)

        // 模型名称（仅 Custom 模式启用）
        c.gridx = 0
        c.gridy++
        form.add(modelLabel, c)
        c.gridx = 1
        form.add(modelField, c)

        c.gridx = 0
        c.gridy++
        form.add(apiKeyLabel, c)
        c.gridx = 1
        form.add(apiKeyField, c)

        c.gridx = 0
        c.gridy++
        form.add(tempLabel, c)
        c.gridx = 1
        form.add(tempSlider, c)
        c.gridx = 2
        form.add(tempValueLabel, c)

        // 测试按钮行：从左开始排列三个按钮
        c.gridx = 0
        c.gridy++
        c.insets = java.awt.Insets(10, 5, 5, 5) // 测试按钮上方增加间距
        form.add(testButton, c)
        c.insets = java.awt.Insets(10, 5, 5, 5) // 保持相同间距
        c.gridx = 1
        form.add(toggleSecretButton, c)
        c.gridx = 2
        form.add(clearSecretButton, c)

        // 测试结果标签
        c.gridx = 0
        c.gridy++
        c.insets = java.awt.Insets(20, 5, 20, 5) // 测试结果下方增加间距
        form.add(testResultLabel, c)

        // ===== 性能优化设置区域 =====
        c.gridx = 0
        c.gridy++
        c.insets = java.awt.Insets(20, 5, 10, 5) // 区域标题：上间距20，下间距10
        form.add(perfSectionLabel, c)

        // 请求超时（毫秒）
        c.gridy++
        c.insets = java.awt.Insets(5, 5, 5, 5) // 恢复默认间距
        form.add(timeoutLabel, c)
        c.gridx = 1
        form.add(timeoutSpinner, c)

        // ===== 偏好设置区域 =====
        c.gridx = 0
        c.gridy++
        c.insets = java.awt.Insets(20, 5, 10, 5) // 区域标题：上间距20，下间距10
        form.add(prefSectionLabel, c)

        // 语言偏好（生成内容）
        c.gridy++
        c.insets = java.awt.Insets(5, 5, 5, 5) // 恢复默认间距
        form.add(langPrefLabel, c)
        c.gridx = 1
        form.add(langPrefCombo, c)

        // 命名风格
        c.gridx = 0
        c.gridy++
        form.add(namingStyleLabel, c)
        c.gridx = 1
        form.add(namingStyleCombo, c)

        // 注释格式
        c.gridx = 0
        c.gridy++
        form.add(commentFormatLabel, c)
        c.gridx = 1
        form.add(commentFormatCombo, c)

        // 记录默认的回显字符，用于显示/隐藏切换
        defaultEchoChar = apiKeyField.echoChar

        // 上下文分析深度滑块监听器：更新数值标签
        depthSlider.addChangeListener {
            depthValueLabel.text = NekoamaBundle.message("settings.depth.value", depthSlider.value.toString(), "1", "3")
        }

        // 模型温度滑块监听器：更新数值标签（显示为0.00-1.00范围）
        tempSlider.addChangeListener {
            val tempValue = tempSlider.value / 100.0
            tempValueLabel.text = String.format(NekoamaBundle.message("settings.temperature.value.format"), tempValue)
        }

        // 显示/隐藏 API Key 回显（中文说明：仅改变 JPasswordField 回显，不改变存储安全性）
        toggleSecretButton.addActionListener {
            secretVisible = !secretVisible
            apiKeyField.echoChar = if (secretVisible) 0.toChar() else defaultEchoChar
            toggleSecretButton.text = if (secretVisible) NekoamaBundle.message("settings.ai.apikey.hide") else NekoamaBundle.message("settings.ai.apikey.show")
        }

        // 清除已保存的 API Key（安全存储与内存字段同时清理）
        clearSecretButton.addActionListener {
            apiKeyField.text = ""
            // 清空缓存和重置加载状态
            cachedApiKey = ""
            isApiKeyLoaded = true
            ApplicationManager.getApplication().executeOnPooledThread {
                NekoamaSecureStorage.clearApiKey()
            }
            // 同时清空旧的明文字段，便于迁移
            settings.apiKey = ""
            NekoamaNotifier.info(NekoamaBundle.message("notification.success"))
        }

        // 测试连接：使用修复后的代理测试方法
        testButton.addActionListener {
            testButton.isEnabled = false
            testResultLabel.text = NekoamaBundle.message("settings.ai.test.connecting")
            testResultLabel.foreground = JBColor.CYAN

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val endpoint = endpointField.text.trim().ifEmpty { "https://api.openai.com" }

                    // 使用新的代理测试方法
                    val result = runBlocking {
                        ProxyConnectionTester.testCurrentIDEAProxy(endpoint)
                    }

                    ApplicationManager.getApplication().invokeLater({
                        if (result.success) {
                            testResultLabel.text = if (result.statusCode == 200) {
                                NekoamaBundle.message("settings.ai.test.success.proxy", result.responseTime)
                            } else {
                                NekoamaBundle.message("settings.ai.test.success.reachable", result.statusCode, result.responseTime)
                            }
                            testResultLabel.foreground = java.awt.Color.GREEN.darker()
                        } else {
                            val failMessage = NekoamaBundle.message("settings.ai.test.failed", result.message)
                            testResultLabel.text = failMessage
                            testResultLabel.foreground = JBColor.RED
                        }
                        testButton.isEnabled = true
                    }, ModalityState.any())
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater({
                        testResultLabel.text = NekoamaBundle.message("settings.ai.test.exception", e.message ?: "")
                        testResultLabel.foreground = JBColor.RED
                        testButton.isEnabled = true
                    }, ModalityState.any())
                }
            }
        }

        panel.add(form, BorderLayout.NORTH)

        // 同步初始化API KEY，确保首次打开时数据已准备就绪
        initializeApiKey()
    }

    /**
     * 初始化API KEY，确保首次打开时数据已准备就绪
     */
    private fun initializeApiKey() {
        // 在后台线程加载 API Key
        ApplicationManager.getApplication().executeOnPooledThread {
            cachedApiKey = NekoamaSecureStorage.getApiKeySync()
            isApiKeyLoaded = true
            // 在EDT中更新UI状态，确保密码框正确显示已保存状态
            ApplicationManager.getApplication().invokeLater {
                updateApiKeyFieldState()
            }
        }
    }

    /**
     * 更新API KEY输入框状态
     * 确保密码框正确显示已保存状态
     */
    private fun updateApiKeyFieldState() {
        if (isApiKeyLoaded) {
            // 如果有缓存的API KEY，但输入框为空，则填入缓存的值
            if (apiKeyField.password.isEmpty() && cachedApiKey.isNotEmpty()) {
                apiKeyField.text = cachedApiKey
            }
            // 确保密码框状态正确
            if (!secretVisible) {
                apiKeyField.echoChar = defaultEchoChar
                toggleSecretButton.text = NekoamaBundle.message("settings.ai.apikey.show")
            }
        }
    }

    /**
     * 获取API KEY值，如果缓存未加载则同步读取
     */
    private fun getApiKey(): String {
        return if (isApiKeyLoaded) {
            cachedApiKey
        } else {
            // 如果缓存未加载，同步读取以确保数据正确性
            NekoamaSecureStorage.getApiKeySync().also {
                cachedApiKey = it
                isApiKeyLoaded = true
            }
        }
    }

    override fun getDisplayName(): String = NekoamaBundle.message("settings.title")

    override fun createComponent(): JComponent = panel

    override fun isModified(): Boolean {
        return enableNaming.isSelected != settings.enableNaming ||
            enableComment.isSelected != settings.enableComment ||
            cacheEnabled.isSelected != settings.cacheEnabled ||
            autoTrigger.isSelected != settings.autoTrigger ||
            depthSlider.value != settings.contextDepth ||
            endpointField.text != settings.apiEndpoint ||
            modelField.text != settings.model ||
            // 使用getApiKey()确保获取到正确的API KEY值，避免缓存未加载的问题
            String(apiKeyField.password) != getApiKey() ||
            tempSlider.value != settings.modelTemperature ||
            (timeoutSpinner.value as Number).toInt() != settings.requestTimeoutMs ||
            langPrefCombo.selectedItem?.toString() != settings.languagePreference ||
            namingStyleCombo.selectedItem?.toString() != settings.namingStyle ||
            commentFormatCombo.selectedItem?.toString() != settings.commentFormat
    }

    override fun apply() {
        // 保存到持久化组件（中文说明：后续将迁移敏感字段到安全存储）
        settings.enableNaming = enableNaming.isSelected
        settings.enableComment = enableComment.isSelected
        settings.cacheEnabled = cacheEnabled.isSelected
        settings.autoTrigger = autoTrigger.isSelected
        settings.contextDepth = depthSlider.value

        settings.aiProvider = "Custom"  // 固定为 Custom
        settings.apiEndpoint = endpointField.text.trim()
        settings.model = modelField.text.trim()
        // 将密钥写入 IDE 安全存储
        val newKey = String(apiKeyField.password).trim()
        // 同时更新缓存值，保持一致性
        cachedApiKey = newKey
        isApiKeyLoaded = true
        ApplicationManager.getApplication().executeOnPooledThread {
            NekoamaSecureStorage.setApiKey(newKey)
        }
        // 清空旧的明文字段，保留向后兼容字段但不再写入
        settings.apiKey = ""
        settings.modelTemperature = tempSlider.value

        // 高级性能设置（仅保存数值，实际联动由相关组件读取使用）
        settings.requestTimeoutMs = (timeoutSpinner.value as Number).toInt()

        // 偏好设置
        settings.languagePreference = langPrefCombo.selectedItem?.toString() ?: settings.languagePreference
        settings.namingStyle = namingStyleCombo.selectedItem?.toString() ?: settings.namingStyle
        settings.commentFormat = commentFormatCombo.selectedItem?.toString() ?: settings.commentFormat
    }

    override fun reset() {
        // 从持久化组件恢复
        enableNaming.isSelected = settings.enableNaming
        enableComment.isSelected = settings.enableComment
        cacheEnabled.isSelected = settings.cacheEnabled
        autoTrigger.isSelected = settings.autoTrigger
        depthSlider.value = settings.contextDepth
        depthValueLabel.text = NekoamaBundle.message("settings.depth.value", settings.contextDepth.toString(), "1", "3") // 更新深度值标签

        endpointField.text = settings.apiEndpoint
        modelField.text = settings.model

        // 使用getApiKey()确保获取到正确的API KEY值，处理异步加载时序问题
        val currentApiKey = getApiKey()
        apiKeyField.text = currentApiKey

        // 重置显示状态为隐藏
        secretVisible = false
        toggleSecretButton.text = NekoamaBundle.message("settings.ai.apikey.show")
        apiKeyField.echoChar = defaultEchoChar

        tempSlider.value = settings.modelTemperature
        tempValueLabel.text = String.format(NekoamaBundle.message("settings.temperature.value.format"), settings.modelTemperature / 100.0) // 更新温度值标签

        // 高级性能设置
        timeoutSpinner.value = settings.requestTimeoutMs

        // 偏好设置
        langPrefCombo.selectedItem = settings.languagePreference
        namingStyleCombo.selectedItem = settings.namingStyle
        commentFormatCombo.selectedItem = settings.commentFormat
    }
}
