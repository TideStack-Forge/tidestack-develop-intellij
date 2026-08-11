package com.ouroboros.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.ouroboros.idea.TideStackMessages
import com.ouroboros.idea.settings.OuroborosConfigurable

class ConfigureProjectAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.text = TideStackMessages.message("settings.configureProjectAction")
        e.presentation.description = TideStackMessages.message("settings.configureProjectAction.description")
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let(::openConfig)
    }

    companion object {
        fun openConfig(project: Project) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, OuroborosConfigurable::class.java)
        }
    }
}
