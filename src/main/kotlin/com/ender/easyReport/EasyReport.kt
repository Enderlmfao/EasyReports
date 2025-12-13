package com.ender.easyReport

import com.ender.easyReport.api.EasyReportAPI
import com.ender.easyReport.api.EasyReportAPIImpl
import com.ender.easyReport.commands.CommandManager
import com.ender.easyReport.config.Settings
import com.ender.easyReport.listeners.ChatListener
import com.ender.easyReport.listeners.InventoryListener
import com.ender.easyReport.manager.AIManager
import com.ender.easyReport.manager.DatabaseManager
import com.ender.easyReport.manager.DiscordManager
import com.ender.easyReport.manager.PunishmentManager
import com.ender.easyReport.manager.ReportManager
import org.bukkit.plugin.java.JavaPlugin

class EasyReport : JavaPlugin() {

    lateinit var databaseManager: DatabaseManager
    lateinit var reportManager: ReportManager
    lateinit var discordManager: DiscordManager
    lateinit var punishmentManager: PunishmentManager
    lateinit var aiManager: AIManager
    lateinit var commandManager: CommandManager
    lateinit var inventoryListener: InventoryListener

    companion object {
        private var api: EasyReportAPI? = null

        /**
         * Gets the EasyReport API instance.
         *
         * @return The API instance, or null if the plugin is not enabled.
         */
        @JvmStatic
        fun getAPI(): EasyReportAPI? {
            return api
        }
    }

    override fun onEnable() {
        try {
            loadConfiguration()

            databaseManager = DatabaseManager(this)
            reportManager = ReportManager(this)
            discordManager = DiscordManager(this)
            punishmentManager = PunishmentManager(this)
            aiManager = AIManager(this)
            commandManager = CommandManager(this)
            inventoryListener = InventoryListener(this)
            api = EasyReportAPIImpl(this)

            reportManager.loadReports()
            punishmentManager.loadOffenses()

            getCommand("report")?.setExecutor(commandManager)
            getCommand("reports")?.setExecutor(commandManager)
            getCommand("easyreport")?.setExecutor(commandManager)
            getCommand("easyreport")?.tabCompleter = commandManager
            getCommand("reports")?.tabCompleter = commandManager

            server.pluginManager.registerEvents(inventoryListener, this)
            server.pluginManager.registerEvents(ChatListener(this), this)

            logger.info("EasyReport v${description.version} has been successfully enabled.")
        } catch (e: Exception) {
            logger.severe("Error occurred while enabling EasyReport v${description.version}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        api = null
        if (::discordManager.isInitialized) {
            discordManager.shutdown()
        }
        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }
        logger.info("EasyReport has been disabled.")
    }

    fun loadConfiguration() {
        Settings.load(this)
    }
}
