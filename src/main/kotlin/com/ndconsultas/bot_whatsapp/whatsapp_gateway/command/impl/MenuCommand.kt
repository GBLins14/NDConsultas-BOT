package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class MenuCommand : BotCommand {

    override val name = "/menu"
    override val description = "Exibe o menu principal interativo"

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendList(
            to = context.from,
            body = "Selecione uma opcao do menu abaixo:",
            buttonLabel = "Ver Opcoes",
            header = "Menu Principal",
            footer = "NDConsultas BOT",
            sections = listOf(
                ListSection(
                    title = "Atendimento",
                    rows = listOf(
                        ListRow(id = "/info", title = "Informacoes", description = "Saiba mais sobre a NDConsultas"),
                        ListRow(id = "/contato", title = "Contato", description = "Fale com nossa equipe")
                    )
                ),
                ListSection(
                    title = "Bot",
                    rows = listOf(
                        ListRow(id = "/help", title = "Ajuda", description = "Lista de comandos disponiveis"),
                        ListRow(id = "/status", title = "Status", description = "Status do bot"),
                        ListRow(id = "/ping", title = "Ping", description = "Verificar latencia")
                    )
                )
            )
        )
    }
}
