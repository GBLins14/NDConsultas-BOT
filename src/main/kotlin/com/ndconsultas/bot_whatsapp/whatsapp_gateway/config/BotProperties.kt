package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "bot")
data class BotProperties(
    val logoUrl: String = ""
)

@Configuration
@EnableConfigurationProperties(BotProperties::class)
class BotConfig
