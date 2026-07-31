package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class ConsultationSessionManager {

    private val sessions = ConcurrentHashMap<String, PendingConsultation>()

    data class PendingConsultation(
        val tipo: String,
        val tipoLabel: String,
        val createdAt: Instant = Instant.now()
    )

    fun setPending(userId: String, tipo: String, tipoLabel: String) {
        sessions[userId] = PendingConsultation(tipo, tipoLabel)
    }

    fun getPending(userId: String): PendingConsultation? {
        val pending = sessions[userId] ?: return null
        if (Duration.between(pending.createdAt, Instant.now()).toMinutes() > 5) {
            sessions.remove(userId)
            return null
        }
        return pending
    }

    fun removePending(userId: String) {
        sessions.remove(userId)
    }
}
