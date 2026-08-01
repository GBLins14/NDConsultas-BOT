package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.*

interface WhatsappService {

    // ── Text ────────────────────────────────────────────────────────
    fun sendMessage(to: String, text: String): MessageResponse
    fun reply(to: String, messageId: String, text: String): MessageResponse

    // ── Media (by URL) ──────────────────────────────────────────────
    fun sendImage(to: String, imageUrl: String, caption: String? = null): MessageResponse
    fun sendVideo(to: String, videoUrl: String, caption: String? = null): MessageResponse
    fun sendAudio(to: String, audioUrl: String): MessageResponse
    fun sendDocument(to: String, documentUrl: String, filename: String, caption: String? = null): MessageResponse
    fun sendSticker(to: String, stickerUrl: String): MessageResponse

    // ── Location ────────────────────────────────────────────────────
    fun sendLocation(to: String, latitude: Double, longitude: Double, name: String? = null, address: String? = null): MessageResponse

    // ── Contact card ────────────────────────────────────────────────
    fun sendContact(to: String, contact: ContactCard): MessageResponse

    // ── Interactive ─────────────────────────────────────────────────
    fun sendButtons(to: String, body: String, buttons: List<Button>, header: String? = null, footer: String? = null): MessageResponse
    fun sendList(to: String, body: String, buttonLabel: String, sections: List<ListSection>, header: String? = null, footer: String? = null): MessageResponse

    // ── Template ────────────────────────────────────────────────────
    fun sendTemplate(to: String, templateName: String, languageCode: String = "pt_BR", components: List<TemplateComponent>? = null): MessageResponse

    // ── Reaction ────────────────────────────────────────────────────
    fun sendReaction(to: String, messageId: String, emoji: String): MessageResponse
    fun removeReaction(to: String, messageId: String): MessageResponse

    // ── Media upload + send by ID ──────────────────────────────────
    fun uploadMedia(fileBytes: ByteArray, mimeType: String, filename: String): String
    fun sendImageById(to: String, mediaId: String, caption: String? = null): MessageResponse
    fun sendDocumentById(to: String, mediaId: String, filename: String, caption: String? = null): MessageResponse

    // ── Status ──────────────────────────────────────────────────────
    fun markAsRead(messageId: String)
}
