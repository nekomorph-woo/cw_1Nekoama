package com.cw2.nekoama.infrastructure.code_suggestion_gen.client.openai

import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableContext
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIMessage
import com.cw2.nekoama.infrastructure.code_suggestion_gen.model.openai.OpenAIRequest

/**
 * OpenAI 提示词模板服务
 *
 * 根据不同的代码生成场景，创建对应的 OpenAI 请求，支持命名建议、注释生成和自定义内容生成。
 * 提示模板遵循场景化设计，可灵活配置以适应不同的 AI 响应需求。
 */
class PromptTemplateService {
    // 构建基于用户语言偏好的系统提示词，用于控制输出语言（注释/说明/描述）
    private fun buildLanguageSystemMessage(): OpenAIMessage? {
        return try {
            val pref = NekoamaSettings.getInstance().languagePreference.uppercase()
            val instruction =
                "System instruction: Use {LANG} for all comments, explanations, and descriptions in your replies."
            val content = when (pref) {
                "EN" -> instruction.replace("{LANG}", "English")
                "ZH", "ZH_CN", "ZH-CN" -> instruction.replace("{LANG}", "Simple Chinese")
                else -> null // AUTO: 让模型根据代码上下文自动选择最合适的语言
            }
            content?.let { OpenAIMessage("system", it) }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val SYSTEM_PROMPT_BASE =
            """You are a professional code assistant that helps developers with code naming and comment generation.
Using the provided code context, produce high-quality suggestions.
Your responses must be accurate, concise, and aligned with established programming best practices."""

        const val NAMING_SYSTEM_PROMPT = """$SYSTEM_PROMPT_BASE

Analysis phase before suggesting:
1. Identify the core purpose and responsibility of the code element
2. Consider the business domain and terminology used in the project
3. Determine if the element represents an action, entity, property, or concept
4. Generate names that reflect this understanding

For naming suggestions:
1. Provide 3-5 high-quality name candidates.
2. Each suggestion should include: "name - brief description".
3. Follow the naming conventions of the target language.
4. Ensure excellent readability and precise semantics.
5. Avoid abbreviations and ambiguous terms.

Avoid these patterns:
- Single letter names except for loop counters (i, j, k)
- Generic names (data, info, temp, manager, handler) unless contextually appropriate
- Abbreviations that aren't widely recognized (usr → user, cnt → count is OK)
- Hungarian notation or type prefixes (strName, intCount)

Scoring criteria:
- 0.9-1.0: Perfect match - follows all conventions, highly descriptive
- 0.7-0.8: Good - mostly correct with minor improvements possible
- 0.5-0.6: Acceptable - works but could be clearer
- Below 0.5: Poor - avoid using

Language requirement:
- The "name" field MUST ALWAYS be in English as it is a code identifier
- The "description" and "reasoning" fields follow the language preference setting

Return strictly in JSON format using the structure below. Do not include any extra text:
{
  "suggestions": [
    {
      "name": "proposedName",
      "description": "brief description of the name",
      "score": 0.9,
      "reasoning": "why this name is appropriate"
    }
  ]
}

IMPORTANT: Each suggestion should use this exact format with the following structure:
- "name": The proposed name for the code element (MUST be in English)
- "description": A very brief description (1-3 words) of what the name represents
- "score": A confidence score between 0.0 and 1.0
- "reasoning": A concise explanation (1-2 sentences) of why this name is appropriate

The suggestions should be ordered by quality, with the highest-scoring suggestion first."""

        const val COMMENT_SYSTEM_PROMPT = """$SYSTEM_PROMPT_BASE

For comment generation:
1. Analyze the code's purpose and behavior thoroughly.
2. Produce clear and accurate comments.
3. Use the appropriate comment style for the target language (KDoc for Kotlin, Javadoc for Java).
4. Include parameter descriptions, return value description, and exceptions when applicable.
5. Avoid obvious or redundant comments (e.g., "this method returns a value").
6. Handle edge cases appropriately:
   - Empty/abstract methods: Focus on purpose and contract, not implementation
   - Unused variables: Note intended use case or future purpose
   - Abstract classes/interfaces: Describe contract and expected behavior

IMPORTANT: Return comments in the following JSON format with strict structure:

{
  "content": "primary comment content",
  "parameters": [
    {"name": "parameter name", "description": "parameter description"}
  ],
  "returnDescription": "return value description",
  "exceptions": [
    {"type": "exception type", "description": "exception description"}
  ]
}

Each field should contain:
- "content": The main comment text (2-4 sentences), providing a clear description of the code's purpose and behavior
- "parameters": Array of parameter objects, each with "name" (exact parameter name) and "description" (clear explanation of what the parameter represents)
- "returnDescription": Clear description of what the method/function returns and when it's used
- "exceptions": Array of exception objects, each with "type" (exact exception class name) and "description" (when this exception might be thrown)

If a section is not applicable, provide an empty array [] for arrays or null for single values.

Line width formatting requirements:
- Maximum line length: 80-100 characters per line
- Break lines at natural pauses: after sentence endings ('.' for English and '。' for Chinese), commas
- Use paragraph breaks (empty lines) to separate different concepts
- For parameter/exception descriptions in JSON, each on a new line with proper indentation

Ensure all content is concise, focused, and provides genuine value to developers reading the code."""

        const val CUSTOM_GENERATE_SYSTEM_PROMPT = """You are an expert code assistant helping developers with various programming tasks.

Core principles:
1. Respond directly and accurately to the user's specific request
2. Keep responses concise and focused
3. For code modifications: preserve existing logic and structure unless explicitly asked to change
4. Use clear formatting with code blocks, examples, or structured explanations as appropriate
5. Match the language used in the user's request for explanations and descriptions

Common request types:
- Code explanation: Analyze functionality, logic, and patterns
- Refactoring: Suggest improvements while maintaining behavior
- Code generation: Provide implementation with comments
- Bug investigation: Identify issues and propose fixes
- Performance optimization: Recommend efficient alternatives

Output formatting:
- Use ```language code blocks for code
- Break long lines at 80-100 characters for readability
- Use numbered lists or bullet points for multiple items
- Avoid unnecessary verbosity or stating the obvious"""
    }

    /**
     * 创建命名建议生成的提示词
     */
    fun createNamingPrompt(context: CodeContext, model: String = "gpt-4"): OpenAIRequest {
        val userPrompt = buildString {
            appendLine("Please provide naming suggestions for the following code element:")
            appendLine()
            appendLine("=== CORE IDENTITY ===")
            appendLine("Language: ${context.language}")
            appendLine("Element type: ${context.elementType}")

            // 用户意图描述（优先级最高）
            context.userIntent?.let { intent ->
                appendLine("\nUser intent: $intent")
            }

            // 根据不同类型的代码元素添加特定信息
            when (context) {
                is MethodContext -> appendMethodContext(context)
                is ClassContext -> appendClassContext(context)
                is VariableContext -> appendVariableContext(context)
            }

            // 项目信息（优先级最低，作为补充上下文）
            appendLine("\n=== PROJECT CONTEXT ===")
            appendLine("Project name: ${context.projectMeta.projectName}")

            // 命名模式分析
            context.surroundingContext.namingPatterns?.let { patterns ->
                appendLine("Project naming conventions: ${patterns.conventionType}")
            }
        }

        val messages = mutableListOf(
            OpenAIMessage("system", NAMING_SYSTEM_PROMPT)
        )
        buildLanguageSystemMessage()?.let { messages.add(it) }
        messages.add(
            OpenAIMessage(
                "system", """
  Language requirement for response fields: Use had specified language for the 'description' and 'reasoning' fields only.
  The 'name' field must ALWAYS be in English as it is a code identifier.
"""
            )
        )
        messages.add(OpenAIMessage("user", userPrompt))
        return OpenAIRequest(
            model = model,
            messages = messages,
            maxTokens = 300,
            temperature = 0.7
        )
    }

    /**
     * 创建注释生成的提示词
     */
    fun createCommentPrompt(context: CodeContext, model: String = "gpt-4"): OpenAIRequest {
        val userPrompt = buildString {
            appendLine("Please generate comments for the following code element:")
            appendLine()
            appendLine("=== CODE SIGNATURE ===")
            appendLine("Language: ${context.language}")
            appendLine("Element type: ${context.elementType}")

            // 根据不同类型的代码元素添加特定信息
            when (context) {
                is MethodContext -> appendMethodContextForComment(context)
                is ClassContext -> appendClassContextForComment(context)
                is VariableContext -> appendVariableContextForComment(context)
            }

            // 项目上下文
            appendLine("\n=== PROJECT CONTEXT ===")
            appendLine("Project: ${context.projectMeta.projectName}")
        }

        val messages = mutableListOf(
            OpenAIMessage("system", COMMENT_SYSTEM_PROMPT)
        )
        buildLanguageSystemMessage()?.let { messages.add(it) }
        messages.add(OpenAIMessage("user", userPrompt))
        return OpenAIRequest(
            model = model,
            messages = messages,
            maxTokens = 800,
            temperature = 0.6
        )
    }

    /**
     * 创建自定义内容生成的提示词
     */
    fun createCustomPrompt(prompt: String, context: CodeContext?, model: String = "gpt-4"): OpenAIRequest {
        val userPrompt = buildString {
            appendLine("=== USER REQUEST ===")
            appendLine(prompt)

            context?.let { ctx ->
                appendLine()
                appendLine("=== CODE CONTEXT ===")
                appendLine("Language: ${ctx.language}")
                appendLine("Project: ${ctx.projectMeta.projectName}")
            }
        }

        val messages = mutableListOf(
            OpenAIMessage("system", CUSTOM_GENERATE_SYSTEM_PROMPT)
        )
        buildLanguageSystemMessage()?.let { messages.add(it) }
        messages.add(OpenAIMessage("user", userPrompt))
        return OpenAIRequest(
            model = model,
            messages = messages,
            maxTokens = 8192,
            temperature = 0.7
        )
    }

    /**
     * 为方法上下文添加详细信息（命名建议专用）
     */
    private fun StringBuilder.appendMethodContext(context: MethodContext) {
        appendLine("\n=== SIGNATURE ===")
        context.methodName?.let { name ->
            appendLine("Current name: $name")
        }
        appendLine("Return type: ${context.returnType.typeName}")

        if (context.parameters.isNotEmpty()) {
            appendLine("Parameters:")
            context.parameters.forEach { param ->
                appendLine("  ${param.name}: ${param.type.typeName}")
            }
        }

        if (context.modifiers.isNotEmpty()) {
            appendLine("Modifiers: ${context.modifiers.joinToString(", ")}")
        }

        if (context.exceptions.isNotEmpty()) {
            appendLine("Possible exceptions: ${context.exceptions.map { it.typeName }.joinToString(", ")}")
        }

        context.containingClass?.let { clazz ->
            appendLine("Containing class: ${clazz.name}")
        }

        context.methodBody?.let { body ->
            appendLine("\n=== BEHAVIOR & CONTEXT ===")
            appendLine("Method implementation:")
            body.lines().forEach { line ->
                appendLine("  $line")
            }
        }
    }

    /**
     * 为类上下文添加详细信息（命名建议专用）
     */
    private fun StringBuilder.appendClassContext(context: ClassContext) {
        appendLine("\n=== SIGNATURE ===")
        context.className?.let { name ->
            appendLine("Current name: $name")
        }

        context.superClass?.let { superClass ->
            appendLine("Superclass: ${superClass.typeName}")
        }

        appendLine("Package: ${context.packageName}")

        // 添加类类型信息
        when {
            context.isInterface -> appendLine("Type: Interface")
            context.isEnum -> appendLine("Type: Enum")
            context.isAbstract -> appendLine("Type: Abstract class")
            else -> appendLine("Type: Class")
        }

        appendLine("\n=== BEHAVIOR & CONTEXT ===")
        // 类文档注释
        context.classComment?.let { comment ->
            appendLine("Class documentation:")
            appendLine("  $comment")
        }

        // 类字段列表
        if (context.fieldNames.isNotEmpty()) {
            appendLine("Fields:")
            context.fieldNames.forEach { field ->
                appendLine("  - $field")
            }
        }

        // 类方法列表
        if (context.methodNames.isNotEmpty()) {
            appendLine("Methods:")
            context.methodNames.forEach { method ->
                appendLine("  - $method")
            }
        }
    }

    /**
     * 为变量上下文添加详细信息（命名建议专用）
     */
    private fun StringBuilder.appendVariableContext(context: VariableContext) {
        appendLine("\n=== SIGNATURE ===")
        context.variableName?.let { name ->
            appendLine("Current name: $name")
        }
        appendLine("Type: ${context.variableType.typeName}")
        appendLine("Scope: ${context.scope}")

        if (context.modifiers.isNotEmpty()) {
            appendLine("Modifiers: ${context.modifiers.joinToString(", ")}")
        }

        context.initializer?.let { init ->
            appendLine("Initializer: $init")
        }

        context.containingClass?.let { clazz ->
            appendLine("Containing class: ${clazz.name}")
        }

        context.containingMethod?.let { method ->
            appendLine("Containing method: ${method.name}")
        }

        // 变量使用示例
        if (context.usageExamples.isNotEmpty()) {
            appendLine("\n=== BEHAVIOR & CONTEXT ===")
            appendLine("Usage examples:")
            context.usageExamples.forEach { example ->
                appendLine("  $example")
            }
        }
    }

    /**
     * 为方法上下文添加注释专用详细信息
     */
    private fun StringBuilder.appendMethodContextForComment(context: MethodContext) {
        appendLine("\n=== SIGNATURE ===")
        val modifiers = if (context.modifiers.isNotEmpty())
            context.modifiers.joinToString(" ") + " " else ""
        val name = context.methodName ?: "[To be named]"
        val params = context.parameters.joinToString(", ") { "${it.type.typeName} ${it.name}" }
        val returnType = context.returnType.typeName

        appendLine("Method: $modifiers$returnType $name($params)")

        if (context.exceptions.isNotEmpty()) {
            appendLine("Throws: ${context.exceptions.joinToString(", ") { it.typeName }}")
        }

        context.containingClass?.let { clazz ->
            appendLine("Containing class: ${clazz.name}")
        }

        // 边界处理：抽象方法或空方法
        val isAbstractOrEmpty = context.isAbstract || context.methodBody.isNullOrEmpty() || context.methodBody?.lines()?.all { it.trim().isEmpty() } == true

        if (isAbstractOrEmpty) {
            appendLine("\n=== CONTRACT ===")
            appendLine("Note: This is ${if (context.isAbstract) "an abstract method" else "an empty method"}.")
            appendLine("Focus the comment on the contract, purpose, and expected behavior.")
        } else {
            context.methodBody?.let { body ->
                appendLine("\n=== IMPLEMENTATION ===")
                appendLine("Method implementation:")
                body.lines().forEach { line ->
                    appendLine("  $line")
                }
            }
        }
    }

    /**
     * 为类上下文添加注释专用详细信息
     */
    private fun StringBuilder.appendClassContextForComment(context: ClassContext) {
        appendLine("\n=== SIGNATURE ===")
        val name = context.className ?: "[To be named]"

        // 根据类类型生成正确的声明
        val classKeyword = when {
            context.isInterface -> "interface"
            context.isEnum -> "enum"
            context.isAbstract -> "abstract class"
            else -> "class"
        }

        appendLine("Definition: $classKeyword $name")

        context.superClass?.let { superClass ->
            val keyword = if (context.isInterface) "extends" else "extends"
            appendLine("Extends: $keyword ${superClass.typeName}")
        }

        appendLine("Package: ${context.packageName}")

        // 边界处理：抽象类或接口
        val isAbstractOrInterface = context.isAbstract || context.isInterface

        if (isAbstractOrInterface) {
            appendLine("\n=== CONTRACT ===")
            appendLine("Note: This is ${if (context.isInterface) "an interface" else "an abstract class"}.")
            appendLine("Focus the comment on the contract, expected behavior, and usage guidelines.")
        }

        appendLine("\n=== CONTEXT ===")

        // 类文档注释
        context.classComment?.let { comment ->
            appendLine("Existing documentation:")
            appendLine("  $comment")
        }

        // 类字段列表
        if (context.fieldNames.isNotEmpty()) {
            appendLine("Fields:")
            context.fieldNames.forEach { field ->
                appendLine("  - $field")
            }
        }

        // 类方法列表
        if (context.methodNames.isNotEmpty()) {
            appendLine("Methods:")
            context.methodNames.forEach { method ->
                appendLine("  - $method")
            }
        }
    }

    /**
     * 为变量上下文添加注释专用详细信息
     */
    private fun StringBuilder.appendVariableContextForComment(context: VariableContext) {
        appendLine("\n=== SIGNATURE ===")
        val modifiers = if (context.modifiers.isNotEmpty())
            context.modifiers.joinToString(" ") + " " else ""
        val type = context.variableType.typeName
        val name = context.variableName ?: "[To be named]"
        val init = context.initializer?.let { " = $it" } ?: ""

        appendLine("Declaration: $modifiers$type $name$init")
        appendLine("Scope: ${context.scope}")

        context.containingClass?.let { clazz ->
            appendLine("Containing class: ${clazz.name}")
        }

        context.containingMethod?.let { method ->
            appendLine("Containing method: ${method.name}")
        }

        // 边界处理：未使用变量
        val isUnused = context.usageExamples.isEmpty()

        if (isUnused) {
            appendLine("\n=== USAGE NOTE ===")
            appendLine("Note: This variable does not appear to have any usage yet.")
            appendLine("Focus the comment on the intended purpose, expected usage, or future use case.")
        } else {
            // 变量使用示例
            appendLine("\n=== USAGE CONTEXT ===")
            appendLine("Usage examples:")
            context.usageExamples.forEach { example ->
                appendLine("  $example")
            }
        }
    }
}
