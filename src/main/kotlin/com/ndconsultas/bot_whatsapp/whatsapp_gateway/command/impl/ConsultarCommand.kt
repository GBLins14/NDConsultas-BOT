package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.QueryTypeRegistry
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.DebitoVeicularService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.DebitoVeicularSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PdfReportService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PricingService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.QrCodeService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ScheduledCrlvPoller
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.VehicleConsultationService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.ScheduledCrlvOrderEntity
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.ScheduledCrlvOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class ConsultarCommand(
    private val consultationService: VehicleConsultationService,
    private val sessionManager: ConsultationSessionManager,
    private val pdfService: PdfReportService,
    private val adminService: AdminService,
    private val consultationStats: ConsultationStats,
    private val pricingService: PricingService,
    private val paymentSessionManager: PaymentSessionManager,
    private val paymentStats: PaymentStats,
    private val paymentService: PaymentService,
    private val queryTypeRegistry: QueryTypeRegistry,
    private val scheduledCrlvOrderRepository: ScheduledCrlvOrderRepository,
    private val debitoVeicularService: DebitoVeicularService,
    private val debitoVeicularSessionManager: DebitoVeicularSessionManager,
    private val qrCodeService: QrCodeService
) : BotCommand {

    override val name = "/consultar"
    override val description = "Painel de consulta veicular"
    override val aliases = listOf("/consulta", "/consultas", "/c")

    companion object {
        private val log = LoggerFactory.getLogger(ConsultarCommand::class.java)
        private val FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        if (adminService.isBotBlocked() && !adminService.isAdmin(context.from)) {
            whatsappService.sendMessage(
                context.from,
                "O sistema de consultas está temporariamente indisponível.\nTente novamente mais tarde."
            )
            return
        }

        when {
            context.args.isEmpty() -> showCategories(context, whatsappService)
            context.args[0] == "cat" && context.args.size >= 2 -> showCategoryTypes(context, whatsappService)
            context.args[0] == "cancelar_operacao" -> cancelOperation(context, whatsappService)
            context.args[0] == "cancelar_pgto" -> cancelPayment(context, whatsappService)
            context.args[0] == "pago" -> executeFromPayment(context, whatsappService)
            context.args[0] == "pgto_pix" -> handlePixPayment(context, whatsappService)
            context.args[0] == "pgto_cartao" -> startCardPayment(context, whatsappService)
            context.args[0] == "cartao_input" -> handleCardInput(context, whatsappService)
            context.args[0] == "crlv_digital" && context.args.size == 1 -> showCrlvRegions(context, whatsappService)
            context.args[0] == "crlv_regiao" && context.args.size >= 2 -> showCrlvRegionStates(context, whatsappService)
            context.args[0] == "crlv_agendado" && context.args.size == 1 -> showCrlvAgendadoStates(context, whatsappService)
            context.args[0] == "status_agendado" -> showScheduledCrlvStatus(context, whatsappService)
            context.args[0] == "debitos_listar" -> showDebitosParaPagamento(context, whatsappService)
            context.args[0] == "debito_pagar" && context.args.size >= 2 -> pagarDebitoItem(context, whatsappService)
            context.args.size == 1 -> promptForData(context, whatsappService)
            else -> executeQuery(context, whatsappService)
        }
    }

    // ── Step 1: Listar categorias ──────────────────────────────────

    private fun showCategories(context: CommandContext, whatsappService: WhatsappService) {
        val isAdmin = adminService.isAdmin(context.from)

        val rows = queryTypeRegistry.categories.mapNotNull { (catKey, catInfo) ->
            val allTypes = queryTypeRegistry.getTypesForCategory(catKey)
            val types = if (isAdmin) allTypes
                else allTypes.filter { pricingService.isModuleEnabled(it.key) }

            if (types.isEmpty()) return@mapNotNull null

            ListRow(
                id = "/consultar cat $catKey",
                title = catInfo.label,
                description = catInfo.description
            )
        }

        if (rows.isEmpty()) {
            whatsappService.sendMessage(
                context.from,
                "Nenhum módulo de consulta disponível no momento.\nTente novamente mais tarde."
            )
            return
        }

        whatsappService.sendList(
            to = context.from,
            header = "Painel de Consultas",
            body = buildString {
                append("Bem-vindo ao *Painel de Consultas*\n\n")
                append("Selecione a categoria de consulta que deseja realizar.")
            },
            buttonLabel = "Ver Categorias",
            footer = "ND Consultas",
            sections = listOf(ListSection(title = "Categorias", rows = rows))
        )
    }

    // ── Step 2: Listar módulos de uma categoria ────────────────────

    private fun showCategoryTypes(context: CommandContext, whatsappService: WhatsappService) {
        val catKey = context.args[1]
        val catInfo = queryTypeRegistry.categories[catKey]

        if (catInfo == null) {
            showCategories(context, whatsappService)
            return
        }

        val isAdmin = adminService.isAdmin(context.from)
        val allTypes = queryTypeRegistry.getTypesForCategory(catKey)
        val types = if (isAdmin) allTypes
            else allTypes.filter { pricingService.isModuleEnabled(it.key) }

        if (types.isEmpty()) {
            whatsappService.sendMessage(
                context.from,
                "Nenhum módulo disponível nesta categoria no momento."
            )
            return
        }

        val rows = types.map { (tipo, info) ->
            ListRow(
                id = "/consultar $tipo",
                title = info.label,
                description = "Escolha para saber mais."
            )
        }

        whatsappService.sendList(
            to = context.from,
            header = catInfo.label,
            body = "Selecione o tipo de consulta:",
            buttonLabel = "Ver Consultas",
            footer = "ND Consultas",
            sections = listOf(ListSection(title = catInfo.label, rows = rows))
        )
    }

    // ── CRLV: Seleção de região ───────────────────────────────────

    private fun showCrlvRegions(context: CommandContext, whatsappService: WhatsappService) {
        val isAdmin = adminService.isAdmin(context.from)

        val rows = QueryTypeRegistry.CRLV_REGIONS.mapNotNull { (regionKey, region) ->
            val hasStates = region.states.any { isAdmin || pricingService.isModuleEnabled(it) }
            if (!hasStates) return@mapNotNull null

            ListRow(
                id = "/consultar crlv_regiao $regionKey",
                title = region.label,
                description = "CRLV-e ${region.label} (${region.states.size} estados)"
            )
        }

        if (rows.isEmpty()) {
            whatsappService.sendMessage(context.from, "Nenhum estado disponível para CRLV-e no momento.")
            return
        }

        whatsappService.sendList(
            to = context.from,
            header = "CRLV Digital (CRLV-e)",
            body = "Selecione a *região* para emissão do CRLV-e:",
            buttonLabel = "Ver Regiões",
            footer = "ND Consultas",
            sections = listOf(ListSection(title = "Regiões", rows = rows))
        )
    }

    // ── CRLV: Seleção de estado dentro de uma região ──────────────

    private fun showCrlvRegionStates(context: CommandContext, whatsappService: WhatsappService) {
        val regionKey = context.args[1]
        val region = QueryTypeRegistry.CRLV_REGIONS[regionKey]

        if (region == null) {
            showCrlvRegions(context, whatsappService)
            return
        }

        val isAdmin = adminService.isAdmin(context.from)

        val rows = region.states
            .filter { isAdmin || pricingService.isModuleEnabled(it) }
            .mapNotNull { key ->
                val stateName = QueryTypeRegistry.CRLV_STATES[key] ?: return@mapNotNull null
                ListRow(
                    id = "/consultar $key",
                    title = stateName,
                    description = "CRLV-e $stateName"
                )
            }

        if (rows.isEmpty()) {
            whatsappService.sendMessage(context.from, "Nenhum estado disponível nesta região.")
            return
        }

        whatsappService.sendList(
            to = context.from,
            header = "CRLV-e — ${region.label}",
            body = "Selecione o *estado*:",
            buttonLabel = "Ver Estados",
            footer = "ND Consultas",
            sections = listOf(ListSection(title = region.label, rows = rows))
        )
    }

    // ── Step 3: Solicitar dado ──────────────────────────────────────

    private fun promptForData(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args[0]
        val info = queryTypeRegistry.getTypeInfo(tipo)

        if (info == null || (!pricingService.isModuleEnabled(tipo) && !adminService.isAdmin(context.from))) {
            whatsappService.sendMessage(
                context.from,
                "Tipo de consulta inválido ou indisponível.\nUse /consultar para ver as opções disponíveis."
            )
            return
        }

        val isAdmin = adminService.isAdmin(context.from)
        val price = getEffectivePrice(tipo)
        val priceText = when {
            isAdmin -> ""
            price > BigDecimal.ZERO -> "\nValor: *R\$ ${"%.2f".format(price)}*"
            else -> ""
        }

        val needsCrlvMultiField = tipo in QueryTypeRegistry.CRLV_FULL_INPUT_STATES ||
            tipo in QueryTypeRegistry.CRLV_AGENDADO_FULL_INPUT_STATES
        sessionManager.setPending(context.from, tipo, info.label, nextField = if (needsCrlvMultiField) "placa" else null)

        whatsappService.sendMessage(
            context.from,
            "*${info.label}*$priceText\n\n${info.description}\n\n*Retorna:* ${info.returnDetails}\n\n${info.inputPrompt}:"
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "Ou cancele a operação:",
            buttons = listOf(
                Button(id = "/consultar cancelar_operacao", title = "Cancelar")
            )
        )
    }

    // ── Step 4: Executar consulta ───────────────────────────────────

    private fun executeQuery(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args[0]
        val query = context.args.drop(1).joinToString(" ").trim()
        val info = queryTypeRegistry.getTypeInfo(tipo)

        if (info == null || (!pricingService.isModuleEnabled(tipo) && !adminService.isAdmin(context.from))) {
            whatsappService.sendMessage(
                context.from,
                "Tipo de consulta inválido ou indisponível.\nUse /consultar para ver as opções disponíveis."
            )
            return
        }

        // Coleta passo a passo para CRLV com placa + renavam + cpf
        if (tipo in QueryTypeRegistry.CRLV_FULL_INPUT_STATES ||
            tipo in QueryTypeRegistry.CRLV_AGENDADO_FULL_INPUT_STATES) {
            val pending = sessionManager.getPending(context.from)
            if (pending != null && pending.nextField != null) {
                handleCrlvFieldInput(context, whatsappService, tipo, info, query, pending)
                return
            }
        }

        sessionManager.removePending(context.from)

        val price = getEffectivePrice(tipo)
        val isAdmin = adminService.isAdmin(context.from)

        // Admin nunca paga
        if (price > BigDecimal.ZERO && !isAdmin) {
            val paymentSession = paymentSessionManager.getSession(context.from)
            if (paymentSession != null && paymentSession.status == PaymentSessionManager.PaymentStatus.PAID) {
                paymentSessionManager.consume(context.from)
            } else {
                paymentSessionManager.create(context.from, tipo, info.label, query, price)

                whatsappService.sendMessage(
                    context.from,
                    buildString {
                        append("*Pagamento Necessário*\n\n")
                        append("Consulta: *${info.label}*\n")
                        append("Dado: $query\n")
                        append("Valor: *R\$ ${"%.2f".format(price)}*\n\n")
                        append("Escolha a forma de pagamento:")
                    }
                )

                whatsappService.sendButtons(
                    to = context.from,
                    body = "Como deseja pagar?",
                    buttons = listOf(
                        Button(id = "/consultar pgto_pix", title = "PIX"),
                        Button(id = "/consultar pgto_cartao", title = "Cartão de Crédito"),
                        Button(id = "/consultar cancelar_pgto", title = "Cancelar")
                    )
                )
                return
            }
        }

        if (tipo == "debito_veicular") {
            performDebitoVeicularQuery(context, whatsappService, info, query)
        } else if (queryTypeRegistry.isAgendadoType(tipo)) {
            submitScheduledCrlv(context, whatsappService, tipo, info, query)
        } else {
            performQuery(context, whatsappService, tipo, info, query)
        }
    }

    // ── CRLV: Coleta passo a passo (placa → renavam → cpf) ───────

    private fun handleCrlvFieldInput(
        context: CommandContext,
        whatsappService: WhatsappService,
        tipo: String,
        info: QueryTypeRegistry.QueryTypeInfo,
        input: String,
        pending: ConsultationSessionManager.PendingConsultation
    ) {
        val fields = pending.collectedFields.toMutableMap()

        when (pending.nextField) {
            "placa" -> {
                val placa = input.trim().uppercase()
                if (placa.isBlank()) {
                    whatsappService.sendMessage(context.from, "Placa inválida. Informe a *placa* do veículo:\n_Formato: ABC1234 ou ABC1A23_")
                    return
                }
                fields["placa"] = placa
                sessionManager.updatePending(context.from, fields, "renavam")
                whatsappService.sendMessage(context.from, "Agora informe o *RENAVAM*:\n_Formato: 00123456789 (11 dígitos)_")
            }
            "renavam" -> {
                val renavam = input.trim().replace(Regex("[^0-9]"), "")
                if (renavam.isBlank()) {
                    whatsappService.sendMessage(context.from, "RENAVAM inválido. Informe o *RENAVAM* (somente números):\n_Formato: 00123456789 (11 dígitos)_")
                    return
                }
                fields["renavam"] = renavam
                sessionManager.updatePending(context.from, fields, "cpf")
                whatsappService.sendMessage(context.from, "Agora informe o *CPF* do proprietário:\n_Formato: 12345678901 (11 dígitos)_")
            }
            "cpf" -> {
                val cpf = input.trim().replace(Regex("[^0-9]"), "")
                if (cpf.length != 11) {
                    whatsappService.sendMessage(context.from, "CPF inválido. Informe o *CPF* (11 dígitos):\n_Formato: 12345678901_")
                    return
                }
                fields["cpf"] = cpf
                sessionManager.removePending(context.from)

                // Todos os campos coletados — montar query e executar
                val fullQuery = "${fields["placa"]} ${fields["renavam"]} ${fields["cpf"]}"
                val fullCtx = context.copy(
                    rawMessage = "/consultar $tipo $fullQuery",
                    args = listOf(tipo, fields["placa"]!!, fields["renavam"]!!, fields["cpf"]!!)
                )
                executeQuery(fullCtx, whatsappService)
            }
        }
    }

    // ── CRLV Agendado: Seleção de estado ─────────────────────────

    private fun showCrlvAgendadoStates(context: CommandContext, whatsappService: WhatsappService) {
        val isAdmin = adminService.isAdmin(context.from)

        val rows = QueryTypeRegistry.CRLV_AGENDADO_STATES.mapNotNull { (key, stateName) ->
            if (!isAdmin && !pricingService.isModuleEnabled("crlv_agendado")) return@mapNotNull null

            ListRow(
                id = "/consultar $key",
                title = stateName,
                description = "CRLV-e Agendado $stateName"
            )
        }

        if (rows.isEmpty()) {
            whatsappService.sendMessage(context.from, "Nenhum estado disponivel para CRLV-e agendado no momento.")
            return
        }

        whatsappService.sendList(
            to = context.from,
            header = "CRLV-e Agendado",
            body = "Selecione o *estado* para solicitar o CRLV-e agendado:\n\n_O documento sera processado e enviado quando estiver pronto._",
            buttonLabel = "Ver Estados",
            footer = "ND Consultas",
            sections = listOf(ListSection(title = "Estados Disponiveis", rows = rows))
        )
    }

    // ── CRLV Agendado: Solicitar ──────────────────────────────────

    private fun submitScheduledCrlv(
        context: CommandContext,
        whatsappService: WhatsappService,
        tipo: String,
        info: QueryTypeRegistry.QueryTypeInfo,
        query: String
    ) {
        val uf = tipo.removePrefix("crlvag_")
        val parts = query.trim().split("\\s+".toRegex())
        val placa = parts[0].uppercase()
        val renavam = parts.getOrNull(1)
        val cpf = parts.getOrNull(2)

        whatsappService.sendMessage(
            context.from,
            "Solicitando *CRLV-e Agendado*...\nAguarde um momento"
        )

        val result = consultationService.solicitarCrlvAgendado(uf, placa, renavam, cpf)

        if (!result.success || result.pedidoId == null) {
            whatsappService.sendMessage(
                context.from,
                "*Erro na solicitacao*\n\n${result.error ?: "Erro desconhecido."}"
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar $tipo", title = "Tentar Novamente"),
                    Button(id = "/consultar", title = "Outra Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )

            if (!ScheduledCrlvPoller.isUserError(result.error)) {
                val stateName = QueryTypeRegistry.CRLV_AGENDADO_STATES[tipo] ?: uf.uppercase()
                notifyAdmin(
                    whatsappService,
                    buildString {
                        append("*ALERTA: Falha ao solicitar CRLV Agendado*\n\n")
                        append("Estado: *$stateName*\n")
                        append("Placa: *$placa*\n")
                        append("Cliente: ${context.from}\n")
                        append("Erro: ${result.error ?: "Erro desconhecido"}\n\n")
                        append("O cliente pode ter sido cobrado. Verifique.")
                    }
                )
            }
            return
        }

        scheduledCrlvOrderRepository.save(
            ScheduledCrlvOrderEntity(
                pedidoId = result.pedidoId,
                userPhone = context.from,
                uf = uf,
                placa = placa,
                renavam = renavam,
                cpf = cpf
            )
        )

        val stateName = QueryTypeRegistry.CRLV_AGENDADO_STATES[tipo] ?: uf.uppercase()

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Solicitacao Enviada!*\n\n")
                append("CRLV-e Agendado — *$stateName*\n")
                append("Placa: *$placa*\n")
                append("Pedido: *#${result.pedidoId}*\n\n")
                append("Voce recebera o documento assim que estiver pronto.\n")
                append("Para acompanhar, use *Ver Status de Pedidos* no painel.")
            }
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/consultar status_agendado", title = "Ver Status"),
                Button(id = "/consultar", title = "Nova Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── CRLV Agendado: Ver status dos pedidos ─────────────────────

    private fun showScheduledCrlvStatus(context: CommandContext, whatsappService: WhatsappService) {
        val orders = scheduledCrlvOrderRepository.findByUserPhoneOrderByCreatedAtDesc(context.from)

        if (orders.isEmpty()) {
            whatsappService.sendMessage(
                context.from,
                "Voce nao possui pedidos de CRLV-e agendado."
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar crlv_agendado", title = "Solicitar CRLV-e"),
                    Button(id = "/consultar", title = "Painel de Consultas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        val statusLabel = mapOf(
            "PENDING" to "Pendente",
            "COMPLETED" to "Concluido",
            "CANCELLED" to "Cancelado",
            "EXPIRED" to "Expirado"
        )

        val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"))

        val text = buildString {
            append("*Seus Pedidos CRLV-e Agendado*\n")

            orders.take(10).forEach { order ->
                val stateName = QueryTypeRegistry.CRLV_AGENDADO_STATES["crlvag_${order.uf}"] ?: order.uf.uppercase()
                val label = statusLabel[order.status] ?: order.status

                append("\n---\n")
                append("Pedido *#${order.pedidoId}*\n")
                append("Estado: *$stateName*\n")
                append("Placa: *${order.placa}*\n")
                append("Status: *$label*\n")
                append("Solicitado: ${dateFmt.format(order.createdAt)}")
                if (!order.adminMessage.isNullOrBlank()) {
                    append("\nObs: ${order.adminMessage}")
                }
            }
        }

        whatsappService.sendMessage(context.from, text)

        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/consultar crlv_agendado", title = "Novo Pedido"),
                Button(id = "/consultar", title = "Painel de Consultas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Débitos Veiculares: executar consulta BB ──────────────────

    private fun performDebitoVeicularQuery(
        context: CommandContext,
        whatsappService: WhatsappService,
        info: QueryTypeRegistry.QueryTypeInfo,
        query: String
    ) {
        whatsappService.sendMessage(
            context.from,
            "Consultando *débitos veiculares (RN)*...\nAguarde um momento \u23F3"
        )

        val renavam = query.trim().split("\\s+".toRegex())[0]
        val uf = "RN"

        val renavamLong = renavam.toLongOrNull()
        if (renavamLong == null) {
            whatsappService.sendMessage(context.from, "*Erro:* RENAVAM inválido.")
            return
        }

        val result = debitoVeicularService.consultarDebitos(renavam = renavamLong, uf = uf)

        if (!result.success) {
            whatsappService.sendMessage(
                context.from,
                "*Erro na consulta de débitos*\n\n${result.error ?: "Erro desconhecido."}"
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar debito_veicular", title = "Tentar Novamente"),
                    Button(id = "/consultar", title = "Outra Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )

            // Notificar admin se for erro de sistema em consulta paga
            val price = getEffectivePrice("debito_veicular")
            if (price > BigDecimal.ZERO) {
                notifyAdmin(
                    whatsappService,
                    buildString {
                        append("*ALERTA: Consulta de débitos veiculares falhou*\n\n")
                        append("RENAVAM: *$renavam*\n")
                        append("UF: *$uf*\n")
                        append("Cliente: ${context.from}\n")
                        append("Erro: ${result.error ?: "Erro desconhecido"}\n\n")
                        append("O cliente pode ter sido cobrado. Verifique.")
                    }
                )
            }
            return
        }

        val debitos = result.debitos!!

        // Salvar sessão para pagamento posterior
        debitoVeicularSessionManager.save(
            context.from,
            DebitoVeicularSessionManager.DebitoSession(
                codigoSolicitacao = debitos.codigoSolicitacao ?: "",
                renavam = renavam,
                uf = uf,
                placa = debitos.numeroPlaca,
                nomeProprietario = debitos.nomeProprietario,
                timestampLimitePagamento = debitos.timestampLimitePagamento,
                servicos = debitos.listaServicos?.mapIndexed { idx, s ->
                    DebitoVeicularSessionManager.ServicoDebitoInfo(
                        index = idx,
                        codigoServico = s.codigoServico,
                        nomeServico = s.nomeServico ?: "Serviço",
                        numeroIdentificadorItem = s.numeroIdentificadorItem,
                        valorItem = s.valorItem ?: 0.0,
                        codigoTextoItem = s.codigoTextoItem ?: "",
                        numeroUnicoItemBanco = s.numeroUnicoItemBanco,
                        codigoEstado = s.codigoEstado
                    )
                } ?: emptyList()
            )
        )

        // Montar texto com resultados
        val text = buildString {
            append("*Débitos Veiculares*\n\n")
            if (!debitos.nomeProprietario.isNullOrBlank()) append("Proprietário: *${debitos.nomeProprietario}*\n")
            if (!debitos.numeroPlaca.isNullOrBlank()) append("Placa: *${debitos.numeroPlaca}*\n")
            append("RENAVAM: *${debitos.numeroRenavam ?: renavam}*\n")
            append("UF: *${debitos.codigoUf ?: uf}*\n")
            if (!debitos.nomeMunicipio.isNullOrBlank()) append("Município: *${debitos.nomeMunicipio}*\n")
            append("\n")

            val servicos = debitos.listaServicos
            if (servicos.isNullOrEmpty()) {
                append("_Nenhum débito pendente encontrado._")
            } else {
                append("*Débitos encontrados:*\n\n")
                var total = 0.0
                servicos.forEachIndexed { idx, s ->
                    val valor = s.valorItem ?: 0.0
                    total += valor
                    val desc = s.codigoTextoItem ?: s.nomeServico ?: "Débito"
                    append("${idx + 1}. $desc — *R\$ ${"%.2f".format(valor)}*")
                    if (s.codigoEstado != null) append(" (${s.codigoEstado})")
                    append("\n")
                }
                append("\n*Total: R\$ ${"%.2f".format(total)}*")
            }
        }

        whatsappService.sendMessage(context.from, text)

        val servicos = debitos.listaServicos
        if (!servicos.isNullOrEmpty()) {
            whatsappService.sendButtons(
                to = context.from,
                body = "Deseja pagar algum débito via PIX?",
                buttons = listOf(
                    Button(id = "/consultar debitos_listar", title = "Pagar Débitos"),
                    Button(id = "/consultar", title = "Nova Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        } else {
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar", title = "Nova Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        }
    }

    // ── Débitos Veiculares: listar débitos para pagamento ─────────

    private fun showDebitosParaPagamento(context: CommandContext, whatsappService: WhatsappService) {
        val session = debitoVeicularSessionManager.get(context.from)
        if (session == null || session.servicos.isEmpty()) {
            whatsappService.sendMessage(
                context.from,
                "Nenhum resultado de débitos encontrado.\nRealize uma nova consulta de débitos veiculares."
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar debito_veicular", title = "Consultar Débitos"),
                    Button(id = "/consultar", title = "Painel de Consultas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        // Agrupar por nomeServico para organizar em seções
        val groups = session.servicos.groupBy { it.nomeServico }

        val sections = groups.map { (serviceName, items) ->
            val rows = items.take(10).map { s ->
                val title = s.codigoTextoItem.ifBlank { s.nomeServico }
                ListRow(
                    id = "/consultar debito_pagar ${s.index}",
                    title = title.take(24),
                    description = "R\$ ${"%.2f".format(s.valorItem)}"
                )
            }
            ListSection(title = serviceName.take(24), rows = rows)
        }.take(10)

        whatsappService.sendList(
            to = context.from,
            header = "Pagar Débitos",
            body = "Selecione o débito que deseja pagar via *PIX*:\n\n_O pagamento é processado diretamente pelo Banco do Brasil._",
            buttonLabel = "Ver Débitos",
            footer = "ND Consultas",
            sections = sections
        )
    }

    // ── Débitos Veiculares: gerar PIX para débito individual ──────

    private fun pagarDebitoItem(context: CommandContext, whatsappService: WhatsappService) {
        val index = context.args[1].toIntOrNull()
        if (index == null) {
            whatsappService.sendMessage(context.from, "Selecione um débito válido.")
            return
        }

        val session = debitoVeicularSessionManager.get(context.from)
        if (session == null) {
            whatsappService.sendMessage(
                context.from,
                "Sessão expirada. Realize uma nova consulta de débitos veiculares."
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar debito_veicular", title = "Consultar Débitos"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        val servico = session.servicos.find { it.index == index }
        if (servico == null) {
            whatsappService.sendMessage(context.from, "Débito não encontrado. Selecione novamente.")
            showDebitosParaPagamento(context, whatsappService)
            return
        }

        val descricao = servico.codigoTextoItem.ifBlank { servico.nomeServico }
        whatsappService.sendMessage(
            context.from,
            "Gerando PIX para *$descricao*...\nValor: *R\$ ${"%.2f".format(servico.valorItem)}*\nAguarde um momento"
        )

        val result = debitoVeicularService.gerarPixParaDebito(
            codigoSolicitacao = session.codigoSolicitacao,
            codigoServico = servico.codigoServico,
            numeroIdentificadorItem = servico.numeroIdentificadorItem,
            numeroUnicoItemBanco = servico.numeroUnicoItemBanco
        )

        if (!result.success) {
            whatsappService.sendMessage(
                context.from,
                "*Erro ao gerar PIX*\n\n${result.error ?: "Erro desconhecido."}"
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar debitos_listar", title = "Tentar Outro"),
                    Button(id = "/consultar", title = "Nova Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        val pixCode = result.pixCode!!

        // Enviar QR Code como imagem
        try {
            val qrBytes = qrCodeService.generate(pixCode)
            val mediaId = whatsappService.uploadMedia(qrBytes, "image/png", "pix_debito.png")
            whatsappService.sendImageById(
                to = context.from,
                mediaId = mediaId,
                caption = "PIX — $descricao — R\$ ${"%.2f".format(servico.valorItem)}"
            )
        } catch (e: Exception) {
            log.warn("Falha ao enviar QR Code do débito veicular: {}", e.message)
        }

        // Explicação + código PIX separado para cópia fácil
        whatsappService.sendMessage(
            context.from,
            "*PIX Copia e Cola*\n\nDébito: *$descricao*\nValor: *R\$ ${"%.2f".format(servico.valorItem)}*\n\nCopie a mensagem abaixo (toque e segure para copiar):"
        )
        whatsappService.sendMessage(context.from, pixCode)

        whatsappService.sendButtons(
            to = context.from,
            body = "Após o pagamento, o débito será liquidado automaticamente pelo Banco do Brasil.",
            buttons = listOf(
                Button(id = "/consultar debitos_listar", title = "Pagar Outro"),
                Button(id = "/consultar", title = "Nova Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Helper: preço efetivo (fallback para meta-módulo) ─────────

    private fun getEffectivePrice(tipo: String): BigDecimal {
        if (tipo.startsWith("crlvag_")) {
            val parentPrice = pricingService.getPrice("crlv_agendado")
            if (parentPrice > BigDecimal.ZERO) return parentPrice
        }
        if (tipo in QueryTypeRegistry.CRLV_STATES) {
            val parentPrice = pricingService.getPrice("crlv_digital")
            if (parentPrice > BigDecimal.ZERO) return parentPrice
        }
        return pricingService.getPrice(tipo)
    }

    private fun notifyAdmin(whatsappService: WhatsappService, message: String) {
        val adminPhone = adminService.getSuperAdminPhone() ?: return
        try {
            whatsappService.sendMessage(adminPhone, message)
        } catch (e: Exception) {
            log.error("Falha ao notificar admin: {}", e.message)
        }
    }

    // ── Pagamento PIX ──────────────────────────────────────────────

    private fun handlePixPayment(context: CommandContext, whatsappService: WhatsappService) {
        val session = paymentSessionManager.getSession(context.from)
        if (session == null) {
            whatsappService.sendMessage(context.from, "Sessão de pagamento expirada. Inicie uma nova consulta.")
            return
        }

        whatsappService.sendMessage(context.from, "Gerando PIX... Aguarde um momento.")

        val result = paymentService.generatePix(
            userPhone = context.from,
            amount = session.price,
            description = "ND Consultas - ${session.tipoLabel}"
        )

        if (!result.success) {
            whatsappService.sendMessage(context.from, result.error ?: "Erro ao gerar PIX.")
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar pgto_pix", title = "Tentar Novamente"),
                    Button(id = "/consultar cancelar_pgto", title = "Cancelar")
                )
            )
            return
        }

        // Enviar QR Code como imagem
        if (result.qrCodeBytes != null) {
            try {
                val mediaId = whatsappService.uploadMedia(result.qrCodeBytes, "image/png", "pix_qrcode.png")
                whatsappService.sendImageById(
                    to = context.from,
                    mediaId = mediaId,
                    caption = "QR Code PIX - R\$ ${"%.2f".format(session.price)}"
                )
            } catch (e: Exception) {
                log.warn("Falha ao enviar QR Code como imagem: {}", e.message)
            }
        }

        // Explicação
        whatsappService.sendMessage(
            context.from,
            "*PIX Copia e Cola*\n\nValor: *R\$ ${"%.2f".format(session.price)}*\n\nCopie a mensagem abaixo (toque e segure para copiar):"
        )

        // Código PIX sozinho em uma mensagem separada — evita que o WhatsApp
        // adicione https:// ao copiar, pois o usuário copia a mensagem inteira.
        whatsappService.sendMessage(context.from, result.pixCode!!)

        whatsappService.sendMessage(
            context.from,
            "Após o pagamento, sua consulta será processada automaticamente."
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "Aguardando pagamento PIX...",
            buttons = listOf(
                Button(id = "/consultar cancelar_pgto", title = "Cancelar"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Pagamento Cartão: iniciar coleta ───────────────────────────

    private fun startCardPayment(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendMessage(
            context.from,
            "O pagamento via *cartão de crédito* está temporariamente indisponível.\n\nPor favor, utilize o *PIX* para realizar o pagamento."
        )
        whatsappService.sendButtons(
            to = context.from,
            body = "Deseja pagar via PIX?",
            buttons = listOf(
                Button(id = "/consultar pgto_pix", title = "Pagar com PIX"),
                Button(id = "/consultar cancelar_pgto", title = "Cancelar")
            )
        )
    }

    // ── Pagamento Cartão: processar input step-by-step ─────────────

    fun handleCardInput(context: CommandContext, whatsappService: WhatsappService) {
        val session = paymentSessionManager.getSession(context.from)
        if (session == null || session.status != PaymentSessionManager.PaymentStatus.COLLECTING_CARD) {
            whatsappService.sendMessage(context.from, "Sessão de pagamento expirada. Inicie uma nova consulta.")
            return
        }

        val input = context.args.drop(1).joinToString(" ").trim()
        val cardInput = session.cardInput ?: PaymentSessionManager.CardInput()
        val step = cardInput.currentStep()

        val updated = when (step) {
            "card_number" -> {
                val number = input.replace(Regex("[^0-9]"), "")
                if (number.length < 13 || number.length > 19) {
                    whatsappService.sendMessage(context.from, "Número de cartão inválido. Informe novamente:")
                    return
                }
                cardInput.copy(number = number)
            }
            "card_holder" -> {
                if (input.length < 3) {
                    whatsappService.sendMessage(context.from, "Nome inválido. Informe o nome como aparece no cartão:")
                    return
                }
                cardInput.copy(holderName = input.uppercase())
            }
            "card_expiry" -> {
                val cleaned = input.replace(Regex("[^0-9/]"), "")
                val parts = cleaned.split("/")
                if (parts.size != 2) {
                    whatsappService.sendMessage(context.from, "Formato inválido. Use *MM/AA* ou *MM/AAAA*:")
                    return
                }
                val month = parts[0].padStart(2, '0')
                val year = if (parts[1].length == 2) "20${parts[1]}" else parts[1]
                if (month.toIntOrNull() !in 1..12) {
                    whatsappService.sendMessage(context.from, "Mês inválido. Use *MM/AA* ou *MM/AAAA*:")
                    return
                }
                cardInput.copy(expiryMonth = month, expiryYear = year)
            }
            "card_cvv" -> {
                val cvv = input.replace(Regex("[^0-9]"), "")
                if (cvv.length !in 3..4) {
                    whatsappService.sendMessage(context.from, "CVV inválido. Informe os *3 ou 4 dígitos* do verso do cartão:")
                    return
                }
                cardInput.copy(cvv = cvv)
            }
            else -> cardInput
        }

        paymentSessionManager.updateCardInput(context.from, updated)

        if (updated.isComplete()) {
            processCardCharge(context, whatsappService, session, updated)
        } else {
            promptNextCardField(context, whatsappService, updated)
        }
    }

    private fun promptNextCardField(
        context: CommandContext,
        whatsappService: WhatsappService,
        cardInput: PaymentSessionManager.CardInput
    ) {
        val prompt = when (cardInput.currentStep()) {
            "card_holder" -> "Informe o *nome do titular* (como está no cartão):"
            "card_expiry" -> "Informe a *validade* (MM/AA):"
            "card_cvv" -> "Informe o *CVV* (3 ou 4 dígitos do verso):"
            else -> return
        }
        whatsappService.sendMessage(context.from, prompt)
    }

    private fun processCardCharge(
        context: CommandContext,
        whatsappService: WhatsappService,
        session: PaymentSessionManager.PaymentSession,
        cardInput: PaymentSessionManager.CardInput
    ) {
        whatsappService.sendMessage(context.from, "Processando pagamento... Aguarde.")

        val result = paymentService.chargeCard(
            userPhone = context.from,
            cardInput = cardInput,
            amount = session.price,
            description = "ND Consultas - ${session.tipoLabel}"
        )

        if (!result.success) {
            whatsappService.sendMessage(
                context.from,
                "*Pagamento recusado*\n\n${result.error ?: "Erro ao processar."}"
            )
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar pgto_cartao", title = "Tentar Novamente"),
                    Button(id = "/consultar pgto_pix", title = "Pagar com PIX"),
                    Button(id = "/consultar cancelar_pgto", title = "Cancelar")
                )
            )
            return
        }

        val brandText = if (result.brand != null) " ${result.brand}" else ""
        whatsappService.sendMessage(
            context.from,
            "Pagamento aprovado!$brandText ****${result.last4 ?: ""}\n\nProcessando sua consulta..."
        )

        val info = queryTypeRegistry.getTypeInfo(session.tipo)
        if (info != null) {
            paymentSessionManager.consume(context.from)
            if (session.tipo == "debito_veicular") {
                performDebitoVeicularQuery(context, whatsappService, info, session.query)
            } else if (queryTypeRegistry.isAgendadoType(session.tipo)) {
                submitScheduledCrlv(context, whatsappService, session.tipo, info, session.query)
            } else {
                performQuery(context, whatsappService, session.tipo, info, session.query)
            }
        }
    }

    // ── Executar a partir de pagamento confirmado (PIX webhook) ────

    private fun executeFromPayment(context: CommandContext, whatsappService: WhatsappService) {
        val session = paymentSessionManager.getSession(context.from)
        if (session == null || session.status != PaymentSessionManager.PaymentStatus.PAID) {
            whatsappService.sendMessage(context.from, "Nenhum pagamento confirmado encontrado.\nUse /consultar para iniciar uma nova consulta.")
            return
        }

        val info = queryTypeRegistry.getTypeInfo(session.tipo)
        if (info == null) {
            paymentSessionManager.consume(context.from)
            whatsappService.sendMessage(context.from, "Tipo de consulta inválido. Use /consultar para iniciar uma nova consulta.")
            return
        }

        paymentSessionManager.consume(context.from)
        if (session.tipo == "debito_veicular") {
            performDebitoVeicularQuery(context, whatsappService, info, session.query)
        } else if (queryTypeRegistry.isAgendadoType(session.tipo)) {
            submitScheduledCrlv(context, whatsappService, session.tipo, info, session.query)
        } else {
            performQuery(context, whatsappService, session.tipo, info, session.query)
        }
    }

    // ── Cancelar operação pendente (coleta de dados) ────────────────

    private fun cancelOperation(context: CommandContext, whatsappService: WhatsappService) {
        sessionManager.removePending(context.from)
        whatsappService.sendMessage(context.from, "Operação cancelada.")
        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/consultar", title = "Nova Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Cancelar pagamento pendente ────────────────────────────────

    private fun cancelPayment(context: CommandContext, whatsappService: WhatsappService) {
        val session = paymentSessionManager.cancel(context.from)
        if (session != null) {
            whatsappService.sendMessage(context.from, "Consulta cancelada.")
        }
        whatsappService.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/consultar", title = "Nova Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Core: executar consulta na API ─────────────────────────────

    private fun performQuery(
        context: CommandContext,
        whatsappService: WhatsappService,
        tipo: String,
        info: QueryTypeRegistry.QueryTypeInfo,
        query: String
    ) {
        if (context.messageId.isNotBlank()) {
            try {
                whatsappService.sendReaction(context.from, context.messageId, "\u23F3")
            } catch (e: Exception) {
                log.warn("Falha ao enviar reaction de processamento: {}", e.message)
            }
        }

        whatsappService.sendMessage(
            context.from,
            "Consultando *${info.label}*...\nAguarde um momento \u23F3"
        )

        val result = consultationService.consultar(tipo, query)

        if (context.messageId.isNotBlank()) {
            try {
                whatsappService.removeReaction(context.from, context.messageId)
            } catch (e: Exception) {
                log.warn("Falha ao remover reaction: {}", e.message)
            }
        }

        consultationStats.record(context.from, tipo, info.label, query, result.success, result.custo)

        if (!result.success) {
            if (context.messageId.isNotBlank()) {
                try {
                    whatsappService.sendReaction(context.from, context.messageId, "\u274C")
                } catch (e: Exception) {
                    log.warn("Falha ao enviar reaction de erro: {}", e.message)
                }
            }

            whatsappService.sendMessage(
                context.from,
                buildString {
                    append("*Erro na consulta*\n\n")
                    append("Tipo: ${info.label}\n")
                    append("Dado: $query\n\n")
                    append(result.error ?: "Erro desconhecido.")
                }
            )

            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar $tipo", title = "Tentar Novamente"),
                    Button(id = "/consultar", title = "Outra Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )

            // Notificar admin se for erro de sistema em consulta paga
            val price = getEffectivePrice(tipo)
            if (price > BigDecimal.ZERO && !ScheduledCrlvPoller.isUserError(result.error)) {
                notifyAdmin(
                    whatsappService,
                    buildString {
                        append("*ALERTA: Consulta paga falhou com erro de sistema*\n\n")
                        append("Tipo: *${info.label}*\n")
                        append("Dado: $query\n")
                        append("Valor: *R\$ ${"%.2f".format(price)}*\n")
                        append("Cliente: ${context.from}\n")
                        append("Erro: ${result.error ?: "Erro desconhecido"}\n\n")
                        append("Verifique se o cliente foi cobrado.")
                    }
                )
            }
            return
        }

        if (context.messageId.isNotBlank()) {
            try {
                whatsappService.sendReaction(context.from, context.messageId, "\u2705")
            } catch (e: Exception) {
                log.warn("Falha ao enviar reaction de sucesso: {}", e.message)
            }
        }

        // PDF direto da API (CRLV-e / CRV)
        if (result.pdfBytes != null) {
            sendDirectPdf(context, whatsappService, info, tipo, query, result.pdfBytes)
        } else {
            val pdfSent = trySendPdf(context, whatsappService, info, tipo, query, result.data)
            if (!pdfSent) {
                log.warn("Fallback para envio de texto — PDF falhou para {} query={}", tipo, query)
                sendResultAsText(context, whatsappService, info, query, result.data)
            }
        }

        whatsappService.sendButtons(
            to = context.from,
            body = "Deseja realizar outra consulta?",
            buttons = listOf(
                Button(id = "/consultar", title = "Nova Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Envio de PDF direto (Portal Despachantes) ─────────────────

    private fun sendDirectPdf(
        context: CommandContext,
        whatsappService: WhatsappService,
        info: QueryTypeRegistry.QueryTypeInfo,
        tipo: String,
        query: String,
        pdfBytes: ByteArray
    ) {
        try {
            val timestamp = LocalDateTime.now().format(FILE_DATE_FMT)
            val filename = "${tipo}_${timestamp}.pdf"

            val mediaId = whatsappService.uploadMedia(pdfBytes, "application/pdf", filename)

            whatsappService.sendDocumentById(
                to = context.from,
                mediaId = mediaId,
                filename = filename,
                caption = "${info.label} - $query"
            )
        } catch (e: Exception) {
            log.error("Erro ao enviar PDF direto [{}] query={}: {}", tipo, query, e.message, e)
            whatsappService.sendMessage(
                context.from,
                "O documento foi gerado com sucesso, mas houve um erro ao enviar o PDF. Tente novamente."
            )
        }
    }

    // ── Envio de PDF gerado ───────────────────────────────────────

    private fun trySendPdf(
        context: CommandContext,
        whatsappService: WhatsappService,
        info: QueryTypeRegistry.QueryTypeInfo,
        tipo: String,
        query: String,
        data: Map<String, Any?>
    ): Boolean {
        return try {
            val pdfBytes = pdfService.generate(tipo, info.label, query, data)
            val timestamp = LocalDateTime.now().format(FILE_DATE_FMT)
            val filename = "consulta_${tipo}_${timestamp}.pdf"

            val mediaId = whatsappService.uploadMedia(pdfBytes, "application/pdf", filename)

            whatsappService.sendDocumentById(
                to = context.from,
                mediaId = mediaId,
                filename = filename,
                caption = "${info.label} - $query"
            )

            true
        } catch (e: Exception) {
            log.error("Erro ao gerar/enviar PDF [{}] query={}: {}", tipo, query, e.message, e)
            false
        }
    }

    // ── Fallback: envio como texto ─────────────────────────────────

    private fun sendResultAsText(
        context: CommandContext,
        whatsappService: WhatsappService,
        info: QueryTypeRegistry.QueryTypeInfo,
        query: String,
        data: Map<String, Any?>
    ) {
        whatsappService.sendMessage(
            context.from,
            "*${info.label}*\nDado consultado: $query"
        )

        val formattedData = formatResult(data)
        val messages = splitMessage(formattedData, 3500)

        messages.forEach { msg ->
            whatsappService.sendMessage(context.from, msg)
        }
    }

    // ── Formatação de resultado (fallback texto) ───────────────────

    private fun formatResult(data: Map<String, Any?>): String {
        if (data.isEmpty()) return "_Nenhum dado retornado para esta consulta._"

        val lines = flattenMap(data, "")
        return lines.joinToString("\n") { (key, value) ->
            val formattedKey = formatKey(key)
            "*$formattedKey:* $value"
        }
    }

    private fun formatKey(key: String): String {
        return key.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    private fun flattenMap(map: Map<String, Any?>, prefix: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        map.forEach { (key, value) ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix > $key"
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    result.addAll(flattenMap(value as Map<String, Any?>, fullKey))
                }
                is List<*> -> {
                    if (value.isEmpty()) {
                        result.add(fullKey to "Nao informado")
                    } else if (value.first() is Map<*, *>) {
                        value.forEachIndexed { index, item ->
                            if (item is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                result.addAll(flattenMap(item as Map<String, Any?>, "$fullKey [${index + 1}]"))
                            }
                        }
                    } else {
                        val joined = value.filterNotNull().joinToString(", ")
                        result.add(fullKey to joined.ifBlank { "Nao informado" })
                    }
                }
                null -> result.add(fullKey to "Nao informado")
                else -> {
                    val text = value.toString().trim()
                    result.add(fullKey to text.ifBlank { "Nao informado" })
                }
            }
        }
        return result
    }

    private fun splitMessage(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val messages = mutableListOf<String>()
        val lines = text.split("\n")
        val current = StringBuilder()

        for (line in lines) {
            if (current.length + line.length + 1 > maxLength && current.isNotEmpty()) {
                messages.add(current.toString().trim())
                current.clear()
            }
            current.appendLine(line)
        }
        if (current.isNotEmpty()) {
            messages.add(current.toString().trim())
        }
        return messages
    }
}
