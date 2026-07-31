package com.ndconsultas.bot_whatsapp.whatsapp_gateway.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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

    private val bannedNumbers = ConcurrentHashMap.newKeySet<String>()
    private val botBlocked = AtomicBoolean(false)
    private val pendingActions = ConcurrentHashMap<String, String>()

    // ── Verificacao de admin ────────────────────────────────────────
    // O campo 'from' vem do webhook do WhatsApp (servidor Meta),
    // NAO do input do usuario. E impossivel falsificar.

    fun isAdmin(phoneNumber: String): Boolean {
        return adminPhoneNumber.isNotBlank() && phoneNumber == adminPhoneNumber
    }

    fun isAdminConfigured(): Boolean = adminPhoneNumber.isNotBlank()

    // ── Ban ─────────────────────────────────────────────────────────
    // Numeros BR podem vir com ou sem o 9 apos o DDD.
    // Ex: 5581999536361 (com 9) e 558199536361 (sem 9).
    // Sempre bane as duas variantes para garantir o bloqueio.

    fun banNumber(number: String): BanResult {
        val normalized = normalizeNumber(number)
        val variants = brVariants(normalized)

        if (variants.any { it == adminPhoneNumber }) {
            log.warn("Tentativa de banir o numero admin bloqueada")
            return BanResult(false, emptySet(), "admin")
        }

        val added = mutableSetOf<String>()
        variants.forEach { v ->
            if (bannedNumbers.add(v)) added.add(v)
        }

        if (added.isNotEmpty()) log.info("Numeros banidos: {}", added)
        return BanResult(added.isNotEmpty(), variants, "ok")
    }

    fun unbanNumber(number: String): Set<String> {
        val normalized = normalizeNumber(number)
        val variants = brVariants(normalized)

        val removed = mutableSetOf<String>()
        variants.forEach { v ->
            if (bannedNumbers.remove(v)) removed.add(v)
        }

        if (removed.isNotEmpty()) log.info("Numeros desbanidos: {}", removed)
        return removed
    }

    fun isBanned(number: String): Boolean {
        val normalized = normalizeNumber(number)
        return brVariants(normalized).any { bannedNumbers.contains(it) }
    }

    fun getBannedNumbers(): Set<String> = bannedNumbers.toSet()

    fun getBannedCount(): Int = bannedNumbers.size

    // ── Block ───────────────────────────────────────────────────────

    fun blockBot() {
        botBlocked.set(true)
        log.info("Bot bloqueado para consultas")
    }

    fun unblockBot() {
        botBlocked.set(false)
        log.info("Bot desbloqueado para consultas")
    }

    fun isBotBlocked(): Boolean = botBlocked.get()

    // ── Pending admin actions ───────────────────────────────────────

    fun setPendingAction(userId: String, action: String) {
        pendingActions[userId] = action
    }

    fun consumePendingAction(userId: String): String? {
        return pendingActions.remove(userId)
    }

    fun clearPendingAction(userId: String) {
        pendingActions.remove(userId)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }

    /**
     * Gera variantes BR de um numero: com e sem o 9 apos o DDD.
     * Formato BR: 55 + DDD(2 digitos) + numero(8 ou 9 digitos)
     *
     * 5581 9 99536361 (13 digitos, com 9) → tambem gera 558199536361 (12 digitos, sem 9)
     * 5581 99536361   (12 digitos, sem 9) → tambem gera 5581999536361 (13 digitos, com 9)
     */
    private fun brVariants(number: String): Set<String> {
        val variants = mutableSetOf(number)

        if (!number.startsWith("55") || number.length < 12) return variants

        val ddd = number.substring(2, 4)
        val rest = number.substring(4)

        when (number.length) {
            13 -> {
                // Com 9: remover o 9 apos DDD
                if (rest.startsWith("9")) {
                    variants.add("55${ddd}${rest.substring(1)}")
                }
            }
            12 -> {
                // Sem 9: adicionar o 9 apos DDD
                variants.add("55${ddd}9${rest}")
            }
        }

        return variants
    }

    data class BanResult(
        val success: Boolean,
        val variants: Set<String>,
        val reason: String
    )
}
