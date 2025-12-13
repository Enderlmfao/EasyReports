package com.ender.easyReport.config

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object Settings {
    // AI Settings
    var aiEnabled: Boolean = false
    var aiProvider: String = "openai"
    var aiApiKey: String = "YOUR_API_KEY_HERE"
    var aiPrompt: String = "You are a strict Minecraft server moderator. A player named %reporter% has reported %reported% for: '%reason%'. Determine if this is a valid report. VALID: Clearly describes a rule violation (e.g., 'killaura', 'flying', 'racism', 'said the n-word', 'spamming chat'). TROLL: Gibberish (e.g., 'asdfgh'), random keys, nonsense (e.g., 'pizza', 'skibidi'), single words that are not violations (e.g., 'ugly', 'bad'), or just insults directed at the player without describing a violation (e.g., 'you suck'). If the reason is just a slur (e.g., 'nigger'), assume it is a report for racism -> VALID. Respond with ONLY one word: VALID or TROLL."
    var aiRejectionMessage: String = "<red>Your report was automatically rejected as it appeared to be spam or invalid. Please provide a clear, detailed reason when reporting."

    // General Settings
    var prefix: String = "<gradient:#00AEEF:#A200FF>> </gradient>"
    var webhookUrl: String = "NOT_SET"
    var webhookUsername: String = "Server Reports"
    var webhookAvatarUrl: String = ""
    var embedColor: Int = 16734296
    var embedTitle: String = "New Player Report"
    var reporterFieldName: String = "Reported By"
    var reportedFieldName: String = "Reported Player"
    var reasonFieldName: String = "Reason"
    var proofFieldName: String = "Proof"
    var locationFieldName: String = "Location"
    var staffAlertsEnabled: Boolean = true
    var staffAlertMessage: String = "<gradient:#ff5555:#ffaa00>[Alert] <white>%reporter% <gray>reported <white>%reported% <gray>for: <yellow>%reason%"
    var reportsListTitle: String = "<gradient:#ff5555:#ffaa00><bold>Active Reports"
    var reportItemName: String = "<yellow>Report for: <white>%player%"
    var reportItemLore: List<String> = listOf(
        "<gray>Reported by: <white>%reporter%",
        "<gray>Reason: <white>%reason%",
        "<gray>Location: <white>%location%",
        "<gray>Date: <white>%date%",
        "",
        "<green><bold>Click to handle report"
    )
    var noReportsItemMaterial: Material = Material.BARRIER
    var noReportsItemName: String = "<red>No Active Reports"
    var noReportsItemLore: List<String> = emptyList()
    var msgMustBePlayer: String = "<red>This command can only be run by a player."
    var msgConfigReloaded: String = "<green>EasyReport configuration has been successfully reloaded."
    var msgUnknownCommand: String = "<red>Unknown command. Use /easyreport help for assistance."
    var msgReportUsage: String = "<red>Usage: /report <player> <reason...>"
    var msgAlreadyReporting: String = "<red>You are already in the process of filing a report."
    var msgPlayerNotFound: String = "<red>Player '%player%' has never played on this server."
    var msgCannotReportSelf: String = "<red>You cannot report yourself."
    var msgReportSubmitted: String = "<green>Thank you, your report has been submitted for review."
    var msgReportHandled: String = "<green>Your report for <white>%player% <green>has been handled. Thank you for your contribution!"
    var msgPromptForProof: String = "<gold>Please provide proof (e.g., a link to a video/screenshot) in chat."
    var msgPromptNoProof: String = "<gold>Type <yellow>'none' <gold>if you have no proof to provide."
    var maxReports: Int = 50

    // Offenses Settings
    var reportReasons: List<String> = emptyList()
    var punishmentCommands: Map<String, String> = emptyMap()
    var punishmentDurations: Map<String, Map<Int, String>> = emptyMap()

    fun load(plugin: JavaPlugin) {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config

        aiEnabled = config.getBoolean("ai-settings.enabled", aiEnabled)
        aiProvider = config.getString("ai-settings.provider", aiProvider)!!
        aiApiKey = config.getString("ai-settings.api-key", aiApiKey)!!
        aiPrompt = config.getString("ai-settings.prompt", aiPrompt)!!
        aiRejectionMessage = config.getString("ai-settings.rejection-message", aiRejectionMessage)!!

        prefix = config.getString("messages.prefix", prefix)!!
        webhookUrl = config.getString("discord-webhook.url", webhookUrl)!!
        webhookUsername = config.getString("discord-webhook.username", webhookUsername)!!
        webhookAvatarUrl = config.getString("discord-webhook.avatar-url", webhookAvatarUrl)!!
        embedColor = config.getInt("discord-webhook.embed.color", embedColor)
        embedTitle = config.getString("discord-webhook.embed.title", embedTitle)!!
        reporterFieldName = config.getString("discord-webhook.embed.reporter-field-name", reporterFieldName)!!
        reportedFieldName = config.getString("discord-webhook.embed.reported-player-field-name", reportedFieldName)!!
        reasonFieldName = config.getString("discord-webhook.embed.reason-field-name", reasonFieldName)!!
        proofFieldName = config.getString("discord-webhook.embed.proof-field-name", proofFieldName)!!
        locationFieldName = config.getString("discord-webhook.embed.location-field-name", locationFieldName)!!
        staffAlertsEnabled = config.getBoolean("staff-alerts.enabled", staffAlertsEnabled)
        staffAlertMessage = config.getString("staff-alerts.message", staffAlertMessage)!!
        reportsListTitle = config.getString("gui-settings.reports-list-title", reportsListTitle)!!
        reportItemName = config.getString("gui-settings.report-item-name", reportItemName)!!
        reportItemLore = config.getStringList("gui-settings.report-item-lore").ifEmpty { reportItemLore }
        noReportsItemMaterial = Material.matchMaterial(config.getString("gui-settings.no-reports-item-material", "BARRIER")!!) ?: Material.BARRIER
        noReportsItemName = config.getString("gui-settings.no-reports-item-name", noReportsItemName)!!
        noReportsItemLore = config.getStringList("gui-settings.no-reports-item-lore")
        msgMustBePlayer = config.getString("messages.must-be-player", msgMustBePlayer)!!
        msgConfigReloaded = config.getString("messages.config-reloaded", msgConfigReloaded)!!
        msgUnknownCommand = config.getString("messages.unknown-command", msgUnknownCommand)!!
        msgReportUsage = config.getString("messages.report-usage", msgReportUsage)!!
        msgAlreadyReporting = config.getString("messages.already-reporting", msgAlreadyReporting)!!
        msgPlayerNotFound = config.getString("messages.player-not-found", msgPlayerNotFound)!!
        msgCannotReportSelf = config.getString("messages.cannot-report-self", msgCannotReportSelf)!!
        msgReportSubmitted = config.getString("messages.report-submitted", msgReportSubmitted)!!
        msgReportHandled = config.getString("messages.report-handled-message", msgReportHandled)!!
        msgPromptForProof = config.getString("messages.prompt-for-proof", msgPromptForProof)!!
        msgPromptNoProof = config.getString("messages.prompt-no-proof-option", msgPromptNoProof)!!
        maxReports = config.getInt("max-reports", maxReports)

        loadOffenses(plugin)
    }

    private fun loadOffenses(plugin: JavaPlugin) {
        val offensesFile = File(plugin.dataFolder, "offenses.yml")
        if (!offensesFile.exists()) {
            plugin.saveResource("offenses.yml", false)
        }
        val offensesConfig = YamlConfiguration.loadConfiguration(offensesFile)

        reportReasons = offensesConfig.getStringList("report-reasons")

        val commands = mutableMapOf<String, String>()
        val durations = mutableMapOf<String, Map<Int, String>>()

        offensesConfig.getConfigurationSection("punishments")?.getKeys(false)?.forEach { type ->
            commands[type] = offensesConfig.getString("punishments.$type.command")!!
            offensesConfig.getConfigurationSection("punishments.$type.durations")?.let { durationSection ->
                val typeDurations = mutableMapOf<Int, String>()
                durationSection.getKeys(false).forEach { key ->
                    if (key == "default") {
                        typeDurations[-1] = durationSection.getString(key)!!
                    } else {
                        typeDurations[key.toInt()] = durationSection.getString(key)!!
                    }
                }
                durations[type] = typeDurations
            }
        }
        punishmentCommands = commands
        punishmentDurations = durations
    }
}
