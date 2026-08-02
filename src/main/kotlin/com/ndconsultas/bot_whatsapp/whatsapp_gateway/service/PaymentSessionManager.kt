package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class PaymentSessionManager {

    companion object {
        private val log = LoggerFactory.getLogger(PaymentSessionManager::class.java)
        private const val EXPIRY_MINUTES = 30L
    }

    enum class PaymentStatus {
        AWAITING_CPF,
        AWAITING_PAYMENT,
        PAID,
        CANCELLED
    }

    data class PaymentSession(
        val userPhone: String,
        val tipo: String,
        val tipoLabel: String,
        val query: String,
        val price: BigDecimal,
        val status: PaymentStatus,
        val createdAt: Instant,
        val paymentId: String? = null,
        val invoiceUrl: String? = null
    ) {
        fun isExpired(): Boolean =
            status != PaymentStatus.PAID &&
                status != PaymentStatus.CANCELLED &&
                Duration.between(createdAt, Instant.now()).toMinutes() >= EXPIRY_MINUTES
    }

    private val sessions = ConcurrentHashMap<String, PaymentSession>()

    fun create(userPhone: String, tipo: String, tipoLabel: String, query: String, price: BigDecimal, needsCpf: Boolean): PaymentSession {
        val session = PaymentSession(
            userPhone = userPhone,
            tipo = tipo,
            tipoLabel = tipoLabel,
            query = query,
            price = price,
            status = if (needsCpf) PaymentStatus.AWAITING_CPF else PaymentStatus.AWAITING_PAYMENT,
            createdAt = Instant.now()
        )
        sessions[userPhone] = session
        log.info("Sessão de pagamento criada: {} - {} R\${}", userPhone, tipo, "%.2f".format(price))
        return session
    }

    fun getSession(userPhone: String): PaymentSession? {
        val session = sessions[userPhone] ?: return null
        if (session.isExpired()) {
            sessions.remove(userPhone)
            log.info("Sessão de pagamento expirada: {}", userPhone)
            return null
        }
        return session
    }

    fun setPaymentCreated(userPhone: String, asaasPaymentId: String, invoiceUrl: String): PaymentSession? {
        val session = sessions[userPhone] ?: return null
        val updated = session.copy(
            status = PaymentStatus.AWAITING_PAYMENT,
            paymentId = asaasPaymentId,
            invoiceUrl = invoiceUrl
        )
        sessions[userPhone] = updated
        log.info("Cobrança Asaas criada para {}: paymentId={}", userPhone, asaasPaymentId)
        return updated
    }

    fun markPaid(userPhone: String, paymentId: String): PaymentSession? {
        val session = sessions[userPhone] ?: return null
        val paid = session.copy(status = PaymentStatus.PAID, paymentId = paymentId)
        sessions[userPhone] = paid
        log.info("Pagamento confirmado: {} - {} (ID: {})", userPhone, session.tipo, paymentId)
        return paid
    }

    fun consume(userPhone: String): PaymentSession? {
        return sessions.remove(userPhone)
    }

    fun cancel(userPhone: String): PaymentSession? {
        val session = sessions.remove(userPhone) ?: return null
        log.info("Pagamento cancelado: {} - {}", userPhone, session.tipo)
        return session
    }

    fun findByPaymentId(asaasPaymentId: String): PaymentSession? {
        return sessions.values.firstOrNull { it.paymentId == asaasPaymentId }
    }

    fun getPendingSessions(): Map<String, PaymentSession> {
        sessions.entries.removeIf { it.value.isExpired() }
        return sessions.filter {
            it.value.status in listOf(
                PaymentStatus.AWAITING_CPF,
                PaymentStatus.AWAITING_PAYMENT
            )
        }
    }

    fun getPendingCount(): Int = getPendingSessions().size
}
