# Tab Extension System Rules

## 1. Axioms
- **Status**: Stable
- **Core Principle**: All custom tabs MUST implement `TabExtension` interface and be registered via `TabExtensionPoint`.

## 2. Mapping Rules
| Category | ❌ Forbidden | ✅ Required |
| :--- | :--- | :--- |
| Tab Creation | Directly add `JComponent` to ToolWindow | Implement `TabExtension`, call `createTab(): NekoamaTab` |
| Registration | Direct modification of `NekoamaTabManager` | `TabExtensionPointSingleton.getInstance().registerExtension(extension)` |
| Event Publish | Custom listeners / callbacks | `TabEventSystemSingleton.getInstance().publishEvent(...)` |
| Event Subscribe | Direct coupling / listeners | `TabEventSystemSingleton.getInstance().subscribe(EventClass, handler)` |
| Extension ID | Simple names (e.g., `"MyTab"`) | Reverse domain format: `"com.example.myplugin.MyTab"` |
| Base Class | Implement `TabExtension` from scratch | Extend `AbstractTabExtension` for defaults |

## 3. Architecture (5-Layer)
```

L5: Presentation (ModularToolWindow, UI Interaction)
L4: Communication (TabEventSystem, TabExtensionConfigManager)
L3: Management (NekoamaTabManager - Lifecycle, State)
L2: Extension (TabExtensionPoint, TabExtensionAdapter, Discovery)
L1: Foundation (TabExtension Interface, NekoamaTab Interface)
```

## 4. Critical Snippets

### 4.1 Create Custom Extension
```
kotlin
// Minimal Extension using AbstractTabExtension
class MyFeatureExtension : AbstractTabExtension() {
override val extensionId = "com.mycompany.myfeature"
override val displayName = "My Feature"
override val description = "Provides feature X"
override val version = "1.0.0"

    override fun createTab(): NekoamaTab = MyFeatureTab()
}
```

### 4.2 Implement NekoamaTab
```
kotlin
class MyFeatureTab : NekoamaTab {
override val tabId = "my_feature_tab"
// displayName uses i18n by default: NekoamaBundle.message("tab.${tabId}.title")

    override fun getComponent(): JComponent = JPanel() // Your UI

    // State Persistence (Optional but Recommended)
    override fun getTabState(): Map<String, Any> = mapOf("key" to value)
    override fun restoreTabState(state: Map<String, Any>) { /* restore */ }
}
```

### 4.3 Register Extension
```
kotlin
val myExtension = MyFeatureExtension()
TabExtensionPointSingleton.getInstance().registerExtension(myExtension)
```

### 4.4 Event Communication
```
kotlin
// Publish
TabEventSystemSingleton.getInstance().publishEvent(TabRefreshEvent("my_tab_id"))

// Subscribe
TabEventSystemSingleton.getInstance().subscribe(
TabRefreshEvent::class.java,
object : TabEventHandler<TabRefreshEvent> {
override fun handleEvent(event: TabRefreshEvent) { /* ... */ }
}
)
```

### 4.5 Quick Tab (No custom Tab class needed)
```
kotlin
val simpleExtension = SimpleTabExtension(
extensionId = "com.mycompany.simple",
displayName = "Quick Tab",
description = "A simple tab",
tabFactory = { MySimpleTab() } // Lambda for tab creation
)
TabExtensionPointSingleton.getInstance().registerExtension(simpleExtension)
```

## 5. Key Interfaces & Defaults
| Property | Interface | Default (AbstractTabExtension) |
| :--- | :--- | :--- |
| `priority` | `Int` | `100` (lower = higher priority) |
| `icon` | `Icon?` | `null` |
| `isEnabled` | `Boolean` | `true` |
| `isCloseable` (NekoamaTab) | `Boolean` | `false` |

## 6. Lifecycle
1.  `registerExtension()` -> `initialize()` called.
2.  Tab selected -> `createTab()` -> `onTabActivated()`.
3.  Tab deselected -> `onTabDeactivated()`.
4.  Plugin unload / `unregisterExtension()` -> `dispose()` (Extension & Tab).

## 7. Verification
* Check: `extensionId` uses a reverse domain format.
* Check: Tab `getComponent()` returns a valid `JComponent`.
* Check: `dispose()` releases all resources (listeners, threads).
* Check: State methods (`getTabState`/`restoreTabState`) are symmetric.
* Check: Extension failures are isolated (wrapped in try-catch by adapter).
```