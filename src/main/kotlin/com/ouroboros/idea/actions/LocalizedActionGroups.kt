package com.ouroboros.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.ouroboros.idea.TideStackMessages

abstract class LocalizedActionGroup(
    private val textKey: String
) : DefaultActionGroup(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.text = TideStackMessages.actionText(textKey)
    }
}

class TideStackDevelopProjectActionsGroup : LocalizedActionGroup("extension.displayName")

class NewMetadataFileActionGroup : LocalizedActionGroup("metadata.new.submenu")
