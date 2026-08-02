package com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

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
@Table(name = "bot_settings")
class BotSettingEntity(
    @Id val key: String = "",
    val value: String = ""
)
