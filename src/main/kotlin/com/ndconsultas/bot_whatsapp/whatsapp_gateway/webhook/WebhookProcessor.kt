package com.ndconsultas.bot_whatsapp.whatsapp_gateway.webhook

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandProcessor
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookMessage
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookPayload
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookValue
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.event.MessageReceivedEvent
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.event.StatusUpdateEvent
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.IncomingMessage
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.MessageType
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class WebhookProcessor(
    private val commandProcessor: CommandProcessor,
    private val eventPublisher: ApplicationEventPublisher,
    private val whatsappService: WhatsappService,
    private val stats: BotStats,
    private val sessionManager: ConsultationSessionManager
) {
    companion object {
        private val log = LoggerFactory.getLogger(WebhookProcessor::class.java)
    }

    @Async
    fun process(payload: WebhookPayload) {
        try {
            payload.entry.forEach { entry ->
                entry.changes.forEach { change ->
                    processMessages(change.value)
                    processStatuses(change.value)
                }
            }
        } catch (e: Exception) {
            log.error(">>> ERRO ao processar webhook: {}", e.message, e)
        }
    }

    private fun processMessages(value: WebhookValue) {
        val contacts = value.contacts?.associateBy { it.wa_id } ?: emptyMap()

        value.messages?.forEach { message ->
            stats.incrementReceived()

            val senderName = contacts[message.from]?.profile?.name ?: message.from
            val incoming = parseMessage(message, senderName)

            log.info("Received [{}] from {} ({})", incoming.type, senderName, message.from)

            // Auto mark as read
            try {
                whatsappService.markAsRead(message.id)
            } catch (e: Exception) {
                log.warn("Failed to mark message as read: {}", e.message)
            }

            val ctx = CommandContext(
                from = message.from,
                senderName = senderName,
                messageId = message.id,
                args = emptyList(),
                rawMessage = ""
            )

            // Check text commands
            if (incoming.type == MessageType.TEXT && incoming.text != null) {
                val textCtx = ctx.copy(rawMessage = incoming.text)
                if (commandProcessor.process(textCtx)) return@forEach

                // Check if user has a pending consultation — route text as query data
                val pending = sessionManager.getPending(message.from)
                if (pending != null) {
                    val consultCtx = ctx.copy(rawMessage = "/consultar ${pending.tipo} ${incoming.text}")
                    commandProcessor.process(consultCtx)
                    return@forEach
                }
            }

            // Check interactive replies (button / list) — route as commands if id starts with /
            if (incoming.type == MessageType.INTERACTIVE) {
                val replyId = incoming.buttonReplyId ?: incoming.listReplyId
                if (replyId != null && replyId.startsWith("/")) {
                    if (commandProcessor.process(ctx.copy(rawMessage = replyId))) return@forEach
                }
            }

            // Any non-command message → execute /start as default
            commandProcessor.process(ctx.copy(rawMessage = "/start"))
        }
    }

    private fun processStatuses(value: WebhookValue) {
        value.statuses?.forEach { status ->
            log.debug("Status update: {} for message {}", status.status, status.id)
            eventPublisher.publishEvent(
                StatusUpdateEvent(this, status.id ?: "", status.status ?: "", status.recipient_id ?: "")
            )
        }
    }

    private fun parseMessage(msg: WebhookMessage, senderName: String): IncomingMessage {
        return IncomingMessage(
            from = msg.from,
            senderName = senderName,
            messageId = msg.id,
            timestamp = msg.timestamp?.toLongOrNull() ?: (System.currentTimeMillis() / 1000),
            type = MessageType.from(msg.type),
            text = msg.text?.body,
            caption = msg.image?.caption ?: msg.video?.caption ?: msg.document?.caption,
            mediaId = msg.image?.id ?: msg.video?.id ?: msg.audio?.id ?: msg.document?.id ?: msg.sticker?.id,
            mimeType = msg.image?.mime_type ?: msg.video?.mime_type ?: msg.audio?.mime_type ?: msg.document?.mime_type,
            latitude = msg.location?.latitude,
            longitude = msg.location?.longitude,
            locationName = msg.location?.name,
            locationAddress = msg.location?.address,
            buttonReplyId = msg.interactive?.button_reply?.id ?: msg.button?.payload,
            buttonReplyTitle = msg.interactive?.button_reply?.title ?: msg.button?.text,
            listReplyId = msg.interactive?.list_reply?.id,
            listReplyTitle = msg.interactive?.list_reply?.title,
            quotedMessageId = msg.context?.id,
            filename = msg.document?.filename
        )
    }
}
