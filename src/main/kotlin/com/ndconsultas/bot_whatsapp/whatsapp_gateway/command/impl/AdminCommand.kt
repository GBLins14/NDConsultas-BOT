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
            "suporte", "suporte_phone" -> handleSetSupportPhone(context, whatsappService)
            "rm_suporte" -> handleRemoveSupportPhone(context, whatsappService)
            // Administradores (somente super admin)
            "admins" -> showAdmins(context, whatsappService)
            "addadmin" -> handleAddAdmin(context, whatsappService)
            "rmadmin" -> handleRemoveAdmin(context, whatsappService)
            else -> whatsappService.sendMessage(context.from, "Ação admin inválida.")
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
        val isSuperAdmin = adminService.isSuperAdmin(context.from)
        val adminCount = adminService.getAdminCount()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*PAINEL ADMINISTRATIVO*\n")
                append("ND Consultas Veiculares\n\n")

                append("*Sistema*\n")
                append("Status: *$botStatus*\n")
                append("Uptime: ${s["uptime"]}\n\n")

                append("*Módulos*\n")
                append("Ativos: *$enabledCount/$totalTypes*\n")
                append("Com preço: $pricedCount/$totalTypes\n\n")

                append("*Consultas*\n")
                append("Total realizadas: *$total*\n\n")

                append("*Financeiro*\n")
                append("Receita: *R\$ ${"%.2f".format(revenue)}*\n")
                append("Pgtos pendentes: *$pending*\n\n")

                append("*Usuários*\n")
                append("Banidos: *$banned*\n")
                if (isSuperAdmin) append("Admins: *${adminCount + 1}* (você + $adminCount)\n")
                append("Msgs: ${s["messagesSent"]} env / ${s["messagesReceived"]} rec")
            }
        )

        val menuRows = mutableListOf(
            ListRow(
                "/admin cat modulos",
                "Módulos de Consulta",
                "Ativar, desativar e ver detalhes"
            ),
            ListRow(
                "/admin cat precos",
                "Preços e Valores",
                "Definir quanto cobrar por consulta"
            ),
            ListRow(
                "/admin cat financeiro",
                "Financeiro",
                "Receita, pagamentos e liberações"
            ),
            ListRow(
                "/admin cat usuarios",
                "Gerenciar Usuários",
                "Banir, desbanir e lista de bloqueados"
            ),
            ListRow(
                "/admin cat relatorios",
                "Relatórios",
                "Estatísticas, ranking e histórico"
            ),
            ListRow(
                "/admin cat controle",
                "Controle do Bot",
                "Bloquear bot, status e reset"
            )
        )

        if (isSuperAdmin) {
            menuRows.add(
                ListRow(
                    "/admin cat admins",
                    "Administradores",
                    "Adicionar e remover admins"
                )
            )
        }

        whatsappService.sendList(
            to = context.from,
            header = "Admin",
            body = "Selecione uma categoria para gerenciar:",
            buttonLabel = "Abrir Menu",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Painel Administrativo",
                    rows = menuRows
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
            "admins" -> showAdminsMenu(context, whatsappService)
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
                append("*Módulos de Consulta*\n\n")
                append("Aqui você gerencia quais tipos de consulta estão disponíveis para os clientes.\n\n")
                append("*Resumo:*\n")
                append("Total de módulos: *$totalTypes*\n")
                append("Ativos (visíveis): *$enabledCount*\n")
                append("Inativos (ocultos): *$disabledCount*\n\n")
                append("_Módulos inativos não aparecem para os clientes e não podem ser consultados._")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Módulos",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Gerenciar Módulos",
                    rows = listOf(
                        ListRow(
                            "/admin modulos",
                            "Ver Todos os Módulos",
                            "Lista completa com status e descrição"
                        ),
                        ListRow(
                            "/admin ativar",
                            "Ativar Módulo",
                            "Tornar um módulo disponível"
                        ),
                        ListRow(
                            "/admin desativar",
                            "Desativar Módulo",
                            "Ocultar um módulo dos clientes"
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
                append("*Preços e Valores*\n\n")
                append("Configure quanto cobrar por cada consulta.\n\n")
                append("*Como funciona:*\n")
                append("- Valor *R\$ 0* = consulta gratuita\n")
                append("- Valor maior que zero = cliente paga via PIX ou cartão antes de consultar\n")
                append("- Você (admin) sempre consulta de graça\n\n")
                append("_Use 'Preço Padrão' para definir o mesmo valor para todos os módulos de uma vez._")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Preços",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Gerenciar Preços",
                    rows = listOf(
                        ListRow(
                            "/admin precos",
                            "Ver Preços Atuais",
                            "Lista de preços de todos os módulos"
                        ),
                        ListRow(
                            "/admin preco",
                            "Alterar Preço",
                            "Mudar valor de um módulo específico"
                        ),
                        ListRow(
                            "/admin preco_padrao",
                            "Preço Padrão",
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
                append("*Pendentes* — Clientes aguardando confirmação de pagamento\n")
                append("*Liberar* — Aprovar um pagamento manualmente (ex: cliente pagou mas webhook não chegou)")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Financeiro",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
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
                append("*Gerenciar Usuários*\n\n")
                append("Controle o acesso dos usuários ao bot.\n\n")
                append("*Banir* — Bloqueia todas as variantes do número (com e sem o 9)\n")
                append("*Desbanir* — Restaura o acesso do número\n")
                append("*Lista* — Veja todos os números bloqueados")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Usuários",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Usuários",
                    rows = listOf(
                        ListRow("/admin ban", "Banir Número", "Bloquear acesso de um número"),
                        ListRow("/admin unban", "Desbanir Número", "Restaurar acesso"),
                        ListRow("/admin banlist", "Lista de Banidos", "Ver números bloqueados")
                    )
                )
            )
        )
    }

    private fun showRelatoriosMenu(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Relatórios*\n\n")
                append("Veja estatísticas detalhadas do uso do bot.\n\n")
                append("*Estatísticas* — Total de consultas, taxa de sucesso e custos\n")
                append("*Top Módulos* — Ranking dos módulos mais consultados\n")
                append("*Histórico* — Últimas consultas realizadas com detalhes")
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Relatórios",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Relatórios",
                    rows = listOf(
                        ListRow("/admin stats", "Estatísticas", "Consultas, taxas e custos internos"),
                        ListRow("/admin top", "Top Módulos", "Ranking dos mais consultados"),
                        ListRow("/admin historico", "Histórico Recente", "Últimas consultas realizadas")
                    )
                )
            )
        )
    }

    private fun showControleMenu(context: CommandContext, whatsappService: WhatsappService) {
        val currentSupport = adminService.getSupportPhone()
        val supportText = if (currentSupport != null) "Suporte atual: $currentSupport" else "Suporte: nao configurado"

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Controle do Bot*\n\n")
                append("Gerencie o funcionamento geral do bot.\n\n")
                append("*Bloquear* — Impede TODOS os clientes de fazer consultas (você continua consultando)\n")
                append("*Liberar* — Reativa as consultas para todos\n")
                append("*Suporte* — Define o numero de suporte para os clientes\n")
                append("*Status* — Relatório completo de todas as métricas\n")
                append("*Reset* — Zera contadores de estatísticas e pagamentos (preços e bans não são afetados)\n\n")
                append("_${supportText}_")
            }
        )

        val rows = mutableListOf(
            ListRow("/admin block", "Bloquear Consultas", "Impedir novas consultas"),
            ListRow("/admin unblock", "Liberar Consultas", "Reativar consultas"),
            ListRow("/admin suporte", "Definir Suporte", "Numero de atendimento"),
            ListRow("/admin status", "Status Completo", "Todas as métricas do bot"),
            ListRow("/admin reset", "Resetar Contadores", "Zerar estatísticas")
        )

        if (currentSupport != null) {
            rows.add(3, ListRow("/admin rm_suporte", "Remover Suporte", "Desativar atendimento"))
        }

        whatsappService.sendList(
            to = context.from,
            header = "Controle",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
            footer = "ND Consultas | Admin",
            sections = listOf(ListSection(title = "Controle", rows = rows))
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // MODULOS
    // ══════════════════════════════════════════════════════════════════

    private fun showModules(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Todos os Módulos de Consulta*\n\n")

                queryTypeRegistry.categories.forEach { (catKey, cat) ->
                    append("*${cat.label}*\n")
                    val types = queryTypeRegistry.getTypesForCategory(catKey)
                    types.forEach { (tipo, info) ->
                        val enabled = pricingService.isModuleEnabled(tipo)
                        val statusIcon = if (enabled) "ON" else "OFF"
                        val price = pricingService.getPrice(tipo)
                        val priceText = if (price > BigDecimal.ZERO) "R\$ ${"%.2f".format(price)}" else "Grátis"
                        append("  [$statusIcon] *${info.label}* — $priceText\n")
                        append("    _${info.description.take(80)}..._\n")
                    }
                    append("\n")
                }

                append("_Para ver detalhes de um módulo, use: /admin modulo <codigo>_\n")
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
                    append("*Detalhes do Módulo*\n\n")
                    append("Informe o *código* do módulo para ver os detalhes completos.\n\n")
                    append("*Códigos disponíveis:*\n")
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
            whatsappService.sendMessage(context.from, "Módulo *$tipo* não encontrado.")
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
                append("Código: `$tipo`\n")
                append("Categoria: ${catInfo?.label ?: info.category}\n")
                append("Status: *${if (enabled) "Ativo" else "Inativo"}*\n")
                append("Preço: *${if (price > BigDecimal.ZERO) "R\$ ${"%.2f".format(price)}" else "Grátis"}*\n\n")
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
        buttons.add(Button(id = "/admin preco $tipo", title = "Alterar Preço"))
        if (buttons.size < 3) {
            buttons.add(Button(id = "/admin cat modulos", title = "Voltar"))
        }

        whatsappService.sendButtons(
            to = context.from,
            body = "Ações para ${info.label}:",
            buttons = buttons
        )
    }

    private fun handleEnableModule(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args.getOrNull(1)

        if (tipo == null) {
            val disabled = pricingService.getDisabledModules()
            if (disabled.isEmpty()) {
                whatsappService.sendMessage(context.from, "Todos os módulos já estão ativos.")
                sendBackButton(context, whatsappService, "modulos")
                return
            }

            adminService.setPendingAction(context.from, "ativar")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Ativar Módulo*\n\n")
                    append("Módulos inativos:\n")
                    disabled.forEach { code ->
                        val label = queryTypeRegistry.getTypeLabel(code)
                        append("  `$code` — $label\n")
                    }
                    append("\nInforme o código do módulo para ativar:")
                }
            )
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Módulo *$tipo* não encontrado.")
            sendBackButton(context, whatsappService, "modulos")
            return
        }

        pricingService.enableModule(tipo)
        whatsappService.sendMessage(
            context.from,
            "Módulo *${info.label}* ativado com sucesso.\nAgora os clientes podem utilizá-lo."
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
                    append("*Desativar Módulo*\n\n")
                    append("Módulos ativos:\n")
                    queryTypeRegistry.types.forEach { (code, info) ->
                        if (pricingService.isModuleEnabled(code)) {
                            append("  `$code` — ${info.label}\n")
                        }
                    }
                    append("\nInforme o código do módulo para desativar:")
                }
            )
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Módulo *$tipo* não encontrado.")
            sendBackButton(context, whatsappService, "modulos")
            return
        }

        pricingService.disableModule(tipo)
        whatsappService.sendMessage(
            context.from,
            "Módulo *${info.label}* desativado.\nOs clientes não verão mais este módulo até você reativá-lo."
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
                append("*Preços das Consultas*\n\n")

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
                            "Grátis"
                        } else {
                            "_Não definido_"
                        }
                        append("  ${info.label}$statusTag: $priceText\n")
                    }
                    append("\n")
                }

                append("_Você (admin) sempre consulta de graça, independente do preço._")
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
                    append("*Alterar Preço*\n\n")
                    append("Informe o *código* do módulo e o *valor*.\n")
                    append("Formato: `codigo valor`\n")
                    append("Ex: `placa_full 25.00`\n")
                    append("Use `0` para tornar grátis.\n\n")
                    append("*Códigos disponíveis:*\n")
                    queryTypeRegistry.categories.forEach { (catKey, cat) ->
                        append("\n_${cat.label}_\n")
                        queryTypeRegistry.getTypesForCategory(catKey).forEach { (code, info) ->
                            val current = pricingService.getPrice(code)
                            val priceText = if (current > BigDecimal.ZERO) "R\$ ${"%.2f".format(current)}" else "Grátis"
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
                whatsappService.sendMessage(context.from, "Módulo *$tipo* não encontrado.\nVerifique o código e tente novamente.")
                sendBackButton(context, whatsappService, "precos")
                return
            }

            val current = pricingService.getPrice(tipo)
            adminService.setPendingAction(context.from, "preco $tipo")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Alterar Preço*\n\n")
                    append("Módulo: *${info.label}*\n")
                    append("Preço atual: R\$ ${"%.2f".format(current)}\n\n")
                    append("Informe o novo valor em R\$:\n")
                    append("Ex: `25.00` ou `0` para grátis")
                }
            )
            return
        }

        val parsedValue = parsePrice(valor)
        if (parsedValue == null) {
            whatsappService.sendMessage(context.from, "Valor inválido. Use o formato: `25.00`")
            sendBackButton(context, whatsappService, "precos")
            return
        }

        val info = queryTypeRegistry.getTypeInfo(tipo)
        if (info == null) {
            whatsappService.sendMessage(context.from, "Módulo *$tipo* não encontrado.")
            sendBackButton(context, whatsappService, "precos")
            return
        }

        pricingService.setPrice(tipo, parsedValue)
        val priceText = if (parsedValue > BigDecimal.ZERO) "R\$ ${"%.2f".format(parsedValue)}" else "Grátis"
        whatsappService.sendMessage(
            context.from,
            "Preço atualizado!\n\n*${info.label}*: $priceText"
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
                    append("*Preço Padrão*\n\n")
                    append("Defina um valor único para *todos* os ${queryTypeRegistry.types.size} módulos de consulta.\n\n")
                    append("Informe o valor em R\$:\n")
                    append("Ex: `25.00`\n\n")
                    append("_Use `0` para tornar todas as consultas gratuitas._")
                }
            )
            return
        }

        val parsedValue = parsePrice(valor)
        if (parsedValue == null) {
            whatsappService.sendMessage(context.from, "Valor inválido. Use o formato: `25.00`")
            sendBackButton(context, whatsappService, "precos")
            return
        }

        pricingService.setDefaultPrice(parsedValue, queryTypeRegistry.types.keys)

        val priceText = if (parsedValue > BigDecimal.ZERO) "R\$ ${"%.2f".format(parsedValue)}" else "Grátis"
        whatsappService.sendMessage(
            context.from,
            "Preço padrão definido!\n\nTodos os *${queryTypeRegistry.types.size}* módulos agora custam: *$priceText*"
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
                "*Banir Número*\n\nInforme o número a ser banido.\nFormato: número completo com DDI\nEx: `5511999998888`\n\n_Números BR são bloqueados com e sem o 9 automaticamente._"
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        if (normalized.length < 10) {
            whatsappService.sendMessage(context.from, "Número inválido. Informe com DDI + DDD + número.\nEx: `5511999998888`")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        if (adminService.isBanned(normalized)) {
            whatsappService.sendMessage(context.from, "O número *$normalized* já está banido.")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        val result = adminService.banNumber(normalized)

        if (result.reason == "admin") {
            whatsappService.sendMessage(context.from, "Não é possível banir o número admin.")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        whatsappService.sendMessage(
            context.from,
            "*Número banido com sucesso*\n\nNúmero bloqueado: *$normalized*"
        )
        sendBackButton(context, whatsappService, "usuarios")
    }

    private fun handleUnban(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            val banned = adminService.getBannedNumbers()
            if (banned.isEmpty()) {
                whatsappService.sendMessage(context.from, "Nenhum número banido no momento.")
                sendBackButton(context, whatsappService, "usuarios")
                return
            }

            val unique = adminService.getUniqueBannedNumbers()
            adminService.setPendingAction(context.from, "unban")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Desbanir Número*\n\n")
                    append("Números banidos:\n")
                    unique.forEachIndexed { i, n ->
                        append("${i + 1}. $n\n")
                    }
                    append("\nInforme o número que deseja desbanir:")
                }
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        if (!adminService.isBanned(normalized)) {
            whatsappService.sendMessage(context.from, "O número *$normalized* não está na lista de banidos.")
            sendBackButton(context, whatsappService, "usuarios")
            return
        }

        adminService.unbanNumber(normalized)
        whatsappService.sendMessage(
            context.from,
            "*Número desbanido com sucesso*\n\nNúmero desbloqueado: *$normalized*"
        )
        sendBackButton(context, whatsappService, "usuarios")
    }

    private fun showBanList(context: CommandContext, whatsappService: WhatsappService) {
        val unique = adminService.getUniqueBannedNumbers()

        if (unique.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Lista de Banidos*\n\nNenhum número banido no momento.")
        } else {
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Lista de Banidos* (${unique.size})\n\n")
                    unique.forEachIndexed { i, n ->
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
                    append("\n*Últimos Pagamentos*\n")
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
                    val method = when (session.status) {
                        PaymentSessionManager.PaymentStatus.AWAITING_PAYMENT -> "PIX gerado"
                        PaymentSessionManager.PaymentStatus.COLLECTING_CARD -> "Cartão (coletando dados)"
                        else -> "Aguardando escolha"
                    }
                    append("*$phone*\n")
                    append("  ${session.tipoLabel} | ${session.query}\n")
                    append("  R\$ ${"%.2f".format(session.price)} | $method\n\n")
                }
                append("_Use 'Liberar Consulta' para aprovar manualmente sem precisar de webhook._")
            }
        )

        sendBackButton(context, whatsappService, "financeiro")
    }

    private fun handleApprovePayment(context: CommandContext, whatsappService: WhatsappService) {
        val number = context.args.getOrNull(1)

        if (number == null) {
            val pending = paymentSessionManager.getPendingSessions()
            if (pending.isEmpty()) {
                whatsappService.sendMessage(context.from, "Nenhum pagamento pendente para liberar no momento.")
                sendBackButton(context, whatsappService, "financeiro")
                return
            }

            adminService.setPendingAction(context.from, "liberar")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Liberar Consulta*\n\n")
                    append("Use esta opção quando o cliente já pagou mas o sistema não confirmou automaticamente.\n\n")
                    append("Pagamentos pendentes:\n")
                    pending.forEach { (phone, session) ->
                        append("  *$phone* — ${session.tipoLabel} (R\$ ${"%.2f".format(session.price)})\n")
                    }
                    append("\nInforme o número para liberar:")
                }
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        val session = paymentSessionManager.getSession(normalized)

        if (session == null) {
            whatsappService.sendMessage(context.from, "Nenhum pagamento pendente encontrado para *$normalized*.")
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
            body = "Sua consulta *${session.tipoLabel}* está liberada.",
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
            whatsappService.sendMessage(context.from, "O bot já está bloqueado para consultas.")
        } else {
            adminService.blockBot()
            whatsappService.sendMessage(context.from, "Bot *bloqueado*.\nNenhum cliente poderá fazer consultas até você liberar.\nVocê continua podendo consultar normalmente.")
        }
        sendBackButton(context, whatsappService, "controle")
    }

    private fun handleUnblock(context: CommandContext, whatsappService: WhatsappService) {
        if (!adminService.isBotBlocked()) {
            whatsappService.sendMessage(context.from, "O bot já está liberado para consultas.")
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
                append("*Estatísticas de Consultas*\n\n")

                append("*Resumo Geral*\n")
                append("Total de consultas: *$total*\n")
                append("Com sucesso: *$success*\n")
                append("Com falha: *$fail*\n")
                append("Taxa de sucesso: *$rate*\n\n")

                append("*Custos Internos (API)*\n")
                append("Gasto total: *R\$ ${"%.2f".format(cost)}*\n\n")

                append("*Usuários*\n")
                append("Únicos: *$users*\n")
                append("Banidos: *${adminService.getBannedCount()}*")
            }
        )

        sendBackButton(context, whatsappService, "relatorios")
    }

    private fun showTopModules(context: CommandContext, whatsappService: WhatsappService) {
        val top = consultationStats.getTopTypes(10)

        if (top.isEmpty()) {
            whatsappService.sendMessage(context.from, "*Top Módulos*\n\nNenhuma consulta registrada ainda.")
            sendBackButton(context, whatsappService, "relatorios")
            return
        }

        val total = consultationStats.getTotal()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Top Módulos Consultados*\n\n")
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
            whatsappService.sendMessage(context.from, "*Histórico Recente*\n\nNenhuma consulta registrada ainda.")
            sendBackButton(context, whatsappService, "relatorios")
            return
        }

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Histórico Recente*\n")
                append("Últimas ${logs.size} consultas\n\n")
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

                append("*Módulos*\n")
                append("Total: $totalTypes\n")
                append("Ativos: $enabledCount\n")
                append("Com preço: $pricedCount\n\n")

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

                append("*Usuários*\n")
                append("Únicos: $users\n")
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
            "*Contadores resetados*\n\nEstatísticas de consultas e pagamentos foram zeradas.\nPreços, módulos e bans *não* foram afetados."
        )
        sendBackButton(context, whatsappService, "controle")
    }

    // ══════════════════════════════════════════════════════════════════
    // SUPORTE
    // ══════════════════════════════════════════════════════════════════

    private fun handleSetSupportPhone(context: CommandContext, whatsappService: WhatsappService) {
        val phone = context.args.getOrNull(1)

        if (phone.isNullOrBlank()) {
            adminService.setPendingAction(context.from, "suporte_phone")
            whatsappService.sendMessage(
                context.from,
                "*Definir Telefone de Suporte*\n\nInforme o numero de telefone com DDD e codigo do pais.\nExemplo: 5581999999999"
            )
            return
        }

        val normalized = phone.replace(Regex("[^0-9]"), "")
        if (normalized.length < 10) {
            whatsappService.sendMessage(context.from, "Numero invalido. Informe com DDD e codigo do pais (ex: 5581999999999).")
            return
        }

        adminService.setSupportPhone(normalized)
        whatsappService.sendMessage(
            context.from,
            "*Telefone de suporte definido!*\n\nNumero: $normalized\nLink: https://wa.me/$normalized"
        )
        sendBackButton(context, whatsappService, "controle")
    }

    private fun handleRemoveSupportPhone(context: CommandContext, whatsappService: WhatsappService) {
        adminService.removeSupportPhone()
        whatsappService.sendMessage(context.from, "*Telefone de suporte removido.*\n\nOs clientes nao terao a opcao de suporte ate que um novo numero seja definido.")
        sendBackButton(context, whatsappService, "controle")
    }

    // ══════════════════════════════════════════════════════════════════
    // ADMINISTRADORES (somente super admin)
    // ══════════════════════════════════════════════════════════════════

    private fun requireSuperAdmin(context: CommandContext, whatsappService: WhatsappService): Boolean {
        if (!adminService.isSuperAdmin(context.from)) {
            whatsappService.sendMessage(context.from, "Apenas o *super admin* pode gerenciar administradores.")
            sendBackButton(context, whatsappService)
            return false
        }
        return true
    }

    private fun showAdminsMenu(context: CommandContext, whatsappService: WhatsappService) {
        if (!requireSuperAdmin(context, whatsappService)) return

        val admins = adminService.getAdminNumbers()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Gerenciar Administradores*\n\n")
                append("Você é o *super admin* (número da ENV).\n")
                append("Admins adicionados podem usar o painel completo, exceto gerenciar outros admins.\n\n")
                append("*Admins atuais:* ${admins.size + 1}\n")
                append("  *Super:* ${context.from}\n")
                admins.forEach { phone ->
                    append("  $phone\n")
                }
            }
        )

        whatsappService.sendList(
            to = context.from,
            header = "Administradores",
            body = "Selecione uma ação:",
            buttonLabel = "Ações",
            footer = "ND Consultas | Admin",
            sections = listOf(
                ListSection(
                    title = "Administradores",
                    rows = listOf(
                        ListRow("/admin admins", "Ver Administradores", "Lista completa de admins"),
                        ListRow("/admin addadmin", "Adicionar Admin", "Dar acesso admin a um número"),
                        ListRow("/admin rmadmin", "Remover Admin", "Revogar acesso admin")
                    )
                )
            )
        )
    }

    private fun showAdmins(context: CommandContext, whatsappService: WhatsappService) {
        if (!requireSuperAdmin(context, whatsappService)) return

        val admins = adminService.getAdminNumbers()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Lista de Administradores*\n\n")
                append("1. *${context.from}* (super admin)\n")
                if (admins.isEmpty()) {
                    append("\nNenhum admin adicional cadastrado.")
                } else {
                    admins.forEachIndexed { i, phone ->
                        append("${i + 2}. $phone\n")
                    }
                }
                append("\n_O super admin não pode ser removido._")
            }
        )

        sendBackButton(context, whatsappService, "admins")
    }

    private fun handleAddAdmin(context: CommandContext, whatsappService: WhatsappService) {
        if (!requireSuperAdmin(context, whatsappService)) return

        val number = context.args.getOrNull(1)

        if (number == null) {
            adminService.setPendingAction(context.from, "addadmin")
            whatsappService.sendMessage(
                context.from,
                "*Adicionar Admin*\n\nInforme o número completo com DDI.\nEx: `5581999998888`\n\n_O número terá acesso total ao painel administrativo._"
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        if (normalized.length < 10) {
            whatsappService.sendMessage(context.from, "Número inválido. Informe com DDI + DDD + número.\nEx: `5581999998888`")
            sendBackButton(context, whatsappService, "admins")
            return
        }

        val result = adminService.addAdmin(normalized)

        when (result.reason) {
            "super_admin" -> {
                whatsappService.sendMessage(context.from, "Este número já é o *super admin*.")
            }
            "already_admin" -> {
                whatsappService.sendMessage(context.from, "O número *$normalized* já é admin.")
            }
            "ok" -> {
                whatsappService.sendMessage(
                    context.from,
                    "*Admin adicionado com sucesso*\n\nNúmero: *$normalized*\nAgora tem acesso ao painel administrativo."
                )
            }
        }

        sendBackButton(context, whatsappService, "admins")
    }

    private fun handleRemoveAdmin(context: CommandContext, whatsappService: WhatsappService) {
        if (!requireSuperAdmin(context, whatsappService)) return

        val number = context.args.getOrNull(1)

        if (number == null) {
            val admins = adminService.getAdminNumbers()
            if (admins.isEmpty()) {
                whatsappService.sendMessage(context.from, "Nenhum admin adicional para remover.")
                sendBackButton(context, whatsappService, "admins")
                return
            }

            adminService.setPendingAction(context.from, "rmadmin")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Remover Admin*\n\n")
                    append("Admins cadastrados:\n")
                    admins.forEachIndexed { i, phone ->
                        append("${i + 1}. $phone\n")
                    }
                    append("\nInforme o número para remover:")
                }
            )
            return
        }

        val normalized = number.replace(Regex("[^0-9]"), "")
        val result = adminService.removeAdmin(normalized)

        when (result.reason) {
            "super_admin" -> {
                whatsappService.sendMessage(context.from, "O *super admin* não pode ser removido.")
            }
            "not_admin" -> {
                whatsappService.sendMessage(context.from, "O número *$normalized* não é admin.")
            }
            "ok" -> {
                whatsappService.sendMessage(
                    context.from,
                    "*Admin removido com sucesso*\n\nNúmero: *$normalized*\nAcesso ao painel revogado."
                )
            }
        }

        sendBackButton(context, whatsappService, "admins")
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
