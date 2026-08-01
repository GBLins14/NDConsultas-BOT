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
        // Status que indicam pagamento confirmado
        private val PAID_STATUSES = setOf("completed", "paid", "approved", "confirmed")
    }

    /**
     * Webhook SyncPay - formato OnUpdate (cashin.update)
     *
     * Payload:
     * {
     *   "id": "uuid",          <- identificador da transacao
     *   "status": "completed", <- status
     *   "amount": 10,
     *   "final_amount": 9.4,
     *   ...
     * }
     */
    @PostMapping("/webhook")
    fun handleWebhook(@RequestBody payload: Map<String, Any?>): ResponseEntity<Any> {
        log.info("Webhook SyncPay recebido: status={}, id={}", payload["status"], payload["id"])

        val transactionId = payload["id"]?.toString()
        val status = payload["status"]?.toString()

        if (transactionId.isNullOrBlank()) {
            log.warn("Webhook sem 'id': {}", payload)
            return ResponseEntity.ok(emptyMap<String, Any>())
        }

        if (status in PAID_STATUSES) {
            val session = paymentService.confirmPixPayment(transactionId)

            if (session != null) {
                whatsappService.sendMessage(
                    session.userPhone,
                    "Pagamento de *R\$ ${"%.2f".format(session.price)}* confirmado!"
                )

                val ctx = CommandContext(
                    from = session.userPhone,
                    senderName = "",
                    messageId = "",
                    args = emptyList(),
                    rawMessage = "/consultar pago"
                )
                commandProcessor.process(ctx)

                log.info("Consulta disparada apos pagamento confirmado: {}", session.userPhone)
            } else {
                log.warn("Nenhuma sessao encontrada para transaction id: {}", transactionId)
            }
        } else {
            log.info("Webhook com status nao-final ignorado: {} (id={})", status, transactionId)
        }

        // SyncPay espera resposta em até 5 segundos
        return ResponseEntity.ok(emptyMap<String, Any>())
    }
}
