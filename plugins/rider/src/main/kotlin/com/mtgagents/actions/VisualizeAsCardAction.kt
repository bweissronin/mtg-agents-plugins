package com.mtgagents.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.mtgagents.MtgAgentService
import com.mtgagents.MtgCardDialog
import com.mtgagents.art.ArtGenerator
import com.mtgagents.model.CardData

/**
 * Action to visualize the current agent as an MTG card.
 * Triggered via right-click context menu or keyboard shortcut.
 */
class VisualizeAsCardAction : AnAction() {

    private val artGenerator = ArtGenerator()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Get line number from editor if available, otherwise use 1
        val editor = e.getData(CommonDataKeys.EDITOR)
        val lineNumber = editor?.caretModel?.logicalPosition?.line?.plus(1) ?: 1
        val filePath = file.path

        // Parse the agent at this location
        val service = project.getService(MtgAgentService::class.java)
        val card = service.parseAgent(filePath, lineNumber)

        if (card != null) {
            // Generate art in background, then show dialog
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Generating MTG Card Art...",
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Generating art for ${card.name}..."

                    // Check cache first
                    val cacheKey = card.name.lowercase().replace(Regex("[^a-z0-9]"), "_")
                    var artUrl = artGenerator.getCachedArt(cacheKey)

                    // Generate if not cached
                    if (artUrl == null) {
                        indicator.text = "Calling Stable Diffusion..."
                        artUrl = artGenerator.generateArt(card)
                        if (artUrl != null) {
                            artGenerator.cacheArt(cacheKey, artUrl)
                        }
                    }

                    // Create updated card with art URL
                    val cardWithArt = if (artUrl != null) {
                        card.copy(artUrl = artUrl)
                    } else {
                        card
                    }

                    // Show dialog on EDT
                    ApplicationManager.getApplication().invokeLater {
                        service.cacheCard(cardWithArt.name, cardWithArt)
                        MtgCardDialog(project, cardWithArt).show()
                    }
                }
            })
        } else {
            // Show notification that no agent was found
            com.intellij.openapi.ui.Messages.showWarningDialog(
                project,
                "No AI agent found at this location.\n\nSupported formats:\n• Markdown with YAML frontmatter (name, model, color)\n• LangChain (TypeScript)\n• OpenAI Agents SDK (TypeScript)\n• CrewAI (TypeScript)",
                "MTG Agent Visualizer"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        // Enable for TypeScript and Markdown files
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = file != null &&
            (file.extension == "ts" || file.extension == "tsx" || file.extension == "md")
    }
}
