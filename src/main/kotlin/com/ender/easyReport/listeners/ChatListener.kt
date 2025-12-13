package com.ender.easyReport.listeners

import com.ender.easyReport.EasyReport
import com.ender.easyReport.api.events.PlayerReportEvent
import com.ender.easyReport.config.Settings
import com.ender.easyReport.model.Report
import com.ender.easyReport.model.ReportState
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import io.papermc.paper.event.player.AsyncChatEvent

class ChatListener(private val plugin: EasyReport) : Listener {

    private val mm = MiniMessage.miniMessage()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val builder = plugin.reportManager.reportBuilders[player.uniqueId] ?: return
        event.isCancelled = true
        val message = LegacyComponentSerializer.legacySection().serialize(event.originalMessage())

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (builder.state == ReportState.AWAITING_PROOF) {
                val proof = if (message.equals("none", true)) "None provided" else message
                val report = Report(
                    reportedPlayer = builder.reportedPlayer,
                    reporterName = player.name,
                    reason = builder.reason,
                    proof = proof,
                    location = builder.location
                )

                val reportEvent = PlayerReportEvent(report)
                Bukkit.getPluginManager().callEvent(reportEvent)

                if (reportEvent.isCancelled) {
                    plugin.reportManager.reportBuilders.remove(player.uniqueId)
                    return@Runnable
                }

                plugin.reportManager.addReport(report)
                plugin.discordManager.sendDiscordWebhook(report)
                notifyStaff(report)

                player.sendMessage(mm.deserialize(Settings.prefix + Settings.msgReportSubmitted))
                plugin.reportManager.reportBuilders.remove(player.uniqueId)
            }
        })
    }

    private fun notifyStaff(report: Report) {
        if (!Settings.staffAlertsEnabled) return
        val msg = mm.deserialize(
            Settings.staffAlertMessage
                .replace("%reporter%", report.reporterName)
                .replace("%reported%", report.reportedPlayer.name ?: "Unknown")
                .replace("%reason%", report.reason)
        )
        plugin.server.onlinePlayers.filter { it.hasPermission("easyreport.alerts") }.forEach { it.sendMessage(msg) }
    }
}
