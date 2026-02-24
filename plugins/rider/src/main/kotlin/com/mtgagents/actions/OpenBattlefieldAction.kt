package com.mtgagents.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to open the Battlefield view tool window.
 */
class OpenBattlefieldAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("MTG Battlefield")

        toolWindow?.show()
    }

    override fun update(e: AnActionEvent) {
        // Always visible, enabled when project is open
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
