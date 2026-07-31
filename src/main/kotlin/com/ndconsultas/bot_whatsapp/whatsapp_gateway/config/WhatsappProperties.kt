package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "whatsapp")
data class WhatsappProperties(
    val phoneNumberId: String,
    val apiKey: String,
    val verifyToken: String,
    val apiVersion: String = "v22.0"
)
