package com.ouroboros.idea.services

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class RemoteDebugService(private val project: Project) {

    fun createAndAttachDebugConfiguration(host: String, port: Int): Result<Unit> {
        return try {
            val runManager = RunManager.getInstance(project)
            val configurationType = RemoteConfigurationType.getInstance()

            val configName = "Ouroboros Debug - $host:$port"
            val existing = runManager.findConfigurationByName(configName)
            val settings = existing
                ?: runManager.createConfiguration(configName, configurationType.configurationFactories[0])

            val configuration = settings.configuration as RemoteConfiguration
            configuration.HOST = host
            configuration.PORT = port.toString()
            configuration.USE_SOCKET_TRANSPORT = true
            configuration.SERVER_MODE = false

            if (existing == null) {
                runManager.addConfiguration(settings)
            }

            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
