package com.mtgagents.parser

import com.mtgagents.model.*
import java.io.File

/**
 * Parser for LangChain/LangGraph agent definitions.
 *
 * Detects:
 * - AgentExecutor instances
 * - @tool decorators
 * - StateGraph.addNode() calls
 * - createReactAgent() calls
 */
class LangChainParser : FrameworkParser {

    // Patterns for LangChain detection
    private val agentExecutorPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*(?:new\s+)?AgentExecutor\s*\(\s*\{([^}]+)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val createAgentPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*(?:await\s+)?create(?:React|OpenAI|Tool)?Agent\s*\(\s*\{([^}]+)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val stateGraphPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*new\s+StateGraph""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val addNodePattern = Regex(
        """\.addNode\s*\(\s*["'](\w+)["']""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val toolDecoratorPattern = Regex(
        """@tool\s*(?:\([^)]*\))?\s*(?:async\s+)?(?:function\s+)?(\w+)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val toolFunctionPattern = Regex(
        """(?:const|let|var)\s+(\w+)\s*=\s*(?:new\s+)?(?:DynamicTool|Tool|StructuredTool)\s*\(\s*\{([^}]+)\}""",
        RegexOption.DOT_MATCHES_ALL
    )

    override fun parse(content: String, filePath: String): ParserResult {
        val agents = mutableListOf<CardData>()
        val relationships = mutableListOf<AgentRelationship>()

        // Check if this file uses LangChain
        if (!content.contains("langchain") && !content.contains("@langchain")) {
            return ParserResult(agents, relationships)
        }

        val projectName = extractProjectName(filePath)

        // Parse AgentExecutor instances
        agentExecutorPattern.findAll(content).forEach { match ->
            val name = match.groupValues[1]
            val config = match.groupValues[2]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            val tools = extractTools(config)
            val systemPrompt = extractStringProperty(config, "systemMessage") ?:
                               extractStringProperty(config, "prefix")

            agents.add(buildCard(
                name = name,
                tools = tools,
                systemPrompt = systemPrompt,
                framework = "LangChain",
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))
        }

        // Parse createAgent calls
        createAgentPattern.findAll(content).forEach { match ->
            val name = match.groupValues[1]
            val config = match.groupValues[2]
            val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            val tools = extractTools(config)
            val systemPrompt = extractStringProperty(config, "systemMessage")

            agents.add(buildCard(
                name = name,
                tools = tools,
                systemPrompt = systemPrompt,
                framework = "LangChain",
                filePath = filePath,
                lineNumber = lineNumber,
                projectName = projectName
            ))
        }

        // Parse StateGraph nodes (LangGraph)
        stateGraphPattern.findAll(content).forEach { match ->
            val graphName = match.groupValues[1]
            val graphLineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1

            // Find all addNode calls for this graph
            val graphContent = content.substring(match.range.first)
            val nodeNames = mutableListOf<String>()

            addNodePattern.findAll(graphContent).forEach { nodeMatch ->
                nodeNames.add(nodeMatch.groupValues[1])
            }

            if (nodeNames.isNotEmpty()) {
                agents.add(buildCard(
                    name = graphName,
                    tools = nodeNames.map { Ability(it, "Graph node", AbilityType.ACTIVATED) },
                    systemPrompt = "LangGraph workflow with ${nodeNames.size} nodes",
                    framework = "LangGraph",
                    filePath = filePath,
                    lineNumber = graphLineNumber,
                    projectName = projectName
                ))

                // Add relationships between nodes (sequential for now)
                for (i in 0 until nodeNames.size - 1) {
                    relationships.add(AgentRelationship(
                        sourceAgent = nodeNames[i],
                        targetAgent = nodeNames[i + 1],
                        relationshipType = RelationshipType.HANDOFF
                    ))
                }
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
            Regex("""\b(\w+)\b""").findAll(toolsContent).forEach {
                val toolName = it.groupValues[1]
                if (toolName !in listOf("new", "const", "let", "var", "function")) {
                    tools.add(Ability(toolName, "Tool", AbilityType.ACTIVATED))
                }
            }
        }

        return tools
    }

    private fun extractStringProperty(config: String, property: String): String? {
        val pattern = Regex("""$property\s*:\s*["'`]([^"'`]+)["'`]""")
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
        framework: String,
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
            typeLine = "Legendary Creature — AI Agent $framework",
            artUrl = null,
            abilities = tools,
            flavorText = systemPrompt?.take(120),
            power = tools.size.coerceAtLeast(1),
            toughness = 1,
            setSymbol = projectName,
            collectorInfo = framework,
            frameType = if (colors.size > 1) FrameType.MULTICOLOR else FrameType.CREATURE,
            sourceFile = filePath,
            sourceLineNumber = lineNumber
        )
    }

    private fun formatName(name: String): String {
        // Convert camelCase/snake_case to Title Case
        return name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun inferColors(tools: List<Ability>, systemPrompt: String?): List<ManaColor> {
        val colors = mutableSetOf<ManaColor>()
        val text = (tools.map { it.name } + listOf(systemPrompt ?: "")).joinToString(" ").lowercase()

        // Blue = Reasoning, planning
        if (text.contains("think") || text.contains("reason") || text.contains("plan") ||
            text.contains("analyze") || text.contains("logic")) {
            colors.add(ManaColor.BLUE)
        }

        // Red = Fast, reactive
        if (text.contains("fast") || text.contains("quick") || text.contains("stream") ||
            text.contains("react") || text.contains("event")) {
            colors.add(ManaColor.RED)
        }

        // Green = Data, RAG
        if (text.contains("data") || text.contains("rag") || text.contains("embed") ||
            text.contains("vector") || text.contains("retriev") || text.contains("search")) {
            colors.add(ManaColor.GREEN)
        }

        // Black = External APIs
        if (text.contains("api") || text.contains("http") || text.contains("fetch") ||
            text.contains("request") || text.contains("external")) {
            colors.add(ManaColor.BLACK)
        }

        // White = Orchestration
        if (text.contains("orchestrat") || text.contains("route") || text.contains("delegat") ||
            text.contains("coordinat") || text.contains("supervis")) {
            colors.add(ManaColor.WHITE)
        }

        // Default to blue if no colors detected
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
