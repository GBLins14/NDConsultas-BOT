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
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PdfReportService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PricingService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.VehicleConsultationService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
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
    private val queryTypeRegistry: QueryTypeRegistry
) : BotCommand {

    override val name = "/consultar"
    override val description = "Painel de consulta veicular"
    override val aliases = listOf("/consulta", "/c")

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
            context.args[0] == "cancelar_pgto" -> cancelPayment(context, whatsappService)
            context.args[0] == "pago" -> executeFromPayment(context, whatsappService)
            context.args[0] == "cpf_input" -> handleCpfInput(context, whatsappService)
            context.args.size == 1 -> promptForData(context, whatsappService)
            else -> executeQuery(context, whatsappService)
        }
    }

    // ── Step 1: Categorias ─────────────────────────────────────────

    private fun showCategories(context: CommandContext, whatsappService: WhatsappService) {
        val isAdmin = adminService.isAdmin(context.from)

        val visibleCategories = if (isAdmin) {
            queryTypeRegistry.categories.toList()
        } else {
            queryTypeRegistry.categories.filter { (catKey, _) ->
                queryTypeRegistry.getTypesForCategory(catKey).any { pricingService.isModuleEnabled(it.key) }
            }.toList()
        }

        if (visibleCategories.isEmpty()) {
            whatsappService.sendMessage(
                context.from,
                "Nenhum módulo de consulta disponível no momento.\nTente novamente mais tarde."
            )
            return
        }

        whatsappService.sendList(
            to = context.from,
            header = "Consulta Veicular",
            body = buildString {
                append("Bem-vindo ao *Painel de Consultas Veiculares*\n\n")
                append("Selecione uma categoria para ver os módulos disponíveis.")
            },
            buttonLabel = "Ver Categorias",
            footer = "ND Consultas | Veicular",
            sections = listOf(
                ListSection(
                    title = "Categorias",
                    rows = visibleCategories.map { (key, cat) ->
                        val activeCount = if (isAdmin) cat.count
                            else queryTypeRegistry.getTypesForCategory(key).count { pricingService.isModuleEnabled(it.key) }
                        ListRow(
                            id = "/consultar cat $key",
                            title = cat.label,
                            description = "${cat.description} ($activeCount módulos)"
                        )
                    }
                )
            )
        )
    }

    // ── Step 2: Tipos da categoria ─────────────────────────────────

    private fun showCategoryTypes(context: CommandContext, whatsappService: WhatsappService) {
        val catKey = context.args[1]
        val category = queryTypeRegistry.categories[catKey]

        if (category == null) {
            whatsappService.sendMessage(
                context.from,
                "Categoria inválida.\nUse /consultar para ver as categorias."
            )
            return
        }

        val allTypes = queryTypeRegistry.getTypesForCategory(catKey)
        val isAdmin = adminService.isAdmin(context.from)

        val types = if (isAdmin) allTypes
            else allTypes.filter { pricingService.isModuleEnabled(it.key) }

        if (types.isEmpty()) {
            whatsappService.sendMessage(
                context.from,
                "Nenhum módulo disponível nesta categoria no momento.\nTente outra categoria."
            )
            return
        }

        whatsappService.sendList(
            to = context.from,
            header = category.label,
            body = "Selecione o tipo de consulta que deseja realizar.",
            buttonLabel = "Ver Módulos",
            footer = "ND Consultas | ${category.label}",
            sections = listOf(
                ListSection(
                    title = category.label,
                    rows = types.map { (tipo, info) ->
                        val price = pricingService.getPrice(tipo)
                        val priceText = when {
                            isAdmin -> "Grátis (Admin)"
                            price > BigDecimal.ZERO -> "R\$ ${"%.2f".format(price)}"
                            else -> "Grátis"
                        }
                        ListRow(
                            id = "/consultar $tipo",
                            title = info.label,
                            description = "$priceText | ${info.inputPrompt.replace("*", "")}"
                        )
                    }
                )
            )
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

        sessionManager.setPending(context.from, tipo, info.label)

        val isAdmin = adminService.isAdmin(context.from)
        val price = pricingService.getPrice(tipo)
        val priceText = when {
            isAdmin -> ""
            price > BigDecimal.ZERO -> "\nValor: *R\$ ${"%.2f".format(price)}*"
            else -> ""
        }

        whatsappService.sendMessage(
            context.from,
            "*${info.label}*$priceText\n\n${info.inputPrompt}:"
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

        sessionManager.removePending(context.from)

        val price = pricingService.getPrice(tipo)
        val isAdmin = adminService.isAdmin(context.from)

        if (price > BigDecimal.ZERO && !isAdmin) {
            val paymentSession = paymentSessionManager.getSession(context.from)
            if (paymentSession != null && paymentSession.status == PaymentSessionManager.PaymentStatus.PAID) {
                paymentSessionManager.consume(context.from)
            } else {
                startPaymentFlow(context, whatsappService, tipo, info.label, query, price)
                return
            }
        }

        performQuery(context, whatsappService, tipo, info, query)
    }

    // ── Fluxo de pagamento ─────────────────────────────────────────

    private fun startPaymentFlow(
        context: CommandContext,
        whatsappService: WhatsappService,
        tipo: String,
        tipoLabel: String,
        query: String,
        price: BigDecimal
    ) {
        val hasCustomer = paymentService.getCustomerId(context.from) != null

        paymentSessionManager.create(
            userPhone = context.from,
            tipo = tipo,
            tipoLabel = tipoLabel,
            query = query,
            price = price,
            needsCpf = !hasCustomer
        )

        if (hasCustomer) {
            createAndSendPayment(context, whatsappService)
        } else {
            whatsappService.sendMessage(
                context.from,
                "*Pagamento Necessário*\n\nConsulta: *$tipoLabel*\nValor: *R\$ ${"%.2f".format(price)}*\n\nPara prosseguir, informe seu *CPF*:"
            )
        }
    }

    private fun handleCpfInput(context: CommandContext, whatsappService: WhatsappService) {
        val session = paymentSessionManager.getSession(context.from)
        if (session == null || session.status != PaymentSessionManager.PaymentStatus.AWAITING_CPF) {
            whatsappService.sendMessage(context.from, "Sessão expirada. Inicie uma nova consulta.")
            return
        }

        val cpf = context.args.drop(1).joinToString("").replace(Regex("[^0-9]"), "")
        if (cpf.length != 11 && cpf.length != 14) {
            whatsappService.sendMessage(context.from, "CPF/CNPJ inválido. Informe apenas os números:")
            return
        }

        whatsappService.sendMessage(context.from, "Processando...")

        val customerId = paymentService.createCustomer(context.from, cpf, context.senderName)
        if (customerId == null) {
            whatsappService.sendMessage(context.from, "Erro ao processar seus dados. Verifique o CPF e tente novamente.")
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar cancelar_pgto", title = "Cancelar"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        createAndSendPayment(context, whatsappService)
    }

    private fun createAndSendPayment(context: CommandContext, whatsappService: WhatsappService) {
        val session = paymentSessionManager.getSession(context.from) ?: return

        val result = paymentService.createPayment(
            userPhone = context.from,
            amount = session.price,
            description = "ND Consultas - ${session.tipoLabel}"
        )

        if (!result.success) {
            whatsappService.sendMessage(context.from, result.error ?: "Erro ao criar pagamento.")
            whatsappService.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/consultar cancelar_pgto", title = "Cancelar"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*Pagamento*\n\n")
                append("Consulta: *${session.tipoLabel}*\n")
                append("Valor: *R\$ ${"%.2f".format(session.price)}*\n\n")
                append("Clique no link abaixo para pagar via *PIX* ou *cartão de crédito*:\n\n")
                append(result.invoiceUrl)
            }
        )

        whatsappService.sendButtons(
            to = context.from,
            body = "Após o pagamento, sua consulta será processada automaticamente.",
            buttons = listOf(
                Button(id = "/consultar cancelar_pgto", title = "Cancelar Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Executar a partir de pagamento confirmado (webhook) ────────

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
        performQuery(context, whatsappService, session.tipo, info, session.query)
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
            return
        }

        if (context.messageId.isNotBlank()) {
            try {
                whatsappService.sendReaction(context.from, context.messageId, "\u2705")
            } catch (e: Exception) {
                log.warn("Falha ao enviar reaction de sucesso: {}", e.message)
            }
        }

        val pdfSent = trySendPdf(context, whatsappService, info, tipo, query, result.data)

        if (!pdfSent) {
            log.warn("Fallback para envio de texto — PDF falhou para {} query={}", tipo, query)
            sendResultAsText(context, whatsappService, info, query, result.data)
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

    // ── Envio de PDF ───────────────────────────────────────────────

    private fun trySendPdf(
        context: CommandContext,
        whatsappService: WhatsappService,
        info: QueryTypeRegistry.QueryTypeInfo,
        tipo: String,
        query: String,
        data: Map<String, Any?>
    ): Boolean {
        return try {
            val pdfBytes = pdfService.generate(info.label, query, data)
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
                    if (value.isNotEmpty() && value.first() is Map<*, *>) {
                        value.forEachIndexed { index, item ->
                            if (item is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                result.addAll(flattenMap(item as Map<String, Any?>, "$fullKey [${index + 1}]"))
                            }
                        }
                    } else {
                        result.add(fullKey to value.filterNotNull().joinToString(", "))
                    }
                }
                null -> result.add(fullKey to "-")
                else -> result.add(fullKey to value.toString())
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
