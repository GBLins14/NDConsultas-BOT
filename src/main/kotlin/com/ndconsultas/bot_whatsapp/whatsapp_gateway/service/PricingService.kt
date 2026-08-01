package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Service
class PricingService {

    companion object {
        private val log = LoggerFactory.getLogger(PricingService::class.java)
    }

    private val prices = ConcurrentHashMap<String, BigDecimal>()
    private val disabledModules = ConcurrentHashMap.newKeySet<String>()

    // ── Precos ──────────────────────────────────────────────────────

    fun setPrice(tipo: String, price: BigDecimal) {
        prices[tipo] = price
        log.info("Preco definido: {} = R$ {}", tipo, "%.2f".format(price))
    }

    fun getPrice(tipo: String): BigDecimal = prices[tipo] ?: BigDecimal.ZERO

    fun getAllPrices(): Map<String, BigDecimal> = prices.toMap()

    fun removePrice(tipo: String): Boolean = prices.remove(tipo) != null

    fun isConfigured(tipo: String): Boolean = prices.containsKey(tipo)

    fun getConfiguredCount(): Int = prices.size

    fun clearAllPrices() {
        prices.clear()
        log.info("Todos os precos removidos")
    }

    // ── Modulos ativos/inativos ─────────────────────────────────────

    fun enableModule(tipo: String) {
        disabledModules.remove(tipo)
        log.info("Modulo ativado: {}", tipo)
    }

    fun disableModule(tipo: String) {
        disabledModules.add(tipo)
        log.info("Modulo desativado: {}", tipo)
    }

    fun isModuleEnabled(tipo: String): Boolean = !disabledModules.contains(tipo)

    fun getDisabledModules(): Set<String> = disabledModules.toSet()

    fun getEnabledCount(totalTypes: Int): Int = totalTypes - disabledModules.size

    fun getDisabledCount(): Int = disabledModules.size
}
