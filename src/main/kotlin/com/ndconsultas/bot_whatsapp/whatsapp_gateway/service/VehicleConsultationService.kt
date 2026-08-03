package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service
class VehicleConsultationService(
    @Value("\${central.api-key:}") private val apiKey: String,
    @Value("\${apibrasil.bearer-token:}") private val apiBrasilToken: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(VehicleConsultationService::class.java)
        private const val BASE_URL = "https://api-centralduality.com/query"
        private const val APIBRASIL_URL = "https://gateway.apibrasil.io/api/v2/consulta/veiculos/credits"

        private val APIBRASIL_TYPES = setOf("leilao_completo_score")
    }

    private val restClient = RestClient.create()

    fun consultar(tipo: String, query: String): ConsultationResult {
        return if (tipo in APIBRASIL_TYPES) {
            consultarApiBrasil(tipo, query)
        } else {
            consultarCentralDuality(tipo, query)
        }
    }

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
    val saldoRestante: String? = null
)
