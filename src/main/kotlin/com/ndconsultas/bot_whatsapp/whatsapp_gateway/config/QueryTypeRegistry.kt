package com.ndconsultas.bot_whatsapp.whatsapp_gateway.config

import org.springframework.stereotype.Component

@Component
class QueryTypeRegistry {

    data class QueryTypeInfo(
        val label: String,
        val inputPrompt: String,
        val category: String,
        val description: String
    )

    data class CategoryInfo(
        val label: String,
        val description: String,
        val count: Int
    )

    val types = mapOf(
        // ── Consultas Veiculares ────────────────────────────────────
        "placa_serpro" to QueryTypeInfo(
            label = "Placa SERPRO",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Consulta por placa direto no SERPRO. Retorna: RENAVAM, Placa, Chassi, Motor, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Combustivel, Potencia, Cilindradas, Capacidade de Passageiros, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Restricoes, Comunicado de Venda, Intencao de Venda."
        ),
        "bin_chassi" to QueryTypeInfo(
            label = "BIN Chassi",
            inputPrompt = "Informe o *numero do chassi*",
            category = "veicular",
            description = "Consulta BIN por numero de chassi. Retorna: Placa, Chassi, Motor, RENAVAM, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Carroceria, Combustivel, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Data da Atualizacao."
        ),
        "bin_motor" to QueryTypeInfo(
            label = "BIN Motor",
            inputPrompt = "Informe o *numero do motor*",
            category = "veicular",
            description = "Consulta BIN por numero do motor. Retorna: Placa, Chassi, Motor, RENAVAM, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Carroceria, Combustivel, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Data da Atualizacao."
        ),
        "bin_renavam" to QueryTypeInfo(
            label = "BIN Renavam",
            inputPrompt = "Informe o *numero do RENAVAM*",
            category = "veicular",
            description = "Consulta BIN por RENAVAM. Retorna: Placa, Chassi, Motor, RENAVAM, Marca/Modelo, Ano Fabricacao, Ano Modelo, Cor, Tipo, Especie, Carroceria, Combustivel, Municipio, UF, Situacao do Veiculo, Proprietario, CPF/CNPJ, Data da Atualizacao."
        ),
        "multas_senatran" to QueryTypeInfo(
            label = "Multas SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Lista multas registradas no SENATRAN. Retorna: Auto de Infracao, Data da Infracao, Descricao da Infracao, Codigo da Infracao, Local, Valor da Multa, Status do Pagamento, Pontuacao, Orgao Autuador."
        ),
        "ocorrencias_senatran" to QueryTypeInfo(
            label = "Ocorrencias SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Consulta ocorrencias registradas no SENATRAN. Retorna: Tipo de Ocorrencia (Furto/Roubo/Sinistro), Data da Ocorrencia, Boletim de Ocorrencia, Delegacia, Municipio, UF, Situacao."
        ),
        "renajud_senatran" to QueryTypeInfo(
            label = "Renajud SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "veicular",
            description = "Consulta restricoes judiciais (RENAJUD). Retorna: Tipo de Restricao, Tribunal, Vara, Numero do Processo, Data da Inclusao, Detalhes do Bloqueio/Penhora/Impedimento."
        ),

        // ── Busca Leilao e Sinistro ─────────────────────────────────
        "leilao_completo_score" to QueryTypeInfo(
            label = "Leilão Completo + Score",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "leilao",
            description = "Consulta completa de leilao e sinistro com score via API Brasil. Retorna: Score do Veiculo, Historico de Leilao, Leiloeiro, Data do Leilao, Lote, Condicao do Veiculo, Situacao do Chassi, Tipo de Sinistro, Indicios de Sinistro, Situacao do Veiculo, Restricoes, Dados do Veiculo (Placa, Chassi, RENAVAM, Marca/Modelo, Ano, Cor, Municipio, UF)."
        )
    )

    val categories = linkedMapOf(
        "veicular" to CategoryInfo("Consultas Veiculares", "Placa, Chassi, Motor, Renavam, Multas e mais", 7),
        "leilao" to CategoryInfo("Busca Leilão e Sinistro", "Leilão completo com score", 1)
    )

    fun getTypeLabel(tipo: String): String = types[tipo]?.label ?: tipo

    fun getTypeInfo(tipo: String): QueryTypeInfo? = types[tipo]

    fun getTypesForCategory(category: String): Map<String, QueryTypeInfo> =
        types.filter { it.value.category == category }
}
