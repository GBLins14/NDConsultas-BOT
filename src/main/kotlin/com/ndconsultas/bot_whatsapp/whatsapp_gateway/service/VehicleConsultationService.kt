package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service
class VehicleConsultationService(
    @Value("\${central.api-key:}") private val apiKey: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(VehicleConsultationService::class.java)
        private const val BASE_URL = "https://api-centralduality.com/query"
    }

    private val restClient = RestClient.create()

    fun consultar(tipo: String, query: String): ConsultationResult {
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
}

data class ConsultationResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null,
    val custo: String? = null,
    val saldoRestante: String? = null
)
