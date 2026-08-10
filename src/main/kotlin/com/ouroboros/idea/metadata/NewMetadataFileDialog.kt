package com.ouroboros.idea.metadata

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

data class NewMetadataFileDialogResult(
    val relativePath: String,
    val templateValues: Map<String, String>
)

class NewMetadataFileDialog(
    project: Project,
    private val contribution: MetadataContributionDefinition,
    private val creationRoot: VirtualFile,
    defaultPath: String
) : DialogWrapper(project) {
    private val pathField = JBTextField(defaultPath)
    private val templateFields = contribution.fileTemplate?.fields.orEmpty()
    private val fieldInputs = linkedMapOf<String, JTextComponent>()
    private val editedFields = mutableSetOf<String>()
    private var applyingPathDefaults = false

    init {
        title = "New ${contribution.displayName}"
        createTemplateFieldInputs(defaultPath)
        pathField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                applyPathDefaultsToUntouchedFields()
            }
        })
        init()
    }

    fun result(): NewMetadataFileDialogResult? {
        val relativePath = normalizedPath().path ?: return null
        return NewMetadataFileDialogResult(
            relativePath = relativePath,
            templateValues = fieldInputs.mapValues { (_, input) -> input.text.trim() }
        )
    }

    override fun createCenterPanel(): JComponent {
        val form = JPanel(GridBagLayout())
        form.border = JBUI.Borders.empty(8)
        var row = 0
        addFormRow(form, row++, "文件路径", pathField)
        templateFields.forEach { field ->
            addFormRow(form, row++, field.label, fieldComponent(field), field.labelRemark)
        }

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.NORTH)
            preferredSize = Dimension(560, (96 + templateFields.size * 72).coerceAtMost(420))
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = pathField

    override fun doValidate(): ValidationInfo? {
        val normalized = normalizedPath()
        if (normalized.error != null) {
            return ValidationInfo(normalized.error, pathField)
        }
        val relativePath = normalized.path ?: return ValidationInfo("Enter a metadata file path.", pathField)
        if (creationRoot.findFileByRelativePath(relativePath) != null) {
            return ValidationInfo("TideStack metadata file already exists: $relativePath", pathField)
        }
        templateFields.forEach { field ->
            val input = fieldInputs[field.name]
            if (field.required && input?.text?.trim().isNullOrEmpty()) {
                return ValidationInfo("${field.label} is required.", input)
            }
        }
        return null
    }

    private fun createTemplateFieldInputs(defaultPath: String) {
        val normalizedDefaultPath = MetadataFileTemplateService.normalizeInputPath(
            defaultPath,
            contribution,
            creationRoot.path
        ).path ?: defaultPath
        val defaults = MetadataFileTemplateService.defaultTemplateFieldValues(contribution, normalizedDefaultPath)
        templateFields.forEach { field ->
            val input = createTemplateFieldInput(field, defaults[field.name].orEmpty())
            input.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    if (!applyingPathDefaults) {
                        editedFields.add(field.name)
                    }
                }
            })
            fieldInputs[field.name] = input
        }
    }

    private fun createTemplateFieldInput(
        field: MetadataFileTemplateFieldDefinition,
        defaultValue: String
    ): JTextComponent {
        if (field.name == "description") {
            return JBTextArea(defaultValue, 3, 32).apply {
                lineWrap = true
                wrapStyleWord = true
            }
        }
        return JBTextField(defaultValue)
    }

    private fun fieldComponent(field: MetadataFileTemplateFieldDefinition): JComponent {
        val input = fieldInputs.getValue(field.name)
        if (input is JBTextArea) {
            return JBScrollPane(input).apply {
                preferredSize = Dimension(360, 76)
            }
        }
        return input
    }

    private fun addFormRow(
        form: JPanel,
        row: Int,
        labelText: String,
        component: JComponent,
        remark: String? = null
    ) {
        val label = JBLabel(labelText).apply {
            labelFor = component
        }
        form.add(label, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.NORTHWEST
            insets = JBUI.insets(0, 0, 12, 12)
        })

        form.add(JPanel(BorderLayout()).apply {
            add(component, BorderLayout.NORTH)
            if (!remark.isNullOrBlank()) {
                add(JBLabel(remark).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyTop(4)
                }, BorderLayout.CENTER)
            }
        }, GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = JBUI.insets(0, 0, 12, 0)
        })
    }

    private fun applyPathDefaultsToUntouchedFields() {
        val relativePath = normalizedPath().path ?: return
        val defaults = MetadataFileTemplateService.defaultTemplateFieldValues(contribution, relativePath)
        applyingPathDefaults = true
        try {
            templateFields.forEach { field ->
                if (!editedFields.contains(field.name)) {
                    fieldInputs[field.name]?.text = defaults[field.name].orEmpty()
                }
            }
        } finally {
            applyingPathDefaults = false
        }
    }

    private fun normalizedPath(): MetadataFileTemplatePath {
        return MetadataFileTemplateService.normalizeInputPath(pathField.text, contribution, creationRoot.path)
    }
}
