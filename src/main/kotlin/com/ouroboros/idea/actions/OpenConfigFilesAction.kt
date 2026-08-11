package com.ouroboros.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.ouroboros.idea.TideStackMessages
import com.ouroboros.idea.services.NotificationService
import com.ouroboros.idea.settings.TideStackDevelopConfigService

class OpenConfigFilesAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.text = TideStackMessages.message("settings.openConfigFilesAction")
        e.presentation.description = TideStackMessages.message("settings.openConfigFilesAction.description")
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let(::openConfigFiles)
    }

    private fun openConfigFiles(project: Project) {
        val configService = project.service<TideStackDevelopConfigService>()
        val projectConfig = configService.loadProjectConfig().getOrElse {
            project.service<NotificationService>().showError(it.message ?: TideStackMessages.message("settings.failedSave"))
            return
        }
        val localConfig = configService.loadLocalConfig().getOrElse {
            project.service<NotificationService>().showError(it.message ?: TideStackMessages.message("settings.failedSave"))
            return
        }

        configService.saveProjectConfig(projectConfig).getOrElse {
            project.service<NotificationService>().showError(it.message ?: TideStackMessages.message("settings.failedSave"))
            return
        }
        configService.saveLocalConfig(localConfig).getOrElse {
            project.service<NotificationService>().showError(it.message ?: TideStackMessages.message("settings.failedSave"))
            return
        }

        openConfigFile(project, configService.projectConfigPath().getOrNull(), true)
        openConfigFile(project, configService.localConfigPath().getOrNull(), false)
    }

    private fun openConfigFile(project: Project, path: String?, focusEditor: Boolean) {
        if (path == null) {
            return
        }
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return
        FileEditorManager.getInstance(project).openFile(file, focusEditor)
    }
}
