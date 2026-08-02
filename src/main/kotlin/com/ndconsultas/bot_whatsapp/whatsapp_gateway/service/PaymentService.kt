package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.client.AsaasClient
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.AsaasCustomerEntity
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.AsaasCustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Service
class PaymentService(
    private val asaasClient: AsaasClient,
    private val paymentSessionManager: PaymentSessionManager,
    private val paymentStats: PaymentStats,
    private val customerRepository: AsaasCustomerRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(PaymentService::class.java)
    }

    private val customerCache = ConcurrentHashMap<String, String>()

    data class CheckoutResult(
        val success: Boolean,
        val invoiceUrl: String? = null,
        val paymentId: String? = null,
        val error: String? = null
    )

    // ── Customer ──────────────────────────────────────────────────

    fun getCustomerId(phone: String): String? = customerCache[phone]

    fun createCustomer(phone: String, cpfCnpj: String, name: String = "Cliente"): String? {
        return try {
            val response = asaasClient.createCustomer(
                name = name,
                cpfCnpj = cpfCnpj,
                phone = phone,
                externalReference = phone
            )

            val customerId = response.id
            if (customerId.isNullOrBlank()) {
                log.error("Asaas não retornou ID do cliente")
                return null
            }

            customerCache[phone] = customerId
            try {
                customerRepository.save(AsaasCustomerEntity(phone, customerId))
            } catch (e: Exception) {
                log.warn("Falha ao persistir cliente Asaas (cache ok): {}", e.message)
            }

            log.info("Cliente Asaas criado: phone={}, customerId={}", phone, customerId)
            customerId
        } catch (e: Exception) {
            log.error("Erro ao criar cliente Asaas para {}: {}", phone, e.message, e)
            null
        }
    }

    /** Chamado pelo ConfigPersistenceService no startup */
    fun loadCustomer(phone: String, customerId: String) {
        customerCache[phone] = customerId
    }

    // ── Payment ───────────────────────────────────────────────────

    fun createPayment(userPhone: String, amount: BigDecimal, description: String): CheckoutResult {
        return try {
            val customerId = customerCache[userPhone]
            if (customerId == null) {
                log.error("Cliente Asaas não encontrado para {}", userPhone)
                return CheckoutResult(false, error = "Erro interno. Tente novamente.")
            }

            val response = asaasClient.createPayment(
                customerId = customerId,
                amount = amount,
                description = description,
                externalReference = userPhone
            )

            if (response.id.isNullOrBlank() || response.invoiceUrl.isNullOrBlank()) {
                log.error("Asaas não retornou ID ou invoiceUrl da cobrança")
                return CheckoutResult(false, error = "Erro ao criar cobrança. Tente novamente.")
            }

            paymentSessionManager.setPaymentCreated(userPhone, response.id, response.invoiceUrl)

            log.info("Cobrança Asaas criada: phone={}, paymentId={}", userPhone, response.id)
            CheckoutResult(
                success = true,
                invoiceUrl = response.invoiceUrl,
                paymentId = response.id
            )
        } catch (e: Exception) {
            log.error("Erro ao criar cobrança Asaas para {}: {}", userPhone, e.message, e)
            CheckoutResult(false, error = "Erro ao criar cobrança. Tente novamente mais tarde.")
        }
    }

    // ── Webhook de confirmação ────────────────────────────────────

    fun confirmPayment(asaasPaymentId: String): PaymentSessionManager.PaymentSession? {
        val session = paymentSessionManager.findByPaymentId(asaasPaymentId) ?: run {
            log.warn("Webhook Asaas recebido para paymentId desconhecido: {}", asaasPaymentId)
            return null
        }

        paymentSessionManager.markPaid(session.userPhone, asaasPaymentId)
        paymentStats.record(session.userPhone, session.tipo, session.tipoLabel, session.price, asaasPaymentId)

        log.info("Pagamento Asaas confirmado: {} - {} R\${}", session.userPhone, session.tipo, "%.2f".format(session.price))
        return paymentSessionManager.getSession(session.userPhone)
    }
}
