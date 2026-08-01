package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DataSourceConfig(
    @Value("\${DATABASE_URL}") private val rawUrl: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(DataSourceConfig::class.java)
    }

    @Bean
    fun dataSource(): DataSource {
        // Aceita tanto postgresql:// quanto postgres:// (formato Neon/Render)
        // e converte para jdbc:postgresql:// esperado pelo driver JDBC
        val jdbcUrl = when {
            rawUrl.startsWith("jdbc:") -> rawUrl
            rawUrl.startsWith("postgresql://") -> "jdbc:${rawUrl}"
            rawUrl.startsWith("postgres://") -> "jdbc:postgresql://${rawUrl.removePrefix("postgres://")}"
            else -> error("DATABASE_URL inválida: deve começar com postgresql://, postgres:// ou jdbc:postgresql://")
        }

        log.info("DataSource configurado para: {}", jdbcUrl.replace(Regex(":[^:@]+@"), ":***@"))

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 3
            connectionTimeout = 30_000
            // Neon encerra conexões ociosas — reconectar automaticamente
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            keepaliveTime = 60_000
        }

        return HikariDataSource(config)
    }
}
