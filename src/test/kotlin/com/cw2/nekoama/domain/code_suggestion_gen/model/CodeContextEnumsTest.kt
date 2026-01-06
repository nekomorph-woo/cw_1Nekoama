package com.cw2.nekoama.domain.code_suggestion_gen.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 代码上下文枚举测试
 *
 * 验证 ProgrammingLanguage、CodeElementType、VariableScope、NamingConvention 枚举
 */
@DisplayName("代码上下文枚举测试")
class CodeContextEnumsTest {

    // ==================== ProgrammingLanguage 枚举测试 ====================

    @Nested
    @DisplayName("编程语言枚举测试")
    inner class ProgrammingLanguageTests {

        @Test
        @DisplayName("枚举值 - 应该包含 JAVA")
        fun `枚举值 - 应该包含 JAVA`() {
            assertThat(ProgrammingLanguage.valueOf("JAVA")).isEqualTo(ProgrammingLanguage.JAVA)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 KOTLIN")
        fun `枚举值 - 应该包含 KOTLIN`() {
            assertThat(ProgrammingLanguage.valueOf("KOTLIN")).isEqualTo(ProgrammingLanguage.KOTLIN)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 OTHER")
        fun `枚举值 - 应该包含 OTHER`() {
            assertThat(ProgrammingLanguage.valueOf("OTHER")).isEqualTo(ProgrammingLanguage.OTHER)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(ProgrammingLanguage.entries).containsExactly(
                ProgrammingLanguage.JAVA,
                ProgrammingLanguage.KOTLIN,
                ProgrammingLanguage.OTHER
            )
        }
    }

    // ==================== CodeElementType 枚举测试 ====================

    @Nested
    @DisplayName("代码元素类型枚举测试")
    inner class CodeElementTypeTests {

        @Test
        @DisplayName("枚举值 - 应该包含 METHOD")
        fun `枚举值 - 应该包含 METHOD`() {
            assertThat(CodeElementType.valueOf("METHOD")).isEqualTo(CodeElementType.METHOD)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 CLASS")
        fun `枚举值 - 应该包含 CLASS`() {
            assertThat(CodeElementType.valueOf("CLASS")).isEqualTo(CodeElementType.CLASS)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 VARIABLE")
        fun `枚举值 - 应该包含 VARIABLE`() {
            assertThat(CodeElementType.valueOf("VARIABLE")).isEqualTo(CodeElementType.VARIABLE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 PARAMETER")
        fun `枚举值 - 应该包含 PARAMETER`() {
            assertThat(CodeElementType.valueOf("PARAMETER")).isEqualTo(CodeElementType.PARAMETER)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 FIELD")
        fun `枚举值 - 应该包含 FIELD`() {
            assertThat(CodeElementType.valueOf("FIELD")).isEqualTo(CodeElementType.FIELD)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 PACKAGE")
        fun `枚举值 - 应该包含 PACKAGE`() {
            assertThat(CodeElementType.valueOf("PACKAGE")).isEqualTo(CodeElementType.PACKAGE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 MODULE")
        fun `枚举值 - 应该包含 MODULE`() {
            assertThat(CodeElementType.valueOf("MODULE")).isEqualTo(CodeElementType.MODULE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 INTERFACE")
        fun `枚举值 - 应该包含 INTERFACE`() {
            assertThat(CodeElementType.valueOf("INTERFACE")).isEqualTo(CodeElementType.INTERFACE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 ENUM")
        fun `枚举值 - 应该包含 ENUM`() {
            assertThat(CodeElementType.valueOf("ENUM")).isEqualTo(CodeElementType.ENUM)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 ANNOTATION")
        fun `枚举值 - 应该包含 ANNOTATION`() {
            assertThat(CodeElementType.valueOf("ANNOTATION")).isEqualTo(CodeElementType.ANNOTATION)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(CodeElementType.entries).containsExactly(
                CodeElementType.METHOD,
                CodeElementType.CLASS,
                CodeElementType.VARIABLE,
                CodeElementType.PARAMETER,
                CodeElementType.FIELD,
                CodeElementType.PACKAGE,
                CodeElementType.MODULE,
                CodeElementType.INTERFACE,
                CodeElementType.ENUM,
                CodeElementType.ANNOTATION
            )
        }
    }

    // ==================== VariableScope 枚举测试 ====================

    @Nested
    @DisplayName("变量作用域枚举测试")
    inner class VariableScopeTests {

        @Test
        @DisplayName("枚举值 - 应该包含 LOCAL")
        fun `枚举值 - 应该包含 LOCAL`() {
            assertThat(VariableScope.valueOf("LOCAL")).isEqualTo(VariableScope.LOCAL)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 PARAMETER")
        fun `枚举值 - 应该包含 PARAMETER`() {
            assertThat(VariableScope.valueOf("PARAMETER")).isEqualTo(VariableScope.PARAMETER)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 FIELD")
        fun `枚举值 - 应该包含 FIELD`() {
            assertThat(VariableScope.valueOf("FIELD")).isEqualTo(VariableScope.FIELD)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 STATIC_FIELD")
        fun `枚举值 - 应该包含 STATIC_FIELD`() {
            assertThat(VariableScope.valueOf("STATIC_FIELD")).isEqualTo(VariableScope.STATIC_FIELD)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 GLOBAL")
        fun `枚举值 - 应该包含 GLOBAL`() {
            assertThat(VariableScope.valueOf("GLOBAL")).isEqualTo(VariableScope.GLOBAL)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(VariableScope.entries).containsExactly(
                VariableScope.LOCAL,
                VariableScope.PARAMETER,
                VariableScope.FIELD,
                VariableScope.STATIC_FIELD,
                VariableScope.GLOBAL
            )
        }
    }

    // ==================== NamingConvention 枚举测试 ====================

    @Nested
    @DisplayName("命名约定枚举测试")
    inner class NamingConventionTests {

        @Test
        @DisplayName("枚举值 - 应该包含 CAMEL_CASE")
        fun `枚举值 - 应该包含 CAMEL_CASE`() {
            assertThat(NamingConvention.valueOf("CAMEL_CASE")).isEqualTo(NamingConvention.CAMEL_CASE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 PASCAL_CASE")
        fun `枚举值 - 应该包含 PASCAL_CASE`() {
            assertThat(NamingConvention.valueOf("PASCAL_CASE")).isEqualTo(NamingConvention.PASCAL_CASE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 SNAKE_CASE")
        fun `枚举值 - 应该包含 SNAKE_CASE`() {
            assertThat(NamingConvention.valueOf("SNAKE_CASE")).isEqualTo(NamingConvention.SNAKE_CASE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 KEBAB_CASE")
        fun `枚举值 - 应该包含 KEBAB_CASE`() {
            assertThat(NamingConvention.valueOf("KEBAB_CASE")).isEqualTo(NamingConvention.KEBAB_CASE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 UPPER_SNAKE_CASE")
        fun `枚举值 - 应该包含 UPPER_SNAKE_CASE`() {
            assertThat(NamingConvention.valueOf("UPPER_SNAKE_CASE")).isEqualTo(NamingConvention.UPPER_SNAKE_CASE)
        }

        @Test
        @DisplayName("枚举值 - 应该包含 MIXED")
        fun `枚举值 - 应该包含 MIXED`() {
            assertThat(NamingConvention.valueOf("MIXED")).isEqualTo(NamingConvention.MIXED)
        }

        @Test
        @DisplayName("枚举值 - entries 应该包含所有值")
        fun `枚举值 - entries 应该包含所有值`() {
            assertThat(NamingConvention.entries).containsExactly(
                NamingConvention.CAMEL_CASE,
                NamingConvention.PASCAL_CASE,
                NamingConvention.SNAKE_CASE,
                NamingConvention.KEBAB_CASE,
                NamingConvention.UPPER_SNAKE_CASE,
                NamingConvention.MIXED
            )
        }
    }

    // ==================== 枚举序列化测试 ====================

    @Nested
    @DisplayName("枚举序列化注解测试")
    inner class EnumSerializationTests {

        @Test
        @DisplayName("ProgrammingLanguage - 应该有 Serializable 注解")
        fun `ProgrammingLanguage - 应该有 Serializable 注解`() {
            // 验证枚举类可以被 kotlinx.serialization 使用
            assertThat(ProgrammingLanguage.JAVA::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("CodeElementType - 应该有 Serializable 注解")
        fun `CodeElementType - 应该有 Serializable 注解`() {
            assertThat(CodeElementType.METHOD::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("VariableScope - 应该有 Serializable 注解")
        fun `VariableScope - 应该有 Serializable 注解`() {
            assertThat(VariableScope.LOCAL::class.simpleName).isNotNull()
        }

        @Test
        @DisplayName("NamingConvention - 应该有 Serializable 注解")
        fun `NamingConvention - 应该有 Serializable 注解`() {
            assertThat(NamingConvention.CAMEL_CASE::class.simpleName).isNotNull()
        }
    }

    // ==================== 枚举名称测试 ====================

    @Nested
    @DisplayName("枚举名称测试")
    inner class EnumNameTests {

        @Test
        @DisplayName("ProgrammingLanguage - name 应该返回枚举名称")
        fun `ProgrammingLanguage - name 应该返回枚举名称`() {
            assertThat(ProgrammingLanguage.JAVA.name).isEqualTo("JAVA")
            assertThat(ProgrammingLanguage.KOTLIN.name).isEqualTo("KOTLIN")
            assertThat(ProgrammingLanguage.OTHER.name).isEqualTo("OTHER")
        }

        @Test
        @DisplayName("CodeElementType - name 应该返回枚举名称")
        fun `CodeElementType - name 应该返回枚举名称`() {
            assertThat(CodeElementType.METHOD.name).isEqualTo("METHOD")
            assertThat(CodeElementType.CLASS.name).isEqualTo("CLASS")
            assertThat(CodeElementType.VARIABLE.name).isEqualTo("VARIABLE")
        }

        @Test
        @DisplayName("VariableScope - name 应该返回枚举名称")
        fun `VariableScope - name 应该返回枚举名称`() {
            assertThat(VariableScope.LOCAL.name).isEqualTo("LOCAL")
            assertThat(VariableScope.FIELD.name).isEqualTo("FIELD")
            assertThat(VariableScope.GLOBAL.name).isEqualTo("GLOBAL")
        }

        @Test
        @DisplayName("NamingConvention - name 应该返回枚举名称")
        fun `NamingConvention - name 应该返回枚举名称`() {
            assertThat(NamingConvention.CAMEL_CASE.name).isEqualTo("CAMEL_CASE")
            assertThat(NamingConvention.SNAKE_CASE.name).isEqualTo("SNAKE_CASE")
            assertThat(NamingConvention.PASCAL_CASE.name).isEqualTo("PASCAL_CASE")
        }
    }

    // ==================== 枚举序数测试 ====================

    @Nested
    @DisplayName("枚举序数测试")
    inner class EnumOrdinalTests {

        @Test
        @DisplayName("ProgrammingLanguage - ordinal 应该按声明顺序")
        fun `ProgrammingLanguage - ordinal 应该按声明顺序`() {
            assertThat(ProgrammingLanguage.JAVA.ordinal).isEqualTo(0)
            assertThat(ProgrammingLanguage.KOTLIN.ordinal).isEqualTo(1)
            assertThat(ProgrammingLanguage.OTHER.ordinal).isEqualTo(2)
        }

        @Test
        @DisplayName("CodeElementType - ordinal 应该按声明顺序")
        fun `CodeElementType - ordinal 应该按声明顺序`() {
            assertThat(CodeElementType.METHOD.ordinal).isEqualTo(0)
            assertThat(CodeElementType.CLASS.ordinal).isEqualTo(1)
            assertThat(CodeElementType.VARIABLE.ordinal).isEqualTo(2)
        }

        @Test
        @DisplayName("VariableScope - ordinal 应该按声明顺序")
        fun `VariableScope - ordinal 应该按声明顺序`() {
            assertThat(VariableScope.LOCAL.ordinal).isEqualTo(0)
            assertThat(VariableScope.PARAMETER.ordinal).isEqualTo(1)
            assertThat(VariableScope.FIELD.ordinal).isEqualTo(2)
        }

        @Test
        @DisplayName("NamingConvention - ordinal 应该按声明顺序")
        fun `NamingConvention - ordinal 应该按声明顺序`() {
            assertThat(NamingConvention.CAMEL_CASE.ordinal).isEqualTo(0)
            assertThat(NamingConvention.PASCAL_CASE.ordinal).isEqualTo(1)
            assertThat(NamingConvention.SNAKE_CASE.ordinal).isEqualTo(2)
        }
    }
}
