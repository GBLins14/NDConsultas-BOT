package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Service
class PaymentStats {

    companion object {
        private val BR_ZONE = ZoneId.of("America/Sao_Paulo")
        private val LOG_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(BR_ZONE)
    }

    private val totalPayments = AtomicLong(0)
    private val totalRevenue = AtomicReference(BigDecimal.ZERO)
    private val recentPayments = ConcurrentLinkedDeque<PaymentLog>()

    data class PaymentLog(
        val timestamp: Instant,
        val userPhone: String,
        val tipo: String,
        val tipoLabel: String,
        val amount: BigDecimal,
        val paymentId: String
    ) {
        fun formatTimestamp(): String = LOG_FMT.format(timestamp)
    }

    fun record(userPhone: String, tipo: String, tipoLabel: String, amount: BigDecimal, paymentId: String = "manual") {
        totalPayments.incrementAndGet()
        totalRevenue.updateAndGet { it.add(amount) }

        recentPayments.addFirst(PaymentLog(Instant.now(), userPhone, tipo, tipoLabel, amount, paymentId))
        while (recentPayments.size > 50) recentPayments.removeLast()
    }

    fun getTotalPayments(): Long = totalPayments.get()
    fun getTotalRevenue(): BigDecimal = totalRevenue.get()
    fun getRecentPayments(limit: Int = 10): List<PaymentLog> = recentPayments.take(limit)

    fun reset() {
        totalPayments.set(0)
        totalRevenue.set(BigDecimal.ZERO)
        recentPayments.clear()
    }
}
