package com.ndconsultas.bot_whatsapp.whatsapp_gateway.event

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.IncomingMessage
import org.springframework.context.ApplicationEvent

class MessageReceivedEvent(
    source: Any,
    val message: IncomingMessage
) : ApplicationEvent(source)

class StatusUpdateEvent(
    source: Any,
    val messageId: String,
    val status: String,
    val recipientId: String
) : ApplicationEvent(source)
