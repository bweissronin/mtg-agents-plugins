package com.mtgagents.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.wm.ToolWindowManager
import com.mtgagents.MtgAgentService
import com.mtgagents.MtgBattlefieldPanel
import com.mtgagents.art.ArtGenerator

/**
 * Action to scan the entire project and generate MTG cards for all agents.
 */
class GenerateDeckAction : AnAction() {

    private val artGenerator = ArtGenerator()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating Agent Deck...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Scanning project for AI agents..."

                val service = project.getService(MtgAgentService::class.java)
                val battlefield = service.scanProject()

                val totalAgents = battlefield.agents.size
                var generatedCount = 0

                println("MTG GenerateDeck: Found ${battlefield.agents.size} agents to process")

                // Generate art and cache all found agents
                battlefield.agents.forEachIndexed { index, card ->
                    if (indicator.isCanceled) return

                    indicator.fraction = index.toDouble() / totalAgents
                    indicator.text = "Generating art for ${card.name} (${index + 1}/$totalAgents)..."
                    println("MTG GenerateDeck: Processing agent '${card.name}' (${index + 1}/$totalAgents)")

                    // Check cache first
                    val cacheKey = card.name.lowercase().replace(Regex("[^a-z0-9]"), "_")
                    var artUrl = artGenerator.getCachedArt(cacheKey)
                    println("MTG GenerateDeck: Cache key '$cacheKey', cached art URL: $artUrl")

                    // Generate if not cached
                    if (artUrl == null) {
                        println("MTG GenerateDeck: Calling artGenerator.generateArt()...")
                        artUrl = artGenerator.generateArt(card)
                        println("MTG GenerateDeck: Art generation result: ${artUrl ?: "NULL"}")
                        if (artUrl != null) {
                            artGenerator.cacheArt(cacheKey, artUrl)
                            generatedCount++
                        }
                    }

                    // Cache the card with art URL
                    val cardWithArt = if (artUrl != null) {
                        card.copy(artUrl = artUrl)
                    } else {
                        card
                    }
                    service.cacheCard(cardWithArt.name, cardWithArt)
                }

                indicator.fraction = 1.0
                indicator.text = "Done!"

                // Update the tool window
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val toolWindow = ToolWindowManager.getInstance(project)
                        .getToolWindow("MTG Battlefield")

                    if (toolWindow != null) {
                        toolWindow.show {
                            // Refresh the panel
                            val content = toolWindow.contentManager.getContent(0)
                            val panel = content?.component as? MtgBattlefieldPanel
                            panel?.refresh()
                        }

                        // Show summary
                        val message = "Found ${battlefield.agents.size} agent(s) with ${battlefield.relationships.size} relationship(s)\nGenerated $generatedCount new card art image(s)"
                        com.intellij.openapi.ui.Messages.showInfoMessage(project, message, "Agent Deck Generated")
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        // Always visible, enabled when project is open
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
