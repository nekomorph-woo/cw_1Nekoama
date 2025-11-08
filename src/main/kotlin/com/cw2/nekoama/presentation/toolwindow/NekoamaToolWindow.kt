package com.cw2.nekoama.presentation.toolwindow

import com.cw2.nekoama.core.metrics.MetricsCollector
import com.cw2.nekoama.presentation.messages.NekoamaBundle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Nekoama 工具窗口主面板
 *
 * 显示使用统计和核心功能入口。
 */
class NekoamaToolWindow {
    fun getComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(12)

        // 顶部欢迎文本
        panel.add(JBLabel(NekoamaBundle.message("toolwindow.welcome")), BorderLayout.NORTH)

        // 中部：使用统计（今日/总计/成功率/平均耗时）
        val center = JPanel()
        center.layout = java.awt.GridLayout(0, 1, 0, 6)
        val section = JBLabel(NekoamaBundle.message("toolwindow.stats.section"))
        val todayLabel = JBLabel()
        val totalLabel = JBLabel()
        val successLabel = JBLabel()
        val latencyLabel = JBLabel()
        // Token 使用统计
        val tokenSection = JBLabel(NekoamaBundle.message("toolwindow.stats.tokens.section"))
        val tokensTodayLabel = JBLabel()
        val tokensWeekLabel = JBLabel()
        val tokensMonthLabel = JBLabel()
        val tokensTotalLabel = JBLabel()
        val refresh = JButton(NekoamaBundle.message("toolwindow.stats.refresh"))

        fun updateStats() {
            val s = MetricsCollector.snapshot()
            todayLabel.text = NekoamaBundle.message("toolwindow.stats.today") + ": " + s.today
            totalLabel.text = NekoamaBundle.message("toolwindow.stats.total") + ": " + s.total
            val ratePercent = String.format("%.0f%%", s.successRate * 100.0)
            successLabel.text = NekoamaBundle.message("toolwindow.stats.successRate") + ": " + ratePercent
            latencyLabel.text = NekoamaBundle.message("toolwindow.stats.avgLatency") + ": " + s.averageLatencyMs

            tokensTodayLabel.text = NekoamaBundle.message("toolwindow.stats.tokens.today") + ": " + s.tokensToday
            tokensWeekLabel.text = NekoamaBundle.message("toolwindow.stats.tokens.week") + ": " + s.tokensWeek
            tokensMonthLabel.text = NekoamaBundle.message("toolwindow.stats.tokens.month") + ": " + s.tokensMonth
            tokensTotalLabel.text = NekoamaBundle.message("toolwindow.stats.tokens.total") + ": " + s.tokensTotal
        }

        refresh.addActionListener { updateStats() }

        center.add(section)
        center.add(todayLabel)
        center.add(totalLabel)
        center.add(successLabel)
        center.add(latencyLabel)
        // Token 区块
        center.add(tokenSection)
        center.add(tokensTodayLabel)
        center.add(tokensWeekLabel)
        center.add(tokensMonthLabel)
        center.add(tokensTotalLabel)
        center.add(refresh)

        panel.add(center, BorderLayout.CENTER)

        // 初始化一次
        updateStats()
        return panel
    }
}
