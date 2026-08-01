package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.QueryTypeRegistry
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PricingService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class AdminCommand(
    private val adminService: AdminService,
    private val botStats: BotStats,
    private val consultationStats: ConsultationStats,
    private val pricingService: PricingService,
    private val paymentSessionManager: PaymentSessionManager,
    private val paymentStats: PaymentStats,
    private val queryTypeRegistry: QueryTypeRegistry
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
            "cat" -> showCategory(context, whatsappService)
            // Usuarios
            "ban" -> handleBan(context, whatsappService)
            "unban" -> handleUnban(context, whatsappService)
            "banlist" -> showBanList(context, whatsappService)
            // Modulos
            "modulos" -> showModules(context, whatsappService)
            "modulo" -> showModuleDetail(context, whatsappService)
            "ativar" -> handleEnableModule(context, whatsappService)
            "desativar" -> handleDisableModule(context, whatsappService)
            // Precos
            "precos" -> showPrices(context, whatsappService)
            "preco" -> handleSetPrice(context, whatsappService)
            "preco_padrao" -> handleDefaultPrice(context, whatsappService)
            // Financeiro
            "faturamento" -> showRevenue(context, whatsappService)
            "pendentes" -> showPendingPayments(context, whatsappService)
            "liberar" -> handleApprovePayment(context, whatsappService)
            // Relatorios
            "stats" -> showConsultationStats(context, whatsappService)
            "top" -> showTopModules(context, whatsappService)
            "historico" -> showHistory(context, whatsappService)
            // Controle
            "block" -> handleBlock(context, whatsappService)
            "unblock" -> handleUnblock(context, whatsappService)
            "status" -> showFullStatus(context, whatsappService)
            "reset" -> handleReset(context, whatsappService)
            else -> whatsappService.sendMessage(context.from, "Acao admin invalida.")
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PAINEL PRINCIPAL
    // ══════════════════════════════════════════════════════════════════

    private fun showPanel(context: CommandContext, whatsappService: WhatsappService) {
        adminService.clearPendingAction(context.from)

        val s = botStats.toMap()
        val botStatus = if (adminService.isBotBlocked()) "Bloqueado" else "Ativo"
        val total = consultationStats.getTotal()
        val revenue = paymentStats.getTotalRevenue()
        val pending = paymentSessionManager.getPendingCount()
        val banned = adminService.getBannedCount()
        val totalTypes = queryTypeRegistry.types.size
        val enabledCount = pricingService.getEnabledCount(totalTypes)
        val pricedCount = pricingService.getConfiguredCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*PAINEL ADMINISTRATIVO*\n")
                append("ND Consultas Veiculares\n\n")

                append("*Sistema*\n")
                append("Status: *$botStatus*\n")
                append("Uptime: ${s["uptime"]}\n\n")

                append("*Modulos*\n")
                append("Ativos: *$enabledCount/$totalTypes*\n")
                append("Com preco: $pricedCount/$totalTypes\n\n")

                append("*Consultas*\n")
                append("Total realizadas: *$total*\n\n")

                append("*Financeiro*\n")
                append("Receita: *R\$ ${"%.2f".format(revenue)}*\n")
                append("Pgtos pendentes: *$pending*\n\n")

                append("*Usuarios*\n")
                append("Banidos: *$banned*\n")
                append("Msgs: ${s["messagesSent"]} env / ${s["messagesReceived"]} rec")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Admin",
            body = "Selecione uma categoria para gerenciar:",
            buttonLabel = "Abrir Menu",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Painel Administrativo",
                    rows = listOf(
                        ListRow(
                            "/admin cat modulos",
                            "Modulos de Consulta",
                            "Ativar, desativar e ver detalhes"
                        ),
                        ListRow(
                            "/admin cat precos",
                            "Precos e Valores",
                            "Definir quanto cobrar por consulta"
                        ),
                        ListRow(
                            "/admin cat financeiro",
                            "Financeiro",
                            "Receita, pagamentos e liberacoes"
                        ),
                        ListRow(
                            "/admin cat usuarios",
                            "Gerenciar Usuarios",
                            "Banir, desbanir e lista de bloqueados"
                        ),
                        ListRow(
                            "/admin cat relatorios",
                            "Relatorios",
                            "Estatisticas, ranking e historico"
                        ),
                        ListRow(
                            "/admin cat controle",
                            "Controle do Bot",
                            "Bloquear bot, status e reset"
                        )
                    )
                )
            )
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // SUB-MENUS POR CATEGORIA
    // ══════════════════════════════════════════════════════════════════

    private fun showCategory(context: CommandContext, whatsappService: WhatsappService) {
        when (context.args.getOrNull(1)) {
            "modulos" -> showModulosMenu(context, whatsappService)
            "precos" -> showPrecosMenu(context, whatsappService)
            "financeiro" -> showFinanceiroMenu(context, whatsappService)
            "usuarios" -> showUsuariosMenu(context, whatsappService)
            "relatorios" -> showRelatoriosMenu(context, whatsappService)
            "controle" -> showControleMenu(context, whatsappService)
            else -> showPanel(context, whatsappService)
        }
    }

    private fun showModulosMenu(context: CommandContext, whatsappService: WhatsappService) {
        val totalTypes = queryTypeRegistry.types.size
        val enabledCount = pricingService.getEnabledCount(totalTypes)
        val disabledCount = pricingService.getDisabledCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Modulos de Consulta*\n\n")
                append("Aqui voce gerencia quais tipos de consulta estao disponiveis para os clientes.\n\n")
                append("*Resumo:*\n")
                append("Total de modulos: *$totalTypes*\n")
                append("Ativos (visiveis): *$enabledCount*\n")
                append("Inativos (ocultos): *$disabledCount*\n\n")
                append("_Modulos inativos nao aparecem para os clientes e nao podem ser consultados._")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Modulos",
            body = "Selecione uma acao:",
            buttonLabel = "Acoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Gerenciar Modulos",
                    rows = listOf(
                        ListRow(
                            "/admin modulos",
                            "Ver Todos os Modulos",
                            "Lista completa com status e descricao"
                        ),
                        ListRow(
                            "/admin ativar",
                            "Ativar Modulo",
                            "Tornar um modulo disponivel"
                        ),
                        ListRow(
                            "/admin desativar",
                            "Desativar Modulo",
                            "Ocultar um modulo dos clientes"
                        )
                    )
                )
            )
        )
    }

    private fun showPrecosMenu(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Precos e Valores*\n\n")
                append("Configure quanto cobrar por cada consulta.\n\n")
                append("*Como funciona:*\n")
                append("- Valor *R\$ 0* = consulta gratuita\n")
                append("- Valor maior que zero = cliente paga via PIX ou cartao antes de consultar\n")
                append("- Voce (admin) sempre consulta de graca\n\n")
                append("_Use 'Preco Padrao' para definir o mesmo valor para todos os modulos de uma vez._")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Precos",
            body = "Selecione uma acao:",
            buttonLabel = "Acoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Gerenciar Precos",
                    rows = listOf(
                        ListRow(
                            "/admin precos",
                            "Ver Precos Atuais",
                            "Lista de precos de todos os modulos"
                        ),
                        ListRow(
                            "/admin preco",
                            "Alterar Preco",
                            "Mudar valor de um modulo especifico"
                        ),
                        ListRow(
                            "/admin preco_padrao",
                            "Preco Padrao",
                            "Definir mesmo valor para todos"
                        )
                    )
                )
            )
        )
    }

    private fun showFinanceiroMenu(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Financeiro*\n\n")
                append("Acompanhe a receita do bot e gerencie pagamentos.\n\n")
                append("*Faturamento* — Receita total, custos de API e lucro\n")
                append("*Pendentes* — Clientes aguardando confirmacao de pagamento\n")
                append("*Liberar* — Aprovar um pagamento manualmente (ex: cliente pagou mas webhook nao chegou)")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Financeiro",
            body = "Selecione uma acao:",
            buttonLabel = "Acoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Financeiro",
                    rows = listOf(
                        ListRow(
                            "/admin faturamento",
                            "Faturamento",
                            "Receita total, custos e lucro"
                        ),
                        ListRow(
                            "/admin pendentes",
                            "Pagamentos Pendentes",
                            "Consultas aguardando pagamento"
                        ),
                        ListRow(
                            "/admin liberar",
                            "Liberar Consulta",
                            "Aprovar pagamento manualmente"
                        )
                    )
                )
            )
        )
    }

    private fun showUsuariosMenu(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Gerenciar Usuarios*\n\n")
                append("Controle o acesso dos usuarios ao bot.\n\n")
                append("*Banir* — Bloqueia todas as variantes do numero (com e sem o 9)\n")
                append("*Desbanir* — Restaura o acesso do numero\n")
                append("*Lista* — Veja todos os numeros bloqueados")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Usuarios",
            body = "Selecione uma acao:",
            buttonLabel = "Acoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Usuarios",
                    rows = listOf(
                        ListRow("/admin ban", "Banir Numero", "Bloquear acesso de um numero"),
                        ListRow("/admin unban", "Desbanir Numero", "Restaurar acesso"),
                        ListRow("/admin banlist", "Lista de Banidos", "Ver numeros bloqueados")
                    )
                )
            )
        )
    }

    private fun showRelatoriosMenu(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Relatorios*\n\n")
                append("Veja estatisticas detalhadas do uso do bot.\n\n")
                append("*Estatisticas* — Total de consultas, taxa de sucesso e custos\n")
                append("*Top Modulos* — Ranking dos modulos mais consultados\n")
                append("*Historico* — Ultimas consultas realizadas com detalhes")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Relatorios",
            body = "Selecione uma acao:",
            buttonLabel = "Acoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Relatorios",
                    rows = listOf(
                        ListRow("/admin stats", "Estatisticas", "Consultas, taxas e custos internos"),
                        ListRow("/admin top", "Top Modulos", "Ranking dos mais consultados"),
                        ListRow("/admin historico", "Historico Recente", "Ultimas consultas realizadas")
                    )
                )
            )
        )
    }

    private fun showControleMenu(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Controle do Bot*\n\n")
                append("Gerencie o funcionamento geral do bot.\n\n")
                append("*Bloquear* — Impede TODOS os clientes de fazer consultas (voce continua consultando)\n")
                append("*Liberar* — Reativa as consultas para todos\n")
                append("*Status* — Relatorio completo de todas as metricas\n")
                append("*Reset* — Zera contadores de estatisticas e pagamentos (precos e bans nao sao afetados)")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Controle",
            body = "Selecione uma acao:",
            buttonLabel = "Acoes",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Controle",
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

    // ══════════════════════════════════════════════════════════════════
    // MODULOS
    // ══════════════════════════════════════════════════════════════════

    private fun showModules(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Todos os Modulos de Consulta*\n\n")

                queryTypeRegistry.categories.forEach { (catKey, cat) ->
                    append("*${cat.label}*\n")
                    val types = queryTypeRegistry.getTypesForCategory(catKey)
                    types.forEach { (tipo, info) ->
                        val enabled = pricingService.isModuleEnabled(tipo)
                        val statusIcon = if (enabled) "ON" else "OFF"
                        val price = pricingService.getPrice(tipo)
                        val priceText = if (price > BigDecimal.ZERO) "R\$ ${"%.2f".format(price)}" else "Gratis"
                        append("  [$statusIcon] *${info.label}* — $priceText\n")
                        append("    _${info.description.take(80)}..._\n")
                    }
                    append("\n")
                }

                append("_Para ver detalhes de um modulo, use: /admin modulo <codigo>_\n")
                append("_Ex: /admin modulo placa_full_")
            }
        )

        sendBackButton(context, whatsappService, "modulos")
    }

    private fun showModuleDetail(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args.getOrNull(1)

        if (tipo == null) {
            adminService.setPendingAction(context.from, "modulo")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Detalhes do Modulo*\n\n")
                    append("Informe o *codigo* do modulo para ver os detalhes completos.\n\n")
                    append("*Codigos disponiveis:*\n")
                    queryTypeRegistry.categories.forEach { (catKey, cat) ->
                        append("\n_${cat.label}_\n")
                        queryTypeRegistry.getTypesForCategory(catKey).forEach { (code, info) ->
                            append("  `$code` — ${info.label}\n")
                        }
                    }
                }
            )
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Modulo *$tipo* nao encontrado.")
            sendBackButton(context, whatsappService, "modulos")
            return
        }

        val enabled = pricingService.isModuleEnabled(tipo)
        val price = pricingService.getPrice(tipo)
        val catInfo = queryTypeRegistry.categories[info.category]

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*${info.label}*\n")
                append("Codigo: `$tipo`\n")
                append("Categoria: ${catInfo?.label ?: info.category}\n")
                append("Status: *${if (enabled) "Ativo" else "Inativo"}*\n")
                append("Preco: *${if (price > BigDecimal.ZERO) "R\$ ${"%.2f".format(price)}" else "Gratis"}*\n\n")
                append("*O que faz:*\n")
                append(info.description)
                append("\n\n*Dado solicitado ao cliente:*\n")
                append(info.inputPrompt.replace("*", ""))
            }
        )

        val buttons = mutableListOf<Button>()
        if (enabled) {
            buttons.add(Button(id = "/admin desativar $tipo", title = "Desativar"))
        } else {
            buttons.add(Button(id = "/admin ativar $tipo", title = "Ativar"))
        }
        buttons.add(Button(id = "/admin preco $tipo", title = "Alterar Preco"))
        if (buttons.size < 3) {
            buttons.add(Button(id = "/admin cat modulos", title = "Voltar"))
        }

        whatsappService.sendButtons(
            to = context.from,
            body = "Acoes para ${info.label}:",
            buttons = buttons
        )
    }

    private fun handleEnableModule(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args.getOrNull(1)

        if (tipo == null) {
            val disabled = pricingService.getDisabledModules()
            if (disabled.isEmpty()) {
                whatsappService.sendMessage(context.from, "Todos os modulos ja estao ativos.")
                sendBackButton(context, whatsappService, "modulos")
                return
            }

            adminService.setPendingAction(context.from, "ativar")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Ativar Modulo*\n\n")
                    append("Modulos inativos:\n")
                    disabled.forEach { code ->
                        val label = queryTypeRegistry.getTypeLabel(code)
                        append("  `$code` — $label\n")
                    }
                    append("\nInforme o codigo do modulo para ativar:")
                }
            )
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Modulo *$tipo* nao encontrado.")
            sendBackButton(context, whatsappService, "modulos")
            return
        }

        pricingService.enableModule(tipo)
        whatsappService.sendMessage(
            context.from,
            "Modulo *${info.label}* ativado com sucesso.\nAgora os clientes podem utiliza-lo."
        )
        sendBackButton(context, whatsappService, "modulos")
    }

    private fun handleDisableModule(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args.getOrNull(1)

        if (tipo == null) {
            adminService.setPendingAction(context.from, "desativar")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Desativar Modulo*\n\n")
                    append("Modulos ativos:\n")
                    queryTypeRegistry.types.forEach { (code, info) ->
                        if (pricingService.isModuleEnabled(code)) {
                            append("  `$code` — ${info.label}\n")
                        }
                    }
                    append("\nInforme o codigo do modulo para desativar:")
                }
            )
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Modulo *$tipo* nao encontrado.")
            sendBackButton(context, whatsappService, "modulos")
            return
        }

        pricingService.disableModule(tipo)
        whatsappService.sendMessage(
            context.from,
            "Modulo *${info.label}* desativado.\nOs clientes nao verao mais este modulo ate voce reativa-lo."
        )
        sendBackButton(context, whatsappService, "modulos")
    }

    // ══════════════════════════════════════════════════════════════════
    // PRECOS
    // ══════════════════════════════════════════════════════════════════

    private fun showPrices(context: CommandContext, whatsappService: WhatsappService) {
        val allPrices = pricingService.getAllPrices()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Precos das Consultas*\n\n")

                queryTypeRegistry.categories.forEach { (catKey, cat) ->
                    append("*${cat.label}*\n")
                    val types = queryTypeRegistry.getTypesForCategory(catKey)
                    types.forEach { (tipo, info) ->
                        val enabled = pricingService.isModuleEnabled(tipo)
                        val statusTag = if (!enabled) " [OFF]" else ""
                        val price = allPrices[tipo]
                        val priceText = if (price != null && price > BigDecimal.ZERO) {
                            "R\$ ${"%.2f".format(price)}"
                        } else if (price != null) {
                            "Gratis"
                        } else {
                            "_Nao definido_"
                        }
                        append("  ${info.label}$statusTag: $priceText\n")
                    }
                    append("\n")
                }

                append("_Voce (admin) sempre consulta de graca, independente do preco._")
            }
        )

        sendBackButton(context, whatsappService, "precos")
    }

    private fun handleSetPrice(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args.getOrNull(1)
        val valor = context.args.getOrNull(2)

        if (tipo == null) {
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Alterar Preco*\n\n")
                    append("Informe o *codigo* do modulo e o *valor*.\n")
                    append("Formato: `codigo valor`\n")
                    append("Ex: `placa_full 25.00`\n")
                    append("Use `0` para tornar gratis.\n\n")
                    append("*Codigos disponiveis:*\n")
                    queryTypeRegistry.categories.forEach { (catKey, cat) ->
                        append("\n_${cat.label}_\n")
                        queryTypeRegistry.getTypesForCategory(catKey).forEach { (code, info) ->
                            val current = pricingService.getPrice(code)
                            val priceText = if (current > BigDecimal.ZERO) "R\$ ${"%.2f".format(current)}" else "Gratis"
                            append("  `$code` ($priceText)\n")
                        }
                    }
                }
            )

            adminService.setPendingAction(context.from, "preco")
            return
        }

        if (valor == null) {
            val info = queryTypeRegistry.getTypeInfo(tipo)
            if (info == null) {
                whatsappService.sendMessage(context.from, "Modulo *$tipo* nao encontrado.\nVerifique o codigo e tente novamente.")
                sendBackButton(context, whatsappService, "precos")
                return
            }

            val current = pricingService.getPrice(tipo)
            adminService.setPendingAction(context.from, "preco $tipo")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Alterar Preco*\n\n")
                    append("Modulo: *${info.label}*\n")
                    append("Preco atual: R\$ ${"%.2f".format(current)}\n\n")
                    append("Informe o novo valor em R\$:\n")
                    append("Ex: `25.00` ou `0` para gratis")
                }
            )
            return
        }

        val parsedValue = parsePrice(valor)
        if (parsedValue == null) {
            whatsappService.sendMessage(context.from, "Valor invalido. Use o formato: `25.00`")
            sendBackButton(context, whatsappService, "precos")
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Modulo *$tipo* nao encontrado.")
            sendBackButton(context, whatsappService, "precos")
            return
        }

        pricingService.setPrice(tipo, parsedValue)
        val priceText = if (parsedValue > BigDecimal.ZERO) "R\$ ${"%.2f".format(parsedValue)}" else "Gratis"
        whatsappService.sendMessage(
            context.from,
            "Preco atualizado!\n\n*${info.label}*: $priceText"
        )
        sendBackButton(context, whatsappService, "precos")
    }

    private fun handleDefaultPrice(context: CommandContext, whatsappService: WhatsappService) {
        val valor = context.args.getOrNull(1)

        if (valor == null) {
            adminService.setPendingAction(context.from, "preco_padrao")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Preco Padrao*\n\n")
                    append("Defina um valor unico para *todos* os ${queryTypeRegistry.types.size} modulos de consulta.\n\n")
                    append("Informe o valor em R\$:\n")
                    append("Ex: `25.00`\n\n")
                    append("_Use `0` para tornar todas as consultas gratuitas._")
                }
            )
            return
        }

        val parsedValue = parsePrice(valor)
        if (parsedValue == null) {
            whatsappService.sendMessage(context.from, "Valor invalido. Use o formato: `25.00`")
            sendBackButton(context, whatsappService, "precos")
            return
        }

        queryTypeRegistry.types.keys.forEach { tipo ->
            pricingService.setPrice(tipo, parsedValue)
        }

        val priceText = if (parsedValue > BigDecimal.ZERO) "R\$ ${"%.2f".format(parsedValue)}" else "Gratis"
        whatsappService.sendMessage(
            context.from,
            "Preco padrao definido!\n\nTodos os *${queryTypeRegistry.types.size}* modulos agora custam: *$priceText*"
        )
        sendBackButton(context, whatsappService, "precos")
    }

    // ══════════════════════════════════════════════════════════════════
    // USUARIOS (BAN)
    // ══════════════════════════════════════════════════════════════════

    private fun handleBan(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            adminService.setPendingAction(context.from, "ban")
            whatsappService.sendMessage(
                context.from,
                "*Banir Numero*\n\nInforme o numero a ser banido.\nFormato: numero completo com DDI\nEx: `5511999998888`\n\n_Numeros BR sao bloqueados com e sem o 9 automaticamente._"
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        if (normalized.length < 10) {
            whatsappService.sendMessage(context.from, "Numero invalido. Informe com DDI + DDD + numero.\nEx: `5511999998888`")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        if (adminService.isBanned(normalized)) {
            whatsappService.sendMessage(context.from, "O numero *$normalized* ja esta banido.")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        val result = adminService.banNumber(normalized)

        if (result.reason == "admin") {
            whatsappService.sendMessage(context.from, "Nao e possivel banir o numero admin.")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        val variantsList = result.variants.joinToString("\n") { "  - $it" }
        whatsappService.sendMessage(
            context.from,
            "*Numero banido com sucesso*\n\nVariantes bloqueadas:\n$variantsList"
        )
        sendBackButton(context, whatsappService, "usuarios")
    }

    private fun handleUnban(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            val banned = adminService.getBannedNumbers()
            if (banned.isEmpty()) {
                whatsappService.sendMessage(context.from, "Nenhum numero banido no momento.")
                sendBackButton(context, whatsappService, "usuarios")
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
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        val removed = adminService.unbanNumber(normalized)
        val variantsList = removed.joinToString("\n") { "  - $it" }
        whatsappService.sendMessage(
            context.from,
            "*Numero desbanido com sucesso*\n\nVariantes desbloqueadas:\n$variantsList"
        )
        sendBackButton(context, whatsappService, "usuarios")
    }

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

        sendBackButton(context, whatsappService, "usuarios")
    }

    // ══════════════════════════════════════════════════════════════════
    // FINANCEIRO
    // ══════════════════════════════════════════════════════════════════

    private fun showRevenue(context: CommandContext, whatsappService: WhatsappService) {
        val totalPay = paymentStats.getTotalPayments()
        val revenue = paymentStats.getTotalRevenue()
        val apiCost = consultationStats.getTotalCost()
        val profit = revenue.subtract(apiCost)
        val recent = paymentStats.getRecentPayments(10)

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Faturamento*\n\n")

                append("*Resumo Financeiro*\n")
                append("Pagamentos recebidos: *$totalPay*\n")
                append("Receita total: *R\$ ${"%.2f".format(revenue)}*\n")
                append("Custo API (interno): R\$ ${"%.2f".format(apiCost)}\n")
                append("Lucro estimado: *R\$ ${"%.2f".format(profit)}*\n")

                if (recent.isNotEmpty()) {
                    append("\n*Ultimos Pagamentos*\n")
                    recent.forEach { p ->
                        append("${p.formatTimestamp()} | ${p.tipoLabel}\n")
                        append("  ${p.userPhone} | R\$ ${"%.2f".format(p.amount)}\n")
                    }
                }
            }
        )

        sendBackButton(context, whatsappService, "financeiro")
    }

    private fun showPendingPayments(context: CommandContext, whatsappService: WhatsappService) {
        val pending = paymentSessionManager.getPendingSessions()

        if (pending.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Pagamentos Pendentes*\n\nNenhum pagamento pendente no momento.")
            sendBackButton(context, whatsappService, "financeiro")
            return
        }

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Pagamentos Pendentes* (${pending.size})\n\n")
                pending.forEach { (phone, session) ->
                    val method = when (session.paymentMethod) {
                        "pix" -> "PIX"
                        "card" -> "Cartao"
                        else -> "Aguardando escolha"
                    }
                    append("*$phone*\n")
                    append("  ${session.tipoLabel} | ${session.query}\n")
                    append("  R\$ ${"%.2f".format(session.price)} | $method\n\n")
                }
                append("_Use 'Liberar Consulta' para aprovar manualmente._")
            }
        )

        sendBackButton(context, whatsappService, "financeiro")
    }

    private fun handleApprovePayment(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            val pending = paymentSessionManager.getPendingSessions()
            if (pending.isEmpty()) {
                whatsappService.sendMessage(context.from, "Nenhum pagamento pendente para liberar.")
                sendBackButton(context, whatsappService, "financeiro")
                return
            }

            adminService.setPendingAction(context.from, "liberar")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Liberar Consulta*\n\n")
                    append("Use esta opcao quando o cliente ja pagou mas o sistema nao confirmou automaticamente.\n\n")
                    append("Pagamentos pendentes:\n")
                    pending.forEach { (phone, session) ->
                        append("  *$phone* — ${session.tipoLabel} (R\$ ${"%.2f".format(session.price)})\n")
                    }
                    append("\nInforme o numero para liberar:")
                }
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        val session = paymentSessionManager.getSession(normalized)

        if (session == null) {
            whatsappService.sendMessage(context.from, "Nenhum pagamento pendente para *$normalized*.")
            sendBackButton(context, whatsappService, "financeiro")
            return
        }

        paymentSessionManager.markPaid(normalized, "manual_admin")
        paymentStats.record(normalized, session.tipo, session.tipoLabel, session.price, "manual_admin")

        whatsappService.sendMessage(
            normalized,
            "Seu pagamento de *R\$ ${"%.2f".format(session.price)}* foi confirmado!"
        )
        whatsappService.sendButtons(
            to = normalized,
            body = "Sua consulta *${session.tipoLabel}* esta liberada.",
            buttons = listOf(
                Button(id = "/consultar pago", title = "Consultar Agora")
            )
        )

        whatsappService.sendMessage(
            context.from,
            "Pagamento liberado!\n\nConsulta *${session.tipoLabel}* para *$normalized* foi aprovada."
        )
        sendBackButton(context, whatsappService, "financeiro")
    }

    // ══════════════════════════════════════════════════════════════════
    // CONTROLE DO BOT
    // ══════════════════════════════════════════════════════════════════

    private fun handleBlock(context: CommandContext, whatsappService: WhatsappService) {
        if (adminService.isBotBlocked()) {
            whatsappService.sendMessage(context.from, "O bot ja esta bloqueado para consultas.")
        } else {
            adminService.blockBot()
            whatsappService.sendMessage(context.from, "Bot *bloqueado*.\nNenhum cliente podera fazer consultas ate voce liberar.\nVoce continua podendo consultar normalmente.")
        }
        sendBackButton(context, whatsappService, "controle")
    }

    private fun handleUnblock(context: CommandContext, whatsappService: WhatsappService) {
        if (!adminService.isBotBlocked()) {
            whatsappService.sendMessage(context.from, "O bot ja esta liberado para consultas.")
        } else {
            adminService.unblockBot()
            whatsappService.sendMessage(context.from, "Bot *liberado*.\nTodos os clientes podem fazer consultas novamente.")
        }
        sendBackButton(context, whatsappService, "controle")
    }

    // ══════════════════════════════════════════════════════════════════
    // RELATORIOS
    // ══════════════════════════════════════════════════════════════════

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
                append("Com sucesso: *$success*\n")
                append("Com falha: *$fail*\n")
                append("Taxa de sucesso: *$rate*\n\n")

                append("*Custos Internos (API)*\n")
                append("Gasto total: *R\$ ${"%.2f".format(cost)}*\n\n")

                append("*Usuarios*\n")
                append("Unicos: *$users*\n")
                append("Banidos: *${adminService.getBannedCount()}*")
            }
        )

        sendBackButton(context, whatsappService, "relatorios")
    }

    private fun showTopModules(context: CommandContext, whatsappService: WhatsappService) {
        val top = consultationStats.getTopTypes(10)

        if (top.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Top Modulos*\n\nNenhuma consulta registrada ainda.")
            sendBackButton(context, whatsappService, "relatorios")
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

        sendBackButton(context, whatsappService, "relatorios")
    }

    private fun showHistory(context: CommandContext, whatsappService: WhatsappService) {
        val logs = consultationStats.getRecentLog(10)

        if (logs.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Historico Recente*\n\nNenhuma consulta registrada ainda.")
            sendBackButton(context, whatsappService, "relatorios")
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

        sendBackButton(context, whatsappService, "relatorios")
    }

    // ══════════════════════════════════════════════════════════════════
    // STATUS COMPLETO
    // ══════════════════════════════════════════════════════════════════

    private fun showFullStatus(context: CommandContext, whatsappService: WhatsappService) {
        val s = botStats.toMap()
        val botStatus = if (adminService.isBotBlocked()) "Bloqueado" else "Ativo"
        val total = consultationStats.getTotal()
        val success = consultationStats.getSuccess()
        val fail = consultationStats.getFail()
        val rate = consultationStats.getSuccessRate()
        val apiCost = consultationStats.getTotalCost()
        val users = consultationStats.getUniqueUsersCount()
        val banned = adminService.getBannedCount()
        val revenue = paymentStats.getTotalRevenue()
        val totalPay = paymentStats.getTotalPayments()
        val pending = paymentSessionManager.getPendingCount()
        val totalTypes = queryTypeRegistry.types.size
        val enabledCount = pricingService.getEnabledCount(totalTypes)
        val pricedCount = pricingService.getConfiguredCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*STATUS COMPLETO DO BOT*\n\n")

                append("*Sistema*\n")
                append("Estado: *$botStatus*\n")
                append("Uptime: ${s["uptime"]}\n")
                append("Iniciado em: ${s["startedAt"]}\n\n")

                append("*Modulos*\n")
                append("Total: $totalTypes\n")
                append("Ativos: $enabledCount\n")
                append("Com preco: $pricedCount\n\n")

                append("*Consultas*\n")
                append("Total: *$total*\n")
                append("Sucesso: $success ($rate)\n")
                append("Falhas: $fail\n\n")

                append("*Financeiro*\n")
                append("Pagamentos: $totalPay\n")
                append("Pendentes: $pending\n")
                append("Receita: *R\$ ${"%.2f".format(revenue)}*\n")
                append("Custo API: R\$ ${"%.2f".format(apiCost)}\n")
                append("Lucro: *R\$ ${"%.2f".format(revenue.subtract(apiCost))}*\n\n")

                append("*Usuarios*\n")
                append("Unicos: $users\n")
                append("Banidos: $banned\n\n")

                append("*Mensagens*\n")
                append("Enviadas: ${s["messagesSent"]}\n")
                append("Recebidas: ${s["messagesReceived"]}\n")
                append("Comandos: ${s["commandsExecuted"]}\n")
                append("Erros: ${s["errors"]}")
            }
        )

        sendBackButton(context, whatsappService, "controle")
    }

    // ══════════════════════════════════════════════════════════════════
    // RESET
    // ══════════════════════════════════════════════════════════════════

    private fun handleReset(context: CommandContext, whatsappService: WhatsappService) {
        consultationStats.reset()
        paymentStats.reset()
        whatsappService.sendMessage(
            context.from,
            "*Contadores resetados*\n\nEstatisticas de consultas e pagamentos foram zeradas.\nPrecos, modulos e bans *nao* foram afetados."
        )
        sendBackButton(context, whatsappService, "controle")
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════

    private fun sendBackButton(context: CommandContext, whatsappService: WhatsappService, category: String? = null) {
        val buttons = mutableListOf<Button>()
        if (category != null) {
            buttons.add(Button(id = "/admin cat $category", title = "Voltar"))
        }
        buttons.add(Button(id = "/admin", title = "Painel Admin"))
        if (buttons.size < 3) {
            buttons.add(Button(id = "/start", title = "Menu Inicial"))
        }
        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = buttons
        )
    }

    private fun parsePrice(value: String): BigDecimal? {
        return try {
            val cleaned = value.replace(",", ".").replace("R$", "").replace("r$", "").trim()
            val parsed = cleaned.toBigDecimal()
            if (parsed < BigDecimal.ZERO) null else parsed
        } catch (_: Exception) {
            null
        }
    }
}
