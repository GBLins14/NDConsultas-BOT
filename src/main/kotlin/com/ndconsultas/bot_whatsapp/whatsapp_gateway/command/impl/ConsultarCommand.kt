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
        val price: String,
        val category: String
    )

    data class CategoryInfo(
        val label: String,
        val description: String,
        val count: Int
    )

    companion object {
        val QUERY_TYPES = mapOf(
            // Consulta por Placa
            "placa_full" to QueryTypeInfo("Placa Full", "Informe a *placa* do veiculo", "R\$ 0,10", "placa"),
            "placa_duality" to QueryTypeInfo("Placa Duality", "Informe a *placa* do veiculo", "R\$ 0,20", "placa"),
            "placa_serpro" to QueryTypeInfo("Placa SERPRO", "Informe a *placa* do veiculo", "R\$ 0,20", "placa"),
            "placa_senatran" to QueryTypeInfo("Placa SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60", "placa"),
            "bin_placa" to QueryTypeInfo("BIN Placa", "Informe a *placa* do veiculo", "R\$ 0,10", "placa"),
            "frota" to QueryTypeInfo("Frota Veicular", "Informe o *CPF ou CNPJ* do proprietario", "R\$ 0,20", "placa"),
            // Chassi e Motor
            "bin_chassi" to QueryTypeInfo("BIN Chassi", "Informe o *numero do chassi*", "R\$ 0,10", "chassi"),
            "chassi_serpro" to QueryTypeInfo("Chassi SERPRO", "Informe o *numero do chassi*", "R\$ 0,30", "chassi"),
            "chassi_senatran" to QueryTypeInfo("Chassi SENATRAN", "Informe o *numero do chassi*", "R\$ 0,60", "chassi"),
            "bin_motor" to QueryTypeInfo("BIN Motor", "Informe o *numero do motor*", "R\$ 0,10", "chassi"),
            "motor_senatran" to QueryTypeInfo("Motor SENATRAN", "Informe o *numero do motor*", "R\$ 0,60", "chassi"),
            // Renavam e CNH
            "bin_renavam" to QueryTypeInfo("BIN Renavam", "Informe o *numero do RENAVAM*", "R\$ 0,10", "renavam"),
            "renavam_serpro" to QueryTypeInfo("Renavam SERPRO", "Informe o *numero do RENAVAM*", "R\$ 0,20", "renavam"),
            "cnh_full" to QueryTypeInfo("CNH Full", "Informe o *CPF* do condutor", "R\$ 0,20", "renavam"),
            "cnh_serpro" to QueryTypeInfo("CNH SERPRO", "Informe o *CPF* do condutor", "R\$ 0,30", "renavam"),
            // Laudos Veiculares
            "laudo_veicular" to QueryTypeInfo("Laudo Veicular", "Informe a *placa* do veiculo", "R\$ 0,25", "laudo"),
            "laudo_veicular_historico" to QueryTypeInfo("Laudo Historico", "Informe a *placa* do veiculo", "R\$ 0,01", "laudo"),
            "laudo_veicular_id" to QueryTypeInfo("Laudo por ID", "Informe o *ID do laudo*", "R\$ 0,07", "laudo"),
            // SENATRAN Avancado
            "multas_senatran" to QueryTypeInfo("Multas SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60", "senatran"),
            "ocorrencias_senatran" to QueryTypeInfo("Ocorrencias SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60", "senatran"),
            "recall_senatran" to QueryTypeInfo("Recall SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60", "senatran"),
            "renajud_senatran" to QueryTypeInfo("Renajud SENATRAN", "Informe a *placa* do veiculo", "R\$ 0,60", "senatran")
        )

        val CATEGORIES = linkedMapOf(
            "placa" to CategoryInfo("Consulta por Placa", "Placa, BIN e Frota", 6),
            "chassi" to CategoryInfo("Chassi e Motor", "Chassi e Motor", 5),
            "renavam" to CategoryInfo("Renavam e CNH", "Renavam e CNH", 4),
            "laudo" to CategoryInfo("Laudos Veiculares", "Laudos e Historico", 3),
            "senatran" to CategoryInfo("SENATRAN Avancado", "Multas, Recall e mais", 4)
        )
    }

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
                append("Selecione uma categoria para ver os modulos disponiveis.\n\n")
                append("*22 modulos* organizados em *5 categorias*.")
            },
            buttonLabel = "Ver Categorias",
            footer = "ND Consultas | Veicular",
            sections = listOf(
                ListSection(
                    title = "Categorias",
                    rows = CATEGORIES.map { (key, cat) ->
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
        val category = CATEGORIES[catKey]

        if (category == null) {
            whatsappService.sendMessage(
                context.from,
                "Categoria invalida.\nUse /consultar para ver as categorias."
            )
            return
        }

        val types = QUERY_TYPES.filter { it.value.category == catKey }

        whatsappService.sendList(
            to = context.from,
            header = category.label,
            body = "Selecione o tipo de consulta que deseja realizar.\nCada modulo possui um custo indicado na descricao.",
            buttonLabel = "Ver Modulos",
            footer = "ND Consultas | ${category.label}",
            sections = listOf(
                ListSection(
                    title = category.label,
                    rows = types.map { (tipo, info) ->
                        ListRow(
                            id = "/consultar $tipo",
                            title = info.label,
                            description = "${info.inputPrompt.replace("*", "")} | ${info.price}"
                        )
                    }
                )
            )
        )
    }

    // ── Step 3: Solicitar dado ──────────────────────────────────────

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
                append("*${info.label}*\n")
                append("Custo: ${info.price}\n\n")
                append("${info.inputPrompt}:")
            }
        )
    }

    // ── Step 4: Executar consulta ───────────────────────────────────

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
                    Button(id = "/consultar cat ${info.category}", title = "Tentar Novamente"),
                    Button(id = "/consultar", title = "Outra Consulta"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        whatsappService.sendReaction(context.from, context.messageId, "\u2705")

        val formattedData = formatResult(result.data)
        val messages = splitMessage(formattedData, 3500)

        // Header
        whatsappService.sendMessage(
            context.from,
            buildString {
                append("*${info.label}*\n")
                append("Dado consultado: $query\n")
                if (result.custo != null || result.saldoRestante != null) {
                    append("\n")
                    if (result.custo != null) append("Custo: ${result.custo}\n")
                    if (result.saldoRestante != null) append("Saldo restante: ${result.saldoRestante}\n")
                }
            }
        )

        // Data
        messages.forEach { msg ->
            whatsappService.sendMessage(context.from, msg)
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
}
