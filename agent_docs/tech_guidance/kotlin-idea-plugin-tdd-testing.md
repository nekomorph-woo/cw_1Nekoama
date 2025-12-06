# Kotlin IDEA Plugin TDD Testing Constraints

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: Red-Green-Refactor cycle drives all plugin feature development

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| Test Order | Write implementation first | Write failing test first |
| Test Scope | Testing implementation details | Testing public behavior only |
| Test Data | Hardcoded test values | Parameterized test builders |
| Mock Usage | Mocking value objects | Mocking dependencies/services |
| EDT Violations | UI operations in unit tests | `EdtTestUtil.runInEdtAndWait()` |
| PSI Access | Direct PSI manipulation | `WriteCommandAction.runWriteCommandAction()` |

## 3. Critical Snippets (核心代码范式)
```kotlin
// TDD Cycle Pattern
@Test
fun `should process code analysis request`() {
    // Arrange - Setup test data
    val mockService = mockk<AnalysisService>()
    val processor = CodeAnalysisProcessor(mockService)

    // Act - Execute failing test first
    val result = processor.process(testCode)

    // Assert - Verify behavior
    verify { mockService.analyze(testCode) }
    assertThat(result).isInstanceOf<AnalysisResult>()
}

// EDT-Safe Test Pattern
@Test
fun `should update UI safely`() {
    EdtTestUtil.runInEdtAndWait {
        val component = MyComponent()
        component.updateUI("test")

        assertThat(component.text).isEqualTo("test")
    }
}

// Write Command Pattern
@Test
fun `should modify PSI safely`() {
    WriteCommandAction.runWriteCommandAction(project) {
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText("Test.kt", KotlinLanguage.INSTANCE, "class Test {}")

        // Test PSI modifications
        assertThat(psiFile.classes).hasSize(1)
    }
}

// Parameterized Test Pattern
@ParameterizedTest
@ValueSource(strings = ["valid", "another_valid"])
fun `should validate code patterns`(code: String) {
    val validator = CodePatternValidator()

    val result = validator.validate(code)

    assertThat(result.isValid).isTrue()
}
```

## 4. Verification (如何验证)
* **Test Structure**: Every feature must have failing test before implementation
* **Coverage**: Plugin services must have >80% unit test coverage
* **EDT Safety**: All UI operations must run on EDT
* **PSI Safety**: All PSI modifications must run in Write Command
* **Mock Quality**: Mock only external dependencies, not value objects
* **Test Speed**: Unit tests must complete within 2 seconds
* **Integration**: Use TestContainers for external service integration tests
* **Isolation**: Tests must not share state or depend on execution order