package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class StatusCommand(
    private val stats: BotStats
) : BotCommand {

    override val name = "/status"
    override val description = "Exibe o status atual do bot"

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        val s = stats.toMap()
        whatsappService.sendMessage(
            context.from,
            """
            📊 *Status do Bot*

            🟢 *Online*
            ⏱ Uptime: ${s["uptime"]}
            📤 Mensagens enviadas: ${s["messagesSent"]}
            📥 Mensagens recebidas: ${s["messagesReceived"]}
            ⚡ Comandos executados: ${s["commandsExecuted"]}
            ⚠️ Erros: ${s["errors"]}
            🕐 Iniciado em: ${s["startedAt"]}
            """.trimIndent()
        )
    }
}
