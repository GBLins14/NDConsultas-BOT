package com.ndconsultas.bot_whatsapp.whatsapp_gateway.model

// ── Interactive button ──────────────────────────────────────────────
data class Button(
    val id: String,
    val title: String
)

// ── Interactive list ────────────────────────────────────────────────
data class ListSection(
    val title: String,
    val rows: List<ListRow>
)

data class ListRow(
    val id: String,
    val title: String,
    val description: String? = null
)

// ── Contact card ────────────────────────────────────────────────────
data class ContactCard(
    val firstName: String,
    val lastName: String? = null,
    val phones: List<String>,
    val emails: List<String>? = null,
    val organization: String? = null
)

// ── Template ────────────────────────────────────────────────────────
data class TemplateComponent(
    val type: String,
    val parameters: List<TemplateParameter>
)

data class TemplateParameter(
    val type: String,
    val text: String? = null,
    val mediaUrl: String? = null
)

// ── API response returned to callers ────────────────────────────────
data class MessageResponse(
    val messageId: String,
    val whatsappId: String? = null
)
