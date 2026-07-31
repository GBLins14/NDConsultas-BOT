package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService

interface BotCommand {
    val name: String
    val description: String
    val aliases: List<String> get() = emptyList()
    val showInHelp: Boolean get() = true

    fun execute(context: CommandContext, whatsappService: WhatsappService)
}

data class CommandContext(
    val from: String,
    val senderName: String,
    val messageId: String,
    val args: List<String>,
    val rawMessage: String
)
