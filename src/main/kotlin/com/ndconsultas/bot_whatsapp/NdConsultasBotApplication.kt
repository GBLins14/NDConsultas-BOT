package com.ndconsultas.bot_whatsapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication
class NdConsultasBotApplication

fun main(args: Array<String>) {
    runApplication<NdConsultasBotApplication>(*args)
}
