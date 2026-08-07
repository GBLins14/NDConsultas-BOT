package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class DebitoVeicularSessionManager {

    data class ServicoDebitoInfo(
        val index: Int,
        val codigoServico: Int,
        val nomeServico: String,
        val numeroIdentificadorItem: Int,
        val valorItem: Double,
        val codigoTextoItem: String,
        val numeroUnicoItemBanco: Long,
        val codigoEstado: String?
    )

    data class DebitoSession(
        val codigoSolicitacao: String,
        val renavam: String,
        val uf: String,
        val placa: String?,
        val nomeProprietario: String?,
        val timestampLimitePagamento: String?,
        val servicos: List<ServicoDebitoInfo>,
        val createdAt: Instant = Instant.now()
    )

    private val sessions = ConcurrentHashMap<String, DebitoSession>()

    @Scheduled(fixedRate = 300_000) // 5 minutos
    fun cleanupExpiredSessions() {
        sessions.entries.removeIf {
            Duration.between(it.value.createdAt, Instant.now()).toMinutes() > 30
        }
    }

    fun save(userId: String, session: DebitoSession) {
        sessions[userId] = session
    }

    fun get(userId: String): DebitoSession? {
        val session = sessions[userId] ?: return null
        if (Duration.between(session.createdAt, Instant.now()).toMinutes() > 30) {
            sessions.remove(userId)
            return null
        }
        return session
    }

    fun remove(userId: String) {
        sessions.remove(userId)
    }
}
