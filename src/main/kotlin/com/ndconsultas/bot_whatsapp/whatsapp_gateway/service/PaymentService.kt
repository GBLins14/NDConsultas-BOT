package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.client.SyncPayClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PaymentService(
    private val syncPayClient: SyncPayClient,
    private val paymentSessionManager: PaymentSessionManager,
    private val paymentStats: PaymentStats,
    private val qrCodeService: QrCodeService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PaymentService::class.java)
    }

    data class PixResult(
        val success: Boolean,
        val pixCode: String? = null,
        val identifier: String? = null,
        val qrCodeBytes: ByteArray? = null,
        val error: String? = null
    )

    // ── PIX ────────────────────────────────────────────────────────

    fun generatePix(
        userPhone: String,
        amount: BigDecimal,
        description: String
    ): PixResult {
        return try {
            val response = syncPayClient.createPixCashIn(
                amount = amount,
                description = description,
                clientPhone = userPhone
            )

            if (response.pix_code.isNullOrBlank() || response.identifier.isNullOrBlank()) {
                log.error("SyncPay retornou PIX sem codigo ou identifier")
                return PixResult(false, error = "Erro ao gerar PIX. Tente novamente.")
            }

            val qrBytes = try {
                qrCodeService.generate(response.pix_code)
            } catch (e: Exception) {
                log.warn("Falha ao gerar QR Code: {}", e.message)
                null
            }

            paymentSessionManager.setMethodPix(userPhone, response.pix_code, response.identifier)

            PixResult(
                success = true,
                pixCode = response.pix_code,
                identifier = response.identifier,
                qrCodeBytes = qrBytes
            )
        } catch (e: Exception) {
            log.error("Erro ao gerar PIX para {}: {}", userPhone, e.message, e)
            PixResult(false, error = "Erro ao gerar PIX. Tente novamente mais tarde.")
        }
    }

    // ── Webhook de confirmacao PIX ─────────────────────────────────

    fun confirmPixPayment(transactionId: String): PaymentSessionManager.PaymentSession? {
        val session = paymentSessionManager.findByPixIdentifier(transactionId) ?: run {
            log.warn("Webhook PIX recebido para identifier desconhecido: {}", transactionId)
            return null
        }

        paymentSessionManager.markPaid(session.userPhone, transactionId)
        paymentStats.record(session.userPhone, session.tipo, session.tipoLabel, session.price, transactionId)

        log.info("PIX confirmado: {} - {} R${}", session.userPhone, session.tipo, "%.2f".format(session.price))
        return paymentSessionManager.getSession(session.userPhone)
    }
}
