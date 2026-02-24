package com.mtgagents

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.mtgagents.art.ArtGenerator
import com.mtgagents.model.*
import com.mtgagents.parser.AgentParser
import com.mtgagents.parser.ParserResult
import java.io.File

@Service(Service.Level.PROJECT)
class MtgAgentService(private val project: Project) {

    private val gson = Gson()
    private val artGenerator = ArtGenerator()
    private val agentParser = AgentParser()

    // Cache of parsed agents
    private val agentCache = mutableMapOf<String, CardData>()

    /**
     * Parse an agent from the given file and line number.
     */
    fun parseAgent(filePath: String, lineNumber: Int): CardData? {
        val file = File(filePath)
        if (!file.exists()) return null

        val content = file.readText()
        val result = agentParser.parseFile(content, filePath)

        // Find the agent at or near the given line
        return result.agents.minByOrNull {
            kotlin.math.abs(it.sourceLineNumber - lineNumber)
        }
    }

    /**
     * Scan the entire project for agents.
     */
    fun scanProject(): BattlefieldData {
        val agents = mutableListOf<CardData>()
        val relationships = mutableListOf<AgentRelationship>()

        val basePath = project.basePath ?: return BattlefieldData(agents, relationships, "Unknown")

        // Find all TypeScript and Markdown files
        File(basePath).walkTopDown()
            .filter { it.isFile && (it.extension == "ts" || it.extension == "tsx" || it.extension == "md") }
            .filter { !it.path.contains("node_modules") }
            .filter { !it.path.contains(".git") }
            .forEach { file ->
                try {
                    val content = file.readText()
                    val result = agentParser.parseFile(content, file.absolutePath)

                    // Use cached cards (which have art URLs) if available
                    val agentsWithCache = result.agents.map { agent ->
                        agentCache[agent.name] ?: agent
                    }
                    agents.addAll(agentsWithCache)
                    relationships.addAll(result.relationships)
                } catch (e: Exception) {
                    // Log but continue
                    println("Error parsing ${file.path}: ${e.message}")
                }
            }

        val projectName = File(basePath).name

        return BattlefieldData(agents, relationships, projectName)
    }

    /**
     * Generate art for a card if not already cached.
     */
    suspend fun generateArt(card: CardData): String? {
        val cacheKey = card.name.lowercase().replace(Regex("[^a-z0-9]"), "_")

        // Check cache first
        val cachedArt = artGenerator.getCachedArt(cacheKey)
        if (cachedArt != null) {
            return cachedArt
        }

        // Generate new art
        return artGenerator.generateArt(card)?.also { artPath ->
            artGenerator.cacheArt(cacheKey, artPath)
        }
    }

    /**
     * Convert card data to JSON for the webview.
     */
    fun cardToJson(card: CardData): String = gson.toJson(card)

    /**
     * Convert battlefield data to JSON for the webview.
     */
    fun battlefieldToJson(battlefield: BattlefieldData): String = gson.toJson(battlefield)

    /**
     * Get cached card or null.
     */
    fun getCachedCard(key: String): CardData? = agentCache[key]

    /**
     * Cache a card.
     */
    fun cacheCard(key: String, card: CardData) {
        agentCache[key] = card
    }

    /**
     * Clear all caches.
     */
    fun clearCaches() {
        agentCache.clear()
        artGenerator.clearCache()
    }
}
