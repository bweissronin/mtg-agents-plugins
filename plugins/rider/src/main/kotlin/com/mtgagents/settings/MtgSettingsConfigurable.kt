package com.mtgagents.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.*

class MtgSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null

    // SD Backend
    private val sdBackendCombo = JComboBox(arrayOf("AUTOMATIC1111", "ComfyUI"))
    private val sdUrlField = JBTextField()

    // Cloud API settings
    private val cloudProviderCombo = JComboBox(arrayOf("None (use bundled fallback)", "Stability AI"))
    private val stabilityApiKeyField = JBPasswordField()
    private val clearCacheButton = JButton("Clear Art Cache")
    private val cacheStatusLabel = JBLabel("")

    // Art settings
    private val artCacheCheckbox = JBCheckBox("Enable art caching")
    private val artStepsSpinner = JSpinner(SpinnerNumberModel(20, 1, 100, 1))
    private val artCfgSpinner = JSpinner(SpinnerNumberModel(7.5, 1.0, 20.0, 0.5))

    // Card rendering
    private val vectorFramesCheckbox = JBCheckBox("Use vector frames (experimental)")
    private val collectorInfoCheckbox = JBCheckBox("Show collector info on cards")

    // Parser settings
    private val inferToolsCheckbox = JBCheckBox("Infer tools from method signatures (not just decorators)")
    private val detectAllCallsCheckbox = JBCheckBox("Detect all agent-to-agent call patterns")

    override fun getDisplayName(): String = "MTG Agent Visualizer"

    override fun createComponent(): JComponent {
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Local Stable Diffusion</b></html>"))
            .addLabeledComponent("Backend:", sdBackendCombo)
            .addLabeledComponent("API URL:", sdUrlField)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Cloud API (for custom art generation)</b></html>"))
            .addLabeledComponent("Provider:", cloudProviderCombo)
            .addLabeledComponent("Stability AI Key:", stabilityApiKeyField)
            .addComponent(JBLabel("<html><i style='color:gray;font-size:10px;'>Get your API key at platform.stability.ai</i></html>"))
            .addSeparator()
            .addComponent(JBLabel("<html><b>Art Cache</b></html>"))
            .addComponent(createCachePanel())
            .addSeparator()
            .addComponent(JBLabel("<html><b>Art Generation</b></html>"))
            .addComponent(artCacheCheckbox)
            .addLabeledComponent("Generation steps:", artStepsSpinner)
            .addLabeledComponent("CFG Scale:", artCfgSpinner)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Card Rendering</b></html>"))
            .addComponent(vectorFramesCheckbox)
            .addComponent(collectorInfoCheckbox)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Parser Settings</b></html>"))
            .addComponent(inferToolsCheckbox)
            .addComponent(detectAllCallsCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Update URL field when backend changes
        sdBackendCombo.addActionListener {
            val settings = MtgSettings.getInstance()
            sdUrlField.text = when (sdBackendCombo.selectedIndex) {
                0 -> settings.sdBaseUrl
                1 -> settings.comfyUiUrl
                else -> settings.sdBaseUrl
            }
        }

        return JPanel(BorderLayout()).apply {
            add(mainPanel, BorderLayout.NORTH)
        }
    }

    override fun isModified(): Boolean {
        val settings = MtgSettings.getInstance()
        return sdBackendCombo.selectedIndex != settings.sdBackend.ordinal ||
               sdUrlField.text != getCurrentUrl(settings) ||
               cloudProviderCombo.selectedIndex != settings.cloudArtProvider.ordinal ||
               String(stabilityApiKeyField.password) != settings.stabilityApiKey ||
               artCacheCheckbox.isSelected != settings.artCacheEnabled ||
               (artStepsSpinner.value as Int) != settings.artGenerationSteps ||
               (artCfgSpinner.value as Double) != settings.artCfgScale ||
               vectorFramesCheckbox.isSelected != settings.useVectorFrames ||
               collectorInfoCheckbox.isSelected != settings.showCollectorInfo ||
               inferToolsCheckbox.isSelected != !settings.parseDecoratorsOnly ||
               detectAllCallsCheckbox.isSelected != settings.detectAllAgentCalls
    }

    override fun apply() {
        val settings = MtgSettings.getInstance()
        settings.sdBackend = SdBackend.entries[sdBackendCombo.selectedIndex]

        when (settings.sdBackend) {
            SdBackend.AUTOMATIC1111 -> settings.sdBaseUrl = sdUrlField.text
            SdBackend.COMFYUI -> settings.comfyUiUrl = sdUrlField.text
        }

        settings.cloudArtProvider = CloudArtProvider.entries[cloudProviderCombo.selectedIndex]
        settings.stabilityApiKey = String(stabilityApiKeyField.password)

        settings.artCacheEnabled = artCacheCheckbox.isSelected
        settings.artGenerationSteps = artStepsSpinner.value as Int
        settings.artCfgScale = artCfgSpinner.value as Double
        settings.useVectorFrames = vectorFramesCheckbox.isSelected
        settings.showCollectorInfo = collectorInfoCheckbox.isSelected
        settings.parseDecoratorsOnly = !inferToolsCheckbox.isSelected
        settings.detectAllAgentCalls = detectAllCallsCheckbox.isSelected
    }

    override fun reset() {
        val settings = MtgSettings.getInstance()
        sdBackendCombo.selectedIndex = settings.sdBackend.ordinal
        sdUrlField.text = getCurrentUrl(settings)
        cloudProviderCombo.selectedIndex = settings.cloudArtProvider.ordinal
        stabilityApiKeyField.text = settings.stabilityApiKey
        artCacheCheckbox.isSelected = settings.artCacheEnabled
        artStepsSpinner.value = settings.artGenerationSteps
        artCfgSpinner.value = settings.artCfgScale
        vectorFramesCheckbox.isSelected = settings.useVectorFrames
        collectorInfoCheckbox.isSelected = settings.showCollectorInfo
        inferToolsCheckbox.isSelected = !settings.parseDecoratorsOnly
        detectAllCallsCheckbox.isSelected = settings.detectAllAgentCalls
    }

    private fun getCurrentUrl(settings: MtgSettings): String {
        return when (settings.sdBackend) {
            SdBackend.AUTOMATIC1111 -> settings.sdBaseUrl
            SdBackend.COMFYUI -> settings.comfyUiUrl
        }
    }

    private fun createCachePanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))

        updateCacheStatus()

        clearCacheButton.addActionListener {
            val cacheDir = File(System.getProperty("java.io.tmpdir"), "mtg-agent-art")
            if (cacheDir.exists()) {
                val fileCount = cacheDir.listFiles()?.size ?: 0
                val confirm = Messages.showYesNoDialog(
                    "Delete $fileCount cached art file(s)?\n\nPath: ${cacheDir.absolutePath}",
                    "Clear Art Cache",
                    Messages.getQuestionIcon()
                )
                if (confirm == Messages.YES) {
                    cacheDir.deleteRecursively()
                    updateCacheStatus()
                    Messages.showInfoMessage("Art cache cleared. Run 'Generate Agent Deck' to regenerate.", "Cache Cleared")
                }
            } else {
                Messages.showInfoMessage("Cache is already empty.", "Clear Art Cache")
            }
        }

        panel.add(clearCacheButton)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(cacheStatusLabel)

        return panel
    }

    private fun updateCacheStatus() {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "mtg-agent-art")
        if (cacheDir.exists()) {
            val files = cacheDir.listFiles() ?: emptyArray()
            val sizeKb = files.sumOf { it.length() } / 1024
            cacheStatusLabel.text = "<html><i style='color:gray;'>${files.size} files (${sizeKb} KB)</i></html>"
        } else {
            cacheStatusLabel.text = "<html><i style='color:gray;'>Empty</i></html>"
        }
    }
}
