package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class StartCommand : BotCommand {

    override val name = "/start"
    override val description = "Inicia o bot e exibe boas-vindas"
    override val aliases = listOf("/iniciar")

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            """
            *Ola, ${context.senderName}!* 👋

            Bem-vindo ao *NDConsultas BOT*!
            Sou seu assistente virtual e estou aqui para ajuda-lo.

            Use /help para ver todos os comandos disponiveis.
            """.trimIndent()
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/menu", title = "Menu"),
                Button(id = "/help", title = "Ajuda"),
                Button(id = "/info", title = "Informacoes")
            )
        )
    }
}
