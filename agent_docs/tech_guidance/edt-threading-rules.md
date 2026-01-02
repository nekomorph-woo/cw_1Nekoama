# EDT Threading Technical Constraints

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: All UI operations must run on Event Dispatch Thread (EDT), all long-running operations must run off EDT

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| UI Updates | `SwingUtilities.invokeLater()` | `EdtExecutor.getInstance().executeLater()` |
| Long Operations | Any blocking call on EDT | `ProgressManager.getInstance().run()` |
| Modal Dialogs | `JOptionPane.showXXX()` | `Messages.showXXXDialog()` |
| Background Tasks | `Thread.sleep()` | `ProgressIndicator.checkCanceled()` |
| PSI Access | `File.read()` | `ReadAction.compute()` |

## 3. Critical Snippets (核心代码范式)
```kotlin
// UI Update Pattern
EdtExecutor.getInstance().executeLater(Runnable {
  myComponent.text = "Updated"
})

// Background Task Pattern
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Task Name") {
  override fun run(indicator: ProgressIndicator) {
    indicator.text = "Processing..."
    val result = computeHeavyOperation()
    EdtExecutor.getInstance().executeLater(Runnable {
      updateUI(result)
    })
  }
})

// PSI Access Pattern
val result = ReadAction.compute<String, Throwable> {
  virtualFile.readText()
}
```

## 4. Verification (如何验证)
* Check: All UI modifications are wrapped in EDT execution blocks
* Check: No I/O operations in UI event handlers
* Check: Proper use of ProgressManager for long operations
* Check: PSI access is protected with ReadAction/WriteAction