### 主题适配指南

**重要注意事项**: 标签页和扩展的实现必须注意主题适配，确保在所有 IntelliJ 主题下都有良好的视觉体验。

#### 必须遵循的主题适配原则

1. **使用主题感知颜色**:
   ```kotlin
   // ✅ 正确：使用主题感知颜色
   component.background = UIUtil.getPanelBackground()
   component.foreground = UIUtil.getLabelForeground()

   // ❌ 错误：硬编码颜色值
   component.background = Color.WHITE
   component.background = Gray._245
   ```

2. **使用统一的边框样式**:
   ```kotlin
   // ✅ 正确：使用主题感知边框
   component.border = JBEmptyBorder(JBUI.insets(10, 10, 10, 10))

   // ❌ 错误：硬编码边框
   component.border = EmptyBorder(10, 10, 10, 10)
   ```

3. **继承 BaseNekoamaTab 使用内置方法**:
   ```kotlin
   // ✅ 正确：使用内置的主题感知方法
   val card = createThemedCard()
   val cardWithPadding = createThemedCard(15, 15, 15, 15)
   applyThemedStyle(existingPanel)
   ```

4. **测试所有主题**:
    - 在暗色主题 (Darcula) 下测试
    - 在亮色主题 (IntelliJ Light) 下测试
    - 在自定义主题下测试
    - 确保切换主题时组件颜色正确更新

#### 常见主题适配问题

- **白色边缘**: 硬编码的白色或浅灰色背景在暗色主题下产生刺眼边缘
- **文本可读性**: 硬编码文本颜色在某些主题下可能不可读
- **边框可见性**: 硬编码边框颜色可能在某些主题下不可见