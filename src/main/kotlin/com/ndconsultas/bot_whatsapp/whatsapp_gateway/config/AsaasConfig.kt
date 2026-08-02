package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(AsaasProperties::class)
class AsaasConfig(
    private val properties: AsaasProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(AsaasConfig::class.java)
    }

    @Bean
    fun asaasRestClient(): RestClient {
        log.info("Asaas REST client configured — api-url: {}", properties.apiUrl)
        return RestClient.builder()
            .baseUrl(properties.apiUrl)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("access_token", properties.apiKey)
            .defaultHeader("User-Agent", "NDConsultas-BOT/1.0.0")
            .build()
    }
}
