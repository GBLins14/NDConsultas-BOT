package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(BancoBrasilProperties::class)
class BancoBrasilConfig(
    private val properties: BancoBrasilProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(BancoBrasilConfig::class.java)
    }

    @Bean
    fun bancoBrasilRestClient(): RestClient {
        log.info("BB Débitos Veiculares REST client — api-url: {}", properties.apiUrl)
        return RestClient.builder()
            .baseUrl(properties.apiUrl)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build()
    }
}
