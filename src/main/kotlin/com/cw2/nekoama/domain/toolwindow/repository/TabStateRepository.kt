package com.cw2.nekoama.domain.toolwindow.repository

import com.cw2.nekoama.domain.toolwindow.model.TabState
import kotlin.reflect.KClass

/**
 * Tab状态持久化接口
 *
 * 设计理念：
 * - 这是一个通用的存储能力接口
 * - 不绑定具体业务类型（AI对话、代码分析等都用同一个接口）
 * - 支持任意实现了 TabState 的状态对象
 * - 初始实现是内存版本，未来可替换为文件/数据库实现
 *
 * 使用示例：
 * ```kotlin
 * // AI对话Tab保存状态
 * val aiState = AIDialogTabState(...)
 * repository.saveState("ai-dialog", aiState)
 *
 * // 代码分析Tab保存状态
 * val codeState = CodeAnalysisTabState(...)
 * repository.saveState("code-analysis", codeState)
 * ```
 *
 * 扩展性：
 * - 未来可以实现 PersistentTabStateRepository（文件存储）
 * - 未来可以实现 DatabaseTabStateRepository（数据库）
 * - 切换实现不影响业务代码
 */
interface TabStateRepository {
    /**
     * 保存Tab状态
     *
     * @param tabId Tab唯一标识
     * @param state 状态对象（任意实现TabState的类型）
     */
    fun saveState(tabId: String, state: TabState)

    /**
     * 加载Tab状态
     *
     * @param tabId Tab唯一标识
     * @param clazz 期望的状态类型
     * @return 状态对象，如果不存在返回null
     */
    fun <T : TabState> loadState(tabId: String, clazz: KClass<T>): T?

    /**
     * 删除Tab状态
     *
     * @param tabId Tab唯一标识
     */
    fun deleteState(tabId: String)

    /**
     * 检查状态是否存在
     *
     * @param tabId Tab唯一标识
     */
    fun hasState(tabId: String): Boolean

    /**
     * 清空所有状态
     */
    fun clear()
}
