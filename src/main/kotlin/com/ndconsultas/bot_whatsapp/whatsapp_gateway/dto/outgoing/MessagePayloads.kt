package com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.outgoing

// ── Main payload sent to POST /{phone-number-id}/messages ───────────

data class MessagePayload(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String,
    val text: TextBody? = null,
    val image: MediaBody? = null,
    val video: MediaBody? = null,
    val audio: MediaBody? = null,
    val document: DocumentBody? = null,
    val sticker: MediaBody? = null,
    val location: LocationBody? = null,
    val contacts: List<ContactPayload>? = null,
    val interactive: InteractiveBody? = null,
    val template: TemplatePayloadBody? = null,
    val reaction: ReactionBody? = null,
    val context: ContextBody? = null
)

// ── Mark-as-read payload ────────────────────────────────────────────

data class MarkAsReadPayload(
    val messaging_product: String = "whatsapp",
    val status: String = "read",
    val message_id: String
)

// ── Text ────────────────────────────────────────────────────────────

data class TextBody(
    val body: String,
    val preview_url: Boolean? = null
)

// ── Media (image / video / sticker) ─────────────────────────────────

data class MediaBody(
    val link: String? = null,
    val id: String? = null,
    val caption: String? = null
)

// ── Document ────────────────────────────────────────────────────────

data class DocumentBody(
    val link: String? = null,
    val id: String? = null,
    val caption: String? = null,
    val filename: String? = null
)

// ── Location ────────────────────────────────────────────────────────

data class LocationBody(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val address: String? = null
)

// ── Contact ─────────────────────────────────────────────────────────

data class ContactPayload(
    val name: ContactNameBody,
    val phones: List<ContactPhoneBody>? = null,
    val emails: List<ContactEmailBody>? = null,
    val org: ContactOrgBody? = null
)

data class ContactNameBody(
    val formatted_name: String,
    val first_name: String? = null,
    val last_name: String? = null
)

data class ContactPhoneBody(
    val phone: String,
    val type: String = "CELL"
)

data class ContactEmailBody(
    val email: String,
    val type: String = "WORK"
)

data class ContactOrgBody(
    val company: String
)

// ── Interactive (buttons / list) ────────────────────────────────────

data class InteractiveBody(
    val type: String,
    val header: InteractiveHeader? = null,
    val body: InteractiveTextBody,
    val footer: InteractiveTextBody? = null,
    val action: InteractiveAction
)

data class InteractiveHeader(
    val type: String = "text",
    val text: String
)

data class InteractiveTextBody(
    val text: String
)

data class InteractiveAction(
    val buttons: List<InteractiveButton>? = null,
    val button: String? = null,
    val sections: List<InteractiveSectionBody>? = null
)

data class InteractiveButton(
    val type: String = "reply",
    val reply: InteractiveReply
)

data class InteractiveReply(
    val id: String,
    val title: String
)

data class InteractiveSectionBody(
    val title: String,
    val rows: List<InteractiveRowBody>
)

data class InteractiveRowBody(
    val id: String,
    val title: String,
    val description: String? = null
)

// ── Template ────────────────────────────────────────────────────────

data class TemplatePayloadBody(
    val name: String,
    val language: TemplateLanguageBody,
    val components: List<TemplateComponentBody>? = null
)

data class TemplateLanguageBody(
    val code: String
)

data class TemplateComponentBody(
    val type: String,
    val parameters: List<TemplateParameterBody>? = null
)

data class TemplateParameterBody(
    val type: String,
    val text: String? = null,
    val image: MediaBody? = null,
    val document: DocumentBody? = null,
    val video: MediaBody? = null
)

// ── Reaction ────────────────────────────────────────────────────────

data class ReactionBody(
    val message_id: String,
    val emoji: String
)

// ── Reply context ───────────────────────────────────────────────────

data class ContextBody(
    val message_id: String
)
