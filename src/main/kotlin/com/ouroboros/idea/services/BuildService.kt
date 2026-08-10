package com.ouroboros.idea.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@Service(Service.Level.PROJECT)
class BuildService(private val project: Project) {

    suspend fun buildProject(): Result<File> {
        return try {
            ProjectTaskManager.getInstance(project).buildAllModules()

            val jarFile: File? = withTimeoutOrNull(120_000L) {
                while (true) {
                    findBuiltJar()?.let { return@withTimeoutOrNull it }
                    delay(1_000L)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }

            if (jarFile != null) {
                Result.success(jarFile)
            } else {
                Result.failure(Exception("Jar file not found after build"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findBuiltJar(): File? {
        val basePath = project.basePath ?: return null

        val mavenTarget = File(basePath, "target")
        if (mavenTarget.exists()) {
            mavenTarget.listFiles()
                ?.filter { it.extension == "jar" && !it.name.contains("sources") && !it.name.contains("javadoc") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { return it }
        }

        val gradleBuild = File(basePath, "build/libs")
        if (gradleBuild.exists()) {
            gradleBuild.listFiles()
                ?.filter { it.extension == "jar" && !it.name.contains("sources") && !it.name.contains("javadoc") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { return it }
        }

        return null
    }
}
