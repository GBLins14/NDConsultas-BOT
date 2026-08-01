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
        private val CARD_APPROVED_STATUSES = setOf("approved", "completed", "paid", "confirmed")
    }

    data class PixResult(
        val success: Boolean,
        val pixCode: String? = null,
        val identifier: String? = null,
        val qrCodeBytes: ByteArray? = null,
        val error: String? = null
    )

    data class CardResult(
        val success: Boolean,
        val transactionId: String? = null,
        val brand: String? = null,
        val last4: String? = null,
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
                log.error("SyncPay retornou PIX sem código ou identifier")
                return PixResult(false, error = "Erro ao gerar PIX. Tente novamente.")
            }

            val qrBytes = try {
                qrCodeService.generate(response.pix_code)
            } catch (e: Exception) {
                log.warn("Falha ao gerar QR Code: {}", e.message)
                null
            }

            paymentSessionManager.setMethodPix(userPhone, response.pix_code, response.identifier)

            log.info(
                "PIX criado -> identifier={}, pixCode={}",
                response.identifier,
                response.pix_code
            )

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

    // ── Cartão ─────────────────────────────────────────────────────

    fun chargeCard(
        userPhone: String,
        cardInput: PaymentSessionManager.CardInput,
        amount: BigDecimal,
        description: String
    ): CardResult {
        return try {
            // 1. Tokenizar cartão
            val tokenResponse = syncPayClient.createCardToken(
                cardNumber = cardInput.number!!,
                holderName = cardInput.holderName!!,
                expiryMonth = cardInput.expiryMonth!!,
                expiryYear = cardInput.expiryYear!!,
                cvv = cardInput.cvv!!
            )

            val cardToken = tokenResponse.token
            if (cardToken.isNullOrBlank()) {
                log.error("SyncPay não retornou token de cartão")
                return CardResult(false, error = "Cartão recusado. Verifique os dados e tente novamente.")
            }

            // 2. Cobrar
            val chargeResponse = syncPayClient.chargeCard(
                amount = amount,
                cardToken = cardToken,
                description = description,
                clientPhone = userPhone
            )

            val transactionId = chargeResponse.id
            if (transactionId.isNullOrBlank()) {
                log.error("SyncPay não retornou ID da transação de cartão")
                return CardResult(false, error = chargeResponse.message ?: "Pagamento recusado.")
            }

            val status = chargeResponse.status?.lowercase()
            if (status != null && status in CARD_APPROVED_STATUSES) {
                // Pagamento aprovado na hora
                paymentSessionManager.markPaid(userPhone, transactionId)
                paymentStats.record(userPhone,
                    paymentSessionManager.getSession(userPhone)?.tipo ?: "",
                    paymentSessionManager.getSession(userPhone)?.tipoLabel ?: "",
                    amount, transactionId
                )
            }

            log.info("Cartão cobrado: id={}, status={}, brand={}", transactionId, status, tokenResponse.brand)

            CardResult(
                success = status in CARD_APPROVED_STATUSES,
                transactionId = transactionId,
                brand = tokenResponse.brand,
                last4 = tokenResponse.last4,
                error = if (status !in CARD_APPROVED_STATUSES) (chargeResponse.message ?: "Pagamento não aprovado.") else null
            )
        } catch (e: Exception) {
            log.error("Erro ao cobrar cartão para {}: {}", userPhone, e.message, e)
            CardResult(false, error = "Erro ao processar pagamento. Tente novamente.")
        }
    }

    // ── Webhook de confirmação PIX ─────────────────────────────────

    fun confirmPixPayment(transactionId: String): PaymentSessionManager.PaymentSession? {
        val session = paymentSessionManager.findByPixIdentifier(transactionId) ?: run {
            log.warn("Webhook PIX recebido para identifier desconhecido: {}", transactionId)
            return null
        }

        paymentSessionManager.markPaid(session.userPhone, transactionId)
        paymentStats.record(session.userPhone, session.tipo, session.tipoLabel, session.price, transactionId)

        log.info("PIX confirmado: {} - {} R\${}", session.userPhone, session.tipo, "%.2f".format(session.price))
        return paymentSessionManager.getSession(session.userPhone)
    }
}
