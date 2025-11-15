# Nekoama：AI 智能代码助手插件

<div align="center">

![Nekoama Icon](src/main/resources/icons/NekoamaToolWindow.svg)

**为 IntelliJ IDEA 打造的智能代码助手，集成先进的大语言模型技术**

</div>

## 📖 简介

**Nekoama** 是一款功能强大的 IntelliJ IDEA 插件，通过集成先进的大语言模型技术，为开发者提供全方位的智能代码辅助功能。插件深度分析代码上下文，理解开发意图，并提供精准的智能建议，显著提升编码效率和代码质量。

### 🎯 设计理念

- **智能理解**: 基于 PSI 和 AST 分析，深度理解代码结构和语义
- **上下文感知**: 提取丰富的代码上下文，生成符合项目规范的建议
- **安全可靠**: 所有敏感数据加密存储，网络请求异步处理
- **模块化架构**: 可扩展的插件式架构，支持自定义功能扩展
- **性能优先**: 后台处理，不阻塞 UI，保持 IDE 流畅响应

## ✨ 核心功能

### 📝 智能命名建议
- **变量命名**: 基于变量类型、用途和作用域智能生成符合驼峰命名规范的变量名
- **方法命名**: 根据方法功能、参数和返回值生成语义清晰的方法名
- **类命名**: 结合类职责和继承关系生成符合 Java/Kotlin 命名约定的类名
- **命名规范**: 自动遵循项目现有的命名模式和最佳实践

### 📖 AI 驱动注释生成
- **方法注释**: 自动生成符合 KDoc/JavaDoc 规范的完整方法文档
- **参数说明**: 智能分析参数类型和用途，生成清晰的参数描述
- **返回值描述**: 基于方法逻辑生成准确的返回值说明
- **异常文档**: 自动识别可能抛出的异常并生成相关文档
- **代码示例**: 根据方法复杂度生成使用示例代码

### 🛠️ 自定义代码生成
- **灵活提示**: 支持自然语言描述，生成任何类型的代码内容
- **上下文集成**: 自动结合当前代码环境，生成符合项目风格的代码
- **模板生成**: 支持算法实现、设计模式、重构建议等多种模板
- **增量生成**: 可在选中位置插入生成内容，支持多次迭代优化

### 🔍 未使用代码分析
- **全项目扫描**: 深度分析整个项目，识别未使用的文件、类、方法和属性
- **智能过滤**: 排除测试文件、配置文件等特殊用途的代码
- **详细报告**: 生成包含位置、类型和影响范围的详细分析报告
- **一键清理**: 提供安全的代码清理建议，支持批量操作

## 📊 高级功能

### 📈 Token 使用统计
- **实时监控**: 精确统计每次 AI 调用的 Token 消耗量
- **多维度分析**: 支持按时间维度（今日/本周/本月）和使用类型分类统计
- **成本优化**: 提供使用趋势分析，帮助优化 API 使用成本
- **数据导出**: 支持将统计数据导出为 CSV 格式进行进一步分析

### ⚙️ 灵活配置选项
- **多提供商支持**: 兼容 OpenAI、Azure OpenAI 和自定义 API 端点
- **参数调节**: 可灵活调整模型参数（温度、最大 Token 数、超时时间等）
- **安全存储**: API 密钥通过 IntelliJ Password Safe 加密存储
- **配置验证**: 自动验证 API 连接和配置有效性

### 🎯 模块化工具窗口
- **现代化界面**: 采用最新的 IntelliJ UI 设计规范
- **标签页管理**: 支持多标签页，可自由切换和重新排列
- **状态持久化**: 自动保存界面状态，重启后恢复上次的工作状态
- **扩展支持**: 插件式架构，支持第三方扩展和自定义功能

## 🚀 安装方法

### 方式一：从 JetBrains Marketplace 安装（推荐）
1. 打开 IntelliJ IDEA
2. 进入 **File** → **Settings** → **Plugins**
3. 搜索 "Nekoama"
4. 点击 **Install** 安装
5. 重启 IDE

### 方式二：手动安装
1. 从 [Releases](https://github.com/your-repo/releases) 下载最新版本的插件包
2. 打开 IntelliJ IDEA
3. 进入 **File** → **Settings** → **Plugins**
4. 点击 ⚙️ 图标，选择 **Install Plugin from Disk...**
5. 选择下载的插件包文件
6. 重启 IDE

### 方式三：从源码构建
```bash
# 克隆项目
git clone https://github.com/your-repo/nekoama.git
cd nekoama

# 构建插件
./gradlew buildPlugin

# 运行沙盒 IDE
./gradlew runIde
```

## 🔧 快速开始

### 1. 配置 AI 服务
1. 打开 **File** → **Settings** → **Tools** → **Nekoama**
2. 选择 AI 提供商（OpenAI API兼容的提供商）
3. 输入 API 密钥和相关配置
4. 点击 **Test Connection** 验证配置

### 2. 使用智能命名
- 选中需要命名的变量、方法或类
- 右键选择 **Nekoama** → **Name for Any**
- 或者使用快捷键 `Ctrl+Alt+N`（可在设置中自定义）

### 3. 生成代码注释
- 将光标置于需要注释的方法或类上
- 右键选择 **Nekoama** → **Comment for Me**
- 或者使用快捷键 `Ctrl+Alt+C`

### 4. 自定义代码生成
- 选中包含需求描述的文本
- 右键选择 **Nekoama** → **IDEA of Neko**
- 支持格式：`[你的需求描述]` 或直接使用选中文本

### 5. 分析未使用代码
- 打开 **Tools** 菜单
- 选择 **Nekoama: Analyze Unused Code**
- 等待分析完成，查看生成的报告文件

## 🎮 使用界面

### 工具窗口
- **位置**: 界面右侧，点击 Nekoama 图标打开
- **概览标签页**: 显示系统状态、快速操作和使用摘要
- **统计标签页**: 详细的 Token 使用统计和成本分析
- **扩展标签页**: 已加载的扩展信息和系统状态

### 编辑器集成
- **右键菜单**: 集成到编辑器的右键上下文菜单
- **快捷键支持**: 支持自定义快捷键绑定
- **实时反馈**: 操作进度实时显示，支持任务取消

## 🏗️ 技术架构

### 分层架构设计
```
┌─────────────────────────────────────┐
│           Presentation Layer        │  # UI 层：动作、工具窗口、设置
├─────────────────────────────────────┤
│           Integration Layer         │  # 集成层：PSI、编辑器、生命周期
├─────────────────────────────────────┤
│              Core Layer             │  # 核心层：异常、日志、指标、序列化
├─────────────────────────────────────┤
│               AI Layer              │  # AI 层：模型、提供商、客户端
└─────────────────────────────────────┘
```

### 关键技术特性
- **PSI 安全**: 所有 PSI 访问都通过 `ReadAction` 保护，确保线程安全
- **EDT 兼容**: UI 操作正确使用事件分发线程，后台任务不阻塞界面
- **异步处理**: 基于 Kotlin 协程的异步架构，支持任务取消和进度跟踪
- **模块化设计**: 插件式扩展系统，支持动态加载和卸载
- **状态持久化**: 自动保存和恢复界面状态及用户配置

### 核心组件
- **UniversalCodeAnalyzer**: 统一的代码分析器，支持 Java 和 Kotlin
- **AIProvider**: 抽象的 AI 服务接口，支持多种提供商
- **ModularToolWindow**: 模块化工具窗口，支持动态标签页
- **TokenStatsTab**: Token 使用统计和可视化
- **MetricsCollector**: 使用指标收集和分析

## 🔌 扩展开发

### 创建自定义标签页扩展
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
```

### 事件通信系统
```kotlin
// 发布事件
TabEventSystemSingleton.getInstance().publishEvent(
    TabRefreshEvent("my_tab_id")
)

// 订阅事件
TabEventSystemSingleton.getInstance().subscribe(
    TabRefreshEvent::class.java,
    object : TabEventHandler<TabRefreshEvent> {
        override fun handleEvent(event: TabRefreshEvent) {
            // 处理刷新事件
        }
    }
)
```

## 🔒 安全与隐私

### 数据安全
- **API 密钥保护**: 使用 IntelliJ Password Safe 加密存储
- **敏感数据过滤**: 代码分析和日志记录自动过滤敏感信息
- **网络安全**: 所有网络请求使用 HTTPS 加密传输
- **本地处理**: 代码分析在本地完成，不发送到外部服务器

### 隐私保护
- **代码匿名化**: 发送到 AI 服务的代码经过匿名化处理
- **可选上传**: 用户可完全控制是否使用 AI 服务
- **数据最小化**: 仅发送必要的代码上下文，最小化数据暴露

## ⚡ 性能优化

### 后台处理
- **非阻塞操作**: 所有 AI 调用在后台线程执行
- **进度指示**: 实时显示操作进度，支持用户取消
- **内存管理**: 智能缓存管理，避免内存泄漏
- **并发控制**: 合理控制并发请求数量，避免资源竞争

### 代码分析优化
- **增量分析**: 仅分析修改的代码，避免重复处理
- **缓存机制**: 缓存分析结果，提高响应速度
- **索引利用**: 充分利用 IntelliJ 的代码索引系统

## 🧪 环境要求

### 系统要求
- **IDE**: IntelliJ IDEA 2025.1 或更高版本
- **JDK**: Java 21 或更高版本
- **Kotlin**: 2.1 或更高版本（支持 K2 编译器）
- **内存**: 建议至少 4GB 可用内存

### 支持语言
- **Java**: 完全支持 Java 8+ 特性
- **Kotlin**: 完全支持 Kotlin 1.9+，支持 K2 编译器模式
- **其他语言**: 基础支持，部分功能可能受限

### 依赖服务
- **AI 服务**: 需要配置 OpenAI API 或兼容的自定义 API
- **网络连接**: 需要稳定的互联网连接以使用 AI 功能

## 🔧 开发环境

### 构建工具
- **Gradle**: 基于 Gradle 的构建系统
- **版本目录**: 使用 `gradle/libs.versions.toml` 进行依赖版本管理
- **中国镜像**: 配置了阿里云 Maven 镜像，加速依赖下载

### 开发命令
```bash
# 构建插件
./gradlew build

# 运行沙盒 IDE
./gradlew runIde

# 运行测试
./gradlew test

# 代码质量检查
./gradlew detekt

# 构建插件包
./gradlew buildPlugin
```

### 代码质量
- **Detekt**: 静态代码分析工具
- **KtLint**: Kotlin 代码格式化工具
- **测试**: JUnit 5 + MockK 测试框架

### 开发环境搭建
```bash
# 克隆项目
git clone https://github.com/your-repo/nekoama.git
cd nekoama

# 构建插件
./gradlew build

# 运行沙盒 IDE
./gradlew runIde

# 运行测试
./gradlew test
```

### 代码规范
- 遵循 [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 [Detekt](https://detekt.dev/) 进行静态代码分析
- 确保所有 PSI 访问都在 `ReadAction` 中执行
- UI 操作必须在 EDT 中执行

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。

---

<div align="center">

**如果 Nekoama 对你有帮助，请给我们一个 ⭐**

Made with ❤️ by [cw2](https://github.com/cw2me)

</div>
