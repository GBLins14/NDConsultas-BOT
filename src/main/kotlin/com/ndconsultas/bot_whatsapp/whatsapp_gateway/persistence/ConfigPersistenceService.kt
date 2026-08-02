package com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.AdminService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PaymentService
import com.ndconsultas.bot_whatsapp.whatsapp_gateway.service.PricingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ConfigPersistenceService(
    private val bannedRepo: BannedNumberRepository,
    private val priceRepo: ModulePriceRepository,
    private val disabledRepo: DisabledModuleRepository,
    private val settingRepo: BotSettingRepository,
    private val customerRepo: AsaasCustomerRepository,
    @Lazy private val pricingService: PricingService,
    @Lazy private val adminService: AdminService,
    @Lazy private val paymentService: PaymentService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ConfigPersistenceService::class.java)
        private const val KEY_BOT_BLOCKED = "botBlocked"
    }

    // ── Carrega tudo do banco ao iniciar ───────────────────────────
    // Roda após o contexto estar pronto (evita dependência circular no startup).
    // O cold start do Neon acontece aqui — aceitável pois ocorre só uma vez.

    @EventListener(ApplicationReadyEvent::class)
    fun loadOnStartup() {
        try {
            log.info("Carregando configurações do banco...")

            priceRepo.findAll().forEach { pricingService.loadPrice(it.tipo, it.price) }
            disabledRepo.findAll().forEach { pricingService.loadDisabledModule(it.tipo) }
            bannedRepo.findAll().forEach { adminService.loadBannedNumber(it.phone) }
            customerRepo.findAll().forEach { paymentService.loadCustomer(it.phone, it.customerId) }
            settingRepo.findById(KEY_BOT_BLOCKED).ifPresent {
                if (it.value == "true") adminService.loadBotBlocked()
            }

            log.info(
                "Configurações carregadas — preços: {}, módulos desativados: {}, banidos: {}, clientes Asaas: {}",
                priceRepo.count(), disabledRepo.count(), bannedRepo.count(), customerRepo.count()
            )
        } catch (e: Exception) {
            log.error("Falha ao carregar configurações do banco (bot iniciará com valores padrão): {}", e.message)
        }
    }

    // ── Preços ─────────────────────────────────────────────────────

    @Async
    @Transactional
    fun savePrice(tipo: String, price: BigDecimal) {
        runCatching { priceRepo.save(ModulePriceEntity(tipo, price)) }
            .onFailure { log.error("Falha ao persistir preço [{}]: {}", tipo, it.message) }
    }

    @Async
    @Transactional
    fun deletePrice(tipo: String) {
        runCatching { priceRepo.deleteById(tipo) }
            .onFailure { log.error("Falha ao remover preço [{}]: {}", tipo, it.message) }
    }

    @Async
    @Transactional
    fun saveAllPrices(prices: Map<String, BigDecimal>) {
        runCatching { priceRepo.saveAll(prices.map { (tipo, price) -> ModulePriceEntity(tipo, price) }) }
            .onFailure { log.error("Falha ao persistir preços em lote: {}", it.message) }
    }

    // ── Módulos desativados ────────────────────────────────────────

    @Async
    @Transactional
    fun saveDisabledModule(tipo: String) {
        runCatching { disabledRepo.save(DisabledModuleEntity(tipo)) }
            .onFailure { log.error("Falha ao persistir módulo desativado [{}]: {}", tipo, it.message) }
    }

    @Async
    @Transactional
    fun deleteDisabledModule(tipo: String) {
        runCatching { disabledRepo.deleteById(tipo) }
            .onFailure { log.error("Falha ao remover módulo desativado [{}]: {}", tipo, it.message) }
    }

    // ── Números banidos ────────────────────────────────────────────

    @Async
    @Transactional
    fun saveBannedNumbers(phones: Set<String>) {
        runCatching { bannedRepo.saveAll(phones.map { BannedNumberEntity(it) }) }
            .onFailure { log.error("Falha ao persistir banidos: {}", it.message) }
    }

    @Async
    @Transactional
    fun deleteBannedNumbers(phones: Set<String>) {
        runCatching { bannedRepo.deleteAllById(phones) }
            .onFailure { log.error("Falha ao remover banidos: {}", it.message) }
    }

    // ── Bot bloqueado ──────────────────────────────────────────────

    @Async
    @Transactional
    fun saveBotBlocked(blocked: Boolean) {
        runCatching { settingRepo.save(BotSettingEntity(KEY_BOT_BLOCKED, blocked.toString())) }
            .onFailure { log.error("Falha ao persistir estado do bot: {}", it.message) }
    }
}
