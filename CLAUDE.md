# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, Lint, and Test Commands

### Gradle Wrapper Commands
- **Windows**: `gradlew.bat` (replace `./gradlew` in commands below)
- **macOS/Linux**: `./gradlew`

### Core Development Commands
- **Build plugin ZIP**: `./gradlew buildPlugin`
- **Run sandbox IDE**: `./gradlew runIde`
- **Run tests**: `./gradlew test`
- **Run detekt linting**: `./gradlew detekt`
- **Build project**: `./gradlew build`
- **Check dependencies**: `./gradlew dependencyUpdates`

### Performance Testing
- **Run IDE performance tests**: `./gradlew testIdePerformance`
- **Prepare performance sandbox**: `./gradlew prepareTestIdePerformanceSandbox`

### Code Quality
- **Generate detekt config**: `./gradlew detektGenerateConfig`
- **Create detekt baseline**: `./gradlew detektBaseline`

## High-Level Code Architecture

### Project Structure
```
src/main/kotlin/com/cw2/nekoama/
├── ai/                          # AI service layer
│   ├── model/                  # Core data models (CodeContext, Suggestion, etc.)
│   └── provider/               # AI provider implementations
│       ├── openai/            # OpenAI provider (OpenAIProvider, HttpClient, ResponseParser)
│       └── custom/             # Custom API provider (CustomAPIProvider, HttpClient)
├── core/                       # Core utilities and abstractions
│   ├── exception/             # Custom exceptions (NekoamaError)
│   ├── logging/               # Logging (NekoamaLogger)
│   ├── metrics/               # Metrics collection (MetricsCollector)
│   ├── result/                # Result types (Result)
│   └── serialization/         # JSON configuration (JsonConfig)
├── data/                       # Data layer
│   └── settings/              # Settings management (NekoamaSettings, NekoamaSecureStorage)
├── integrations/              # IntelliJ Platform integrations
│   ├── editor/               # Editor-related utilities (NekoamaTypedActionHandler, SymbolTypedHandler)
│   └── psi/                  # PSI utilities (UniversalCodeAnalyzer, CodeAnalyzer, JavaCodeAnalyzer, KotlinCodeAnalyzer)
├── platform/                  # Platform-specific code
│   ├── lifecycle/            # Lifecycle management (NekoamaProjectActivity, NekoamaStartupActivity)
│   └── task/                 # Task management (AITaskManager)
├── presentation/              # UI layer
│   ├── actions/              # Editor actions (GenerateNamingAction, GenerateCommentAction, CustomGenerateAction, AnalyzeUnusedCodeAction)
│   ├── messages/             # Internationalization (NekoamaBundle)
│   ├── notifications/        # Notifications (NekoamaNotifier)
│   ├── settings/             # Settings UI (NekoamaConfigurable)
│   ├── templates/            # Live templates (NekoamaAiCommentMacro, NekoamaLiveTemplatesProvider)
│   └── toolwindow/           # Tool window and tab management
│       ├── NekoamaToolWindowFactory.kt  # Tool window factory
│       ├── NekoamaToolWindow.kt         # Legacy tool window (fallback)
│       ├── ModularToolWindow.kt         # New modular tool window
│       ├── tab/                         # Tab system
│       │   ├── NekoamaTab.kt            # Tab interface and base classes
│       │   ├── NekoamaTabManager.kt     # Tab lifecycle and state management
│       │   ├── OverviewTab.kt           # Overview dashboard tab
│       │   └── TokenStatsTab.kt         # Token statistics tab (refactored)
│       └── extension/                   # Extension system
│           ├── TabExtension.kt          # Extension interfaces and base classes
│           ├── TabExtensionPointImpl.kt # Extension point implementation
│           ├── TabExtensionAdapter.kt   # Extension to Tab adapter
│           ├── TabEventSystem.kt        # Event-driven communication
│           ├── TabExtensionConfig.kt    # Configuration management
│           ├── ExtensionDiscovery.kt    # Extension discovery mechanism
│           └── example/
│               └── DemoTabExtension.kt  # Example extension implementation
└── NekoamaPlugin.kt          # Plugin entry point
```

### Key Architectural Patterns

1. **Layered Architecture**: Clear separation between AI services, core utilities, data layer, platform integrations, and presentation layer.

2. **Provider Pattern**: AI providers (OpenAI, CustomAPI) implement a common interface with pluggable HTTP clients and response parsers.

3. **Modular Tab Architecture**: Tab-based UI with state management, dynamic loading, and lifecycle management. Supports plugin-style extensions.

4. **Extension System**: Plugin-like architecture for dynamically loading custom tabs with event-driven communication and configuration management.

5. **Background Task Management**: All AI operations run in background tasks using IntelliJ's ProgressManager to maintain IDE responsiveness.

6. **Security-First Design**: API keys stored in IntelliJ Password Safe, sensitive data never logged.

7. **Modular AI Pipeline**: Context extraction → Prompt generation → Provider call → Response parsing → Suggestion application.

### Core Components

- **AI Provider Interface**: Abstracts AI service calls with retry, timeout, and concurrency management
- **Code Context Model**: Rich context extraction from PSI for generating meaningful prompts
- **Tab Management System**: Centralized tab lifecycle, state persistence, and switching management
- **Extension Framework**: Plugin-like system for dynamically loading custom tabs with configuration management
- **Event Communication System**: Publish/subscribe pattern for tab-to-tab and extension-to-core communication
- **Settings Management**: Type-safe settings with secure storage for API credentials
- **Metrics Collection**: Usage statistics and token tracking for the tool window
- **Action System**: IntelliJ action framework integration for editor and menu commands

### Configuration
- **Plugin ID**: `me.cw2.Nekoama`
- **Target Platform**: IntelliJ IDEA 2025.1+ (since-build 251)
- **JDK**: 21
- **Kotlin**: 2.1 (K2 compiler)
- **Dependencies**: OpenAI client, Azure OpenAI, OkHttp, Retrofit, KotlinX Serialization, Caffeine, Guava

## Important Files and Directories

- `src/main/resources/META-INF/plugin.xml` - Plugin configuration and action definitions
- `src/main/kotlin/com/cw2/nekoama/presentation/settings/NekoamaConfigurable.kt` - Settings UI
- `src/main/kotlin/com/cw2/nekoama/ai/provider/openai/OpenAIProvider.kt` - OpenAI service implementation
- `src/main/kotlin/com/cw2/nekoama/presentation/actions/GenerateNamingAction.kt` - Core action implementation
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/ModularToolWindow.kt` - Main modular tool window
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/tab/NekoamaTabManager.kt` - Tab management system
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/TabExtension.kt` - Extension interfaces
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/TabExtensionPointImpl.kt` - Extension point implementation
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/TabEventSystem.kt` - Event communication system
- `src/main/kotlin/com/cw2/nekoama/presentation/toolwindow/extension/example/DemoTabExtension.kt` - Example extension
- `build.gradle.kts` - Build configuration with all dependencies and repositories
- `gradle/libs.versions.toml` - Version catalog for dependency management
- `gradle.properties` - Gradle configuration and dependency versions
- `TASK_PLAN.md` - Project development plan and task tracking
- `README.md` - Project documentation and usage guide

## Development Guidelines

### Dependency Management
- Uses Gradle Version Catalogs (`gradle/libs.versions.toml`) for centralized dependency versioning
- Versions are also declared in `gradle.properties` for build script reference
- Dependencies are organized into bundles for logical grouping

### Chinese Mirror Configuration
- Build system is configured with Chinese Maven mirrors for faster dependency resolution
- Primary mirrors: maven.aliyun.com for central, jcenter, spring, and apache-snapshots repositories

### Testing Setup
- Test framework: JUnit Jupiter 5.10.1 with MockK for mocking
- Integration testing support with TestContainers
- MockWebServer for HTTP client testing
- Currently no test sources exist in the project

### Code Quality
- Detekt static analysis configured (version 1.23.4)
- KtLint integration available (version 0.50.0)
- No existing detekt configuration file - can be generated with `./gradlew detektGenerateConfig`

### Plugin Development Specifics
- Supports Kotlin K2 compiler mode
- Uses IntelliJ Platform Gradle Plugin 2.7.1
- Bundled plugins required: com.intellij.java, org.jetbrains.kotlin
- All network operations run in background tasks with proper cancellation support

## Tab Extension System

### Overview
The Nekoama plugin features a modular tab-based architecture with a plugin-like extension system. This allows for dynamic loading of custom tabs without modifying core code.

### Tab Architecture

**5-Layer Architecture:**
1. **Foundation Layer**: Tab and extension interfaces (TabExtension, NekoamaTab)
2. **Extension Layer**: Extension discovery, adapters, and point management
3. **Management Layer**: Tab lifecycle and state management (NekoamaTabManager)
4. **Communication Layer**: Event system and configuration management
5. **Presentation Layer**: UI integration and user interaction (ModularToolWindow)

### Key Components

**Core Interfaces:**
- `TabExtension`: Base interface for creating custom tab extensions
- `NekoamaTab`: Tab interface with lifecycle and state management
- `TabExtensionPoint`: Extension registration and management interface

**Management System:**
- `NekoamaTabManager`: Singleton managing all tabs, state persistence, and switching
- `TabExtensionAdapter`: Adapts TabExtension to NekoamaTab interface
- `ExtensionDiscovery`: Discovers and loads extensions from various sources

**Communication System:**
- `TabEventSystem`: Event-driven communication between tabs and extensions
- `TabExtensionConfigManager`: Configuration persistence and management

### Creating Custom Extensions

**Basic Extension:**
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
        // Return your UI component
        return JPanel()
    }

    override fun getTabState(): Map<String, Any> {
        // Return state for persistence
        return mapOf("data" to "value")
    }

    override fun restoreTabState(state: Map<String, Any>) {
        // Restore saved state
    }
}
```

**Register Extension:**
```kotlin
val extension = MyCustomExtension()
TabExtensionPointSingleton.getInstance().registerExtension(extension)
```

### Event Communication

**Publish Events:**
```kotlin
TabEventSystemSingleton.getInstance().publishEvent(
    TabRefreshEvent("my_tab_id")
)
```

**Subscribe to Events:**
```kotlin
TabEventSystemSingleton.getInstance().subscribe(
    TabRefreshEvent::class.java,
    object : TabEventHandler<TabRefreshEvent> {
        override fun handleEvent(event: TabRefreshEvent) {
            // Handle refresh event
        }
    }
)
```

### Built-in Tabs

1. **Overview Tab**: Default dashboard showing system status, quick actions, and usage summary
2. **Token Statistics Tab**: Enhanced token usage tracking with export capabilities
3. **Demo Extension**: Example tab demonstrating extension capabilities

### Extension Features

- **Dynamic Loading**: Extensions can be loaded/unloaded at runtime
- **State Persistence**: Tab states are automatically saved and restored
- **Event Communication**: Type-safe event system for inter-component communication
- **Configuration Management**: Extension settings with persistent storage
- **Compatibility Checking**: Automatic validation of extension compatibility
- **Error Isolation**: Extension failures don't affect other functionality

### User Interface

- **Extension Info Button**: View loaded extensions and system status
- **Tab Management**: Native IntelliJ tab behavior with drag-and-drop support
- **State Preservation**: Tab content preserved when switching between tabs
- **Refresh Controls**: Manual refresh of all tabs or individual tabs