package com.mtgagents.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "MtgAgentVisualizerSettings",
    storages = [Storage("MtgAgentVisualizer.xml")]
)
class MtgSettings : PersistentStateComponent<MtgSettings> {

    // Stable Diffusion settings
    var sdBackend: SdBackend = SdBackend.AUTOMATIC1111
    var sdBaseUrl: String = "http://localhost:7860"
    var comfyUiUrl: String = "http://localhost:8188"

    // Cloud API settings (fallback when local SD unavailable)
    var cloudArtProvider: CloudArtProvider = CloudArtProvider.NONE
    var stabilityApiKey: String = ""  // User's own API key

    // Art generation settings
    var artCacheEnabled: Boolean = true
    var artCacheDirectory: String = ""  // Empty = use system temp
    var artGenerationSteps: Int = 40          // More steps for clarity
    var artCfgScale: Double = 6.5             // Balance: painterly but defined
    var artWidth: Int = 768                   // Higher res for more detail
    var artHeight: Int = 768
    var artSampler: String = "DPM++ 2M Karras"  // Best for painterly styles

    // Card rendering settings
    var useVectorFrames: Boolean = false  // false = community PNG frames, true = vector
    var cardScale: Double = 1.0
    var showCollectorInfo: Boolean = true

    // Parser settings
    var parseDecoratorsOnly: Boolean = false  // false = also infer from signatures
    var detectAllAgentCalls: Boolean = true   // Detect all forms of agent-to-agent calls

    // Color identity overrides (agent name -> forced colors)
    var colorOverrides: MutableMap<String, String> = mutableMapOf()

    override fun getState(): MtgSettings = this

    override fun loadState(state: MtgSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): MtgSettings {
            return ApplicationManager.getApplication().getService(MtgSettings::class.java)
        }
    }
}

enum class SdBackend {
    AUTOMATIC1111,
    COMFYUI
}

enum class CloudArtProvider {
    NONE,           // Use local SD only, fallback to bundled art
    STABILITY_AI    // Use Stability AI API
}
