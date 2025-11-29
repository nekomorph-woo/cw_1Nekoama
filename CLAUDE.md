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
├── ai/                    # AI服务层 - AI提供商、模型、响应处理
├── core/                  # 核心工具 - 异常、日志、指标、网络、报告
├── data/                  # 数据层 - 设置管理、安全存储
├── integrations/          # IntelliJ平台集成 - PSI分析器、编辑器工具
├── platform/              # 平台特定代码 - 生命周期、任务管理
├── presentation/          # UI层 - 动作、工具窗口、设置界面
└── resources/             # 资源文件 - 插件配置、国际化、模板
```

### 关键架构模式

1. **分层架构**: AI服务、核心工具、数据层、平台集成、表示层清晰分离
2. **提供商模式**: 可插拔AI服务提供商（OpenAI、CustomAPI）
3. **模块化标签页架构**: 基于标签页的UI系统，支持状态管理和扩展
4. **扩展系统**: 插件式架构，动态加载自定义标签页
5. **后台任务管理**: 所有AI操作在后台任务中运行，保持IDE响应性
6. **安全优先设计**: API密钥安全存储，敏感数据保护
7. **代码分析系统**: 基于PSI的深度代码分析引擎
8. **报告生成系统**: 多格式分析报告生成（HTML、Markdown、JSON）
9. **网络代理支持**: 企业级网络环境代理支持

### 核心组件

**基础架构:**
- AI提供商接口、代码上下文模型、标签页管理系统、扩展框架、事件通信系统、设置管理、指标收集

**代码分析引擎:**
- PSI分析器集合、依赖分析系统、代码质量检测、批量处理引擎

**报告生成系统:**
- HTML报告生成器（集成可视化）、多格式导出、场景交叉分析

**网络与代理支持:**
- 代理检测器、连接测试器、HTTP客户端配置

**增强UI组件:**
- 分析配置对话框、进度反馈系统、报告查看器

### 配置

- **插件ID**: `me.cw2.Nekoama`
- **目标平台**: IntelliJ IDEA 2025.1+
- **JDK**: 21
- **Kotlin**: 2.1 (K2编译器)
- **主要依赖**: OpenAI client、OkHttp、Retrofit、KotlinX Serialization、Caffeine、Guava

### 技术文档
- `docs/Kotlin_EDT_PSI实践.md` - EDT和PSI实践指南
- `docs/PSI_AST代码分析方案.md` - PSI/AST代码分析方案
- `docs/JDK_HTTP-OKHTTP迁移方案.md`
- `docs/Nekoama-IDEA代理适配方案.md`
- `docs/Nekoama-标签页扩展系统方案.md`
- `docs/Nekoama-IDEA主题适配方案.md`

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

#### 推荐的最佳实践

1. **始终使用 `UIUtil` 类获取主题感知颜色**，参考 `docs/Nekoama-IDEA主题适配方案.md`
2. **使用 `JBUI.insets()` 替代硬编码数值**
3. **避免使用 `Color.WHITE`、`Color.BLACK` 等硬编码颜色**
4. **在开发过程中频繁切换主题进行测试**
5. **总是在你的任务完成时确认可能的编译异常，然后修复它**
6. **插件UI触发的任务处理参考 `docs/Kotlin_EDT_PSI实践.md`**
7. **PSI_代码分析处理参考 `docs/PSI_AST代码分析方案.md`**
8. **标签页扩展系统参考 `docs/Nekoama-标签页扩展系统方案.md`**

## 用户-AI协作开发规则（**强制**）
1. **输出均要保证UTF-8编码兼容！**
2. **响应要求**：请全程使用简体中文，涵盖聊天、方案、文档及代码注释，所有专业术语（如Spring Boot, Kubernetes）保持原样，无需翻译，对于 `properties` 类型（或其它配置文件），请严格使用英文
3. **核心开发原则**：
   - 专注于实现当前明确的需求，避免为臆想的未来需求进行过度设计。除非收到显式要求，否则不应编写兼容代码
   - **Write Boring code for readable, and predictable, making it easy to maintain, debug, and understand**
   - **拥有无限的设计和开发时间！！拒绝简化代码，必须按照要求实现编码**;
   - **当上下文快满了就自动压缩，不要因为 token 不够就提前停。即使预算快用完，也请把任务完整做完**;
   - **制定编码的TODOs时，请在最后步骤中添加 `确认可能的编译异常，然后修复它` 的任务，避免遗留问题**
   - **代码移除请使用真实的删除，不要使用代码注释替代**
4. **代码注释的核心原则**：代码注释应旨在阐明复杂的业务意图与实现逻辑，而非记录如时间、计划等元信息
5. **规范维护职责**：
   - 您应负责根据项目特性（如编程语言、应用框架），审慎地更新 `./CLAUDE.md` 中的编码规范，以确保其始终适用且精准
6. **国际化规范**：
   - 语言支持：项目仅需支持英语
   - 实现方式：所有用户界面文案必须置于外部配置文件（如资源文件）中，严禁在代码中硬编码
   - 例外情况：此规则不适用于报错信息、日志、异常等非用户直接可见的内容
7. **Epic Develop Flow（史诗任务协同，简写ED-Flow）**：对于日常任务，你可按既有TODO列表规划执行。但若涉及复杂功能或目标输入，则必须启用以下协同流程，并务必与我确认后方可推进：
    - 讨论阶段： 使用 Plan Mode 与我详细讨论功能需求，该过程需要向用户反复确认不明确的内容，并且确认时可以提供2～3种方案给用户提供灵感
    - 规划阶段：在与用户完全确认需求清晰后，请按照 `项目根目录/Epic_Develop_Flow.md` 模板填充内容，并输出到 `项目根目录/TASK_PLAN.md` 文档中，用以管理整体规划与里程碑
    - 执行阶段：
       - 仅将当前子功能的相关任务纳入你的TODO列表
       - 完成一个子功能后，立即输出其任务总结
       - 依据总结更新 `项目根目录/TASK_PLAN.md` 文档对应子功能的检查点，然后清空你当前TODO列表，等待用户输入