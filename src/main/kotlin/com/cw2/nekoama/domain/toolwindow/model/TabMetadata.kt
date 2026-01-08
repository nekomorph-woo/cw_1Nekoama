package com.cw2.nekoama.domain.toolwindow.model

import javax.swing.Icon

/**
 * Tab元数据（不可变）
 *
 * @property id Tab唯一标识符
 * @property displayName Tab显示名称（支持国际化）
 * @property icon Tab图标（AllIcons 或自定义）
 */
data class TabMetadata(
    val id: TabId,
    val displayName: String,
    val icon: Icon
) {
    /**
     * Tab唯一标识符（使用值对象避免字符串错误）
     */
    @JvmInline
    value class TabId(val value: String) {
        init {
            require(value.isNotBlank()) { "Tab ID cannot be blank" }
        }
    }
}
