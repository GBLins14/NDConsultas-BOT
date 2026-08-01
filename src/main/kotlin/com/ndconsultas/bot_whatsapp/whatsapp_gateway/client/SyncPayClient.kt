package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.SyncPayProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class SyncPayClient(
    @Qualifier("syncPayRestClient") private val restClient: RestClient,
    private val properties: SyncPayProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(SyncPayClient::class.java)
        private const val EXPIRY_BUFFER_SECONDS = 120L
    }

    // ── Auth token (cache com renovação automática) ────────────────

    private val tokenLock = ReentrantLock()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant = Instant.EPOCH

    data class AuthRequest(
        val client_id: String,
        val client_secret: String
    )

    data class AuthResponse(
        val access_token: String? = null,
        val token_type: String? = null,
        val expires_in: Int? = null,
        val expires_at: String? = null
    )

    private fun getToken(): String = tokenLock.withLock {
        val now = Instant.now()
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS))) {
            return@withLock cachedToken!!
        }

        log.info("Renovando token SyncPay...")
        val request = AuthRequest(
            client_id = properties.clientId,
            client_secret = properties.clientSecret
        )

        val response = restClient.post()
            .uri("/api/partner/v1/auth-token")
            .body(request)
            .retrieve()
            .body(AuthResponse::class.java)
            ?: throw RuntimeException("Resposta vazia ao obter token SyncPay")

        val token = response.access_token
            ?: throw RuntimeException("Token SyncPay não retornado")

        cachedToken = token
        tokenExpiresAt = now.plusSeconds(response.expires_in?.toLong() ?: 3600L)
        log.info("Token SyncPay renovado. Expira em: {}", tokenExpiresAt)

        token
    }

    // ── DTOs comuns ────────────────────────────────────────────────

    data class ClientInfo(
        val name: String,
        val cpf: String,
        val email: String,
        val phone: String
    )

    // ── PIX Cash-In ────────────────────────────────────────────────

    data class CashInRequest(
        val amount: Double,
        val description: String? = null,
        val webhook_url: String,
        val client: ClientInfo
    )

    data class CashInResponse(
        val message: String? = null,
        val pix_code: String? = null,
        val identifier: String? = null
    )

    fun createPixCashIn(
        amount: BigDecimal,
        description: String? = null,
        clientName: String = "Cliente",
        clientCpf: String = "00000000000",
        clientEmail: String = "cliente@ndconsultas.com",
        clientPhone: String = "00000000000"
    ): CashInResponse {
        if (properties.webhookUrl.isBlank()) {
            throw RuntimeException("SYNCPAY_WEBHOOK_URL não configurado.")
        }

        log.info("Criando PIX cash-in: R\$ {}", "%.2f".format(amount))

        val request = CashInRequest(
            amount = amount.toDouble(),
            description = description,
            webhook_url = properties.webhookUrl,
            client = buildClient(clientName, clientCpf, clientEmail, clientPhone)
        )

        val token = getToken()
        val response = restClient.post()
            .uri("/api/partner/v1/cash-in")
            .header("Authorization", "Bearer $token")
            .body(request)
            .retrieve()
            .body(CashInResponse::class.java)

        log.info("PIX cash-in criado: identifier={}", response?.identifier)
        return response ?: throw RuntimeException("Resposta vazia do SyncPay cash-in")
    }

    // ── Card Token ─────────────────────────────────────────────────

    data class CardTokenRequest(
        val card_number: String,
        val holder_name: String,
        val expiration_month: String,
        val expiration_year: String,
        val security_code: String
    )

    data class CardTokenResponse(
        val token: String? = null,
        val brand: String? = null,
        val last4: String? = null
    )

    fun createCardToken(
        cardNumber: String,
        holderName: String,
        expiryMonth: String,
        expiryYear: String,
        cvv: String
    ): CardTokenResponse {
        log.info("Tokenizando cartão ****{}", cardNumber.takeLast(4))

        val request = CardTokenRequest(
            card_number = cardNumber,
            holder_name = holderName,
            expiration_month = expiryMonth,
            expiration_year = expiryYear,
            security_code = cvv
        )

        val token = getToken()
        val response = restClient.post()
            .uri("/api/partner/v1/card-token")
            .header("Authorization", "Bearer $token")
            .body(request)
            .retrieve()
            .body(CardTokenResponse::class.java)

        log.info("Cartão tokenizado: brand={}, last4={}", response?.brand, response?.last4)
        return response ?: throw RuntimeException("Resposta vazia ao tokenizar cartão")
    }

    // ── Card Charge ────────────────────────────────────────────────

    data class CardChargeRequest(
        val amount: Double,
        val card_token: String,
        val description: String? = null,
        val installments: Int = 1,
        val webhook_url: String,
        val client: ClientInfo
    )

    data class CardChargeResponse(
        val id: String? = null,
        val status: String? = null,
        val message: String? = null
    )

    fun chargeCard(
        amount: BigDecimal,
        cardToken: String,
        description: String? = null,
        installments: Int = 1,
        clientName: String = "Cliente",
        clientCpf: String = "00000000000",
        clientEmail: String = "cliente@ndconsultas.com",
        clientPhone: String = "00000000000"
    ): CardChargeResponse {
        if (properties.webhookUrl.isBlank()) {
            throw RuntimeException("SYNCPAY_WEBHOOK_URL não configurado.")
        }

        log.info("Cobrando cartão: R\$ {}", "%.2f".format(amount))

        val request = CardChargeRequest(
            amount = amount.toDouble(),
            card_token = cardToken,
            description = description,
            installments = installments,
            webhook_url = properties.webhookUrl,
            client = buildClient(clientName, clientCpf, clientEmail, clientPhone)
        )

        val token = getToken()
        val response = restClient.post()
            .uri("/api/partner/v1/card-charge")
            .header("Authorization", "Bearer $token")
            .body(request)
            .retrieve()
            .body(CardChargeResponse::class.java)

        log.info("Cobrança cartão: id={}, status={}", response?.id, response?.status)
        return response ?: throw RuntimeException("Resposta vazia do SyncPay card-charge")
    }

    // ── Helper ─────────────────────────────────────────────────────

    private fun buildClient(
        name: String,
        cpf: String,
        email: String,
        phone: String
    ) = ClientInfo(
        name = name,
        cpf = cpf.replace(Regex("[^0-9]"), "").padStart(11, '0').takeLast(11),
        email = email,
        phone = phone.replace(Regex("[^0-9]"), "").takeLast(11)
    )
}
