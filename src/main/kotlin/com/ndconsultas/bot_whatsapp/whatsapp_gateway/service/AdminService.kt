package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence.ConfigPersistenceService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service
class AdminService(
    @Value("\${admin.phone-number:}") private val adminPhoneNumber: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(AdminService::class.java)
    }

    // @Lazy para evitar dependência circular com ConfigPersistenceService
    @Autowired @Lazy
    private lateinit var persistence: ConfigPersistenceService

    private val bannedNumbers = ConcurrentHashMap.newKeySet<String>()
    private val botBlocked = AtomicBoolean(false)
    private val pendingActions = ConcurrentHashMap<String, String>()

    // ── Verificação de admin ────────────────────────────────────────

    fun isAdmin(phoneNumber: String): Boolean =
        adminPhoneNumber.isNotBlank() && phoneNumber == adminPhoneNumber

    fun isAdminConfigured(): Boolean = adminPhoneNumber.isNotBlank()

    // ── Ban ─────────────────────────────────────────────────────────

    fun banNumber(number: String): BanResult {
        val normalized = normalizeNumber(number)
        val variants = brVariants(normalized)

        if (variants.any { it == adminPhoneNumber }) {
            log.warn("Tentativa de banir o número admin bloqueada")
            return BanResult(false, emptySet(), "admin")
        }

        val added = mutableSetOf<String>()
        variants.forEach { v ->
            if (bannedNumbers.add(v)) added.add(v)
        }

        if (added.isNotEmpty()) {
            log.info("Números banidos: {}", added)
            persistence.saveBannedNumbers(added)
        }
        return BanResult(added.isNotEmpty(), variants, "ok")
    }

    fun unbanNumber(number: String): Set<String> {
        val normalized = normalizeNumber(number)
        val variants = brVariants(normalized)

        val removed = mutableSetOf<String>()
        variants.forEach { v ->
            if (bannedNumbers.remove(v)) removed.add(v)
        }

        if (removed.isNotEmpty()) {
            log.info("Números desbanidos: {}", removed)
            persistence.deleteBannedNumbers(removed)
        }
        return removed
    }

    fun isBanned(number: String): Boolean {
        val normalized = normalizeNumber(number)
        return brVariants(normalized).any { bannedNumbers.contains(it) }
    }

    fun getBannedNumbers(): Set<String> = bannedNumbers.toSet()

    /**
     * Retorna apenas uma versão por número (deduplica variantes BR).
     * Usado para exibição ao admin — internamente ambas variantes permanecem banidas.
     */
    fun getUniqueBannedNumbers(): Set<String> {
        val seen = mutableSetOf<String>()
        val unique = mutableSetOf<String>()
        for (number in bannedNumbers) {
            if (number !in seen) {
                unique.add(number)
                seen.addAll(brVariants(number))
            }
        }
        return unique
    }

    fun getBannedCount(): Int = getUniqueBannedNumbers().size

    /** Chamado apenas pelo ConfigPersistenceService no startup — não dispara save. */
    fun loadBannedNumber(phone: String) {
        bannedNumbers.add(phone)
    }

    // ── Block ───────────────────────────────────────────────────────

    fun blockBot() {
        botBlocked.set(true)
        log.info("Bot bloqueado para consultas")
        persistence.saveBotBlocked(true)
    }

    fun unblockBot() {
        botBlocked.set(false)
        log.info("Bot desbloqueado para consultas")
        persistence.saveBotBlocked(false)
    }

    fun isBotBlocked(): Boolean = botBlocked.get()

    /** Chamado apenas pelo ConfigPersistenceService no startup — não dispara save. */
    fun loadBotBlocked() {
        botBlocked.set(true)
    }

    // ── Pending admin actions ───────────────────────────────────────

    fun setPendingAction(userId: String, action: String) {
        pendingActions[userId] = action
    }

    fun consumePendingAction(userId: String): String? = pendingActions.remove(userId)

    fun clearPendingAction(userId: String) {
        pendingActions.remove(userId)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun normalizeNumber(number: String): String =
        number.replace(Regex("[^0-9]"), "")

    /**
     * Gera variantes BR de um número: com e sem o 9 após o DDD.
     * 5581999536361 (com 9) ↔ 558199536361 (sem 9)
     */
    private fun brVariants(number: String): Set<String> {
        val variants = mutableSetOf(number)

        if (!number.startsWith("55") || number.length < 12) return variants

        val ddd = number.substring(2, 4)
        val rest = number.substring(4)

        when (number.length) {
            13 -> if (rest.startsWith("9")) variants.add("55${ddd}${rest.substring(1)}")
            12 -> variants.add("55${ddd}9${rest}")
        }

        return variants
    }

    data class BanResult(
        val success: Boolean,
        val variants: Set<String>,
        val reason: String
    )
}
