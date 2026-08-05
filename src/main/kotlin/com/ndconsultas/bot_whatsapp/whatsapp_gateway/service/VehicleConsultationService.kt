package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.QueryTypeRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.Base64

@Service
class VehicleConsultationService(
    @Value("\${central.api-key:}") private val apiKey: String,
    @Value("\${apibrasil.bearer-token:}") private val apiBrasilToken: String,
    @Value("\${portaldespachantes.chave-acesso:}") private val portalChaveAcesso: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(VehicleConsultationService::class.java)
        private const val BASE_URL = "https://api-centralduality.com/query"
        private const val APIBRASIL_URL = "https://gateway.apibrasil.io/api/v2/consulta/veiculos/credits"
        private const val PORTAL_BASE_URL = "https://portaldespachantes.online"

        private val APIBRASIL_TYPES = setOf("leilao_completo_score")
    }

    private val restClient = RestClient.create()

    fun consultar(tipo: String, query: String): ConsultationResult {
        return when {
            QueryTypeRegistry.PORTAL_DESPACHANTES_TYPES.contains(tipo) ->
                consultarPortalDespachantes(tipo, query)
            tipo in APIBRASIL_TYPES ->
                consultarApiBrasil(tipo, query)
            else ->
                consultarCentralDuality(tipo, query)
        }
    }

    // ── Portal Despachantes (CRLV-e / CRV) ─────────────────────────

    private fun consultarPortalDespachantes(tipo: String, query: String): ConsultationResult {
        if (portalChaveAcesso.isBlank()) {
            return ConsultationResult(success = false, error = "Chave de acesso do Portal Despachantes nao configurada. Contate o administrador.")
        }

        val endpoint = when {
            tipo.startsWith("crlv_") -> "/consultar-crlv-${tipo.removePrefix("crlv_")}"
            tipo == "crv_codigo" -> "/consultar-crv"
            tipo == "crv_digital_cod" -> "/consultar-crv-cod"
            else -> return ConsultationResult(success = false, error = "Tipo invalido: $tipo")
        }

        val body = buildPortalRequestBody(tipo, query)
            ?: return ConsultationResult(success = false, error = "Dados insuficientes. Verifique o formato informado.")

        return try {
            restClient.post()
                .uri("$PORTAL_BASE_URL$endpoint")
                .header("Content-Type", "application/json")
                .header("chaveAcesso", portalChaveAcesso)
                .body(body)
                .exchange { _, response ->
                    val bytes = response.body.readAllBytes()
                    val contentType = response.headers.contentType?.toString() ?: ""

                    if (response.statusCode.is2xxSuccessful && contentType.contains("application/pdf")) {
                        ConsultationResult(success = true, pdfBytes = bytes)
                    } else if (response.statusCode.is2xxSuccessful && tipo == "crv_digital_cod") {
                        parseBase64PdfResponse(bytes, tipo)
                    } else {
                        val errorMsg = parsePortalError(bytes)
                        log.warn("Erro Portal Despachantes [{}]: status={} error={}", tipo, response.statusCode, errorMsg)
                        ConsultationResult(success = false, error = errorMsg)
                    }
                }
        } catch (e: RestClientException) {
            log.error("Erro ao consultar Portal Despachantes [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro de comunicacao com o Portal Despachantes. Tente novamente mais tarde.")
        } catch (e: Exception) {
            log.error("Erro inesperado Portal Despachantes [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro inesperado. Tente novamente.")
        }
    }

    private fun buildPortalRequestBody(tipo: String, query: String): Map<String, String>? {
        val needsFullInput = tipo in QueryTypeRegistry.CRLV_FULL_INPUT_STATES

        if (!needsFullInput) {
            val placa = query.trim().uppercase()
            if (placa.isBlank()) return null
            return mapOf("placa" to placa)
        }

        val parts = query.trim().split("\\s+".toRegex())
        if (parts.size < 3) return null

        return mapOf(
            "placa" to parts[0].uppercase(),
            "renavam" to parts[1],
            "cpf" to parts[2]
        )
    }

    private fun parseBase64PdfResponse(bytes: ByteArray, tipo: String): ConsultationResult {
        return try {
            val jsonStr = String(bytes, Charsets.UTF_8)

            val successMatch = Regex(""""succe?ss(?:o)?"\s*:\s*(true|false)""").find(jsonStr)
            val isSuccess = successMatch?.groupValues?.get(1) == "true"

            if (!isSuccess) {
                val errorMsg = parsePortalError(bytes)
                log.warn("Erro Portal Despachantes [{}]: {}", tipo, errorMsg)
                return ConsultationResult(success = false, error = errorMsg)
            }

            val pdfMatch = Regex(""""pdf_base64"\s*:\s*"([^"]+)"""").find(jsonStr)
            val pdfBase64 = pdfMatch?.groupValues?.get(1)

            if (pdfBase64.isNullOrBlank()) {
                log.warn("Resposta crv_digital_cod sem pdf_base64 [{}]", tipo)
                return ConsultationResult(success = false, error = "PDF nao encontrado na resposta da API.")
            }

            val pdfBytes = Base64.getDecoder().decode(pdfBase64)
            ConsultationResult(success = true, pdfBytes = pdfBytes)
        } catch (e: IllegalArgumentException) {
            log.error("Erro ao decodificar base64 PDF [{}]: {}", tipo, e.message)
            ConsultationResult(success = false, error = "Erro ao processar o PDF retornado.")
        } catch (e: Exception) {
            log.error("Erro ao processar resposta JSON/PDF [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro ao processar resposta. Tente novamente.")
        }
    }

    private fun parsePortalError(bytes: ByteArray): String {
        return try {
            val jsonStr = String(bytes, Charsets.UTF_8)
            val match = Regex(""""(?:error|erro|message)"\s*:\s*"([^"]+)"""").find(jsonStr)
            match?.groupValues?.get(1)
                ?: "Erro desconhecido do Portal Despachantes."
        } catch (e: Exception) {
            "Erro ao processar resposta do Portal Despachantes."
        }
    }

    // ── Central Duality ─────────────────────────────────────────────

    private fun consultarCentralDuality(tipo: String, query: String): ConsultationResult {
        if (apiKey.isBlank()) {
            return ConsultationResult(success = false, error = "API key nao configurada. Contate o administrador.")
        }

        return try {
            val response = restClient.get()
                .uri("$BASE_URL?api_key={apiKey}&tipo={tipo}&query={query}", apiKey, tipo, query)
                .retrieve()
                .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})

            if (response == null) {
                return ConsultationResult(success = false, error = "Resposta vazia da API.")
            }

            val success = response["success"]
            if (success == false) {
                val errorMsg = response["message"]?.toString()
                    ?: response["error"]?.toString()
                    ?: "Erro desconhecido na consulta."
                return ConsultationResult(success = false, error = errorMsg)
            }

            @Suppress("UNCHECKED_CAST")
            val meta = response["_meta"] as? Map<String, Any?>
            val data = response.filterKeys { it != "_meta" && it != "success" }

            ConsultationResult(
                success = true,
                data = data,
                custo = meta?.get("custo")?.toString(),
                saldoRestante = meta?.get("saldo_restante")?.toString()
            )
        } catch (e: RestClientException) {
            log.error("Erro ao consultar API Central Duality [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro de comunicacao com a API. Tente novamente mais tarde.")
        } catch (e: Exception) {
            log.error("Erro inesperado na consulta [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro inesperado. Tente novamente.")
        }
    }

    // ── API Brasil ──────────────────────────────────────────────────

    private fun consultarApiBrasil(tipo: String, query: String): ConsultationResult {
        if (apiBrasilToken.isBlank()) {
            return ConsultationResult(success = false, error = "Token da API Brasil nao configurado. Contate o administrador.")
        }

        val apiTipo = tipo.replace("_", "-")

        return try {
            val body = mapOf(
                "tipo" to apiTipo,
                "placa" to query.trim().uppercase(),
                "homolog" to false
            )

            val response = restClient.post()
                .uri(APIBRASIL_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiBrasilToken")
                .body(body)
                .retrieve()
                .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})

            if (response == null) {
                return ConsultationResult(success = false, error = "Resposta vazia da API Brasil.")
            }

            val error = response["error"]
            if (error != null && error != false) {
                val errorMsg = response["message"]?.toString()
                    ?: error.toString()
                return ConsultationResult(success = false, error = errorMsg)
            }

            val statusCode = response["status_code"]
            if (statusCode != null && statusCode != 200 && statusCode != 200.0) {
                val errorMsg = response["message"]?.toString() ?: "Erro na consulta (status $statusCode)."
                return ConsultationResult(success = false, error = errorMsg)
            }

            @Suppress("UNCHECKED_CAST")
            val rawData = response["data"] as? Map<String, Any?>
            if (rawData == null) {
                return ConsultationResult(success = false, error = "Nenhum dado retornado pela API Brasil.")
            }

            @Suppress("UNCHECKED_CAST")
            val veicular = rawData["veicular"] as? Map<String, Any?> ?: rawData

            ConsultationResult(success = true, data = veicular)
        } catch (e: RestClientException) {
            log.error("Erro ao consultar API Brasil [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro de comunicacao com a API Brasil. Tente novamente mais tarde.")
        } catch (e: Exception) {
            log.error("Erro inesperado na consulta API Brasil [{}]: {}", tipo, e.message, e)
            ConsultationResult(success = false, error = "Erro inesperado. Tente novamente.")
        }
    }
}

data class ConsultationResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null,
    val custo: String? = null,
    val saldoRestante: String? = null,
    val pdfBytes: ByteArray? = null
)
