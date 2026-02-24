package com.mtgagents.model

/**
 * Represents a single MTG card generated from an AI agent definition.
 */
data class CardData(
    val name: String,
    val manaCost: String,                    // e.g., "{2}{U}{U}" for 2 colorless + 2 blue
    val colorIdentity: List<ManaColor>,
    val typeLine: String,                     // e.g., "Legendary Creature — AI Agent LangChain"
    val artUrl: String?,                      // Local file path or base64 data URL
    val abilities: List<Ability>,
    val flavorText: String?,                  // First 120 chars of system prompt
    val power: Int,                           // Number of tools
    val toughness: Int,                       // Max retries or fallback depth
    val setSymbol: String,                    // Project/repository name
    val collectorInfo: String,                // LLM model + framework version
    val frameType: FrameType = FrameType.CREATURE,
    val sourceFile: String,                   // Path to source file
    val sourceLineNumber: Int,                // Line number in source

    // Art generation hints (from metadata)
    val artPrompt: String? = null,           // Custom prompt for art generation
    val creatureType: String? = null,        // e.g., "arcane wizard", "mechanical construct"
    val artStyle: String? = null,            // e.g., "dark fantasy", "ethereal", "cyberpunk"
    val cardStyle: CardStyle = CardStyle.STANDARD  // Card layout style
)

/**
 * Represents a single ability (tool/function) on the card.
 */
data class Ability(
    val name: String,
    val description: String,
    val abilityType: AbilityType = AbilityType.ACTIVATED
)

enum class AbilityType {
    ACTIVATED,   // Tap abilities, costs
    TRIGGERED,   // "When...", "Whenever..."
    STATIC,      // Passive effects
    KEYWORD      // Flying, Haste, etc.
}

enum class ManaColor {
    WHITE,   // W - Orchestration, routing
    BLUE,    // U - Reasoning, planning
    BLACK,   // B - External APIs, side effects
    RED,     // R - Fast, reactive
    GREEN,   // G - Data processing, RAG
    COLORLESS
}

enum class FrameType {
    CREATURE,
    ARTIFACT,
    MULTICOLOR
}

enum class CardStyle {
    STANDARD,    // Traditional framed card
    BORDERLESS,  // Full art extends to edges (alt art style)
    EXTENDED     // Extended art with partial frame
}

/**
 * Represents a relationship between two agents.
 */
data class AgentRelationship(
    val sourceAgent: String,
    val targetAgent: String,
    val relationshipType: RelationshipType
)

enum class RelationshipType {
    HANDOFF,     // Agent explicitly hands off to another
    TOOL_CALL,   // Agent uses another agent as a tool
    SUB_AGENT,   // Agent is a sub-agent of another
    REFERENCE    // Agent references another (inferred)
}

/**
 * Represents the full battlefield state.
 */
data class BattlefieldData(
    val agents: List<CardData>,
    val relationships: List<AgentRelationship>,
    val projectName: String
)
