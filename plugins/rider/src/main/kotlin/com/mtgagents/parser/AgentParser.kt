package com.mtgagents.parser

import com.mtgagents.model.*
import com.mtgagents.settings.MtgSettings

/**
 * Result of parsing a file for agent definitions.
 */
data class ParserResult(
    val agents: List<CardData>,
    val relationships: List<AgentRelationship>
)

/**
 * Main parser that delegates to framework-specific parsers.
 */
class AgentParser {

    private val langChainParser = LangChainParser()
    private val openAiParser = OpenAiAgentsParser()
    private val crewAiParser = CrewAiParser()
    private val genericParser = GenericParser()
    private val markdownParser = MarkdownParser()

    /**
     * Parse a file for agent definitions.
     * Supports TypeScript (.ts, .tsx) and Markdown (.md) files.
     */
    fun parseFile(content: String, filePath: String): ParserResult {
        val allAgents = mutableListOf<CardData>()
        val allRelationships = mutableListOf<AgentRelationship>()

        // Check if this is a Markdown file
        if (filePath.endsWith(".md")) {
            val markdownResult = markdownParser.parse(content, filePath)
            return markdownResult
        }

        // For TypeScript files, try each parser
        val langChainResult = langChainParser.parse(content, filePath)
        val openAiResult = openAiParser.parse(content, filePath)
        val crewAiResult = crewAiParser.parse(content, filePath)

        allAgents.addAll(langChainResult.agents)
        allAgents.addAll(openAiResult.agents)
        allAgents.addAll(crewAiResult.agents)
        allRelationships.addAll(langChainResult.relationships)
        allRelationships.addAll(openAiResult.relationships)
        allRelationships.addAll(crewAiResult.relationships)

        // If no framework-specific agents found, try generic parser
        if (allAgents.isEmpty()) {
            val settings = MtgSettings.getInstance()
            if (!settings.parseDecoratorsOnly) {
                val genericResult = genericParser.parse(content, filePath)
                allAgents.addAll(genericResult.agents)
                allRelationships.addAll(genericResult.relationships)
            }
        }

        // Detect cross-agent relationships from call patterns
        if (MtgSettings.getInstance().detectAllAgentCalls) {
            val additionalRelationships = detectAgentCalls(content, allAgents)
            allRelationships.addAll(additionalRelationships)
        }

        return ParserResult(allAgents, allRelationships.distinctBy { "${it.sourceAgent}->${it.targetAgent}" })
    }

    /**
     * Detect agent-to-agent calls by scanning for invocation patterns.
     */
    private fun detectAgentCalls(content: String, agents: List<CardData>): List<AgentRelationship> {
        val relationships = mutableListOf<AgentRelationship>()
        val agentNames = agents.map { it.name }.toSet()

        // Pattern: agent.invoke(), agent.run(), agent.execute(), await agent()
        val callPatterns = listOf(
            Regex("""(\w+)\s*\.\s*(?:invoke|run|execute|call|process)\s*\("""),
            Regex("""await\s+(\w+)\s*\("""),
            Regex("""(\w+)\s*\.\s*stream\s*\(""")
        )

        for (pattern in callPatterns) {
            pattern.findAll(content).forEach { match ->
                val calledName = match.groupValues[1]
                if (calledName in agentNames) {
                    // Find which agent contains this call
                    val callerLine = content.substring(0, match.range.first).count { it == '\n' } + 1
                    val caller = agents.minByOrNull { kotlin.math.abs(it.sourceLineNumber - callerLine) }
                    if (caller != null && caller.name != calledName) {
                        relationships.add(
                            AgentRelationship(
                                sourceAgent = caller.name,
                                targetAgent = calledName,
                                relationshipType = RelationshipType.REFERENCE
                            )
                        )
                    }
                }
            }
        }

        return relationships
    }
}

/**
 * Base interface for framework-specific parsers.
 */
interface FrameworkParser {
    fun parse(content: String, filePath: String): ParserResult
}
