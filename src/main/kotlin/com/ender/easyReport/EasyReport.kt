package com.ender.easyReport

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class Report(
    val reportedPlayerName: String,
    val reportedPlayerUUID: UUID,
    val reporterName: String,
    val reporterUUID: UUID,
    val reason: String,
    val proof: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ReportState { AWAITING_CUSTOM_REASON, AWAITING_PROOF }

data class ReportBuilder(
    val reportedPlayerName: String,
    val reportedPlayerUUID: UUID,
    var reason: String? = null,
    var state: ReportState
)

class ReportReasonsGUIHolder(val reportedPlayer: OfflinePlayer) : InventoryHolder {
    override fun getInventory(): Inventory = error("This is a holder, not the inventory itself.")
}

class ActiveReportsGUIHolder : InventoryHolder {
    override fun getInventory(): Inventory = error("This is a holder, not the inventory itself.")
}


class EasyReport : JavaPlugin(), CommandExecutor, Listener {

    private val activeReports = ArrayDeque<Report>()
    private val reportBuilders = ConcurrentHashMap<UUID, ReportBuilder>()
    private val executorService = Executors.newSingleThreadExecutor()

    private lateinit var webhookUrl: String
    private lateinit var reportReasons: List<String>
    private var staffAlertsEnabled: Boolean = true
    private lateinit var staffAlertMessage: String
    private lateinit var webhookUsername: String
    private lateinit var webhookAvatarUrl: String
    private var embedColor: Int = 16734296

    companion object {
        private const val PERM_STAFF = "easyreport.staff"
        private const val PERM_REPORTS_VIEW = "easyreport.reports.view"
        private const val PERM_REPORTS_DELETE = "easyreport.reports.delete"
        private const val PERM_RELOAD = "easyreport.reload"

        fun colorize(message: String): String {
            return ChatColor.translateAlternateColorCodes('&', message)
        }
    }

    override fun onEnable() {
        loadConfiguration()
        getCommand("report")?.setExecutor(this)
        getCommand("reports")?.setExecutor(this)
        getCommand("easyreport")?.setExecutor(this)
        server.pluginManager.registerEvents(this, this)
        logger.info("EasyReport has been enabled.")
    }

    private fun loadConfiguration() {
        saveDefaultConfig()
        reloadConfig()
        webhookUrl = config.getString("discord-webhook.url") ?: "NOT_SET"
        webhookUsername = config.getString("discord-webhook.username") ?: "Server Reports"
        webhookAvatarUrl = config.getString("discord-webhook.avatar-url") ?: ""
        embedColor = config.getInt("discord-webhook.embed.color", 16734296)
        staffAlertsEnabled = config.getBoolean("staff-alerts.enabled", true)
        staffAlertMessage = config.getString("staff-alerts.message") ?: "&c[Alert] &f%reporter% &7reported &f%reported% &7for: &e%reason%"
        reportReasons = config.getStringList("report-reasons")

        if (webhookUrl.contains("WEBHOOK_URL_HERE") || webhookUrl == "NOT_SET") {
            logger.severe("!!! Discord webhook URL is not set correctly in config.yml!")
        }
    }

    override fun onDisable() {
        executorService.shutdown()
        logger.info("EasyReport disabled.")
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        return when (cmd.name.lowercase()) {
            "report" -> handleReportCommand(sender, args)
            "reports" -> handleReportsCommand(sender)
            "easyreport" -> handleAdminCommand(sender, args)
            else -> false
        }
    }

    private fun handleAdminCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERM_RELOAD)) {
            sender.sendMessage(colorize("&cYou do not have permission to use this command."))
            return true
        }
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            loadConfiguration()
            sender.sendMessage(colorize("&aEasyReport configuration has been reloaded."))
            return true
        }
        sender.sendMessage(colorize("&cUsage: /easyreport reload"))
        return true
    }

    private fun handleReportCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(colorize("&cThis command can only be run by a player."))
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(colorize("&cUsage: /report <player>"))
            return true
        }
        if (reportBuilders.containsKey(sender.uniqueId)) {
            sender.sendMessage(colorize("&cYou are already in the process of filing a report."))
            return true
        }

        val reported = Bukkit.getOfflinePlayer(args[0])
        if (!reported.hasPlayedBefore() && !reported.isOnline) {
            sender.sendMessage(colorize("&cPlayer '${args[0]}' has never played on this server."))
            return true
        }
        if (reported.uniqueId == sender.uniqueId) {
            sender.sendMessage(colorize("&cYou cannot report yourself."))
            return true
        }
        openReportReasonsGUI(sender, reported)
        return true
    }

    private fun handleReportsCommand(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage(colorize("&cThis command can only be run by a player."))
            return true
        }
        if (!sender.hasPermission(PERM_REPORTS_VIEW)) {
            sender.sendMessage(colorize("&cYou do not have permission to view reports."))
            return true
        }
        openActiveReportsGUI(sender)
        return true
    }

    private fun openReportReasonsGUI(player: Player, reported: OfflinePlayer) {
        val holder = ReportReasonsGUIHolder(reported)
        val inv: Inventory = Bukkit.createInventory(holder, 54, colorize("&9Reporting: ${reported.name}"))
        reportReasons.forEach { reason ->
            val item = ItemStack(Material.PAPER)
            item.itemMeta = item.itemMeta?.apply {
                setDisplayName(colorize("&e$reason"))
            }
            inv.addItem(item)
        }
        player.openInventory(inv)
    }

    private fun openActiveReportsGUI(player: Player) {
        val holder = ActiveReportsGUIHolder()
        val inv: Inventory = Bukkit.createInventory(holder, 54, colorize("&cActive Reports"))

        if (activeReports.isEmpty()) {
            val noReportsItem = ItemStack(Material.BARRIER)
            noReportsItem.itemMeta = noReportsItem.itemMeta?.apply {
                setDisplayName(colorize("&7No Active Reports"))
                lore = listOf(colorize("&8It looks like there are no reports to review."))
            }
            inv.setItem(22, noReportsItem)
        } else {
            val reportsSnapshot = activeReports.toList()
            reportsSnapshot.forEach { report ->
                val skull = ItemStack(Material.PLAYER_HEAD)
                skull.itemMeta = (skull.itemMeta as SkullMeta).apply {
                    owningPlayer = Bukkit.getOfflinePlayer(report.reportedPlayerUUID)
                    setDisplayName(colorize("&eReported: &f${report.reportedPlayerName}"))
                    val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(Date(report.timestamp))
                    lore = listOf(
                        colorize("&7By: &f${report.reporterName}"),
                        colorize("&7Reason: &f${report.reason}"),
                        colorize("&7Proof: &f${report.proof}"),
                        "",
                        colorize("&8$date"),
                        "",
                        colorize("&cRight-click to delete this report.")
                    )
                }
                inv.addItem(skull)
            }
        }
        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        when (val holder = event.inventory.holder) {
            is ReportReasonsGUIHolder -> handleReportReasonClick(event, player, holder)
            is ActiveReportsGUIHolder -> handleActiveReportsClick(event, player)
        }
    }

    private fun handleReportReasonClick(event: InventoryClickEvent, player: Player, holder: ReportReasonsGUIHolder) {
        event.isCancelled = true
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type != Material.PAPER) return

        val reason = ChatColor.stripColor(clickedItem.itemMeta?.displayName ?: "Other")
        player.closeInventory()

        val reportedPlayer = holder.reportedPlayer
        val builder = ReportBuilder(reportedPlayer.name!!, reportedPlayer.uniqueId, state = ReportState.AWAITING_PROOF)

        if (reason.equals("Other", ignoreCase = true)) {
            builder.state = ReportState.AWAITING_CUSTOM_REASON
            player.sendMessage(colorize("&6Please type your custom reason for the report in chat now."))
        } else {
            builder.reason = reason
            player.sendMessage(colorize("&6Reason set to '&f$reason&6'."))
            player.sendMessage(colorize("&6Please provide proof (a link, description, or 'none')."))
        }
        reportBuilders[player.uniqueId] = builder
    }

    private fun handleActiveReportsClick(event: InventoryClickEvent, player: Player) {
        event.isCancelled = true
        if (event.click == ClickType.RIGHT) {
            if (!player.hasPermission(PERM_REPORTS_DELETE)) {
                player.sendMessage(colorize("&cYou don't have permission to delete reports."))
                return
            }

            val clickedItem = event.currentItem ?: return
            if (clickedItem.type != Material.PLAYER_HEAD) return

            val slot = event.slot
            val reportsSnapshot = activeReports.toList()
            if (slot < reportsSnapshot.size) {
                val reportToRemove = reportsSnapshot[slot]
                activeReports.remove(reportToRemove)
                event.currentItem = null
                player.sendMessage(colorize("&aReport against &f${reportToRemove.reportedPlayerName} &aremoved."))
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val builder = reportBuilders[event.player.uniqueId] ?: return
        val player = event.player
        event.isCancelled = true
        val message = event.message

        Bukkit.getScheduler().runTask(this, Runnable {
            when (builder.state) {
                ReportState.AWAITING_CUSTOM_REASON -> {
                    builder.reason = message
                    builder.state = ReportState.AWAITING_PROOF
                    player.sendMessage(colorize("&6Reason set. Now, please provide proof (a link, description, or 'none')."))
                }
                ReportState.AWAITING_PROOF -> {
                    val proof = if (message.equals("none", ignoreCase = true)) "None provided" else message
                    val report = Report(
                        builder.reportedPlayerName,
                        builder.reportedPlayerUUID,
                        player.name,
                        player.uniqueId,
                        builder.reason!!,
                        proof
                    )
                    activeReports.addFirst(report)
                    if (activeReports.size > 50) activeReports.removeLast()
                    sendDiscordWebhook(report)
                    notifyStaff(report)
                    player.sendMessage(colorize("&aThank you, your report has been submitted."))
                    reportBuilders.remove(player.uniqueId)
                }
            }
        })
    }

    private fun notifyStaff(report: Report) {
        if (!staffAlertsEnabled) return

        val message = colorize(staffAlertMessage)
            .replace("%reporter%", report.reporterName)
            .replace("%reported%", report.reportedPlayerName)
            .replace("%reason%", report.reason)

        server.onlinePlayers.filter { it.hasPermission(PERM_STAFF) }.forEach { it.sendMessage(message) }
    }

    private fun sendDiscordWebhook(report: Report) {
        if (webhookUrl.contains("WEBHOOK_URL_HERE") || webhookUrl == "NOT_SET") return

        executorService.submit {
            try {
                fun String.escapeJson() = this.replace("\\", "\\\\").replace("\"", "\\\"")

                val jsonPayload = """
                {
                  "username": "${webhookUsername.escapeJson()}",
                  "avatar_url": "${webhookAvatarUrl.escapeJson()}",
                  "embeds": [
                    {
                      "title": "New Player Report",
                      "color": $embedColor,
                      "fields": [
                        {"name": "Reported Player", "value": "${report.reportedPlayerName.escapeJson()}", "inline": true},
                        {"name": "Reported By", "value": "${report.reporterName.escapeJson()}", "inline": true},
                        {"name": "Reason", "value": "${report.reason.escapeJson()}", "inline": false},
                        {"name": "Proof", "value": "${report.proof.escapeJson()}", "inline": false}
                      ],
                      "footer": {"text": "Reports made easy."}
                    }
                  ]
                }
                """.trimIndent()

                (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "EasyReportPlugin")
                    OutputStreamWriter(outputStream, "UTF-8").use { it.write(jsonPayload) }
                    inputStream.close()
                    disconnect()
                }
            } catch (e: Exception) {
                logger.severe("Error sending Discord webhook: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
