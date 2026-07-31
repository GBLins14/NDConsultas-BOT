package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class AdminCommand(
    private val adminService: AdminService,
    private val stats: BotStats
) : BotCommand {

    override val name = "/admin"
    override val description = "Painel administrativo"
    override val showInHelp = false

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        // Verificacao de seguranca: o campo 'from' vem do webhook da Meta,
        // nao do input do usuario. Nao pode ser falsificado.
        if (!adminService.isAdmin(context.from)) {
            whatsappService.sendMessage(context.from, "Acesso negado.")
            return
        }

        val action = context.args.getOrNull(0)
        when (action) {
            null -> showPanel(context, whatsappService)
            "ban" -> handleBan(context, whatsappService)
            "unban" -> handleUnban(context, whatsappService)
            "banlist" -> showBanList(context, whatsappService)
            "block" -> handleBlock(context, whatsappService)
            "unblock" -> handleUnblock(context, whatsappService)
            "status" -> showStatus(context, whatsappService)
            else -> whatsappService.sendMessage(context.from, "Acao admin invalida.")
        }
    }

    // ── Painel principal ───────────────────────────────────────────

    private fun showPanel(context: CommandContext, whatsappService: WhatsappService) {
        adminService.clearPendingAction(context.from)

        val botStatus = if (adminService.isBotBlocked()) "Bloqueado" else "Ativo"
        val bannedCount = adminService.getBannedCount()
        val s = stats.toMap()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Painel Administrativo*\n\n")
                append("Bot: *$botStatus*\n")
                append("Banidos: *$bannedCount*\n")
                append("Uptime: ${s["uptime"]}\n")
                append("Msgs enviadas: ${s["messagesSent"]}\n")
                append("Msgs recebidas: ${s["messagesReceived"]}\n")
                append("Comandos: ${s["commandsExecuted"]}\n")
                append("Erros: ${s["errors"]}")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Admin",
            body = "Selecione uma acao:",
            buttonLabel = "Configuracoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Gerenciar Usuarios",
                    rows = listOf(
                        ListRow("/admin ban", "Banir Numero", "Bloquear acesso de um numero"),
                        ListRow("/admin unban", "Desbanir Numero", "Restaurar acesso de um numero"),
                        ListRow("/admin banlist", "Lista de Banidos", "Ver todos os numeros banidos")
                    )
                ),
                ListSection(
                    title = "Controle do Bot",
                    rows = listOf(
                        ListRow("/admin block", "Bloquear Consultas", "Impedir novas consultas"),
                        ListRow("/admin unblock", "Liberar Consultas", "Reativar consultas"),
                        ListRow("/admin status", "Status Detalhado", "Metricas completas do bot")
                    )
                )
            )
        )
    }

    // ── Ban ─────────────────────────────────────────────────────────

    private fun handleBan(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            adminService.setPendingAction(context.from, "ban")
            whatsappService.sendMessage(
                context.from,
                "*Banir Numero*\n\nInforme o numero a ser banido.\nFormato: numero completo com DDI\nEx: 5511999998888"
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        if (normalized.length < 10) {
            whatsappService.sendMessage(context.from, "Numero invalido. Informe com DDI + DDD + numero.\nEx: 5511999998888")
            sendBackButton(context, whatsappService)
            return
        }

        if (adminService.isAdmin(normalized)) {
            whatsappService.sendMessage(context.from, "Nao e possivel banir o numero admin.")
            sendBackButton(context, whatsappService)
            return
        }

        if (adminService.isBanned(normalized)) {
            whatsappService.sendMessage(context.from, "O numero *$normalized* ja esta banido.")
            sendBackButton(context, whatsappService)
            return
        }

        adminService.banNumber(normalized)
        whatsappService.sendMessage(context.from, "Numero *$normalized* foi banido com sucesso.")
        sendBackButton(context, whatsappService)
    }

    // ── Unban ───────────────────────────────────────────────────────

    private fun handleUnban(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            val banned = adminService.getBannedNumbers()
            if (banned.isEmpty()) {
                whatsappService.sendMessage(context.from, "Nenhum numero banido no momento.")
                sendBackButton(context, whatsappService)
                return
            }

            adminService.setPendingAction(context.from, "unban")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Desbanir Numero*\n\n")
                    append("Numeros banidos:\n")
                    banned.forEachIndexed { i, n ->
                        append("${i + 1}. $n\n")
                    }
                    append("\nInforme o numero que deseja desbanir:")
                }
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        if (!adminService.isBanned(normalized)) {
            whatsappService.sendMessage(context.from, "O numero *$normalized* nao esta na lista de banidos.")
            sendBackButton(context, whatsappService)
            return
        }

        adminService.unbanNumber(normalized)
        whatsappService.sendMessage(context.from, "Numero *$normalized* foi desbanido com sucesso.")
        sendBackButton(context, whatsappService)
    }

    // ── Ban list ────────────────────────────────────────────────────

    private fun showBanList(context: CommandContext, whatsappService: WhatsappService) {
        val banned = adminService.getBannedNumbers()

        if (banned.isEmpty()) {
            whatsappService.sendMessage(context.from, "Nenhum numero banido no momento.")
        } else {
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Numeros Banidos* (${banned.size})\n\n")
                    banned.forEachIndexed { i, n ->
                        append("${i + 1}. $n\n")
                    }
                }
            )
        }

        sendBackButton(context, whatsappService)
    }

    // ── Block / Unblock ────────────────────────────────────────────

    private fun handleBlock(context: CommandContext, whatsappService: WhatsappService) {
        if (adminService.isBotBlocked()) {
            whatsappService.sendMessage(context.from, "O bot ja esta bloqueado para consultas.")
        } else {
            adminService.blockBot()
            whatsappService.sendMessage(context.from, "Bot *bloqueado*. Nenhuma consulta sera processada ate voce liberar.")
        }
        sendBackButton(context, whatsappService)
    }

    private fun handleUnblock(context: CommandContext, whatsappService: WhatsappService) {
        if (!adminService.isBotBlocked()) {
            whatsappService.sendMessage(context.from, "O bot ja esta liberado para consultas.")
        } else {
            adminService.unblockBot()
            whatsappService.sendMessage(context.from, "Bot *liberado*. Consultas estao ativas novamente.")
        }
        sendBackButton(context, whatsappService)
    }

    // ── Status ──────────────────────────────────────────────────────

    private fun showStatus(context: CommandContext, whatsappService: WhatsappService) {
        val s = stats.toMap()
        val botStatus = if (adminService.isBotBlocked()) "Bloqueado" else "Ativo"
        val bannedCount = adminService.getBannedCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Status Detalhado do Bot*\n\n")
                append("*Estado:* $botStatus\n")
                append("*Numeros banidos:* $bannedCount\n\n")
                append("*Uptime:* ${s["uptime"]}\n")
                append("*Iniciado em:* ${s["startedAt"]}\n\n")
                append("*Msgs enviadas:* ${s["messagesSent"]}\n")
                append("*Msgs recebidas:* ${s["messagesReceived"]}\n")
                append("*Comandos executados:* ${s["commandsExecuted"]}\n")
                append("*Erros:* ${s["errors"]}")
            }
        )

        sendBackButton(context, whatsappService)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun sendBackButton(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendButtons(
            to = context.from,
            body = "Voltar ao painel?",
            buttons = listOf(
                Button(id = "/admin", title = "Painel Admin"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
