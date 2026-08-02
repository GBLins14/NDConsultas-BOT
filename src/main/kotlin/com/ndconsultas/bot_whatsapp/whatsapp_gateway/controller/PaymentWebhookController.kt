package com.ndconsultas.bot_whatsapp.whatsapp_gateway.controller

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandProcessor
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.SyncPayProperties
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/v1/payment")
class PaymentWebhookController(
    private val paymentService: PaymentService,
    private val commandProcessor: CommandProcessor,
    private val whatsappService: WhatsappService,
    private val syncPayProperties: SyncPayProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(PaymentWebhookController::class.java)

        private val PAID_STATUSES = setOf("completed")
    }

    // Idempotência: guarda IDs já processados (evita pagamento duplicado)
    private val processedIds = ConcurrentHashMap.newKeySet<String>()

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestHeader("Authorization", required = false) authHeader: String?,
        @RequestBody payload: Map<String, Any?>
    ): ResponseEntity<Map<String, Any>> {

        // Validar autenticação do webhook
        if (syncPayProperties.webhookSecret.isNotBlank()) {
            val expectedToken = "Bearer ${syncPayProperties.webhookSecret}"
            if (authHeader != expectedToken) {
                log.warn("Webhook SyncPay com autenticação inválida")
                return ResponseEntity.status(401).build()
            }
        }

        val data = (payload["data"] as? Map<*, *>) ?: payload

        val transactionId =
            data["id"]?.toString()
                ?: data["idtransaction"]?.toString()

        val status =
            data["status"]
                ?.toString()
                ?.trim()
                ?.lowercase()

        log.info("Webhook SyncPay recebido: status={}, id={}", status, transactionId)

        if (transactionId.isNullOrBlank()) {
            log.warn("Webhook sem id da transação: {}", payload)
            return ResponseEntity.ok(emptyMap())
        }

        if (status == null || status !in PAID_STATUSES) {
            log.info("Webhook ignorado. Status={} Transaction={}", status, transactionId)
            return ResponseEntity.ok(emptyMap())
        }

        // Idempotência: ignorar se já foi processado
        if (!processedIds.add(transactionId)) {
            log.info("Webhook duplicado ignorado: transactionId={}", transactionId)
            return ResponseEntity.ok(emptyMap())
        }

        return try {
            val session = paymentService.confirmPixPayment(transactionId)

            if (session == null) {
                processedIds.remove(transactionId)
                log.warn("Nenhuma sessão encontrada para transactionId={}", transactionId)
                return ResponseEntity.ok(emptyMap())
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

            log.info("Consulta liberada para {} (transaction={})", session.userPhone, transactionId)

            ResponseEntity.ok(emptyMap())
        } catch (e: Exception) {
            processedIds.remove(transactionId)
            log.error("Erro processando webhook {}", transactionId, e)
            ResponseEntity.ok(emptyMap())
        }
    }
}