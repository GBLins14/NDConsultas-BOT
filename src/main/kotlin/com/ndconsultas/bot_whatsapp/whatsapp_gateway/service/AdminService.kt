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

    fun banNumber(number: String): Boolean {
        val normalized = normalizeNumber(number)
        if (normalized == adminPhoneNumber) {
            log.warn("Tentativa de banir o numero admin bloqueada")
            return false
        }
        val added = bannedNumbers.add(normalized)
        if (added) log.info("Numero banido: {}", normalized)
        return added
    }

    fun unbanNumber(number: String): Boolean {
        val normalized = normalizeNumber(number)
        val removed = bannedNumbers.remove(normalized)
        if (removed) log.info("Numero desbanido: {}", normalized)
        return removed
    }

    fun isBanned(number: String): Boolean {
        return bannedNumbers.contains(normalizeNumber(number))
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
}
