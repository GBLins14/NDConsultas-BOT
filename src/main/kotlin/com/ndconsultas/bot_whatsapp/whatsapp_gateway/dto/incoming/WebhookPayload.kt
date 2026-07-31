package com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming

// ── Root webhook payload from Meta ──────────────────────────────────

data class WebhookPayload(
    val entry: List<WebhookEntry> = emptyList()
)

data class WebhookEntry(
    val id: String? = null,
    val changes: List<WebhookChange> = emptyList()
)

data class WebhookChange(
    val value: WebhookValue,
    val field: String? = null
)

data class WebhookValue(
    val messaging_product: String? = null,
    val metadata: WebhookMetadata? = null,
    val contacts: List<WebhookContact>? = null,
    val messages: List<WebhookMessage>? = null,
    val statuses: List<WebhookStatus>? = null
)

// ── Metadata ────────────────────────────────────────────────────────

data class WebhookMetadata(
    val display_phone_number: String? = null,
    val phone_number_id: String? = null
)

// ── Contact / profile ───────────────────────────────────────────────

data class WebhookContact(
    val profile: WebhookProfile? = null,
    val wa_id: String? = null
)

data class WebhookProfile(
    val name: String? = null
)

// ── Incoming message ────────────────────────────────────────────────

data class WebhookMessage(
    val from: String,
    val id: String,
    val timestamp: String? = null,
    val type: String? = null,
    val text: WebhookText? = null,
    val image: WebhookMedia? = null,
    val video: WebhookMedia? = null,
    val audio: WebhookMedia? = null,
    val document: WebhookDocument? = null,
    val sticker: WebhookMedia? = null,
    val location: WebhookLocation? = null,
    val contacts: List<WebhookContactCard>? = null,
    val interactive: WebhookInteractive? = null,
    val button: WebhookButtonReply? = null,
    val context: WebhookContext? = null
)

data class WebhookText(
    val body: String? = null
)

data class WebhookMedia(
    val id: String? = null,
    val mime_type: String? = null,
    val sha256: String? = null,
    val caption: String? = null
)

data class WebhookDocument(
    val id: String? = null,
    val mime_type: String? = null,
    val sha256: String? = null,
    val caption: String? = null,
    val filename: String? = null
)

data class WebhookLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val name: String? = null,
    val address: String? = null
)

data class WebhookContactCard(
    val name: WebhookContactName? = null,
    val phones: List<WebhookContactPhone>? = null
)

data class WebhookContactName(
    val formatted_name: String? = null
)

data class WebhookContactPhone(
    val phone: String? = null,
    val type: String? = null
)

// ── Interactive replies ─────────────────────────────────────────────

data class WebhookInteractive(
    val type: String? = null,
    val button_reply: WebhookInteractiveReply? = null,
    val list_reply: WebhookInteractiveReply? = null
)

data class WebhookInteractiveReply(
    val id: String? = null,
    val title: String? = null
)

data class WebhookButtonReply(
    val payload: String? = null,
    val text: String? = null
)

// ── Quote context ───────────────────────────────────────────────────

data class WebhookContext(
    val from: String? = null,
    val id: String? = null
)

// ── Delivery / read statuses ────────────────────────────────────────

data class WebhookStatus(
    val id: String? = null,
    val status: String? = null,
    val timestamp: String? = null,
    val recipient_id: String? = null,
    val errors: List<WebhookError>? = null
)

data class WebhookError(
    val code: Int? = null,
    val title: String? = null
)
