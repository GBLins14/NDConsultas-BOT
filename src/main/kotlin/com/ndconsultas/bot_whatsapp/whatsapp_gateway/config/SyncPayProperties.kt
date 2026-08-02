package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "syncpay")
data class SyncPayProperties(
    val apiUrl: String = "https://api.syncpayments.com.br/",
    val clientId: String = "",
    val clientSecret: String = "",
    val webhookUrl: String = "",
    val split: List<SplitEntry> = emptyList()
) {
    data class SplitEntry(
        val percentage: Int = 0,
        val userId: String = ""
    )
}
