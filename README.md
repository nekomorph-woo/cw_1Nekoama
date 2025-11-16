# Nekoama: AI-Powered Intelligent Code Assistant Plugin

<div align="center">

![Nekoama Icon](src/main/resources/icons/NekoamaToolWindow.svg)

**An intelligent code assistant for IntelliJ IDEA, integrating advanced large language model technology**

**[中文文档](README-zh.md)**

</div>

## 📖 Table of Contents

- [✨ Core Features](#-core-features)
- [📊 Advanced Features](#-advanced-features)
- [🚀 Installation Methods](#-installation-methods)
- [🔧 Quick Start](#-quick-start)
- [🎮 User Interface](#-user-interface)
- [🏗️ Technical Architecture](#-technical-architecture)
- [🔌 Extension Development](#-extension-development)
- [🌐 Network Configuration & Proxy Support](#-network-configuration--proxy-support)
- [❓ FAQ](#-faq-frequently-asked-questions)
- [🔒 Security & Privacy](#-security--privacy)
- [⚡ Performance Optimization](#-performance-optimization)
- [🧪 System Requirements](#-system-requirements)
- [🔧 Development Environment](#development-environment)
- [📄 License](#-license)

## 📖 Overview

**Nekoama** is a powerful IntelliJ IDEA plugin that provides developers with comprehensive intelligent code assistance by integrating advanced large language model technology. The plugin deeply analyzes code context, understands development intent, and delivers precise intelligent suggestions, significantly improving coding efficiency and code quality.

### 🎯 Design Philosophy

- **Intelligent Understanding**: Deeply understand code structure and semantics based on PSI and AST analysis
- **Context Awareness**: Extract rich code context to generate suggestions that comply with project standards
- **Security & Reliability**: All sensitive data is encrypted and stored, with network requests processed asynchronously
- **Modular Architecture**: Extensible plugin-based architecture supporting custom feature extensions
- **Performance First**: Background processing without blocking UI, maintaining IDE responsiveness

## ✨ Core Features

### 📝 Intelligent Naming Suggestions
- **Variable Naming**: Generate camelCase-compliant variable names based on type, purpose, and scope
- **Method Naming**: Create semantically clear method names based on functionality, parameters, and return values
- **Class Naming**: Generate Java/Kotlin naming convention-compliant class names based on class responsibilities and inheritance
- **Naming Standards**: Automatically follow existing project naming patterns and best practices

### 📖 AI-Driven Comment Generation
- **Method Documentation**: Automatically generate complete method documentation compliant with KDoc/JavaDoc standards
- **Parameter Descriptions**: Intelligently analyze parameter types and purposes to generate clear parameter descriptions
- **Return Value Documentation**: Generate accurate return value descriptions based on method logic
- **Exception Documentation**: Automatically identify potential exceptions and generate related documentation
- **Code Examples**: Generate usage example code based on method complexity

### 🛠️ Custom Code Generation
- **Flexible Prompts**: Support natural language descriptions to generate any type of code content
- **Context Integration**: Automatically combine with current code environment to generate project-style code
- **Template Generation**: Support algorithm implementations, design patterns, refactoring suggestions, and more
- **Incremental Generation**: Insert generated content at selected locations, supporting iterative optimization

### 🔍 Unused Code Analysis
- **Project-wide Scanning**: Deeply analyze the entire project to identify unused files, classes, methods, and properties
- **Smart Filtering**: Exclude test files, configuration files, and other special-purpose code
- **Detailed Reports**: Generate detailed analysis reports including location, type, and impact scope
- **One-click Cleanup**: Provide safe code cleanup suggestions supporting batch operations

## 📊 Advanced Features

### 📈 Token Usage Statistics
- **Real-time Monitoring**: Precisely track token consumption for each AI call
- **Multi-dimensional Analysis**: Support classification by time dimension (today/this week/this month) and usage type
- **Cost Optimization**: Provide usage trend analysis to help optimize API usage costs
- **Data Export**: Support exporting statistics to CSV format for further analysis

### ⚙️ Flexible Configuration Options
- **Multi-provider Support**: Compatible with OpenAI, Azure OpenAI, and custom API endpoints
- **Parameter Adjustment**: Flexibly adjust model parameters (temperature, max tokens, timeout, etc.)
- **Secure Storage**: API keys encrypted and stored via IntelliJ Password Safe
- **Configuration Validation**: Automatically verify API connections and configuration validity

### 🎯 Modular Tool Window
- **Modern Interface**: Adopt the latest IntelliJ UI design specifications
- **Tab Management**: Support multiple tabs with free switching and rearrangement
- **State Persistence**: Automatically save interface state, restoring previous work session after restart
- **Extension Support**: Plugin-based architecture supporting third-party extensions and custom features

## 🚀 Installation Methods

### Method 1: Install from JetBrains Marketplace (Recommended)
1. Open IntelliJ IDEA
2. Navigate to **File** → **Settings** → **Plugins**
3. Search for "Nekoama"
4. Click **Install** to install
5. Restart IDE

### Method 2: Manual Installation
1. Download the latest plugin package from [Releases](https://github.com/your-repo/releases)
2. Open IntelliJ IDEA
3. Navigate to **File** → **Settings** → **Plugins**
4. Click the ⚙️ icon and select **Install Plugin from Disk...**
5. Select the downloaded plugin package file
6. Restart IDE

### Method 3: Build from Source
```bash
# Clone the project
git clone https://github.com/your-repo/nekoama.git
cd nekoama

# Build the plugin
./gradlew buildPlugin

# Run sandbox IDE
./gradlew runIde
```

## 🔧 Quick Start

### 1. Configure AI Service
1. Open **File** → **Settings** → **Tools** → **Nekoama**
2. Select AI provider (OpenAI API-compatible provider)
3. Enter API key and related configuration
4. Click **Test Connection** to verify configuration

### 2. Use Intelligent Naming
- Select the variable, method, or class to be named
- Right-click and select **Nekoama** → **Name for Any**
- Or use shortcut `Ctrl+Alt+N` (customizable in settings)

### 3. Generate Code Comments
- Place cursor on the method or class that needs comments
- Right-click and select **Nekoama** → **Comment for Me**
- Or use shortcut `Ctrl+Alt+C`

### 4. Custom Code Generation
- Select text containing requirement descriptions
- Right-click and select **Nekoama** → **IDEA for Neko**
- Supported format: `[your requirement description]` or directly use selected text

### 5. Analyze Unused Code
- Open the **Tools** menu
- Select **Nekoama: Analyze Unused Code**
- Wait for analysis completion, then view the generated report file

## 🎮 User Interface

### Tool Window
- **Location**: Right side of interface, click Nekoama icon to open
- **Overview Tab**: Display system status, quick operations, and usage summary
- **Statistics Tab**: Detailed token usage statistics and cost analysis
- **Extensions Tab**: Loaded extension information and system status

### Editor Integration
- **Context Menu**: Integrated into editor's right-click context menu
- **Shortcut Support**: Support custom shortcut key bindings
- **Real-time Feedback**: Operation progress displayed in real-time, supporting task cancellation

## 🏗️ Technical Architecture

### Layered Architecture Design
```
┌─────────────────────────────────────┐
│           Presentation Layer        │  # UI Layer: Actions, Tool Windows, Settings
├─────────────────────────────────────┤
│           Integration Layer         │  # Integration Layer: PSI, Editor, Lifecycle
├─────────────────────────────────────┤
│              Core Layer             │  # Core Layer: Exceptions, Logging, Metrics, Serialization
├─────────────────────────────────────┤
│               AI Layer              │  # AI Layer: Models, Providers, Clients
└─────────────────────────────────────┘
```

### Key Technical Features
- **PSI Safety**: All PSI access is protected via `ReadAction`, ensuring thread safety
- **EDT Compatibility**: UI operations correctly use event dispatch thread, background tasks don't block interface
- **Asynchronous Processing**: Kotlin coroutine-based asynchronous architecture supporting task cancellation and progress tracking
- **Modular Design**: Plugin-based extension system supporting dynamic loading and unloading
- **State Persistence**: Automatically save and restore interface state and user configuration

### Core Components
- **UniversalCodeAnalyzer**: Unified code analyzer supporting Java and Kotlin
- **AIProvider**: Abstract AI service interface supporting multiple providers
- **ModularToolWindow**: Modular tool window supporting dynamic tabs
- **TokenStatsTab**: Token usage statistics and visualization
- **MetricsCollector**: Usage metrics collection and analysis

## 🔌 Extension Development

### Creating Custom Tab Extensions
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

### Event Communication System
```kotlin
// Publish event
TabEventSystemSingleton.getInstance().publishEvent(
    TabRefreshEvent("my_tab_id")
)

// Subscribe to events
TabEventSystemSingleton.getInstance().subscribe(
    TabRefreshEvent::class.java,
    object : TabEventHandler<TabRefreshEvent> {
        override fun handleEvent(event: TabRefreshEvent) {
            // Handle refresh event
        }
    }
)
```

## 🔒 Security & Privacy

### Data Security
- **API Key Protection**: Encrypted storage using IntelliJ Password Safe
- **Sensitive Data Filtering**: Code analysis and logging automatically filter sensitive information
- **Network Security**: All network requests use HTTPS encrypted transmission
- **Local Processing**: Code analysis completed locally, not sent to external servers

### Privacy Protection
- **Code Anonymization**: Code sent to AI services is anonymized
- **Optional Upload**: Users have complete control over whether to use AI services
- **Data Minimization**: Only send necessary code context, minimizing data exposure

## ⚡ Performance Optimization

### Background Processing
- **Non-blocking Operations**: All AI calls executed in background threads
- **Progress Indication**: Real-time display of operation progress, supporting user cancellation
- **Memory Management**: Intelligent cache management, avoiding memory leaks
- **Concurrency Control**: Reasonable control of concurrent request count, avoiding resource competition

### Code Analysis Optimization
- **Incremental Analysis**: Only analyze modified code, avoiding redundant processing
- **Caching Mechanism**: Cache analysis results to improve response speed
- **Index Utilization**: Fully leverage IntelliJ's code indexing system

## 🧪 System Requirements

### System Requirements
- **IDE**: IntelliJ IDEA 2025.1 or higher
- **JDK**: Java 21 or higher
- **Kotlin**: 2.1 or higher (supports K2 compiler)
- **Memory**: Recommended minimum 4GB available memory

### Supported Languages
- **Java**: Full support for Java 8+ features
- **Kotlin**: Full support for Kotlin 1.9+, supports K2 compiler mode
- **Other Languages**: Basic support, some features may be limited

### Dependent Services
- **AI Services**: Need to configure OpenAI API or compatible custom API
- **Network Connection**: Stable internet connection required for AI functionality

## 🌐 Network Configuration & Proxy Support

### Proxy Configuration
Nekoama supports enterprise network environments with proxy configurations. The plugin automatically detects and uses IDEA's system proxy settings.

#### HTTP/HTTPS Proxy
1. Open IDEA: `File` → `Settings` → `System Settings` → `HTTP Proxy`
2. Select `Manual proxy configuration`
3. Configure proxy settings:
   - **HTTP proxy**: Hostname and port for HTTP traffic
   - **HTTPS proxy**: Hostname and port for HTTPS traffic
   - **Proxy authentication**: Username and password if required
4. Click `OK` to apply settings

#### SOCKS Proxy
1. In the same proxy settings dialog, select SOCKS for proxy type
2. Enter SOCKS proxy server details
3. Ensure the proxy server supports SOCKS4/5 protocol
4. Configure authentication if required

### Proxy Authentication Troubleshooting

#### HTTP 407: Proxy Authentication Required
**Symptoms**: Error message "Proxy authentication failed" when connecting to AI services
**Solutions**:
- Verify proxy username and password in IDEA proxy settings
- Check if proxy server requires domain authentication (use `DOMAIN\username` format)
- Test proxy connectivity with a web browser first
- Contact network administrator for correct proxy credentials

#### Connection Timeouts in Proxy Environment
**Symptoms**: Requests timeout when using proxy
**Solutions**:
- Increase timeout values in Nekoama settings (`File` → `Settings` → `Tools` → `Nekoama`)
- Check proxy server performance and network stability
- Verify firewall settings allow proxy connections
- Try different proxy servers if available

### Network Diagnostic Tools

#### Test Connection
Use the built-in connection test in Nekoama settings:
1. Open `File` → `Settings` → `Tools` → **Nekoama**
2. Configure your API endpoint and API key
3. Click **Test Connection** to verify network connectivity
4. Check results for proxy authentication status and response times

#### Error Identification
**Plugin Errors** (Nekoama-related):
- Package names start with `com.cw2.nekoama`
- Appear in Nekoama tool window or settings
- Related to AI service calls or configuration

**IDE Errors** (IntelliJ IDEA-related):
- Package names start with `com.intellij`
- Appear during IDE startup
- Related to IDEA's built-in features (AI Assistant, etc.)

## ❓ FAQ (Frequently Asked Questions)

### Q: Getting "Proxy Authentication Required" error?
**A**: This indicates your proxy server requires authentication. Check your IDEA proxy settings (`File` → `Settings` → `System Settings` → `HTTP Proxy`) and ensure correct username and password are configured.

### Q: Plugin shows "Connection failed" but internet works?
**A**: This might be a proxy configuration issue. Test with browser first, then verify IDEA proxy settings match your system proxy configuration.

### Q: How to verify proxy is working correctly?
**A**: Use Nekoama's built-in connection test in settings. The test will show proxy status, response time, and any authentication issues.

### Q: Can I use different proxies for different requests?
**A**: Nekoama respects IDEA's global proxy settings. For different proxy configurations, you'll need to modify IDEA's proxy settings manually.

### Q: Getting SSL certificate errors in proxy environment?
**A**: Configure your proxy to allow SSL connections or temporarily disable SSL verification in Nekoama settings (not recommended for production).

### Q: How to distinguish Nekoama errors from other plugin errors?
**A**: Check the error log package names. Nekoama errors start with `com.cw2.nekoama`, while other plugins have different package prefixes.

### Q: Plugin works without proxy but fails with proxy enabled?
**A**: This usually indicates proxy authentication issues. Verify credentials and ensure the proxy allows HTTPS connections to your AI service endpoint.

## 🔧 Development Environment

### Build Tools
- **Gradle**: Gradle-based build system
- **Version Catalog**: Use `gradle/libs.versions.toml` for dependency version management
- **China Mirror**: Configured Alibaba Cloud Maven mirror for accelerated dependency downloads

### Development Commands
```bash
# Build plugin
./gradlew build

# Run sandbox IDE
./gradlew runIde

# Run tests
./gradlew test

# Code quality checks
./gradlew detekt

# Build plugin package
./gradlew buildPlugin
```

### Code Quality
- **Detekt**: Static code analysis tool
- **KtLint**: Kotlin code formatting tool
- **Testing**: JUnit 5 + MockK testing framework

### Development Environment Setup
```bash
# Clone the project
git clone https://github.com/your-repo/nekoama.git
cd nekoama

# Build plugin
./gradlew build

# Run sandbox IDE
./gradlew runIde

# Run tests
./gradlew test
```

### Code Standards
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use [Detekt](https://detekt.dev/) for static code analysis
- Ensure all PSI access is executed within `ReadAction`
- UI operations must be performed on EDT

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**If Nekoama helps you, please give us a ⭐**

Made with ❤️ by [cw2](https://github.com/cw2me)

</div>