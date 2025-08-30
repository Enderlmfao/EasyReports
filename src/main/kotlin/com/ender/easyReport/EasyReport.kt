package com.ender.easyReport

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.io.BufferedReader
import java.io.InputStreamReader
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

class EasyReport : JavaPlugin(), CommandExecutor, Listener {

    private val activeReports = LinkedList<Report>()
    private val reportBuilders = ConcurrentHashMap<UUID, ReportBuilder>()
    private val executorService = Executors.newSingleThreadExecutor()

    private lateinit var webhookUrl: String
    private lateinit var reportReasons: List<String>
    private var staffAlertsEnabled: Boolean = true
    private lateinit var staffAlertMessage: String
    private lateinit var webhookUsername: String
    private lateinit var webhookAvatarUrl: String
    private var embedColor: Int = 15158332

    override fun onEnable() {
        loadConfiguration()
        getCommand("report")?.setExecutor(this)
        getCommand("reports")?.setExecutor(this)
        getCommand("easyreport")?.setExecutor(this)
        server.pluginManager.registerEvents(this, this)
        logger.info("EasyReport has been successfully enabled.")
    }

    private fun loadConfiguration() {
        saveDefaultConfig()
        reloadConfig()

        webhookUrl = config.getString("discord-webhook-url") ?: "NOT_SET"
        reportReasons = config.getStringList("report-reasons")
        staffAlertsEnabled = config.getBoolean("staff-alerts.enabled", true)
        staffAlertMessage = config.getString("staff-alerts.message", "&c[Alert] &f%reporter% &7reported &f%reported% &7for: &e%reason%")!!
        webhookUsername = config.getString("webhook-settings.username", "Server Reports")!!
        webhookAvatarUrl = config.getString("webhook-settings.avatar-url", "")!!
        embedColor = config.getInt("embed-settings.color", 15158332)

        if (webhookUrl.contains("WEBHOOK_URL_HERE") || webhookUrl == "NOT_SET") {
            logger.severe("!!! Discord webhook URL is not set correctly in config.yml!")
        }
    }

    override fun onDisable() {
        executorService.shutdown()
        logger.info("EasyReport has been disabled.")
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (cmd.name.equals("report", ignoreCase = true)) return handleReportCommand(sender, args)
        if (cmd.name.equals("reports", ignoreCase = true)) return handleReportsCommand(sender)
        if (cmd.name.equals("easyreport", ignoreCase = true)) return handleAdminCommand(sender, args)
        return false
    }

    private fun handleAdminCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            loadConfiguration()
            sender.sendMessage("${ChatColor.GREEN}EasyReport configuration has been reloaded.")
            return true
        }
        sender.sendMessage("${ChatColor.RED}Usage: /easyreport reload")
        return true
    }

    private fun handleReportCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be run by a player.")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}Usage: /report <player>")
            return true
        }
        if (reportBuilders.containsKey(sender.uniqueId)) {
            sender.sendMessage("${ChatColor.RED}You are already in the process of filing a report.")
            return true
        }
        val reportedOfflinePlayer = Bukkit.getOfflinePlayer(args[0])
        if (!reportedOfflinePlayer.hasPlayedBefore() && !reportedOfflinePlayer.isOnline) {
            sender.sendMessage("${ChatColor.RED}Player '${args[0]}' has never played on this server.")
            return true
        }
        if (reportedOfflinePlayer.uniqueId == sender.uniqueId) {
            sender.sendMessage("${ChatColor.RED}You cannot report yourself.")
            return true
        }
        openReportReasonsGUI(sender, reportedOfflinePlayer.name!!, reportedOfflinePlayer.uniqueId)
        return true
    }

    private fun handleReportsCommand(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be run by a player.")
            return true
        }
        openActiveReportsGUI(sender)
        return true
    }

    private fun openReportReasonsGUI(player: Player, reportedName: String, reportedUUID: UUID) {
        val inv = Bukkit.createInventory(null, 54, "${ChatColor.BLUE}Reporting: $reportedName")
        reportReasons.forEach { reason ->
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta!!
            meta.setDisplayName("${ChatColor.YELLOW}$reason")
            item.itemMeta = meta
            inv.addItem(item)
        }
        player.openInventory(inv)
    }

    private fun openActiveReportsGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, "${ChatColor.DARK_RED}Active Reports")
        if (activeReports.isEmpty()) {
            val noReportsItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
            val meta = noReportsItem.itemMeta!!
            meta.setDisplayName("${ChatColor.GRAY}No Active Reports")
            meta.lore = listOf("${ChatColor.DARK_GRAY}It looks like there are no reports to review.")
            noReportsItem.itemMeta = meta
            inv.setItem(22, noReportsItem)
        } else {
            activeReports.forEach { report ->
                val skull = ItemStack(Material.PLAYER_HEAD)
                val meta = skull.itemMeta as SkullMeta
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(report.reportedPlayerUUID))
                meta.setDisplayName("${ChatColor.YELLOW}Reported: ${ChatColor.WHITE}${report.reportedPlayerName}")
                val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(Date(report.timestamp))
                meta.lore = listOf(
                    "${ChatColor.GRAY}By: ${ChatColor.WHITE}${report.reporterName}",
                    "${ChatColor.GRAY}Reason: ${ChatColor.WHITE}${report.reason}",
                    "${ChatColor.GRAY}Proof: ${ChatColor.WHITE}${report.proof}",
                    "",
                    "${ChatColor.AQUA}Right-click to delete this report.",
                    "${ChatColor.DARK_GRAY}$date"
                )
                skull.itemMeta = meta
                inv.addItem(skull)
            }
        }
        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val viewTitle = event.view.title

        if (viewTitle.startsWith("${ChatColor.BLUE}Reporting:")) {
            event.isCancelled = true
            val clickedItem = event.currentItem ?: return
            if (clickedItem.type != Material.PAPER) return

            val reportedPlayerName = viewTitle.substringAfter("Reporting: ").trim()
            val reportedOfflinePlayer = Bukkit.getOfflinePlayer(reportedPlayerName)
            val reason = ChatColor.stripColor(clickedItem.itemMeta.displayName) ?: "Other"

            player.closeInventory()

            val builder = ReportBuilder(reportedOfflinePlayer.name!!, reportedOfflinePlayer.uniqueId, state = ReportState.AWAITING_PROOF)
            if (reason.equals("Other", ignoreCase = true)) {
                builder.state = ReportState.AWAITING_CUSTOM_REASON
                player.sendMessage("${ChatColor.GOLD}Please type your custom reason for the report in chat now.")
            } else {
                builder.reason = reason
                player.sendMessage("${ChatColor.GOLD}Reason set to '${ChatColor.WHITE}$reason${ChatColor.GOLD}'.")
                player.sendMessage("${ChatColor.GOLD}Please provide proof (e.g., a link) in chat. Type ${ChatColor.YELLOW}none${ChatColor.GOLD} if you have no proof.")
            }
            reportBuilders[player.uniqueId] = builder
            return
        }

        if (viewTitle == "${ChatColor.DARK_RED}Active Reports") {
            event.isCancelled = true
            if (event.isRightClick) {
                val clickedSlot = event.slot
                if (clickedSlot >= 0 && clickedSlot < activeReports.size) {
                    val removedReport = activeReports.removeAt(clickedSlot)
                    player.sendMessage("${ChatColor.GREEN}Successfully deleted the report for ${ChatColor.YELLOW}${removedReport.reportedPlayerName}${ChatColor.GREEN}.")
                    openActiveReportsGUI(player)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val builder = reportBuilders[event.player.uniqueId] ?: return
        event.isCancelled = true
        val message = event.message

        Bukkit.getScheduler().runTask(this, Runnable {
            val player = event.player
            when (builder.state) {
                ReportState.AWAITING_CUSTOM_REASON -> {
                    builder.reason = message
                    builder.state = ReportState.AWAITING_PROOF
                    player.sendMessage("${ChatColor.GOLD}Reason set. Now, please provide proof (e.g., a link) in chat.")
                    player.sendMessage("${ChatColor.GOLD}Type ${ChatColor.YELLOW}none${ChatColor.GOLD} if you have no proof.")
                }
                ReportState.AWAITING_PROOF -> {
                    val proof = if (message.equals("none", ignoreCase = true)) "None provided" else message
                    val report = Report(builder.reportedPlayerName, builder.reportedPlayerUUID, player.name, player.uniqueId, builder.reason!!, proof)

                    activeReports.addFirst(report)
                    if (activeReports.size > 28) activeReports.removeLast()

                    sendDiscordWebhook(report)
                    notifyStaff(report)

                    player.sendMessage("${ChatColor.GREEN}Thank you, your report has been submitted.")
                    reportBuilders.remove(player.uniqueId)
                }
            }
        })
    }

    private fun notifyStaff(report: Report) {
        if (!staffAlertsEnabled) return

        val message = ChatColor.translateAlternateColorCodes('&', staffAlertMessage)
            .replace("%reporter%", report.reporterName)
            .replace("%reported%", report.reportedPlayerName)
            .replace("%reason%", report.reason)

        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.hasPermission("quickreport.staff")) {
                player.sendMessage(message)
            }
        }
    }

    private fun sendDiscordWebhook(report: Report) {
        if (webhookUrl.contains("WEBHOOK_URL_HERE") || webhookUrl == "NOT_SET") return
        executorService.submit {
            try {
                fun String.escape() = this.replace("\"", "\\\"")

                val json = """
                {
                  "username": "${webhookUsername.escape()}",
                  "avatar_url": "${webhookAvatarUrl.escape()}",
                  "embeds": [
                    {
                      "title": "New Player Report",
                      "color": $embedColor,
                      "fields": [
                        { "name": "Reported Player", "value": "${report.reportedPlayerName.escape()}", "inline": true },
                        { "name": "Reported By", "value": "${report.reporterName.escape()}", "inline": true },
                        { "name": "Reason", "value": "${report.reason.escape()}", "inline": false },
                        { "name": "Proof", "value": "${report.proof.escape()}", "inline": false }
                      ],
                      "footer": { "text": "EasyReport | ${server.name}" }
                    }
                  ]
                }
                """.trimIndent()

                val url = URL(webhookUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "EasyReportPlugin")

                OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(json) }

                val responseCode = connection.responseCode
                if (responseCode >= 300) {
                    logger.warning("Discord API responded with code $responseCode. This is an error.")
                    BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                        val errorResponse = reader.readLines().joinToString("\n")
                        logger.warning("Discord Error Details: $errorResponse")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                logger.severe("An error occurred while sending a Discord webhook.")
                e.printStackTrace()
            }
        }
    }
}