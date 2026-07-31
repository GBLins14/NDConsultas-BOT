package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandRegistry
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class HelpCommand(
    @Lazy private val registry: CommandRegistry
) : BotCommand {

    override val name = "/help"
    override val description = "Exibe todos os comandos disponiveis"
    override val aliases = listOf("/ajuda")

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        val commands = registry.getAllCommands()
        val list = commands.joinToString("\n") { cmd ->
            val aliasText = if (cmd.aliases.isNotEmpty()) " _(${cmd.aliases.joinToString(", ")})_" else ""
            "• *${cmd.name}*$aliasText\n   ${cmd.description}"
        }

        whatsappService.sendMessage(
            context.from,
            """
            📋 *Comandos Disponiveis*

            $list

            _Todos os comandos comecam com /_
            """.trimIndent()
        )
    }
}
