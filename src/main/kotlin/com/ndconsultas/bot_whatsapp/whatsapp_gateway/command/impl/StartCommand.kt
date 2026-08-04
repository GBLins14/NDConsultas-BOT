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
            buildString {
                appendLine("*Olá, ${context.senderName}!*")
                appendLine()
                appendLine("Bem-vindo ao *ND Consultas*!")
                appendLine("Sou seu assistente virtual para consultas veiculares e pessoais.")
                appendLine()
                appendLine("Escolha uma opção abaixo para começar.")
            }
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/consultar", title = "Consultas"),
                Button(id = "/help", title = "Ajuda")
            ),
            footer = "ND Consultas | v1.0"
        )
    }
}
