package com.mtgagents.parser

import com.mtgagents.model.*
import java.io.File

/**
 * Parser for CrewAI agent definitions.
 *
 * Detects:
 * - Agent() with role, goal, backstory
 * - @agent decorator
 * - @task decorator
 * - Crew() with agents array
 */
class CrewAiParser : FrameworkParser {

    // Pattern for Agent instantiation
    private val agentPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*new\s+Agent\s*\(\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for @agent decorator
    private val agentDecoratorPattern = Regex(
        """@agent\s*(?:\([^)]*\))?\s*(?:async\s+)?(\w+)\s*\(""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for @task decorator
    private val taskDecoratorPattern = Regex(
        """@task\s*(?:\([^)]*\))?\s*(?:async\s+)?(\w+)\s*\(""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern for Crew instantiation
    private val crewPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*new\s+Crew\s*\(\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    override fun parse(content: String, filePath: String): ParserResult {
        val agents = mutableListOf<CardData>()
        val relationships = mutableListOf<AgentRelationship>()

        // Check if this file uses CrewAI
        if (!content.contains("crewai") && !content.contains("@agent") && !content.contains("Crew(")) {
            return ParserResult(agents, relationships)
        }

        val projectName = extractProjectName(filePath)

        // Parse Agent instantiations
        agentPattern.findAll(content).forEach { match ->
            val varName = match.groupValues[1]
            val config = match.groupValues[2]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            val role = extractStringProperty(config, "role") ?: varName
            val goal = extractStringProperty(config, "goal")
            val backstory = extractStringProperty(config, "backstory")

            val tools = extractTools(config)

            // Combine goal and backstory for flavor text
            val flavorText = listOfNotNull(goal, backstory).joinToString(" — ").take(120)

            agents.add(buildCard(
                name = role,
                tools = tools,
                systemPrompt = flavorText,
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))
        }

        // Parse @agent decorated functions
        agentDecoratorPattern.findAll(content).forEach { match ->
            val functionName = match.groupValues[1]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            // Try to extract the function body for more info
            val functionContent = extractFunctionBody(content, match.range.last)

            agents.add(buildCard(
                name = functionName,
                tools = emptyList(),
                systemPrompt = "CrewAI agent function",
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))
        }

        // Parse Crew to find relationships
        crewPattern.findAll(content).forEach { match ->
            val crewName = match.groupValues[1]
            val config = match.groupValues[2]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            // Extract agents array
            val agentsInCrew = extractAgentsArray(config)

            // Create a "commander" card for the crew
            agents.add(CardData(
                name = formatName(crewName),
                manaCost = "{${agentsInCrew.size}}{W}{W}",
                colorIdentity = listOf(ManaColor.WHITE),
                typeLine = "Legendary Creature — AI Crew Commander",
                artUrl = null,
                abilities = agentsInCrew.map { Ability(it, "Crew member", AbilityType.STATIC) },
                flavorText = "Orchestrates ${agentsInCrew.size} agents",
                power = agentsInCrew.size,
                toughness = agentsInCrew.size,
                setSymbol = projectName,
                collectorInfo = "CrewAI",
                frameType = FrameType.MULTICOLOR,
                sourceFile = filePath,
                sourceLineNumber = lineNumber
            ))

            // Add relationships from crew to each agent
            agentsInCrew.forEach { agentName ->
                relationships.add(AgentRelationship(
                    sourceAgent = crewName,
                    targetAgent = agentName,
                    relationshipType = RelationshipType.SUB_AGENT
                ))
            }
        }

        return ParserResult(agents, relationships)
    }

    private fun extractTools(config: String): List<Ability> {
        val tools = mutableListOf<Ability>()

        val toolsArrayMatch = Regex("""tools\s*:\s*\[([^\]]+)\]""").find(config)
        if (toolsArrayMatch != null) {
            val toolsContent = toolsArrayMatch.groupValues[1]
            Regex("""\b(\w+)\b""").findAll(toolsContent).forEach {
                val toolName = it.groupValues[1]
                if (toolName !in listOf("new", "const", "let", "var", "function")) {
                    tools.add(Ability(toolName, "Tool", AbilityType.ACTIVATED))
                }
            }
        }

        return tools
    }

    private fun extractAgentsArray(config: String): List<String> {
        val agents = mutableListOf<String>()

        val agentsMatch = Regex("""agents\s*:\s*\[([^\]]+)\]""").find(config)
        if (agentsMatch != null) {
            val agentsContent = agentsMatch.groupValues[1]
            Regex("""\b(\w+)\b""").findAll(agentsContent).forEach {
                val name = it.groupValues[1]
                if (name !in listOf("new", "const", "let", "var")) {
                    agents.add(name)
                }
            }
        }

        return agents
    }

    private fun extractFunctionBody(content: String, startIndex: Int): String {
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

    private fun extractStringProperty(config: String, property: String): String? {
        val templatePattern = Regex("""$property\s*:\s*`([^`]+)`""", RegexOption.DOT_MATCHES_ALL)
        val templateMatch = templatePattern.find(config)
        if (templateMatch != null) {
            return templateMatch.groupValues[1].trim()
        }

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
            typeLine = "Legendary Creature — AI Agent CrewAI",
            artUrl = null,
            abilities = tools,
            flavorText = systemPrompt?.take(120),
            power = tools.size.coerceAtLeast(1),
            toughness = 1,
            setSymbol = projectName,
            collectorInfo = "CrewAI",
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
            text.contains("analyze") || text.contains("research")) {
            colors.add(ManaColor.BLUE)
        }

        if (text.contains("write") || text.contains("create") || text.contains("generate") ||
            text.contains("quick") || text.contains("fast")) {
            colors.add(ManaColor.RED)
        }

        if (text.contains("data") || text.contains("search") || text.contains("find") ||
            text.contains("collect") || text.contains("gather")) {
            colors.add(ManaColor.GREEN)
        }

        if (text.contains("api") || text.contains("web") || text.contains("external") ||
            text.contains("scrape") || text.contains("fetch")) {
            colors.add(ManaColor.BLACK)
        }

        if (text.contains("lead") || text.contains("manage") || text.contains("coordinat") ||
            text.contains("supervis") || text.contains("direct")) {
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
