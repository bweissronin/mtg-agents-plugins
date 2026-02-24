package com.mtgagents.parser

import com.mtgagents.model.*
import java.io.File

/**
 * Generic parser that uses heuristics to detect agent-like classes.
 *
 * Detects:
 * - Classes/objects with "agent" in the name
 * - Functions that use LLM clients (OpenAI, Anthropic, etc.)
 * - Classes with system prompts or instructions
 */
class GenericParser : FrameworkParser {

    // Pattern for class definitions with "agent" in name
    private val agentClassPattern = Regex(
        """(?:class|interface)\s+(\w*[Aa]gent\w*)\s*(?:extends|implements)?""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for const/let agent definitions
    private val agentConstPattern = Regex(
        """(?:const|let|var)\s+(\w*[Aa]gent\w*)\s*[:=]""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for functions that might be agent-like (use LLM)
    private val llmUsagePattern = Regex(
        """(?:openai|anthropic|ChatOpenAI|Claude|ChatAnthropic|ChatCompletion|completion)\s*[.(]""",
        RegexOption.IGNORE_CASE
    )

    // Pattern for system prompt definitions
    private val systemPromptPattern = Regex(
        """(?:systemPrompt|system_prompt|systemMessage|instructions)\s*[:=]\s*["'`]([^"'`]+)["'`]""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for method definitions (potential tools)
    private val methodPattern = Regex(
        """(?:async\s+)?(?:function\s+)?(\w+)\s*\([^)]*\)\s*(?::\s*\w+)?\s*\{""",
        RegexOption.DOT_MATCHES_ALL
    )

    override fun parse(content: String, filePath: String): ParserResult {
        val agents = mutableListOf<CardData>()
        val relationships = mutableListOf<AgentRelationship>()

        // Only proceed if there's evidence of LLM usage
        if (!llmUsagePattern.containsMatchIn(content)) {
            return ParserResult(agents, relationships)
        }

        val projectName = extractProjectName(filePath)

        // Find agent-like classes
        agentClassPattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            val classBody = extractClassBody(content, match.range.last)
            val methods = extractMethods(classBody)
            val systemPrompt = extractSystemPrompt(classBody)

            agents.add(buildCard(
                name = className,
                tools = methods,
                systemPrompt = systemPrompt,
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))
        }

        // Find agent-like constants/variables
        agentConstPattern.findAll(content).forEach { match ->
            val varName = match.groupValues[1]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            // Skip if we already found this as a class
            if (agents.any { it.name == formatName(varName) }) {
                return@forEach
            }

            // Look for object definition
            val definition = extractDefinition(content, match.range.last)
            val systemPrompt = extractSystemPrompt(definition)

            agents.add(buildCard(
                name = varName,
                tools = emptyList(),
                systemPrompt = systemPrompt,
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))
        }

        return ParserResult(agents, relationships)
    }

    private fun extractClassBody(content: String, startIndex: Int): String {
        var braceCount = 0
        var started = false
        val sb = StringBuilder()

        for (i in startIndex until content.length) {
            val char = content[i]
            if (char == '{') {
                braceCount++
                started = true
            } else if (char == '}') {
                braceCount--
            }

            if (started) {
                sb.append(char)
                if (braceCount == 0) break
            }
        }

        return sb.toString()
    }

    private fun extractDefinition(content: String, startIndex: Int): String {
        // Find the next 500 chars or until end of statement
        val endIndex = minOf(startIndex + 500, content.length)
        return content.substring(startIndex, endIndex)
    }

    private fun extractMethods(classBody: String): List<Ability> {
        val methods = mutableListOf<Ability>()

        methodPattern.findAll(classBody).forEach { match ->
            val methodName = match.groupValues[1]
            // Skip common non-tool methods
            if (methodName !in listOf("constructor", "init", "initialize", "setup", "dispose", "destroy")) {
                methods.add(Ability(methodName, "Method", AbilityType.ACTIVATED))
            }
        }

        return methods.take(5)  // Limit to 5 abilities per card
    }

    private fun extractSystemPrompt(content: String): String? {
        val match = systemPromptPattern.find(content)
        return match?.groupValues?.get(1)?.take(120)
    }

    private fun extractProjectName(filePath: String): String {
        val file = File(filePath)
        var dir = file.parentFile
        while (dir != null) {
            if (File(dir, "package.json").exists() || File(dir, ".git").exists()) {
                return dir.name
            }
            dir = dir.parentFile
        }
        return file.parentFile?.name ?: "Unknown"
    }

    private fun buildCard(
        name: String,
        tools: List<Ability>,
        systemPrompt: String?,
        filePath: String,
        lineNumber: Int,
        projectName: String
    ): CardData {
        val colors = inferColors(tools, systemPrompt)
        val manaCost = buildManaCost(tools.size, colors)

        return CardData(
            name = formatName(name),
            manaCost = manaCost,
            colorIdentity = colors,
            typeLine = "Legendary Creature — AI Agent",
            artUrl = null,
            abilities = tools,
            flavorText = systemPrompt?.take(120),
            power = tools.size.coerceAtLeast(1),
            toughness = 1,
            setSymbol = projectName,
            collectorInfo = "Custom",
            frameType = if (colors.size > 1) FrameType.MULTICOLOR else FrameType.CREATURE,
            sourceFile = filePath,
            sourceLineNumber = lineNumber
        )
    }

    private fun formatName(name: String): String {
        return name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun inferColors(tools: List<Ability>, systemPrompt: String?): List<ManaColor> {
        val colors = mutableSetOf<ManaColor>()
        val text = (tools.map { it.name } + listOf(systemPrompt ?: "")).joinToString(" ").lowercase()

        if (text.contains("think") || text.contains("reason") || text.contains("plan") ||
            text.contains("analyze") || text.contains("logic")) {
            colors.add(ManaColor.BLUE)
        }

        if (text.contains("fast") || text.contains("quick") || text.contains("stream") ||
            text.contains("react") || text.contains("event")) {
            colors.add(ManaColor.RED)
        }

        if (text.contains("data") || text.contains("search") || text.contains("embed") ||
            text.contains("vector") || text.contains("retriev")) {
            colors.add(ManaColor.GREEN)
        }

        if (text.contains("api") || text.contains("http") || text.contains("fetch") ||
            text.contains("request") || text.contains("external")) {
            colors.add(ManaColor.BLACK)
        }

        if (text.contains("orchestrat") || text.contains("route") || text.contains("delegat") ||
            text.contains("coordinat") || text.contains("supervis")) {
            colors.add(ManaColor.WHITE)
        }

        if (colors.isEmpty()) {
            colors.add(ManaColor.BLUE)
        }

        return colors.toList()
    }

    private fun buildManaCost(toolCount: Int, colors: List<ManaColor>): String {
        val colorless = (toolCount - colors.size).coerceAtLeast(0)
        val sb = StringBuilder()

        if (colorless > 0) {
            sb.append("{$colorless}")
        }

        colors.forEach { color ->
            sb.append(when (color) {
                ManaColor.WHITE -> "{W}"
                ManaColor.BLUE -> "{U}"
                ManaColor.BLACK -> "{B}"
                ManaColor.RED -> "{R}"
                ManaColor.GREEN -> "{G}"
                ManaColor.COLORLESS -> "{C}"
            })
        }

        return sb.toString().ifEmpty { "{1}" }
    }
}
