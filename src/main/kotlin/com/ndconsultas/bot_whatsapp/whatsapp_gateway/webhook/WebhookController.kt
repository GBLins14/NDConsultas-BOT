package com.ndconsultas.bot_whatsapp.whatsapp_gateway.webhook

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.WhatsappProperties
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.dto.incoming.WebhookPayload
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/webhook")
class WebhookController(
    private val properties: WhatsappProperties,
    private val webhookProcessor: WebhookProcessor
) {
    companion object {
        private val log = LoggerFactory.getLogger(WebhookController::class.java)
    }

    @GetMapping
    fun verify(
        @RequestParam("hub.mode") mode: String,
        @RequestParam("hub.verify_token") token: String,
        @RequestParam("hub.challenge") challenge: String
    ): ResponseEntity<String> {
        log.info("Webhook verification — mode: {}", mode)
        if (mode == "subscribe" && token == properties.verifyToken) {
            log.info("Webhook verified successfully")
            return ResponseEntity.ok(challenge)
        }
        log.warn("Webhook verification failed — invalid token")
        return ResponseEntity.status(403).build()
    }

    @PostMapping("/syncpay")
    fun receive(@RequestBody payload: WebhookPayload): ResponseEntity<Void> {
        log.debug("Webhook received — {} entries", payload.entry.size)
        webhookProcessor.process(payload)
        return ResponseEntity.ok().build()
    }
}
