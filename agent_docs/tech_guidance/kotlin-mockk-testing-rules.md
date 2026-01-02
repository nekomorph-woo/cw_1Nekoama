# MockK Testing Technical Constraints

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: All mocks must be explicit, controllable, and isolated
- **Framework Standard**: JUnit 5 is required for all new test cases

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| Mock Creation | `Mockito.mock()` | `mockk<T>()` |
| Verification | `verify()` | `verify { }` |
| Stubbing | `when().thenReturn()` | `every { } returns` |
| Argument Matching | `any()` | `any<T>()` |
| Private Mocking | Reflection hacks | `mockkPrivate()` |
| Static Mocking | PowerMock | `mockkStatic()` |
| Object Mocking | N/A | `mockkObject()` |
| Constructor Mocking | N/A | `mockkConstructor()` |
| Test Framework | `org.junit.Test` (JUnit 4) | `org.junit.jupiter.api.Test` (JUnit 5) |

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| Mock Creation | `Mockito.mock()` | `mockk<T>()` |
| Verification | `verify()` | `verify { }` |
| Stubbing | `when().thenReturn()` | `every { } returns` |
| Argument Matching | `any()` | `any<T>()` |
| Private Mocking | Reflection hacks | `mockkPrivate()` |
| Static Mocking | PowerMock | `mockkStatic()` |
| Object Mocking | N/A | `mockkObject()` |
| Constructor Mocking | N/A | `mockkConstructor()` |

## 3. Critical Snippets (核心代码范式)
```kotlin
// Basic Mock Creation
val service = mockk<MyService>()

// Stubbing Behavior
every { service.getData(any()) } returns Result.success("data")

// Verification
verify { service.getData("input") }
verify(exactly = 1) { service.getData(any()) }

// Exception Stubbing
every { service.process() } throws RuntimeException("error")

// Relaxed Mocks (use sparingly)
val relaxedMock = mockk<MyService>(relaxed = true)

// Spy (partial mock)
val spy = spyk(originalService)
every { spy.calculate() } returns 42

// Mocking static methods
mockkStatic(Math::class)
every { Math.random() } returns 0.5

// Mocking objects
mockkObject(MyObject)
every { MyObject.doSomething() } returns "mocked"
```

## 4. Verification (如何验证)
* Check: All mocks use `mockk<T>()` syntax, never Mockito
* Check: Stubbing uses `every { } returns` pattern
* Check: Verification uses `verify { }` block syntax
* Check: Argument matchers are type-safe (`any<T>()`)
* Check: Mocks are properly cleared in `@After` if reused
* Check: No unnecessary relaxed mocks without justification
* Check: Static and object mocking are properly unmocked in cleanup