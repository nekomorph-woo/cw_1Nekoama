package com.cw2.nekoama.infrastructure.toolwindow

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Tab主题适配器
 *
 * 职责：
 * - 提供主题感知的颜色和字体
 * - 支持深色/浅色主题切换
 * - 遵循 intellij-theme-adaptation-rules.md
 */
object TabThemeManager {
    /**
     * 获取Tab背景色
     */
    fun getTabBackgroundColor(): Color {
        return UIUtil.getPanelBackground()
    }

    /**
     * 获取Tab文本颜色
     */
    fun getTabTextColor(): Color {
        return UIUtil.getLabelForeground()
    }

    /**
     * 获取边框颜色
     */
    fun getBorderColor(): Color {
        return JBColor.GRAY
    }

    /**
     * 获取激活Tab的背景色
     */
    fun getActiveTabBackgroundColor(): Color {
        return JBColor(Color(0x3C3F41), Color(0x4B6EAF))
    }

    /**
     * 获取激活Tab的文本颜色
     */
    fun getActiveTabTextColor(): Color {
        return JBColor(Color.WHITE, Color.WHITE)
    }
}
