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
        )
    )

    val categories = linkedMapOf(
        "veicular" to CategoryInfo("Consultas Veiculares", "Placa, Chassi, Motor, Renavam, Multas e mais", 7),
        "pessoal" to CategoryInfo("Consultas Pessoais", "Telefone, CPF", 2),
        "leilao" to CategoryInfo("Busca Leilão e Sinistro", "Leilão completo com score", 1)
    )

    fun getTypeLabel(tipo: String): String = types[tipo]?.label ?: tipo

    fun getTypeInfo(tipo: String): QueryTypeInfo? = types[tipo]

    fun getTypesForCategory(category: String): Map<String, QueryTypeInfo> =
        types.filter { it.value.category == category }
}
