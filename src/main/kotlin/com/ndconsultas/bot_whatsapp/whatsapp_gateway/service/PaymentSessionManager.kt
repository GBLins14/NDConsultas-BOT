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
        AWAITING_METHOD,
        AWAITING_PAYMENT,
        COLLECTING_CARD,
        PAID,
        CANCELLED
    }

    data class CardInput(
        val number: String? = null,
        val holderName: String? = null,
        val expiryMonth: String? = null,
        val expiryYear: String? = null,
        val cvv: String? = null
    ) {
        fun currentStep(): String = when {
            number == null -> "card_number"
            holderName == null -> "card_holder"
            expiryMonth == null -> "card_expiry"
            cvv == null -> "card_cvv"
            else -> "complete"
        }

        fun isComplete(): Boolean =
            number != null && holderName != null && expiryMonth != null && expiryYear != null && cvv != null
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
        val pixCode: String? = null,
        val pixIdentifier: String? = null,
        val cardInput: CardInput? = null
    ) {
        fun isExpired(): Boolean =
            status != PaymentStatus.PAID &&
                status != PaymentStatus.CANCELLED &&
                Duration.between(createdAt, Instant.now()).toMinutes() >= EXPIRY_MINUTES
    }

    private val sessions = ConcurrentHashMap<String, PaymentSession>()

    fun create(userPhone: String, tipo: String, tipoLabel: String, query: String, price: BigDecimal): PaymentSession {
        val session = PaymentSession(
            userPhone = userPhone,
            tipo = tipo,
            tipoLabel = tipoLabel,
            query = query,
            price = price,
            status = PaymentStatus.AWAITING_METHOD,
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

    // ── PIX ─────────────────────────────────────────────────────────

    fun setMethodPix(userPhone: String, pixCode: String, pixIdentifier: String): PaymentSession? {
        val session = sessions[userPhone] ?: return null
        val updated = session.copy(
            status = PaymentStatus.AWAITING_PAYMENT,
            pixCode = pixCode,
            pixIdentifier = pixIdentifier
        )
        sessions[userPhone] = updated
        log.info("PIX gerado para {}: identifier={}", userPhone, pixIdentifier)
        return updated
    }

    // ── Cartão ──────────────────────────────────────────────────────

    fun setMethodCard(userPhone: String): PaymentSession? {
        val session = sessions[userPhone] ?: return null
        val updated = session.copy(
            status = PaymentStatus.COLLECTING_CARD,
            cardInput = CardInput()
        )
        sessions[userPhone] = updated
        log.info("Coleta de cartão iniciada para {}", userPhone)
        return updated
    }

    fun updateCardInput(userPhone: String, cardInput: CardInput): PaymentSession? {
        val session = sessions[userPhone] ?: return null
        val updated = session.copy(cardInput = cardInput)
        sessions[userPhone] = updated
        return updated
    }

    // ── Comum ───────────────────────────────────────────────────────

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

    fun findByPixIdentifier(identifier: String): PaymentSession? {
        return sessions.values.firstOrNull { it.pixIdentifier == identifier }
    }

    fun getPendingSessions(): Map<String, PaymentSession> {
        sessions.entries.removeIf { it.value.isExpired() }
        return sessions.filter {
            it.value.status in listOf(
                PaymentStatus.AWAITING_METHOD,
                PaymentStatus.AWAITING_PAYMENT,
                PaymentStatus.COLLECTING_CARD
            )
        }
    }

    fun getPendingCount(): Int = getPendingSessions().size
}
