package com.ndconsultas.bot_whatsapp.whatsapp_gateway.controller

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandProcessor
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
    private val whatsappService: WhatsappService
) {

    companion object {
        private val log = LoggerFactory.getLogger(PaymentWebhookController::class.java)

        private val PAID_STATUSES = setOf(
            "completed",
            "confirmed",
            "approved",
            "paid",
            "paid_out"
        )
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: Map<String, Any?>
    ): ResponseEntity<Map<String, Any>> {

        val data = (payload["data"] as? Map<*, *>) ?: payload

        val transactionId =
            data["id"]?.toString()
                ?: data["idtransaction"]?.toString()

        val status =
            data["status"]
                ?.toString()
                ?.trim()
                ?.lowercase()

        log.info(
            "Webhook SyncPay recebido: status={}, id={}",
            status,
            transactionId
        )

        if (transactionId.isNullOrBlank()) {
            log.warn("Webhook sem id da transação: {}", payload)
            return ResponseEntity.ok(emptyMap())
        }

        if (status == null || status !in PAID_STATUSES) {
            log.info(
                "Webhook ignorado. Status={} Transaction={}",
                status,
                transactionId
            )
            return ResponseEntity.ok(emptyMap())
        }

        return try {

            val session = paymentService.confirmPixPayment(transactionId)

            if (session == null) {
                log.warn(
                    "Nenhuma sessão encontrada para transactionId={}",
                    transactionId
                )
                return ResponseEntity.ok(emptyMap())
            }

            whatsappService.sendMessage(
                session.userPhone,
                "✅ Pagamento de *R$ ${"%.2f".format(session.price)}* confirmado!"
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

            log.info(
                "Consulta liberada para {} (transaction={})",
                session.userPhone,
                transactionId
            )

            ResponseEntity.ok(emptyMap())

        } catch (e: Exception) {

            log.error(
                "Erro processando webhook {}",
                transactionId,
                e
            )

            ResponseEntity.ok(emptyMap())
        }
    }
}