package com.ender.easyReport.api.events

import com.ender.easyReport.model.Report
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Called when a player report is created.
 * This event is cancellable.
 */
class PlayerReportEvent(val report: Report) : Event(), Cancellable {

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
