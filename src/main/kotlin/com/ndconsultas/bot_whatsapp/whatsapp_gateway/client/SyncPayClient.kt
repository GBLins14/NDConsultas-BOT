package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.SyncPayProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
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
        // Renova 2 minutos antes de expirar
        private const val EXPIRY_BUFFER_SECONDS = 120L
    }

    // ── Auth token (cache com renovacao automatica) ────────────────

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
            ?: throw RuntimeException("Token SyncPay nao retornado")

        cachedToken = token
        // expires_in em segundos
        tokenExpiresAt = now.plusSeconds(response.expires_in?.toLong() ?: 3600L)
        log.info("Token SyncPay renovado. Expira em: {}", tokenExpiresAt)

        token
    }

    // ── DTOs ───────────────────────────────────────────────────────

    data class ClientInfo(
        val name: String,
        val cpf: String,
        val email: String,
        val phone: String
    )

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

    // ── PIX Cash-In ────────────────────────────────────────────────

    fun createPixCashIn(
        amount: BigDecimal,
        description: String? = null,
        clientName: String = "Cliente",
        clientCpf: String = "00000000000",
        clientEmail: String = "cliente@ndconsultas.com",
        clientPhone: String = "00000000000"
    ): CashInResponse {
        if (properties.webhookUrl.isBlank()) {
            throw RuntimeException("SYNCPAY_WEBHOOK_URL nao configurado. Configure a variavel de ambiente.")
        }

        log.info("Criando PIX cash-in: R$ {}", "%.2f".format(amount))

        val request = CashInRequest(
            amount = amount.toDouble(),
            description = description,
            webhook_url = properties.webhookUrl,
            client = ClientInfo(
                name = clientName,
                cpf = clientCpf.replace(Regex("[^0-9]"), "").padStart(11, '0').takeLast(11),
                email = clientEmail,
                phone = clientPhone.replace(Regex("[^0-9]"), "").takeLast(11)
            )
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
}
