package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Service
class ConsultationStats {

    companion object {
        private val BR_ZONE = ZoneId.of("America/Sao_Paulo")
        private val LOG_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(BR_ZONE)
    }

    private val totalConsultations = AtomicLong(0)
    private val successCount = AtomicLong(0)
    private val failCount = AtomicLong(0)
    private val perType = ConcurrentHashMap<String, AtomicLong>()
    private val perTypeLabel = ConcurrentHashMap<String, String>()
    private val uniqueUsers = ConcurrentHashMap.newKeySet<String>()
    private val totalCost = AtomicReference(BigDecimal.ZERO)
    private val recentLog = ConcurrentLinkedDeque<LogEntry>()

    data class LogEntry(
        val timestamp: Instant,
        val userPhone: String,
        val tipo: String,
        val tipoLabel: String,
        val query: String,
        val success: Boolean
    ) {
        fun formatTimestamp(): String = LOG_FMT.format(timestamp)
        fun statusIcon(): String = if (success) "ok" else "err"
    }

    // ── Registrar consulta ─────────────────────────────────────────

    fun record(userPhone: String, tipo: String, tipoLabel: String, query: String, success: Boolean, custo: String?) {
        totalConsultations.incrementAndGet()
        if (success) successCount.incrementAndGet() else failCount.incrementAndGet()

        perType.computeIfAbsent(tipo) { AtomicLong(0) }.incrementAndGet()
        perTypeLabel[tipo] = tipoLabel
        uniqueUsers.add(userPhone)

        recentLog.addFirst(LogEntry(Instant.now(), userPhone, tipo, tipoLabel, query, success))
        while (recentLog.size > 50) recentLog.removeLast()

        if (custo != null) {
            try {
                val value = custo.replace("R$", "").replace(".", "").replace(",", ".").trim().toBigDecimal()
                totalCost.updateAndGet { it.add(value) }
            } catch (_: Exception) { }
        }
    }

    // ── Getters ────────────────────────────────────────────────────

    fun getTotal(): Long = totalConsultations.get()
    fun getSuccess(): Long = successCount.get()
    fun getFail(): Long = failCount.get()
    fun getUniqueUsersCount(): Int = uniqueUsers.size
    fun getTotalCost(): BigDecimal = totalCost.get()

    fun getSuccessRate(): String {
        val total = totalConsultations.get()
        if (total == 0L) return "0%"
        return "%.1f%%".format((successCount.get().toDouble() / total) * 100)
    }

    fun getTopTypes(limit: Int = 5): List<Pair<String, Long>> {
        return perType.entries
            .map { (tipo, count) -> (perTypeLabel[tipo] ?: tipo) to count.get() }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun getRecentLog(limit: Int = 10): List<LogEntry> {
        return recentLog.take(limit)
    }

    fun reset() {
        totalConsultations.set(0)
        successCount.set(0)
        failCount.set(0)
        perType.clear()
        perTypeLabel.clear()
        uniqueUsers.clear()
        totalCost.set(BigDecimal.ZERO)
        recentLog.clear()
    }
}
