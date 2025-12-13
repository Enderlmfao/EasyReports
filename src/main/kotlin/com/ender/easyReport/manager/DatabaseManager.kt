package com.ender.easyReport.manager

import com.ender.easyReport.EasyReport
import com.ender.easyReport.model.Report
import org.bukkit.Bukkit
import org.bukkit.Location
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Level

class DatabaseManager(private val plugin: EasyReport) {

    private var connection: Connection? = null

    init {
        try {
            val dbFile = File(plugin.dataFolder, "reports.db")
            if (!dbFile.exists()) {
                dbFile.parentFile.mkdirs()
                dbFile.createNewFile()
            }
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            connection = DriverManager.getConnection(url)
            createTables()
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to connect to SQLite database", e)
        }
    }

    private fun createTables() {
        val reportsSql = """
            CREATE TABLE IF NOT EXISTS reports (
                id TEXT PRIMARY KEY,
                reported_player_uuid TEXT NOT NULL,
                reported_player_name TEXT NOT NULL,
                reporter_name TEXT NOT NULL,
                reason TEXT NOT NULL,
                proof TEXT NOT NULL,
                world TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                yaw REAL NOT NULL,
                pitch REAL NOT NULL,
                timestamp INTEGER NOT NULL
            );
        """
        val offensesSql = """
            CREATE TABLE IF NOT EXISTS offenses (
                player_uuid TEXT PRIMARY KEY,
                offense_count INTEGER NOT NULL
            );
        """
        connection?.createStatement()?.use {
            it.execute(reportsSql)
            it.execute(offensesSql)
        }
    }

    fun addReport(report: Report) {
        val sql = """
            INSERT INTO reports(id, reported_player_uuid, reported_player_name, reporter_name, reason, proof, world, x, y, z, yaw, pitch, timestamp)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?);
        """
        connection?.prepareStatement(sql)?.use {
            it.setString(1, report.id.toString())
            it.setString(2, report.reportedPlayer.uniqueId.toString())
            it.setString(3, report.reportedPlayer.name)
            it.setString(4, report.reporterName)
            it.setString(5, report.reason)
            it.setString(6, report.proof)
            it.setString(7, report.location.world.name)
            it.setDouble(8, report.location.x)
            it.setDouble(9, report.location.y)
            it.setDouble(10, report.location.z)
            it.setFloat(11, report.location.yaw)
            it.setFloat(12, report.location.pitch)
            it.setLong(13, report.timestamp)
            it.executeUpdate()
        }
    }

    fun getReports(): List<Report> {
        val reports = mutableListOf<Report>()
        val sql = "SELECT * FROM reports ORDER BY timestamp DESC;"
        connection?.createStatement()?.use { stmt ->
            val rs = stmt.executeQuery(sql)
            while (rs.next()) {
                val world = Bukkit.getWorld(rs.getString("world"))
                if (world != null) {
                    val location = Location(
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                    )
                    val reportedPlayer = Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("reported_player_uuid")))
                    reports.add(
                        Report(
                            id = UUID.fromString(rs.getString("id")),
                            reportedPlayer = reportedPlayer,
                            reporterName = rs.getString("reporter_name"),
                            reason = rs.getString("reason"),
                            proof = rs.getString("proof"),
                            location = location,
                            timestamp = rs.getLong("timestamp")
                        )
                    )
                }
            }
        }
        return reports
    }

    fun removeReport(id: UUID) {
        val sql = "DELETE FROM reports WHERE id = ?;"
        connection?.prepareStatement(sql)?.use {
            it.setString(1, id.toString())
            it.executeUpdate()
        }
    }

    fun clearReports() {
        val sql = "DELETE FROM reports;"
        connection?.createStatement()?.use { it.execute(sql) }
    }

    fun getOffenses(): Map<UUID, Int> {
        val offenses = mutableMapOf<UUID, Int>()
        val sql = "SELECT * FROM offenses;"
        connection?.createStatement()?.use { stmt ->
            val rs = stmt.executeQuery(sql)
            while (rs.next()) {
                val uuid = UUID.fromString(rs.getString("player_uuid"))
                val count = rs.getInt("offense_count")
                offenses[uuid] = count
            }
        }
        return offenses
    }

    fun saveOffense(uuid: UUID, count: Int) {
        val sql = "INSERT OR REPLACE INTO offenses (player_uuid, offense_count) VALUES (?, ?);"
        connection?.prepareStatement(sql)?.use {
            it.setString(1, uuid.toString())
            it.setInt(2, count)
            it.executeUpdate()
        }
    }

    fun close() {
        connection?.close()
    }
}
