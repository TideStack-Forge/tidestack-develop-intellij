package com.ouroboros.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.ouroboros.idea.TideStackMessages
import com.ouroboros.idea.services.BuildService
import com.ouroboros.idea.services.NotificationService
import com.ouroboros.idea.services.OuroborosApiService
import com.ouroboros.idea.services.RemoteDebugService
import com.ouroboros.idea.settings.TideStackDevelopConfig
import com.ouroboros.idea.settings.TideStackDevelopConfigService
import kotlinx.coroutines.runBlocking

class DeployAndDebugAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.text = TideStackMessages.message("deploy.action")
        e.presentation.description = TideStackMessages.message("deploy.action.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val notificationService = project.service<NotificationService>()
        val configResult = project.service<TideStackDevelopConfigService>().loadEffectiveConfig()
        if (configResult.isFailure) {
            notificationService.showError(TideStackMessages.message("deploy.configInvalid", mapOf("message" to configResult.exceptionOrNull()?.message)))
            return
        }
        val config = configResult.getOrThrow()

        if (!validateConfig(config, project)) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, TideStackMessages.message("deploy.task"), true) {
            override fun run(indicator: ProgressIndicator) {
                val buildService = project.service<BuildService>()
                val apiService = project.service<OuroborosApiService>()
                val debugService = project.service<RemoteDebugService>()

                try {
                    indicator.text = TideStackMessages.message("deploy.resolveTarget")
                    val targetResult = apiService.resolveDevelopTarget(config)
                    if (targetResult.isFailure) {
                        notificationService.showError(TideStackMessages.message("deploy.resolveTargetFailed", mapOf("message" to targetResult.exceptionOrNull()?.message)))
                        return
                    }
                    val target = targetResult.getOrThrow()
                    notificationService.showInfo(TideStackMessages.message("deploy.targetResolved", mapOf("profileId" to target.profileId, "containerName" to target.containerName)))

                    indicator.text = TideStackMessages.message("deploy.buildProject")
                    val jarResult = runBlocking { buildService.buildProject() }
                    if (jarResult.isFailure) {
                        notificationService.showError(TideStackMessages.message("deploy.buildFailed", mapOf("message" to jarResult.exceptionOrNull()?.message)))
                        return
                    }
                    val jarFile = jarResult.getOrThrow()
                    notificationService.showInfo(TideStackMessages.message("deploy.buildCompleted", mapOf("fileName" to jarFile.name)))

                    indicator.text = TideStackMessages.message("deploy.uploadJar")
                    val uploadResult = apiService.uploadJar(jarFile, config)
                    if (uploadResult.isFailure) {
                        notificationService.showError(TideStackMessages.message("deploy.uploadFailed", mapOf("message" to uploadResult.exceptionOrNull()?.message)))
                        return
                    }
                    notificationService.showInfo(TideStackMessages.message("deploy.uploadSuccess"))

                    indicator.text = TideStackMessages.message("deploy.restartContainer")
                    val restartResult = apiService.restartContainer(target.containerId, config)
                    if (restartResult.isFailure) {
                        notificationService.showError(TideStackMessages.message("deploy.restartFailed", mapOf("message" to restartResult.exceptionOrNull()?.message)))
                        return
                    }
                    notificationService.showInfo(TideStackMessages.message("deploy.containerRestarted"))

                    indicator.text = TideStackMessages.message("deploy.createDebugSession")
                    val sessionResult = apiService.createDebugSession(config, target)
                    if (sessionResult.isFailure) {
                        notificationService.showError(TideStackMessages.message("deploy.createDebugSessionFailed", mapOf("message" to sessionResult.exceptionOrNull()?.message)))
                        return
                    }
                    val session = sessionResult.getOrThrow()
                    notificationService.showInfo(TideStackMessages.message("deploy.debugSessionCreated", mapOf("host" to session.host, "port" to session.port)))

                    indicator.text = TideStackMessages.message("deploy.attachDebugger")
                    val attachResult = debugService.createAndAttachDebugConfiguration(session.host, session.port)
                    if (attachResult.isFailure) {
                        notificationService.showError(TideStackMessages.message("deploy.attachDebuggerFailed", mapOf("message" to attachResult.exceptionOrNull()?.message)))
                        return
                    }

                    notificationService.showSuccess(TideStackMessages.message("deploy.completed"))
                } catch (e: Exception) {
                    notificationService.showError(TideStackMessages.message("deploy.unexpectedError", mapOf("message" to e.message)))
                }
            }
        })
    }

    private fun validateConfig(config: TideStackDevelopConfig, project: com.intellij.openapi.project.Project): Boolean {
        if (config.developmentServerUrl.isBlank()) {
            showMissingConfigWarning(project, TideStackMessages.message("deploy.missingServerUrl"))
            return false
        }
        if (config.appName.isBlank()) {
            showMissingConfigWarning(project, TideStackMessages.message("deploy.missingAppName"))
            return false
        }
        if (config.devKey.isBlank()) {
            showMissingConfigWarning(project, TideStackMessages.message("deploy.missingDevKey"))
            return false
        }

        return true
    }

    private fun showMissingConfigWarning(project: com.intellij.openapi.project.Project, message: String) {
        project.service<NotificationService>().showWarning(
            message,
            TideStackMessages.message("settings.configureProjectAction")
        ) {
            ConfigureProjectAction.openConfig(project)
        }
    }
}
