package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(SyncPayProperties::class)
class SyncPayConfig(
    private val properties: SyncPayProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(SyncPayConfig::class.java)
    }

    @Bean
    fun syncPayRestClient(): RestClient {
        log.info("SyncPay REST client configured — api-url: {}", properties.apiUrl)
        return RestClient.builder()
            .baseUrl(properties.apiUrl)
            .defaultHeader("Authorization", "Bearer ${properties.apiKey}")
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build()
    }
}
