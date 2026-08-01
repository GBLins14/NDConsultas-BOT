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
        // ── Consulta por Placa ──────────────────────────────────────
        "placa_full" to QueryTypeInfo(
            label = "Placa Full",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "placa",
            description = "Consulta completa por placa. Retorna dados do veiculo, proprietario, situacao, restricoes, recalls e historico de multas. A mais completa."
        ),
        "placa_duality" to QueryTypeInfo(
            label = "Placa Duality",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "placa",
            description = "Consulta por placa via base Duality. Retorna dados basicos do veiculo: marca, modelo, ano, cor, situacao e municipio."
        ),
        "placa_serpro" to QueryTypeInfo(
            label = "Placa SERPRO",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "placa",
            description = "Consulta por placa direto no SERPRO (Servico Federal de Processamento de Dados). Dados oficiais do governo federal."
        ),
        "placa_senatran" to QueryTypeInfo(
            label = "Placa SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "placa",
            description = "Consulta por placa via SENATRAN (antigo DENATRAN). Retorna dados do registro nacional do veiculo."
        ),
        "bin_placa" to QueryTypeInfo(
            label = "BIN Placa",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "placa",
            description = "Consulta BIN (Base de Informacoes Nacionais) por placa. Retorna dados do veiculo cadastrados na base nacional."
        ),
        "frota" to QueryTypeInfo(
            label = "Frota Veicular",
            inputPrompt = "Informe o *CPF ou CNPJ* do proprietario",
            category = "placa",
            description = "Lista todos os veiculos vinculados a um CPF ou CNPJ. Ideal para verificar frota completa de uma pessoa ou empresa."
        ),

        // ── Chassi e Motor ──────────────────────────────────────────
        "bin_chassi" to QueryTypeInfo(
            label = "BIN Chassi",
            inputPrompt = "Informe o *numero do chassi*",
            category = "chassi",
            description = "Consulta BIN por numero de chassi. Localiza o veiculo pela identificacao unica de fabrica."
        ),
        "chassi_serpro" to QueryTypeInfo(
            label = "Chassi SERPRO",
            inputPrompt = "Informe o *numero do chassi*",
            category = "chassi",
            description = "Consulta por chassi direto no SERPRO. Dados oficiais do governo para verificacao de chassi."
        ),
        "chassi_senatran" to QueryTypeInfo(
            label = "Chassi SENATRAN",
            inputPrompt = "Informe o *numero do chassi*",
            category = "chassi",
            description = "Consulta por chassi via SENATRAN. Verifica se o chassi esta regular nos registros nacionais."
        ),
        "bin_motor" to QueryTypeInfo(
            label = "BIN Motor",
            inputPrompt = "Informe o *numero do motor*",
            category = "chassi",
            description = "Consulta BIN por numero do motor. Identifica o veiculo pela numeracao do motor."
        ),
        "motor_senatran" to QueryTypeInfo(
            label = "Motor SENATRAN",
            inputPrompt = "Informe o *numero do motor*",
            category = "chassi",
            description = "Consulta por numero do motor via SENATRAN. Verificacao oficial do motor nos registros nacionais."
        ),

        // ── Renavam e CNH ───────────────────────────────────────────
        "bin_renavam" to QueryTypeInfo(
            label = "BIN Renavam",
            inputPrompt = "Informe o *numero do RENAVAM*",
            category = "renavam",
            description = "Consulta BIN por RENAVAM (Registro Nacional de Veiculos Automotores). Localiza o veiculo pelo numero do documento."
        ),
        "renavam_serpro" to QueryTypeInfo(
            label = "Renavam SERPRO",
            inputPrompt = "Informe o *numero do RENAVAM*",
            category = "renavam",
            description = "Consulta por RENAVAM direto no SERPRO. Dados oficiais do registro do veiculo."
        ),
        "cnh_full" to QueryTypeInfo(
            label = "CNH Full",
            inputPrompt = "Informe o *CPF* do condutor",
            category = "renavam",
            description = "Consulta completa da CNH por CPF. Retorna dados da habilitacao: categoria, validade, pontuacao, restricoes e situacao."
        ),
        "cnh_serpro" to QueryTypeInfo(
            label = "CNH SERPRO",
            inputPrompt = "Informe o *CPF* do condutor",
            category = "renavam",
            description = "Consulta da CNH por CPF via SERPRO. Dados oficiais da habilitacao direto do governo federal."
        ),

        // ── Laudos Veiculares ───────────────────────────────────────
        "laudo_veicular" to QueryTypeInfo(
            label = "Laudo Veicular",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "laudo",
            description = "Consulta de laudo de vistoria veicular por placa. Retorna informacoes da ultima vistoria realizada no veiculo."
        ),
        "laudo_veicular_id" to QueryTypeInfo(
            label = "Laudo por ID",
            inputPrompt = "Informe o *ID do laudo*",
            category = "laudo",
            description = "Busca um laudo de vistoria especifico pelo seu ID. Use quando ja tiver o numero do laudo em maos."
        ),

        // ── SENATRAN Avancado ───────────────────────────────────────
        "multas_senatran" to QueryTypeInfo(
            label = "Multas SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "senatran",
            description = "Lista todas as multas registradas no SENATRAN para o veiculo. Inclui valor, data, local e status de cada multa."
        ),
        "ocorrencias_senatran" to QueryTypeInfo(
            label = "Ocorrencias SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "senatran",
            description = "Consulta ocorrencias registradas no SENATRAN. Inclui furtos, roubos, sinistros e outras anotacoes oficiais."
        ),
        "recall_senatran" to QueryTypeInfo(
            label = "Recall SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "senatran",
            description = "Verifica se o veiculo possui recalls pendentes. Recalls sao chamadas das montadoras para corrigir defeitos de fabrica."
        ),
        "renajud_senatran" to QueryTypeInfo(
            label = "Renajud SENATRAN",
            inputPrompt = "Informe a *placa* do veiculo",
            category = "senatran",
            description = "Consulta restricoes judiciais (RENAJUD) do veiculo. Verifica se ha bloqueios, penhoras ou impedimentos determinados pela justica."
        )
    )

    val categories = linkedMapOf(
        "placa" to CategoryInfo("Consulta por Placa", "Placa, BIN e Frota", 6),
        "chassi" to CategoryInfo("Chassi e Motor", "Chassi e Motor", 5),
        "renavam" to CategoryInfo("Renavam e CNH", "Renavam e CNH", 4),
        "laudo" to CategoryInfo("Laudos Veiculares", "Laudo e Laudo por ID", 2),
        "senatran" to CategoryInfo("SENATRAN Avancado", "Multas, Recall e mais", 4)
    )

    fun getTypeLabel(tipo: String): String = types[tipo]?.label ?: tipo

    fun getTypeInfo(tipo: String): QueryTypeInfo? = types[tipo]

    fun getTypesForCategory(category: String): Map<String, QueryTypeInfo> =
        types.filter { it.value.category == category }
}
