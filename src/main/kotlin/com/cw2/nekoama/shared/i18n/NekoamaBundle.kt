package com.cw2.nekoama.shared.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.NekoamaBundle"

/**
 * Nekoama 消息绑定工具
 *
 * 为什么需要：
 * - 统一管理多语言资源，避免硬编码文案
 * - 便于后续国际化扩展与动态切换
 */
internal object NekoamaBundle : DynamicBundle(BUNDLE) {
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        // 使用 IDE 当前语言设置
        return getMessage(key, *params)
    }
}
