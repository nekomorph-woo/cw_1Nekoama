## 标签页扩展系统

### 概述
Nekoama 插件采用基于标签页的模块化架构和插件式扩展系统。这允许动态加载自定义标签页而无需修改核心代码。

### 标签页架构

**5层架构:**
1. **基础层**: 标签页和扩展接口 (TabExtension, NekoamaTab)
2. **扩展层**: 扩展发现、适配器和点管理
3. **管理层**: 标签页生命周期和状态管理 (NekoamaTabManager)
4. **通信层**: 事件系统和配置管理
5. **表示层**: UI 集成和用户交互 (ModularToolWindow)

### 关键组件

**核心接口:**
- `TabExtension`: 创建自定义标签页扩展的基础接口
- `NekoamaTab`: 具有生命周期和状态管理的标签页接口
- `TabExtensionPoint`: 扩展注册和管理接口

**管理系统:**
- `NekoamaTabManager`: 管理所有标签页、状态持久化和切换的单例
- `TabExtensionAdapter`: 将 TabExtension 适配到 NekoamaTab 接口
- `ExtensionDiscovery`: 从各种来源发现和加载扩展

**通信系统:**
- `TabEventSystem`: 标签页和扩展之间的事件驱动通信
- `TabExtensionConfigManager`: 配置持久化管理

### 创建自定义扩展

**基础扩展:**
```kotlin
class MyCustomExtension : AbstractTabExtension() {
    override val extensionId = "com.example.myplugin"
    override val displayName = "My Feature"
    override val description = "Custom functionality"
    override val version = "1.1.0"

    override fun createTab(): NekoamaTab {
        return MyCustomTab()
    }
}

class MyCustomTab : NekoamaTab {
    override val tabId = "my_custom_tab"
    override val displayName = "My Feature"

    override fun getComponent(): JComponent {
        // 返回你的 UI 组件
        return JPanel()
    }

    override fun getTabState(): Map<String, Any> {
        // 返回用于持久化的状态
        return mapOf("data" to "value")
    }

    override fun restoreTabState(state: Map<String, Any>) {
        // 恢复保存的状态
    }
}
```

**注册扩展:**
```kotlin
val extension = MyCustomExtension()
TabExtensionPointSingleton.getInstance().registerExtension(extension)
```

### 事件通信

**发布事件:**
```kotlin
TabEventSystemSingleton.getInstance().publishEvent(
    TabRefreshEvent("my_tab_id")
)
```

**订阅事件:**
```kotlin
TabEventSystemSingleton.getInstance().subscribe(
    TabRefreshEvent::class.java,
    object : TabEventHandler<TabRefreshEvent> {
        override fun handleEvent(event: TabRefreshEvent) {
            // 处理刷新事件
        }
    }
)
```

### 内置标签页

1. **概览标签页**: 默认仪表板，显示系统状态、快速操作和使用摘要
2. **Token 统计标签页**: 增强的 Token 使用跟踪，具有导出功能
3. **演示扩展**: 展示扩展能力的示例标签页

### 扩展功能

- **动态加载**: 扩展可在运行时加载/卸载
- **状态持久化**: 标签页状态自动保存和恢复
- **事件通信**: 类型安全的事件系统，用于组件间通信
- **配置管理**: 具有持久化存储的扩展设置
- **兼容性检查**: 自动验证扩展兼容性
- **错误隔离**: 扩展失败不影响其他功能

### 用户界面

- **扩展信息按钮**: 查看已加载扩展和系统状态
- **标签页管理**: 原生 IntelliJ 标签页行为，支持拖放
- **状态保持**: 在标签页间切换时保留标签页内容
- **刷新控制**: 手动刷新所有标签页或单个标签页