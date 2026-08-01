package com.ndconsultas.bot_whatsapp.whatsapp_gateway.webhook

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandProcessor
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookMessage
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookPayload
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookValue
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.event.StatusUpdateEvent
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.IncomingMessage
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.MessageType
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentSessionManager
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
    private val sessionManager: ConsultationSessionManager,
    private val adminService: AdminService,
    private val paymentSessionManager: PaymentSessionManager
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

            // ── Check ban (admin nunca e afetado) ──────────────────
            if (adminService.isBanned(message.from) && !adminService.isAdmin(message.from)) {
                log.info("Mensagem de numero banido ignorada: {}", message.from)
                whatsappService.sendMessage(message.from, "Seu acesso foi bloqueado. Entre em contato com o administrador.")
                return@forEach
            }

            val ctx = CommandContext(
                from = message.from,
                senderName = senderName,
                messageId = message.id,
                args = emptyList(),
                rawMessage = ""
            )

            // ── Text messages ──────────────────────────────────────
            if (incoming.type == MessageType.TEXT && incoming.text != null) {
                val textCtx = ctx.copy(rawMessage = incoming.text)

                // 1. Tentar processar como comando
                if (commandProcessor.process(textCtx)) return@forEach

                // 2. Admin: verificar acao pendente (ban/unban input)
                if (adminService.isAdmin(message.from)) {
                    val pendingAction = adminService.consumePendingAction(message.from)
                    if (pendingAction != null) {
                        val adminCtx = ctx.copy(rawMessage = "/admin $pendingAction ${incoming.text}")
                        commandProcessor.process(adminCtx)
                        return@forEach
                    }
                }

                // 3. Consulta pendente: rotear texto como dado de consulta
                val pending = sessionManager.getPending(message.from)
                if (pending != null) {
                    val consultCtx = ctx.copy(rawMessage = "/consultar ${pending.tipo} ${incoming.text}")
                    commandProcessor.process(consultCtx)
                    return@forEach
                }

                // 4. Pagamento PIX pendente: lembrar o usuário
                val paymentSession = paymentSessionManager.getSession(message.from)
                if (paymentSession != null && paymentSession.status == PaymentSessionManager.PaymentStatus.AWAITING_PAYMENT) {
                    whatsappService.sendMessage(
                        message.from,
                        "Você possui um pagamento pendente de *R\$ ${"%.2f".format(paymentSession.price)}* para *${paymentSession.tipoLabel}*.\n\nEfetue o pagamento via PIX para liberar a consulta."
                    )
                    whatsappService.sendButtons(
                        to = message.from,
                        body = "O que deseja fazer?",
                        buttons = listOf(
                            Button(id = "/consultar cancelar_pgto", title = "Cancelar Consulta"),
                            Button(id = "/start", title = "Menu Inicial")
                        )
                    )
                    return@forEach
                }
            }

            // ── Interactive replies (button / list) ────────────────
            if (incoming.type == MessageType.INTERACTIVE) {
                val replyId = incoming.buttonReplyId ?: incoming.listReplyId
                if (replyId != null && replyId.startsWith("/")) {
                    if (commandProcessor.process(ctx.copy(rawMessage = replyId))) return@forEach
                }
            }

            // ── Default: /start ────────────────────────────────────
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
