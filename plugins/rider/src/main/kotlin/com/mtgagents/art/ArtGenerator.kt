package com.mtgagents.art

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mtgagents.model.CardData
import com.mtgagents.model.ManaColor
import com.mtgagents.settings.CloudArtProvider
import com.mtgagents.settings.MtgSettings
import com.mtgagents.settings.SdBackend
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Client for generating card art via local Stable Diffusion.
 * Supports AUTOMATIC1111 and ComfyUI backends.
 */
class ArtGenerator {

    private val gson = Gson()
    private val artCache = mutableMapOf<String, String>()

    /**
     * Generate art for the given card.
     * Tries in order: Local SD → Cloud API (if configured) → Bundled fallback
     * Returns the path to the generated image.
     */
    fun generateArt(card: CardData): String? {
        val settings = MtgSettings.getInstance()

        println("MTG Art Generator: Starting art generation for '${card.name}'")

        // 1. Try local Stable Diffusion first
        if (isBackendAvailable()) {
            println("MTG Art Generator: Trying local SD backend ${settings.sdBackend} at ${settings.sdBaseUrl}")
            try {
                val result = when (settings.sdBackend) {
                    SdBackend.AUTOMATIC1111 -> generateWithAutomatic1111(card, settings)
                    SdBackend.COMFYUI -> generateWithComfyUI(card, settings)
                }
                if (result != null) {
                    println("MTG Art Generator: Successfully generated art via local SD at $result")
                    return result
                }
            } catch (e: Exception) {
                println("MTG Art Generator: Local SD failed: ${e.message}")
            }
        } else {
            println("MTG Art Generator: Local SD not available")
        }

        // 2. Try cloud API if configured
        if (settings.cloudArtProvider == CloudArtProvider.STABILITY_AI && settings.stabilityApiKey.isNotBlank()) {
            println("MTG Art Generator: Trying Stability AI cloud API")
            try {
                val result = generateWithStabilityAI(card, settings)
                if (result != null) {
                    println("MTG Art Generator: Successfully generated art via Stability AI at $result")
                    return result
                }
            } catch (e: Exception) {
                println("MTG Art Generator: Stability AI failed: ${e.message}")
            }
        }

        // 3. Fall back to bundled art
        println("MTG Art Generator: Using bundled fallback art for color ${card.colorIdentity.firstOrNull() ?: ManaColor.BLUE}")
        return getBundledFallbackArt(card)
    }

    /**
     * Generate art using AUTOMATIC1111's REST API.
     */
    private fun generateWithAutomatic1111(card: CardData, settings: MtgSettings): String? {
        val prompt = buildPrompt(card)
        val negativePrompt = buildNegativePrompt()

        val payload = JsonObject().apply {
            addProperty("prompt", prompt)
            addProperty("negative_prompt", negativePrompt)
            addProperty("steps", settings.artGenerationSteps)
            addProperty("cfg_scale", settings.artCfgScale)
            addProperty("width", settings.artWidth)
            addProperty("height", settings.artHeight)
            addProperty("sampler_name", settings.artSampler)
            addProperty("seed", -1)  // Random seed

            // Hi-res fix for sharper details (2-pass generation)
            addProperty("enable_hr", true)
            addProperty("hr_scale", 1.5)  // Upscale to 1.5x then downscale
            addProperty("hr_upscaler", "Latent")
            addProperty("hr_second_pass_steps", 15)
            addProperty("denoising_strength", 0.4)  // Lower = sharper, preserve more detail
        }

        println("MTG Art Generator: Prompt = $prompt")
        println("MTG Art Generator: Negative = $negativePrompt")

        val url = URL("${settings.sdBaseUrl}/sdapi/v1/txt2img")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 120000  // 2 minutes for generation

        connection.outputStream.use { os ->
            os.write(gson.toJson(payload).toByteArray())
        }

        println("MTG Art Generator: Sending request to ${settings.sdBaseUrl}/sdapi/v1/txt2img")
        val responseCode = connection.responseCode
        println("MTG Art Generator: Response code: $responseCode")

        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val images = jsonResponse.getAsJsonArray("images")

            println("MTG Art Generator: Received ${images?.size() ?: 0} images")

            if (images != null && images.size() > 0) {
                val base64Image = images[0].asString
                return saveBase64Image(base64Image, card.name)
            }
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { "N/A" }
            println("MTG Art Generator: Error response: $errorBody")
        }

        return null
    }

    /**
     * Generate art using ComfyUI's REST API.
     */
    private fun generateWithComfyUI(card: CardData, settings: MtgSettings): String? {
        // ComfyUI uses a workflow-based API
        // This is a simplified version - full implementation would load a workflow JSON
        val prompt = buildPrompt(card)

        val workflow = buildComfyWorkflow(prompt, settings)

        val url = URL("${settings.comfyUiUrl}/prompt")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 120000

        connection.outputStream.use { os ->
            os.write(gson.toJson(workflow).toByteArray())
        }

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val promptId = jsonResponse.get("prompt_id")?.asString

            if (promptId != null) {
                // Poll for completion and get the image
                return pollComfyUIResult(promptId, settings)
            }
        }

        return null
    }

    private fun pollComfyUIResult(promptId: String, settings: MtgSettings): String? {
        val maxAttempts = 60  // 60 seconds max wait
        var attempts = 0

        while (attempts < maxAttempts) {
            Thread.sleep(1000)
            attempts++

            try {
                val url = URL("${settings.comfyUiUrl}/history/$promptId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val history = gson.fromJson(response, JsonObject::class.java)

                    if (history.has(promptId)) {
                        val result = history.getAsJsonObject(promptId)
                        val outputs = result.getAsJsonObject("outputs")

                        // Find the image output
                        for (key in outputs.keySet()) {
                            val output = outputs.getAsJsonObject(key)
                            if (output.has("images")) {
                                val images = output.getAsJsonArray("images")
                                if (images.size() > 0) {
                                    val imageInfo = images[0].asJsonObject
                                    val filename = imageInfo.get("filename").asString
                                    val subfolder = imageInfo.get("subfolder")?.asString ?: ""

                                    // Download the image
                                    return downloadComfyImage(filename, subfolder, settings)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue polling
            }
        }

        return null
    }

    private fun downloadComfyImage(filename: String, subfolder: String, settings: MtgSettings): String? {
        val urlStr = "${settings.comfyUiUrl}/view?filename=$filename&subfolder=$subfolder&type=output"
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection

        if (connection.responseCode == 200) {
            val imageBytes = connection.inputStream.readBytes()
            val cacheDir = getCacheDirectory()
            val outputFile = File(cacheDir, filename)
            outputFile.writeBytes(imageBytes)
            return outputFile.absolutePath
        }

        return null
    }

    private fun buildComfyWorkflow(prompt: String, settings: MtgSettings): JsonObject {
        // Simplified ComfyUI workflow
        // A full implementation would use a proper workflow JSON template
        return JsonObject().apply {
            add("prompt", JsonObject().apply {
                // KSampler node
                add("3", JsonObject().apply {
                    addProperty("class_type", "KSampler")
                    add("inputs", JsonObject().apply {
                        addProperty("seed", (Math.random() * 1000000000).toLong())
                        addProperty("steps", settings.artGenerationSteps)
                        addProperty("cfg", settings.artCfgScale)
                        addProperty("sampler_name", "dpmpp_2m")
                        addProperty("scheduler", "karras")
                    })
                })
                // CLIPTextEncode for positive prompt
                add("6", JsonObject().apply {
                    addProperty("class_type", "CLIPTextEncode")
                    add("inputs", JsonObject().apply {
                        addProperty("text", prompt)
                    })
                })
            })
        }
    }

    /**
     * Build the Stable Diffusion prompt from card data.
     * Uses custom art hints if provided in metadata.
     * Optimized for authentic MTG card art style - traditional painting aesthetic.
     */
    private fun buildPrompt(card: CardData): String {
        // Core MTG art style - TRADITIONAL painting with sharp detail
        val mtgStyleCore = """
            traditional fantasy oil painting, masterwork illustration,
            painted with confident brushstrokes, rich color depth,
            sharp defined edges, clear focal point, crisp details,
            professional fantasy book cover art,
            Magic the Gathering card art quality
        """.trimIndent().replace("\n", " ")

        // Color-specific lighting and atmosphere
        val colorAtmosphere = getColorAtmosphere(card.colorIdentity)

        // Artist style - focus on detailed traditional painters
        val artistStyle = "by Donato Giancola, Terese Nielsen, Todd Lockwood, Michael Whelan, Brom"

        // Quality terms that add sharpness without AI-look
        val qualityTerms = "award winning illustration, highly detailed face, intricate costume details, sharp focus, in-focus subject"

        // If a custom art prompt is provided, use it with MTG style additions
        if (!card.artPrompt.isNullOrBlank()) {
            val customStyle = card.artStyle ?: ""
            return buildString {
                append(card.artPrompt)
                append(", ")
                append(mtgStyleCore)
                if (customStyle.isNotBlank()) {
                    append(", ")
                    append(customStyle)
                }
                append(", ")
                append(colorAtmosphere)
                append(", ")
                append(artistStyle)
                append(", ")
                append(qualityTerms)
            }
        }

        // Otherwise, build prompt from card data
        val creatureType = card.creatureType ?: inferCreatureType(card)
        val colorPalette = getColorPalette(card.colorIdentity)
        val setting = getColorSetting(card.colorIdentity)
        val style = card.artStyle ?: ""

        return buildString {
            append("detailed fantasy portrait of $creatureType")
            append(", dynamic pose, clear subject")
            append(", $colorPalette color scheme")
            append(", $setting")
            append(", ")
            append(mtgStyleCore)
            if (style.isNotBlank()) {
                append(", ")
                append(style)
            }
            append(", ")
            append(colorAtmosphere)
            append(", dramatic lighting, strong contrast")
            append(", ")
            append(artistStyle)
            append(", ")
            append(qualityTerms)
        }
    }

    private fun buildNegativePrompt(): String {
        return """
            photograph, photo, photorealistic, hyperrealistic, realistic, real life,
            3d render, CGI, unreal engine, octane render, cinema4d, blender,
            digital art, digital painting, airbrushed, smooth gradients,
            anime, cartoon, comic book, manga, cel shaded, vector art,
            text, words, letters, watermark, logo, signature, artist name,
            border, frame, card border, card frame, UI elements, website,
            blurry, out of focus, low quality, jpeg artifacts, noise, grain,
            ugly, deformed, disfigured, mutated, bad anatomy, extra limbs,
            oversaturated, HDR, oversharpened, overprocessed,
            plastic skin, airbrushed skin, perfect skin, flawless,
            modern, contemporary, technology, computer, sci-fi,
            plain background, white background, simple background, studio lighting,
            stock photo, corporate, generic, boring, amateur,
            bad proportions, poorly drawn, extra fingers, missing fingers,
            smooth, artificial, fake, uncanny valley, wax figure
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Get atmospheric lighting/mood keywords for card colors.
     */
    private fun getColorAtmosphere(colors: List<ManaColor>): String {
        if (colors.isEmpty()) return "ethereal silver glow, cosmic atmosphere"
        if (colors.size > 1) return "golden divine radiance, multicolored magical energy swirling"

        return when (colors[0]) {
            ManaColor.WHITE -> "divine golden light rays, heavenly atmosphere, soft warm glow, celestial radiance, dawn lighting"
            ManaColor.BLUE -> "mystical blue arcane energy, ethereal mist, deep ocean depths, moonlit atmosphere, cool magical glow"
            ManaColor.BLACK -> "dark shadows, sickly green necrotic glow, ominous purple mist, moonless night, eerie atmosphere"
            ManaColor.RED -> "blazing fire and flames, volcanic orange glow, explosive energy, dramatic red lighting, intense heat"
            ManaColor.GREEN -> "dappled forest sunlight, verdant overgrowth, ancient primal energy, natural green glow, wild untamed"
            ManaColor.COLORLESS -> "otherworldly silver radiance, cosmic void, crystalline structures, eldritch geometry"
        }
    }

    /**
     * Get color palette description for the card colors.
     */
    private fun getColorPalette(colors: List<ManaColor>): String {
        if (colors.isEmpty()) return "silver and gray"
        if (colors.size > 1) return "rich gold and multicolored"

        return when (colors[0]) {
            ManaColor.WHITE -> "warm white, cream, gold, and soft yellow"
            ManaColor.BLUE -> "deep blue, cyan, silver, and purple"
            ManaColor.BLACK -> "dark purple, sickly green, black, and gray"
            ManaColor.RED -> "fiery red, orange, yellow, and crimson"
            ManaColor.GREEN -> "forest green, brown, gold, and emerald"
            ManaColor.COLORLESS -> "silver, chrome, crystal, and iridescent"
        }
    }

    /**
     * Get appropriate setting/background for card colors.
     */
    private fun getColorSetting(colors: List<ManaColor>): String {
        if (colors.isEmpty()) return "floating in the blind eternities"
        if (colors.size > 1) return "at a magical nexus of power"

        return when (colors[0]) {
            ManaColor.WHITE -> "in a gleaming marble temple with sunbeams through stained glass"
            ManaColor.BLUE -> "in an ancient mystical library with swirling arcane runes"
            ManaColor.BLACK -> "in a cursed swamp graveyard with twisted dead trees"
            ManaColor.RED -> "in a volcanic mountain fortress with rivers of lava"
            ManaColor.GREEN -> "in a primordial ancient forest with massive trees"
            ManaColor.COLORLESS -> "amid floating crystalline structures in the void"
        }
    }

    /**
     * Infer creature type from card data.
     */
    private fun inferCreatureType(card: CardData): String {
        val text = (card.name + " " + (card.flavorText ?: "")).lowercase()

        return when {
            text.contains("search") || text.contains("research") -> "hooded scholarly wizard with ancient tome"
            text.contains("data") || text.contains("analyz") -> "crystalline golem construct with glowing runes"
            text.contains("write") || text.contains("creat") -> "ethereal spirit scribe with quill and scroll"
            text.contains("code") || text.contains("program") -> "mechanical arcane automaton with gears"
            text.contains("orchestrat") || text.contains("manag") -> "armored celestial commander with banner"
            text.contains("fast") || text.contains("quick") -> "crackling lightning elemental"
            text.contains("api") || text.contains("connect") -> "shadowy planar messenger"
            text.contains("guard") || text.contains("protect") -> "stalwart armored guardian"
            text.contains("destroy") || text.contains("kill") -> "fearsome death knight"
            text.contains("heal") || text.contains("restore") -> "radiant angel cleric"
            text.contains("summon") || text.contains("call") -> "robed summoner mage"
            else -> "powerful mystical entity"
        }
    }

    /**
     * Get color adjective for prompts.
     */
    private fun colorToAdjective(color: ManaColor): String {
        return when (color) {
            ManaColor.WHITE -> "radiant holy"
            ManaColor.BLUE -> "arcane mystical"
            ManaColor.BLACK -> "dark necrotic"
            ManaColor.RED -> "blazing chaotic"
            ManaColor.GREEN -> "primal wild"
            ManaColor.COLORLESS -> "ethereal cosmic"
        }
    }

    /**
     * Save base64 image data to a file.
     */
    private fun saveBase64Image(base64: String, cardName: String): String {
        val imageBytes = Base64.getDecoder().decode(base64)
        val cacheDir = getCacheDirectory()
        val filename = cardName.lowercase().replace(Regex("[^a-z0-9]"), "_") + ".png"
        val outputFile = File(cacheDir, filename)

        outputFile.writeBytes(imageBytes)
        return outputFile.absolutePath
    }

    /**
     * Get the art cache directory.
     */
    private fun getCacheDirectory(): File {
        val settings = MtgSettings.getInstance()
        val cacheDir = if (settings.artCacheDirectory.isNotEmpty()) {
            File(settings.artCacheDirectory)
        } else {
            File(System.getProperty("java.io.tmpdir"), "mtg-agent-art")
        }

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        return cacheDir
    }

    /**
     * Check if art is cached for the given key.
     */
    fun getCachedArt(cacheKey: String): String? {
        // Check in-memory cache first
        artCache[cacheKey]?.let {
            println("MTG ArtGenerator: Found '$cacheKey' in memory cache: $it")
            return it
        }

        // Check file cache
        val cacheDir = getCacheDirectory()
        println("MTG ArtGenerator: Cache directory: ${cacheDir.absolutePath}")
        val file = File(cacheDir, "$cacheKey.png")
        println("MTG ArtGenerator: Looking for ${file.absolutePath}, exists=${file.exists()}")
        if (file.exists()) {
            artCache[cacheKey] = file.absolutePath
            return file.absolutePath
        }

        println("MTG ArtGenerator: No cache found for '$cacheKey'")
        return null
    }

    /**
     * Cache art for the given key.
     */
    fun cacheArt(cacheKey: String, artPath: String) {
        artCache[cacheKey] = artPath
    }

    /**
     * Clear the in-memory cache.
     */
    fun clearCache() {
        artCache.clear()
    }

    /**
     * Check if the SD backend is available.
     */
    fun isBackendAvailable(): Boolean {
        val settings = MtgSettings.getInstance()
        val baseUrl = when (settings.sdBackend) {
            SdBackend.AUTOMATIC1111 -> settings.sdBaseUrl
            SdBackend.COMFYUI -> settings.comfyUiUrl
        }

        return try {
            val url = URL(baseUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate art using Stability AI's REST API.
     */
    private fun generateWithStabilityAI(card: CardData, settings: MtgSettings): String? {
        val prompt = buildPrompt(card)

        // Use Stability AI's text-to-image endpoint
        val url = URL("https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${settings.stabilityApiKey}")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 120000

        val payload = """
            {
                "text_prompts": [
                    {"text": "${escapeJson(prompt)}", "weight": 1.0},
                    {"text": "${escapeJson(buildNegativePrompt())}", "weight": -1.0}
                ],
                "cfg_scale": ${settings.artCfgScale},
                "height": 1024,
                "width": 1024,
                "steps": ${settings.artGenerationSteps.coerceAtMost(50)},
                "samples": 1
            }
        """.trimIndent()

        println("MTG Art Generator: Stability AI prompt = $prompt")

        connection.outputStream.use { os ->
            os.write(payload.toByteArray())
        }

        val responseCode = connection.responseCode
        println("MTG Art Generator: Stability AI response code: $responseCode")

        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val artifacts = jsonResponse.getAsJsonArray("artifacts")

            if (artifacts != null && artifacts.size() > 0) {
                val base64Image = artifacts[0].asJsonObject.get("base64").asString
                return saveBase64Image(base64Image, card.name)
            }
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { "N/A" }
            println("MTG Art Generator: Stability AI error: $errorBody")
        }

        return null
    }

    /**
     * Get bundled fallback art based on card's primary color.
     * Copies from resources to cache directory and returns path.
     */
    private fun getBundledFallbackArt(card: CardData): String? {
        val primaryColor = card.colorIdentity.firstOrNull() ?: ManaColor.BLUE

        val resourceName = when (primaryColor) {
            ManaColor.WHITE -> "white_knight.png"
            ManaColor.BLUE -> "blue_scholar.png"
            ManaColor.BLACK -> "black_vampire.png"
            ManaColor.RED -> "red_goblin.png"
            ManaColor.GREEN -> "green_elf.png"
            ManaColor.COLORLESS -> "blue_scholar.png"  // Default to blue for colorless
        }

        val resourcePath = "/art/fallback/$resourceName"

        return try {
            val inputStream = javaClass.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                val cacheDir = getCacheDirectory()
                val cacheKey = card.name.lowercase().replace(Regex("[^a-z0-9]"), "_")
                val outputFile = File(cacheDir, "${cacheKey}_fallback.png")

                // Copy resource to cache
                outputFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()

                println("MTG Art Generator: Copied fallback art to ${outputFile.absolutePath}")
                outputFile.absolutePath
            } else {
                println("MTG Art Generator: Fallback resource not found: $resourcePath")
                null
            }
        } catch (e: Exception) {
            println("MTG Art Generator: Failed to load fallback art: ${e.message}")
            null
        }
    }

    /**
     * Escape string for JSON.
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", "")
            .replace("\t", " ")
    }
}
