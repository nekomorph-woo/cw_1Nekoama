# 可扩展Tool Window框架实现计划

## 1. Overview

**目标：** 构建一个支持扩展的IntelliJ IDEA侧边弹窗（Tool Window）系统，支持通过工厂模式动态注册Tab，并提供通用的状态持久化能力。

**业务价值：**
- 为AI对话、代码分析等模块提供统一的UI容器
- 通过事件总线实现Tab间松耦合通信
- 通用持久化接口支持各Tab独立管理自己的状态

**范围：**
- [x] In Scope:
  - Tool Window框架（右侧停靠）
  - Tab生命周期管理（创建、激活、销毁）
  - Tab事件总线（跨Tab通信）
  - 通用持久化接口（预留扩展能力）
  - 工厂模式Tab注册（内部扩展）
  - WelcomeTab示例
  - 基础主题适配（深色/浅色）

- [ ] Out of Scope:
  - 具体业务Tab实现（AI对话、代码分析等后续迭代）
  - 持久化的具体存储实现（仅提供接口和内存实现）
  - 高级UI组件（复杂图表、动画等）

## 2. Tech Stack

**Backend/Core:**
- **语言：** Kotlin (JVM 21)
- **架构：** DDD分层（Domain + Infrastructure + Interfaces）
- **并发：** 协程 + EDT线程管理（遵循edt-threading-rules.md）

**Frontend/UI:**
- **UI框架：** Swing (JPanel, JTabbedPane)
- **主题：** IntelliJ Platform Theme API (UIUtil, JBUI)
- **布局：** BorderLayout, FormBuilder

**数据/存储:**
- **状态管理：** 内存Map（初始实现）
- **持久化接口：** Domain层定义，Infrastructure层实现
- **序列化：** JSON（使用 kotlinx.serialization 或 gson）

**测试：**
- **单元测试：** JUnit 5 + MockK
- **UI测试：** （暂不包含，后续可添加 Fest Swing）

## 3. Architecture Design

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    IntelliJ Platform                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              interfaces (接口适配层)                       │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  NekoamaToolWindowFactory                           │  │  │
│  │  │  - 实现 ToolWindowFactory 接口                       │  │  │
│  │  │  - 创建 Tool Window 并注册到 IDE                    │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  NekoamaToolWindowContent                          │  │  │
│  │  │  - Tool Window 主内容面板                           │  │  │
│  │  │  - 组装 TabManager 和具体Tab实现                    │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  TabFactories (工厂注册)                            │  │  │
│  │  │  - 注册所有Tab工厂实例                              │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              ▲                                   │
│                              │ 依赖注入                           │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │            domain (领域模型层)                              │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  toolwindow/model/                                  │  │  │
│  │  │  - TabMetadata: Tab元数据(ID、标题、图标)           │  │  │
│  │  │  - TabLifecycle: Tab生命周期状态枚举                │  │  │
│  │  │  - TabEvent: 事件定义（密封类）                      │  │  │
│  │  │  - TabState: Tab状态数据接口                        │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  toolwindow/service/                                │  │  │
│  │  │  - TabCoordinatorService: Tab协调服务               │  │  │
│  │  │  - TabEventBus: 事件总线接口                        │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  toolwindow/repository/                             │  │  │
│  │  │  - TabStateRepository: 状态持久化接口（通用能力）   │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              ▲                                   │
│                              │ 实现接口                           │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │         infrastructure (基础设施层)                         │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  toolwindow/                                        │  │  │
│  │  │  - InMemoryTabEventBus: 内存事件总线实现             │  │  │
│  │  │  - InMemoryTabStateRepository: 内存状态存储实现     │  │  │
│  │  │  - TabThemeManager: 主题适配器                      │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 数据模型层

**路径：** `src/main/kotlin/com/cw2/nekoama/domain/toolwindow/model/`

#### `TabMetadata.kt`
```kotlin
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
```

#### `TabLifecycle.kt`
```kotlin
/**
 * Tab生命周期状态
 */
enum class TabLifecycle {
    /**
     * Tab已创建但未初始化UI
     */
    CREATED,

    /**
     * Tab正在初始化UI组件
     */
    INITIALIZING,

    /**
     * Tab已就绪，可以显示
     */
    READY,

    /**
     * Tab当前激活（用户选中）
     */
    ACTIVE,

    /**
     * Tab已失活（用户切换到其他Tab）
     */
    INACTIVE,

    /**
     * Tab已销毁
     */
    DESTROYED
}
```

#### `TabEvent.kt`
```kotlin
/**
 * Tab事件定义（密封类，类型安全的模式匹配）
 *
 * 设计原则：
 * - 事件是不可变的
 * - 使用密封类确保完整的类型安全
 * - 支持事件携带任意类型的payload
 */
sealed class TabEvent {
    /**
     * Tab激活事件
     */
    data class TabActivated(
        val tabId: TabMetadata.TabId,
        val timestamp: Long = System.currentTimeMillis()
    ) : TabEvent()

    /**
     * Tab失活事件
     */
    data class TabDeactivated(
        val tabId: TabMetadata.TabId,
        val timestamp: Long = System.currentTimeMillis()
    ) : TabEvent()

    /**
     * 通用数据事件（用于Tab间业务数据传递）
     *
     * 使用示例：
     * - 代码选择事件：DataType = CodeSelectionData
     * - AI消息事件：DataType = AIMessageData
     *
     * @param sourceId 发布事件的Tab ID
     * @param dataType 数据类型标识（用于反序列化）
     * @param payload 任意序列化数据
     */
    data class DataEvent<T : Any>(
        val sourceId: TabMetadata.TabId,
        val dataType: String,
        val payload: T
    ) : TabEvent()
}
```

#### `TabState.kt`
```kotlin
/**
 * Tab状态数据接口
 *
 * 设计理念：
 * - 这是一个通用能力接口，不绑定具体业务
 * - 每个Tab实现自己的State类
 * - 框架只负责存储，不关心State的具体内容
 *
 * 使用示例：
 * ```kotlin
 * // AI对话Tab的状态
 * data class AIDialogTabState(
 *     val conversationHistory: List<ChatMessage>,
 *     val selectedCodeFragments: List<CodeFragment>
 * ) : TabState
 *
 * // 代码分析Tab的状态
 * data class CodeAnalysisTabState(
 *     val analysisResults: Map<String, CodeSmell>,
 *     val selectedFile: String?
 * ) : TabState
 * ```
 *
 * @property version 状态版本号（用于迁移和兼容性检查）
 */
interface TabState {
    val version: Int
        get() = 1

    /**
     * 验证状态是否有效
     */
    fun validate(): Result<Unit>
}
```

### 3.3 核心逻辑层

**路径：** `src/main/kotlin/com/cw2/nekoama/domain/toolwindow/service/`

#### `TabEventBus.kt` (接口)
```kotlin
/**
 * Tab事件总线接口
 *
 * 职责：
 * - 管理事件订阅者
 * - 分发事件到订阅者
 * - 线程安全保证
 *
 * 设计模式：观察者模式
 */
interface TabEventBus {
    /**
     * 订阅事件
     *
     * @param eventType 事件类型（KClass）
     * @param subscriber 订阅者（Tab ID 或 事件处理器）
     * @param handler 事件处理回调
     */
    fun <T : TabEvent> subscribe(
        eventType: KClass<T>,
        subscriber: TabMetadata.TabId,
        handler: (T) -> Unit
    ): Disposable

    /**
     * 发布事件
     *
     * @param event 事件实例
     */
    fun publish(event: TabEvent)

    /**
     * 取消订阅
     *
     * @param subscriber 订阅者ID
     */
    fun unsubscribe(subscriber: TabMetadata.TabId)

    /**
     * 清空所有订阅
     */
    fun clear()
}
```

#### `TabCoordinatorService.kt`
```kotlin
/**
 * Tab协调服务
 *
 * 职责：
 * - 管理Tab生命周期
 * - 协调Tab间的切换
 * - 集成事件总线和状态持久化
 *
 * 依赖注入：通过构造函数注入依赖
 */
class TabCoordinatorService(
    private val eventBus: TabEventBus,
    private val stateRepository: TabStateRepository
) {
    private val _tabs = mutableMapOf<TabMetadata.TabId, BaseTab>()
    val tabs: Map<TabMetadata.TabId, BaseTab> = _tabs

    private var _activeTabId: TabMetadata.TabId? = null
    val activeTabId: TabMetadata.TabId? get() = _activeTabId

    /**
     * 注册Tab
     */
    fun registerTab(tab: BaseTab) {
        // 实现细节...
    }

    /**
     * 激活Tab（用户切换）
     */
    fun activateTab(tabId: TabMetadata.TabId) {
        // 1. 触发当前activeTab的 onDeactivated()
        // 2. 保存当前Tab状态到 repository
        // 3. 切换到新Tab
        // 4. 从 repository 加载新Tab状态
        // 5. 触发新Tab的 onActivated()
        // 6. 发布 TabActivated 事件
    }

    /**
     * 获取Tab状态
     */
    fun <T : TabState> getTabState(tabId: TabMetadata.TabId): T? {
        // 实现细节...
    }

    /**
     * 保存Tab状态
     */
    fun saveTabState(tabId: TabMetadata.TabId, state: TabState) {
        // 实现细节...
    }
}
```

### 3.4 状态持久化层（通用能力）

**路径：** `src/main/kotlin/com/cw2/nekoama/domain/toolwindow/repository/`

#### `TabStateRepository.kt` (接口)
```kotlin
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
     * @param T 期望的状态类型
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
```

### 3.5 基础设施层实现

**路径：** `src/main/kotlin/com/cw2/nekoama/infrastructure/toolwindow/`

#### `InMemoryTabEventBus.kt`
```kotlin
/**
 * 内存事件总线实现
 *
 * 线程安全：使用 synchronized 保证并发安全
 */
class InMemoryTabEventBus : TabEventBus {
    private val subscribers = mutableMapOf<KClass<*>, MutableMap<TabMetadata.TabId, (TabEvent) -> Unit>>()

    override fun <T : TabEvent> subscribe(
        eventType: KClass<T>,
        subscriber: TabMetadata.TabId,
        handler: (T) -> Unit
    ): Disposable {
        synchronized(subscribers) {
            subscribers.getOrPut(eventType) { mutableMapOf() }[subscriber] = handler as (TabEvent) -> Unit
        }
        return Disposable { unsubscribe(subscriber) }
    }

    override fun publish(event: TabEvent) {
        val handlers = synchronized(subscribers) {
            subscribers[event::class]?.values?.toList() ?: emptyList()
        }
        handlers.forEach { it(event) }
    }

    // ... 其他方法实现
}
```

#### `InMemoryTabStateRepository.kt`
```kotlin
/**
 * 内存状态存储实现
 *
 * 说明：
 * - 这是初始实现，状态仅在运行时保留
 * - 不依赖文件系统，适合快速验证框架
 * - 未来可以无缝替换为持久化实现
 *
 * 线程安全：使用 synchronized 保证并发安全
 */
class InMemoryTabStateRepository : TabStateRepository {
    private val states = mutableMapOf<String, TabState>()

    override fun saveState(tabId: String, state: TabState) {
        // 验证状态
        state.validate().onFailure { error ->
            throw IllegalArgumentException("Invalid state for tab $tabId: ${error.message}", error)
        }
        synchronized(states) {
            states[tabId] = state
        }
    }

    override fun <T : TabState> loadState(tabId: String, clazz: KClass<T>): T? {
        val state = synchronized(states) { states[tabId] }
        return if (clazz.isInstance(state)) {
            @Suppress("UNCHECKED_CAST")
            state as? T
        } else {
            null
        }
    }

    override fun deleteState(tabId: String) {
        synchronized(states) { states.remove(tabId) }
    }

    override fun hasState(tabId: String): Boolean {
        return synchronized(states) { states.containsKey(tabId) }
    }

    override fun clear() {
        synchronized(states) { states.clear() }
    }
}
```

#### `TabThemeManager.kt`
```kotlin
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
        return JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
    }
}
```

### 3.6 UI / Presentation Layer

**路径：** `src/main/kotlin/com/cw2/nekoama/interfaces/intellij/toolwindow/`

#### `NekoamaToolWindowFactory.kt`
```kotlin
/**
 * Tool Window 工厂类
 *
 * 职责：
 * - 实现 IntelliJ ToolWindowFactory 接口
 * - 创建 Tool Window 内容
 * - 依赖注入组装所有组件
 *
 * 说明：这是框架的入口点，在 plugin.xml 中注册
 */
class NekoamaToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 1. 创建基础设施层实例
        val eventBus = InMemoryTabEventBus()
        val stateRepository = InMemoryTabStateRepository()

        // 2. 创建领域服务
        val coordinatorService = TabCoordinatorService(eventBus, stateRepository)

        // 3. 创建主内容面板
        val contentPanel = NekoamaToolWindowContent(
            project = project,
            coordinatorService = coordinatorService
        ).createContent()

        // 4. 注册到Tool Window
        toolWindow.contentManager.addContent(contentPanel)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
```

#### `NekoamaToolWindowContent.kt`
```kotlin
/**
 * Tool Window 主内容面板
 *
 * 职责：
 * - 创建UI组件（JTabbedPane）
 * - 注册所有Tab工厂
 * - 委托 TabCoordinatorService 管理生命周期
 *
 * 说明：遵循 Swing UI 规范，所有UI操作在EDT执行
 */
class NekoamaToolWindowContent(
    private val project: Project,
    private val coordinatorService: TabCoordinatorService
) {
    private val tabbedPane = JTabbedPane()

    fun createContent(): Content {
        // 1. 注册所有Tab
        registerTabs()

        // 2. 创建主面板
        val mainPanel = JPanel(BorderLayout()).apply {
            add(createToolbar(), BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }

        // 3. 返回Content对象
        return ContentFactory.getInstance().createContent(
            mainPanel,
            "",
            false
        )
    }

    private fun registerTabs() {
        // 从 TabFactories 获取所有Tab工厂并注册
        TabFactories.all.forEach { factory ->
            val tab = factory.create(project, coordinatorService)
            coordinatorService.registerTab(tab)
            tabbedPane.addTab(tab.metadata.displayName, tab.createComponent())
        }
    }

    private fun createToolbar(): JComponent {
        // 创建工具栏（可选）
        return JPanel().apply {
            border = JBUI.Borders.empty(5)
            background = TabThemeManager.getTabBackgroundColor()
        }
    }
}
```

#### `BaseTab.kt` (抽象基类)
```kotlin
/**
 * Tab抽象基类
 *
 * 职责：
 * - 定义Tab生命周期方法
 * - 提供事件发布/订阅的便捷方法
 * - 提供状态保存/加载的便捷方法
 *
 * 说明：
 * - 所有Tab实现都必须继承此类
 * - 框架保证生命周期方法的调用顺序
 * - 使用模板方法模式
 */
abstract class BaseTab(
    protected val project: Project,
    protected val coordinatorService: TabCoordinatorService
) : Disposable {
    /**
     * Tab元数据（子类必须提供）
     */
    abstract val metadata: TabMetadata

    /**
     * Tab状态类型（子类提供，用于序列化）
     */
    protected abstract val stateType: KClass<out TabState>

    private var _lifecycle = TabLifecycle.CREATED
    val lifecycle: TabLifecycle get() = _lifecycle

    private var _component: JComponent? = null

    /**
     * 创建UI组件（子类实现）
     *
     * 说明：此方法仅在首次调用时执行，结果会被缓存
     */
    protected abstract fun createComponentImpl(): JComponent

    /**
     * Tab激活回调（子类可选实现）
     *
     * 说明：
     * - 用户切换到此Tab时调用
     * - 可以在这里加载状态、刷新UI
     * - 在EDT线程执行
     */
    protected open fun onActivated() {
        // 默认空实现
    }

    /**
     * Tab失活回调（子类可选实现）
     *
     * 说明：
     * - 用户切换到其他Tab时调用
     * - 可以在这里保存状态、暂停操作
     * - 在EDT线程执行
     */
    protected open fun onDeactivated() {
        // 默认空实现
    }

    /**
     * Tab销毁回调（子类可选实现）
     *
     * 说明：
     * - Tool Window关闭时调用
     * - 应该在这里释放资源、取消订阅
     */
    protected open fun onDestroy() {
        // 默认空实现
    }

    /**
     * 创建组件（公开方法，带缓存）
     */
    fun createComponent(): JComponent {
        return _component ?: run {
            _lifecycle = TabLifecycle.INITIALIZING
            val component = createComponentImpl()
            _lifecycle = TabLifecycle.READY
            _component = component
            component
        }
    }

    /**
     * 内部方法：激活Tab（由框架调用）
     */
    internal fun activate() {
        if (_lifecycle != TabLifecycle.DESTROYED) {
            _lifecycle = TabLifecycle.ACTIVE
            onActivated()
        }
    }

    /**
     * 内部方法：失活Tab（由框架调用）
     */
    internal fun deactivate() {
        if (_lifecycle == TabLifecycle.ACTIVE) {
            _lifecycle = TabLifecycle.INACTIVE
            onDeactivated()
        }
    }

    /**
     * 内部方法：销毁Tab（由框架调用）
     */
    internal fun destroy() {
        onDestroy()
        _lifecycle = TabLifecycle.DESTROYED
        _component = null
    }

    /**
     * 便捷方法：发布事件
     */
    protected fun publishEvent(event: TabEvent) {
        coordinatorService.eventBus.publish(event)
    }

    /**
     * 便捷方法：订阅事件
     */
    protected fun <T : TabEvent> subscribeEvent(
        eventType: KClass<T>,
        handler: (T) -> Unit
    ): Disposable {
        return coordinatorService.eventBus.subscribe(
            eventType,
            metadata.id,
            handler
        )
    }

    /**
     * 便捷方法：保存状态
     */
    protected fun saveState(state: TabState) {
        coordinatorService.saveTabState(metadata.id.value, state)
    }

    /**
     * 便捷方法：加载状态
     */
    protected fun <T : TabState> loadState(): T? {
        return coordinatorService.getTabState<T>(metadata.id)
    }

    override fun dispose() {
        destroy()
    }
}
```

#### `TabFactory.kt` (工厂接口)
```kotlin
/**
 * Tab工厂接口
 *
 * 职责：
 * - 定义Tab创建契约
 * - 支持依赖注入
 * - 实现内部扩展机制
 */
fun interface TabFactory {
    /**
     * 创建Tab实例
     *
     * @param project IntelliJ Project
     * @param coordinatorService Tab协调服务
     * @return Tab实例
     */
    fun create(project: Project, coordinatorService: TabCoordinatorService): BaseTab
}
```

#### `TabFactories.kt` (工厂注册)
```kotlin
/**
 * Tab工厂注册表
 *
 * 说明：
 * - 这是内部扩展机制的实现
 * - 新增Tab只需在此处添加工厂
 * - 无需修改其他代码
 */
object TabFactories {
    /**
     * 所有Tab工厂（在此处注册）
     *
     * 扩展方式：
     * 1. 创建新的Tab类（继承 BaseTab）
     * 2. 创建工厂实例
     * 3. 添加到此列表
     */
    val all: List<TabFactory> = listOf(
        // WelcomeTab（示例Tab）
        TabFactory { project, coordinator ->
            WelcomeTab(project, coordinator)
        }

        // 未来扩展示例：
        // TabFactory { project, coordinator ->
        //     AIDialogTab(project, coordinator)
        // },
        // TabFactory { project, coordinator ->
        //     CodeAnalysisTab(project, coordinator)
        // }
    )
}
```

#### `WelcomeTab.kt` (示例实现)
```kotlin
/**
 * 欢迎Tab（示例实现）
 *
 * 职责：
 * - 演示如何实现BaseTab
 * - 显示欢迎信息和使用说明
 * - 作为未来Tab实现的参考模板
 */
class WelcomeTab(
    project: Project,
    coordinatorService: TabCoordinatorService
) : BaseTab(project, coordinatorService) {

    override val metadata = TabMetadata(
        id = TabMetadata.TabId("welcome"),
        displayName = "Welcome",
        icon = AllIcons.Actions.Home
    )

    override val stateType = WelcomeTabState::class

    private var state: WelcomeTabState? = null

    override fun createComponentImpl(): JComponent {
        return FormBuilder.createFormBuilder()
            .addComponent(createHeaderPanel(), 0)
            .addComponent(JBSeparator(), 0)
            .addComponent(createContentPanel(), 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createHeaderPanel(): JComponent {
        return JPanel().apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(10)
            background = TabThemeManager.getTabBackgroundColor()

            add(JBLabel("欢迎使用 Nekoama").apply {
                font = font.deriveFont(Font.BOLD, 18f)
            }, BorderLayout.WEST)
        }
    }

    private fun createContentPanel(): JComponent {
        return JBTextArea(
            """
            |Nekoama 是一款 AI 驱动的智能代码助手。
            |
            |当前功能：
            |• Name for Any - 智能命名建议
            |• Comment for Me - AI 驱动注释生成
            |• IDEA for Neko - 自定义代码生成
            |
            |未来功能（敬请期待）：
            |• AI 对话助手
            |• 代码质量分析
            |• 代码气味检测
            |
            |---
            |Tool Window Framework v1.0
            |支持扩展的侧边弹窗系统
            """.trimMargin()
        ).apply {
            isEditable = false
            background = TabThemeManager.getTabBackgroundColor()
            foreground = TabThemeManager.getTabTextColor()
            border = JBUI.Borders.empty(10)
        }
    }

    override fun onActivated() {
        // 演示：激活时加载状态
        state = loadState<WelcomeTabState>()
        NekoamaLogger.info("WelcomeTab activated, state: $state")
    }

    override fun onDeactivated() {
        // 演示：失活时保存状态
        val newState = WelcomeTabState(lastVisited = System.currentTimeMillis())
        saveState(newState)
        NekoamaLogger.info("WelcomeTab deactivated, saved state: $newState")
    }
}

/**
 * Welcome Tab 状态数据
 *
 * 说明：
 * - 实现 TabState 接口
 * - 框架不关心具体内容
 * - 可以扩展任意字段
 */
data class WelcomeTabState(
    val lastVisited: Long = System.currentTimeMillis()
) : TabState {
    override fun validate(): Result<Unit> {
        return if (lastVisited > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("lastVisited must be positive"))
        }
    }
}
```

### 3.7 plugin.xml 配置

**路径：** `src/main/resources/META-INF/plugin.xml`

```xml
<!-- 在 <extensions> 中添加 -->
<extensions defaultExtensionNs="com.intellij">
    <!-- ... 现有扩展 ... -->

    <!-- Tool Window 注册 -->
    <toolWindow id="Nekoama.Main"
                factoryClass="com.cw2.nekoama.interfaces.intellij.toolwindow.NekoamaToolWindowFactory"
                anchor="right"
                icon="AllIcons.Actions.Layout"/>
</extensions>
```

## 4. Implementation Steps (Phasing)

### Phase 1: 领域模型与接口定义（优先级：高）

**目标：** 定义核心接口和数据模型，建立类型安全契约

**任务：**
1. 创建 `domain/toolwindow/model/` 目录和文件
   - [ ] `TabMetadata.kt`
   - [ ] `TabLifecycle.kt`
   - [ ] `TabEvent.kt`
   - [ ] `TabState.kt`

2. 创建 `domain/toolwindow/service/` 接口
   - [ ] `TabEventBus.kt` (接口)
   - [ ] `TabCoordinatorService.kt` (接口)

3. 创建 `domain/toolwindow/repository/` 接口
   - [ ] `TabStateRepository.kt` (接口)

**验收标准：**
- 所有接口编译通过
- 接口设计符合DDD原则（领域层不依赖基础设施）
- KDoc注释完整

**测试：**
- 无需测试（仅接口定义）

---

### Phase 2: 基础设施层实现（优先级：高）

**目标：** 实现内存版本的事件总线和状态存储

**任务：**
1. 创建 `infrastructure/toolwindow/` 目录

2. 实现事件总线
   - [ ] `InMemoryTabEventBus.kt`
   - [ ] 单元测试（验证订阅/发布/取消订阅）

3. 实现状态存储
   - [ ] `InMemoryTabStateRepository.kt`
   - [ ] 单元测试（验证保存/加载/删除）

4. 实现主题适配器
   - [ ] `TabThemeManager.kt`

**验收标准：**
- 所有单元测试通过
- 线程安全验证（多线程测试）

**测试文件：**
- `src/test/kotlin/com/cw2/nekoama/infrastructure/toolwindow/InMemoryTabEventBusTest.kt`
- `src/test/kotlin/com/cw2/nekoama/infrastructure/toolwindow/InMemoryTabStateRepositoryTest.kt`

---

### Phase 3: Tab框架核心实现（优先级：高）

**目标：** 实现 TabCoordinatorService 和 BaseTab

**任务：**
1. 实现 `domain/toolwindow/service/TabCoordinatorService.kt`
   - [ ] 注册Tab
   - [ ] 激活/失活Tab
   - [ ] 状态管理
   - [ ] 单元测试

2. 实现 `interfaces/intellij/toolwindow/BaseTab.kt`
   - [ ] 生命周期方法模板
   - [ ] 事件发布/订阅便捷方法
   - [ ] 状态保存/加载便捷方法

3. 实现工厂机制
   - [ ] `TabFactory.kt` (接口)
   - [ ] `TabFactories.kt` (注册表)

**验收标准：**
- TabCoordinatorService 单元测试通过
- BaseTab 生命周期正确触发

**测试文件：**
- `src/test/kotlin/com/cw2/nekoama/domain/toolwindow/service/TabCoordinatorServiceTest.kt`

---

### Phase 4: Tool Window 集成（优先级：中）

**目标：** 集成到 IntelliJ Platform，实现 Tool Window 显示

**任务：**
1. 实现 `NekoamaToolWindowFactory.kt`
   - [ ] 依赖注入组装
   - [ ] 实现 ToolWindowFactory 接口

2. 实现 `NekoamaToolWindowContent.kt`
   - [ ] 创建UI组件（遵循Swing规则）
   - [ ] 集成 JTabbedPane
   - [ ] 注册所有Tab工厂

3. 实现示例Tab
   - [ ] `WelcomeTab.kt` (完整实现)
   - [ ] `WelcomeTabState.kt`

4. 配置 plugin.xml
   - [ ] 注册 Tool Window

**验收标准：**
- 在IDE中可以看到 Nekoama Tool Window
- 点击后显示 WelcomeTab
- UI组件符合主题适配

**测试：**
- 手动测试（运行 Plugin）

---

### Phase 5: 事件驱动验证（优先级：中）

**目标：** 验证Tab间事件通信

**任务：**
1. 添加第二个示例Tab（SenderTab）
   - [ ] 发布按钮
   - [ ] 发布 TestEvent

2. 修改 WelcomeTab 接收事件
   - [ ] 订阅 TestEvent
   - [ ] 显示接收到的消息

3. 验证事件流
   - [ ] Tab切换事件
   - [ ] 自定义数据事件

**验收标准：**
- SenderTab 发布事件，WelcomeTab 能正确接收
- 切换Tab时触发激活/失活事件

---

### Phase 6: 状态持久化验证（优先级：中）

**目标：** 验证Tab状态保存和恢复

**任务：**
1. 在 WelcomeTab 中演示状态保存
   - [ ] 记录访问时间戳
   - [ ] onDeactivated 时保存
   - [ ] onActivated 时恢复

2. 添加UI显示状态
   - [ ] 显示上次访问时间
   - [ ] 验证状态持久化

**验收标准：**
- 切换Tab后，状态正确保存
- 切换回来后，状态正确恢复

---

### Phase 7: 代码质量与文档（优先级：低）

**目标：** 完善代码质量和文档

**任务：**
1. 代码审查
   - [ ] 确保符合DDD分层
   - [ ] 确保线程安全
   - [ ] 确保EDT规则遵循

2. 文档完善
   - [ ] 补充KDoc注释
   - [ ] 添加使用示例
   - [ ] 更新 README

3. 性能优化
   - [ ] 懒加载验证
   - [ ] 内存泄漏检查

**验收标准：**
- 所有KDoc完整
- 无内存泄漏
- 无Lint错误

## 5. Technical Constraints

### 5.1 并发模型
- **UI操作：** 必须在EDT线程执行（遵循 `edt-threading-rules.md`）
- **事件分发：** 同步执行（订阅者应快速处理，避免阻塞）
- **状态持久化：** 同步写入（内存实现），未来异步（持久化实现）

### 5.2 性能要求
- **Tab创建：** 应在 100ms 内完成
- **Tab切换：** 应在 50ms 内完成
- **事件分发：** 单个事件处理不应超过 10ms
- **内存占用：** 每个Tab不应超过 1MB（不含业务数据）

### 5.3 兼容性
- **IDE版本：** IntelliJ IDEA 2025.1+ (since-build 251)
- **JDK版本：** JVM 21
- **Kotlin版本：** 2.0.21+

### 5.4 安全性
- **状态验证：** 所有 TabState 实现必须提供 validate() 方法
- **异常处理：** 事件处理异常不应影响其他订阅者
- **资源清理：** Disposable 接口确保资源释放

## 6. Testing Strategy

### 6.1 单元测试

**目标类：**
- `InMemoryTabEventBus`
- `InMemoryTabStateRepository`
- `TabCoordinatorService`

**覆盖目标：**
- 代码覆盖率 > 80%
- 分支覆盖率 > 70%

### 6.2 集成测试

**测试场景：**
- Tab 注册 → 激活 → 失活 → 销毁流程
- 事件发布 → 订阅 → 取消订阅流程
- 状态保存 → Tab切换 → 状态恢复流程

### 6.3 手动/UI验证

**验证项：**
- Tool Window 正常显示
- Tab切换流畅
- 主题适配正确（深色/浅色）
- 无控制台错误信息

## 7. Key File Checklist

### Domain层
- [ ] `domain/toolwindow/model/TabMetadata.kt`
- [ ] `domain/toolwindow/model/TabLifecycle.kt`
- [ ] `domain/toolwindow/model/TabEvent.kt`
- [ ] `domain/toolwindow/model/TabState.kt`
- [ ] `domain/toolwindow/service/TabEventBus.kt`
- [ ] `domain/toolwindow/service/TabCoordinatorService.kt`
- [ ] `domain/toolwindow/repository/TabStateRepository.kt`

### Infrastructure层
- [ ] `infrastructure/toolwindow/InMemoryTabEventBus.kt`
- [ ] `infrastructure/toolwindow/InMemoryTabStateRepository.kt`
- [ ] `infrastructure/toolwindow/TabThemeManager.kt`

### Interfaces层
- [ ] `interfaces/intellij/toolwindow/NekoamaToolWindowFactory.kt`
- [ ] `interfaces/intellij/toolwindow/NekoamaToolWindowContent.kt`
- [ ] `interfaces/intellij/toolwindow/BaseTab.kt`
- [ ] `interfaces/intellij/toolwindow/TabFactory.kt`
- [ ] `interfaces/intellij/toolwindow/TabFactories.kt`
- [ ] `interfaces/intellij/toolwindow/tabs/WelcomeTab.kt`
- [ ] `interfaces/intellij/toolwindow/tabs/WelcomeTabState.kt`

### 配置文件
- [ ] `src/main/resources/META-INF/plugin.xml` (修改)

### 测试文件
- [ ] `infrastructure/toolwindow/InMemoryTabEventBusTest.kt`
- [ ] `infrastructure/toolwindow/InMemoryTabStateRepositoryTest.kt`
- [ ] `domain/toolwindow/service/TabCoordinatorServiceTest.kt`

## 8. Definition of Done (交付标准)

### 8.1 功能完整性
- [x] Tool Window 在右侧显示
- [x] WelcomeTab 正常显示内容
- [x] Tab切换触发激活/失活事件
- [x] 事件发布/订阅正常工作
- [x] 状态保存/加载正常工作

### 8.2 代码质量
- [x] 所有单元测试通过（100%）
- [x] 无新的Lint错误
- [x] 符合DDD分层规则
- [x] 符合EDT线程规则
- [x] 符合Swing UI规则

### 8.3 文档完整性
- [x] 所有公开API有KDoc注释
- [x] 关键设计决策有内联注释
- [x] 框架使用示例（WelcomeTab作为模板）
- [x] 实现计划文档完整

### 8.4 扩展性验证
- [x] 新增Tab只需在 TabFactories 添加一行
- [x] 状态持久化接口不绑定具体业务
- [x] 事件总线支持任意数据类型
- [x] 生命周期方法完整且可覆盖

## 9. Future Enhancements (未来增强)

### 9.1 持久化实现（渐进式升级）
```kotlin
/**
 * 文件持久化实现（未来实现）
 *
 * 替换 InMemoryTabStateRepository，无需修改业务代码
 */
class PersistentTabStateRepository(
    private val project: Project
) : TabStateRepository {
    // 使用 PropertiesComponent 或文件系统存储
    // 支持 JSON 序列化
    // 支持版本迁移
}
```

### 9.2 异步事件总线（性能优化）
```kotlin
/**
 * 异步事件总线（未来实现）
 *
 * 使用协程实现异步事件分发
 * 避免单个订阅者阻塞其他订阅者
 */
class AsyncTabEventBus : TabEventBus {
    // 使用 CoroutineScope + Channel
    // 支持背压处理
}
```

### 9.3 高级UI组件
- Tab拖拽排序
- Tab固定/隐藏
- 自定义Tab样式
- Tab图标徽章（通知数量等）

## 10. 设计决策记录 (ADR)

### ADR-001: 为什么使用内存实现而非直接持久化？
**决策：** 初始实现使用 InMemoryTabStateRepository

**理由：**
1. 快速验证框架可行性
2. 避免过早优化（YAGNI原则）
3. 持久化需求可能因业务而异（AI对话历史需要长期存储，其他Tab可能不需要）

**未来：** 预留接口，可无缝替换为持久化实现

### ADR-002: 为什么使用密封类而非接口定义事件？
**决策：** TabEvent 使用密封类（sealed class）

**理由：**
1. 编译时类型检查（when 表达式完整覆盖）
2. 支持数据携带（data class）
3. 更好的模式匹配支持

### ADR-003: 为什么TabState是通用接口而非泛型？
**决策：** TabState 是非泛型接口

**理由：**
1. 简化Repository接口设计
2. 支持运行时类型检查
3. 不同Tab可以有完全不同的State类型，互不影响

---

**文档版本：** 1.0
**创建日期：** 2025-01-08
**最后更新：** 2025-01-08
**状态：** 已审核
