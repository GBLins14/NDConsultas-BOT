package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class SuporteCommand(
    private val adminService: AdminService
) : BotCommand {

    override val name = "/suporte"
    override val description = "Falar com o suporte"
    override val aliases = listOf("/support", "/ajuda_suporte")

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        val supportPhone = adminService.getSupportPhone()

        if (supportPhone.isNullOrBlank()) {
            whatsappService.sendMessage(
                context.from,
                "O suporte nao esta disponivel no momento.\nTente novamente mais tarde."
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar", title = "Consultas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Suporte ND Consultas*\n\n")
                append("Clique no link abaixo para falar diretamente com nosso suporte:\n\n")
                append("https://wa.me/$supportPhone")
            }
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "O que mais deseja fazer?",
            buttons = listOf(
                Button(id = "/consultar", title = "Consultas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
