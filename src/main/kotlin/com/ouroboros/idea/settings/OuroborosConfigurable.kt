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

    override fun getDisplayName(): String = "TideStack Develop Config"

    override fun createComponent(): JComponent {
        reloadFields()

        panel = panel {
            group("Project Shared Config") {
                row("Development Server URL:") {
                    textField()
                        .bindText(::developmentServerUrl)
                        .columns(40)
                        .comment("Saved in ${TideStackDevelopConfigService.PROJECT_CONFIG_RELATIVE_PATH}")
                }
                row("Application Name:") {
                    textField()
                        .bindText(::appName)
                        .columns(40)
                        .comment("Application name in the TideStack development server")
                }
            }

            group("Local Private Config") {
                row("Dev Key:") {
                    passwordField()
                        .bindText(::devKey)
                        .columns(40)
                        .comment("Saved in ${TideStackDevelopConfigService.LOCAL_CONFIG_RELATIVE_PATH}; profile and container are resolved from this key")
                }
            }

            group("Quick Actions") {
                row {
                    button("Import from File") {
                        importFromFile()
                    }.comment("Import a platform-generated JSON config and split it into shared/local files")
                }
                row {
                    button("Test Connection") {
                        testConnection()
                    }.comment("Verify the development server is reachable")
                }
                row {
                    button("Resolve Develop Target") {
                        resolveDevelopTarget()
                    }.comment("Use the dev key to resolve profile and target container")
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
        descriptor.title = "Select TideStack Develop Config File"
        descriptor.description = "Select the JSON config file downloaded from TideStack"

        val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        try {
            val content = File(virtualFile.path).readText()
            val config = JsonParser.parseString(content).asJsonObject

            developmentServerUrl = TideStackDevelopConfigContracts.readProjectValue(
                config,
                "developmentServerUrl"
            ) ?: developmentServerUrl
            appName = TideStackDevelopConfigContracts.readProjectValue(config, "appName") ?: appName
            devKey = TideStackDevelopConfigContracts.readLocalValue(config, "devKey") ?: devKey

            saveFields().getOrElse { throw it }
            panel?.reset()

            Messages.showInfoMessage(
                "Configuration imported successfully.\n\n" +
                    "Shared: ${configService.projectConfigPath().getOrNull()}\n" +
                    "Local: ${configService.localConfigPath().getOrNull()}",
                "Import Successful"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Failed to import configuration: ${e.message}",
                "Import Failed"
            )
        }
    }

    private fun testConnection() {
        panel?.apply()
        val config = currentConfig()
        if (config.developmentServerUrl.isBlank()) {
            Messages.showWarningDialog("Please configure Development Server URL first", "Missing Configuration")
            return
        }

        val result = apiService.testConnection(config)
        if (result.isSuccess) {
            Messages.showInfoMessage("Connection successful. The development server is reachable.", "Test Connection")
        } else {
            Messages.showErrorDialog(
                "Connection failed: ${result.exceptionOrNull()?.message}",
                "Test Connection"
            )
        }
    }

    private fun resolveDevelopTarget() {
        panel?.apply()
        val config = currentConfig()
        if (config.developmentServerUrl.isBlank() || config.appName.isBlank() || config.devKey.isBlank()) {
            Messages.showWarningDialog(
                "Please configure Development Server URL, Application Name, and Dev Key first",
                "Missing Configuration"
            )
            return
        }

        val result = apiService.resolveDevelopTarget(config)
        if (result.isFailure) {
            Messages.showErrorDialog(
                "Failed to resolve develop target: ${result.exceptionOrNull()?.message}",
                "Resolve Failed"
            )
            return
        }

        val target = result.getOrThrow()
        Messages.showInfoMessage(
            "Resolved develop target:\n\n" +
                "Profile: ${target.profileId}\n" +
                "Container: ${target.containerName} (${target.containerId})\n" +
                "Status: ${target.containerStatus}",
            "Develop Target"
        )
    }

    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }

    override fun apply() {
        panel?.apply()
        saveFields().onFailure {
            throw ConfigurationException(it.message ?: "Failed to save TideStack Develop Config")
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
