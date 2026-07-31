package com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception

open class WhatsappException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class WhatsappApiException(message: String, cause: Throwable? = null) : WhatsappException(message, cause)

class WhatsappAuthException(message: String) : WhatsappException(message)

class WhatsappRateLimitException(message: String) : WhatsappException(message)
