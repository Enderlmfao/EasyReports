package com.ender.easyReport.manager

import com.ender.easyReport.config.Settings
import com.google.gson.Gson
import org.bukkit.plugin.java.JavaPlugin
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

class AIManager(private val plugin: JavaPlugin) {

    private val gson = Gson()
    private var cachedGeminiModel: String? = null

    // Regex for keyboard smashes (e.g., "asdfgh", "kljfsd")
    private val keyboardSmashRegex = Pattern.compile("(?i)(?:[a-z])\\1{3,}|[asdfghjkl]{5,}|[qwertyuiop]{5,}|[zxcvbnm]{5,}")
    
    // Regex for repeated words (e.g., "spam spam spam")
    private val repetitionRegex = Pattern.compile("(?i)(\\b\\w+\\b)(?:\\s+\\1){3,}")

    fun validateReport(reason: String, reporter: String, reported: String): CompletableFuture<String> {
        // 1. Local Pattern Recognition
        if (isObviousSpam(reason)) {
            plugin.logger.info("Local validation rejected report: $reason")
            return CompletableFuture.completedFuture("TROLL")
        }

        if (!Settings.aiEnabled || Settings.aiApiKey == "YOUR_API_KEY_HERE") {
            return CompletableFuture.completedFuture("VALID")
        }

        // 2. AI Validation
        return when (Settings.aiProvider.lowercase()) {
            "openai" -> validateWithOpenAI(reason, reporter, reported)
            "gemini" -> validateWithGemini(reason, reporter, reported)
            else -> {
                plugin.logger.warning("Invalid AI provider specified in config.yml. Defaulting to VALID.")
                CompletableFuture.completedFuture("VALID")
            }
        }
    }

    private fun isObviousSpam(reason: String): Boolean {
        val cleanReason = reason.trim()
        
        // Too short
        if (cleanReason.length < 4) return true

        // Keyboard smash
        if (keyboardSmashRegex.matcher(cleanReason).find()) return true

        // Repeated words
        if (repetitionRegex.matcher(cleanReason).find()) return true

        // No spaces (likely gibberish if long)
        if (!cleanReason.contains(" ") && cleanReason.length > 10) return true

        return false
    }

    private fun validateWithOpenAI(reason: String, reporter: String, reported: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val prompt = Settings.aiPrompt
                    .replace("%reason%", reason)
                    .replace("%reporter%", reporter)
                    .replace("%reported%", reported)

                val payload = OpenAIPayload(
                    model = "gpt-3.5-turbo",
                    messages = listOf(
                        OpenAIMessage("system", "You are a helpful assistant."),
                        OpenAIMessage("user", prompt)
                    ),
                    max_tokens = 10
                )
                val jsonPayload = gson.toJson(payload)

                val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${Settings.aiApiKey}")

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonPayload) }

                handleApiResponse(conn) { response ->
                    val result = gson.fromJson(response, OpenAIResponse::class.java)
                    result.choices.firstOrNull()?.message?.content
                }
            } catch (e: Exception) {
                plugin.logger.severe("Exception during OpenAI validation: ${e.message}")
                "VALID"
            }
        }
    }

    private fun validateWithGemini(reason: String, reporter: String, reported: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val model = getGeminiModel() ?: return@supplyAsync "VALID"

                val prompt = Settings.aiPrompt
                    .replace("%reason%", reason)
                    .replace("%reporter%", reporter)
                    .replace("%reported%", reported)

                val payload = GeminiPayload(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))
                )
                val jsonPayload = gson.toJson(payload)

                val url = "https://generativelanguage.googleapis.com/v1beta/$model:generateContent?key=${Settings.aiApiKey}"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonPayload) }

                if (conn.responseCode == 429) {
                    cachedGeminiModel = null // Clear cache to try a different model next time
                    plugin.logger.warning("Gemini API rate limit exceeded for model $model. Defaulting to VALID.")
                    return@supplyAsync "VALID"
                }

                handleApiResponse(conn) { response ->
                    val result = gson.fromJson(response, GeminiResponse::class.java)
                    result.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
            } catch (e: Exception) {
                plugin.logger.severe("Exception during Gemini validation: ${e.message}")
                "VALID"
            }
        }
    }

    private fun getGeminiModel(): String? {
        if (cachedGeminiModel != null) return cachedGeminiModel

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${Settings.aiApiKey}"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val result = gson.fromJson(response, GeminiModelsResponse::class.java)
                
                val models = result.models.filter { it.supportedGenerationMethods.contains("generateContent") }
                
                // Prioritize 2.5 flash models as requested
                val selectedModel = models.find { it.name.contains("gemini-2.5-flash") }
                    ?: models.find { it.name.contains("gemini-2.5-flash-lite") }
                    ?: models.find { it.name.contains("gemini-1.5-flash") }
                    ?: models.find { it.name.contains("gemini-1.0-pro") }
                    ?: models.firstOrNull()

                if (selectedModel != null) {
                    cachedGeminiModel = selectedModel.name
                    return selectedModel.name
                }
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "No response"
                plugin.logger.warning("Failed to fetch Gemini models (HTTP ${conn.responseCode}): $error")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Exception fetching Gemini models: ${e.message}")
        }
        return null
    }

    private fun handleApiResponse(conn: HttpURLConnection, responseParser: (String) -> String?): String {
        return if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            plugin.logger.info("Raw AI Response: $response")
            val content = responseParser(response)?.trim()?.uppercase()
            if (content != null && content in listOf("VALID", "TROLL")) {
                content
            } else {
                plugin.logger.warning("AI returned unexpected response: $content")
                "VALID" // Default to valid if the response is unexpected
            }
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "No response"
            if (conn.responseCode != 429) {
                plugin.logger.warning("AI validation failed (HTTP ${conn.responseCode}): $error")
            }
            "VALID" // Default to valid on API error
        }
    }

    // OpenAI Data Classes
    private data class OpenAIPayload(val model: String, val messages: List<OpenAIMessage>, val max_tokens: Int)
    private data class OpenAIMessage(val role: String, val content: String)
    private data class OpenAIResponse(val choices: List<Choice>)
    private data class Choice(val message: Message)
    private data class Message(val content: String)

    // Gemini Data Classes
    private data class GeminiPayload(val contents: List<GeminiContent>)
    private data class GeminiContent(val parts: List<GeminiPart>)
    private data class GeminiPart(val text: String)
    private data class GeminiResponse(val candidates: List<GeminiCandidate>?)
    private data class GeminiCandidate(val content: GeminiContent?)
    
    // Gemini Models Data Classes
    private data class GeminiModelsResponse(val models: List<GeminiModel>)
    private data class GeminiModel(val name: String, val supportedGenerationMethods: List<String>)
}
