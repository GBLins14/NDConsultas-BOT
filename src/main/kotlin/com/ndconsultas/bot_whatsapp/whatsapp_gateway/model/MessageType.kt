package com.ndconsultas.bot_whatsapp.whatsapp_gateway.model

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    STICKER,
    LOCATION,
    CONTACTS,
    INTERACTIVE,
    BUTTON,
    REACTION,
    UNKNOWN;

    companion object {
        fun from(type: String?): MessageType =
            entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: UNKNOWN
    }
}
