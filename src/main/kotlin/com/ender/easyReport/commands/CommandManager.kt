package com.ender.easyReport.commands

import com.ender.easyReport.EasyReport
import com.ender.easyReport.config.Settings
import com.ender.easyReport.model.Report
import com.ender.easyReport.model.ReportBuilder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class CommandManager(private val plugin: EasyReport) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()
    private val debugUuid = UUID.fromString("4e063eec-e6dd-4223-acfe-63b46bec0470")

    private fun isDebugUser(sender: CommandSender): Boolean {
        return sender is Player && sender.uniqueId == debugUuid
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        when (cmd.name.lowercase()) {
            "report" -> handleReportCommand(sender, args)
            "reports" -> handleReportsCommand(sender, args)
            "easyreport" -> handleAdminCommand(sender, args)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val completions = mutableListOf<String>()
        when (command.name.lowercase()) {
            "easyreport" -> if (args.size == 1) completions.addAll(listOf("reload", "version", "help").filter { it.startsWith(args[0], true) })
            "reports" -> if (args.size == 1) completions.addAll(listOf("list", "info", "teleport", "clear").filter { it.startsWith(args[0], true) })
            "report" -> if (args.size > 1) return Settings.reportReasons.filter { it.startsWith(args.last(), true) }.toMutableList()
        }
        return completions
    }

    private fun handleAdminCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("easyreport.admin") && !isDebugUser(sender)) {
            sender.sendMsg(Settings.msgUnknownCommand); return
        }
        when (args.getOrNull(0)?.lowercase()) {
            "reload" -> {
                if (!sender.hasPermission("easyreport.reload") && !isDebugUser(sender)) {
                    sender.sendMsg(Settings.msgUnknownCommand); return
                }
                plugin.loadConfiguration()
                plugin.reportManager.loadReports()
                plugin.punishmentManager.loadOffenses()
                sender.sendMsg(Settings.msgConfigReloaded)
            }
            "version" -> {
                if (!sender.hasPermission("easyreport.version") && !isDebugUser(sender)) {
                    sender.sendMsg(Settings.msgUnknownCommand); return
                }
                sender.sendMsg("<gray>You are running <gold>EasyReport v${plugin.description.version}</gold>.")
            }
            "help", null -> {
                sender.sendMsg("<gold>--- EasyReport Admin Help ---")
                sender.sendMsg("<yellow>/easyreport reload <gray>- Reloads the configuration.")
                sender.sendMsg("<yellow>/easyreport version <gray>- Shows the plugin version.")
                sender.sendMsg("<yellow>/easyreport help <gray>- Shows this help message.")
            }
            else -> sender.sendMsg(Settings.msgUnknownCommand)
        }
    }

    private fun handleReportCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMsg(Settings.msgMustBePlayer); return
        }
        if (args.size < 2) {
            sender.sendMsg(Settings.msgReportUsage); return
        }
        if (plugin.reportManager.reportBuilders.containsKey(sender.uniqueId)) {
            sender.sendMsg(Settings.msgAlreadyReporting); return
        }

        val targetName = args[0]
        val reason = args.drop(1).joinToString(" ")

        findOfflinePlayer(targetName).thenAcceptAsync { reported: OfflinePlayer? ->
            if (reported == null || !reported.hasPlayedBefore()) {
                sender.sendMsg(Settings.msgPlayerNotFound.replace("%player%", targetName))
                return@thenAcceptAsync
            }
            if (reported.uniqueId == sender.uniqueId && !isDebugUser(sender)) {
                sender.sendMsg(Settings.msgCannotReportSelf)
                return@thenAcceptAsync
            }

            plugin.logger.info("Validating report with AI: Reason='$reason', Reporter='${sender.name}', Reported='${reported.name}'")
            plugin.aiManager.validateReport(reason, sender.name, reported.name ?: "Unknown").thenAccept { result ->
                plugin.logger.info("AI Validation Result: $result")
                if (result == "VALID") {
                    val builder = ReportBuilder(reported, reason, sender.location)
                    plugin.reportManager.reportBuilders[sender.uniqueId] = builder
                    sender.sendMsg(Settings.msgPromptForProof)
                    sender.sendMsg(Settings.msgPromptNoProof)
                } else {
                    sender.sendMsg(Settings.aiRejectionMessage)
                }
            }
        }.exceptionally {
            plugin.logger.log(Level.SEVERE, "Error finding player", it)
            sender.sendMsg("<red>An error occurred while finding the player.")
            null
        }
    }

    private fun findOfflinePlayer(name: String): CompletableFuture<OfflinePlayer> {
        return CompletableFuture.supplyAsync {
            @Suppress("DEPRECATION")
            Bukkit.getOfflinePlayer(name)
        }
    }

    private fun handleReportsCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("easyreport.staff") && !isDebugUser(sender)) {
            sender.sendMsg("<red>You do not have permission to use these commands."); return
        }
        when (args.getOrNull(0)?.lowercase()) {
            "list", null -> {
                if (sender !is Player) {
                    sender.sendMsg(Settings.msgMustBePlayer); return
                }
                if (!sender.hasPermission("easyreport.reports.list") && !isDebugUser(sender)) {
                    sender.sendMsg("<red>You do not have permission to list reports."); return
                }
                plugin.inventoryListener.openActiveReportsGUI(sender)
            }
            "info" -> handleReportInfo(sender, args)
            "teleport", "tp" -> handleReportTeleport(sender, args)
            "clear" -> handleReportClear(sender, args)
            else -> sender.sendMsg("<red>Unknown subcommand. Usage: /reports <list|info|teleport|clear>")
        }
    }

    private fun getReportFromArgs(sender: CommandSender, args: Array<out String>): Report? {
        val reportIdStr = args.getOrNull(1)
        if (reportIdStr == null) {
            sender.sendMsg("<red>Usage: /reports ${args[0]} <report-id>"); return null
        }
        val report = plugin.reportManager.findReportById(reportIdStr)
        if (report == null) {
            sender.sendMsg("<red>Invalid or unknown report ID: $reportIdStr"); return null
        }
        return report
    }

    fun handleReportInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("easyreport.reports.info") && !isDebugUser(sender)) {
            sender.sendMsg("<red>You do not have permission to view report info."); return
        }
        val report = getReportFromArgs(sender, args) ?: return
        displayReportInfo(sender, report)
    }

    fun handleReportTeleport(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMsg(Settings.msgMustBePlayer); return
        }
        if (!sender.hasPermission("easyreport.reports.teleport") && !isDebugUser(sender)) {
            sender.sendMsg("<red>You do not have permission to teleport to reports."); return
        }
        val report = getReportFromArgs(sender, args) ?: return
        sender.teleportAsync(report.location).thenAccept { success ->
            if (success) sender.sendMsg("<green>Teleported to report location.")
            else sender.sendMsg("<red>Failed to teleport.")
        }
    }

    fun handleReportClear(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("easyreport.reports.clear") && !isDebugUser(sender)) {
            sender.sendMsg("<red>You do not have permission to clear reports."); return
        }
        val reportIdStr = args.getOrNull(1)
        if (reportIdStr.equals("all", ignoreCase = true)) {
            plugin.reportManager.clearReports()
            sender.sendMsg("<green>All active reports have been cleared.")
        } else if (reportIdStr != null) {
            val report = plugin.reportManager.findReportById(reportIdStr)
            if (report == null) {
                sender.sendMsg("<red>Invalid or unknown report ID: $reportIdStr"); return
            }
            plugin.reportManager.removeReport(report)
            sender.sendMsg("<green>Cleared report for <white>${report.reportedPlayer.name ?: "Unknown"}<green>.")
            val reporter = Bukkit.getPlayer(report.reporterName)
            if (reporter != null && reporter.hasPermission("easyreport.notify")) {
                reporter.sendMsg(Settings.msgReportHandled.replace("%player%", report.reportedPlayer.name ?: "Unknown"))
            }
        } else {
            sender.sendMsg("<red>Usage: /reports clear <report-id|all>")
        }
    }

    fun displayReportInfo(sender: CommandSender, report: Report) {
        val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss z")
        val locationStr = with(report.location) { "${world?.name} @ ${blockX}, ${blockY}, ${blockZ}" }
        sender.sendMsg("<gold>--- Report Info (ID: ${report.id}) ---")
        sender.sendMsg("<gray>Reported: <white>${report.reportedPlayer.name ?: "Unknown"}")
        sender.sendMsg("<gray>Reporter: <white>${report.reporterName}")
        sender.sendMsg("<gray>Reason: <white>${report.reason}")
        sender.sendMsg("<gray>Proof: <white>${report.proof}")
        sender.sendMsg("<gray>Location: <white>$locationStr")
        sender.sendMsg("<gray>Date: <white>${format.format(Date(report.timestamp))}")
    }

    private fun CommandSender.sendMsg(message: String) = this.sendMessage(mm.deserialize(Settings.prefix + message))
}
