package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "asaas")
data class AsaasProperties(
    val apiUrl: String = "https://api.asaas.com",
    val apiKey: String = "",
    val webhookToken: String = ""
)
