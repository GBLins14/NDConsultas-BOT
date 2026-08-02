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

        private val PRIMARY = Color(0, 82, 155)
        private val PRIMARY_DARK = Color(0, 60, 120)
        private val ACCENT = Color(0, 150, 136)
        private val DARK_TEXT = Color(33, 33, 33)
        private val GRAY_TEXT = Color(100, 100, 100)
        private val LIGHT_GRAY = Color(150, 150, 150)
        private val LIGHT_BG = Color(245, 247, 250)
        private val BORDER_COLOR = Color(210, 215, 220)
        private val HEADER_GRADIENT_TOP = Color(0, 82, 155)
        private val HEADER_GRADIENT_BOTTOM = Color(0, 55, 110)
        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        private val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        private val baseFontBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        private fun font(size: Float, bold: Boolean = false, color: Color = DARK_TEXT): Font {
            return Font(if (bold) baseFontBold else baseFont, size, Font.NORMAL, color)
        }

        private const val MAX_REDIRECTS = 5
    }

    @Volatile
    private var cachedLogo: ByteArray? = null

    @Volatile
    private var logoLoadAttempted = false

    fun generate(tipoLabel: String, query: String, data: Map<String, Any?>): ByteArray {
        val output = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(document, output)

        document.open()
        addHeader(document)
        addConsultationInfo(document, tipoLabel, query)
        addResultsTable(document, data)
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
        headerTable.setWidths(floatArrayOf(20f, 80f))

        // Logo cell
        val logoCell = try {
            val img = Image.getInstance(logoBytes)
            img.scaleToFit(60f, 60f)
            val cell = PdfPCell(img, false)
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.verticalAlignment = Element.ALIGN_MIDDLE
            cell
        } catch (e: Exception) {
            log.warn("Falha ao processar logo para PDF: {}", e.message)
            PdfPCell(Phrase(""))
        }
        logoCell.backgroundColor = HEADER_GRADIENT_TOP
        logoCell.border = Rectangle.NO_BORDER
        logoCell.setPadding(12f)
        logoCell.paddingLeft = 16f
        headerTable.addCell(logoCell)

        // Title cell
        val titlePhrase = Phrase()
        titlePhrase.add(Phrase("ND CONSULTAS\n", font(18f, bold = true, color = Color.WHITE)))
        titlePhrase.add(Phrase("VEICULARES", font(14f, bold = false, color = Color(180, 210, 240))))

        val titleCell = PdfPCell(titlePhrase)
        titleCell.backgroundColor = HEADER_GRADIENT_TOP
        titleCell.border = Rectangle.NO_BORDER
        titleCell.horizontalAlignment = Element.ALIGN_LEFT
        titleCell.verticalAlignment = Element.ALIGN_MIDDLE
        titleCell.setPadding(12f)
        titleCell.paddingLeft = 8f
        headerTable.addCell(titleCell)

        document.add(headerTable)

        // Accent bar
        addAccentBar(document)
    }

    private fun addHeaderWithoutLogo(document: Document) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f

        val cell = PdfPCell()
        cell.backgroundColor = HEADER_GRADIENT_TOP
        cell.border = Rectangle.NO_BORDER
        cell.setPadding(18f)
        cell.horizontalAlignment = Element.ALIGN_CENTER

        val titlePhrase = Phrase()
        titlePhrase.add(Phrase("ND CONSULTAS ", font(18f, bold = true, color = Color.WHITE)))
        titlePhrase.add(Phrase("VEICULARES", font(18f, bold = false, color = Color(180, 210, 240))))
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

        // Separator
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

    // ── Results Table ──────────────────────────────────────────────

    private fun addResultsTable(document: Document, data: Map<String, Any?>) {
        val sectionTitle = Paragraph("Resultado da Consulta", font(12f, bold = true, color = PRIMARY))
        sectionTitle.spacingAfter = 8f
        document.add(sectionTitle)

        if (data.isEmpty()) {
            document.add(Paragraph("Nenhum dado retornado para esta consulta.", font(10f, color = GRAY_TEXT)))
            return
        }

        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(35f, 65f))

        // Table header
        addTableHeaderCell(table, "Campo")
        addTableHeaderCell(table, "Valor")

        val flatData = flattenMap(data, "")
        flatData.forEachIndexed { index, (key, value) ->
            val bg = if (index % 2 == 0) Color.WHITE else LIGHT_BG

            val keyCell = PdfPCell(Phrase(formatKey(key), font(9f, bold = true)))
            keyCell.backgroundColor = bg
            keyCell.setPadding(7f)
            keyCell.borderColor = BORDER_COLOR
            keyCell.borderWidth = 0.5f
            table.addCell(keyCell)

            val valCell = PdfPCell(Phrase(value, font(9f)))
            valCell.backgroundColor = bg
            valCell.setPadding(7f)
            valCell.borderColor = BORDER_COLOR
            valCell.borderWidth = 0.5f
            table.addCell(valCell)
        }

        document.add(table)
    }

    private fun addTableHeaderCell(table: PdfPTable, text: String) {
        val cell = PdfPCell(Phrase(text, font(9.5f, bold = true, color = Color.WHITE)))
        cell.backgroundColor = PRIMARY_DARK
        cell.setPadding(9f)
        cell.border = Rectangle.NO_BORDER
        table.addCell(cell)
    }

    // ── Footer ─────────────────────────────────────────────────────

    private fun addFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        val footerTable = PdfPTable(1)
        footerTable.widthPercentage = 100f

        // Accent bar above footer
        val accentCell = PdfPCell(Phrase(" "))
        accentCell.backgroundColor = ACCENT
        accentCell.border = Rectangle.NO_BORDER
        accentCell.fixedHeight = 3f
        footerTable.addCell(accentCell)

        // Company name
        val brandCell = PdfPCell(Phrase("ND Consultas Veiculares", font(8f, bold = true, color = PRIMARY)))
        brandCell.border = Rectangle.NO_BORDER
        brandCell.paddingTop = 8f
        brandCell.paddingBottom = 2f
        brandCell.horizontalAlignment = Element.ALIGN_CENTER
        footerTable.addCell(brandCell)

        // Disclaimer
        val disclaimerCell = PdfPCell(Phrase(
            "Documento gerado automaticamente. As informacoes contidas neste relatorio sao provenientes de fontes publicas e oficiais.",
            font(7f, color = LIGHT_GRAY)
        ))
        disclaimerCell.border = Rectangle.NO_BORDER
        disclaimerCell.paddingTop = 2f
        disclaimerCell.paddingBottom = 4f
        disclaimerCell.horizontalAlignment = Element.ALIGN_CENTER
        footerTable.addCell(disclaimerCell)

        document.add(footerTable)
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
                    // Validate that it's a valid image by trying to parse it
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
}
