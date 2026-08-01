package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.SyncPayProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Component
class SyncPayClient(
    @Qualifier("syncPayRestClient") private val restClient: RestClient,
    private val properties: SyncPayProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(SyncPayClient::class.java)
    }

    // ── DTOs ───────────────────────────────────────────────────────

    data class CashInRequest(
        val amount: Double,
        val description: String? = null,
        val webhook_url: String? = null,
        val client: ClientInfo? = null
    )

    data class ClientInfo(
        val name: String,
        val cpf: String,
        val email: String,
        val phone: String
    )

    data class CashInResponse(
        val message: String? = null,
        val pix_code: String? = null,
        val identifier: String? = null
    )

    data class CardTokenRequest(
        val card: CardData
    )

    data class CardData(
        val number: String,
        val holder_name: String,
        val expiry_month: String,
        val expiry_year: String,
        val cvv: String
    )

    data class CardTokenResponse(
        val data: CardTokenData? = null
    )

    data class CardTokenData(
        val token: String? = null,
        val brand: String? = null,
        val last4: String? = null,
        val expires_at: String? = null
    )

    data class CreditCardChargeRequest(
        val amount: Double,
        val card_token: String,
        val description: String? = null
    )

    data class CreditCardChargeResponse(
        val message: String? = null,
        val identifier: String? = null,
        val status: String? = null
    )

    // ── PIX Cash-In ────────────────────────────────────────────────

    fun createPixCashIn(amount: BigDecimal, description: String? = null): CashInResponse {
        log.info("Criando PIX cash-in: R$ {}", "%.2f".format(amount))

        val request = CashInRequest(
            amount = amount.toDouble(),
            description = description,
            webhook_url = properties.webhookUrl.ifBlank { null }
        )

        val response = restClient.post()
            .uri("/api/partner/v1/cash-in")
            .body(request)
            .retrieve()
            .body(CashInResponse::class.java)

        log.info("PIX cash-in criado: identifier={}", response?.identifier)
        return response ?: throw RuntimeException("Resposta vazia do SyncPay cash-in")
    }

    // ── Card Token ─────────────────────────────────────────────────

    fun createCardToken(
        number: String,
        holderName: String,
        expiryMonth: String,
        expiryYear: String,
        cvv: String
    ): CardTokenResponse {
        log.info("Criando token de cartao: ****{}", number.takeLast(4))

        val request = CardTokenRequest(
            card = CardData(
                number = number,
                holder_name = holderName,
                expiry_month = expiryMonth,
                expiry_year = expiryYear,
                cvv = cvv
            )
        )

        val response = restClient.post()
            .uri("/api/partner/v1/card-tokens")
            .body(request)
            .retrieve()
            .body(CardTokenResponse::class.java)

        log.info("Token criado: brand={}, last4={}", response?.data?.brand, response?.data?.last4)
        return response ?: throw RuntimeException("Resposta vazia do SyncPay card-tokens")
    }

    // ── Credit Card Charge ─────────────────────────────────────────

    fun chargeCard(cardToken: String, amount: BigDecimal, description: String? = null): CreditCardChargeResponse {
        log.info("Cobrando cartao: R$ {}", "%.2f".format(amount))

        val request = CreditCardChargeRequest(
            amount = amount.toDouble(),
            card_token = cardToken,
            description = description
        )

        val response = restClient.post()
            .uri("/api/partner/v1/credit-card")
            .body(request)
            .retrieve()
            .body(CreditCardChargeResponse::class.java)

        log.info("Cobranca cartao: status={}, identifier={}", response?.status, response?.identifier)
        return response ?: throw RuntimeException("Resposta vazia do SyncPay credit-card")
    }
}
