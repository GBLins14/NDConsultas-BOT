package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "syncpay")
data class SyncPayProperties(
    val apiUrl: String = "https://api.syncpay.com.br",
    val apiKey: String = "",
    val webhookUrl: String = ""
)
