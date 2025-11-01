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
│   └── toolwindow/           # Tool window (NekoamaToolWindowFactory, NekoamaToolWindow)
└── NekoamaPlugin.kt          # Plugin entry point
```

### Key Architectural Patterns

1. **Layered Architecture**: Clear separation between AI services, core utilities, data layer, platform integrations, and presentation layer.

2. **Provider Pattern**: AI providers (OpenAI, CustomAPI) implement a common interface with pluggable HTTP clients and response parsers.

3. **Background Task Management**: All AI operations run in background tasks using IntelliJ's ProgressManager to maintain IDE responsiveness.

4. **Security-First Design**: API keys stored in IntelliJ Password Safe, sensitive data never logged.

5. **Modular AI Pipeline**: Context extraction → Prompt generation → Provider call → Response parsing → Suggestion application.

### Core Components

- **AI Provider Interface**: Abstracts AI service calls with retry, timeout, and concurrency management
- **Code Context Model**: Rich context extraction from PSI for generating meaningful prompts
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
- `build.gradle.kts` - Build configuration with all dependencies and repositories
- `README.md` - Project documentation and usage guide