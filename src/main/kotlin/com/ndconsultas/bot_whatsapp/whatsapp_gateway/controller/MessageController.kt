package com.ndconsultas.bot_whatsapp.whatsapp_gateway.controller

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.api.*
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.MessageResponse
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/messages")
class MessageController(
    private val whatsappService: WhatsappService
) {
    @PostMapping("/text")
    fun sendText(@RequestBody req: SendTextRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendMessage(req.to, req.message))

    @PostMapping("/image")
    fun sendImage(@RequestBody req: SendMediaRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendImage(req.to, req.url, req.caption))

    @PostMapping("/video")
    fun sendVideo(@RequestBody req: SendMediaRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendVideo(req.to, req.url, req.caption))

    @PostMapping("/audio")
    fun sendAudio(@RequestBody req: SendMediaRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendAudio(req.to, req.url))

    @PostMapping("/document")
    fun sendDocument(@RequestBody req: SendMediaRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendDocument(req.to, req.url, req.filename ?: "document", req.caption))

    @PostMapping("/sticker")
    fun sendSticker(@RequestBody req: SendMediaRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendSticker(req.to, req.url))

    @PostMapping("/location")
    fun sendLocation(@RequestBody req: SendLocationRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendLocation(req.to, req.latitude, req.longitude, req.name, req.address))

    @PostMapping("/contact")
    fun sendContact(@RequestBody req: SendContactRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendContact(req.to, req.contact))

    @PostMapping("/buttons")
    fun sendButtons(@RequestBody req: SendButtonsRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendButtons(req.to, req.body, req.buttons, req.header, req.footer))

    @PostMapping("/list")
    fun sendList(@RequestBody req: SendListRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendList(req.to, req.body, req.buttonLabel, req.sections, req.header, req.footer))

    @PostMapping("/template")
    fun sendTemplate(@RequestBody req: SendTemplateRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendTemplate(req.to, req.templateName, req.languageCode, req.components))

    @PostMapping("/reaction")
    fun sendReaction(@RequestBody req: SendReactionRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.sendReaction(req.to, req.messageId, req.emoji))

    @PostMapping("/reply")
    fun reply(@RequestBody req: ReplyRequest): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(whatsappService.reply(req.to, req.messageId, req.message))

    @PostMapping("/mark-read")
    fun markAsRead(@RequestBody req: MarkAsReadRequest): ResponseEntity<Void> {
        whatsappService.markAsRead(req.messageId)
        return ResponseEntity.ok().build()
    }
}
