package com.ouroboros.idea.settings

import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import com.ouroboros.idea.TideStackMessages
import com.ouroboros.idea.services.OuroborosApiService
import java.io.File
import javax.swing.JComponent

class OuroborosConfigurable(private val project: Project) : Configurable {
    private val configService = project.service<TideStackDevelopConfigService>()
    private val apiService = project.service<OuroborosApiService>()
    private var panel: DialogPanel? = null

    private var developmentServerUrl: String = ""
    private var appName: String = ""
    private var devKey: String = ""

    override fun getDisplayName(): String = TideStackMessages.message("settings.displayName")

    override fun createComponent(): JComponent {
        reloadFields()

        panel = panel {
            group(TideStackMessages.message("settings.projectSharedConfig")) {
                row(TideStackMessages.message("settings.developmentServerUrl")) {
                    textField()
                        .bindText(::developmentServerUrl)
                        .columns(40)
                        .comment(TideStackMessages.message("settings.developmentServerUrl.comment", mapOf("path" to TideStackDevelopConfigService.PROJECT_CONFIG_RELATIVE_PATH)))
                }
                row(TideStackMessages.message("settings.appName")) {
                    textField()
                        .bindText(::appName)
                        .columns(40)
                        .comment(TideStackMessages.message("settings.appName.comment"))
                }
            }

            group(TideStackMessages.message("settings.localPrivateConfig")) {
                row(TideStackMessages.message("settings.devKey")) {
                    passwordField()
                        .bindText(::devKey)
                        .columns(40)
                        .comment(TideStackMessages.message("settings.devKey.comment", mapOf("path" to TideStackDevelopConfigService.LOCAL_CONFIG_RELATIVE_PATH)))
                }
            }

            group(TideStackMessages.message("settings.quickActions")) {
                row {
                    button(TideStackMessages.message("settings.importFromFile")) {
                        importFromFile()
                    }.comment(TideStackMessages.message("settings.importFromFile.comment"))
                }
                row {
                    button(TideStackMessages.message("settings.testConnection")) {
                        testConnection()
                    }.comment(TideStackMessages.message("settings.testConnection.comment"))
                }
                row {
                    button(TideStackMessages.message("settings.resolveDevelopTarget")) {
                        resolveDevelopTarget()
                    }.comment(TideStackMessages.message("settings.resolveDevelopTarget.comment"))
                }
            }
        }
        return panel!!
    }

    private fun reloadFields() {
        val config = configService.loadEffectiveConfig().getOrElse {
            TideStackDevelopConfig("", "", "")
        }

        developmentServerUrl = config.developmentServerUrl
        appName = config.appName
        devKey = config.devKey
    }

    private fun importFromFile() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        descriptor.title = TideStackMessages.message("settings.selectConfigFileTitle")
        descriptor.description = TideStackMessages.message("settings.selectConfigFileDescription")

        val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        try {
            val content = File(virtualFile.path).readText()
            val config = TideStackDevelopConfigContracts.importDocument(JsonParser.parseString(content).asJsonObject)

            developmentServerUrl = TideStackDevelopConfigContracts.readProjectValue(
                config,
                "developmentServerUrl"
            ) ?: developmentServerUrl
            appName = TideStackDevelopConfigContracts.readProjectValue(config, "appName") ?: appName
            devKey = TideStackDevelopConfigContracts.readLocalValue(config, "devKey") ?: devKey

            saveFields().getOrElse { throw it }
            panel?.reset()

            Messages.showInfoMessage(
                TideStackMessages.message(
                    "settings.importSuccessMessage",
                    mapOf(
                        "projectPath" to configService.projectConfigPath().getOrNull(),
                        "localPath" to configService.localConfigPath().getOrNull()
                    )
                ),
                TideStackMessages.message("settings.importSuccessTitle")
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                TideStackMessages.message("settings.importFailedMessage", mapOf("message" to e.message)),
                TideStackMessages.message("settings.importFailedTitle")
            )
        }
    }

    private fun testConnection() {
        panel?.apply()
        val config = currentConfig()
        if (config.developmentServerUrl.isBlank()) {
            Messages.showWarningDialog(
                TideStackMessages.message("settings.missingServerUrl"),
                TideStackMessages.message("settings.missingConfigTitle")
            )
            return
        }

        val result = apiService.testConnection(config)
        if (result.isSuccess) {
            Messages.showInfoMessage(
                TideStackMessages.message("settings.connectionSuccessMessage"),
                TideStackMessages.message("settings.connectionSuccessTitle")
            )
        } else {
            Messages.showErrorDialog(
                TideStackMessages.message("settings.connectionFailedMessage", mapOf("message" to result.exceptionOrNull()?.message)),
                TideStackMessages.message("settings.connectionFailedTitle")
            )
        }
    }

    private fun resolveDevelopTarget() {
        panel?.apply()
        val config = currentConfig()
        if (config.developmentServerUrl.isBlank() || config.appName.isBlank() || config.devKey.isBlank()) {
            Messages.showWarningDialog(
                TideStackMessages.message("settings.missingRequiredConfig"),
                TideStackMessages.message("settings.missingConfigTitle")
            )
            return
        }

        val result = apiService.resolveDevelopTarget(config)
        if (result.isFailure) {
            Messages.showErrorDialog(
                TideStackMessages.message("settings.resolveFailedMessage", mapOf("message" to result.exceptionOrNull()?.message)),
                TideStackMessages.message("settings.resolveFailedTitle")
            )
            return
        }

        val target = result.getOrThrow()
        Messages.showInfoMessage(
            TideStackMessages.message(
                "settings.resolveTargetMessage",
                mapOf(
                    "profileId" to target.profileId,
                    "containerName" to target.containerName,
                    "containerId" to target.containerId,
                    "containerStatus" to target.containerStatus
                )
            ),
            TideStackMessages.message("settings.resolveTargetTitle")
        )
    }

    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }

    override fun apply() {
        panel?.apply()
        saveFields().onFailure {
            throw ConfigurationException(it.message ?: TideStackMessages.message("settings.failedSave"))
        }
    }

    override fun reset() {
        reloadFields()
        panel?.reset()
    }

    private fun saveFields(): Result<Unit> {
        val projectResult = configService.saveProjectConfig(
            TideStackDevelopProjectConfig(
                developmentServerUrl = developmentServerUrl.trim(),
                appName = appName.trim()
            )
        )
        if (projectResult.isFailure) {
            return projectResult
        }

        return configService.saveLocalConfig(
            TideStackDevelopLocalConfig(
                devKey = devKey.trim()
            )
        )
    }

    private fun currentConfig(): TideStackDevelopConfig {
        return TideStackDevelopConfig(
            developmentServerUrl = developmentServerUrl.trim(),
            appName = appName.trim(),
            devKey = devKey.trim()
        )
    }
}
