package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.client.BancoBrasilClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

@Service
class DebitoVeicularService(
    private val bbClient: BancoBrasilClient
) {
    companion object {
        private val log = LoggerFactory.getLogger(DebitoVeicularService::class.java)
        private const val MAX_POLL_ATTEMPTS = 5
        private const val POLL_INTERVAL_MS = 2000L
    }

    data class DebitoConsultaResult(
        val success: Boolean,
        val debitos: BancoBrasilClient.DebitosVeicularesResponse? = null,
        val error: String? = null
    )

    data class PixDebitoResult(
        val success: Boolean,
        val pixCode: String? = null,
        val error: String? = null
    )

    // ── Consultar débitos (POST + polling GET) ────────────────────

    fun consultarDebitos(
        renavam: Long,
        uf: String,
        placa: String? = null,
        cpf: Long? = null
    ): DebitoConsultaResult {
        val request = BancoBrasilClient.ConsultaDebitosRequest(
            numeroRenavam = renavam,
            codigoUf = uf.uppercase(),
            codigoPlaca = placa?.uppercase(),
            codigoCpf = cpf
        )

        // 1. Criar solicitação
        val solicitacao = try {
            bbClient.criarConsultaDebitos(request)
        } catch (e: RestClientException) {
            log.error("Erro ao criar consulta BB: {}", e.message, e)
            return DebitoConsultaResult(
                success = false,
                error = parseRestClientError(e, "Erro ao solicitar consulta de débitos. Verifique os dados e tente novamente.")
            )
        } catch (e: Exception) {
            log.error("Erro inesperado ao criar consulta BB: {}", e.message, e)
            return DebitoConsultaResult(success = false, error = "Erro inesperado. Tente novamente.")
        }

        val codigoSolicitacao = solicitacao.codigoSolicitacao
        if (codigoSolicitacao.isNullOrBlank()) {
            return DebitoConsultaResult(success = false, error = "Código de solicitação não retornado pela API.")
        }

        log.info("Consulta BB criada: solicitacao={}", codigoSolicitacao)

        // 2. Polling para obter resultado (API assíncrona)
        var lastError: String? = null
        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return DebitoConsultaResult(success = false, error = "Consulta interrompida.")
            }

            try {
                val resultado = bbClient.obterResultadoDebitos(codigoSolicitacao)

                if (resultado.codigoSolicitacao != null) {
                    log.info(
                        "Resultado BB obtido: solicitacao={}, servicos={}",
                        codigoSolicitacao, resultado.quantidadeOcorrenciaServicos ?: 0
                    )
                    return DebitoConsultaResult(success = true, debitos = resultado)
                }
            } catch (e: RestClientException) {
                lastError = parseRestClientError(e, null)
                log.warn("Tentativa {}/{} falhou para BB solicitacao={}: {}", attempt, MAX_POLL_ATTEMPTS, codigoSolicitacao, lastError)
            } catch (e: Exception) {
                lastError = e.message
                log.warn("Tentativa {}/{} falhou para BB solicitacao={}: {}", attempt, MAX_POLL_ATTEMPTS, codigoSolicitacao, e.message)
            }
        }

        log.error("Timeout ao obter resultado BB: solicitacao={}", codigoSolicitacao)
        return DebitoConsultaResult(
            success = false,
            error = lastError ?: "Tempo limite excedido ao consultar débitos. Tente novamente em alguns instantes."
        )
    }

    // ── Gerar PIX para débito individual ──────────────────────────

    fun gerarPixParaDebito(
        codigoSolicitacao: String,
        codigoServico: Int,
        numeroIdentificadorItem: Int,
        numeroUnicoItemBanco: Long,
        segundosExpiracao: Int = 3600
    ): PixDebitoResult {
        val request = BancoBrasilClient.PagamentoPixRequest(
            codigoSolicitacao = codigoSolicitacao,
            codigoServico = codigoServico,
            numeroIdentificadorItem = numeroIdentificadorItem,
            numeroUnicoItemBanco = numeroUnicoItemBanco,
            segundosExpiracao = segundosExpiracao
        )

        return try {
            val response = bbClient.gerarPixDebito(request)

            if (response.qrCodePagamento.isNullOrBlank()) {
                log.warn("PIX BB sem qrCode: solicitacao={}, servico={}", codigoSolicitacao, codigoServico)
                PixDebitoResult(success = false, error = "PIX não gerado. Tente novamente.")
            } else {
                log.info("PIX BB gerado: solicitacao={}, servico={}", codigoSolicitacao, codigoServico)
                PixDebitoResult(success = true, pixCode = response.qrCodePagamento)
            }
        } catch (e: RestClientException) {
            log.error("Erro ao gerar PIX BB: {}", e.message, e)
            PixDebitoResult(
                success = false,
                error = parseRestClientError(e, "Erro ao gerar PIX para pagamento. Tente novamente.")
            )
        } catch (e: Exception) {
            log.error("Erro inesperado ao gerar PIX BB: {}", e.message, e)
            PixDebitoResult(success = false, error = "Erro inesperado. Tente novamente.")
        }
    }

    // ── Helper ────────────────────────────────────────────────────

    private fun parseRestClientError(e: RestClientException, fallback: String?): String {
        val message = e.message ?: return fallback ?: "Erro de comunicação com o Banco do Brasil."
        // Tentar extrair mensagem do corpo da resposta de erro BB
        val match = Regex(""""message"\s*:\s*"([^"]+)"""").find(message)
        return match?.groupValues?.get(1) ?: fallback ?: "Erro de comunicação com o Banco do Brasil."
    }
}
