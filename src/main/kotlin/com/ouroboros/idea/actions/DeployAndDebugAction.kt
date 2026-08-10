package com.ouroboros.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.ouroboros.idea.services.BuildService
import com.ouroboros.idea.services.NotificationService
import com.ouroboros.idea.services.OuroborosApiService
import com.ouroboros.idea.services.RemoteDebugService
import com.ouroboros.idea.settings.TideStackDevelopConfig
import com.ouroboros.idea.settings.TideStackDevelopConfigService
import kotlinx.coroutines.runBlocking

class DeployAndDebugAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val notificationService = project.service<NotificationService>()
        val configResult = project.service<TideStackDevelopConfigService>().loadEffectiveConfig()
        if (configResult.isFailure) {
            notificationService.showError("TideStack Develop Config is invalid: ${configResult.exceptionOrNull()?.message}")
            return
        }
        val config = configResult.getOrThrow()

        if (!validateConfig(config, project)) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Deploy and Debug", true) {
            override fun run(indicator: ProgressIndicator) {
                val buildService = project.service<BuildService>()
                val apiService = project.service<OuroborosApiService>()
                val debugService = project.service<RemoteDebugService>()

                try {
                    indicator.text = "Resolving TideStack develop target..."
                    val targetResult = apiService.resolveDevelopTarget(config)
                    if (targetResult.isFailure) {
                        notificationService.showError("Develop target resolution failed: ${targetResult.exceptionOrNull()?.message}")
                        return
                    }
                    val target = targetResult.getOrThrow()
                    notificationService.showInfo("Develop target resolved: ${target.profileId}/${target.containerName}")

                    indicator.text = "Building project..."
                    val jarResult = runBlocking { buildService.buildProject() }
                    if (jarResult.isFailure) {
                        notificationService.showError("Build failed: ${jarResult.exceptionOrNull()?.message}")
                        return
                    }
                    val jarFile = jarResult.getOrThrow()
                    notificationService.showInfo("Build completed: ${jarFile.name}")

                    indicator.text = "Uploading jar file..."
                    val uploadResult = apiService.uploadJar(jarFile, config)
                    if (uploadResult.isFailure) {
                        notificationService.showError("Upload failed: ${uploadResult.exceptionOrNull()?.message}")
                        return
                    }
                    notificationService.showInfo("Upload successful")

                    indicator.text = "Restarting container..."
                    val restartResult = apiService.restartContainer(target.containerId, config)
                    if (restartResult.isFailure) {
                        notificationService.showError("Restart failed: ${restartResult.exceptionOrNull()?.message}")
                        return
                    }
                    notificationService.showInfo("Container restarted")

                    indicator.text = "Creating debug session..."
                    val sessionResult = apiService.createDebugSession(config, target)
                    if (sessionResult.isFailure) {
                        notificationService.showError("Debug session creation failed: ${sessionResult.exceptionOrNull()?.message}")
                        return
                    }
                    val session = sessionResult.getOrThrow()
                    notificationService.showInfo("Debug session created: ${session.host}:${session.port}")

                    indicator.text = "Attaching debugger..."
                    val attachResult = debugService.createAndAttachDebugConfiguration(session.host, session.port)
                    if (attachResult.isFailure) {
                        notificationService.showError("Debugger attach failed: ${attachResult.exceptionOrNull()?.message}")
                        return
                    }

                    notificationService.showSuccess("Deploy and debug completed successfully!")
                } catch (e: Exception) {
                    notificationService.showError("Unexpected error: ${e.message}")
                }
            }
        })
    }

    private fun validateConfig(config: TideStackDevelopConfig, project: com.intellij.openapi.project.Project): Boolean {
        val notificationService = project.service<NotificationService>()

        if (config.developmentServerUrl.isBlank()) {
            notificationService.showWarning("Development Server URL is not configured")
            return false
        }
        if (config.appName.isBlank()) {
            notificationService.showWarning("Application Name is not configured")
            return false
        }
        if (config.devKey.isBlank()) {
            notificationService.showWarning("Dev Key is not configured")
            return false
        }

        return true
    }
}
