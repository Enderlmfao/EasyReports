package com.ender.easyReport.manager

import com.ender.easyReport.EasyReport
import com.ender.easyReport.model.Report
import com.ender.easyReport.model.ReportBuilder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ReportManager(private val plugin: EasyReport) {

    val reportBuilders = ConcurrentHashMap<UUID, ReportBuilder>()
    private val activeReports = mutableListOf<Report>()

    fun loadReports() {
        activeReports.clear()
        activeReports.addAll(plugin.databaseManager.getReports())
    }

    fun getReports(): List<Report> {
        return activeReports.toList()
    }

    fun addReport(report: Report) {
        plugin.databaseManager.addReport(report)
        activeReports.add(0, report)
    }

    fun removeReport(report: Report) {
        plugin.databaseManager.removeReport(report.id)
        activeReports.remove(report)
    }

    fun clearReports() {
        plugin.databaseManager.clearReports()
        activeReports.clear()
    }

    fun findReportById(id: String): Report? {
        val uuid = try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
        return activeReports.find { it.id == uuid }
    }
}
