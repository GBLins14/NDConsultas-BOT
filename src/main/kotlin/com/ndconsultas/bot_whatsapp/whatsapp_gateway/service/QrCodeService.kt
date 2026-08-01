package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class QrCodeService {

    fun generate(text: String, size: Int = 400): ByteArray {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)

        val output = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", output)
        return output.toByteArray()
    }
}
