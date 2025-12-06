# Tech Constraint: IntelliJ Theme Adaptation

## 1. Strict Mapping Rules (强制映射规则)
All UI components **MUST** use IDEA Platform SDK APIs. Standard AWT/Swing calls are **FORBIDDEN**.

| Category | ❌ Forbidden (Hardcoded)         | ✅ Required (Theme-Aware) |
| :--- |:--------------------------------| :--- |
| **Color** | `Color.WHITE`, `new Color(...)` | `UIUtil.getPanelBackground()`, `UIUtil.getLabelForeground()` |
| **Border** | `EmptyBorder`, `LineBorder`     | `JBEmptyBorder`, `JBUI.Borders.customLine()` |
| **Insets** | `new Insets(...)`               | `JBUI.insets(...)` |
| **Dimension** | `new Dimension(...)`            | `JBUI.size(...)` |
| **Scaling** | Raw pixels (e.g., `10`)         | `JBUI.scale(10)` |

## 2. Component Inheritance
*   All Tabs **MUST** inherit from `BaseNekoamaTab`.
*   **DO NOT** manually set colors/borders in Tabs. Use built-in helpers:
    *   `createThemedCard()`
    *   `applyThemedStyle(panel)`

## 3. Review Checklist (AI Self-Correction)
Before outputting code, verify:
1. Are there any `java.awt.Color` references? -> **Remove them.**
2. Is `JBUI` used for all spacing/sizing? -> **Enforce it.**
3. Does it look correct in Darcula (Dark) Mode? -> **Assume Dark Mode by default.**