# Central de Alertas e Saúde Financeira

<p align="center">
  <img src="docs/central-alertas.gif" alt="Demonstração do Sistema" width="100%" style="max-height: 300px; object-fit: cover; border-radius: 8px;">
</p>


Projeto de estudo: um sistema que **automatiza a cobrança de faturas a vencer e atrasadas**.
Em vez de alguém ficar olhando uma planilha todo dia para ver quem está perto de vencer ou
já venceu, o sistema faz isso sozinho: todo dia ele checa as faturas, marca as vencidas como
atrasadas e dispara um e-mail de cobrança para o cliente. Também tem um painel web com a
"saúde financeira" (quanto há a receber, quanto está atrasado) e a lista de faturas.

Fiz tudo em **Java puro, sem Spring nem nenhum framework**, justamente para entender cada
camada na mão: o servidor HTTP, o acesso ao banco com JDBC, o envio de e-mail e o agendamento.

---

## O que ele faz

- Cadastra **clientes** e **faturas** (uma fatura pertence a um cliente — relação 1:N).
- Mostra um **painel de Saúde Financeira**: total a receber (faturas pendentes), total
  atrasado, e a quantidade de cada.
- Lista as faturas com o status colorido (pendente / atrasada / paga).
- **Cobrança automática**: um agendador roda de tempos em tempos e, para cada fatura que
  vence em 2 dias ou que já venceu, envia um e-mail de cobrança para o cliente — sem
  repetir o mesmo alerta no mesmo dia. Também dá para **disparar a cobrança na hora** por um
  botão no painel.

---

## Arquitetura

O sistema é dividido em camadas, cada uma com uma responsabilidade só. O fluxo de uma
requisição vinda da tela é sempre o mesmo: o front chama a API, que cai num Handler, que
chama um Service (regras), que usa um DAO (banco).

```
┌──────────────────────┐      HTTP/JSON       ┌───────────────────────────────────────┐
│   Front-end (web/)    │  ───────────────▶    │        API — com.sun.net.httpserver    │
│   HTML + CSS + JS      │   fetch (CORS)      │              (porta 8090)              │
│   (aberto no Live      │  ◀───────────────   │                                        │
│    Server)            │      JSON            │   Handlers (rotas)                     │
└──────────────────────┘                      │     ├─ ClienteHandler                  │
                                               │     ├─ FaturaHandler                   │
                                               │     ├─ DashboardHandler                │
                                               │     ├─ CobrancaHandler                 │
                                               │     └─ HealthHandler                   │
                                               │            │                           │
                                               │            ▼                           │
                                               │   Services (regras de negócio)         │
                                               │     ├─ CobrancaService                 │
                                               │     └─ EmailService ─────▶ SMTP Gmail   │
                                               │            │              (Jakarta Mail)│
                                               │            ▼                           │
                                               │   DAOs (JDBC + PreparedStatement)      │
                                               │     ├─ ClienteDAO                      │
                                               │     ├─ FaturaDAO                       │
                                               │     └─ AlertaEnviadoDAO                │
                                               └────────────┬──────────────────────────┘
                                                            │ JDBC
                                                            ▼
                                                   ┌─────────────────┐
                                                   │  MySQL/MariaDB  │
                                                   │ central_alertas │
                                                   └─────────────────┘

   Em paralelo, rodando em segundo plano:
   ┌────────────────────────────────────────────────────────────┐
   │  Agendador (ScheduledExecutorService)                       │
   │  a cada X tempo -> CobrancaService.executarCobranca()       │
   │  (busca faturas a vencer/vencidas -> marca ATRASADA ->      │
   │   EmailService envia -> AlertaEnviadoDAO registra o log)    │
   └────────────────────────────────────────────────────────────┘
```

**Camadas (de cima para baixo):**

- **Front (`web/`)** — HTML/CSS/JS puro. Conversa com a API por `fetch` (JSON).
- **Handler** — recebe a requisição HTTP, lê o corpo, chama quem sabe a regra e devolve JSON
  com o status certo (200/201/400/404/500). Não tem regra de negócio.
- **Service** — as regras: `CobrancaService` decide quais faturas alertar; `EmailService` monta
  e envia o e-mail.
- **DAO** — único lugar que conhece SQL. Faz o CRUD com `PreparedStatement`.
- **Model** — POJOs simples (`Cliente`, `Fatura`, `AlertaEnviado`) que trafegam entre as camadas.

**Entidades:** `Cliente` (1) ──< `Fatura` (N); cada envio de cobrança vira um registro em
`AlertaEnviado` (log).

---

## Tecnologias (e por que cada uma)

| Tecnologia | Para quê | Por que |
|---|---|---|
| **Java 21** | linguagem | versão LTS atual; uso recursos modernos (java.time, etc.) |
| **`com.sun.net.httpserver`** | servidor HTTP | já vem no JDK — dá pra ter uma API sem framework e enxergar como uma rota funciona |
| **JDBC + PreparedStatement** | banco | acesso direto e explícito ao MySQL, sem ORM; o PreparedStatement protege contra SQL Injection |
| **MySQL/MariaDB (XAMPP)** | banco de dados | banco local simples de subir para estudo |
| **Jakarta Mail (impl. Angus)** | e-mail | biblioteca padrão para enviar e-mail por SMTP em Java |
| **Gson** | JSON | converte objeto Java ⇄ JSON nos handlers, sem escrever isso na mão |
| **Vanilla JS** | front-end | sem framework, para focar no `fetch` e na manipulação do DOM |
| **Maven Wrapper** | dependências | baixa os `.jar` (e o próprio Maven) para `lib/`; o código segue compilado/rodado por `javac`/`java` |

---

## Como rodar

### Pré-requisitos
- **Java 21** instalado (`java -version`).
- **XAMPP** com o **MySQL** rodando.
- Internet na primeira vez (o Maven Wrapper baixa o Maven e as dependências).

### 1. Criar o banco e as tabelas
No phpMyAdmin (aba **SQL**), rode:

```sql
CREATE DATABASE IF NOT EXISTS central_alertas CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE central_alertas;

CREATE TABLE cliente (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  nome      VARCHAR(120) NOT NULL,
  email     VARCHAR(150) NOT NULL,
  telefone  VARCHAR(20)  DEFAULT NULL,
  criado_em DATETIME     NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fatura (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  cliente_id      BIGINT        NOT NULL,
  descricao       VARCHAR(200)  DEFAULT NULL,
  valor           DECIMAL(10,2) NOT NULL,
  data_vencimento DATE          NOT NULL,
  status          VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE',
  criado_em       DATETIME      NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_fatura_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alerta_enviado (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  fatura_id  BIGINT      NOT NULL,
  tipo       VARCHAR(30) NOT NULL,
  canal      VARCHAR(20) NOT NULL,
  enviado_em DATETIME    NOT NULL,
  sucesso    TINYINT(1)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_alerta_fatura FOREIGN KEY (fatura_id) REFERENCES fatura (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. Carregar os dados de demonstração (opcional)
No phpMyAdmin, selecione o banco `central_alertas` e rode o script [`banco/dados_demo.sql`](banco/dados_demo.sql).
Ele **apaga** o que estiver lá e cria 5 clientes e 10 faturas de exemplo.

### 3. Configurar o e-mail
O envio usa uma conta Gmail. A senha **não fica no código** — fica num arquivo ignorado pelo Git.

1. Copie `config/email.properties.exemplo` para `config/email.properties`.
2. Preencha:
   - `email.remetente` = seu Gmail;
   - `email.senha` = uma **senha de app** de 16 letras (gere em *Conta Google → Segurança →
     Verificação em duas etapas → Senhas de app*; a senha normal não funciona por SMTP).

### 4. Compilar e rodar o back-end (Windows)
Os `.bat` na raiz fazem tudo em ordem (duplo clique ou terminal):

1. `1-baixar-dependencias.bat` — baixa os `.jar` para `lib/` (primeira vez baixa o Maven, ~10 MB).
2. `2-compilar.bat` — compila `src/` para `out/`.
3. `3-rodar.bat` — sobe o servidor em `http://localhost:8090` (e liga o agendador).

### 5. Abrir o front-end
Com o back-end no ar, abra [`web/index.html`](web/index.html) com a extensão **Live Server**
do VS Code (botão direito → *Open with Live Server*). Ele abre em outra origem (ex.:
`http://127.0.0.1:5500`) e chama a API em `:8090` — por isso o back-end tem **CORS** habilitado.

### Rotas da API
```
GET    /api/health
GET    /api/dashboard/resumo
GET    /api/clientes            POST /api/clientes
GET    /api/clientes/{id}       PUT  /api/clientes/{id}     DELETE /api/clientes/{id}
GET    /api/faturas             POST /api/faturas
GET    /api/faturas/{id}        PUT  /api/faturas/{id}      DELETE /api/faturas/{id}
POST   /api/cobrancas/disparar
```

---

## Decisões técnicas e desafios

- **API separada do front (e CORS).** O back-end só fala JSON; o front é estático e roda em
  outra origem (Live Server). Como o navegador bloqueia chamadas entre origens diferentes, o
  back-end responde os cabeçalhos `Access-Control-Allow-*` e trata o pedido `OPTIONS`
  (preflight) que o navegador manda antes de um POST com JSON.
- **`PreparedStatement` em tudo.** Os valores entram como parâmetros (`?`), nunca concatenados
  na string SQL. Isso evita **SQL Injection** e ainda cuida do escape de tipos (datas, etc.).
- **Agendamento com `ScheduledExecutorService`.** Ele mantém uma thread em segundo plano que
  dispara a cobrança de tempos em tempos, sem travar o servidor HTTP (que roda em paralelo).
  Detalhe que me pegou: no `scheduleAtFixedRate`, se a tarefa lançar uma exceção sem tratar, o
  agendador **para de repetir silenciosamente** — por isso envolvi a tarefa num `try/catch`.
  Está em 1 minuto para teste; em produção seria 1x por dia (trocando a unidade para `DAYS`).
- **Não repetir cobrança no mesmo dia.** Antes de enviar, consulto `alerta_enviado` para ver se
  já houve um envio **com sucesso** hoje para aquela fatura. Se uma tentativa falha, ela é
  re-tentada no próximo ciclo (só sucesso não se repete).
- **Senha fora do código.** As credenciais ficam em `config/email.properties`, que está no
  `.gitignore`; no repositório vai só o `.exemplo`, sem senha. Assim nada sensível vai pro GitHub.
- **Maven só para dependências.** O Jakarta Mail puxa várias dependências transitivas; resolver
  isso na mão seria chato. Então uso o Maven (via wrapper, sem instalar nada) só para baixar os
  `.jar` para `lib/` — mas continuo compilando e rodando com `javac`/`java`, para o classpath
  ficar visível.
- **Porta 8090.** A 8080 costuma estar ocupada na minha máquina, então o padrão é 8090
  (dá para passar outra como argumento: `java -cp "out;lib/*" com.centralalertas.Main 9000`).

---

## Estrutura do projeto

```
central-alertas/
├─ src/com/centralalertas/
│  ├─ Main.java                 # sobe o servidor, registra as rotas e liga o agendador
│  ├─ handler/                  # rotas HTTP (Cliente, Fatura, Dashboard, Cobranca, Health)
│  ├─ service/                  # CobrancaService, EmailService, Agendador, ResumoCobranca
│  ├─ dao/                      # ClienteDAO, FaturaDAO, AlertaEnviadoDAO (JDBC)
│  ├─ model/                    # Cliente, Fatura, AlertaEnviado, enums (StatusFatura, TipoAlerta)
│  └─ util/                     # Conexao (JDBC), Json (Gson), Http (corpo/JSON/CORS)
├─ web/                         # front-end: index.html, styles.css, app.js
├─ banco/dados_demo.sql         # dados de demonstração
├─ config/email.properties(.exemplo)  # credenciais de e-mail (o real é gitignored)
├─ lib/                         # .jar baixados pelo Maven (gitignored)
├─ 1-baixar-dependencias.bat / 2-compilar.bat / 3-rodar.bat
└─ pom.xml / mvnw / .mvn        # Maven Wrapper (só para baixar dependências)
```
## Limitações e próximos passos

É um MVP de estudo, então deixei coisas de fora de propósito:

- **Sem login/autenticação** — qualquer um com acesso à API consegue mexer. Num sistema real entraria um controle de acesso.
- **E-mails podem cair no spam** — o envio é por uma conta Gmail comum, sem domínio próprio configurado (SPF/DKIM/DMARC). Em produção isso se resolve com autenticação de domínio ou um serviço de envio dedicado.
- **Roda local** — banco e servidor sobem na minha máquina; não há deploy em nuvem.
- **Agendador em modo teste** — está rodando a cada 1 minuto para facilitar a demonstração; em produção rodaria 1x por dia.
