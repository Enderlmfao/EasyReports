package com.ender.easyReport.listeners

import com.ender.easyReport.EasyReport
import com.ender.easyReport.api.events.PunishmentAction
import com.ender.easyReport.api.events.ReportHandleEvent
import com.ender.easyReport.config.Settings
import com.ender.easyReport.model.Report
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import java.text.SimpleDateFormat
import java.util.*

class InventoryListener(private val plugin: EasyReport) : Listener {

    val mm = MiniMessage.miniMessage()
    private val reportIdKey = NamespacedKey(plugin, "report-id")

    class ReportsGuiHolder(val page: Int) : InventoryHolder {
        override fun getInventory(): Inventory {
            throw UnsupportedOperationException("This holder is just for data; the inventory is created and managed in the listener")
        }
    }

    class PunishmentGuiHolder(val report: Report) : InventoryHolder {
        override fun getInventory(): Inventory {
            throw UnsupportedOperationException("This holder is just for data; the inventory is created and managed in the listener")
        }
    }

    fun openActiveReportsGUI(player: Player, page: Int = 0) {
        val reports = plugin.reportManager.getReports()
        val holder = ReportsGuiHolder(page)
        val inv = Bukkit.createInventory(holder, 54, mm.deserialize(Settings.reportsListTitle))

        // Create border
        val borderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        borderItem.itemMeta = borderItem.itemMeta?.apply { displayName(mm.deserialize(" ")) }
        for (i in 0..8) inv.setItem(i, borderItem) // Top
        for (i in 45..53) inv.setItem(i, borderItem) // Bottom
        for (i in 9..44 step 9) inv.setItem(i, borderItem) // Left
        for (i in 17..53 step 9) inv.setItem(i, borderItem) // Right

        // Close button
        val closeItem = ItemStack(Material.BARRIER)
        closeItem.itemMeta = closeItem.itemMeta?.apply { displayName(mm.deserialize("<red><bold>Close")) }
        inv.setItem(49, closeItem)

        val reportsPerPage = 28 // 4 rows of 7 inner slots
        val startIndex = page * reportsPerPage
        val endIndex = minOf(startIndex + reportsPerPage, reports.size)
        val pageReports = if (startIndex < reports.size) reports.subList(startIndex, endIndex) else emptyList()

        if (pageReports.isEmpty() && page == 0) {
            val noItem = ItemStack(Settings.noReportsItemMaterial)
            noItem.itemMeta = noItem.itemMeta?.apply {
                displayName(mm.deserialize(Settings.noReportsItemName))
                lore(Settings.noReportsItemLore.map { mm.deserialize(it) })
            }
            inv.setItem(22, noItem)
        } else {
            // Pagination buttons
            if (page > 0) {
                val prevItem = ItemStack(Material.ARROW)
                prevItem.itemMeta = prevItem.itemMeta?.apply { displayName(mm.deserialize("<yellow><bold>Previous Page")) }
                inv.setItem(48, prevItem)
            }
            if (endIndex < reports.size) {
                val nextItem = ItemStack(Material.ARROW)
                nextItem.itemMeta = nextItem.itemMeta?.apply { displayName(mm.deserialize("<yellow><bold>Next Page")) }
                inv.setItem(50, nextItem)
            }

            // Add report items to inner slots
            val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss z")
            var slot = 10
            for (report in pageReports) {
                while (slot % 9 == 0 || slot % 9 == 8) { // Skip border slots
                    slot++
                }
                if (slot >= 45) break // Stop before bottom border

                val skull = ItemStack(Material.PLAYER_HEAD)
                val meta = skull.itemMeta as SkullMeta
                meta.owningPlayer = report.reportedPlayer
                meta.displayName(mm.deserialize(Settings.reportItemName.replace("%player%", report.reportedPlayer.name ?: "Unknown")))
                val locationStr = with(report.location) { "${world?.name} @ ${blockX}, ${blockY}, ${blockZ}" }
                meta.lore(Settings.reportItemLore.map {
                    mm.deserialize(
                        it.replace("%reporter%", report.reporterName)
                            .replace("%reason%", report.reason)
                            .replace("%location%", locationStr)
                            .replace("%date%", format.format(Date(report.timestamp)))
                    )
                })
                meta.persistentDataContainer.set(reportIdKey, PersistentDataType.STRING, report.id.toString())
                skull.itemMeta = meta
                inv.setItem(slot, skull)
                slot++
            }
        }
        player.openInventory(inv)
    }

    fun openPunishmentGUI(player: Player, report: Report) {
        val holder = PunishmentGuiHolder(report)
        val inv = Bukkit.createInventory(holder, 27, mm.deserialize("<dark_red>Punish: ${report.reportedPlayer.name}"))

        val warnItem = ItemStack(Material.YELLOW_WOOL)
        warnItem.itemMeta = warnItem.itemMeta?.apply { displayName(mm.deserialize("<yellow>Warn")) }
        inv.setItem(10, warnItem)

        val kickItem = ItemStack(Material.ORANGE_WOOL)
        kickItem.itemMeta = kickItem.itemMeta?.apply { displayName(mm.deserialize("<gold>Kick")) }
        inv.setItem(12, kickItem)

        val muteItem = ItemStack(Material.RED_WOOL)
        muteItem.itemMeta = muteItem.itemMeta?.apply { displayName(mm.deserialize("<red>Mute")) }
        inv.setItem(14, muteItem)

        val banItem = ItemStack(Material.BARRIER)
        banItem.itemMeta = banItem.itemMeta?.apply { displayName(mm.deserialize("<dark_red>Ban")) }
        inv.setItem(16, banItem)

        val dismissItem = ItemStack(Material.GREEN_WOOL)
        dismissItem.itemMeta = dismissItem.itemMeta?.apply { displayName(mm.deserialize("<green>Dismiss")) }
        inv.setItem(22, dismissItem)

        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        when (val holder = event.inventory.holder) {
            is ReportsGuiHolder -> handleReportsGuiClick(event, player, holder)
            is PunishmentGuiHolder -> handlePunishmentGuiClick(event, player, holder)
        }
    }

    private fun handleReportsGuiClick(event: InventoryClickEvent, player: Player, holder: ReportsGuiHolder) {
        event.isCancelled = true
        val item = event.currentItem ?: return
        val displayName = LegacyComponentSerializer.legacySection().serialize(item.itemMeta?.displayName() ?: return)

        when (item.type) {
            Material.PLAYER_HEAD -> {
                val reportIdStr = item.itemMeta.persistentDataContainer.get(reportIdKey, PersistentDataType.STRING) ?: return
                val report = plugin.reportManager.findReportById(reportIdStr) ?: return
                openPunishmentGUI(player, report)
            }
            Material.ARROW -> {
                if (displayName.contains("Next Page")) {
                    openActiveReportsGUI(player, holder.page + 1)
                } else if (displayName.contains("Previous Page")) {
                    openActiveReportsGUI(player, holder.page - 1)
                }
            }
            Material.BARRIER -> {
                if (displayName.contains("Close")) {
                    player.closeInventory()
                }
            }
            else -> {}
        }
    }

    private fun handlePunishmentGuiClick(event: InventoryClickEvent, player: Player, holder: PunishmentGuiHolder) {
        event.isCancelled = true
        val item = event.currentItem ?: return
        val reportedPlayer = holder.report.reportedPlayer.player ?: run {
            player.sendMessage(mm.deserialize("<red>The reported player is not online."))
            player.closeInventory()
            return
        }

        val reason = holder.report.reason
        val action = when (item.type) {
            Material.YELLOW_WOOL -> PunishmentAction.WARN
            Material.ORANGE_WOOL -> PunishmentAction.KICK
            Material.RED_WOOL -> PunishmentAction.MUTE
            Material.BARRIER -> PunishmentAction.BAN
            Material.GREEN_WOOL -> PunishmentAction.DISMISS
            else -> return
        }

        val handleEvent = ReportHandleEvent(player, holder.report, action)
        Bukkit.getPluginManager().callEvent(handleEvent)

        if (handleEvent.isCancelled) {
            return
        }

        when (action) {
            PunishmentAction.WARN -> plugin.punishmentManager.issueWarn(reportedPlayer, reason)
            PunishmentAction.KICK -> plugin.punishmentManager.issueKick(reportedPlayer, reason)
            PunishmentAction.MUTE -> plugin.punishmentManager.issueMute(reportedPlayer, reason)
            PunishmentAction.BAN -> plugin.punishmentManager.issueBan(reportedPlayer, reason)
            PunishmentAction.DISMISS -> { /* Do nothing */ }
        }

        plugin.reportManager.removeReport(holder.report)
        player.closeInventory()
        player.sendMessage(mm.deserialize("<green>Report for ${reportedPlayer.name} has been handled."))
    }
}
