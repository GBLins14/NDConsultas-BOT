package com.ndconsultas.bot_whatsapp.whatsapp_gateway.model

data class IncomingMessage(
    val from: String,
    val senderName: String,
    val messageId: String,
    val timestamp: Long,
    val type: MessageType,
    val text: String? = null,
    val caption: String? = null,
    val mediaId: String? = null,
    val mimeType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val locationAddress: String? = null,
    val buttonReplyId: String? = null,
    val buttonReplyTitle: String? = null,
    val listReplyId: String? = null,
    val listReplyTitle: String? = null,
    val quotedMessageId: String? = null,
    val filename: String? = null
)
