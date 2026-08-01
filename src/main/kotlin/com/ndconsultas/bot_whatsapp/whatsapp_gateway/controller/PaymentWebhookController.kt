package com.ndconsultas.bot_whatsapp.whatsapp_gateway.controller

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandProcessor
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/payment")
class PaymentWebhookController(
    private val paymentService: PaymentService,
    private val commandProcessor: CommandProcessor,
    private val whatsappService: WhatsappService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PaymentWebhookController::class.java)
    }

    /**
     * Webhook do SyncPay (formato OLD OnCreate/OnUpdate).
     *
     * Payload esperado:
     * {
     *   "data": {
     *     "id": "uuid",
     *     "idtransaction": "uuid",
     *     "status": "pending" | "paid" | ...,
     *     "amount": 1500,       // centavos
     *     "end_to_end": "...",
     *     ...
     *   }
     * }
     */
    @PostMapping("/webhook")
    fun handleWebhook(@RequestBody payload: Map<String, Any?>): ResponseEntity<Any> {
        log.info("Webhook SyncPay recebido: {}", payload)

        val data = payload["data"] as? Map<*, *>
        if (data == null) {
            log.warn("Webhook sem campo 'data': {}", payload)
            return ResponseEntity.ok(mapOf("received" to true))
        }

        val status = data["status"]?.toString()
        val transactionId = data["idtransaction"]?.toString() ?: data["id"]?.toString()

        if (transactionId.isNullOrBlank()) {
            log.warn("Webhook sem identifier: {}", data)
            return ResponseEntity.ok(mapOf("received" to true))
        }

        log.info("Webhook SyncPay: status={}, transaction={}", status, transactionId)

        // Verificar se e confirmacao de pagamento
        if (status in listOf("paid", "approved", "completed", "confirmed")) {
            val session = paymentService.confirmPixPayment(transactionId)

            if (session != null) {
                // Notificar o usuario
                whatsappService.sendMessage(
                    session.userPhone,
                    "Pagamento de *R\$ ${"%.2f".format(session.price)}* confirmado!"
                )

                // Disparar a consulta automaticamente
                val ctx = CommandContext(
                    from = session.userPhone,
                    senderName = "",
                    messageId = "",
                    args = emptyList(),
                    rawMessage = "/consultar pago"
                )
                commandProcessor.process(ctx)
            }
        } else {
            log.info("Webhook com status nao-final: {} (transaction={})", status, transactionId)
        }

        return ResponseEntity.ok(mapOf("received" to true))
    }
}
