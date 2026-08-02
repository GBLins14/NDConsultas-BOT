package com.ndconsultas.bot_whatsapp.whatsapp_gateway.controller

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandProcessor
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.AsaasProperties
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/payment")
class PaymentWebhookController(
    private val paymentService: PaymentService,
    private val commandProcessor: CommandProcessor,
    private val whatsappService: WhatsappService,
    private val asaasProperties: AsaasProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(PaymentWebhookController::class.java)

        private val PAID_EVENTS = setOf(
            "PAYMENT_RECEIVED",
            "PAYMENT_CONFIRMED"
        )
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestHeader("asaas-access-token", required = false) webhookToken: String?,
        @RequestBody payload: Map<String, Any?>
    ): ResponseEntity<Any> {

        // Validar token do webhook (se configurado)
        if (asaasProperties.webhookToken.isNotBlank() && webhookToken != asaasProperties.webhookToken) {
            log.warn("Webhook Asaas com token inválido")
            return ResponseEntity.status(401).build()
        }

        val event = payload["event"]?.toString()
        val paymentData = payload["payment"] as? Map<*, *>
        val paymentId = paymentData?.get("id")?.toString()

        log.info("Webhook Asaas recebido: event={}, paymentId={}", event, paymentId)

        if (event == null || event !in PAID_EVENTS) {
            log.info("Webhook Asaas ignorado: event={}", event)
            return ResponseEntity.ok().build()
        }

        if (paymentId.isNullOrBlank()) {
            log.warn("Webhook Asaas sem paymentId: {}", payload)
            return ResponseEntity.ok().build()
        }

        return try {
            val session = paymentService.confirmPayment(paymentId)

            if (session == null) {
                log.warn("Nenhuma sessão encontrada para paymentId={}", paymentId)
                return ResponseEntity.ok().build()
            }

            whatsappService.sendMessage(
                session.userPhone,
                "Pagamento de *R\$ ${"%.2f".format(session.price)}* confirmado!"
            )

            commandProcessor.process(
                CommandContext(
                    from = session.userPhone,
                    senderName = "",
                    messageId = "",
                    args = emptyList(),
                    rawMessage = "/consultar pago"
                )
            )

            log.info("Consulta liberada para {} (paymentId={})", session.userPhone, paymentId)

            ResponseEntity.ok().build()
        } catch (e: Exception) {
            log.error("Erro processando webhook Asaas {}", paymentId, e)
            ResponseEntity.ok().build()
        }
    }
}
