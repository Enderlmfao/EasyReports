package com.ender.easyReport.api.events

import com.ender.easyReport.model.Report
import org.bukkit.command.CommandSender
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Called when a staff member handles a report.
 * This event is cancellable.
 */
class ReportHandleEvent(
    val handler: CommandSender,
    val report: Report,
    val action: PunishmentAction
) : Event(), Cancellable {

    private var isCancelled = false

    override fun isCancelled(): Boolean {
        return isCancelled
    }

    override fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

enum class PunishmentAction {
    WARN, KICK, MUTE, BAN, DISMISS
}
