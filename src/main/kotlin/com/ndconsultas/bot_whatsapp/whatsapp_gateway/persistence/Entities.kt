package com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "bot_banned_numbers")
class BannedNumberEntity(
    @Id val phone: String = ""
)

@Entity
@Table(name = "bot_module_prices")
class ModulePriceEntity(
    @Id val tipo: String = "",
    val price: BigDecimal = BigDecimal.ZERO
)

@Entity
@Table(name = "bot_disabled_modules")
class DisabledModuleEntity(
    @Id val tipo: String = ""
)

@Entity
@Table(name = "bot_admin_numbers")
class AdminNumberEntity(
    @Id val phone: String = ""
)

@Entity
@Table(name = "bot_settings")
class BotSettingEntity(
    @Id val key: String = "",
    val value: String = ""
)

@Entity
@Table(name = "bot_scheduled_crlv_orders")
class ScheduledCrlvOrderEntity(
    @Id val pedidoId: Long = 0,
    val userPhone: String = "",
    val uf: String = "",
    val placa: String = "",
    val renavam: String? = null,
    val cpf: String? = null,
    val status: String = "PENDING",
    val adminMessage: String? = null,
    val failCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
