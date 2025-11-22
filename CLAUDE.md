# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 构建、代码检查和测试命令

### Gradle Wrapper 命令
- **Windows**: `gradlew.bat` (下方命令中的 `./gradlew` 替换为此)
- **macOS/Linux**: `./gradlew`

### 核心开发命令
- **构建插件 ZIP**: `./gradlew buildPlugin`
- **运行沙盒 IDE**: `./gradlew runIde`
- **运行测试**: `./gradlew test`
- **运行 detekt 代码检查**: `./gradlew detekt`
- **构建项目**: `./gradlew build`
- **检查依赖更新**: `./gradlew dependencyUpdates`

### 性能测试
- **运行 IDE 性能测试**: `./gradlew testIdePerformance`
- **准备性能测试沙盒**: `./gradlew prepareTestIdePerformanceSandbox`

### 代码质量
- **生成 detekt 配置**: `./gradlew detektGenerateConfig`
- **创建 detekt 基线**: `./gradlew detektBaseline`

## 高级代码架构

### 项目结构
```
src/main/kotlin/com/cw2/nekoama/
├── ai/                          # AI 服务层
│   ├── model/                  # 核心数据模型 (CodeContext, Suggestion, 等)
│   │   └── dependency/         # 代码依赖分析数据模型
│   │       ├── DependencyData.kt      # 依赖关系核心数据模型
│   │       └── AnalysisMetrics.kt     # 分析指标统计模型
│   └── provider/               # AI 提供商实现
│       ├── openai/            # OpenAI 提供商 (OpenAIProvider, HttpClient, ResponseParser)
│       └── custom/             # 自定义 API 提供商 (CustomAPIProvider, HttpClient)
├── core/                       # 核心工具和抽象
│   ├── exception/             # 自定义异常 (NekoamaError)
│   ├── logging/               # 日志记录 (NekoamaLogger)
│   ├── metrics/               # 指标收集 (MetricsCollector)
│   ├── result/                # 结果类型 (Result)
│   └── serialization/         # JSON 配置 (JsonConfig)
├── data/                       # 数据层
│   └── settings/              # 设置管理 (NekoamaSettings, NekoamaSecureStorage)
├── integrations/              # IntelliJ 平台集成
│   ├── editor/               # 编辑器相关工具 (NekoamaTypedActionHandler, SymbolTypedHandler)
│   └── psi/                  # PSI 工具 (UniversalCodeAnalyzer, CodeAnalyzer, JavaCodeAnalyzer, KotlinCodeAnalyzer)
│       ├── DependencyCodeAnalyzer.kt           # 代码依赖关系分析器
│       ├── JavaDependencyExtractor.kt          # Java依赖提取器
│       ├── ComplexityCalculator.kt              # 复杂度计算器
│       ├── CodeSmellDetector.kt                # 代码坏味道检测器
│       ├── BoundaryEntryPointDetector.kt        # 业务边界入口检测器
│       ├── CrossBoundaryAnalyzer.kt             # 跨边界使用分析器
│       ├── AnalysisScopeController.kt           # 分析范围控制器
│       └── BatchAnalysisProcessor.kt            # 批量分析处理器
├── platform/                  # 平台特定代码
│   ├── lifecycle/            # 生命周期管理 (NekoamaProjectActivity, NekoamaStartupActivity)
│   └── task/                 # 任务管理 (AITaskManager)
├── presentation/              # UI 层
│   ├── actions/              # 编辑器动作 (GenerateNamingAction, GenerateCommentAction, CustomGenerateAction, AnalyzeUnusedCodeAction)
│   ├── messages/             # 国际化 (NekoamaBundle)
│   ├── notifications/        # 通知 (NekoamaNotifier)
│   ├── settings/             # 设置 UI (NekoamaConfigurable)
│   ├── templates/            # Live 模板 (NekoamaAiCommentMacro, NekoamaLiveTemplatesProvider)
│   └── toolwindow/           # 工具窗口和标签页管理
│       ├── NekoamaToolWindowFactory.kt  # 工具窗口工厂
│       ├── NekoamaToolWindow.kt         # 旧版工具窗口 (备用)
│       ├── ModularToolWindow.kt         # 新模块化工具窗口
│       ├── tab/                         # 标签页系统
│       │   ├── NekoamaTab.kt            # 标签页接口和基类
│       │   ├── NekoamaTabManager.kt     # 标签页生命周期和状态管理
│       │   ├── OverviewTab.kt           # 概览仪表板标签页
│       │   └── TokenStatsTab.kt         # Token 统计标签页 (重构版)
│       └── extension/                   # 扩展系统
│           ├── TabExtension.kt          # 扩展接口和基类
│           ├── TabExtensionPointImpl.kt # 扩展点实现
│           ├── TabExtensionAdapter.kt   # 扩展到标签页适配器
│           ├── TabEventSystem.kt        # 事件驱动通信
│           ├── TabExtensionConfig.kt    # 配置管理
│           ├── ExtensionDiscovery.kt    # 扩展发现机制
│           └── example/
│               └── DemoTabExtension.kt  # 示例扩展实现
└── NekoamaPlugin.kt          # 插件入口点
```

### 关键架构模式

1. **分层架构**: AI 服务、核心工具、数据层、平台集成和表示层之间清晰分离。

2. **提供商模式**: AI 提供商 (OpenAI, CustomAPI) 实现通用接口，支持可插拔的 HTTP 客户端和响应解析器。

3. **模块化标签页架构**: 基于标签页的 UI，具有状态管理、动态加载和生命周期管理。支持插件式扩展。

4. **扩展系统**: 插件式架构，用于动态加载自定义标签页，具有事件驱动通信和配置管理。

5. **后台任务管理**: 所有 AI 操作使用 IntelliJ 的 ProgressManager 在后台任务中运行，保持 IDE 响应性。

6. **安全优先设计**: API 密钥存储在 IntelliJ Password Safe 中，敏感数据永不记录日志。

7. **模块化 AI 流水线**: 上下文提取 → 提示生成 → 提供商调用 → 响应解析 → 建议应用。

8. **代码依赖分析系统**: 基于PSI的深度代码分析，支持依赖关系提取、复杂度计算、业务场景识别和代码坏味道检测。

### 核心组件

- **AI 提供商接口**: 抽象 AI 服务调用，具有重试、超时和并发管理
- **代码上下文模型**: 从 PSI 提取丰富上下文以生成有意义的提示
- **标签页管理系统**: 集中化标签页生命周期、状态持久化和切换管理
- **扩展框架**: 插件式系统，用于动态加载具有配置管理的自定义标签页
- **事件通信系统**: 发布/订阅模式，用于标签页间和扩展到核心的通信
- **设置管理**: 具有安全存储 API 凭据的类型安全设置
- **指标收集**: 工具窗口的使用统计和 Token 跟踪
- **动作系统**: IntelliJ 动作框架集成，用于编辑器和菜单命令

### 配置
- **插件 ID**: `me.cw2.Nekoama`
- **目标平台**: IntelliJ IDEA 2025.1+ (since-build 251)
- **JDK**: 21
- **Kotlin**: 2.1 (K2 编译器)
- **依赖**: OpenAI client, Azure OpenAI, OkHttp, Retrofit, KotlinX Serialization, Caffeine, Guava

## 重要文件和目录

- `src/main/resources/META-INF/plugin.xml` - 插件配置和动作定义
- `src/main/kotlin/com/cw2/nekoama/presentation/settings/NekoamaConfigurable.kt` - 设置 UI
- `src/main/kotlin/com/cw2/nekoama/ai/provider/openai/OpenAIProvider.kt` - OpenAI 服务实现
- `src/main/kotlin/com/cw2/nekoama/presentation/actions/GenerateNamingAction.kt` - 核心动作实现
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/ModularToolWindow.kt` - 主模块化工具窗口
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/tab/NekoamaTabManager.kt` - 标签页管理系统
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/TabExtension.kt` - 扩展接口
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/TabExtensionPointImpl.kt` - 扩展点实现
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/TabEventSystem.kt` - 事件通信系统
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/example/DemoTabExtension.kt` - 示例扩展
- `build.gradle.kts` - 构建配置，包含所有依赖和仓库
- `gradle/libs.versions.toml` - 依赖管理的版本目录
- `gradle.properties` - Gradle 配置和依赖版本
- `TASK_PLAN.md` - 项目开发计划和任务跟踪
- `README.md` - 项目文档和使用指南

## 开发指南

### 依赖管理
- 使用 Gradle 版本目录 (`gradle/libs.versions.toml`) 进行集中化依赖版本管理
- 版本也在 `gradle.properties` 中声明，用于构建脚本引用
- 依赖按逻辑分组组织

### 中国镜像配置
- 构建系统配置了中国 Maven 镜像以加快依赖解析
- 主要镜像: maven.aliyun.com 用于 central, jcenter, spring, 和 apache-snapshots 仓库

### 测试设置
- 测试框架: JUnit Jupiter 5.10.1 配合 MockK 进行模拟
- 集成测试支持 TestContainers
- HTTP 客户端测试支持 MockWebServer
- 当前项目中不存在测试源码

### 代码质量
- 配置了 Detekt 静态分析 (版本 1.23.4)
- 可用 KtLint 集成 (版本 0.50.0)
- 不存在现有的 detekt 配置文件 - 可通过 `./gradlew detektGenerateConfig` 生成

### 插件开发特性
- 支持 Kotlin K2 编译器模式
- 使用 IntelliJ Platform Gradle Plugin 2.7.1
- 需要的捆绑插件: com.intellij.java, org.jetbrains.kotlin
- 所有网络操作在后台任务中运行，具有适当的取消支持

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
    override val version = "1.0.0"

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

#### 推荐的最佳实践

1. **始终使用 `UIUtil` 类获取主题感知颜色**
2. **使用 `JBUI.insets()` 替代硬编码数值**
3. **避免使用 `Color.WHITE`、`Color.BLACK` 等硬编码颜色**
4. **在开发过程中频繁切换主题进行测试**
5. **总是在你的任务完成时确认可能的编译异常，然后修复它**
6. **插件UI触发的任务处理参考 `docs/Kotlin_EDT_PSI实践.md`**
7. **PSI_代码分析处理参考 `docs/PSI_AST代码分析方案.md`**

## 用户-AI协作开发规则（**强制**）
1. **响应要求**：请全程使用简体中文，涵盖聊天、方案、文档及代码注释，所有专业术语（如Spring Boot, Kubernetes）保持原样，无需翻译，对于 `properties` 类型（或其它配置文件），请严格使用英文
2. **核心开发原则**：
   - 专注于实现当前明确的需求，避免为臆想的未来需求进行过度设计。除非收到显式要求，否则不应编写兼容代码
   - **没有时间限制！！禁止使用简化代码，必须按照要求实现编码**;
   - **制定编码的Todos时，请在最后步骤中添加 `确认可能的编译异常，然后修复它` 的任务，避免遗留问题**
3. **代码注释的核心原则**：代码注释应旨在阐明复杂的业务意图与实现逻辑，而非记录如时间、计划等元信息
4. **规范维护职责**：
   - 您应负责根据项目特性（如编程语言、应用框架），审慎地更新 `./CLAUDE.md` 中的编码规范，以确保其始终适用且精准
   - 每次任务结束前，您应该判断本次修改是否涉及项目架构变动，审慎地更新 `./CLAUDE.md`，以确保项目上下文始终准确
5. **国际化规范**：
   - 语言支持：项目仅需支持英语
   - 实现方式：所有用户界面文案必须置于外部配置文件（如资源文件）中，严禁在代码中硬编码
   - 例外情况：此规则不适用于报错信息、日志、异常等非用户直接可见的内容
6. **Epic Flow（史诗任务协同，简写E-Flow）**：对于日常任务，你可按既有TODO列表规划执行。但若涉及复杂功能或目标输入，则必须启用以下协同流程，并务必与我确认后方可推进：
    - 讨论阶段： 使用 Plan Mode 与我详细讨论功能需求
    - 规划阶段：在与我确认需求清晰后，请你按照以下模板填充内容，并输出到 `项目根目录/TASK_PLAN.md` 文档中，用以管理整体规划与里程碑
   ```markdown
    # Epic Flow（史诗任务协同）
    
    ##  功能概述
    
    ### 功能名称
    [功能的简短名称]
    
    ### 功能目标
    [用1-3句话描述这个功能要解决什么问题，为用户带来什么价值]
    
    ### 任务状态
    - **当前状态**:  规划中 /  开发中 / ✅ 已完成
    
    ---
    
    ##  需求分析 (规划阶段)
    
    ### 功能需求
    1. [核心需求1]
    2. [核心需求2]
    3. [核心需求3]
    
    ### 技术需求
    - **依赖组件**: [列出需要的核心组件]
    - **数据模型**: [需要创建/修改的数据模型]
    - **UI需求**: [界面要求描述]
    - **性能要求**: [响应时间、资源占用等]
    
    ### 非功能需求
    - **可扩展性**: [未来扩展点]
    - **安全性**: [数据安全、权限控制等]
    
    ---
    
    ##  任务分解 (执行阶段)
    
    ###  大目标: [功能总目标]
    
    ####  子功能A: [子功能名称]
    > **优先级**:  高 /  中 /  低  
    > **状态**: ⏳ 待开始 /  进行中 / ✅ 已完成
    
    ##### [阶段1] 核心数据模型和业务逻辑
    - [ ] **任务A1**: [任务描述]
      - 涉及文件: `path/to/file.kt`
      - 状态: ⏳ 待开始 /  进行中 / ✅ 已完成
    
    - [ ] **任务A2**: [任务描述]
      - 涉及文件: `path/to/file.kt`
      - 状态: ⏳ 待开始
    
    ##### [阶段2] UI组件和交互逻辑
    - [ ] **任务B1**: [任务描述]
     - 涉及文件: `path/to/ui.kt`
     - 状态: ⏳ 待开始
    
    ##### [阶段3] 测试和优化
    - [ ] **任务C1**: [任务描述]
     - 测试类型: 单元测试 / 手动测试
     - 测试要求：[根据测试类型编写测试要求，若为手动测试，请在此向用户索要测试结果]
     - 状态: ⏳ 待开始
    
     ---
    
    ####  子功能B: [子功能名称]
    > **优先级**:  中  
    > **状态**: ⏳ 待开始
    
     [重复上述结构]
    
     ---
    
    ##  检查点记录
    
    ### 子功能A完成总结
    - **完成内容**:
     - ✅ [完成项1]
     - ✅ [完成项2]
     - **技术要点**:
      - [关键技术决策或实现方式]
     - **遗留问题**:
      - [ ] [待解决问题1]
      - **下一步**: 开始子功能B
    
     ---
    
    ### 子功能B完成总结
     [同上结构]
    
     ---
    
    ##  相关文档
    
    - [相关技术文档链接]
     - [API文档]
     - [设计文档]
    
     ---
    
    ## ✅ 完成标准
    
    - [ ] 所有子功能任务完成
     - [ ] 代码通过编译检查
     - [ ] 文档更新完成
    
     ---
    
    ##  备注
    
     [其他需要记录的信息]
    
     ---
    
     **最后更新**: YYYY-MM-DD  
     **文档版本**: v1.0
   ```
   - 执行阶段：
     - 仅将当前子功能的相关任务纳入你的TODO列表
     - 完成一个子功能后，立即输出其任务总结
     - 依据总结更新 `项目根目录/TASK_PLAN.md` 文档对应子功能的检查点，然后清空你当前TODO列表，等待用户输入