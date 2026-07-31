package com.ndconsultas.bot_whatsapp.whatsapp_gateway.controller

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandRegistry
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/dashboard")
class DashboardController(
    private val stats: BotStats,
    private val registry: CommandRegistry
) {
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(stats.toMap())

    @GetMapping("/commands")
    fun getCommands(): ResponseEntity<List<CommandInfo>> {
        val commands = registry.getAllCommands().map { cmd ->
            CommandInfo(
                name = cmd.name,
                description = cmd.description,
                aliases = cmd.aliases
            )
        }
        return ResponseEntity.ok(commands)
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "UP", "service" to "NDConsultas-BOT"))
}

data class CommandInfo(
    val name: String,
    val description: String,
    val aliases: List<String>
)
