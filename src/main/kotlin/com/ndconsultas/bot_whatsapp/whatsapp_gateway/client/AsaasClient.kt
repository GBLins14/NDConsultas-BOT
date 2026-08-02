package com.ndconsultas.bot_whatsapp.whatsapp_gateway.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate

@Component
class AsaasClient(
    @Qualifier("asaasRestClient") private val restClient: RestClient
) {
    companion object {
        private val log = LoggerFactory.getLogger(AsaasClient::class.java)
    }

    // ── DTOs ──────────────────────────────────────────────────────

    data class CustomerRequest(
        val name: String,
        val cpfCnpj: String,
        val mobilePhone: String? = null,
        val notificationDisabled: Boolean = true,
        val externalReference: String? = null
    )

    data class CustomerResponse(
        val id: String? = null,
        val name: String? = null,
        val cpfCnpj: String? = null
    )

    data class PaymentRequest(
        val customer: String,
        val billingType: String = "UNDEFINED",
        val value: Double,
        val dueDate: String,
        val description: String? = null,
        val externalReference: String? = null
    )

    data class PaymentResponse(
        val id: String? = null,
        val status: String? = null,
        val invoiceUrl: String? = null,
        val value: Double? = null,
        val billingType: String? = null
    )

    // ── Customer ──────────────────────────────────────────────────

    fun createCustomer(
        name: String,
        cpfCnpj: String,
        phone: String? = null,
        externalReference: String? = null
    ): CustomerResponse {
        log.info("Criando cliente Asaas: cpf={}***", cpfCnpj.take(3))

        val request = CustomerRequest(
            name = name,
            cpfCnpj = cpfCnpj.replace(Regex("[^0-9]"), ""),
            mobilePhone = phone?.replace(Regex("[^0-9]"), "")?.takeLast(11),
            notificationDisabled = true,
            externalReference = externalReference
        )

        val response = restClient.post()
            .uri("/v3/customers")
            .body(request)
            .retrieve()
            .body(CustomerResponse::class.java)

        log.info("Cliente Asaas criado: id={}", response?.id)
        return response ?: throw RuntimeException("Resposta vazia ao criar cliente Asaas")
    }

    // ── Payment ───────────────────────────────────────────────────

    fun createPayment(
        customerId: String,
        amount: BigDecimal,
        description: String? = null,
        externalReference: String? = null
    ): PaymentResponse {
        log.info("Criando cobrança Asaas: R\$ {}", "%.2f".format(amount))

        val request = PaymentRequest(
            customer = customerId,
            billingType = "UNDEFINED",
            value = amount.toDouble(),
            dueDate = LocalDate.now().plusDays(1).toString(),
            description = description,
            externalReference = externalReference
        )

        val response = restClient.post()
            .uri("/v3/payments")
            .body(request)
            .retrieve()
            .body(PaymentResponse::class.java)

        log.info("Cobrança Asaas criada: id={}, invoiceUrl={}", response?.id, response?.invoiceUrl)
        return response ?: throw RuntimeException("Resposta vazia ao criar cobrança Asaas")
    }
}
