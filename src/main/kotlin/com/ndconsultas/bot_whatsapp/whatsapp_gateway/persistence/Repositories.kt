package com.ndconsultas.bot_whatsapp.whatsapp_gateway.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface BannedNumberRepository : JpaRepository<BannedNumberEntity, String>

interface ModulePriceRepository : JpaRepository<ModulePriceEntity, String>

interface DisabledModuleRepository : JpaRepository<DisabledModuleEntity, String>

interface BotSettingRepository : JpaRepository<BotSettingEntity, String>

interface AsaasCustomerRepository : JpaRepository<AsaasCustomerEntity, String>
