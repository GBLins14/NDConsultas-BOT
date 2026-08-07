package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.config.BotProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class PdfReportService(
    private val botProperties: BotProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(PdfReportService::class.java)

        // ── Paleta de cores ───────────────────────────────────────
        private val PRIMARY = Color(0, 82, 155)
        private val PRIMARY_DARK = Color(0, 60, 120)
        private val ACCENT = Color(0, 150, 136)
        private val DARK_TEXT = Color(33, 33, 33)
        private val GRAY_TEXT = Color(100, 100, 100)
        private val LIGHT_GRAY = Color(150, 150, 150)
        private val LIGHT_BG = Color(245, 247, 250)
        private val BORDER_COLOR = Color(210, 215, 220)

        private val GREEN = Color(34, 139, 34)
        private val GREEN_BG = Color(232, 245, 233)
        private val GREEN_BORDER = Color(129, 199, 132)
        private val RED = Color(198, 40, 40)
        private val RED_BG = Color(255, 235, 238)
        private val RED_BORDER = Color(239, 154, 154)
        private val YELLOW = Color(245, 166, 35)
        private val YELLOW_BG = Color(255, 248, 225)
        private val YELLOW_BORDER = Color(255, 224, 130)

        private val SECTION_BLUE = Color(227, 242, 253)
        private val SECTION_BLUE_BORDER = Color(100, 181, 246)

        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        private val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        private val baseFontBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        private fun font(size: Float, bold: Boolean = false, color: Color = DARK_TEXT): Font {
            return Font(if (bold) baseFontBold else baseFont, size, Font.NORMAL, color)
        }

        private const val MAX_REDIRECTS = 5

        private val NEGATIVE_KEYWORDS = listOf(
            "roubo", "furto", "sinistro", "bloqueio", "penhora", "impedimento",
            "restricao", "restri\u00e7\u00e3o", "existe ocorrencia", "ocorrencia de sinistro",
            "existe_ocorrencia", "indicio", "irregular", "cancelad"
        )

        // Valores que significam "limpo" / "sem problema" — usados para NÃO gerar alerta
        private val SAFE_VALUES = listOf(
            "sem restricao", "sem restricoes", "nenhuma", "nenhum", "nao consta",
            "nao existe", "nao ha", "nao possui", "sem ocorrencia", "sem ocorrencias",
            "sem comunicado", "sem intencao", "livre", "normal", "regular",
            "circulacao", "em circulacao", "0", "false", "nao", ""
        )
    }

    @Volatile private var cachedLogo: ByteArray? = null
    @Volatile private var logoLoadAttempted = false

    // ── Modelo de alerta por módulo ────────────────────────────────

    private enum class AlertStrategy {
        HAS_RECORDS,           // Alerta se existem registros (arrays não-vazios) nos dados
        FIELD_VALUE_NEGATIVE,  // Alerta se o valor do campo NÃO está na lista de valores "seguros"
        FIELD_HAS_CONTENT,     // Alerta se o campo tem conteúdo significativo (não vazio/nao/sem)
        EXISTE_FLAG,           // Alerta se campo existe_ocorrencia == "1"
        COUNT_FIELD,           // Alerta se campo numérico > 0
        INFO_ONLY              // Nunca alerta, apenas informativo
    }

    private data class AlertDef(
        val label: String,
        val strategy: AlertStrategy,
        val targetFields: List<String>,
        val positiveText: String,
        val negativeText: String
    )

    data class AlertInfo(
        val label: String,
        val isAlert: Boolean,
        val detail: String,
        val isIndeterminate: Boolean = false
    )

    private val moduleAlerts: Map<String, List<AlertDef>> = mapOf(
        "placa_serpro" to listOf(
            AlertDef("RESTRICOES", AlertStrategy.FIELD_VALUE_NEGATIVE, listOf("restricao", "restricoes", "restricao_1", "restricao_2", "restricao_3", "restricao_4"), "Sem restricoes", "Restricao encontrada"),
            AlertDef("SITUACAO", AlertStrategy.FIELD_VALUE_NEGATIVE, listOf("situacao", "situacao_veiculo"), "Veiculo regular", "Situacao irregular"),
            AlertDef("COMUNICADO DE VENDA", AlertStrategy.FIELD_HAS_CONTENT, listOf("comunicado_venda", "comunicado_de_venda"), "Sem comunicado", "Comunicado de venda registrado"),
            AlertDef("INTENCAO DE VENDA", AlertStrategy.FIELD_HAS_CONTENT, listOf("intencao_venda", "intencao_de_venda"), "Sem intencao", "Intencao de venda registrada")
        ),
        "bin_chassi" to listOf(
            AlertDef("SITUACAO", AlertStrategy.FIELD_VALUE_NEGATIVE, listOf("situacao", "situacao_veiculo"), "Veiculo regular", "Situacao irregular")
        ),
        "bin_motor" to listOf(
            AlertDef("SITUACAO", AlertStrategy.FIELD_VALUE_NEGATIVE, listOf("situacao", "situacao_veiculo"), "Veiculo regular", "Situacao irregular")
        ),
        "bin_renavam" to listOf(
            AlertDef("SITUACAO", AlertStrategy.FIELD_VALUE_NEGATIVE, listOf("situacao", "situacao_veiculo"), "Veiculo regular", "Situacao irregular")
        ),
        "multas_senatran" to listOf(
            AlertDef("MULTAS", AlertStrategy.HAS_RECORDS, emptyList(), "Sem multas registradas", "Multa(s) encontrada(s)")
        ),
        "ocorrencias_senatran" to listOf(
            AlertDef("ROUBO / FURTO", AlertStrategy.HAS_RECORDS, emptyList(), "Nenhuma ocorrencia", "Ocorrencia(s) encontrada(s)"),
        ),
        "renajud_senatran" to listOf(
            AlertDef("RESTRICOES JUDICIAIS", AlertStrategy.HAS_RECORDS, emptyList(), "Sem restricoes judiciais", "Restricao judicial encontrada")
        ),
        "leilao_completo_score" to listOf(
            AlertDef("SINISTRO", AlertStrategy.EXISTE_FLAG, listOf("existe_ocorrencia"), "Sem indicio de sinistro", "Indicio de sinistro encontrado"),
            AlertDef("LEILAO", AlertStrategy.COUNT_FIELD, listOf("quantidade_ocorrencias"), "Sem historico de leilao", "Historico de leilao encontrado"),
            AlertDef("SCORE", AlertStrategy.INFO_ONLY, listOf("pontuacao", "aceitacao", "descricao_pontuacao"), "", "")
        ),
        "cpf_full" to listOf(
            AlertDef("SITUACAO CADASTRAL", AlertStrategy.FIELD_VALUE_NEGATIVE, listOf("situacao_cadastral", "situacao"), "Cadastro regular", "Situacao irregular"),
            AlertDef("OBITO", AlertStrategy.FIELD_HAS_CONTENT, listOf("obito", "indicador_obito", "data_obito"), "Sem registro de obito", "Registro de obito encontrado")
        ),
        "credlink_telefone" to listOf(
            AlertDef("PORTABILIDADE", AlertStrategy.FIELD_HAS_CONTENT, listOf("portabilidade"), "Sem portabilidade", "Portabilidade registrada")
        )
    )

    // ── Geração principal ─────────────────────────────────────────

    fun generate(tipo: String, tipoLabel: String, query: String, data: Map<String, Any?>): ByteArray {
        val output = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(document, output)

        document.open()
        addHeader(document)
        addConsultationInfo(document, tipoLabel, query)

        val alertDefs = moduleAlerts[tipo]
        if (alertDefs != null && alertDefs.isNotEmpty()) {
            val alerts = evaluateAlerts(alertDefs, data)
            if (alerts.isNotEmpty()) {
                val panelLabel = when (tipo) {
                    "cpf_full" -> "Situacao Cadastral"
                    "telefone_full" -> "Informacoes da Linha"
                    else -> "Situacao do Veiculo"
                }
                addAlertPanel(document, alerts, panelLabel)
            }
        }

        addResultSections(document, data)
        addFooter(document)
        document.close()

        return output.toByteArray()
    }

    // ── Avaliação de alertas por estratégia ──────────────────────

    private fun evaluateAlerts(defs: List<AlertDef>, data: Map<String, Any?>): List<AlertInfo> {
        return defs.map { def ->
            when (def.strategy) {
                AlertStrategy.HAS_RECORDS -> evaluateHasRecords(def, data)
                AlertStrategy.FIELD_VALUE_NEGATIVE -> evaluateFieldValueNegative(def, data)
                AlertStrategy.FIELD_HAS_CONTENT -> evaluateFieldHasContent(def, data)
                AlertStrategy.EXISTE_FLAG -> evaluateExisteFlag(def, data)
                AlertStrategy.COUNT_FIELD -> evaluateCountField(def, data)
                AlertStrategy.INFO_ONLY -> evaluateInfoOnly(def, data)
            }
        }
    }

    // Alerta se existem arrays com registros (maps) nos dados
    private fun evaluateHasRecords(def: AlertDef, data: Map<String, Any?>): AlertInfo {
        val recordCount = countDataRecords(data)
        val isAlert = recordCount > 0
        val detail = if (isAlert) "$recordCount registro(s) encontrado(s)" else def.positiveText
        return AlertInfo(def.label, isAlert, detail)
    }

    // Conta quantos registros (items em arrays de maps) existem nos dados
    private fun countDataRecords(data: Map<String, Any?>): Int {
        var count = 0
        for ((_, value) in data) {
            when (value) {
                is List<*> -> {
                    val mapItems = value.filterIsInstance<Map<*, *>>()
                    if (mapItems.isNotEmpty()) {
                        count += mapItems.size
                    }
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    count += countDataRecords(value as Map<String, Any?>)
                }
            }
        }
        return count
    }

    // Alerta se o valor do campo NÃO é seguro (não está na lista SAFE_VALUES)
    private fun evaluateFieldValueNegative(def: AlertDef, data: Map<String, Any?>): AlertInfo {
        for (fieldName in def.targetFields) {
            val value = findRawValue(data, fieldName)
            if (value != null) {
                val valueLower = value.lowercase().trim()
                val isSafe = SAFE_VALUES.any { valueLower == it || valueLower.contains(it) }
                if (!isSafe && valueLower.isNotBlank()) {
                    if (isNullLikeValue(valueLower)) {
                        return AlertInfo(def.label, false, "Indefinido", isIndeterminate = true)
                    }
                    return AlertInfo(def.label, true, value)
                }
            }
        }
        return AlertInfo(def.label, false, def.positiveText)
    }

    // Alerta se o campo existe e tem conteúdo significativo
    private fun evaluateFieldHasContent(def: AlertDef, data: Map<String, Any?>): AlertInfo {
        for (fieldName in def.targetFields) {
            val value = findRawValue(data, fieldName)
            if (value != null) {
                val valueLower = value.lowercase().trim()
                val isEmpty = valueLower.isBlank() || SAFE_VALUES.any { valueLower == it }
                if (!isEmpty) {
                    // Valores null-like (ex: {ano=null, data=null}) → indefinido (amarelo)
                    if (isNullLikeValue(valueLower)) {
                        return AlertInfo(def.label, false, "Indefinido", isIndeterminate = true)
                    }
                    return AlertInfo(def.label, true, value)
                }
            }
        }
        return AlertInfo(def.label, false, def.positiveText)
    }

    private fun isNullLikeValue(value: String): Boolean {
        if (value == "null") return true
        // Detecta padrões como {ano=null, data=null} ou {key=null}
        if (value.startsWith("{") && value.endsWith("}")) {
            val inner = value.removeSurrounding("{", "}").trim()
            return inner.split(",").all { part ->
                val v = part.substringAfter("=", "").trim()
                v == "null" || v.isBlank()
            }
        }
        return false
    }

    // Alerta se existe_ocorrencia == "1"
    private fun evaluateExisteFlag(def: AlertDef, data: Map<String, Any?>): AlertInfo {
        for (fieldName in def.targetFields) {
            val value = findRawValue(data, fieldName)
            if (value != null) {
                val isAlert = value == "1" || value.equals("sim", ignoreCase = true) || value.equals("true", ignoreCase = true)
                if (isAlert) {
                    val descricao = findRawValue(data, "descricao_ocorrencia")
                    return AlertInfo(def.label, true, descricao ?: def.negativeText)
                }
            }
        }
        return AlertInfo(def.label, false, def.positiveText)
    }

    // Alerta se campo numérico > 0
    private fun evaluateCountField(def: AlertDef, data: Map<String, Any?>): AlertInfo {
        for (fieldName in def.targetFields) {
            val value = findRawValue(data, fieldName)
            if (value != null) {
                val num = value.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                if (num > 0) {
                    return AlertInfo(def.label, true, "$num ocorrencia(s)")
                }
            }
        }
        return AlertInfo(def.label, false, def.positiveText)
    }

    // Nunca alerta — apenas informativo (ex: Score)
    private fun evaluateInfoOnly(def: AlertDef, data: Map<String, Any?>): AlertInfo {
        val detail = buildString {
            val pontuacao = findRawValue(data, "pontuacao")
            val aceitacao = findRawValue(data, "aceitacao")
            val descricao = findRawValue(data, "descricao_pontuacao")
            if (pontuacao != null) append("Nota: $pontuacao")
            if (aceitacao != null) {
                if (isNotEmpty()) append(" | ")
                append("Aceitacao: $aceitacao%")
            }
            if (isEmpty() && descricao != null) append(descricao)
            if (isEmpty()) append("Informacao disponivel")
        }
        return AlertInfo(def.label, false, detail)
    }

    // ── Header com Logo ───────────────────────────────────────────

    private fun addHeader(document: Document) {
        val logoBytes = getLogoBytes()
        if (logoBytes != null) addHeaderWithLogo(document, logoBytes)
        else addHeaderWithoutLogo(document)
        document.add(Paragraph(" "))
    }

    private fun addHeaderWithLogo(document: Document, logoBytes: ByteArray) {
        val headerTable = PdfPTable(2)
        headerTable.widthPercentage = 100f
        headerTable.setWidths(floatArrayOf(15f, 85f))

        val logoCell = try {
            val img = Image.getInstance(logoBytes)
            img.scaleToFit(55f, 55f)
            val cell = PdfPCell(img, false)
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.verticalAlignment = Element.ALIGN_MIDDLE
            cell
        } catch (e: Exception) {
            log.warn("Falha ao processar logo para PDF: {}", e.message)
            PdfPCell(Phrase(""))
        }
        logoCell.backgroundColor = PRIMARY_DARK
        logoCell.border = Rectangle.NO_BORDER
        logoCell.setPadding(12f)
        logoCell.paddingLeft = 16f
        headerTable.addCell(logoCell)

        val titlePhrase = Phrase()
        titlePhrase.add(Phrase("ND CONSULTAS\n", font(20f, bold = true, color = Color.WHITE)))

        val titleCell = PdfPCell(titlePhrase)
        titleCell.backgroundColor = PRIMARY_DARK
        titleCell.border = Rectangle.NO_BORDER
        titleCell.horizontalAlignment = Element.ALIGN_LEFT
        titleCell.verticalAlignment = Element.ALIGN_MIDDLE
        titleCell.setPadding(12f)
        titleCell.paddingLeft = 8f
        headerTable.addCell(titleCell)

        document.add(headerTable)
        addAccentBar(document)
    }

    private fun addHeaderWithoutLogo(document: Document) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f

        val cell = PdfPCell()
        cell.backgroundColor = PRIMARY_DARK
        cell.border = Rectangle.NO_BORDER
        cell.setPadding(18f)
        cell.horizontalAlignment = Element.ALIGN_CENTER

        val titlePhrase = Phrase()
        titlePhrase.add(Phrase("ND CONSULTAS", font(20f, bold = true, color = Color.WHITE)))
        cell.phrase = titlePhrase

        table.addCell(cell)
        document.add(table)
        addAccentBar(document)
    }

    private fun addAccentBar(document: Document) {
        val bar = PdfPTable(1)
        bar.widthPercentage = 100f
        val barCell = PdfPCell(Phrase(" "))
        barCell.backgroundColor = ACCENT
        barCell.border = Rectangle.NO_BORDER
        barCell.fixedHeight = 4f
        bar.addCell(barCell)
        document.add(bar)
    }

    // ── Consultation Info ──────────────────────────────────────────

    private fun addConsultationInfo(document: Document, tipoLabel: String, query: String) {
        document.add(Paragraph(" "))

        val now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DATE_FMT)

        val reportTitle = when {
            tipoLabel.contains("CPF", ignoreCase = true) || tipoLabel.contains("Telefone", ignoreCase = true) -> "Relatorio de Consulta Pessoal"
            else -> "Relatorio de Consulta Veicular"
        }
        val sectionTitle = Paragraph(reportTitle, font(13f, bold = true, color = PRIMARY))
        sectionTitle.spacingAfter = 8f
        document.add(sectionTitle)

        val infoTable = PdfPTable(2)
        infoTable.widthPercentage = 100f
        infoTable.setWidths(floatArrayOf(28f, 72f))

        addInfoRow(infoTable, "Tipo de Consulta:", tipoLabel)
        addInfoRow(infoTable, "Dado Consultado:", query)
        addInfoRow(infoTable, "Data / Hora:", now)

        document.add(infoTable)
        document.add(Paragraph(" "))
    }

    private fun addInfoRow(table: PdfPTable, label: String, value: String) {
        val labelCell = PdfPCell(Phrase(label, font(9.5f, bold = true)))
        labelCell.border = Rectangle.NO_BORDER
        labelCell.paddingBottom = 5f
        labelCell.paddingLeft = 4f
        table.addCell(labelCell)

        val valueCell = PdfPCell(Phrase(value, font(9.5f, color = GRAY_TEXT)))
        valueCell.border = Rectangle.NO_BORDER
        valueCell.paddingBottom = 5f
        table.addCell(valueCell)
    }

    // ── Painel de alertas visuais ──────────────────────────────────

    private fun addAlertPanel(document: Document, alerts: List<AlertInfo>, panelLabel: String = "Situacao do Veiculo") {
        val panelTitle = Paragraph(panelLabel, font(12f, bold = true, color = PRIMARY))
        panelTitle.spacingAfter = 6f
        document.add(panelTitle)

        val cols = when {
            alerts.size == 1 -> 1
            alerts.size <= 4 -> 2
            else -> 3
        }
        val table = PdfPTable(cols)
        table.widthPercentage = 100f

        for (alert in alerts) {
            val bgColor: Color
            val borderColor: Color
            val textColor: Color
            val icon: String

            when {
                alert.label == "SCORE" -> {
                    bgColor = SECTION_BLUE
                    borderColor = SECTION_BLUE_BORDER
                    textColor = PRIMARY_DARK
                    icon = "\u2605"  // ★
                }
                alert.isAlert -> {
                    bgColor = RED_BG
                    borderColor = RED_BORDER
                    textColor = RED
                    icon = "\u2716"  // ✖
                }
                alert.isIndeterminate -> {
                    bgColor = YELLOW_BG
                    borderColor = YELLOW_BORDER
                    textColor = YELLOW
                    icon = "\u26A0"  // ⚠
                }
                else -> {
                    bgColor = GREEN_BG
                    borderColor = GREEN_BORDER
                    textColor = GREEN
                    icon = "\u2714"  // ✔
                }
            }

            val phrase = Phrase()
            phrase.add(Phrase("$icon ", font(14f, bold = true, color = textColor)))
            phrase.add(Phrase("${alert.label}\n", font(8.5f, bold = true, color = textColor)))
            phrase.add(Phrase(alert.detail, font(7f, color = GRAY_TEXT)))

            val cell = PdfPCell(phrase)
            cell.backgroundColor = bgColor
            cell.borderColor = borderColor
            cell.borderWidth = 1.5f
            cell.setPadding(8f)
            cell.paddingTop = 10f
            cell.paddingBottom = 10f
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.verticalAlignment = Element.ALIGN_MIDDLE
            cell.minimumHeight = 55f
            table.addCell(cell)
        }

        val remainder = alerts.size % cols
        if (remainder != 0) {
            for (i in 0 until (cols - remainder)) {
                val emptyCell = PdfPCell(Phrase(""))
                emptyCell.border = Rectangle.NO_BORDER
                table.addCell(emptyCell)
            }
        }

        document.add(table)

        val separator = PdfPTable(1)
        separator.widthPercentage = 100f
        val sepCell = PdfPCell(Phrase(" "))
        sepCell.border = Rectangle.BOTTOM
        sepCell.borderColor = PRIMARY
        sepCell.borderWidth = 2f
        sepCell.fixedHeight = 4f
        separator.addCell(sepCell)
        document.add(separator)
        document.add(Paragraph(" "))
    }

    // ── Resultado por seções ──────────────────────────────────────

    private fun addResultSections(document: Document, data: Map<String, Any?>) {
        if (data.isEmpty()) {
            document.add(Paragraph("Nenhum dado retornado para esta consulta.", font(10f, color = GRAY_TEXT)))
            return
        }

        val collapsed = collapseData(data)

        val simplePairs = mutableListOf<Pair<String, Any?>>()
        val sections = mutableListOf<Triple<String, String, Any?>>()

        collapsed.forEach { (key, value) ->
            when {
                value is Map<*, *> && value.isNotEmpty() -> sections.add(Triple(key, formatKey(key), value))
                value is List<*> && value.isNotEmpty() && value.first() is Map<*, *> -> sections.add(Triple(key, formatKey(key), value))
                else -> simplePairs.add(key to value)
            }
        }

        if (simplePairs.isNotEmpty()) {
            addSectionTitle(document, "Informacoes Gerais")
            addDataTable(document, simplePairs.map { (k, v) -> k to formatValue(v) })
        }

        for ((_, label, value) in sections) {
            addSectionTitle(document, label)
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    renderSectionData(document, value as Map<String, Any?>)
                }
                is List<*> -> {
                    val items = value.filterIsInstance<Map<String, Any?>>()
                    if (items.isNotEmpty()) addGroupedTable(document, items)
                }
            }
        }
    }

    private fun renderSectionData(document: Document, data: Map<String, Any?>) {
        val collapsed = collapseData(data)

        val simplePairs = mutableListOf<Pair<String, String>>()
        val subSections = mutableListOf<Triple<String, String, Any?>>()

        collapsed.forEach { (key, value) ->
            when {
                value is Map<*, *> && value.isNotEmpty() -> subSections.add(Triple(key, formatKey(key), value))
                value is List<*> && value.isNotEmpty() && value.first() is Map<*, *> -> subSections.add(Triple(key, formatKey(key), value))
                else -> simplePairs.add(key to formatValue(value))
            }
        }

        if (simplePairs.isNotEmpty()) addDataTable(document, simplePairs)

        for ((_, label, value) in subSections) {
            when (value) {
                is Map<*, *> -> {
                    addSubSectionTitle(document, label)
                    @Suppress("UNCHECKED_CAST")
                    renderSectionData(document, value as Map<String, Any?>)
                }
                is List<*> -> {
                    addSubSectionTitle(document, label)
                    val items = value.filterIsInstance<Map<String, Any?>>()
                    if (items.isNotEmpty()) addGroupedTable(document, items)
                }
            }
        }
    }

    // ── Colapsar {codigo, descricao} ──────────────────────────────

    private fun collapseData(data: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        data.forEach { (key, value) ->
            when {
                value is Map<*, *> && isCodigoDescricao(value) -> {
                    @Suppress("UNCHECKED_CAST")
                    result[key] = mergeCodigoDescricao(value as Map<String, Any?>)
                }
                value is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    result[key] = collapseData(value as Map<String, Any?>)
                }
                value is List<*> -> {
                    result[key] = value.map { item ->
                        if (item is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            collapseData(item as Map<String, Any?>)
                        } else item
                    }
                }
                else -> result[key] = value
            }
        }
        return result
    }

    private fun isCodigoDescricao(map: Map<*, *>): Boolean {
        val keys = map.keys.map { it.toString().lowercase() }.toSet()
        return keys.size <= 3 && keys.contains("codigo") && keys.contains("descricao")
    }

    private fun mergeCodigoDescricao(map: Map<String, Any?>): String {
        val descricao = map["descricao"]?.toString()?.trim() ?: ""
        val codigo = map["codigo"]?.toString()?.trim() ?: ""
        return when {
            descricao.isNotBlank() && codigo.isNotBlank() -> "$descricao ($codigo)"
            descricao.isNotBlank() -> descricao
            codigo.isNotBlank() -> codigo
            else -> "Nao informado"
        }
    }

    // ── Tabela agrupada para arrays de objetos ────────────────────

    private fun addGroupedTable(document: Document, items: List<Map<String, Any?>>) {
        if (items.isEmpty()) return

        val columns = linkedSetOf<String>()
        for (item in items) {
            for ((key, value) in item) {
                if (value !is Map<*, *> && !(value is List<*> && value.firstOrNull() is Map<*, *>)) {
                    columns.add(key)
                }
            }
        }

        if (columns.isEmpty()) {
            items.forEachIndexed { index, item ->
                addSubSectionTitle(document, "[${index + 1}]")
                addDataTable(document, flattenMap(item, ""))
            }
            return
        }

        val visibleColumns = columns.toList().take(8)
        val colCount = visibleColumns.size

        val table = PdfPTable(colCount)
        table.widthPercentage = 100f

        for (col in visibleColumns) {
            val cell = PdfPCell(Phrase(formatKey(col), font(7.5f, bold = true, color = Color.WHITE)))
            cell.backgroundColor = PRIMARY_DARK
            cell.setPadding(6f)
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.border = Rectangle.BOX
            cell.borderColor = PRIMARY_DARK
            cell.borderWidth = 0.5f
            table.addCell(cell)
        }

        items.forEachIndexed { rowIndex, item ->
            val rowBg = if (rowIndex % 2 == 0) Color.WHITE else LIGHT_BG

            for (col in visibleColumns) {
                val text = formatValue(item[col])
                val sentiment = classifyValue(col, text)

                val cellBg: Color
                val textColor: Color
                when (sentiment) {
                    ValueSentiment.POSITIVE -> { cellBg = GREEN_BG; textColor = GREEN }
                    ValueSentiment.NEGATIVE -> { cellBg = RED_BG; textColor = RED }
                    ValueSentiment.WARNING -> { cellBg = YELLOW_BG; textColor = YELLOW }
                    ValueSentiment.NEUTRAL -> { cellBg = rowBg; textColor = DARK_TEXT }
                }

                val cell = PdfPCell(Phrase(text, font(7f, color = textColor)))
                cell.backgroundColor = cellBg
                cell.setPadding(5f)
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.verticalAlignment = Element.ALIGN_MIDDLE
                cell.borderColor = BORDER_COLOR
                cell.borderWidth = 0.5f
                cell.minimumHeight = 22f
                table.addCell(cell)
            }
        }

        document.add(table)

        items.forEachIndexed { index, item ->
            val subMaps = item.filter { (key, value) ->
                key !in visibleColumns && (value is Map<*, *> || (value is List<*> && value.firstOrNull() is Map<*, *>))
            }
            if (subMaps.isNotEmpty()) {
                addSubSectionTitle(document, "Detalhes [${index + 1}]")
                renderSectionData(document, subMaps)
            }
        }
    }

    // ── Classificação de sentimento ───────────────────────────────

    private enum class ValueSentiment { POSITIVE, NEGATIVE, WARNING, NEUTRAL }

    private fun classifyValue(key: String, value: String): ValueSentiment {
        val keyLower = key.lowercase()
        val valueLower = value.lowercase()

        val isStatusField = keyLower.contains("situacao") || keyLower.contains("condicao") ||
            keyLower.contains("status") || keyLower.contains("ocorrencia") ||
            keyLower.contains("sinistro") || keyLower.contains("restricao") ||
            keyLower.contains("restri\u00e7\u00e3o") || keyLower.contains("roubo") ||
            keyLower.contains("furto") || keyLower.contains("bloqueio") ||
            keyLower.contains("existe") || keyLower.contains("score") ||
            keyLower.contains("pontuacao") || keyLower.contains("chassi")

        if (!isStatusField) return ValueSentiment.NEUTRAL

        for (keyword in NEGATIVE_KEYWORDS) {
            if (valueLower.contains(keyword)) return ValueSentiment.NEGATIVE
        }
        if (valueLower.contains("irregular") || valueLower.contains("cancelad") ||
            valueLower.contains("bloqueado") || valueLower.contains("existe ocorrencia")
        ) return ValueSentiment.NEGATIVE
        if (keyLower.contains("existe") && (valueLower == "1" || valueLower == "sim" || valueLower == "true"))
            return ValueSentiment.NEGATIVE

        if (valueLower.contains("verificar") || valueLower.contains("n/c") ||
            valueLower.contains("nao informado") || valueLower.contains("nao divulgado")
        ) return ValueSentiment.WARNING

        if (valueLower.contains("sucesso") || valueLower.contains("regular") ||
            valueLower.contains("nenhum") || valueLower.contains("sem ") ||
            valueLower.contains("nao existe") || valueLower.contains("livre") ||
            valueLower.contains("sem restricao") || valueLower.contains("normal")
        ) return ValueSentiment.POSITIVE
        if (keyLower.contains("existe") && (valueLower == "0" || valueLower == "nao" || valueLower == "false"))
            return ValueSentiment.POSITIVE

        return ValueSentiment.NEUTRAL
    }

    // ── Títulos e tabela key-value ─────────────────────────────────

    private fun addSectionTitle(document: Document, title: String) {
        document.add(Paragraph(" "))
        val table = PdfPTable(1)
        table.widthPercentage = 100f

        val cell = PdfPCell(Phrase(title.uppercase(), font(10f, bold = true, color = PRIMARY_DARK)))
        cell.backgroundColor = SECTION_BLUE
        cell.borderColor = SECTION_BLUE_BORDER
        cell.borderWidth = 1f
        cell.setPadding(8f)
        cell.paddingLeft = 12f
        table.addCell(cell)

        document.add(table)
    }

    private fun addSubSectionTitle(document: Document, title: String) {
        val p = Paragraph(title, font(9f, bold = true, color = ACCENT))
        p.spacingBefore = 6f
        p.spacingAfter = 4f
        p.indentationLeft = 8f
        document.add(p)
    }

    private fun addDataTable(document: Document, pairs: List<Pair<String, String>>) {
        if (pairs.isEmpty()) return

        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(35f, 65f))

        pairs.forEachIndexed { index, (key, value) ->
            val sentiment = classifyValue(key, value)
            val rowBg = if (index % 2 == 0) Color.WHITE else LIGHT_BG

            val keyCell = PdfPCell(Phrase(formatKey(key), font(8.5f, bold = true)))
            keyCell.backgroundColor = rowBg
            keyCell.setPadding(6f)
            keyCell.paddingLeft = 10f
            keyCell.borderColor = BORDER_COLOR
            keyCell.borderWidth = 0.5f
            table.addCell(keyCell)

            val valueBg: Color
            val textColor: Color
            when (sentiment) {
                ValueSentiment.POSITIVE -> { valueBg = GREEN_BG; textColor = GREEN }
                ValueSentiment.NEGATIVE -> { valueBg = RED_BG; textColor = RED }
                ValueSentiment.WARNING -> { valueBg = YELLOW_BG; textColor = YELLOW }
                ValueSentiment.NEUTRAL -> { valueBg = rowBg; textColor = DARK_TEXT }
            }

            val valCell = PdfPCell(Phrase(value, font(8.5f, color = textColor)))
            valCell.backgroundColor = valueBg
            valCell.setPadding(6f)
            valCell.borderColor = BORDER_COLOR
            valCell.borderWidth = 0.5f
            table.addCell(valCell)
        }

        document.add(table)
    }

    // ── Footer ─────────────────────────────────────────────────────

    private fun addFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        val footerTable = PdfPTable(1)
        footerTable.widthPercentage = 100f

        val accentCell = PdfPCell(Phrase(" "))
        accentCell.backgroundColor = ACCENT
        accentCell.border = Rectangle.NO_BORDER
        accentCell.fixedHeight = 3f
        footerTable.addCell(accentCell)

        val brandCell = PdfPCell(Phrase("ND Consultas", font(8f, bold = true, color = PRIMARY)))
        brandCell.border = Rectangle.NO_BORDER
        brandCell.paddingTop = 8f
        brandCell.paddingBottom = 2f
        brandCell.horizontalAlignment = Element.ALIGN_CENTER
        footerTable.addCell(brandCell)

        val disclaimerCell = PdfPCell(
            Phrase(
                "Documento gerado automaticamente. As informacoes contidas neste relatorio sao provenientes de fontes publicas e oficiais.",
                font(7f, color = LIGHT_GRAY)
            )
        )
        disclaimerCell.border = Rectangle.NO_BORDER
        disclaimerCell.paddingTop = 2f
        disclaimerCell.paddingBottom = 4f
        disclaimerCell.horizontalAlignment = Element.ALIGN_CENTER
        footerTable.addCell(disclaimerCell)

        document.add(footerTable)
    }

    // ── Busca de valores nos dados ────────────────────────────────

    private fun findRawValue(data: Map<String, Any?>, targetKey: String): String? {
        for ((key, value) in data) {
            if (key.equals(targetKey, ignoreCase = true)) return value?.toString()?.trim()?.ifBlank { null }
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val found = findRawValue(value as Map<String, Any?>, targetKey)
                if (found != null) return found
            }
            if (value is List<*>) {
                for (item in value) {
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val found = findRawValue(item as Map<String, Any?>, targetKey)
                        if (found != null) return found
                    }
                }
            }
        }
        return null
    }

    // ── Logo download with cache ───────────────────────────────────

    private fun getLogoBytes(): ByteArray? {
        if (cachedLogo != null) return cachedLogo
        if (logoLoadAttempted) return null

        synchronized(this) {
            if (cachedLogo != null) return cachedLogo
            if (logoLoadAttempted) return null
            logoLoadAttempted = true

            val url = botProperties.logoUrl
            if (url.isBlank()) {
                log.info("Logo URL nao configurada — PDF sera gerado sem logo")
                return null
            }

            return try {
                val bytes = downloadImage(url)
                if (bytes != null && bytes.isNotEmpty()) {
                    Image.getInstance(bytes)
                    cachedLogo = bytes
                    log.info("Logo carregada com sucesso ({} bytes)", bytes.size)
                    bytes
                } else {
                    log.warn("Logo URL retornou conteudo vazio: {}", url)
                    null
                }
            } catch (e: Exception) {
                log.warn("Falha ao carregar logo de {}: {}", url, e.message)
                null
            }
        }
    }

    private fun downloadImage(urlString: String): ByteArray? {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "NDConsultas-BOT/1.0")

            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) return null
                    currentUrl = if (location.startsWith("http")) location
                    else URI(currentUrl).resolve(location).toString()
                    redirects++
                    continue
                }
                if (code != 200) return null
                val contentType = connection.contentType ?: ""
                if (!contentType.startsWith("image/")) return null
                return connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    // ── Utilities ──────────────────────────────────────────────────

    private fun formatKey(key: String): String {
        return key.replace("_", " ").split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "Nao informado"
            is List<*> -> {
                if (value.isEmpty()) "Nao informado"
                else value.filterNotNull().joinToString(", ").ifBlank { "Nao informado" }
            }
            else -> value.toString().trim().ifBlank { "Nao informado" }
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
}
