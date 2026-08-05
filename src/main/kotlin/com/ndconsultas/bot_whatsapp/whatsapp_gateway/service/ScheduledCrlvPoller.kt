package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.QueryTypeRegistry
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.ScheduledCrlvOrderEntity
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.ScheduledCrlvOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ScheduledCrlvPoller(
    private val orderRepository: ScheduledCrlvOrderRepository,
    private val consultationService: VehicleConsultationService,
    private val whatsappService: WhatsappService,
    private val adminService: AdminService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ScheduledCrlvPoller::class.java)
        private const val MAX_AGE_HOURS = 48L
        private const val NOTIFY_ADMIN_AT_FAIL = 5
        private const val MARK_FAILED_AT_FAIL = 15
        private val FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        private val USER_ERROR_PATTERNS = listOf(
            "invalida", "invalido", "invalidos", "invalidas",
            "nao encontrad", "não encontrad",
            "formato", "obrigatori"
        )

        fun isUserError(error: String?): Boolean {
            if (error.isNullOrBlank()) return false
            val lower = error.lowercase()
            return USER_ERROR_PATTERNS.any { lower.contains(it) }
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        val pending = orderRepository.findByStatusInOrderByCreatedAtAsc(listOf("PENDING"))
        if (pending.isNotEmpty()) {
            log.info("Recuperando {} pedidos CRLV agendados pendentes do banco de dados", pending.size)
            notifyAdmin(
                "Servidor reiniciado. *${pending.size}* pedido(s) CRLV agendado(s) pendente(s) recuperado(s) do banco. O polling sera retomado automaticamente."
            )
        }
    }

    @Scheduled(fixedRate = 120_000) // 2 minutos
    fun pollPendingOrders() {
        val pendingOrders = orderRepository.findByStatusInOrderByCreatedAtAsc(listOf("PENDING"))
        if (pendingOrders.isEmpty()) return

        log.info("Verificando {} pedidos CRLV agendados pendentes", pendingOrders.size)

        for (order in pendingOrders) {
            if (Duration.between(order.createdAt, Instant.now()).toHours() > MAX_AGE_HOURS) {
                expireOrder(order)
                continue
            }

            try {
                processOrder(order)
            } catch (e: Exception) {
                log.error("Erro ao processar pedido agendado {}: {}", order.pedidoId, e.message, e)
                recordFailure(order, "Erro interno: ${e.message}")
            }
        }
    }

    private fun processOrder(order: ScheduledCrlvOrderEntity) {
        val statusResult = consultationService.consultarStatusAgendado(order.pedidoId)

        if (!statusResult.success) {
            val error = statusResult.error ?: "Erro desconhecido"
            log.warn("Falha ao consultar status do pedido {}: {}", order.pedidoId, error)
            recordFailure(order, error)
            return
        }

        // Reset failCount on successful API response
        if (order.failCount > 0) {
            saveOrder(order, status = order.status, failCount = 0, lastError = null)
        }

        val normalizedStatus = statusResult.statusNormalizado ?: statusResult.status ?: return

        when {
            normalizedStatus == "concluido" || statusResult.pdfDisponivel -> handleCompleted(order)
            statusResult.cancelado || normalizedStatus == "cancelado" -> handleCancelled(order, statusResult.mensagemAdmin)
        }
    }

    private fun recordFailure(order: ScheduledCrlvOrderEntity, error: String) {
        val newFailCount = order.failCount + 1

        when (newFailCount) {
            NOTIFY_ADMIN_AT_FAIL -> {
                saveOrder(order, failCount = newFailCount, lastError = error)
                val stateName = resolveStateName(order.uf)
                notifyAdmin(
                    buildString {
                        append("*ALERTA: Pedido CRLV Agendado com falhas repetidas*\n\n")
                        append("Pedido: *#${order.pedidoId}*\n")
                        append("Estado: *$stateName*\n")
                        append("Placa: *${order.placa}*\n")
                        append("Cliente: ${order.userPhone}\n")
                        append("Falhas consecutivas: *$newFailCount*\n")
                        append("Ultimo erro: $error\n\n")
                        append("O sistema continuara tentando.")
                    }
                )
            }
            MARK_FAILED_AT_FAIL -> {
                saveOrder(order, status = "FAILED", failCount = newFailCount, lastError = error)
                val stateName = resolveStateName(order.uf)

                whatsappService.sendMessage(
                    order.userPhone,
                    buildString {
                        append("*CRLV-e Agendado — Erro*\n\n")
                        append("Houve um problema ao processar seu pedido de CRLV-e de *$stateName*.\n")
                        append("Placa: *${order.placa}*\n")
                        append("Pedido: *#${order.pedidoId}*\n\n")
                        append("Nossa equipe foi notificada e entrara em contato.")
                    }
                )

                notifyAdmin(
                    buildString {
                        append("*CRITICO: Pedido CRLV Agendado FALHOU*\n\n")
                        append("Pedido: *#${order.pedidoId}*\n")
                        append("Estado: *$stateName*\n")
                        append("Placa: *${order.placa}*\n")
                        append("Cliente: ${order.userPhone}\n")
                        append("Falhas: *$newFailCount*\n")
                        append("Ultimo erro: $error\n\n")
                        append("O cliente foi notificado. Verifique o pedido manualmente.")
                    }
                )

                log.error("Pedido {} marcado como FAILED apos {} falhas", order.pedidoId, newFailCount)
            }
            else -> {
                saveOrder(order, failCount = newFailCount, lastError = error)
            }
        }
    }

    private fun handleCompleted(order: ScheduledCrlvOrderEntity) {
        val pdfResult = consultationService.baixarPdfAgendado(order.pedidoId)

        if (!pdfResult.success || pdfResult.pdfBytes == null) {
            log.warn("PDF nao disponivel ainda para pedido {}: {}", order.pedidoId, pdfResult.error)
            recordFailure(order, "PDF concluido mas download falhou: ${pdfResult.error}")
            return
        }

        try {
            val stateName = resolveStateName(order.uf)
            val timestamp = LocalDateTime.now().format(FILE_DATE_FMT)
            val filename = "crlv_agendado_${order.uf}_${timestamp}.pdf"

            val mediaId = whatsappService.uploadMedia(pdfResult.pdfBytes, "application/pdf", filename)

            whatsappService.sendMessage(
                order.userPhone,
                buildString {
                    append("*CRLV-e Agendado — Pronto!*\n\n")
                    append("Seu CRLV-e de *$stateName* esta pronto.\n")
                    append("Placa: *${order.placa}*\n")
                    append("Pedido: *#${order.pedidoId}*\n\n")
                    append("Segue o documento em PDF:")
                }
            )

            whatsappService.sendDocumentById(
                to = order.userPhone,
                mediaId = mediaId,
                filename = filename,
                caption = "CRLV-e Agendado - $stateName - ${order.placa}"
            )

            saveOrder(order, status = "COMPLETED", failCount = 0, lastError = null)
            log.info("Pedido {} concluido e PDF enviado para {}", order.pedidoId, order.userPhone)
        } catch (e: Exception) {
            log.error("Erro ao enviar PDF do pedido {}: {}", order.pedidoId, e.message, e)
            recordFailure(order, "Envio WhatsApp falhou: ${e.message}")
        }
    }

    private fun handleCancelled(order: ScheduledCrlvOrderEntity, adminMessage: String?) {
        val stateName = resolveStateName(order.uf)

        whatsappService.sendMessage(
            order.userPhone,
            buildString {
                append("*CRLV-e Agendado — Cancelado*\n\n")
                append("Seu pedido de CRLV-e de *$stateName* foi cancelado.\n")
                append("Placa: *${order.placa}*\n")
                append("Pedido: *#${order.pedidoId}*")
                if (!adminMessage.isNullOrBlank()) {
                    append("\n\nMotivo: $adminMessage")
                }
            }
        )

        saveOrder(order, status = "CANCELLED", adminMsg = adminMessage)

        notifyAdmin(
            buildString {
                append("*Pedido CRLV Agendado cancelado pela API*\n\n")
                append("Pedido: *#${order.pedidoId}*\n")
                append("Estado: *$stateName*\n")
                append("Placa: *${order.placa}*\n")
                append("Cliente: ${order.userPhone}")
                if (!adminMessage.isNullOrBlank()) {
                    append("\nMotivo: $adminMessage")
                }
            }
        )

        log.info("Pedido {} cancelado, usuario {} notificado", order.pedidoId, order.userPhone)
    }

    private fun expireOrder(order: ScheduledCrlvOrderEntity) {
        val stateName = resolveStateName(order.uf)

        whatsappService.sendMessage(
            order.userPhone,
            buildString {
                append("*CRLV-e Agendado — Expirado*\n\n")
                append("Seu pedido de CRLV-e de *$stateName* expirou.\n")
                append("Placa: *${order.placa}*\n")
                append("Pedido: *#${order.pedidoId}*\n\n")
                append("O prazo de processamento foi excedido. Entre em contato com o suporte.")
            }
        )

        saveOrder(order, status = "EXPIRED")

        notifyAdmin(
            buildString {
                append("*Pedido CRLV Agendado expirado (>${MAX_AGE_HOURS}h)*\n\n")
                append("Pedido: *#${order.pedidoId}*\n")
                append("Estado: *$stateName*\n")
                append("Placa: *${order.placa}*\n")
                append("Cliente: ${order.userPhone}\n")
                if (!order.lastError.isNullOrBlank()) {
                    append("Ultimo erro: ${order.lastError}\n")
                }
                append("\nVerifique se o cliente precisa de reembolso.")
            }
        )

        log.info("Pedido {} expirado (>{}h)", order.pedidoId, MAX_AGE_HOURS)
    }

    private fun saveOrder(
        order: ScheduledCrlvOrderEntity,
        status: String = order.status,
        failCount: Int = order.failCount,
        lastError: String? = order.lastError,
        adminMsg: String? = order.adminMessage
    ) {
        orderRepository.save(
            ScheduledCrlvOrderEntity(
                pedidoId = order.pedidoId,
                userPhone = order.userPhone,
                uf = order.uf,
                placa = order.placa,
                renavam = order.renavam,
                cpf = order.cpf,
                status = status,
                adminMessage = adminMsg,
                failCount = failCount,
                lastError = lastError,
                createdAt = order.createdAt,
                updatedAt = Instant.now()
            )
        )
    }

    private fun notifyAdmin(message: String) {
        val adminPhone = adminService.getSuperAdminPhone() ?: return
        try {
            whatsappService.sendMessage(adminPhone, message)
        } catch (e: Exception) {
            log.error("Falha ao notificar admin: {}", e.message)
        }
    }

    private fun resolveStateName(uf: String): String {
        return QueryTypeRegistry.CRLV_AGENDADO_STATES["crlvag_$uf"] ?: uf.uppercase()
    }
}
