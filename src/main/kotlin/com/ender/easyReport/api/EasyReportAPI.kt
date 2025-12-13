package com.ender.easyReport.api

import com.ender.easyReport.model.Report
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID

interface EasyReportAPI {

    /**
     * Creates and submits a new report.
     *
     * @param reporter The player creating the report.
     * @param reported The player being reported.
     * @param reason The reason for the report.
     * @param proof Optional proof (e.g., a URL).
     * @param location The location where the report was created.
     * @return The created Report object.
     */
    fun createReport(reporter: Player, reported: OfflinePlayer, reason: String, proof: String, location: Location): Report

    /**
     * Retrieves a report by its unique ID.
     *
     * @param id The UUID of the report.
     * @return The Report object, or null if not found.
     */
    fun getReport(id: UUID): Report?

    /**
     * Retrieves all currently active reports.
     *
     * @return A list of active reports.
     */
    fun getActiveReports(): List<Report>

    /**
     * Removes a report from the system (e.g., after it has been handled).
     *
     * @param report The report to remove.
     */
    fun removeReport(report: Report)

    /**
     * Gets the number of offenses for a specific player.
     *
     * @param playerUuid The UUID of the player.
     * @return The number of offenses.
     */
    fun getOffenseCount(playerUuid: UUID): Int

    /**
     * Issues a warning to a player.
     *
     * @param player The player to warn.
     * @param reason The reason for the warning.
     */
    fun warnPlayer(player: Player, reason: String)

    /**
     * Kicks a player from the server.
     *
     * @param player The player to kick.
     * @param reason The reason for the kick.
     */
    fun kickPlayer(player: Player, reason: String)

    /**
     * Mutes a player. The duration scales based on their offense history.
     *
     * @param player The player to mute.
     * @param reason The reason for the mute.
     */
    fun mutePlayer(player: Player, reason: String)

    /**
     * Bans a player. The duration scales based on their offense history.
     *
     * @param player The player to ban.
     * @param reason The reason for the ban.
     */
    fun banPlayer(player: Player, reason: String)
}
