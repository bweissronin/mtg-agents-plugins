package com.mtgagents.parser

import com.mtgagents.model.*
import java.io.File

/**
 * Parser for Markdown-based agent definitions.
 *
 * Expects format:
 * ---
 * name: agent-name
 * description: Agent description
 * model: sonnet
 * color: blue
 * ---
 *
 * System prompt / instructions body...
 */
class MarkdownParser : FrameworkParser {

    // Pattern to match YAML frontmatter
    private val frontmatterPattern = Regex(
        """^---\s*\n(.*?)\n---\s*\n(.*)$""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    // Patterns for frontmatter fields
    private val namePattern = Regex("""^name:\s*(.+)$""", RegexOption.MULTILINE)
    private val descriptionPattern = Regex("""^description:\s*(.+)$""", RegexOption.MULTILINE)
    private val modelPattern = Regex("""^model:\s*(.+)$""", RegexOption.MULTILINE)
    private val colorPattern = Regex("""^color:\s*(.+)$""", RegexOption.MULTILINE)

    // Card customization patterns
    private val displayNamePattern = Regex("""^display_name:\s*(.+)$""", RegexOption.MULTILINE)
    private val artPromptPattern = Regex("""^art_prompt:\s*(.+)$""", RegexOption.MULTILINE)
    private val creatureTypePattern = Regex("""^creature_type:\s*(.+)$""", RegexOption.MULTILINE)
    private val artStylePattern = Regex("""^art_style:\s*(.+)$""", RegexOption.MULTILINE)
    private val manaCostPattern = Regex("""^mana_cost:\s*(.+)$""", RegexOption.MULTILINE)
    private val powerPattern = Regex("""^power:\s*(\d+)$""", RegexOption.MULTILINE)
    private val toughnessPattern = Regex("""^toughness:\s*(\d+)$""", RegexOption.MULTILINE)
    private val flavorTextPattern = Regex("""^flavor_text:\s*(.+)$""", RegexOption.MULTILINE)
    private val typeLinePattern = Regex("""^type_line:\s*(.+)$""", RegexOption.MULTILINE)
    private val cardStylePattern = Regex("""^card_style:\s*(.+)$""", RegexOption.MULTILINE)

    override fun parse(content: String, filePath: String): ParserResult {
        val agents = mutableListOf<CardData>()
        val relationships = mutableListOf<AgentRelationship>()

        // Check if this is a markdown file with frontmatter
        val frontmatterMatch = frontmatterPattern.find(content) ?: return ParserResult(agents, relationships)

        val frontmatter = frontmatterMatch.groupValues[1]
        val body = frontmatterMatch.groupValues[2].trim()

        // Extract frontmatter fields
        val name = namePattern.find(frontmatter)?.groupValues?.get(1)?.trim() ?: return ParserResult(agents, relationships)
        val description = descriptionPattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val model = modelPattern.find(frontmatter)?.groupValues?.get(1)?.trim() ?: "unknown"
        val colorStr = colorPattern.find(frontmatter)?.groupValues?.get(1)?.trim()

        // Extract card customization fields
        val displayName = displayNamePattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val artPrompt = artPromptPattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val creatureType = creatureTypePattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val artStyle = artStylePattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val customManaCost = manaCostPattern.find(frontmatter)?.groupValues?.get(1)?.trim()
            ?.removeSurrounding("\"")?.removeSurrounding("'")
        val customPower = powerPattern.find(frontmatter)?.groupValues?.get(1)?.toIntOrNull()
        val customToughness = toughnessPattern.find(frontmatter)?.groupValues?.get(1)?.toIntOrNull()
        val customFlavorText = flavorTextPattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val customTypeLine = typeLinePattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val cardStyleStr = cardStylePattern.find(frontmatter)?.groupValues?.get(1)?.trim()
        val cardStyle = parseCardStyle(cardStyleStr)

        val projectName = extractProjectName(filePath)

        // Parse color from frontmatter
        val colors = parseColor(colorStr)

        // Extract tools/capabilities from the body (look for headers or lists)
        val tools = extractToolsFromBody(body)

        // Get first meaningful paragraph as flavor text (skip headers)
        val flavorText = customFlavorText ?: extractFlavorText(body, description)

        // Use custom mana cost or calculate from tools
        val manaCost = customManaCost ?: buildManaCost(tools.size, colors)

        // Use custom type line or generate default
        val typeLine = customTypeLine ?: "Legendary Creature — AI Agent ${formatModel(model)}"

        agents.add(CardData(
            name = displayName ?: formatName(name),
            manaCost = manaCost,
            colorIdentity = colors,
            typeLine = typeLine,
            artUrl = null,
            abilities = tools,
            flavorText = flavorText?.take(120),
            power = customPower ?: tools.size.coerceAtLeast(1),
            toughness = customToughness ?: 1,
            setSymbol = projectName,
            collectorInfo = formatModel(model),
            frameType = if (colors.size > 1) FrameType.MULTICOLOR else FrameType.CREATURE,
            sourceFile = filePath,
            sourceLineNumber = 1,
            artPrompt = artPrompt,
            creatureType = creatureType,
            artStyle = artStyle,
            cardStyle = cardStyle
        ))

        // Look for agent references in the body (for relationships)
        val agentReferences = extractAgentReferences(body)
        agentReferences.forEach { refName ->
            relationships.add(AgentRelationship(
                sourceAgent = formatName(name),
                targetAgent = refName,
                relationshipType = RelationshipType.REFERENCE
            ))
        }

        return ParserResult(agents, relationships)
    }

    private fun parseCardStyle(styleStr: String?): CardStyle {
        if (styleStr.isNullOrBlank()) return CardStyle.STANDARD
        return when (styleStr.lowercase().trim()) {
            "borderless", "full-art", "fullart", "alt", "alt-art" -> CardStyle.BORDERLESS
            "extended", "extended-art" -> CardStyle.EXTENDED
            else -> CardStyle.STANDARD
        }
    }

    private fun parseColor(colorStr: String?): List<ManaColor> {
        if (colorStr.isNullOrBlank()) return listOf(ManaColor.BLUE)

        val colors = mutableListOf<ManaColor>()
        val colorLower = colorStr.lowercase()

        // Support multiple colors (comma or space separated)
        val colorNames = colorLower.split(Regex("[,\\s]+"))

        colorNames.forEach { c ->
            when (c.trim()) {
                "white", "w" -> colors.add(ManaColor.WHITE)
                "blue", "u" -> colors.add(ManaColor.BLUE)
                "black", "b" -> colors.add(ManaColor.BLACK)
                "red", "r" -> colors.add(ManaColor.RED)
                "green", "g" -> colors.add(ManaColor.GREEN)
                "colorless", "c" -> colors.add(ManaColor.COLORLESS)
            }
        }

        return if (colors.isEmpty()) listOf(ManaColor.BLUE) else colors.distinct()
    }

    private fun extractToolsFromBody(body: String): List<Ability> {
        val tools = mutableListOf<Ability>()
        val lines = body.lines()

        // Look for ## headers as major capabilities
        val headerPattern = Regex("""^##\s+(.+)$""", RegexOption.MULTILINE)
        headerPattern.findAll(body).forEach { match ->
            val header = match.groupValues[1].trim()
            // Skip common non-tool headers
            if (header !in listOf("Core Responsibilities", "Operational Methodology",
                                  "Edge Cases & Problem Solving", "Output Standards",
                                  "Quality Assurance Checklist")) {
                // Check if it looks like a capability section
                if (header.contains("&") || header.length < 40) {
                    val description = extractDescriptionAfterHeader(body, match.range.last, 2)
                    tools.add(Ability(
                        name = header,
                        description = description,
                        abilityType = AbilityType.ACTIVATED
                    ))
                }
            }
        }

        // Also look for ### headers as specific tools
        val subHeaderPattern = Regex("""^###\s+\d*\.?\s*(.+)$""", RegexOption.MULTILINE)
        subHeaderPattern.findAll(body).take(5).forEach { match ->
            val header = match.groupValues[1].trim()
            if (header.length < 50) {
                val description = extractDescriptionAfterHeader(body, match.range.last, 3)
                tools.add(Ability(
                    name = header,
                    description = description,
                    abilityType = AbilityType.ACTIVATED
                ))
            }
        }

        // If no headers found, look for bold items or list items describing capabilities
        if (tools.isEmpty()) {
            val boldPattern = Regex("""\*\*([^*]+)\*\*""")
            boldPattern.findAll(body).take(5).forEach { match ->
                val item = match.groupValues[1].trim()
                if (item.length < 30 && !item.contains(":")) {
                    val description = extractDescriptionAfterMatch(body, match.range.last)
                    tools.add(Ability(
                        name = item,
                        description = description,
                        abilityType = AbilityType.STATIC
                    ))
                }
            }
        }

        return tools.take(5).distinctBy { it.name }
    }

    /**
     * Extract the first meaningful sentence/phrase after a header.
     */
    private fun extractDescriptionAfterHeader(body: String, startPos: Int, headerLevel: Int): String {
        val remaining = body.substring(startPos.coerceAtMost(body.length))
        val lines = remaining.lines().drop(1) // Skip the header line itself

        for (line in lines) {
            val trimmed = line.trim()
            // Stop at next header
            if (trimmed.startsWith("#")) break
            // Skip empty lines
            if (trimmed.isEmpty()) continue
            // Skip list markers, get content
            val content = trimmed
                .removePrefix("-").removePrefix("*").removePrefix(">")
                .trim()
            if (content.isNotEmpty() && content.length > 5) {
                // Get first sentence, max 80 chars
                val firstSentence = content.split(Regex("""[.!?]""")).firstOrNull()?.trim() ?: content
                return firstSentence.take(80)
            }
        }
        return ""
    }

    /**
     * Extract description after a bold match (look for colon or next text).
     */
    private fun extractDescriptionAfterMatch(body: String, startPos: Int): String {
        val remaining = body.substring(startPos.coerceAtMost(body.length))
        // Look for text after colon or dash
        val colonMatch = Regex("""^[*]*\s*[:—-]\s*([^*\n]+)""").find(remaining)
        if (colonMatch != null) {
            return colonMatch.groupValues[1].trim().take(80)
        }
        // Otherwise get next non-empty content
        val nextContent = Regex("""^\s*([^*#\n][^\n]{5,})""").find(remaining)
        if (nextContent != null) {
            return nextContent.groupValues[1].trim().take(80)
        }
        return ""
    }

    private fun extractFlavorText(body: String, description: String?): String? {
        // Try to get the first paragraph after "You are" or first sentence
        val youArePattern = Regex("""You are ([^.]+\.)""")
        val youAreMatch = youArePattern.find(body)
        if (youAreMatch != null) {
            return "You are ${youAreMatch.groupValues[1]}"
        }

        // Otherwise use description if available (clean up escape sequences)
        if (!description.isNullOrBlank()) {
            val cleanDesc = description
                .replace("\\n", " ")
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            // Get first sentence
            val firstSentence = cleanDesc.split(Regex("""[.!?]""")).firstOrNull()?.trim()
            if (!firstSentence.isNullOrBlank() && firstSentence.length > 10) {
                return firstSentence
            }
        }

        // Fall back to first non-header line
        val lines = body.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("-")) {
                return trimmed.take(120)
            }
        }

        return null
    }

    private fun extractAgentReferences(body: String): List<String> {
        val references = mutableListOf<String>()

        // Look for agent references like "the ui-owner agent" or "use the X agent"
        val agentRefPattern = Regex("""(?:the|use|invoke|call)\s+(\w+[-_]?\w*)\s+agent""", RegexOption.IGNORE_CASE)
        agentRefPattern.findAll(body).forEach { match ->
            val agentName = match.groupValues[1]
            references.add(formatName(agentName))
        }

        return references.distinct()
    }

    private fun extractProjectName(filePath: String): String {
        val file = File(filePath)
        var dir = file.parentFile
        while (dir != null) {
            if (File(dir, "package.json").exists() ||
                File(dir, ".git").exists() ||
                File(dir, "CLAUDE.md").exists()) {
                return dir.name
            }
            dir = dir.parentFile
        }
        return file.parentFile?.name ?: "Unknown"
    }

    private fun formatName(name: String): String {
        return name
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun formatModel(model: String): String {
        return when (model.lowercase()) {
            "sonnet" -> "Claude Sonnet"
            "opus" -> "Claude Opus"
            "haiku" -> "Claude Haiku"
            "gpt-4", "gpt4" -> "GPT-4"
            "gpt-4o" -> "GPT-4o"
            "gpt-3.5", "gpt35" -> "GPT-3.5"
            else -> model
        }
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
