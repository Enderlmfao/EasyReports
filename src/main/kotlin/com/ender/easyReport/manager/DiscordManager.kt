package com.ender.easyReport.manager

import com.ender.easyReport.config.Settings
import com.ender.easyReport.model.Report
import com.google.gson.Gson
import org.bukkit.plugin.java.JavaPlugin
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.Executors
import java.util.logging.Level

class DiscordManager(private val plugin: JavaPlugin) {

    private val executorService = Executors.newSingleThreadExecutor()
    private val gson = Gson()

    fun sendDiscordWebhook(report: Report) {
        if (Settings.webhookUrl.isBlank() || Settings.webhookUrl == "NOT_SET") return

        executorService.submit {
            try {
                val reportedName = report.reportedPlayer.name ?: "Unknown"
                val reporterName = report.reporterName
                val locationStr = with(report.location) { "`${world?.name} @ ${blockX}, ${blockY}, ${blockZ}`" }
                val thumbnailUrl = "https://cravatar.eu/helmavatar/${reportedName}/128.png"

                val payload = DiscordWebhookPayload(
                    username = Settings.webhookUsername,
                    avatar_url = Settings.webhookAvatarUrl,
                    embeds = listOf(
                        DiscordEmbed(
                            title = Settings.embedTitle,
                            color = Settings.embedColor,
                            timestamp = Instant.now().toString(),
                            thumbnail = DiscordEmbedThumbnail(thumbnailUrl),
                            fields = listOf(
                                DiscordEmbedField(Settings.reportedFieldName, "`${reportedName}`", true),
                                DiscordEmbedField(Settings.reporterFieldName, "`${reporterName}`", true),
                                DiscordEmbedField(Settings.reasonFieldName, "```${report.reason}```", false),
                                DiscordEmbedField(Settings.proofFieldName, "```${report.proof}```", false),
                                DiscordEmbedField(Settings.locationFieldName, locationStr, false)
                            ),
                            footer = DiscordEmbedFooter("Report ID: ${report.id}")
                        )
                    )
                )
                val json = gson.toJson(payload)

                val conn = URL(Settings.webhookUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("User-Agent", "EasyReportPlugin/${plugin.description.version}")
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json) }

                if (conn.responseCode >= 300) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "No response"
                    plugin.logger.warning("[EasyReport] Failed to send Discord webhook (HTTP ${conn.responseCode}): $error")
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "[EasyReport] Exception while sending Discord webhook", e)
            }
        }
    }

    fun shutdown() {
        executorService.shutdownNow()
    }

    // Data classes for GSON serialization
    private data class DiscordWebhookPayload(val username: String, val avatar_url: String, val embeds: List<DiscordEmbed>)
    private data class DiscordEmbed(val title: String, val color: Int, val timestamp: String, val thumbnail: DiscordEmbedThumbnail, val fields: List<DiscordEmbedField>, val footer: DiscordEmbedFooter)
    private data class DiscordEmbedThumbnail(val url: String)
    private data class DiscordEmbedField(val name: String, val value: String, val inline: Boolean)
    private data class DiscordEmbedFooter(val text: String)
}
