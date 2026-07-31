package com.ndconsultas.bot_whatsapp.whatsapp_gateway.command

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.BotStats
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.WhatsappService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CommandProcessor(
    private val registry: CommandRegistry,
    private val whatsappService: WhatsappService,
    private val stats: BotStats
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandProcessor::class.java)
        private const val PREFIX = "/"
    }

    fun isCommand(text: String): Boolean = text.trim().startsWith(PREFIX)

    fun process(context: CommandContext): Boolean {
        if (!isCommand(context.rawMessage)) return false

        val parts = context.rawMessage.trim().split("\\s+".toRegex())
        val commandName = parts[0]
        val args = parts.drop(1)

        val command = registry.findCommand(commandName)
        if (command == null) {
            whatsappService.sendMessage(
                context.from,
                "Comando nao encontrado: *$commandName*\nDigite /help para ver os comandos disponiveis."
            )
            return true
        }

        log.info("Executing command [{}] from {} ({})", commandName, context.senderName, context.from)
        stats.incrementCommands()

        try {
            command.execute(context.copy(args = args), whatsappService)
        } catch (e: Exception) {
            log.error("Error executing command {}: {}", commandName, e.message, e)
            stats.incrementErrors()
            whatsappService.sendMessage(
                context.from,
                "Ocorreu um erro ao executar o comando *$commandName*. Tente novamente."
            )
        }
        return true
    }
}
