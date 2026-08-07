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

    /**
     * Garante que o número BR tenha o 9 após o DDD para links wa.me.
     * 5581XXXXXXXX (12 dígitos) → 55819XXXXXXXX (13 dígitos)
     */
    private fun ensureBrMobile(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        if (digits.startsWith("55") && digits.length == 12) {
            val ddd = digits.substring(2, 4)
            val rest = digits.substring(4)
            return "55${ddd}9${rest}"
        }
        return digits
    }

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

        val formattedPhone = ensureBrMobile(supportPhone)

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Suporte ND Consultas*\n\n")
                append("Clique no link abaixo para falar diretamente com nosso suporte:\n\n")
                append("https://wa.me/$formattedPhone")
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
