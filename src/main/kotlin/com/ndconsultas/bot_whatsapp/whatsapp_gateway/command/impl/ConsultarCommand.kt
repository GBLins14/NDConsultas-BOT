package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.impl

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.BotCommand
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.command.CommandContext
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.Button
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListRow
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.model.ListSection
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.ConsultationSessionManager
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.VehicleConsultationService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.springframework.stereotype.Component

@Component
class ConsultarCommand(
    private val consultationService: VehicleConsultationService,
    private val sessionManager: ConsultationSessionManager
) : BotCommand {

    override val name = "/consultar"
    override val description = "Painel de consulta veicular"
    override val aliases = listOf("/consulta", "/c")

    data class QueryTypeInfo(
        val label: String,
        val inputPrompt: String,
        val price: String
    )

    companion object {
        val QUERY_TYPES = mapOf(
            // Consulta por Placa
            "placa_full" to QueryTypeInfo("Placa Full", "Informe a *placa* do veiculo", "R\$ 0,10"),
            "placa_duality" to QueryTypeInfo("Placa Duality", "Informe a *placa* do veiculo", "R\$ 0,20"),
            "placa_serpro" to QueryTypeInfo("Placa SERPRO", "Informe a *placa* do veiculo", "R\$ 0,20"),
            "placa_senatran" to QueryTypeInfo("Placa SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60"),
            "bin_placa" to QueryTypeInfo("BIN Placa", "Informe a *placa* do veiculo", "R\$ 0,10"),
            "frota" to QueryTypeInfo("Frota Veicular", "Informe o *CPF ou CNPJ* do proprietario", "R\$ 0,20"),
            // Chassi e Motor
            "bin_chassi" to QueryTypeInfo("BIN Chassi", "Informe o *numero do chassi*", "R\$ 0,10"),
            "chassi_serpro" to QueryTypeInfo("Chassi SERPRO", "Informe o *numero do chassi*", "R\$ 0,30"),
            "chassi_senatran" to QueryTypeInfo("Chassi SENATRAN", "Informe o *numero do chassi*", "R\$ 0,60"),
            "bin_motor" to QueryTypeInfo("BIN Motor", "Informe o *numero do motor*", "R\$ 0,10"),
            "motor_senatran" to QueryTypeInfo("Motor SENATRAN", "Informe o *numero do motor*", "R\$ 0,60"),
            // Renavam e CNH
            "bin_renavam" to QueryTypeInfo("BIN Renavam", "Informe o *numero do RENAVAM*", "R\$ 0,10"),
            "renavam_serpro" to QueryTypeInfo("Renavam SERPRO", "Informe o *numero do RENAVAM*", "R\$ 0,20"),
            "cnh_full" to QueryTypeInfo("CNH Full", "Informe o *CPF* do condutor", "R\$ 0,20"),
            "cnh_serpro" to QueryTypeInfo("CNH SERPRO", "Informe o *CPF* do condutor", "R\$ 0,30"),
            // Laudos Veiculares
            "laudo_veicular" to QueryTypeInfo("Laudo Veicular", "Informe a *placa* do veiculo", "R\$ 0,25"),
            "laudo_veicular_historico" to QueryTypeInfo("Laudo Historico", "Informe a *placa* do veiculo", "R\$ 0,01"),
            "laudo_veicular_id" to QueryTypeInfo("Laudo por ID", "Informe o *ID do laudo*", "R\$ 0,07"),
            // SENATRAN Avancado
            "multas_senatran" to QueryTypeInfo("Multas SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60"),
            "ocorrencias_senatran" to QueryTypeInfo("Ocorrencias SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60"),
            "recall_senatran" to QueryTypeInfo("Recall SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60"),
            "renajud_senatran" to QueryTypeInfo("Renajud SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60")
        )
    }

    override fun execute(context: CommandContext, whatsappService: WhatsappService) {
        when {
            context.args.isEmpty() -> showConsultationPanel(context, whatsappService)
            context.args.size == 1 -> promptForData(context, whatsappService)
            else -> executeQuery(context, whatsappService)
        }
    }

    // ── Step 1: Painel de Consulta ──────────────────────────────────

    private fun showConsultationPanel(context: CommandContext, whatsappService: WhatsappService) {
        whatsappService.sendList(
            to = context.from,
            header = "Consulta Veicular",
            body = buildString {
                appendLine("Bem-vindo ao *Painel de Consultas Veiculares*")
                appendLine()
                appendLine("Selecione abaixo o tipo de consulta que deseja realizar.")
                appendLine()
                appendLine("Temos *22 modulos* disponiveis organizados por categoria.")
            },
            buttonLabel = "Ver Consultas",
            footer = "ND Consultas | Veicular",
            sections = buildSections()
        )
    }

    // ── Step 2: Solicitar dado ──────────────────────────────────────

    private fun promptForData(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args[0]
        val info = QUERY_TYPES[tipo]

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
            buildString {
                appendLine("*${info.label}*")
                appendLine("Custo: ${info.price}")
                appendLine()
                appendLine("${info.inputPrompt}:")
            }
        )
    }

    // ── Step 3: Executar consulta ───────────────────────────────────

    private fun executeQuery(context: CommandContext, whatsappService: WhatsappService) {
        val tipo = context.args[0]
        val query = context.args.drop(1).joinToString(" ").trim()
        val info = QUERY_TYPES[tipo]

        if (info == null) {
            whatsappService.sendMessage(
                context.from,
                "Tipo de consulta invalido.\nUse /consultar para ver as opcoes disponiveis."
            )
            return
        }

        sessionManager.removePending(context.from)

        whatsappService.sendReaction(context.from, context.messageId, "\u23F3")

        whatsappService.sendMessage(
            context.from,
            "Consultando *${info.label}*...\nAguarde um momento."
        )

        val result = consultationService.consultar(tipo, query)

        whatsappService.removeReaction(context.from, context.messageId)

        if (!result.success) {
            whatsappService.sendReaction(context.from, context.messageId, "\u274C")
            whatsappService.sendMessage(
                context.from,
                buildString {
                    appendLine("*Erro na consulta*")
                    appendLine()
                    appendLine("Tipo: ${info.label}")
                    appendLine("Dado: $query")
                    appendLine()
                    appendLine(result.error ?: "Erro desconhecido.")
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

        whatsappService.sendReaction(context.from, context.messageId, "\u2705")

        val formattedData = formatResult(result.data)
        val messages = splitMessage(formattedData, 3500)

        // Header message
        whatsappService.sendMessage(
            context.from,
            buildString {
                appendLine("*${info.label}*")
                appendLine("Dado consultado: `$query`")
                if (result.custo != null || result.saldoRestante != null) {
                    appendLine()
                    if (result.custo != null) appendLine("Custo: ${result.custo}")
                    if (result.saldoRestante != null) appendLine("Saldo restante: ${result.saldoRestante}")
                }
            }
        )

        // Data messages (split if too long)
        messages.forEach { msg ->
            whatsappService.sendMessage(context.from, msg)
        }

        // Follow-up buttons
        whatsappService.sendButtons(
            to = context.from,
            body = "Deseja realizar outra consulta?",
            buttons = listOf(
                Button(id = "/consultar", title = "Nova Consulta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    // ── Formatacao de resultado ─────────────────────────────────────

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

    // ── Construcao do menu interativo ───────────────────────────────

    private fun buildSections(): List<ListSection> = listOf(
        ListSection(
            title = "Consulta por Placa",
            rows = listOf(
                ListRow("/consultar placa_full", "Placa Full", "Consulta completa | R\$ 0,10"),
                ListRow("/consultar placa_duality", "Placa Duality", "Base Duality | R\$ 0,20"),
                ListRow("/consultar placa_serpro", "Placa SERPRO", "Base SERPRO | R\$ 0,20"),
                ListRow("/consultar placa_senatran", "Placa SENATRAN", "Base SENATRAN | R\$ 0,60"),
                ListRow("/consultar bin_placa", "BIN Placa", "Base Nacional | R\$ 0,10"),
                ListRow("/consultar frota", "Frota Veicular", "Por CPF/CNPJ | R\$ 0,20")
            )
        ),
        ListSection(
            title = "Chassi e Motor",
            rows = listOf(
                ListRow("/consultar bin_chassi", "BIN Chassi", "Base Nacional | R\$ 0,10"),
                ListRow("/consultar chassi_serpro", "Chassi SERPRO", "Base SERPRO | R\$ 0,30"),
                ListRow("/consultar chassi_senatran", "Chassi SENATRAN", "Base SENATRAN | R\$ 0,60"),
                ListRow("/consultar bin_motor", "BIN Motor", "Base Nacional | R\$ 0,10"),
                ListRow("/consultar motor_senatran", "Motor SENATRAN", "Base SENATRAN | R\$ 0,60")
            )
        ),
        ListSection(
            title = "Renavam e CNH",
            rows = listOf(
                ListRow("/consultar bin_renavam", "BIN Renavam", "Base Nacional | R\$ 0,10"),
                ListRow("/consultar renavam_serpro", "Renavam SERPRO", "Base SERPRO | R\$ 0,20"),
                ListRow("/consultar cnh_full", "CNH Full", "Por CPF | R\$ 0,20"),
                ListRow("/consultar cnh_serpro", "CNH SERPRO", "Por CPF | R\$ 0,30")
            )
        ),
        ListSection(
            title = "Laudos Veiculares",
            rows = listOf(
                ListRow("/consultar laudo_veicular", "Laudo Veicular", "Laudo completo | R\$ 0,25"),
                ListRow("/consultar laudo_veicular_historico", "Laudo Historico", "Historico de laudos | R\$ 0,01"),
                ListRow("/consultar laudo_veicular_id", "Laudo por ID", "Consulta por ID | R\$ 0,07")
            )
        ),
        ListSection(
            title = "SENATRAN Avancado",
            rows = listOf(
                ListRow("/consultar multas_senatran", "Multas", "Consulta de multas | R\$ 0,60"),
                ListRow("/consultar ocorrencias_senatran", "Ocorrencias", "Registros policiais | R\$ 0,60"),
                ListRow("/consultar recall_senatran", "Recall", "Recalls do veiculo | R\$ 0,60"),
                ListRow("/consultar renajud_senatran", "Renajud", "Restricoes judiciais | R\$ 0,60")
            )
        )
    )
}
