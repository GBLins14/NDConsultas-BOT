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

    data class CardChargeResult(
        val success: Boolean,
        val paymentId: String? = null,
        val brand: String? = null,
        val last4: String? = null,
        val error: String? = null
    )

    // ── PIX ────────────────────────────────────────────────────────

    fun generatePix(userPhone: String, amount: BigDecimal, description: String): PixResult {
        return try {
            val response = syncPayClient.createPixCashIn(amount, description)

            if (response.pix_code.isNullOrBlank() || response.identifier.isNullOrBlank()) {
                log.error("SyncPay retornou PIX sem codigo ou identifier")
                return PixResult(false, error = "Erro ao gerar PIX. Tente novamente.")
            }

            // Gerar QR Code
            val qrBytes = qrCodeService.generate(response.pix_code)

            // Atualizar sessao
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

    // ── Cartao ─────────────────────────────────────────────────────

    fun chargeCard(
        userPhone: String,
        cardInput: PaymentSessionManager.CardInput,
        amount: BigDecimal,
        description: String
    ): CardChargeResult {
        return try {
            paymentSessionManager.markProcessing(userPhone)

            // 1. Criar token do cartao
            val tokenResponse = syncPayClient.createCardToken(
                number = cardInput.number!!,
                holderName = cardInput.holderName!!,
                expiryMonth = cardInput.expiryMonth!!,
                expiryYear = cardInput.expiryYear!!,
                cvv = cardInput.cvv!!
            )

            val token = tokenResponse.data?.token
            if (token.isNullOrBlank()) {
                paymentSessionManager.markFailed(userPhone)
                return CardChargeResult(false, error = "Erro ao processar cartao. Verifique os dados e tente novamente.")
            }

            // 2. Cobrar
            val chargeResponse = syncPayClient.chargeCard(token, amount, description)

            val paymentId = chargeResponse.identifier ?: "card_${System.currentTimeMillis()}"

            // 3. Marcar como pago
            paymentSessionManager.markPaid(userPhone, paymentId)

            val session = paymentSessionManager.getSession(userPhone)
            if (session != null) {
                paymentStats.record(userPhone, session.tipo, session.tipoLabel, amount, paymentId)
            }

            CardChargeResult(
                success = true,
                paymentId = paymentId,
                brand = tokenResponse.data.brand,
                last4 = tokenResponse.data.last4
            )
        } catch (e: Exception) {
            log.error("Erro ao cobrar cartao para {}: {}", userPhone, e.message, e)
            paymentSessionManager.markFailed(userPhone)
            CardChargeResult(false, error = "Erro ao processar pagamento. Verifique os dados do cartao e tente novamente.")
        }
    }

    // ── Webhook de confirmacao PIX ─────────────────────────────────

    fun confirmPixPayment(identifier: String): PaymentSessionManager.PaymentSession? {
        val session = paymentSessionManager.findByPixIdentifier(identifier) ?: run {
            log.warn("Webhook PIX recebido para identifier desconhecido: {}", identifier)
            return null
        }

        paymentSessionManager.markPaid(session.userPhone, identifier)
        paymentStats.record(session.userPhone, session.tipo, session.tipoLabel, session.price, identifier)

        log.info("PIX confirmado via webhook: {} - {} R$ {}", session.userPhone, session.tipo, "%.2f".format(session.price))
        return paymentSessionManager.getSession(session.userPhone)
    }
}
