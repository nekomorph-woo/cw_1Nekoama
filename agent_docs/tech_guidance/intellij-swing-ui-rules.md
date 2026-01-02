# IntelliJ IDEA Plugin Swing UI Technical Constraints

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: All UI operations must be executed on Event Dispatch Thread (EDT) with proper thread isolation

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| **Thread** | Direct UI updates from background threads | `EDT.runOrInvokeLater { UI code }` |
| **Long Operations** | Blocking EDT with file/network operations | `ProgressManager.getInstance().run()` with `Task.Backgroundable` |
| **UI Components** | `new JFrame()`, `new JDialog()` | `JBPopupFactory.getInstance().createActionGroupPopup()` |
| **Colors** | `Color.RED`, `Color.BLUE` hardcoded | `UIUtil.getHeaderForeground()`, `JBUI.CurrentTheme.ActionButton.hoverBackground()` |
| **Fonts** | `new Font("Arial", ...)` | `UIUtil.getLabelFont()`, `JBUI.Fonts.label()` |
| **Layouts** | Absolute positioning with `setBounds()` | `BorderLayout`, `GridBagLayout`, `FormBuilder` |
| **Threading** | `SwingUtilities.invokeLater()` (deprecated) | `ApplicationManager.getApplication().invokeLater()` |
| **Dialogs** | `JOptionPane.showMessageDialog()` | `Messages.showInfoMessage()`, `Messages.showErrorDialog()` |
| **Validation** | Manual input validation in listeners | `DocumentListener` with `CommitCallback` |

## 3. Critical Snippets (核心代码范式)
```kotlin
// Good Pattern: EDT-Safe UI Creation
class MyToolWindow {
  private val contentPanel = JPanel(BorderLayout())

  fun createComponent(): JComponent {
    return JPanel().apply {
      layout = BorderLayout()

      // Use UIUtil for colors
      background = UIUtil.getPanelBackground()

      // Use JBUI for dimensions
      border = JBUI.Borders.empty(5)

      // Add components with proper constraints
      add(JBLabel("Header"), BorderLayout.NORTH)
      add(createToolbar(), BorderLayout.WEST)
    }
  }

  private fun createToolbar(): JComponent {
    return ActionManager.getInstance().createActionToolbar(
      "MyToolbar",
      createActionGroup(),
      true
    ).component
  }
}

// Good Pattern: Background Task with UI Update
fun performLongOperation(project: Project) {
  ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Processing", true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = "Analyzing..."

      // Background computation
      val result = computeResult()

      // UI Update on EDT
      ApplicationManager.getApplication().invokeLater {
        updateUIWithResult(result)
      }
    }
  })
}

// Good Pattern: Form Building with FormBuilder
fun createSettingsPanel(): JPanel {
  return FormBuilder.createFormBuilder()
    .addComponent(createFieldComponent(), 1)
    .addComponent(JBSeparator(), 1)
    .addLabeledComponent(JBLabel("Name:"), nameField, 1, false)
    .addComponentFillVertically(JPanel(), 0)
    .panel
}

// Good Pattern: Theme-Aware Colors
class CustomComponent : JPanel() {
  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    // Use theme-aware colors
    g.color = UIUtil.getPanelBackground()
    g.fillRect(0, 0, width, height)

    // Border with current theme
    g.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
    g.drawRect(0, 0, width - 1, height - 1)
  }
}

// Good Pattern: Event Handling with Proper Threading
class MyButtonAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    // Show progress immediately
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        // Background work
        val result = performHeavyComputation()

        // UI updates in EDT
        ApplicationManager.getApplication().invokeLater {
          showResults(project, result)
        }
      },
      "Processing Data",
      true,
      project
    )
  }
}
```

## 4. Verification (如何验证)
* Check: All UI modifications wrapped in `ApplicationManager.getApplication().invokeLater()`
* Check: No hardcoded colors or fonts - always use `UIUtil` or `JBUI.CurrentTheme`
* Check: Long-running operations use `Task.Backgroundable` or `ProgressManager`
* Check: Dialogs use `Messages` API instead of `JOptionPane`
* Check: Layout managers are used instead of absolute positioning
* Check: Components extend `JComponent`/`JPanel` properly with `@Inject` constructor
* Check: Event listeners don't block EDT
* Check: Custom painting respects current theme colors
* Check: Form components created with `FormBuilder` for consistency
* Check: Tool windows implement `Disposable` for cleanup