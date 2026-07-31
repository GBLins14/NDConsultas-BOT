package com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.api

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ContactCard
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.TemplateComponent

// ── REST API request DTOs ───────────────────────────────────────────

data class SendTextRequest(
    val to: String,
    val message: String
)

data class SendMediaRequest(
    val to: String,
    val url: String,
    val caption: String? = null,
    val filename: String? = null
)

data class SendLocationRequest(
    val to: String,
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val address: String? = null
)

data class SendButtonsRequest(
    val to: String,
    val body: String,
    val buttons: List<Button>,
    val header: String? = null,
    val footer: String? = null
)

data class SendListRequest(
    val to: String,
    val body: String,
    val buttonLabel: String,
    val sections: List<ListSection>,
    val header: String? = null,
    val footer: String? = null
)

data class SendTemplateRequest(
    val to: String,
    val templateName: String,
    val languageCode: String = "pt_BR",
    val components: List<TemplateComponent>? = null
)

data class SendReactionRequest(
    val to: String,
    val messageId: String,
    val emoji: String
)

data class SendContactRequest(
    val to: String,
    val contact: ContactCard
)

data class ReplyRequest(
    val to: String,
    val messageId: String,
    val message: String
)

data class MarkAsReadRequest(
    val messageId: String
)

// ── WhatsApp API response (from Meta) ───────────────────────────────

data class WhatsappApiResponse(
    val messaging_product: String? = null,
    val contacts: List<WhatsappApiContact>? = null,
    val messages: List<WhatsappApiMessageId>? = null
)

data class WhatsappApiContact(
    val input: String? = null,
    val wa_id: String? = null
)

data class WhatsappApiMessageId(
    val id: String? = null
)
