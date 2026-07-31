package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PdfReportService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.VehicleConsultationService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ConsultarCommand(
    private val consultationService: VehicleConsultationService,
    private val sessionManager: ConsultationSessionManager,
    private val pdfService: PdfReportService
) : BotCommand {

    override val name = "/consultar"
    override val description = "Painel de consulta veicular"
    override val aliases = listOf("/consulta", "/c")

    companion object {
        private val log = LoggerFactory.getLogger(ConsultarCommand::class.java)
        private val FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    data class QueryTypeInfo(
        val label: String,
        val inputPrompt: String,
        val category: String
    )

    data class CategoryInfo(
        val label: String,
        val description: String,
        val count: Int
    )

    private val queryTypes = mapOf(
        // Consulta por Placa
        "placa_full" to QueryTypeInfo("Placa Full", "Informe a *placa* do veiculo", "placa"),
        "placa_duality" to QueryTypeInfo("Placa Duality", "Informe a *placa* do veiculo", "placa"),
        "placa_serpro" to QueryTypeInfo("Placa SERPRO", "Informe a *placa* do veiculo", "placa"),
        "placa_senatran" to QueryTypeInfo("Placa SENATRAN", "Informe a *placa* do veiculo", "placa"),
        "bin_placa" to QueryTypeInfo("BIN Placa", "Informe a *placa* do veiculo", "placa"),
        "frota" to QueryTypeInfo("Frota Veicular", "Informe o *CPF ou CNPJ* do proprietario", "placa"),
        // Chassi e Motor
        "bin_chassi" to QueryTypeInfo("BIN Chassi", "Informe o *numero do chassi*", "chassi"),
        "chassi_serpro" to QueryTypeInfo("Chassi SERPRO", "Informe o *numero do chassi*", "chassi"),
        "chassi_senatran" to QueryTypeInfo("Chassi SENATRAN", "Informe o *numero do chassi*", "chassi"),
        "bin_motor" to QueryTypeInfo("BIN Motor", "Informe o *numero do motor*", "chassi"),
        "motor_senatran" to QueryTypeInfo("Motor SENATRAN", "Informe o *numero do motor*", "chassi"),
        // Renavam e CNH
        "bin_renavam" to QueryTypeInfo("BIN Renavam", "Informe o *numero do RENAVAM*", "renavam"),
        "renavam_serpro" to QueryTypeInfo("Renavam SERPRO", "Informe o *numero do RENAVAM*", "renavam"),
        "cnh_full" to QueryTypeInfo("CNH Full", "Informe o *CPF* do condutor", "renavam"),
        "cnh_serpro" to QueryTypeInfo("CNH SERPRO", "Informe o *CPF* do condutor", "renavam"),
        // Laudos Veiculares
        "laudo_veicular" to QueryTypeInfo("Laudo Veicular", "Informe a *placa* do veiculo", "laudo"),
        "laudo_veicular_id" to QueryTypeInfo("Laudo por ID", "Informe o *ID do laudo*", "laudo"),
        // SENATRAN Avancado
        "multas_senatran" to QueryTypeInfo("Multas SENATRAN", "Informe a *placa* do veiculo", "senatran"),
        "ocorrencias_senatran" to QueryTypeInfo("Ocorrencias SENATRAN", "Informe a *placa* do veiculo", "senatran"),
        "recall_senatran" to QueryTypeInfo("Recall SENATRAN", "Informe a *placa* do veiculo", "senatran"),
        "renajud_senatran" to QueryTypeInfo("Renajud SENATRAN", "Informe a *placa* do veiculo", "senatran")
    )

    private val categories = linkedMapOf(
        "placa" to CategoryInfo("Consulta por Placa", "Placa, BIN e Frota", 6),
        "chassi" to CategoryInfo("Chassi e Motor", "Chassi e Motor", 5),
        "renavam" to CategoryInfo("Renavam e CNH", "Renavam e CNH", 4),
        "laudo" to CategoryInfo("Laudos Veiculares", "Laudo e Laudo por ID", 2),
        "senatran" to CategoryInfo("SENATRAN Avancado", "Multas, Recall e mais", 4)
    )

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        when {
            context.args.isEmpty() -> showCategories(context, whatsappService)
            context.args[0] == "cat" && context.args.size >= 2 -> showCategoryTypes(context, whatsappService)
            context.args.size == 1 -> promptForData(context, whatsappService)
            else -> executeQuery(context, whatsappService)
        }
    }

    // ── Step 1: Categorias ─────────────────────────────────────────

    private fun showCategories(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendList(
            to = context.from,
            header = "Consulta Veicular",
            body = buildString {
                append("Bem-vindo ao *Painel de Consultas Veiculares*\n\n")
                append("Selecione uma categoria para ver os modulos disponiveis.")
            },
            buttonLabel = "Ver Categorias",
            footer = "ND Consultas | Veicular",
            sections = listOf(
                ListSection(
                    title = "Categorias",
                    rows = categories.map { (key, cat) ->
                        ListRow(
                            id = "/consultar cat $key",
                            title = cat.label,
                            description = "${cat.description} (${cat.count} modulos)"
                        )
                    }
                )
            )
        )
    }

    // ── Step 2: Tipos da categoria ─────────────────────────────────

    private fun showCategoryTypes(context: CommandContext, whatsappService: WhatsappService) {
        val catKey = context.args[1]
        val category = categories[catKey]

        if (category == null) {
            whatsappService.sendMessage(
                context.from,
                "Categoria invalida.\nUse /consultar para ver as categorias."
            )
            return
        }

        val types = queryTypes.filter { it.value.category == catKey }

        whatsappService.sendList(
            to = context.from,
            header = category.label,
            body = "Selecione o tipo de consulta que deseja realizar.",
            buttonLabel = "Ver Modulos",
            footer = "ND Consultas | ${category.label}",
            sections = listOf(
                ListSection(
                    title = category.label,
                    rows = types.map { (tipo, info) ->
                        ListRow(
                            id = "/consultar $tipo",
                            title = info.label,
                            description = info.inputPrompt.replace("*", "")
                        )
                    }
                )
            )
        )
    }

    // ── Step 3: Solicitar dado ──────────────────────────────────────

    private fun promptForData(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args[0]
        val info = queryTypes[tipo]

        if (info == null) {
            whatsappService.sendMessage(
                context.from,
                "Tipo de consulta invalido.\nUse /consultar para ver as opcoes disponiveis."
            )
            return
        }

        sessionManager.setPending(context.from, tipo, info.label)

        whatsappService.sendMessage(
            context.from,
            "*${info.label}*\n\n${info.inputPrompt}:"
        )
    }

    // ── Step 4: Executar consulta ───────────────────────────────────

    private fun executeQuery(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args[0]
        val query = context.args.drop(1).joinToString(" ").trim()
        val info = queryTypes[tipo]

        if (info == null) {
            whatsappService.sendMessage(
                context.from,
                "Tipo de consulta invalido.\nUse /consultar para ver as opcoes disponiveis."
            )
            return
        }

        sessionManager.removePending(context.from)

        // Reaction de "processando"
        try {
            whatsappService.sendReaction(context.from, context.messageId, "\u23F3")
        } catch (e: Exception) {
            log.warn("Falha ao enviar reaction de processamento: {}", e.message)
        }

        whatsappService.sendMessage(
            context.from,
            "Consultando *${info.label}*...\nAguarde um momento."
        )

        // Consultar API
        val result = consultationService.consultar(tipo, query)

        // Remover reaction de "processando"
        try {
            whatsappService.removeReaction(context.from, context.messageId)
        } catch (e: Exception) {
            log.warn("Falha ao remover reaction: {}", e.message)
        }

        // Erro na consulta
        if (!result.success) {
            try {
                whatsappService.sendReaction(context.from, context.messageId, "\u274C")
            } catch (e: Exception) {
                log.warn("Falha ao enviar reaction de erro: {}", e.message)
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

        // Consulta OK — reagir com check
        try {
            whatsappService.sendReaction(context.from, context.messageId, "\u2705")
        } catch (e: Exception) {
            log.warn("Falha ao enviar reaction de sucesso: {}", e.message)
        }

        // Tentar gerar PDF e enviar como documento
        val pdfSent = trySendPdf(context, whatsappService, info, tipo, query, result.data)

        // Fallback: se o PDF falhar, envia como texto
        if (!pdfSent) {
            log.warn("Fallback para envio de texto — PDF falhou para {} query={}", tipo, query)
            sendResultAsText(context, whatsappService, info, query, result.data)
        }

        // Follow-up
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
        info: QueryTypeInfo,
        tipo: String,
        query: String,
        data: Map<String, Any?>
    ): Boolean {
        return try {
            // Gerar PDF
            val pdfBytes = pdfService.generate(info.label, query, data)
            val timestamp = LocalDateTime.now().format(FILE_DATE_FMT)
            val filename = "consulta_${tipo}_${timestamp}.pdf"

            // Upload para WhatsApp Media API
            val mediaId = whatsappService.uploadMedia(pdfBytes, "application/pdf", filename)

            // Enviar documento
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
        info: QueryTypeInfo,
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

    // ── Formatacao de resultado (fallback texto) ───────────────────

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
