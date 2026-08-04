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
            context.args[0] == "cancelar_pgto" -> cancelPayment(context, whatsappService)
            context.args[0] == "pago" -> executeFromPayment(context, whatsappService)
            context.args[0] == "pgto_pix" -> handlePixPayment(context, whatsappService)
            context.args[0] == "pgto_cartao" -> startCardPayment(context, whatsappService)
            context.args[0] == "cartao_input" -> handleCardInput(context, whatsappService)
            context.args.size == 1 -> promptForData(context, whatsappService)
            else -> executeQuery(context, whatsappService)
        }
    }

    // ── Step 1: Listar módulos agrupados por categoria ───────────────

    private fun showCategories(context: CommandContext, whatsappService: WhatsappService) {
        val isAdmin = adminService.isAdmin(context.from)

        val sections = queryTypeRegistry.categories.mapNotNull { (catKey, catInfo) ->
            val allTypes = queryTypeRegistry.getTypesForCategory(catKey)
            val types = if (isAdmin) allTypes
                else allTypes.filter { pricingService.isModuleEnabled(it.key) }

            if (types.isEmpty()) return@mapNotNull null

            ListSection(
                title = catInfo.label,
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
                        description = "Escolha para saber mais."
                    )
                }
            )
        }

        if (sections.isEmpty()) {
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
                append("Selecione o tipo de consulta que deseja realizar.")
            },
            buttonLabel = "Ver Consultas",
            footer = "ND Consultas",
            sections = sections
        )
    }

    // ── Step 2 (legado): Redireciona para listagem direta ──────────

    private fun showCategoryTypes(context: CommandContext, whatsappService: WhatsappService) {
        showCategories(context, whatsappService)
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
            "*${info.label}*$priceText\n\n${info.description}\n\n*Retorna:* ${info.returnDetails}\n\n${info.inputPrompt}:"
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

        performQuery(context, whatsappService, tipo, info, query)
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
            performQuery(context, whatsappService, session.tipo, info, session.query)
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
