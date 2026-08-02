# ND Consultas BOT

Bot de consultas veiculares via WhatsApp com pagamento integrado via PIX (SyncPay), geração de relatórios em PDF e painel administrativo completo.

---

## Sumário

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Stack Tecnológica](#stack-tecnológica)
- [Funcionalidades](#funcionalidades)
- [Módulos de Consulta](#módulos-de-consulta)
- [Fluxo do Usuário](#fluxo-do-usuário)
- [Fluxo de Pagamento](#fluxo-de-pagamento)
- [Painel Administrativo](#painel-administrativo)
- [API REST](#api-rest)
- [Configuração](#configuração)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Execução Local](#execução-local)
- [Deploy com Docker](#deploy-com-docker)
- [Deploy no Render](#deploy-no-render)
- [Estrutura do Projeto](#estrutura-do-projeto)

---

## Visão Geral

O **ND Consultas BOT** é um sistema de consultas veiculares automatizado que opera via WhatsApp Business Cloud API. Os usuários interagem diretamente pelo WhatsApp para consultar dados de veículos, CNH, chassi, motor, multas, recalls e mais — tudo com pagamento via PIX e entrega de resultados em PDF.

**Principais características:**

- 21 tipos de consulta veicular em 5 categorias
- Pagamento via PIX com QR Code gerado automaticamente
- Relatórios em PDF enviados como documento no WhatsApp
- Painel administrativo completo via comandos do bot
- Dashboard REST para monitoramento
- Persistência de configurações em PostgreSQL
- Split de pagamento configurável via SyncPay

---

## Arquitetura

```
┌─────────────┐      ┌──────────────────┐      ┌─────────────────┐
│  WhatsApp   │◄────►│  NDConsultas-BOT  │◄────►│  Central API    │
│  Cloud API  │      │  (Spring Boot)   │      │  (Duality)      │
└─────────────┘      └────────┬─────────┘      └─────────────────┘
                              │
                    ┌─────────┼──────────┐
                    │         │          │
              ┌─────▼──┐ ┌───▼────┐ ┌───▼──────┐
              │SyncPay │ │Postgre │ │Dashboard │
              │  API   │ │  SQL   │ │  REST    │
              └────────┘ └────────┘ └──────────┘
```

- **Modular Monolith** com Spring Modulith
- Processamento assíncrono de mensagens (`@Async`)
- Sessões em memória com `ConcurrentHashMap` + cleanup periódico
- Persistência de configurações (preços, módulos, bans) em PostgreSQL
- Operações atômicas com `computeIfPresent()` para segurança em concorrência

---

## Stack Tecnológica

| Tecnologia | Versão | Uso |
|---|---|---|
| Kotlin | 2.2.21 | Linguagem principal |
| Spring Boot | 4.0.7 | Framework backend |
| Spring Modulith | 2.0.7 | Arquitetura modular |
| Spring Data JPA | - | Persistência |
| PostgreSQL | - | Banco de dados |
| Jackson | - | Serialização JSON |
| OpenPDF | 2.0.3 | Geração de relatórios PDF |
| ZXing | 3.5.3 | Geração de QR Code (PIX) |
| Java | 17 | Runtime (Eclipse Temurin) |
| Gradle | Kotlin DSL | Build tool |
| Docker | Multi-stage | Containerização |

---

## Funcionalidades

### Para o Usuário

- Menu interativo com botões e listas no WhatsApp
- 21 tipos de consulta veicular organizados em 5 categorias
- Pagamento via PIX com QR Code e código copia-e-cola
- Relatório em PDF com os resultados da consulta
- Feedback visual com reactions (⏳ processando, ✅ sucesso, ❌ erro)
- Fallback automático para texto caso o PDF falhe

### Para o Administrador

- Ativar/desativar módulos de consulta
- Definir preços individuais ou em lote
- Banir/desbanir números de telefone
- Relatórios financeiros (faturamento, custos, lucro)
- Estatísticas de uso (top módulos, taxa de sucesso)
- Histórico de consultas recentes
- Liberação manual de pagamentos
- Bloqueio/desbloqueio do bot para todos os usuários
- Reset de contadores

---

## Módulos de Consulta

### Consulta por Placa (6 módulos)

| Código | Nome | Entrada | Descrição |
|---|---|---|---|
| `placa_full` | Placa Full | Placa | Consulta completa: proprietário, restrições, recalls, multas |
| `placa_duality` | Placa Duality | Placa | Dados básicos: marca, modelo, ano, cor, situação |
| `placa_serpro` | Placa SERPRO | Placa | Dados oficiais do governo federal |
| `placa_senatran` | Placa SENATRAN | Placa | Registro nacional do veículo |
| `bin_placa` | BIN Placa | Placa | Base de Informações Nacionais |
| `frota` | Frota Veicular | CPF/CNPJ | Todos os veículos vinculados a um CPF/CNPJ |

### Chassi e Motor (5 módulos)

| Código | Nome | Entrada | Descrição |
|---|---|---|---|
| `bin_chassi` | BIN Chassi | Chassi | Localização por identificação de fábrica |
| `chassi_serpro` | Chassi SERPRO | Chassi | Verificação oficial pelo governo |
| `chassi_senatran` | Chassi SENATRAN | Chassi | Regularidade nos registros nacionais |
| `bin_motor` | BIN Motor | Motor | Identificação pelo número do motor |
| `motor_senatran` | Motor SENATRAN | Motor | Verificação oficial do motor |

### Renavam e CNH (4 módulos)

| Código | Nome | Entrada | Descrição |
|---|---|---|---|
| `bin_renavam` | BIN Renavam | RENAVAM | Localização pelo documento |
| `renavam_serpro` | Renavam SERPRO | RENAVAM | Dados oficiais do registro |
| `cnh_full` | CNH Full | CPF | Categoria, validade, pontuação, restrições |
| `cnh_serpro` | CNH SERPRO | CPF | Dados oficiais da habilitação |

### Laudos Veiculares (2 módulos)

| Código | Nome | Entrada | Descrição |
|---|---|---|---|
| `laudo_veicular` | Laudo Veicular | Placa | Última vistoria realizada no veículo |
| `laudo_veicular_id` | Laudo por ID | ID do laudo | Busca laudo específico pelo número |

### SENATRAN Avançado (4 módulos)

| Código | Nome | Entrada | Descrição |
|---|---|---|---|
| `multas_senatran` | Multas SENATRAN | Placa | Todas as multas com valores e datas |
| `ocorrencias_senatran` | Ocorrências SENATRAN | Placa | Furtos, roubos, sinistros |
| `recall_senatran` | Recall SENATRAN | Placa | Recalls pendentes das montadoras |
| `renajud_senatran` | Renajud SENATRAN | Placa | Restrições judiciais (bloqueios, penhoras) |

---

## Fluxo do Usuário

```
/start
  │
  ├── [Consultar Veículo] ──► /consultar
  │     │
  │     ├── Selecionar Categoria (lista interativa)
  │     │     │
  │     │     ├── Selecionar Tipo de Consulta (lista com preços)
  │     │     │     │
  │     │     │     ├── Informar Dado (placa, chassi, CPF, etc.)
  │     │     │     │     │
  │     │     │     │     ├── [Grátis ou Admin] ──► Executa consulta
  │     │     │     │     │
  │     │     │     │     └── [Pago] ──► Fluxo de Pagamento
  │     │     │     │           │
  │     │     │     │           └── Após pagamento ──► Executa consulta
  │     │     │     │                                       │
  │     │     │     │                                       ├── PDF enviado como documento
  │     │     │     │                                       │
  │     │     │     │                                       └── [Fallback] Texto formatado
  │     │     │     │
  │     │     │     └── Nova Consulta / Menu Inicial
  │     │     │
  │     │     └── Voltar
  │     │
  │     └── Nenhum módulo disponível
  │
  └── [Ajuda] ──► /help (lista de comandos)
```

---

## Fluxo de Pagamento

### PIX (ativo)

```
Usuário seleciona consulta paga
    │
    ├── Exibe valor e opções de pagamento
    │     │
    │     └── [PIX] ──► Gera PIX via SyncPay API
    │           │
    │           ├── Envia QR Code como imagem
    │           ├── Envia código copia-e-cola (mensagem separada)
    │           └── Aguarda webhook de confirmação
    │                 │
    │                 └── SyncPay confirma ──► Executa consulta automaticamente
    │
    └── [Cancelar] ──► Remove sessão de pagamento
```

**Detalhes técnicos do PIX:**

- QR Code gerado em memória (400x400px, PNG) via ZXing
- Upload do QR Code para a Media API do WhatsApp
- Código PIX enviado em mensagem separada para facilitar cópia
- Webhook do SyncPay confirma pagamento e dispara consulta
- Sessão expira em 30 minutos se não confirmada
- Suporte a split de pagamento (divisão entre recebedores)

### Cartão de Crédito (temporariamente indisponível)

Ao selecionar cartão, o bot exibe aviso de indisponibilidade e redireciona para PIX.

---

## Painel Administrativo

Acesso restrito ao número configurado em `ADMIN_PHONE_NUMBER`. Comando: `/admin`

### Menu Principal

Exibe resumo com: status, módulos ativos, total de consultas, faturamento, pagamentos pendentes e banidos.

### Módulos de Consulta

| Comando | Descrição |
|---|---|
| `/admin modulos` | Listar todos os módulos com status |
| `/admin modulo <código>` | Detalhes de um módulo específico |
| `/admin ativar <código>` | Habilitar módulo para usuários |
| `/admin desativar <código>` | Desabilitar módulo |

### Preços e Valores

| Comando | Descrição |
|---|---|
| `/admin precos` | Ver preços de todos os módulos |
| `/admin preco <código> <valor>` | Definir preço de um módulo |
| `/admin preco_padrao <valor>` | Definir mesmo preço para todos |

### Gerenciar Usuários

| Comando | Descrição |
|---|---|
| `/admin ban <número>` | Banir número de telefone |
| `/admin unban <número>` | Desbanir número |
| `/admin banlist` | Listar números banidos |

### Financeiro

| Comando | Descrição |
|---|---|
| `/admin faturamento` | Resumo: total recebido, custos API, lucro |
| `/admin pendentes` | Usuários aguardando confirmação de pagamento |
| `/admin liberar <número>` | Aprovar pagamento manualmente |

### Relatórios

| Comando | Descrição |
|---|---|
| `/admin stats` | Estatísticas de consultas |
| `/admin top` | Top 10 módulos mais utilizados |
| `/admin historico` | Últimas 10 consultas realizadas |

### Controle do Bot

| Comando | Descrição |
|---|---|
| `/admin block` | Bloquear consultas para todos (exceto admin) |
| `/admin unblock` | Desbloquear consultas |
| `/admin status` | Métricas completas do bot |
| `/admin reset` | Zerar contadores (preços e bans mantidos) |

---

## API REST

### Dashboard

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/v1/dashboard/stats` | Estatísticas do bot (mensagens, uptime, erros) |
| `GET` | `/v1/dashboard/commands` | Lista de comandos registrados |
| `GET` | `/v1/dashboard/health` | Health check (`{"status": "UP"}`) |
| `GET` | `/v1/dashboard/keepalive` | Keep-alive com persistência no banco |

### Mensagens (WhatsApp Gateway)

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/v1/messages/text` | Enviar mensagem de texto |
| `POST` | `/v1/messages/image` | Enviar imagem por URL |
| `POST` | `/v1/messages/video` | Enviar vídeo por URL |
| `POST` | `/v1/messages/audio` | Enviar áudio por URL |
| `POST` | `/v1/messages/document` | Enviar documento por URL |
| `POST` | `/v1/messages/sticker` | Enviar sticker por URL |
| `POST` | `/v1/messages/location` | Enviar localização |
| `POST` | `/v1/messages/contact` | Enviar cartão de contato |
| `POST` | `/v1/messages/buttons` | Enviar botões interativos |
| `POST` | `/v1/messages/list` | Enviar lista interativa |
| `POST` | `/v1/messages/template` | Enviar template aprovado |
| `POST` | `/v1/messages/reaction` | Enviar reação emoji |
| `POST` | `/v1/messages/reply` | Responder mensagem específica |
| `POST` | `/v1/messages/mark-read` | Marcar como lida |

### Webhook

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/v1/webhook` | Verificação do webhook (Meta) |
| `POST` | `/v1/webhook` | Receber mensagens do WhatsApp |
| `POST` | `/v1/payment/webhook` | Webhook de confirmação de pagamento (SyncPay) |

---

## Configuração

### application.yml

```yaml
spring:
  application:
    name: NDConsultas-BOT
  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false
  jackson:
    default-property-inclusion: non_null
    deserialization:
      fail-on-unknown-properties: false

whatsapp:
  phone-number-id: ${WHATSAPP_PHONE_NUMBER_ID}
  api-key: ${WHATSAPP_API_KEY}
  verify-token: ${VERIFY_TOKEN}
  api-version: ${WHATSAPP_API_VERSION:v22.0}

central:
  api-key: ${CENTRAL_API_KEY}

admin:
  phone-number: ${ADMIN_PHONE_NUMBER}

bot:
  logo-url: ${BOT_LOGO_URL}

syncpay:
  api-url: ${SYNCPAY_API_URL:https://api.syncpayments.com.br}
  client-id: ${SYNCPAY_CLIENT_ID}
  client-secret: ${SYNCPAY_CLIENT_SECRET}
  webhook-url: ${SYNCPAY_WEBHOOK_URL}
  webhook-secret: ${SYNCPAY_WEBHOOK_SECRET:}

server:
  port: ${SERVER_PORT:8080}

logging:
  level:
    com.ndconsultas.bot_whatsapp: INFO
```

---

## Variáveis de Ambiente

### Obrigatórias

| Variável | Descrição |
|---|---|
| `WHATSAPP_PHONE_NUMBER_ID` | ID do número do WhatsApp Business (Meta) |
| `WHATSAPP_API_KEY` | Token de acesso permanente da Meta |
| `VERIFY_TOKEN` | Token personalizado para verificação do webhook |
| `CENTRAL_API_KEY` | Chave de API do Central Duality |
| `ADMIN_PHONE_NUMBER` | Número do admin com DDI+DDD (ex: `5581999999999`) |
| `SYNCPAY_CLIENT_ID` | Client ID da SyncPay (OAuth) |
| `SYNCPAY_CLIENT_SECRET` | Client Secret da SyncPay (OAuth) |
| `SYNCPAY_WEBHOOK_URL` | URL pública para receber webhooks do SyncPay |
| `SPRING_DATASOURCE_URL` | URL JDBC do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco de dados |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco de dados |

### Opcionais

| Variável | Padrão | Descrição |
|---|---|---|
| `WHATSAPP_API_VERSION` | `v22.0` | Versão da API do WhatsApp |
| `SYNCPAY_API_URL` | `https://api.syncpayments.com.br` | URL base da SyncPay |
| `SYNCPAY_WEBHOOK_SECRET` | *(vazio)* | Secret para autenticação do webhook |
| `BOT_LOGO_URL` | *(nenhum)* | URL da logo para mensagens |
| `SERVER_PORT` | `8080` | Porta do servidor |

### Split de Pagamento (opcional)

Para configurar divisão de pagamento PIX entre recebedores:

```
SYNCPAY_SPLIT_0_PERCENTAGE=10
SYNCPAY_SPLIT_0_USER_ID=uuid-do-recebedor-1
SYNCPAY_SPLIT_1_PERCENTAGE=5
SYNCPAY_SPLIT_1_USER_ID=uuid-do-recebedor-2
```

O UUID do recebedor é obtido no painel da SyncPay. As porcentagens devem somar no máximo 100%.

---

## Execução Local

### Pré-requisitos

- JDK 17+
- PostgreSQL
- Conta na Meta Business (WhatsApp Cloud API)
- Conta na SyncPay
- Chave da API Central Duality

### Passos

1. Clone o repositório:
```bash
git clone https://github.com/GBLins14/NDConsultas-BOT.git
cd NDConsultas-BOT
```

2. Configure as variáveis de ambiente (crie um arquivo `.env` ou exporte diretamente):
```bash
export WHATSAPP_PHONE_NUMBER_ID=seu_phone_id
export WHATSAPP_API_KEY=seu_token
export VERIFY_TOKEN=seu_verify_token
export CENTRAL_API_KEY=sua_api_key
export ADMIN_PHONE_NUMBER=5581999999999
export SYNCPAY_CLIENT_ID=seu_client_id
export SYNCPAY_CLIENT_SECRET=seu_client_secret
export SYNCPAY_WEBHOOK_URL=https://seu-dominio.com/v1/payment/webhook
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ndconsultas
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=sua_senha
```

3. Execute:
```bash
./gradlew bootRun
```

O servidor estará disponível em `http://localhost:8080`.

4. Configure o webhook no Meta Business:
   - URL: `https://seu-dominio.com/v1/webhook`
   - Token de verificação: o valor de `VERIFY_TOKEN`
   - Assinar: `messages`

---

## Deploy com Docker

### Build e execução

```bash
docker build -t ndconsultas-bot .
docker run -d \
  -p 8080:8080 \
  -e WHATSAPP_PHONE_NUMBER_ID=seu_phone_id \
  -e WHATSAPP_API_KEY=seu_token \
  -e VERIFY_TOKEN=seu_verify_token \
  -e CENTRAL_API_KEY=sua_api_key \
  -e ADMIN_PHONE_NUMBER=5581999999999 \
  -e SYNCPAY_CLIENT_ID=seu_client_id \
  -e SYNCPAY_CLIENT_SECRET=seu_client_secret \
  -e SYNCPAY_WEBHOOK_URL=https://seu-dominio.com/v1/payment/webhook \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/ndconsultas \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=sua_senha \
  ndconsultas-bot
```

### Dockerfile

Multi-stage build otimizado:
- **Build**: Eclipse Temurin JDK 17 Alpine + Gradle (cache de dependências)
- **Runtime**: Eclipse Temurin JRE 17 Alpine (imagem mínima)

---

## Deploy no Render

1. Crie um novo **Web Service** no Render
2. Conecte ao repositório do GitHub
3. Configure:
   - **Runtime**: Docker
   - **Port**: 8080
4. Adicione todas as variáveis de ambiente na seção **Environment**
5. O Render fará o build automaticamente a partir do `Dockerfile`

**Importante**: Configure o `SYNCPAY_WEBHOOK_URL` com a URL do Render (ex: `https://ndconsultas-bot.onrender.com/v1/payment/webhook`)

---

## Estrutura do Projeto

```
src/main/kotlin/com/ndconsultas/bot_whatsapp/
├── NdConsultasBotApplication.kt          # Entrada (@EnableAsync, @EnableScheduling)
└── whatsapp_gateway/
    ├── command/
    │   ├── BotCommand.kt                 # Interface base de comandos
    │   ├── CommandContext.kt             # Contexto da mensagem recebida
    │   ├── CommandProcessor.kt           # Roteamento de comandos
    │   ├── CommandRegistry.kt            # Registro de comandos disponíveis
    │   └── impl/
    │       ├── StartCommand.kt           # /start - Menu inicial
    │       ├── HelpCommand.kt            # /help - Ajuda
    │       ├── ConsultarCommand.kt       # /consultar - Consultas + pagamento
    │       └── AdminCommand.kt           # /admin - Painel administrativo
    ├── client/
    │   └── SyncPayClient.kt              # Cliente HTTP da SyncPay
    ├── config/
    │   ├── WhatsappProperties.kt         # Configurações WhatsApp
    │   ├── WhatsappConfig.kt             # Bean de RestClient
    │   ├── SyncPayProperties.kt          # Configurações SyncPay + Split
    │   ├── SyncPayConfig.kt              # Bean de RestClient
    │   ├── QueryTypeRegistry.kt          # Registry dos 21 tipos de consulta
    │   └── DataSourceConfig.kt           # Configuração do PostgreSQL
    ├── controller/
    │   ├── MessageController.kt          # API REST de mensagens
    │   ├── DashboardController.kt        # Dashboard + health + keepalive
    │   └── PaymentWebhookController.kt   # Webhook SyncPay (idempotente)
    ├── webhook/
    │   ├── WebhookController.kt          # Recepção de mensagens do WhatsApp
    │   └── WebhookProcessor.kt           # Processamento assíncrono
    ├── service/
    │   ├── WhatsappService.kt            # Interface do serviço WhatsApp
    │   ├── WhatsappServiceImpl.kt        # Implementação (Meta Graph API)
    │   ├── VehicleConsultationService.kt  # Chamadas à Central Duality
    │   ├── PaymentService.kt             # Orquestração PIX + Cartão
    │   ├── PaymentSessionManager.kt      # Sessões de pagamento (30min TTL)
    │   ├── ConsultationSessionManager.kt  # Sessões de consulta (5min TTL)
    │   ├── AdminService.kt               # Ban/unban, bloqueio do bot
    │   ├── PricingService.kt             # Preços e módulos ativos
    │   ├── PdfReportService.kt           # Geração de relatórios PDF (OpenPDF)
    │   ├── QrCodeService.kt              # QR Code PIX (ZXing)
    │   ├── ConsultationStats.kt          # Métricas de consultas
    │   ├── PaymentStats.kt               # Métricas de pagamentos
    │   └── BotStats.kt                   # Contadores gerais + uptime
    ├── dto/                              # Data Transfer Objects
    ├── model/                            # Modelos (Button, ListSection, etc.)
    ├── persistence/
    │   ├── Entities.kt                   # Entidades JPA
    │   ├── Repositories.kt              # Spring Data Repositories
    │   └── ConfigPersistenceService.kt   # Load/save de configurações
    ├── event/
    │   └── WhatsappEvents.kt             # Eventos Spring internos
    └── exception/
        ├── WhatsappExceptions.kt         # Exceções customizadas
        └── GlobalExceptionHandler.kt     # Handler global de erros
```

### Banco de Dados (PostgreSQL)

| Tabela | Descrição |
|---|---|
| `bot_banned_numbers` | Números de telefone banidos |
| `bot_module_prices` | Preços por tipo de consulta |
| `bot_disabled_modules` | Módulos desativados |
| `bot_settings` | Configurações chave-valor (keepalive, etc.) |

As tabelas são criadas/atualizadas automaticamente via `ddl-auto: update`.

---

## Segurança

- **Webhook WhatsApp**: Verificação de token no handshake GET
- **Webhook SyncPay**: Autenticação via header `Authorization: Bearer {secret}`
- **Idempotência**: Webhooks duplicados são ignorados (tracking por transaction ID)
- **Sessões**: Expiração automática (pagamento: 30min, consulta: 5min)
- **Cleanup periódico**: `@Scheduled` remove sessões expiradas a cada 5 minutos
- **Concorrência**: Operações atômicas com `computeIfPresent()` em `ConcurrentHashMap`
- **Admin**: Acesso restrito por número de telefone configurado

---

## Relatórios PDF

Os resultados de consultas são entregues como documentos PDF com:

- **Cabeçalho**: "ND CONSULTAS VEICULARES" (branco sobre azul)
- **Informações**: tipo de consulta, dado consultado, data/hora
- **Tabela de resultados**: campos e valores em linhas alternadas
- **Rodapé**: disclaimer sobre fontes de dados

**Formato**: A4, fonte Helvetica, esquema de cores azul/cinza.
Dados aninhados (objetos e arrays JSON) são achatados recursivamente para exibição.

Se o upload do PDF falhar, o bot envia os resultados como texto formatado no WhatsApp.

---

## Licença

Projeto proprietário - ND Consultas. Todos os direitos reservados.
