package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.ConfigPersistenceService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Service
class PricingService {

    companion object {
        private val log = LoggerFactory.getLogger(PricingService::class.java)
    }

    // @Lazy para evitar dependência circular com ConfigPersistenceService
    @Autowired @Lazy
    private lateinit var persistence: ConfigPersistenceService

    private val prices = ConcurrentHashMap<String, BigDecimal>()
    private val disabledModules = ConcurrentHashMap.newKeySet<String>()

    // ── Preços ──────────────────────────────────────────────────────

    fun setPrice(tipo: String, price: BigDecimal) {
        prices[tipo] = price
        log.info("Preço definido: {} = R$ {}", tipo, "%.2f".format(price))
        persistence.savePrice(tipo, price)
    }

    fun setDefaultPrice(price: BigDecimal, tipos: Set<String>) {
        tipos.forEach { prices[it] = price }
        log.info("Preço padrão R$ {} aplicado a {} módulos", "%.2f".format(price), tipos.size)
        persistence.saveAllPrices(tipos.associateWith { price })
    }

    /** Chamado apenas pelo ConfigPersistenceService no startup — não dispara save. */
    fun loadPrice(tipo: String, price: BigDecimal) {
        prices[tipo] = price
    }

    fun getPrice(tipo: String): BigDecimal = prices[tipo] ?: BigDecimal.ZERO

    fun getAllPrices(): Map<String, BigDecimal> = prices.toMap()

    fun removePrice(tipo: String): Boolean {
        val removed = prices.remove(tipo) != null
        if (removed) persistence.deletePrice(tipo)
        return removed
    }

    fun isConfigured(tipo: String): Boolean = prices.containsKey(tipo)

    fun getConfiguredCount(): Int = prices.size

    fun clearAllPrices() {
        val tipos = prices.keys.toSet()
        prices.clear()
        log.info("Todos os preços removidos")
        tipos.forEach { persistence.deletePrice(it) }
    }

    // ── Módulos ativos/inativos ─────────────────────────────────────

    fun enableModule(tipo: String) {
        disabledModules.remove(tipo)
        log.info("Módulo ativado: {}", tipo)
        persistence.deleteDisabledModule(tipo)
    }

    fun disableModule(tipo: String) {
        disabledModules.add(tipo)
        log.info("Módulo desativado: {}", tipo)
        persistence.saveDisabledModule(tipo)
    }

    /** Chamado apenas pelo ConfigPersistenceService no startup — não dispara save. */
    fun loadDisabledModule(tipo: String) {
        disabledModules.add(tipo)
    }

    fun isModuleEnabled(tipo: String): Boolean = !disabledModules.contains(tipo)

    fun getDisabledModules(): Set<String> = disabledModules.toSet()

    fun getEnabledCount(totalTypes: Int): Int = totalTypes - disabledModules.size

    fun getDisabledCount(): Int = disabledModules.size
}
