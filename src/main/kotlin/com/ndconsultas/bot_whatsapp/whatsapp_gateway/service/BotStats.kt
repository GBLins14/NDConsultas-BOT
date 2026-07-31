package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

@Service
class BotStats {

    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val commandsExecuted = AtomicLong(0)
    private val errors = AtomicLong(0)
    private val startTime: Instant = Instant.now()

    fun incrementSent() { messagesSent.incrementAndGet() }
    fun incrementReceived() { messagesReceived.incrementAndGet() }
    fun incrementCommands() { commandsExecuted.incrementAndGet() }
    fun incrementErrors() { errors.incrementAndGet() }

    fun toMap(): Map<String, Any> {
        val uptime = Duration.between(startTime, Instant.now())
        return mapOf(
            "messagesSent" to messagesSent.get(),
            "messagesReceived" to messagesReceived.get(),
            "commandsExecuted" to commandsExecuted.get(),
            "errors" to errors.get(),
            "uptime" to formatDuration(uptime),
            "uptimeSeconds" to uptime.seconds,
            "startedAt" to startTime.toString()
        )
    }

    private fun formatDuration(d: Duration): String {
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        val seconds = d.seconds % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }
}
