package com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface BannedNumberRepository : JpaRepository<BannedNumberEntity, String>

interface ModulePriceRepository : JpaRepository<ModulePriceEntity, String>

interface DisabledModuleRepository : JpaRepository<DisabledModuleEntity, String>

interface AdminNumberRepository : JpaRepository<AdminNumberEntity, String>

interface BotSettingRepository : JpaRepository<BotSettingEntity, String>
