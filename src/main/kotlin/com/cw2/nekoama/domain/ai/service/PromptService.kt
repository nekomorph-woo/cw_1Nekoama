package com.cw2.nekoama.domain.ai.service

import com.cw2.nekoama.infrastructure.ai.client.openai.OpenAIMessage
import com.cw2.nekoama.infrastructure.ai.client.openai.OpenAIRequest
import com.cw2.nekoama.domain.settings.model.NekoamaSettings
import com.cw2.nekoama.domain.code_suggestion_gen.model.ClassContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.CodeContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.MethodContext
import com.cw2.nekoama.domain.code_suggestion_gen.model.VariableContext

/**
 * OpenAI 提示模板系统
 *
 * 根据不同的代码生成场景，创建对应的提示词，支持命名建议、注释生成和自定义内容生成。
 * 提示模板遵循场景化设计，可灵活配置以适应不同的 AI 响应需求。
 */
class PromptService {
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

For naming suggestions:
1. Provide 3-5 high-quality name candidates.
2. Each suggestion should include: "name - brief description".
3. Follow the naming conventions of the target language.
4. Ensure excellent readability and precise semantics.
5. Avoid abbreviations and ambiguous terms.

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
- "name": The proposed name for the code element
- "description": A very brief description (1-3 words) of what the name represents
- "score": A confidence score between 0.0 and 1.0
- "reasoning": A concise explanation (1-2 sentences) of why this name is appropriate

The suggestions should be ordered by quality, with the highest-scoring suggestion first."""

        const val COMMENT_SYSTEM_PROMPT = """$SYSTEM_PROMPT_BASE

For comment generation:
1. Analyze the code's purpose and behavior.
2. Produce clear and accurate comments.
3. Use the appropriate comment style for the target language.
4. Include parameter descriptions, return value description, and exceptions when applicable.
5. Avoid obvious or redundant comments.

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

Ensure all content is concise, focused, and provides genuine value to developers reading the code."""

        const val CUSTOM_SYSTEM_PROMPT = """$SYSTEM_PROMPT_BASE

For custom generation:
1. Generate content according to the user's specific request.
2. Consider the provided code context.
3. Ensure the output is accurate and useful.
4. If the request is code-related, follow best practices.

IMPORTANT: Always use the same language as specified in the user's request for all generated content, explanations, and descriptions."""
    }

    /**
     * 创建命名建议生成的提示词
     */
    fun createNamingPrompt(context: CodeContext, model: String = "gpt-4"): OpenAIRequest {
        val userPrompt = buildString {
            appendLine("Please provide naming suggestions for the following code element:")
            appendLine()
            appendLine("Language: ${context.language}")
            appendLine("Element type: ${context.elementType}")

            // 根据不同类型的代码元素添加特定信息
            when (context) {
                is MethodContext -> appendMethodContext(context)
                is ClassContext -> appendClassContext(context)
                is VariableContext -> appendVariableContext(context)
            }

            // 添加周边代码上下文信息
            if (context.surroundingContext.precedingCode.isNotEmpty()) {
                appendLine("\nPreceding code:")
                context.surroundingContext.precedingCode.take(3).forEach { line ->
                    appendLine("  $line")
                }
            }

            if (context.surroundingContext.followingCode.isNotEmpty()) {
                appendLine("\nFollowing code:")
                context.surroundingContext.followingCode.take(3).forEach { line ->
                    appendLine("  $line")
                }
            }

            // 用户意图描述（例如：重命名/重构/优化）
            context.userIntent?.let { intent ->
                appendLine("\nUser intent: $intent")
            }

            // 项目信息
            appendLine("\nProject information:")
            appendLine("  Project name: ${context.projectMeta.projectName}")

            // 命名模式分析
            context.surroundingContext.namingPatterns?.let { patterns ->
                appendLine("\nProject naming conventions: ${patterns.conventionType}")
                if (patterns.commonPrefixes.isNotEmpty()) {
                    appendLine("  Common prefixes: ${patterns.commonPrefixes.joinToString(", ")}")
                }
                if (patterns.commonSuffixes.isNotEmpty()) {
                    appendLine("  Common suffixes: ${patterns.commonSuffixes.joinToString(", ")}")
                }
            }
        }

        val messages = mutableListOf(
            OpenAIMessage("system", NAMING_SYSTEM_PROMPT)
        )
        buildLanguageSystemMessage()?.let { messages.add(it) }
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
            appendLine("Language: ${context.language}")
            appendLine("Element type: ${context.elementType}")

            when (context) {
                is MethodContext -> appendMethodContextForComment(context)
                is ClassContext -> appendClassContextForComment(context)
                is VariableContext -> appendVariableContextForComment(context)
            }

            // 添加代码片段
            if (context.surroundingContext.precedingCode.isNotEmpty() ||
                context.surroundingContext.followingCode.isNotEmpty()
            ) {
                appendLine("\nRelated code:")
                context.surroundingContext.precedingCode.take(2).forEach { line ->
                    appendLine("  $line")
                }
                context.surroundingContext.followingCode.take(2).forEach { line ->
                    appendLine("  $line")
                }
            }

            // 注释风格偏好
            context.surroundingContext.codeStyleAnalysis?.commentStyle?.let { style ->
                appendLine("\nComment style: $style")
            }
        }

        val messages = mutableListOf(
            OpenAIMessage("system", COMMENT_SYSTEM_PROMPT)
        )
        buildLanguageSystemMessage()?.let { messages.add(it) }
        messages.add(OpenAIMessage("user", userPrompt))
        return OpenAIRequest(
            model = model,
            messages = messages,
            maxTokens = 400,
            temperature = 0.6
        )
    }

    /**
     * 创建自定义内容生成的提示词
     */
    fun createCustomPrompt(prompt: String, context: CodeContext?, model: String = "gpt-4"): OpenAIRequest {
        val userPrompt = buildString {
            appendLine("User request: $prompt")

            context?.let { ctx ->
                appendLine()
                appendLine("Code context:")
                appendLine("  Language: ${ctx.language}")
                appendLine("  Project: ${ctx.projectMeta.projectName}")

                if (ctx.surroundingContext.precedingCode.isNotEmpty()) {
                    appendLine("\nRelated code:")
                    ctx.surroundingContext.precedingCode.take(5).forEach { line ->
                        appendLine("  $line")
                    }
                }
            }
        }

        val messages = mutableListOf(
            OpenAIMessage("system", CUSTOM_SYSTEM_PROMPT)
        )
        buildLanguageSystemMessage()?.let { messages.add(it) }
        messages.add(OpenAIMessage("user", userPrompt))
        return OpenAIRequest(
            model = model,
            messages = messages,
            maxTokens = 500,
            temperature = 0.8
        )
    }

    /**
     * 为方法上下文添加详细信息（命名建议专用）
     */
    private fun StringBuilder.appendMethodContext(context: MethodContext) {
        appendLine("\nMethod information:")
        context.methodName?.let { name ->
            appendLine("  Current name: $name")
        }
        appendLine("  Return type: ${context.returnType.typeName}")

        if (context.parameters.isNotEmpty()) {
            appendLine("  Parameters:")
            context.parameters.forEach { param ->
                appendLine("    ${param.name}: ${param.type.typeName}")
            }
        }

        if (context.modifiers.isNotEmpty()) {
            appendLine("  Modifiers: ${context.modifiers.joinToString(", ")}")
        }

        if (context.exceptions.isNotEmpty()) {
            appendLine("  Possible exceptions: ${context.exceptions.map { it.typeName }.joinToString(", ")}")
        }

        context.methodBody?.let { body ->
            appendLine("\nMethod body snippet:")
            body.lines().take(5).forEach { line ->
                appendLine("  $line")
            }
        }

        context.containingClass?.let { clazz ->
            appendLine("\nContaining class: ${clazz.name}")
        }
    }

    /**
     * 为类上下文添加详细信息（命名建议专用）
     */
    private fun StringBuilder.appendClassContext(context: ClassContext) {
        appendLine("\nClass information:")
        context.className?.let { name ->
            appendLine("  Current name: $name")
        }

        context.superClass?.let { superClass ->
            appendLine("  Superclass: ${superClass.typeName}")
        }

        if (context.interfaces.isNotEmpty()) {
            appendLine("  Implements: ${context.interfaces.map { it.typeName }.joinToString(", ")}")
        }

        if (context.modifiers.isNotEmpty()) {
            appendLine("  Modifiers: ${context.modifiers.joinToString(", ")}")
        }

        appendLine("  Package: ${context.packageName}")

        if (context.fields.isNotEmpty()) {
            appendLine("  Key fields:")
            context.fields.take(5).forEach { field ->
                appendLine("    ${field.name}: ${field.type.typeName}")
            }
        }

        if (context.methods.isNotEmpty()) {
            appendLine("  Key methods:")
            context.methods.take(5).forEach { method ->
                appendLine("    ${method.name}(): ${method.returnType.typeName}")
            }
        }
    }

    /**
     * 为变量上下文添加详细信息（命名建议专用）
     */
    private fun StringBuilder.appendVariableContext(context: VariableContext) {
        appendLine("\nVariable information:")
        context.variableName?.let { name ->
            appendLine("  Current name: $name")
        }
        appendLine("  Type: ${context.variableType.typeName}")
        appendLine("  Scope: ${context.scope}")

        if (context.modifiers.isNotEmpty()) {
            appendLine("  Modifiers: ${context.modifiers.joinToString(", ")}")
        }

        context.initializer?.let { init ->
            appendLine("  Initializer: $init")
        }

        context.containingClass?.let { clazz ->
            appendLine("  Containing class: ${clazz.name}")
        }

        context.containingMethod?.let { method ->
            appendLine("  Containing method: ${method.name}")
        }
    }

    /**
     * 为方法上下文添加注释专用详细信息
     */
    private fun StringBuilder.appendMethodContextForComment(context: MethodContext) {
        appendLine("\nMethod signature:")
        val modifiers = if (context.modifiers.isNotEmpty())
            context.modifiers.joinToString(" ") + " " else ""
        val name = context.methodName ?: "[To be named]"
        val params = context.parameters.joinToString(", ") { "${it.type.typeName} ${it.name}" }
        val returnType = context.returnType.typeName

        appendLine("  $modifiers$returnType $name($params)")

        if (context.exceptions.isNotEmpty()) {
            appendLine("  throws: ${context.exceptions.joinToString(", ") { it.typeName }}")
        }

        context.methodBody?.let { body ->
            appendLine("\nMethod implementation:")
            body.lines().take(8).forEach { line ->
                appendLine("  $line")
            }
        }
    }

    /**
     * 为类上下文添加注释专用详细信息
     */
    private fun StringBuilder.appendClassContextForComment(context: ClassContext) {
        appendLine("\nClass definition:")
        val modifiers = if (context.modifiers.isNotEmpty())
            context.modifiers.joinToString(" ") + " " else ""
        val type = when {
            context.isInterface -> "interface"
            context.isEnum -> "enum"
            context.isAbstract -> "abstract class"
            else -> "class"
        }
        val name = context.className ?: "[To be named]"

        appendLine("  $modifiers$type $name")

        context.superClass?.let { superClass ->
            appendLine("    extends ${superClass.typeName}")
        }

        if (context.interfaces.isNotEmpty()) {
            appendLine("    implements ${context.interfaces.joinToString(", ") { it.typeName }}")
        }

        appendLine("\nClass structure overview:")
        if (context.fields.isNotEmpty()) {
            appendLine("  Field count: ${context.fields.size}")
        }
        if (context.methods.isNotEmpty()) {
            appendLine("  Method count: ${context.methods.size}")
        }
        if (context.innerClasses.isNotEmpty()) {
            appendLine("  Inner class count: ${context.innerClasses.size}")
        }
    }

    /**
     * 为变量上下文添加注释专用详细信息
     */
    private fun StringBuilder.appendVariableContextForComment(context: VariableContext) {
        appendLine("\nVariable declaration:")
        val modifiers = if (context.modifiers.isNotEmpty())
            context.modifiers.joinToString(" ") + " " else ""
        val type = context.variableType.typeName
        val name = context.variableName ?: "[To be named]"
        val init = context.initializer?.let { " = $it" } ?: ""

        appendLine("  $modifiers$type $name$init")
    }
}
