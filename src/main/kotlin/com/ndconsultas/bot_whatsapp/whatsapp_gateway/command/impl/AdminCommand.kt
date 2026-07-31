package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class AdminCommand(
    private val adminService: AdminService,
    private val botStats: BotStats,
    private val consultationStats: ConsultationStats
) : BotCommand {

    override val name = "/admin"
    override val description = "Painel administrativo"
    override val showInHelp = false

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        if (!adminService.isAdmin(context.from)) {
            whatsappService.sendMessage(context.from, "Acesso negado.")
            return
        }

        when (context.args.getOrNull(0)) {
            null -> showPanel(context, whatsappService)
            "ban" -> handleBan(context, whatsappService)
            "unban" -> handleUnban(context, whatsappService)
            "banlist" -> showBanList(context, whatsappService)
            "block" -> handleBlock(context, whatsappService)
            "unblock" -> handleUnblock(context, whatsappService)
            "stats" -> showConsultationStats(context, whatsappService)
            "top" -> showTopModules(context, whatsappService)
            "historico" -> showHistory(context, whatsappService)
            "status" -> showFullStatus(context, whatsappService)
            "reset" -> handleReset(context, whatsappService)
            else -> whatsappService.sendMessage(context.from, "Acao admin invalida.")
        }
    }

    // ── Painel principal ───────────────────────────────────────────

    private fun showPanel(context: CommandContext, whatsappService: WhatsappService) {
        adminService.clearPendingAction(context.from)

        val s = botStats.toMap()
        val botStatus = if (adminService.isBotBlocked()) "Bloqueado" else "Ativo"
        val total = consultationStats.getTotal()
        val success = consultationStats.getSuccess()
        val fail = consultationStats.getFail()
        val rate = consultationStats.getSuccessRate()
        val cost = consultationStats.getTotalCost()
        val users = consultationStats.getUniqueUsersCount()
        val banned = adminService.getBannedCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*PAINEL ADMINISTRATIVO*\n")
                append("ND Consultas Veiculares\n\n")

                append("*Sistema*\n")
                append("Status: *$botStatus*\n")
                append("Uptime: ${s["uptime"]}\n\n")

                append("*Consultas*\n")
                append("Total: *$total*\n")
                if (total > 0) {
                    append("Sucesso: $success ($rate)\n")
                    append("Falhas: $fail\n")
                }
                append("Gasto API: R\$ ${"%.2f".format(cost)}\n\n")

                append("*Usuarios*\n")
                append("Ativos: *$users*\n")
                append("Banidos: *$banned*\n\n")

                append("*Mensagens*\n")
                append("Enviadas: ${s["messagesSent"]}\n")
                append("Recebidas: ${s["messagesReceived"]}\n")
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
                        ListRow("/admin banlist", "Lista de Banidos", "Ver numeros banidos")
                    )
                ),
                ListSection(
                    title = "Relatorios",
                    rows = listOf(
                        ListRow("/admin stats", "Estatisticas", "Consultas, taxas e custos"),
                        ListRow("/admin top", "Top Modulos", "Modulos mais consultados"),
                        ListRow("/admin historico", "Historico Recente", "Ultimas consultas realizadas")
                    )
                ),
                ListSection(
                    title = "Controle do Bot",
                    rows = listOf(
                        ListRow("/admin block", "Bloquear Consultas", "Impedir novas consultas"),
                        ListRow("/admin unblock", "Liberar Consultas", "Reativar consultas"),
                        ListRow("/admin status", "Status Completo", "Todas as metricas do bot"),
                        ListRow("/admin reset", "Resetar Contadores", "Zerar estatisticas")
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
            whatsappService.sendMessage(context.from, "*Lista de Banidos*\n\nNenhum numero banido no momento.")
        } else {
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Lista de Banidos* (${banned.size})\n\n")
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

    // ── Estatisticas de Consultas ──────────────────────────────────

    private fun showConsultationStats(context: CommandContext, whatsappService: WhatsappService) {
        val total = consultationStats.getTotal()
        val success = consultationStats.getSuccess()
        val fail = consultationStats.getFail()
        val rate = consultationStats.getSuccessRate()
        val cost = consultationStats.getTotalCost()
        val users = consultationStats.getUniqueUsersCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Estatisticas de Consultas*\n\n")

                append("*Resumo Geral*\n")
                append("Total de consultas: *$total*\n")
                append("Consultas com sucesso: *$success*\n")
                append("Consultas com falha: *$fail*\n")
                append("Taxa de sucesso: *$rate*\n\n")

                append("*Financeiro (interno)*\n")
                append("Gasto total API: *R\$ ${"%.2f".format(cost)}*\n\n")

                append("*Usuarios*\n")
                append("Usuarios unicos: *$users*\n")
                append("Usuarios banidos: *${adminService.getBannedCount()}*")
            }
        )

        sendBackButton(context, whatsappService)
    }

    // ── Top Modulos ────────────────────────────────────────────────

    private fun showTopModules(context: CommandContext, whatsappService: WhatsappService) {
        val top = consultationStats.getTopTypes(10)

        if (top.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Top Modulos*\n\nNenhuma consulta registrada ainda.")
            sendBackButton(context, whatsappService)
            return
        }

        val total = consultationStats.getTotal()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Top Modulos Consultados*\n\n")
                top.forEachIndexed { i, (label, count) ->
                    val pct = if (total > 0) "%.1f%%".format((count.toDouble() / total) * 100) else "0%"
                    append("${i + 1}. *$label*\n")
                    append("   $count consultas ($pct)\n")
                }
            }
        )

        sendBackButton(context, whatsappService)
    }

    // ── Historico Recente ──────────────────────────────────────────

    private fun showHistory(context: CommandContext, whatsappService: WhatsappService) {
        val logs = consultationStats.getRecentLog(10)

        if (logs.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Historico Recente*\n\nNenhuma consulta registrada ainda.")
            sendBackButton(context, whatsappService)
            return
        }

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Historico Recente*\n")
                append("Ultimas ${logs.size} consultas\n\n")
                logs.forEach { entry ->
                    val status = if (entry.success) "ok" else "FALHA"
                    append("*${entry.formatTimestamp()}* [$status]\n")
                    append("${entry.tipoLabel} | ${entry.query}\n")
                    append("De: ${entry.userPhone}\n\n")
                }
            }
        )

        sendBackButton(context, whatsappService)
    }

    // ── Status Completo ────────────────────────────────────────────

    private fun showFullStatus(context: CommandContext, whatsappService: WhatsappService) {
        val s = botStats.toMap()
        val botStatus = if (adminService.isBotBlocked()) "Bloqueado" else "Ativo"
        val total = consultationStats.getTotal()
        val success = consultationStats.getSuccess()
        val fail = consultationStats.getFail()
        val rate = consultationStats.getSuccessRate()
        val cost = consultationStats.getTotalCost()
        val users = consultationStats.getUniqueUsersCount()
        val banned = adminService.getBannedCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*STATUS COMPLETO DO BOT*\n\n")

                append("*Sistema*\n")
                append("Estado: *$botStatus*\n")
                append("Uptime: ${s["uptime"]}\n")
                append("Iniciado em: ${s["startedAt"]}\n\n")

                append("*Consultas Veiculares*\n")
                append("Total: *$total*\n")
                append("Sucesso: $success ($rate)\n")
                append("Falhas: $fail\n")
                append("Gasto API: R\$ ${"%.2f".format(cost)}\n\n")

                append("*Usuarios*\n")
                append("Unicos: $users\n")
                append("Banidos: $banned\n\n")

                append("*Mensagens*\n")
                append("Enviadas: ${s["messagesSent"]}\n")
                append("Recebidas: ${s["messagesReceived"]}\n\n")

                append("*Processamento*\n")
                append("Comandos executados: ${s["commandsExecuted"]}\n")
                append("Erros totais: ${s["errors"]}")
            }
        )

        sendBackButton(context, whatsappService)
    }

    // ── Reset ──────────────────────────────────────────────────────

    private fun handleReset(context: CommandContext, whatsappService: WhatsappService) {
        consultationStats.reset()
        whatsappService.sendMessage(
            context.from,
            "*Contadores resetados*\n\nTodas as estatisticas de consultas foram zeradas.\nContadores de mensagens do bot nao foram afetados."
        )
        sendBackButton(context, whatsappService)
    }

    // ── Helpers ─────────────────────────────────────────────────────

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
