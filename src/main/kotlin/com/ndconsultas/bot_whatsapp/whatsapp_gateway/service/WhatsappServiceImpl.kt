package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.client.WhatsappApiClient
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.outgoing.*
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WhatsappServiceImpl(
    private val apiClient: WhatsappApiClient
) : WhatsappService {

    companion object {
        private val log = LoggerFactory.getLogger(WhatsappServiceImpl::class.java)
    }

    // ── Text ────────────────────────────────────────────────────────

    override fun sendMessage(to: String, text: String): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "text",
            text = TextBody(body = text)
        )
        return send(payload)
    }

    override fun reply(to: String, messageId: String, text: String): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "text",
            text = TextBody(body = text),
            context = ContextBody(message_id = messageId)
        )
        return send(payload)
    }

    // ── Media ───────────────────────────────────────────────────────

    override fun sendImage(to: String, imageUrl: String, caption: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "image",
            image = MediaBody(link = imageUrl, caption = caption)
        )
        return send(payload)
    }

    override fun sendVideo(to: String, videoUrl: String, caption: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "video",
            video = MediaBody(link = videoUrl, caption = caption)
        )
        return send(payload)
    }

    override fun sendAudio(to: String, audioUrl: String): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "audio",
            audio = MediaBody(link = audioUrl)
        )
        return send(payload)
    }

    override fun sendDocument(to: String, documentUrl: String, filename: String, caption: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "document",
            document = DocumentBody(link = documentUrl, filename = filename, caption = caption)
        )
        return send(payload)
    }

    override fun sendSticker(to: String, stickerUrl: String): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "sticker",
            sticker = MediaBody(link = stickerUrl)
        )
        return send(payload)
    }

    // ── Location ────────────────────────────────────────────────────

    override fun sendLocation(to: String, latitude: Double, longitude: Double, name: String?, address: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "location",
            location = LocationBody(
                latitude = latitude,
                longitude = longitude,
                name = name,
                address = address
            )
        )
        return send(payload)
    }

    // ── Contact ─────────────────────────────────────────────────────

    override fun sendContact(to: String, contact: ContactCard): MessageResponse {
        val fullName = listOfNotNull(contact.firstName, contact.lastName).joinToString(" ")
        val payload = MessagePayload(
            to = to,
            type = "contacts",
            contacts = listOf(
                ContactPayload(
                    name = ContactNameBody(
                        formatted_name = fullName,
                        first_name = contact.firstName,
                        last_name = contact.lastName
                    ),
                    phones = contact.phones.map { ContactPhoneBody(phone = it) },
                    emails = contact.emails?.map { ContactEmailBody(email = it) },
                    org = contact.organization?.let { ContactOrgBody(company = it) }
                )
            )
        )
        return send(payload)
    }

    // ── Interactive ─────────────────────────────────────────────────

    override fun sendButtons(to: String, body: String, buttons: List<Button>, header: String?, footer: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "interactive",
            interactive = InteractiveBody(
                type = "button",
                header = header?.let { InteractiveHeader(text = it) },
                body = InteractiveTextBody(text = body),
                footer = footer?.let { InteractiveTextBody(text = it) },
                action = InteractiveAction(
                    buttons = buttons.map { btn ->
                        InteractiveButton(reply = InteractiveReply(id = btn.id, title = btn.title))
                    }
                )
            )
        )
        return send(payload)
    }

    override fun sendList(to: String, body: String, buttonLabel: String, sections: List<ListSection>, header: String?, footer: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "interactive",
            interactive = InteractiveBody(
                type = "list",
                header = header?.let { InteractiveHeader(text = it) },
                body = InteractiveTextBody(text = body),
                footer = footer?.let { InteractiveTextBody(text = it) },
                action = InteractiveAction(
                    button = buttonLabel,
                    sections = sections.map { section ->
                        InteractiveSectionBody(
                            title = section.title,
                            rows = section.rows.map { row ->
                                InteractiveRowBody(
                                    id = row.id,
                                    title = row.title,
                                    description = row.description
                                )
                            }
                        )
                    }
                )
            )
        )
        return send(payload)
    }

    // ── Template ────────────────────────────────────────────────────

    override fun sendTemplate(to: String, templateName: String, languageCode: String, components: List<TemplateComponent>?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "template",
            template = TemplatePayloadBody(
                name = templateName,
                language = TemplateLanguageBody(code = languageCode),
                components = components?.map { comp ->
                    TemplateComponentBody(
                        type = comp.type,
                        parameters = comp.parameters.map { param ->
                            TemplateParameterBody(
                                type = param.type,
                                text = param.text,
                                image = param.mediaUrl?.let { MediaBody(link = it) }
                            )
                        }
                    )
                }
            )
        )
        return send(payload)
    }

    // ── Reaction ────────────────────────────────────────────────────

    override fun sendReaction(to: String, messageId: String, emoji: String): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "reaction",
            reaction = ReactionBody(message_id = messageId, emoji = emoji)
        )
        return send(payload)
    }

    override fun removeReaction(to: String, messageId: String): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "reaction",
            reaction = ReactionBody(message_id = messageId, emoji = "")
        )
        return send(payload)
    }

    // ── Media upload + document by ID ─────────────────────────────

    override fun uploadMedia(fileBytes: ByteArray, mimeType: String, filename: String): String {
        return apiClient.uploadMedia(fileBytes, mimeType, filename)
    }

    override fun sendDocumentById(to: String, mediaId: String, filename: String, caption: String?): MessageResponse {
        val payload = MessagePayload(
            to = to,
            type = "document",
            document = DocumentBody(id = mediaId, filename = filename, caption = caption)
        )
        return send(payload)
    }

    // ── Status ──────────────────────────────────────────────────────

    override fun markAsRead(messageId: String) {
        apiClient.markAsRead(MarkAsReadPayload(message_id = messageId))
    }

    // ── Internal ────────────────────────────────────────────────────

    private fun send(payload: MessagePayload): MessageResponse {
        val response = apiClient.sendMessage(payload)
        return MessageResponse(
            messageId = response.messages?.firstOrNull()?.id ?: "",
            whatsappId = response.contacts?.firstOrNull()?.wa_id
        )
    }
}
