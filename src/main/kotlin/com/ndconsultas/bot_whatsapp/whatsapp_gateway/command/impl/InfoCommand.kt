package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class InfoCommand : BotCommand {

    override val name = "/info"
    override val description = "Informacoes sobre a NDConsultas"
    override val aliases = listOf("/sobre")

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            """
            ℹ️ *NDConsultas*

            Sistema de atendimento automatizado via WhatsApp.

            🤖 *Bot Version:* 1.0.0
            🔧 *Desenvolvido com:* Spring Boot + Kotlin
            📡 *API:* WhatsApp Business Cloud API

            Para suporte, entre em contato com nossa equipe.
            """.trimIndent()
        )
    }
}
