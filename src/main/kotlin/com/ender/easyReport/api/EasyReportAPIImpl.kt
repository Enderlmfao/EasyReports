package com.ender.easyReport.api

import com.ender.easyReport.EasyReport
import com.ender.easyReport.model.Report
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID

class EasyReportAPIImpl(private val plugin: EasyReport) : EasyReportAPI {

    override fun createReport(reporter: Player, reported: OfflinePlayer, reason: String, proof: String, location: Location): Report {
        val report = Report(
            reportedPlayer = reported,
            reporterName = reporter.name,
            reason = reason,
            proof = proof,
            location = location
        )
        plugin.reportManager.addReport(report)
        plugin.discordManager.sendDiscordWebhook(report)
        // Note: Staff notification is handled internally when a report is created via commands.
        // To trigger it here, you might need to expose that functionality or fire an event.
        return report
    }

    override fun getReport(id: UUID): Report? {
        return plugin.reportManager.findReportById(id.toString())
    }

    override fun getActiveReports(): List<Report> {
        return plugin.reportManager.getReports()
    }

    override fun removeReport(report: Report) {
        plugin.reportManager.removeReport(report)
    }

    override fun getOffenseCount(playerUuid: UUID): Int {
        return plugin.punishmentManager.getOffenseCount(playerUuid)
    }

    override fun warnPlayer(player: Player, reason: String) {
        plugin.punishmentManager.issueWarn(player, reason)
    }

    override fun kickPlayer(player: Player, reason: String) {
        plugin.punishmentManager.issueKick(player, reason)
    }

    override fun mutePlayer(player: Player, reason: String) {
        plugin.punishmentManager.issueMute(player, reason)
    }

    override fun banPlayer(player: Player, reason: String) {
        plugin.punishmentManager.issueBan(player, reason)
    }
}
