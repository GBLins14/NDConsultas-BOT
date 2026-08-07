package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bancobrasil")
data class BancoBrasilProperties(
    val apiUrl: String = "https://api-ip.bb.com.br/debitos-veiculares/v1",
    val appKey: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val oauthUrl: String = "https://oauth.bb.com.br/oauth/token"
)
