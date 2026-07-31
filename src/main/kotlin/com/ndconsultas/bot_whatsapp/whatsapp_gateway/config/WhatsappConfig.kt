package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(WhatsappProperties::class)
class WhatsappConfig(
    private val properties: WhatsappProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(WhatsappConfig::class.java)
    }

    @Bean
    fun whatsappRestClient(): RestClient {
        log.info("WhatsApp REST client configured — phone-number-id: {}, api-version: {}",
            properties.phoneNumberId, properties.apiVersion)
        return RestClient.builder()
            .baseUrl("https://graph.facebook.com/${properties.apiVersion}")
            .defaultHeader("Authorization", "Bearer ${properties.apiKey}")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
