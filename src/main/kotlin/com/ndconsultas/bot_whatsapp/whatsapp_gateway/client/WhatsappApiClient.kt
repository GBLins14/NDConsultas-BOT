package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.WhatsappProperties
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.api.WhatsappApiResponse
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.outgoing.MarkAsReadPayload
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.outgoing.MessagePayload
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception.WhatsappApiException
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception.WhatsappAuthException
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception.WhatsappRateLimitException
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

@Component
class WhatsappApiClient(
    private val whatsappRestClient: RestClient,
    private val properties: WhatsappProperties,
    private val stats: BotStats
) {
    companion object {
        private val log = LoggerFactory.getLogger(WhatsappApiClient::class.java)
    }

    fun sendMessage(payload: MessagePayload): WhatsappApiResponse {
        log.info("Sending [{}] to {}", payload.type, payload.to)
        try {
            val response = whatsappRestClient.post()
                .uri("/${properties.phoneNumberId}/messages")
                .body(payload)
                .retrieve()
                .body(WhatsappApiResponse::class.java)
                ?: throw WhatsappApiException("Resposta vazia da API do WhatsApp")

            stats.incrementSent()
            log.info("Message sent — id: {}", response.messages?.firstOrNull()?.id)
            return response
        } catch (e: HttpClientErrorException.Unauthorized) {
            stats.incrementErrors()
            log.error("Token inválido: {}", e.responseBodyAsString)
            throw WhatsappAuthException("Token do WhatsApp inválido ou expirado")
        } catch (e: HttpClientErrorException.TooManyRequests) {
            stats.incrementErrors()
            log.error("Rate limit: {}", e.responseBodyAsString)
            throw WhatsappRateLimitException("Limite de requisições da API do WhatsApp excedido")
        } catch (e: HttpClientErrorException) {
            stats.incrementErrors()
            log.error("WhatsApp API error [{}]: {}", e.statusCode, e.responseBodyAsString)
            throw WhatsappApiException("Erro na API do WhatsApp (${e.statusCode}): ${e.responseBodyAsString}", e)
        }
    }

    fun markAsRead(payload: MarkAsReadPayload) {
        log.debug("Marking message {} as read", payload.message_id)
        try {
            whatsappRestClient.post()
                .uri("/${properties.phoneNumberId}/messages")
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (e: HttpClientErrorException) {
            log.warn("Failed to mark message as read: {}", e.message)
        }
    }
}
