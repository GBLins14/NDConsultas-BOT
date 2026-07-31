package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.WhatsappProperties
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.api.MediaUploadResponse
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.api.WhatsappApiResponse
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.outgoing.MarkAsReadPayload
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.outgoing.MessagePayload
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception.WhatsappApiException
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception.WhatsappAuthException
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception.WhatsappRateLimitException
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
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

    fun uploadMedia(fileBytes: ByteArray, mimeType: String, filename: String): String {
        log.info("Uploading media — type: {}, filename: {}, size: {} bytes", mimeType, filename, fileBytes.size)
        try {
            val resource = object : ByteArrayResource(fileBytes) {
                override fun getFilename() = filename
            }

            val fileHeaders = HttpHeaders()
            fileHeaders.contentType = MediaType.parseMediaType(mimeType)
            fileHeaders.contentDisposition = ContentDisposition.formData()
                .name("file")
                .filename(filename)
                .build()

            val body = LinkedMultiValueMap<String, Any>()
            body.add("messaging_product", "whatsapp")
            body.add("type", mimeType)
            body.add("file", HttpEntity(resource, fileHeaders))

            // RestClient separado para multipart (sem Content-Type: application/json padrao)
            val mediaClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com/${properties.apiVersion}")
                .defaultHeader("Authorization", "Bearer ${properties.apiKey}")
                .build()

            val response = mediaClient.post()
                .uri("/${properties.phoneNumberId}/media")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(MediaUploadResponse::class.java)
                ?: throw WhatsappApiException("Resposta vazia ao fazer upload de media")

            log.info("Media uploaded — id: {}", response.id)
            return response.id
        } catch (e: HttpClientErrorException) {
            log.error("Erro ao fazer upload de media [{}]: {}", e.statusCode, e.responseBodyAsString)
            throw WhatsappApiException("Erro ao fazer upload de media: ${e.responseBodyAsString}", e)
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
