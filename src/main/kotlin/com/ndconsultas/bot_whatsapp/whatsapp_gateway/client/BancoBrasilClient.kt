package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.BancoBrasilProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class BancoBrasilClient(
    @Qualifier("bancoBrasilRestClient") private val restClient: RestClient,
    private val properties: BancoBrasilProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(BancoBrasilClient::class.java)
        private const val EXPIRY_BUFFER_SECONDS = 60L
    }

    // ── OAuth2 token (client_credentials com cache) ───────────────

    private val tokenLock = ReentrantLock()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant = Instant.EPOCH

    private val oauthClient = RestClient.create()

    private fun getToken(): String = tokenLock.withLock {
        val now = Instant.now()
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS))) {
            return@withLock cachedToken!!
        }

        log.info("Renovando token OAuth2 Banco do Brasil...")

        val credentials = Base64.getEncoder().encodeToString(
            "${properties.clientId}:${properties.clientSecret}".toByteArray()
        )

        val response = oauthClient.post()
            .uri("${properties.oauthUrl}?gw-dev-app-key=${properties.appKey}")
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("grant_type=client_credentials&scope=debitos-veiculares.requisicao debitos-veiculares.info")
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            ?: throw RuntimeException("Resposta vazia ao obter token BB")

        val token = response["access_token"]?.toString()
            ?: throw RuntimeException("Token BB não retornado")

        cachedToken = token
        val expiresIn = (response["expires_in"] as? Number)?.toLong() ?: 600L
        tokenExpiresAt = now.plusSeconds(expiresIn)
        log.info("Token BB renovado. Expira em: {}", tokenExpiresAt)

        token
    }

    // ── DTOs ──────────────────────────────────────────────────────

    data class ConsultaDebitosRequest(
        val numeroRenavam: Long,
        val codigoUf: String,
        val codigoCpf: Long? = null,
        val codigoCnpj: String? = null,
        val codigoPlaca: String? = null
    )

    data class ConsultaDebitosResponse(
        val codigoSolicitacao: String? = null,
        val timestampConsulta: String? = null
    )

    data class ServicoDebito(
        val codigoServico: Int = 0,
        val nomeServico: String? = null,
        val numeroIdentificadorItem: Int = 0,
        val valorItem: Double? = null,
        val codigoTextoItem: String? = null,
        val numeroUnicoItemBanco: Long = 0,
        val numeroUnicoItemOrgao: Long? = null,
        val codigoEstado: String? = null
    )

    data class IpvaDebito(
        val numeroIdentificadorIpva: Int? = null,
        val anoExercicioIpva: Int? = null,
        val dataVencimentoIpva: String? = null,
        val numeroCotaIpva: Int? = null,
        val nomeCotaIpva: String? = null
    )

    data class MultaDebito(
        val numeroIdentificadorMulta: Int? = null,
        val numeroGuiaMulta: Long? = null,
        val numeroAutoInfracao: String? = null,
        val codigoOrgaoMulta: Int? = null,
        val nomeOrgaoMulta: String? = null,
        val nomeEnquadramentoMulta: String? = null,
        val dataInfracaoMulta: String? = null
    )

    data class LicenciamentoDebito(
        val numeroIdentificadorLicenciamento: Int? = null,
        val anoExercicioLicenciamento: Int? = null,
        val valorTaxaLicenciamento: Double? = null,
        val valorTaxaCorreio: Double? = null
    )

    data class TransferenciaDebito(
        val numeroIdentificadorTransferencia: Int? = null,
        val anoExercicioTransferencia: Int? = null,
        val valorTaxaLicenciamentoTransferencia: Double? = null,
        val valorTaxaTransferencia: Double? = null
    )

    data class DpvatDebito(
        val numeroIdentificadorDpvat: Int? = null,
        val anoExercicioDpvat: Int? = null,
        val numeroParcelaDpvat: Int? = null,
        val nomeParcelaDpvat: String? = null
    )

    data class DebitosVeicularesResponse(
        val codigoSolicitacao: String? = null,
        val numeroRenavam: String? = null,
        val codigoUf: String? = null,
        val numeroPlaca: String? = null,
        val nomeProprietario: String? = null,
        val numeroIdentificadorProprietario: String? = null,
        val nomeMunicipio: String? = null,
        val codigoMunicipioEstadual: Int? = null,
        val timestampLimitePagamento: String? = null,
        val quantidadeOcorrenciaServicos: Int? = null,
        val quantidadeOcorrenciaListaIpva: Int? = null,
        val quantidadeOcorrenciaListaMulta: Int? = null,
        val quantidadeOcorrenciaListaDpvat: Int? = null,
        val quantidadeOcorrenciaListaTransferencia: Int? = null,
        val quantidadeOcorrenciaListaLicenciamento: Int? = null,
        val listaServicos: List<ServicoDebito>? = null,
        val listaIpva: List<IpvaDebito>? = null,
        val listaMulta: List<MultaDebito>? = null,
        val listaDpvat: List<DpvatDebito>? = null,
        val listaTransferencia: List<TransferenciaDebito>? = null,
        val listaLicenciamento: List<LicenciamentoDebito>? = null
    )

    data class PagamentoPixRequest(
        val codigoSolicitacao: String,
        val codigoServico: Int,
        val numeroIdentificadorItem: Int,
        val numeroUnicoItemBanco: Long,
        val segundosExpiracao: Int? = null
    )

    data class IdentificadoresPagamento(
        val codigoSolicitacao: String? = null,
        val codigoServico: Int? = null,
        val numeroUnicoItemBanco: Long? = null,
        val numeroIdentificadorItem: Int? = null
    )

    data class PagamentoPixResponse(
        val identificadoresPagamento: IdentificadoresPagamento? = null,
        val qrCodePagamento: String? = null
    )

    data class BbApiError(
        val code: String? = null,
        val message: String? = null
    )

    // ── POST /solicitacoes ────────────────────────────────────────

    fun criarConsultaDebitos(request: ConsultaDebitosRequest): ConsultaDebitosResponse {
        log.info("Criando consulta BB: renavam={}, uf={}", request.numeroRenavam, request.codigoUf)

        val token = getToken()
        return restClient.post()
            .uri { uriBuilder ->
                uriBuilder.path("/solicitacoes")
                    .queryParam("gw-dev-app-key", properties.appKey)
                    .build()
            }
            .header("Authorization", "Bearer $token")
            .body(request)
            .retrieve()
            .body(ConsultaDebitosResponse::class.java)
            ?: throw RuntimeException("Resposta vazia ao criar consulta BB")
    }

    // ── GET /solicitacoes/{codigoSolicitacao} ─────────────────────

    fun obterResultadoDebitos(codigoSolicitacao: String): DebitosVeicularesResponse {
        log.info("Obtendo resultado BB: solicitacao={}", codigoSolicitacao)

        val token = getToken()
        return restClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/solicitacoes/{codigoSolicitacao}")
                    .queryParam("gw-dev-app-key", properties.appKey)
                    .build(codigoSolicitacao)
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(DebitosVeicularesResponse::class.java)
            ?: throw RuntimeException("Resposta vazia ao obter resultado BB")
    }

    // ── POST /pagamentos/pix ──────────────────────────────────────

    fun gerarPixDebito(request: PagamentoPixRequest): PagamentoPixResponse {
        log.info(
            "Gerando PIX BB: solicitacao={}, servico={}, item={}",
            request.codigoSolicitacao, request.codigoServico, request.numeroIdentificadorItem
        )

        val token = getToken()
        return restClient.post()
            .uri { uriBuilder ->
                uriBuilder.path("/pagamentos/pix")
                    .queryParam("gw-dev-app-key", properties.appKey)
                    .build()
            }
            .header("Authorization", "Bearer $token")
            .body(request)
            .retrieve()
            .body(PagamentoPixResponse::class.java)
            ?: throw RuntimeException("Resposta vazia ao gerar PIX BB")
    }
}
