package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.stereotype.Component

@Component
class QueryTypeRegistry {

    data class QueryTypeInfo(
        val label: String,
        val inputPrompt: String,
        val category: String,
        val description: String,
        val returnSummary: String,
        val returnDetails: String
    )

    data class CategoryInfo(
        val label: String,
        val description: String,
        val count: Int
    )

    companion object {
        val CRLV_FULL_INPUT_STATES = setOf(
            "crlv_ac", "crlv_ap", "crlv_ba", "crlv_go", "crlv_ma",
            "crlv_mg", "crlv_mt", "crlv_pa", "crlv_pr", "crlv_rr", "crlv_to"
        )

        val PORTAL_DESPACHANTES_TYPES: Set<String> by lazy {
            CRLV_STATES.keys + setOf("crv_codigo", "crv_digital_cod")
        }

        val CRLV_AGENDADO_STATES = linkedMapOf(
            "crlvag_al" to "Alagoas",
            "crlvag_ce" to "Ceará",
            "crlvag_df" to "Distrito Federal",
            "crlvag_es" to "Espírito Santo",
            "crlvag_pb" to "Paraíba",
            "crlvag_pe" to "Pernambuco",
            "crlvag_rj" to "Rio de Janeiro",
            "crlvag_rn" to "Rio Grande do Norte",
            "crlvag_sc" to "Santa Catarina"
        )

        val CRLV_AGENDADO_FULL_INPUT_STATES = setOf("crlvag_pb", "crlvag_rn")

        val CRLV_STATES = linkedMapOf(
            "crlv_ac" to "Acre",
            "crlv_al" to "Alagoas",
            "crlv_ap" to "Amapá",
            "crlv_ba" to "Bahia",
            "crlv_ce" to "Ceará",
            "crlv_df" to "Distrito Federal",
            "crlv_go" to "Goiás",
            "crlv_ma" to "Maranhão",
            "crlv_mg" to "Minas Gerais",
            "crlv_ms" to "Mato Grosso do Sul",
            "crlv_mt" to "Mato Grosso",
            "crlv_pa" to "Pará",
            "crlv_pe" to "Pernambuco",
            "crlv_pi" to "Piauí",
            "crlv_pr" to "Paraná",
            "crlv_rj" to "Rio de Janeiro",
            "crlv_ro" to "Rondônia",
            "crlv_rr" to "Roraima",
            "crlv_se" to "Sergipe",
            "crlv_sp" to "São Paulo",
            "crlv_to" to "Tocantins"
        )

        data class CrlvRegion(val label: String, val states: List<String>)

        val CRLV_REGIONS = linkedMapOf(
            "norte" to CrlvRegion("Norte", listOf("crlv_ac", "crlv_ap", "crlv_pa", "crlv_ro", "crlv_rr", "crlv_to")),
            "nordeste" to CrlvRegion("Nordeste", listOf("crlv_al", "crlv_ba", "crlv_ce", "crlv_ma", "crlv_pe", "crlv_pi", "crlv_se")),
            "centro_oeste" to CrlvRegion("Centro-Oeste", listOf("crlv_df", "crlv_go", "crlv_ms", "crlv_mt")),
            "sudeste_sul" to CrlvRegion("Sudeste e Sul", listOf("crlv_mg", "crlv_pr", "crlv_rj", "crlv_sp"))
        )
    }

    val types = mapOf(
        // ── Consultas Veiculares ────────────────────────────────────
        "placa_serpro" to QueryTypeInfo(
            label = "Placa SERPRO",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Consulta por placa direto no SERPRO (governo federal).",
            returnSummary = "Veiculo, Proprietario, Restricoes, Venda",
            returnDetails = "RENAVAM, Placa, Chassi, Motor, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Combustivel, Potencia, Cilindradas, Capacidade de Passageiros, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Restricoes, Comunicado de Venda, Intencao de Venda."
        ),
        "bin_chassi" to QueryTypeInfo(
            label = "BIN Chassi",
            inputPrompt = "Informe o *numero do chassi*",
            category = "veicular",
            description = "Consulta BIN por numero de chassi.",
            returnSummary = "Veiculo, Proprietario, Situacao",
            returnDetails = "Placa, Chassi, Motor, RENAVAM, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Carroceria, Combustivel, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Data da Atualizacao."
        ),
        "bin_motor" to QueryTypeInfo(
            label = "BIN Motor",
            inputPrompt = "Informe o *numero do motor*",
            category = "veicular",
            description = "Consulta BIN por numero do motor.",
            returnSummary = "Veiculo, Proprietario, Situacao",
            returnDetails = "Placa, Chassi, Motor, RENAVAM, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Carroceria, Combustivel, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Data da Atualizacao."
        ),
        "bin_renavam" to QueryTypeInfo(
            label = "BIN Renavam",
            inputPrompt = "Informe o *numero do RENAVAM*",
            category = "veicular",
            description = "Consulta BIN por RENAVAM.",
            returnSummary = "Veiculo, Proprietario, Situacao",
            returnDetails = "Placa, Chassi, Motor, RENAVAM, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Carroceria, Combustivel, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Data da Atualizacao."
        ),
        "multas_senatran" to QueryTypeInfo(
            label = "Multas SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Lista todas as multas registradas no SENATRAN.",
            returnSummary = "Multas, Valores, Pontuacao",
            returnDetails = "Auto de Infracao, Data da Infracao, Descricao da Infracao, Codigo da Infracao, Local, Valor da Multa, Status do Pagamento, Pontuacao, Orgao Autuador."
        ),
        "ocorrencias_senatran" to QueryTypeInfo(
            label = "Ocorrencias SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Consulta ocorrencias registradas no SENATRAN.",
            returnSummary = "Furto, Roubo, Sinistro, B.O.",
            returnDetails = "Tipo de Ocorrencia (Furto/Roubo/Sinistro), Data da Ocorrencia, Boletim de Ocorrencia, Delegacia, Municipio, UF, Situacao."
        ),
        "renajud_senatran" to QueryTypeInfo(
            label = "Renajud SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Consulta restricoes judiciais (RENAJUD).",
            returnSummary = "Bloqueio, Penhora, Impedimento",
            returnDetails = "Tipo de Restricao, Tribunal, Vara, Numero do Processo, Data da Inclusao, Detalhes do Bloqueio/Penhora/Impedimento."
        ),

        // ── Consultas Pessoais ──────────────────────────────────────
        "telefone_full" to QueryTypeInfo(
            label = "Telefone Full",
            inputPrompt = "Informe o *numero de telefone* (com DDD)",
            category = "pessoal",
            description = "Consulta completa por numero de telefone.",
            returnSummary = "Nome, CPF, Endereco, Operadora",
            returnDetails = "Nome Completo, CPF, Data de Nascimento, Sexo, Endereco, Bairro, Cidade, UF, CEP, Operadora, Tipo de Linha, Portabilidade."
        ),
        "cpf_full" to QueryTypeInfo(
            label = "CPF Full",
            inputPrompt = "Informe o *CPF*",
            category = "pessoal",
            description = "Consulta completa por CPF.",
            returnSummary = "Nome, Endereco, Telefone, Renda",
            returnDetails = "Nome Completo, CPF, Data de Nascimento, Sexo, Nome da Mae, Situacao Cadastral, Endereco, Bairro, Cidade, UF, CEP, Telefones, E-mail, Renda Presumida, Escolaridade, Obito."
        ),

        // ── Busca Leilao e Sinistro ─────────────────────────────────
        "leilao_completo_score" to QueryTypeInfo(
            label = "Leilao Completo + Score",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "leilao",
            description = "Consulta completa de leilao e sinistro com score.",
            returnSummary = "Score, Leilao, Sinistro, Veiculo",
            returnDetails = "Score do Veiculo, Pontuacao, Aceitacao, Historico de Leilao, Leiloeiro, Data do Leilao, Lote, Comitente, Patio, Condicao Geral, Condicao Motor, Condicao Cambio, Situacao do Chassi, Indicios de Sinistro, Dados do Veiculo (Placa, Chassi, RENAVAM, Marca/Modelo, Ano, Cor, Kilometragem, Combustivel)."
        ),

        // ── Documentos Digitais — Meta-módulo CRLV ──────────────────
        "crlv_digital" to QueryTypeInfo(
            label = "CRLV Digital (CRLV-e)",
            inputPrompt = "",
            category = "documentos",
            description = "Emissão do CRLV-e (Certificado de Registro e Licenciamento do Veículo eletrônico).",
            returnSummary = "PDF do CRLV-e",
            returnDetails = "Documento CRLV-e em PDF do estado selecionado."
        ),

        // ── Documentos Digitais — CRV ───────────────────────────────
        "crv_codigo" to QueryTypeInfo(
            label = "Código Segurança CRV",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "documentos",
            description = "Consulta o código de segurança do CRV.",
            returnSummary = "PDF com Código CRV",
            returnDetails = "Documento PDF com o código de segurança do CRV."
        ),
        "crv_digital_cod" to QueryTypeInfo(
            label = "CRV Digital + Código",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "documentos",
            description = "CRV Digital completo com código de segurança (JSON + PDF via API).",
            returnSummary = "PDF CRV Digital + Código",
            returnDetails = "Documento PDF do CRV Digital com código de segurança."
        ),

        // ── CRLV Agendado (meta-módulo) ────────────────────────────────
        "crlv_agendado" to QueryTypeInfo(
            label = "Solicitar CRLV-e",
            inputPrompt = "",
            category = "documentos_agendados",
            description = "Solicita CRLV-e agendado. O documento é emitido e enviado quando estiver pronto.",
            returnSummary = "PDF do CRLV-e (entrega posterior)",
            returnDetails = "Documento CRLV-e em PDF — processamento pode levar algumas horas."
        ),
        "status_agendado" to QueryTypeInfo(
            label = "Ver Status de Pedidos",
            inputPrompt = "",
            category = "documentos_agendados",
            description = "Consulte o status dos seus pedidos de CRLV-e agendado.",
            returnSummary = "Status dos pedidos",
            returnDetails = "Lista de pedidos agendados com status atualizado."
        ),

        // ── CRLV por estado (ocultos — acessados via seleção de estado) ──
        *buildCrlvStateTypes().toList().toTypedArray(),

        // ── CRLV agendado por estado (ocultos) ─────────────────────────
        *buildCrlvAgendadoStateTypes().toList().toTypedArray()
    )

    val categories = linkedMapOf(
        "veicular" to CategoryInfo("Consultas Veiculares", "Placa, Chassi, Motor, Renavam, Multas e mais", 7),
        "pessoal" to CategoryInfo("Consultas Pessoais", "Telefone, CPF", 2),
        "leilao" to CategoryInfo("Busca Leilão e Sinistro", "Leilão completo com score", 1),
        "documentos" to CategoryInfo("CRLV-e Imediato", "CRLV-e, CRV — entrega instantânea", 3),
        "documentos_agendados" to CategoryInfo("CRLV-e Agendado", "Solicite e receba quando pronto", 2)
    )

    fun getTypeLabel(tipo: String): String = types[tipo]?.label ?: tipo

    fun getTypeInfo(tipo: String): QueryTypeInfo? = types[tipo]

    fun getTypesForCategory(category: String): Map<String, QueryTypeInfo> =
        types.filter { it.value.category == category }

    fun isCrlvState(tipo: String): Boolean = tipo in CRLV_STATES

    fun isPortalDespachantesType(tipo: String): Boolean = tipo in PORTAL_DESPACHANTES_TYPES

    fun isAgendadoType(tipo: String): Boolean = tipo.startsWith("crlvag_")

    fun isCrlvAgendadoState(tipo: String): Boolean = tipo in CRLV_AGENDADO_STATES
}

private fun buildCrlvStateTypes(): Map<String, QueryTypeRegistry.QueryTypeInfo> {
    return QueryTypeRegistry.CRLV_STATES.map { (key, stateName) ->
        key to QueryTypeRegistry.QueryTypeInfo(
            label = "CRLV $stateName",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "_crlv",
            description = "Emissão do CRLV-e para o estado de $stateName.",
            returnSummary = "PDF do CRLV-e",
            returnDetails = "Documento CRLV-e em PDF."
        )
    }.toMap()
}

private fun buildCrlvAgendadoStateTypes(): Map<String, QueryTypeRegistry.QueryTypeInfo> {
    return QueryTypeRegistry.CRLV_AGENDADO_STATES.map { (key, stateName) ->
        key to QueryTypeRegistry.QueryTypeInfo(
            label = "CRLV Agendado $stateName",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "_crlvag",
            description = "Solicitacao de CRLV-e agendado para $stateName.",
            returnSummary = "PDF do CRLV-e (entrega posterior)",
            returnDetails = "Documento CRLV-e em PDF — processamento pode levar algumas horas."
        )
    }.toMap()
}
