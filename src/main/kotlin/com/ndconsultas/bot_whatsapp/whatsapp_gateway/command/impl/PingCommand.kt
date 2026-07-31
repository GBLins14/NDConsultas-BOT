package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class PingCommand : BotCommand {

    override val name = "/ping"
    override val description = "Verifica se o bot esta respondendo"

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        val start = System.currentTimeMillis()
        whatsappService.sendMessage(context.from, "🏓 *Pong!*")
        val elapsed = System.currentTimeMillis() - start
        whatsappService.sendMessage(context.from, "⚡ Tempo de resposta: ${elapsed}ms")
    }
}
