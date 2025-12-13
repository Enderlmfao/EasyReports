package com.ender.easyReport.model

import org.bukkit.Location
import org.bukkit.OfflinePlayer
import java.util.UUID

data class Report(
    val id: UUID = UUID.randomUUID(),
    val reportedPlayer: OfflinePlayer,
    val reporterName: String,
    val reason: String,
    val proof: String,
    val location: Location,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ReportState { AWAITING_PROOF }

data class ReportBuilder(
    val reportedPlayer: OfflinePlayer,
    val reason: String,
    val location: Location,
    var state: ReportState = ReportState.AWAITING_PROOF
)
