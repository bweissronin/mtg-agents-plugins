package com.mtgagents.parser

import com.mtgagents.model.*
import java.io.File

/**
 * Parser for OpenAI Agents SDK definitions.
 *
 * Detects:
 * - Agent() constructor calls with name, instructions, tools
 * - handoffs arrays for agent-to-agent relationships
 * - function_tool() definitions
 */
class OpenAiAgentsParser : FrameworkParser {

    // Pattern for Agent instantiation
    private val agentPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*new\s+Agent\s*\(\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Alternative pattern for Agent() without 'new'
    private val agentFunctionPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*Agent\s*\(\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for function tools
    private val functionToolPattern = Regex(
        """function_tool\s*\(\s*["'](\w+)["']""",
        RegexOption.DOT_MATCHES_ALL
    )

    override fun parse(content: String, filePath: String): ParserResult {
        val agents = mutableListOf<CardData>()
        val relationships = mutableListOf<AgentRelationship>()

        // Check if this file uses OpenAI Agents SDK
        if (!content.contains("openai") && !content.contains("Agent(") && !content.contains("new Agent")) {
            return ParserResult(agents, relationships)
        }

        val projectName = extractProjectName(filePath)

        // Parse Agent instantiations
        val allMatches = agentPattern.findAll(content).toList() + agentFunctionPattern.findAll(content).toList()

        allMatches.forEach { match ->
            val varName = match.groupValues[1]
            val config = match.groupValues[2]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            val name = extractStringProperty(config, "name") ?: varName
            val instructions = extractStringProperty(config, "instructions")
            val model = extractStringProperty(config, "model") ?: "gpt-4"

            val tools = extractTools(config)
            val handoffs = extractHandoffs(config)

            agents.add(buildCard(
                name = name,
                tools = tools,
                systemPrompt = instructions,
                model = model,
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))

            // Add handoff relationships
            handoffs.forEach { targetAgent ->
                relationships.add(AgentRelationship(
                    sourceAgent = name,
                    targetAgent = targetAgent,
                    relationshipType = RelationshipType.HANDOFF
                ))
            }
        }

        return ParserResult(agents, relationships)
    }

    private fun extractTools(config: String): List<Ability> {
        val tools = mutableListOf<Ability>()

        // Extract tools array
        val toolsArrayMatch = Regex("""tools\s*:\s*\[([^\]]+)\]""").find(config)
        if (toolsArrayMatch != null) {
            val toolsContent = toolsArrayMatch.groupValues[1]

            // Look for function_tool references
            functionToolPattern.findAll(toolsContent).forEach {
                tools.add(Ability(it.groupValues[1], "Function tool", AbilityType.ACTIVATED))
            }

            // Look for direct tool references
            Regex("""\b(\w+Tool)\b""").findAll(toolsContent).forEach {
                val toolName = it.groupValues[1]
                if (!tools.any { t -> t.name == toolName }) {
                    tools.add(Ability(toolName, "Tool", AbilityType.ACTIVATED))
                }
            }
        }

        return tools
    }

    private fun extractHandoffs(config: String): List<String> {
        val handoffs = mutableListOf<String>()

        // Extract handoffs array
        val handoffsMatch = Regex("""handoffs\s*:\s*\[([^\]]+)\]""").find(config)
        if (handoffsMatch != null) {
            val handoffsContent = handoffsMatch.groupValues[1]
            Regex("""\b(\w+)\b""").findAll(handoffsContent).forEach {
                val name = it.groupValues[1]
                if (name !in listOf("new", "const", "let", "var")) {
                    handoffs.add(name)
                }
            }
        }

        return handoffs
    }

    private fun extractStringProperty(config: String, property: String): String? {
        // Handle multiline template literals
        val templatePattern = Regex("""$property\s*:\s*`([^`]+)`""", RegexOption.DOT_MATCHES_ALL)
        val templateMatch = templatePattern.find(config)
        if (templateMatch != null) {
            return templateMatch.groupValues[1].trim()
        }

        // Handle single/double quoted strings
        val pattern = Regex("""$property\s*:\s*["']([^"']+)["']""")
        return pattern.find(config)?.groupValues?.get(1)
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
        model: String,
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
            typeLine = "Legendary Creature — AI Agent OpenAI",
            artUrl = null,
            abilities = tools,
            flavorText = systemPrompt?.take(120),
            power = tools.size.coerceAtLeast(1),
            toughness = 1,
            setSymbol = projectName,
            collectorInfo = model,
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

        if (text.contains("data") || text.contains("rag") || text.contains("embed") ||
            text.contains("vector") || text.contains("retriev") || text.contains("search")) {
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
