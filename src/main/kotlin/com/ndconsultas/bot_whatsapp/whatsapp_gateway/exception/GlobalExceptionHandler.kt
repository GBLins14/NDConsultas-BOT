package com.ndconsultas.bot_whatsapp.whatsapp_gateway.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(WhatsappApiException::class)
    fun handleApiError(e: WhatsappApiException): ProblemDetail {
        log.error("WhatsApp API error: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.message ?: "Erro na API do WhatsApp").apply {
            title = "Erro na API do WhatsApp"
        }
    }

    @ExceptionHandler(WhatsappAuthException::class)
    fun handleAuthError(e: WhatsappAuthException): ProblemDetail {
        log.error("WhatsApp auth error: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message ?: "Token invalido").apply {
            title = "Erro de autenticacao"
        }
    }

    @ExceptionHandler(WhatsappRateLimitException::class)
    fun handleRateLimit(e: WhatsappRateLimitException): ProblemDetail {
        log.error("WhatsApp rate limit: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.message ?: "Limite excedido").apply {
            title = "Limite de requisicoes"
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ProblemDetail {
        log.error("Unexpected error", e)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor").apply {
            title = "Erro interno"
        }
    }
}
