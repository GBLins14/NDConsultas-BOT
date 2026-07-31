package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PdfReportService {

    companion object {
        private val log = LoggerFactory.getLogger(PdfReportService::class.java)

        private val PRIMARY = Color(0, 82, 155)
        private val DARK_TEXT = Color(33, 33, 33)
        private val GRAY_TEXT = Color(100, 100, 100)
        private val LIGHT_BG = Color(245, 247, 250)
        private val BORDER_COLOR = Color(210, 215, 220)
        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        private val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        private val baseFontBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        private fun font(size: Float, bold: Boolean = false, color: Color = DARK_TEXT): Font {
            return Font(if (bold) baseFontBold else baseFont, size, Font.NORMAL, color)
        }
    }

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

    private fun addHeader(document: Document) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f

        val cell = PdfPCell(Phrase("ND CONSULTAS VEICULARES", font(16f, bold = true, color = Color.WHITE)))
        cell.backgroundColor = PRIMARY
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(16f)
        cell.border = Rectangle.NO_BORDER
        table.addCell(cell)

        document.add(table)
        document.add(Paragraph(" "))
    }

    private fun addConsultationInfo(document: Document, tipoLabel: String, query: String) {
        val now = LocalDateTime.now().format(DATE_FMT)

        document.add(Paragraph("Relatorio de Consulta Veicular", font(13f, bold = true, color = PRIMARY)))
        document.add(Paragraph(" "))

        val infoTable = PdfPTable(2)
        infoTable.widthPercentage = 100f
        infoTable.setWidths(floatArrayOf(30f, 70f))

        addInfoRow(infoTable, "Tipo de Consulta:", tipoLabel)
        addInfoRow(infoTable, "Dado Consultado:", query)
        addInfoRow(infoTable, "Data / Hora:", now)

        document.add(infoTable)
        document.add(Paragraph(" "))

        val separator = PdfPTable(1)
        separator.widthPercentage = 100f
        val sepCell = PdfPCell(Phrase(" "))
        sepCell.border = Rectangle.BOTTOM
        sepCell.borderColor = PRIMARY
        sepCell.borderWidth = 2f
        sepCell.fixedHeight = 8f
        separator.addCell(sepCell)
        document.add(separator)
        document.add(Paragraph(" "))
    }

    private fun addInfoRow(table: PdfPTable, label: String, value: String) {
        val labelCell = PdfPCell(Phrase(label, font(9f, bold = true)))
        labelCell.border = Rectangle.NO_BORDER
        labelCell.paddingBottom = 4f
        table.addCell(labelCell)

        val valueCell = PdfPCell(Phrase(value, font(9f, color = GRAY_TEXT)))
        valueCell.border = Rectangle.NO_BORDER
        valueCell.paddingBottom = 4f
        table.addCell(valueCell)
    }

    private fun addResultsTable(document: Document, data: Map<String, Any?>) {
        document.add(Paragraph("Resultado da Consulta", font(12f, bold = true, color = PRIMARY)))
        document.add(Paragraph(" "))

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
        val cell = PdfPCell(Phrase(text, font(9f, bold = true, color = Color.WHITE)))
        cell.backgroundColor = PRIMARY
        cell.setPadding(8f)
        cell.border = Rectangle.NO_BORDER
        table.addCell(cell)
    }

    private fun addFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        val footerTable = PdfPTable(1)
        footerTable.widthPercentage = 100f
        val cell = PdfPCell(Phrase(
            "Documento gerado automaticamente pelo sistema ND Consultas Veiculares. " +
                "As informacoes contidas neste relatorio sao provenientes de fontes publicas e oficiais.",
            font(7f, color = GRAY_TEXT)
        ))
        cell.border = Rectangle.TOP
        cell.borderColor = BORDER_COLOR
        cell.borderWidth = 1f
        cell.paddingTop = 8f
        cell.horizontalAlignment = Element.ALIGN_CENTER
        footerTable.addCell(cell)

        document.add(footerTable)
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
}
