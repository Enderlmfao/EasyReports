package com.ender.easyReport.manager

import com.ender.easyReport.EasyReport
import com.ender.easyReport.config.Settings
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PunishmentManager(private val plugin: EasyReport) {

    private val offenseHistory = ConcurrentHashMap<UUID, Int>()

    fun loadOffenses() {
        offenseHistory.clear()
        offenseHistory.putAll(plugin.databaseManager.getOffenses())
    }

    fun getOffenseCount(uuid: UUID): Int {
        return offenseHistory.getOrDefault(uuid, 0)
    }

    private fun incrementOffenses(uuid: UUID) {
        val newCount = getOffenseCount(uuid) + 1
        offenseHistory[uuid] = newCount
        plugin.databaseManager.saveOffense(uuid, newCount)
    }

    private fun executePunishment(type: String, player: Player, reason: String) {
        incrementOffenses(player.uniqueId)
        val offenseCount = getOffenseCount(player.uniqueId)
        val command = Settings.punishmentCommands[type] ?: return

        val duration = Settings.punishmentDurations[type]?.let {
            it[offenseCount] ?: it[-1] // -1 is the default key
        } ?: ""

        val formattedCommand = command
            .replace("%player%", player.name)
            .replace("%reason%", reason)
            .replace("%duration%", duration)
            .replace("%offense%", offenseCount.toString())

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand)
    }

    fun issueWarn(player: Player, reason: String) {
        executePunishment("warn", player, reason)
    }

    fun issueKick(player: Player, reason: String) {
        // Kick doesn't use the generic executePunishment because it's not a command
        incrementOffenses(player.uniqueId)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            player.kick(plugin.inventoryListener.mm.deserialize("<red>You have been kicked!\n<white>Reason: $reason"))
        })
    }

    fun issueMute(player: Player, reason: String) {
        executePunishment("mute", player, reason)
    }

    fun issueBan(player: Player, reason: String) {
        executePunishment("ban", player, reason)
    }
}
