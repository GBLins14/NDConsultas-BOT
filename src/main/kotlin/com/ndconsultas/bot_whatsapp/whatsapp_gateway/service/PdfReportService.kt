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
import java.time.LocalDateTime
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

        // Alertas
        private val GREEN = Color(34, 139, 34)
        private val GREEN_BG = Color(232, 245, 233)
        private val GREEN_BORDER = Color(129, 199, 132)
        private val RED = Color(198, 40, 40)
        private val RED_BG = Color(255, 235, 238)
        private val RED_BORDER = Color(239, 154, 154)
        private val YELLOW = Color(245, 166, 35)
        private val YELLOW_BG = Color(255, 248, 225)
        private val YELLOW_BORDER = Color(255, 224, 130)

        // Seções
        private val SECTION_BLUE = Color(227, 242, 253)
        private val SECTION_BLUE_BORDER = Color(100, 181, 246)

        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        private val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        private val baseFontBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        private fun font(size: Float, bold: Boolean = false, color: Color = DARK_TEXT): Font {
            return Font(if (bold) baseFontBold else baseFont, size, Font.NORMAL, color)
        }

        private const val MAX_REDIRECTS = 5

        // Keywords para detectar alertas nos dados
        private val NEGATIVE_KEYWORDS = listOf(
            "roubo", "furto", "sinistro", "bloqueio", "penhora", "impedimento",
            "restricao", "restri\u00e7\u00e3o", "existe ocorrencia", "ocorrencia de sinistro",
            "existe_ocorrencia", "indicio", "irregular", "cancelad"
        )
    }

    @Volatile
    private var cachedLogo: ByteArray? = null

    @Volatile
    private var logoLoadAttempted = false

    // ── Data class para alertas ────────────────────────────────────

    data class AlertInfo(
        val label: String,
        val isAlert: Boolean,
        val detail: String
    )

    // ── Geração principal ─────────────────────────────────────────

    fun generate(tipoLabel: String, query: String, data: Map<String, Any?>): ByteArray {
        val output = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(document, output)

        document.open()
        addHeader(document)
        addConsultationInfo(document, tipoLabel, query)

        val alerts = extractAlerts(data)
        if (alerts.isNotEmpty()) {
            addAlertPanel(document, alerts)
        }

        addResultSections(document, data)
        addFooter(document)
        document.close()

        return output.toByteArray()
    }

    // ── Header com Logo ───────────────────────────────────────────

    private fun addHeader(document: Document) {
        val logoBytes = getLogoBytes()

        if (logoBytes != null) {
            addHeaderWithLogo(document, logoBytes)
        } else {
            addHeaderWithoutLogo(document)
        }

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
        titlePhrase.add(Phrase("VEICULARES", font(12f, bold = false, color = Color(180, 210, 240))))

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
        titlePhrase.add(Phrase("ND CONSULTAS ", font(20f, bold = true, color = Color.WHITE)))
        titlePhrase.add(Phrase("VEICULARES", font(20f, bold = false, color = Color(180, 210, 240))))
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

        val now = LocalDateTime.now().format(DATE_FMT)

        val sectionTitle = Paragraph("Relatorio de Consulta Veicular", font(13f, bold = true, color = PRIMARY))
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

    private fun extractAlerts(data: Map<String, Any?>): List<AlertInfo> {
        val alerts = mutableListOf<AlertInfo>()

        // Roubo / Furto
        val hasRouboFurto = searchInData(data, listOf("roubo", "furto"))
        val routeDetail = findValueForKeys(data, listOf("roubo", "furto", "tipo_ocorrencia", "tipo de ocorrencia"))
        alerts.add(
            AlertInfo(
                label = "ROUBO / FURTO",
                isAlert = hasRouboFurto,
                detail = if (hasRouboFurto) routeDetail ?: "Ocorrencia encontrada" else "Nenhuma ocorrencia"
            )
        )

        // Sinistro
        val existeSinistro = findRawValue(data, "existe_ocorrencia")
        val hasSinistro = existeSinistro == "1" || searchInData(data, listOf("sinistro", "indicio_sinistro", "indicio sinistro"))
        val sinistroDetail = findValueForKeys(data, listOf("descricao_ocorrencia", "descricao ocorrencia"))
        alerts.add(
            AlertInfo(
                label = "SINISTRO",
                isAlert = hasSinistro,
                detail = if (hasSinistro) sinistroDetail ?: "Indicio de sinistro encontrado" else "Sem indicio de sinistro"
            )
        )

        // Restricoes
        val hasRestricao = searchInData(data, listOf("restricao", "restri\u00e7\u00e3o", "bloqueio", "penhora", "impedimento", "renajud"))
        val restricaoDetail = findValueForKeys(data, listOf("restricao", "restri\u00e7\u00e3o", "restricoes", "tipo_restricao"))
        alerts.add(
            AlertInfo(
                label = "RESTRICOES",
                isAlert = hasRestricao,
                detail = if (hasRestricao) restricaoDetail ?: "Restricao encontrada" else "Sem restricoes"
            )
        )

        // Leilao
        val qtdOcorrencias = findRawValue(data, "quantidade_ocorrencias")
        val hasLeilao = (qtdOcorrencias != null && qtdOcorrencias != "0") || searchInData(data, listOf("leilao", "leiloeiro", "comitente"))
        alerts.add(
            AlertInfo(
                label = "LEILAO",
                isAlert = hasLeilao,
                detail = if (hasLeilao) "${qtdOcorrencias ?: "?"} ocorrencia(s) de leilao" else "Sem historico de leilao"
            )
        )

        // Multas
        val hasMultas = searchInData(data, listOf("multa", "infracao", "infra\u00e7\u00e3o", "auto_infracao"))
        alerts.add(
            AlertInfo(
                label = "MULTAS",
                isAlert = hasMultas,
                detail = if (hasMultas) "Multa(s) encontrada(s)" else "Sem multas registradas"
            )
        )

        return alerts
    }

    private fun addAlertPanel(document: Document, alerts: List<AlertInfo>) {
        // Titulo do painel
        val panelTitle = Paragraph("Situacao do Veiculo", font(12f, bold = true, color = PRIMARY))
        panelTitle.spacingAfter = 6f
        document.add(panelTitle)

        // Grid de alertas: 2 ou 3 colunas
        val cols = if (alerts.size <= 4) 2 else 3
        val table = PdfPTable(cols)
        table.widthPercentage = 100f

        for (alert in alerts) {
            val bgColor: Color
            val borderColor: Color
            val textColor: Color
            val icon: String

            if (alert.isAlert) {
                bgColor = RED_BG
                borderColor = RED_BORDER
                textColor = RED
                icon = "\u2716"  // ✖
            } else {
                bgColor = GREEN_BG
                borderColor = GREEN_BORDER
                textColor = GREEN
                icon = "\u2714"  // ✔
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

        // Preencher celulas restantes se nao for multiplo do numero de colunas
        val remainder = alerts.size % cols
        if (remainder != 0) {
            for (i in 0 until (cols - remainder)) {
                val emptyCell = PdfPCell(Phrase(""))
                emptyCell.border = Rectangle.NO_BORDER
                table.addCell(emptyCell)
            }
        }

        document.add(table)

        // Separador
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

        // Separar dados em seções (maps de nível top-level) vs campos simples
        val simplePairs = mutableListOf<Pair<String, Any?>>()
        val sections = mutableListOf<Triple<String, String, Map<String, Any?>>>() // key, label, data

        data.forEach { (key, value) ->
            when {
                value is Map<*, *> && value.isNotEmpty() -> {
                    @Suppress("UNCHECKED_CAST")
                    sections.add(Triple(key, formatKey(key), value as Map<String, Any?>))
                }
                value is List<*> && value.isNotEmpty() && value.first() is Map<*, *> -> {
                    // Lista de maps => cada item vira uma sub-seção
                    value.forEachIndexed { index, item ->
                        if (item is Map<*, *> && item.isNotEmpty()) {
                            @Suppress("UNCHECKED_CAST")
                            sections.add(Triple("${key}_${index}", "${formatKey(key)} [${index + 1}]", item as Map<String, Any?>))
                        }
                    }
                }
                else -> simplePairs.add(key to value)
            }
        }

        // Campos simples no topo
        if (simplePairs.isNotEmpty()) {
            addSectionTitle(document, "Informacoes Gerais")
            addDataTable(document, simplePairs.map { (k, v) -> k to formatValue(v) })
        }

        // Cada seção com header colorido
        for ((_, label, sectionData) in sections) {
            addSectionTitle(document, label)
            renderSectionData(document, sectionData)
        }
    }

    private fun renderSectionData(document: Document, data: Map<String, Any?>) {
        val simplePairs = mutableListOf<Pair<String, String>>()
        val subSections = mutableListOf<Triple<String, String, Any?>>()

        data.forEach { (key, value) ->
            when {
                value is Map<*, *> && value.isNotEmpty() -> subSections.add(Triple(key, formatKey(key), value))
                value is List<*> && value.isNotEmpty() && value.first() is Map<*, *> -> subSections.add(Triple(key, formatKey(key), value))
                else -> simplePairs.add(key to formatValue(value))
            }
        }

        if (simplePairs.isNotEmpty()) {
            addDataTable(document, simplePairs)
        }

        for ((_, label, value) in subSections) {
            when (value) {
                is Map<*, *> -> {
                    addSubSectionTitle(document, label)
                    @Suppress("UNCHECKED_CAST")
                    val flat = flattenMap(value as Map<String, Any?>, "")
                    addDataTable(document, flat)
                }
                is List<*> -> {
                    value.forEachIndexed { index, item ->
                        if (item is Map<*, *> && item.isNotEmpty()) {
                            addSubSectionTitle(document, "$label [${index + 1}]")
                            @Suppress("UNCHECKED_CAST")
                            val flat = flattenMap(item as Map<String, Any?>, "")
                            addDataTable(document, flat)
                        }
                    }
                }
            }
        }
    }

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
            val bg = if (index % 2 == 0) Color.WHITE else LIGHT_BG

            val keyCell = PdfPCell(Phrase(formatKey(key), font(8.5f, bold = true)))
            keyCell.backgroundColor = bg
            keyCell.setPadding(6f)
            keyCell.paddingLeft = 10f
            keyCell.borderColor = BORDER_COLOR
            keyCell.borderWidth = 0.5f
            table.addCell(keyCell)

            // Colorir valores negativos
            val valueColor = getValueColor(key, value)
            val valCell = PdfPCell(Phrase(value, font(8.5f, color = valueColor)))
            valCell.backgroundColor = bg
            valCell.setPadding(6f)
            valCell.borderColor = BORDER_COLOR
            valCell.borderWidth = 0.5f
            table.addCell(valCell)
        }

        document.add(table)
    }

    private fun getValueColor(key: String, value: String): Color {
        val keyLower = key.lowercase()
        val valueLower = value.lowercase()

        // Valores positivos / limpos
        if (valueLower.contains("nenhum") || valueLower.contains("sem ") ||
            valueLower == "0" || valueLower == "nao informado" ||
            valueLower.contains("nao existe") || valueLower.contains("sem ocorrencia")
        ) {
            // Só aplica verde se for campo de alerta
            if (NEGATIVE_KEYWORDS.any { keyLower.contains(it) || valueLower.contains(it) }.not() &&
                keyLower.contains("ocorrencia").not() && keyLower.contains("sinistro").not()
            ) {
                return DARK_TEXT
            }
        }

        // Valores negativos / alertas
        for (keyword in NEGATIVE_KEYWORDS) {
            if (valueLower.contains(keyword)) return RED
        }

        // Campos de situação irregular
        if ((keyLower.contains("situacao") || keyLower.contains("status")) &&
            (valueLower.contains("irregular") || valueLower.contains("cancelad") || valueLower.contains("bloqueado"))
        ) {
            return RED
        }

        return DARK_TEXT
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

        val brandCell = PdfPCell(Phrase("ND Consultas Veiculares", font(8f, bold = true, color = PRIMARY)))
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

    // ── Busca recursiva nos dados ──────────────────────────────────

    private fun searchInData(data: Map<String, Any?>, keywords: List<String>): Boolean {
        for ((key, value) in data) {
            val keyLower = key.lowercase()
            for (keyword in keywords) {
                if (keyLower.contains(keyword)) {
                    val strVal = value?.toString()?.lowercase() ?: ""
                    // Não considerar "0", "nao", "nenhum", "sem" como positivos
                    if (strVal != "0" && strVal != "false" && !strVal.startsWith("nao ") &&
                        !strVal.startsWith("nenhum") && !strVal.startsWith("sem ") && strVal.isNotBlank()
                    ) {
                        return true
                    }
                }
            }
            // Buscar nos valores também
            val strVal = value?.toString()?.lowercase() ?: ""
            for (keyword in keywords) {
                if (strVal.contains(keyword) && strVal != "0" && strVal != "false") {
                    // Verificar se nao é negação
                    if (!strVal.contains("nao existe") && !strVal.contains("sem $keyword") &&
                        !strVal.contains("nenhum") && !strVal.startsWith("sem ")
                    ) {
                        return true
                    }
                }
            }
            // Recursão em sub-maps
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                if (searchInData(value as Map<String, Any?>, keywords)) return true
            }
            if (value is List<*>) {
                for (item in value) {
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        if (searchInData(item as Map<String, Any?>, keywords)) return true
                    }
                }
            }
        }
        return false
    }

    private fun findRawValue(data: Map<String, Any?>, targetKey: String): String? {
        for ((key, value) in data) {
            if (key.equals(targetKey, ignoreCase = true)) {
                return value?.toString()?.trim()?.ifBlank { null }
            }
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

    private fun findValueForKeys(data: Map<String, Any?>, keys: List<String>): String? {
        for (key in keys) {
            val value = findRawValue(data, key)
            if (value != null) return value
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
                    if (location.isNullOrBlank()) {
                        log.warn("Redirect sem Location header: {}", currentUrl)
                        return null
                    }
                    currentUrl = if (location.startsWith("http")) location
                    else URI(currentUrl).resolve(location).toString()
                    redirects++
                    continue
                }

                if (code != 200) {
                    log.warn("Logo download retornou HTTP {}: {}", code, currentUrl)
                    return null
                }

                val contentType = connection.contentType ?: ""
                if (!contentType.startsWith("image/")) {
                    log.warn("Logo URL nao retornou imagem (content-type: {}): {}", contentType, currentUrl)
                    return null
                }

                return connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }

        log.warn("Muitos redirects ao baixar logo: {}", urlString)
        return null
    }

    // ── Utilities ──────────────────────────────────────────────────

    private fun formatKey(key: String): String {
        return key.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
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
