# CLAUDE.md — Fiscal-Ingest (contexto mestre · v2)

> **Leia este arquivo inteiro antes de qualquer ação.**
> Ele é o **documento-base do projeto**: o que o produto é, o que já existe no repositório, as
> decisões tomadas, as regras inegociáveis, a arquitetura, o plano de fases e as dúvidas em aberto.
>
> **v2 (2026-10-20)** — consolida a v1 (motor de coleta NF-e no Ambiente Nacional) com a visão de
> **hub de DF-e**: agente local em Go para capturar as saídas no ERP, catálogo único de documentos
> e API/webhook para softwares contábeis de terceiros.
>
> Substitui o `.clinerules` e **prevalece** sobre `docs/ARCHITECTURE.md` e `docs/DECISIONS.md` até que
> estes sejam atualizados (seção 18). Ao encontrar conflito, **sinalize** e proponha a correção.

Idioma de trabalho: **português do Brasil (pt-BR)** em conversa, commits, docs e nomes de domínio.
O código segue o vocabulário de domínio já existente (`Nsu`, `Desfecho`, `Cursor`, `Documento`...).

**Ritmo:** projeto de estudo e de longo prazo, **sem prazo**. O autor é dev pleno e trabalha na área.
A prioridade é **fazer certo**: passos pequenos, TDD, decisões registradas. Nada de atalho para
"entregar logo".

---

## 0. Protocolo de entrada (como começar cada sessão)

**A auditoria inicial (Fase 1.5) foi concluída em 2026-10-21/22** — ver `docs/AUDITORIA-1.5.md`.
Wrapper, `settings`, arquivos perdidos e docs já foram corrigidos; não refaça essa auditoria.

Em cada sessão:
1. Leia este arquivo e veja em que fase/subetapa o projeto está (seção 16; no modo autônomo, o
   `ROADMAP.md` da branch `auto/estado`).
2. Siga o plano da subetapa. Achados da auditoria ainda abertos estão em `docs/AUDITORIA-1.5.md` §2.

**Regra de ouro:** proponha o design → aguarde OK → escreva o teste → veja falhar → implemente →
pare e resuma. Incrementos pequenos. Na dúvida entre "avançar" e "perguntar", **pergunte**.

---

## 1. O que é o produto

> **Reunir todos os DF-e de uma empresa — os que ela recebe e os que ela emite —, guardá-los com
> histórico completo e auditoria, e entregá-los por API e webhook ao software contábil.**

### 1.1 Componentes

| # | Componente | Linguagem | Onde roda | Papel |
|---|---|---|---|---|
| 1 | **Motor de coleta** (`fiscal-ingest`, este repositório) | Java 21 | Servidor/plataforma | Busca no Ambiente Nacional, por NSU, as notas **contra** a empresa e as notas em que o escritório consta no `autXML` |
| 2 | **Porta push + catálogo** (este repositório) | Java 21 | Plataforma | Recebe arquivos do agente/upload/e-mail, valida, deduplica e mantém **um documento canônico por nota** |
| 3 | **API de entrega** (este repositório) | Java 21 | Plataforma | Feed incremental, download de XML, webhooks e OAuth para integradores |
| 4 | **Agente local** (`fiscal-agent`, **repositório separado**) | **Go** | Servidor/PC do cliente (Windows; Linux depois) | Vigia as pastas do ERP e envia os XML emitidos pela empresa |

### 1.2 Atores

- **Escritório contábil = tenant.** É o cliente da plataforma e possui várias empresas.
- **Empresa:** um CNPJ (com suas filiais), que pertence a um tenant.
- **Integrador:** a software house de um sistema contábil de terceiro. O escritório **autoriza** um
  integrador a receber as notas de algumas de suas empresas. O integrador implementa o recebimento por webhook.
- **Agente:** uma instalação do `fiscal-agent`, vinculada a um tenant e a uma lista de CNPJs que pode enviar.
- **Operador da plataforma:** o autor. Tem acesso administrativo auditado e nunca lê XML sem motivo registrado.

### 1.3 Visão geral

```
 ENTRADAS (contra a empresa)           SAÍDAS (emitidas pela empresa)
 SEFAZ – Ambiente Nacional             Pasta do ERP          E-mail em cópia   Upload manual
  · cert. da EMPRESA → seu CNPJ        Agente Go (push)       (push)            (push)
  · cert. do ESCRITÓRIO → autXML ─┐          │                    │                │
        │ PULL por NSU            │          └────────┬───────────┴────────────────┘
        ▼                         ▼                   ▼
 ┌─────────────────────────── PLATAFORMA (Java) ────────────────────────────────────┐
 │  Porta PULL (SourceAdapter)               Porta PUSH (API de recebimento)          │
 │          └───────────────────┬──────────────────────┘                               │
 │                              ▼                                                      │
 │  Pipeline único: grava bruto (MinIO) → registra (Postgres) → outbox → processors    │
 │                              ▼                                                      │
 │  Catálogo: 1 documento por (tenant, chave) · N arquivos · N proveniências           │
 │                              ▼                                                      │
 │  Feed sequencial (seq_feed) ──► despachante de webhooks                             │
 └──────────────────────────────┬──────────────────────────────────────────────────────┘
                                ▼
          API de entrega: feed ?desde=seq · XML · eventos · export ZIP · webhooks
                                ▼
                 Software contábil de terceiro (integrador)
```

### 1.4 Contexto de negócio

- O autor trabalha com um sistema **legado** que trava, não guarda histórico, não permite auditoria
  e sofre com concorrência desordenada. Este projeto reconstrói esse núcleo do jeito certo.
- **Público:** pequenas e médias empresas e pequenos e médios escritórios contábeis. Por isso a
  captura precisa ser **genérica em relação ao ERP** e o mais simples possível de instalar (seção 10).
- **Modelo de certificados:** há **N certificados por tenant**, alguns do escritório e outros das
  empresas. Na prática, muitos escritórios recebem o A1 da empresa, **com contrato** que autoriza o
  uso por terceiros (seção 7).
- **Ideia futura de crédito** (análise a partir do faturamento real). É o motivo do rigor com
  histórico, auditoria e **proveniência/confiança** dos dados. **Não implementar agora.** Apenas
  garantir que o modelo suporte isso (seção 14).
- **Escala-alvo de longo prazo:** milhares de escritórios × milhares de empresas, o que dá centenas de
  milhares a milhões de CNPJs. Projete para isso, mas **implemente o simples primeiro** (ADR-021).
- Open source e self-host via docker-compose por ora (ADR-008). Licença a definir (ADR-022).

### 1.5 Objetivos, em ordem de prioridade

1. **Nunca perder um documento.** Guardar **tudo**: XML bruto, eventos, resumos e a resposta bruta do webservice.
2. **Nunca bloquear um CNPJ** na SEFAZ (NSU, consumo indevido, janelas de bloqueio).
3. **Nunca deixar o integrador perder uma nota** (feed sem buracos + webhook com retentativa).
4. **Auditabilidade total:** ledger append-only e trilha de quem fez o quê, com qual autorização.
5. **Captura genérica:** funcionar com qualquer ERP que grave XML.
6. **Escalar horizontalmente** sem corrida entre workers.
7. **Extensibilidade:** novas fontes entram pela porta pull (`SourceAdapter`) ou pela porta push, sem tocar no core.

### 1.6 Fora de escopo por enquanto

Análise de crédito, front-end completo (no começo, só o mínimo necessário: ativação de agente,
autorização de integrador), deploy em nuvem/k8s, NFS-e municipal, robôs de scraping/RPA em portais,
estaduais. Não desenhe nada que impeça esses itens, mas **não os implemente**.

---

## 2. Regras inegociáveis

### 2.1 Metodologia
- **TDD obrigatório:** teste primeiro → rodar e ver **falhar** → implementar → passar → refatorar.
  **Os testes existentes devem continuar verdes.** Ao mudar um comportamento, mude o teste primeiro e explique o porquê.
- Passos pequenos. Ao fim de cada incremento, **pare e resuma** o que mudou, os testes e o próximo passo.
- Decisão arquitetural ambígua ou grande: **pergunte**. No máximo 3 perguntas por vez, sempre com recomendação.
- Antes de codar um módulo novo, **proponha o design e aguarde OK**.

### 2.2 Invariantes críticas (nunca violar; cada uma precisa de teste dedicado)

**Coleta (pull):**
1. **Não-retrocesso de NSU:** nunca enviar NSU ou cursor anterior ao já consumido (gera `cStat 656` e bloqueia o CNPJ).
2. **Um CNPJ consultado por vez por fonte:** nunca consultas concorrentes para o mesmo `(fonte, cnpj_consultado, ambiente)`.
3. **XML fora do core:** o XML **nunca** passa por domínio ou scheduler. Só metadado, NSU e `BlobRef`.
4. **Core desacoplado da origem:** nenhum `cStat`, HTTP ou SOAP vaza do adapter. O core só vê `Desfecho`.

**Armazenamento:**
5. **Grave primeiro, avance depois:** o bruto é persistido **antes** de o cursor, o ledger ou o catálogo
   avançarem. Se falhar no meio, o lote é relido. **Nunca** se perde documento.
6. **Append-only:** sem `UPDATE` ou `DELETE` em ledger, auditoria, proveniência, autorização ou log de entrega.
7. **Blob imutável:** um objeto gravado nunca é reescrito com conteúdo diferente. A chave inclui ou referencia o sha256.

**Catálogo e push (novas na v2):**
8. **Um documento canônico por `(tenant, chave_acesso)`.** A mesma nota chegando por vários canais vira
   **um** documento com **várias proveniências**, nunca duas cópias.
9. **Entrada push não é confiável até ser validada:** parser sem XXE, limites de tamanho, assinatura
   conferida e CNPJ emitente pertencente ao agente que enviou. Toda proveniência registra a sua **confiança**.
10. **Atribuição pelo conteúdo:** a empresa dona do documento é definida pelos CNPJs **dentro do XML**,
    nunca só pelo CNPJ que fez a consulta (caso do `autXML`, seção 7.3).

**Entrega (novas na v2):**
11. **Feed sem buracos:** `seq_feed` é estritamente crescente, e um leitor que pede `desde=N` nunca pula
    um item que ainda vai ser confirmado com número menor (seção 11.3).
12. **Entregar só com autorização vigente:** nenhum dado vai a um integrador sem autorização ativa do
    tenant para aquela empresa e aquele escopo. Revogou, parou na hora.

**Gerais:**
13. **Nada de segredo em log, em exceção ou em disco em claro** (certificado, senha, token, XML com dados pessoais).
14. **Nunca chamar a SEFAZ real em teste automatizado.** Só WireMock e fakes. Smoke manual em homologação
    é permitido, fora do CI, com flag explícita.

### 2.3 O que NÃO fazer
- Reinventar o protocolo SEFAZ quando houver WSDL ou XSD oficial. O valor está na **orquestração**.
- Colocar XML no caminho do core ou do scheduler. Acoplar o core a uma origem ou transporte.
- Colocar **regra fiscal no agente** (ele encontra, identifica e envia; quem decide é a plataforma).
- Implementar todos os documentos ou estados de uma vez.
- Copiar código de projetos de referência sem checar a **licença** e sem **testes próprios**.
- Commitar certificados, senhas, XMLs reais ou dados de clientes. Use fixtures **sintéticas ou anonimizadas**.

---

## 3. Fatos do domínio (NF-e no Ambiente Nacional)

> Itens marcados com **[VALIDAR]** devem ser conferidos no MOC, nas NTs vigentes e no portal da NF-e
> (nfe.fazenda.gov.br) antes de virarem teste "congelado". Registre a fonte em `docs/FONTES-OFICIAIS.md`.

### 3.1 Distribuição (`NFeDistribuicaoDFe`)
- Três formas de consulta: por **`ultNSU`** (lote, `distNSU`), por **NSU específico** (`consNSU`) e por
  **chave de acesso** (`consChNFe`). A resposta traz `ultNSU`, `maxNSU`, `cStat`, `xMotivo` e uma lista de `docZip`.
- Cada `docZip` é **base64 + gzip**. Os schemas possíveis são `resNFe`, `procNFe`, `resEvento` e `procEventoNFe`.
  Cada um traz os atributos `NSU` e `schema`.
- **Os eventos chegam pelo mesmo canal** e são cidadãos de primeira classe, vinculados à chave.
- A sequência de NSU é **por CNPJ consultado e por ambiente** (produção ≠ homologação).
- Na primeira consulta, `ultNSU = 0`, o que traz o que o AN ainda retém. **[VALIDAR]** o tempo de retenção (~90 dias).
- **Atores atendidos pela distribuição** (NT 2014.002): destinatário, transportador, terceiros
  autorizados no `autXML` e outros previstos na NT. **[VALIDAR]** a lista vigente e se o **emitente**
  recebe os eventos das próprias notas (por exemplo, a manifestação feita pelo destinatário).
- O **XML da nota emitida pela própria empresa não volta para ela** por esse canal **[VALIDAR]**. Por isso
  existem o agente e o `autXML`.

### 3.2 Consumo e bloqueio (o centro do rate-limit)
- **Não retroceder NSU.** Pedir NSU já consumido → `cStat 656` (Consumo Indevido) → bloqueio.
- **Espera de ~1h:** se a consulta não traz documento novo (`137`, tipicamente com `ultNSU == maxNSU`),
  novas consultas daquele CNPJ ficam bloqueadas por ~1h. **[VALIDAR]** a duração exata e o comportamento
  quando `ultNSU < maxNSU` (a leitura atual é que, havendo mais documentos, o loop continua em seguida).
- O bloqueio é **por CNPJ consultado**, não um limite global. Ele é modelado como estado do par
  `(fonte, cnpj_consultado)`, com `bloqueado_ate` e **duração configurável por fonte** (`PoliticaRateLimit`).
- `consNSU` e `consChNFe` têm limites próprios (~20/hora) **[VALIDAR]**.
- Mapeamento inicial de `cStat` para `Desfecho` (**[VALIDAR]** cada um):
  - `138` → `Ok`
  - `137` → `Vazio`
  - `656` → `LimiteAtingido` (em produção, é **bug de invariante**)
  - `108`/`109` → `ServicoFora` ou `IndisponivelTemporario`
  - `632` → **a confirmar** (a v1 do ARCHITECTURE mapeava para `LimiteAtingido` e a v1 deste arquivo para `ServicoFora`; validar o significado na NT)
  - `589` (NSU maior que o máximo) → **erro de consistência** (cursor corrompido), nunca retentar
  - rejeições de certificado ou schema → `ErroAutenticacao` ou `IndisponivelDefinitivo`

### 3.3 Manifestação do destinatário
1. Chega o **resumo** (`resNFe`), sem o XML completo.
2. O **destinatário manifesta** (210210 – Ciência da Operação).
3. O **XML completo** (`procNFe`) é liberado e chega em um **NSU posterior**.
- Eventos: `210200` Confirmação · `210210` Ciência · `210220` Desconhecimento · `210240` Operação não Realizada. **[VALIDAR]** os prazos.
- **Só o certificado da própria empresa (destinatária) manifesta.** O certificado do escritório não
  manifesta pelo cliente **[VALIDAR]**. Logo, ciência automática exige o certificado da empresa no cofre.
- **Atenção jurídica:** a ciência automática é uma declaração em nome do destinatário. Ela só pode ser
  ligada com **consentimento explícito** por empresa e, por padrão, fica **DESLIGADA** (corrige o ADR-004,
  seção 17). Confirmação, desconhecimento e operação não realizada **nunca** são automáticas: são sempre
  manuais, com auditoria de quem decidiu.
- Manifestar exige **assinatura XML-DSig** com o certificado da empresa. Baixar não exige assinatura.

### 3.4 `autXML` (pessoas autorizadas a obter o XML)
- O emitente pode incluir, na própria NF-e, CNPJs ou CPFs autorizados a obter o XML (grupo `autXML`,
  com limite de ocorrências **[VALIDAR]**, ~10).
- Esses autorizados **recebem a nota pela distribuição do AN**, consultando com o **próprio** certificado.
- Uso no produto: o ERP da empresa coloca o **CNPJ do escritório** no `autXML`. A consulta com o certificado
  do escritório passa a receber as **saídas** de todas as empresas que fizeram isso (seção 7.3).
- Só vale **dali em diante** (não é retroativo), só para os modelos que têm distribuição (55; CT-e tem
  o seu próprio serviço), e depende do ERP permitir configurar.

### 3.5 Autenticação e certificado
- **mTLS com A1 (.pfx) ICP-Brasil**, normalmente com validade de 1 ano. A3 (token ou cartão) não serve para automação.
- **[VALIDAR]** qual certificado pode consultar qual CNPJ: e-CNPJ da matriz para as filiais com mesma raiz?
  Procurador? A premissa segura é **cada certificado consulta o CNPJ do seu próprio titular** (ou raiz).
- `.pfx` antigo pode usar criptografia legada (RC2/3DES). Se o `KeyStore` padrão recusar, carregue com BouncyCastle.

### 3.6 Outros fatos de mercado relevantes
- **CF-e SAT (modelo 59):** São Paulo encerrou o SAT e tornou a NFC-e obrigatória a partir de 2026.
  Suporte ao modelo 59 só para **importação de histórico**.
- **NFS-e:** o padrão nacional (ADN) passou a ser obrigatório para os municípios em 2026. Antes de
  qualquer conector municipal, medir quanto o ADN já cobre. Conector municipal, se houver, é **por provedor**, nunca por cidade.
- **NFCom (62) / NF3e (66):** **[VALIDAR]** se há serviço de distribuição ao interessado ou se só dá
  para obter por push (e-mail ou agente).
- **Reforma tributária (IBS/CBS):** a NF-e ganha novos grupos e campos. O **parser e as tabelas
  derivadas são versionados** e reconstruíveis a partir do bruto.

---

## 4. Canais de captura

| # | Canal | Direção | Instala algo? | Cobre | Confiança padrão | Fase |
|---|---|---|---|---|---|---|
| A | AN, certificado **da empresa** consultando o próprio CNPJ | Entradas (+ eventos) | Não | NF-e (depois CT-e) | `OFICIAL` | 2 |
| B | AN, certificado **do escritório** consultando o CNPJ do escritório (`autXML`) | Saídas (e o que mais o escritório for interessado) | Não (configuração no ERP) | NF-e emitidas depois da configuração | `OFICIAL` | 2/3 |
| C | **Agente Go** vigiando pastas | Saídas (e o que estiver na pasta) | Sim | Tudo que o ERP grava, incluindo NFC-e e histórico | `VALIDADO_ASSINATURA` | 7 |
| D | **E-mail em cópia** (`xml+{empresa}@...`) | Saídas | Não | O que o ERP manda por e-mail | `VALIDADO_ASSINATURA` | 9 |
| E | **Upload manual / ZIP** pela API | Qualquer | Não | Histórico, casos avulsos | `VALIDADO_ASSINATURA` | 5 |

Níveis de confiança (`Confianca`):
- `OFICIAL`: veio da SEFAZ/AN por mTLS.
- `CONFIRMADO_SEFAZ`: veio por push e a situação foi conferida na SEFAZ por consulta de protocolo **[VALIDAR o serviço e os limites]**.
- `VALIDADO_ASSINATURA`: veio por push com as assinaturas XML-DSig da nota e do protocolo válidas.
- `DECLARADO`: veio por push sem validação completa (ex.: assinatura não verificável). É armazenado, mas sinalizado.

Para crédito no futuro, só `OFICIAL` e `CONFIRMADO_SEFAZ` contam como faturamento comprovado.

Todos os canais convergem na **mesma porta push** (C, D, E) ou no **mesmo pipeline pull** (A, B), e
depois no **mesmo catálogo**.

---

## 5. Estado atual do repositório (atualizado em 2026-10-22)

Pacote raiz `br.com.doc.ingest`. Gradle Kotlin DSL (wrapper 8.10.2), multi-módulo, Java 21.

| Módulo | Conteúdo | Status |
|---|---|---|
| `domain` | `Nsu`, `Cnpj`, `FonteId`, `Documento`; `adapter/{Cursor, Desfecho, Capacidades, ContextoConsulta, SourceAdapter, AdapterDescriptor, Credencial, BuscarLoteResultado, BuscarUmResultado, ConsultaPontualPort}`; `ledger/{StatusNsu, EntradaLedger, GapCalculator, NsuLedger}`; `ratelimit/{TokenBucket, ResultadoConsumo}`; `usecase/{AvancarPonteiroUseCase, RecuperarGapUseCase, ResultadoAvanco, ResultadoRecuperacao}`; regras **ArchUnit** de pureza (E1-S04) e **propriedades jqwik** (E1-S05) | Fases 0 e 1.5 concluídas |
| `adapter-fake` | `FakeSourceAdapter` em memória | Fase 1 concluída |
| `adapter-nfe-nacional` | `mapping/CStatMapper`; JAXB `soap/{DistDfeInt, DocZip, RetDistDfeInt, package-info}`; dependências **`java.net.http` + JAXB jakarta + Santuario + WireMock** (CXF removido na E2-S00) | Fase 2, Sub-A concluída |

**Pontos de atenção:**
- Achados da auditoria ainda não corrigidos (ex.: `cStat 108` mapeado como `Ok`; `Credencial` com caminho
  em vez de bytes) estão em `docs/AUDITORIA-1.5.md` §2 e entram pelas subetapas do roadmap.
- O jqwik puxa o JUnit Platform 5.13 implicitamente; alinhar com `junit-bom` quando oportuno.
- Existe um **projeto importado do GitHub** usado como referência de certificados e SOAP. Use-o como
  referência de comportamento. Confira a licença antes de reaproveitar qualquer coisa e reescreva com teste.
  Pergunte ao autor o nome e o caminho quando precisar.

---

## 6. Arquitetura da plataforma

### 6.1 Princípios
> **Postgres decide. O object store guarda. Kafka e Redis aceleram.**
> Se Kafka ou Redis caírem, o sistema para de andar, mas **nunca corrompe estado**. Se o Postgres
> cair, nada avança.

- Clean Architecture + Ports & Adapters. **Domínio puro** no centro, sem I/O e sem framework.
- Control plane (scheduler, metadado) separado do data plane (bytes, parse).
- **Dois estágios:** *fetch/receive* (coleta ou recebe, grava o bruto e registra) e *process*
  (parse, catálogo, manifestação, feed). Parser lento nunca trava a coleta.
- **Kafka e Redis podem ser adiados** (ADR-021): a primeira versão roda com outbox + workers lendo o
  Postgres (`SKIP LOCKED`), com as **portas** já definidas para que Kafka e Redis entrem sem mexer no domínio.

### 6.2 Dimensionamento (conta de padaria — refine com medição)
- CNPJ consultado ocioso: no máximo 1 consulta por hora. Carga de repouso ≈ `N / 3600` req/s
  (100 mil ≈ 28 req/s; 1 milhão ≈ 278 req/s).
- Picos: backfill de CNPJ novo, virada de mês, retomada depois de queda, carga de histórico pelo agente.
- Custo oculto: **handshake mTLS por certificado**. Use cache de `SSLContext` com LRU/TTL e reuso de conexões.
- **[VALIDAR]** se o AN limita por IP ou por volume global. Preveja backoff global por endpoint.
- Consultas por certificado de escritório (`autXML`) concentram muitas empresas em um único CNPJ
  consultado. Esse único par `(fonte, cnpj)` pode ter **volume alto**, com muitas páginas seguidas.

### 6.3 Fluxo pull (coleta)
```
Scheduler ─claim (SKIP LOCKED + lease)─► estado_coleta(fonte, cnpj_consultado, ambiente)
   │ due_at por CNPJ + teto por tenant (fairness)
   ▼
Fetcher (virtual threads, stateless)
   1. carrega o certificado do TITULAR do cnpj_consultado (cofre, só em memória)
   2. adapter.buscarLote(ctx, cursor)            [mTLS, SOAP]
   3. grava ENVELOPE BRUTO + cada docZip descompactado no blob store (sha256)  ◄ grave primeiro
   4. TX Postgres: ledger_nsu (append) + CAS do cursor (versao) + bloqueado_ate + outbox
   5. libera o lease
Processor (via outbox)
   6. parse → roteamento pelo conteúdo (seção 7.3) → catálogo (documento/arquivo/proveniência)
   7. atualiza situação; agenda ciência automática se consentida; gera item no feed
```

### 6.4 Fluxo push (recebimento)
```
Agente / e-mail / upload ─HTTPS─► POST /v1/ingestao/arquivos  (credencial do agente ou do tenant)
   1. limites: tamanho, tipo, taxa por agente
   2. grava BRUTO no blob store; chave de idempotência = sha256  (reenvio = no-op, resposta 200 idêntica)
   3. TX: recebimento (append) + outbox      ◄ responde 202 aqui; o resto é assíncrono
Processor
   4. parse seguro (XXE off, entidades off, limite de profundidade/tamanho)
   5. classifica pelo conteúdo; valida assinaturas; confere CNPJ emitente ∈ empresas do agente
   6. catálogo: documento canônico + arquivo + proveniência(confiança)
   7. gera item no feed
   inválido → recebimento marcado REJEITADO (com motivo), visível ao tenant; o bruto é mantido para diagnóstico
```

### 6.5 Garantias e mecanismos
| Garantia | Mecanismo |
|---|---|
| Um CNPJ por vez | (a) lease no Postgres no claim; (b) lock curto no Redis (otimização, quando existir); (c) **CAS do cursor** (`UPDATE ... WHERE versao = :v`) como proteção final |
| Não-retrocesso de NSU | O cursor só avança por CAS e só se `novo > atual`; teste de propriedade |
| Sem perda | O bruto vai para o blob **antes** do registro; se cair no meio, relê. Ledger e catálogo idempotentes |
| Sem duplicata no catálogo | `UNIQUE (tenant_id, chave_acesso)` em `documento`; `UNIQUE (documento_id, sha256)` em `arquivo` |
| Eventos consistentes | **Outbox transacional**; consumidores idempotentes |
| Ordem por CNPJ | Partição por `cnpj_consultado` (pull) ou por `empresa_id` (catálogo) |
| Feed sem buracos | Sequenciador único (seção 11.3) |
| Falha isolada | DLQ; CNPJ "venenoso" vai para `ERRO_DEFINITIVO` sem parar os demais |
| Fairness | Teto de claims por tenant por ciclo |

### 6.6 Máquina de estados de `estado_coleta`
`ATIVO` → `BLOQUEADO_ATE(t)` → `ATIVO` · `AGUARDANDO_CERTIFICADO` · `CERT_VENCIDO` ·
`PAUSADO_PELO_CLIENTE` · `CONSENTIMENTO_REVOGADO` · `ERRO_DEFINITIVO`.
Toda transição gera uma linha de auditoria (append-only).

### 6.7 Loop de coleta (pseudocódigo normativo)
```
ctx = carregar(estado_coleta); exigir estado == ATIVO e agora >= bloqueado_ate
repita:
  r = adapter.buscarLote(ctx, cursor)            # o adapter já gravou o bruto no blob store
  caso r.desfecho:
    Ok:                     registrar no ledger; avançar o cursor (CAS); se r.temMais, continuar; senão due = agora + PAUSA_PADRAO
    Vazio:                  bloqueado_ate = agora + janela_fonte (≈1h, configurável); due = bloqueado_ate; parar
    LimiteAtingido(t):      bloqueado_ate = agora + t; ALERTA CRÍTICO; parar
    IndisponivelTemporario: backoff exponencial com jitter; NÃO avançar o cursor
    ServicoFora:            circuit breaker da fonte inteira
    ErroAutenticacao:       estado = CERT_*; notificar o tenant; parar
    IndisponivelDefinitivo: estado = ERRO_DEFINITIVO; DLQ; parar
```

---

## 7. Certificados: N por tenant, de dois tipos de titular

### 7.1 Modelo
- Um certificado tem um **titular**, que é uma **empresa** do tenant **ou o próprio escritório**
  (o escritório também é cadastrado como "empresa" do tipo `ESCRITORIO` dentro do seu tenant).
- Cada titular com certificado válido gera um **`estado_coleta`** para o seu CNPJ: o **CNPJ consultado**.
- O uso de certificado de empresa por terceiro (o escritório ou a plataforma) exige **contrato e
  consentimento registrados** (`consentimento` com finalidade `COLETA_DFE`, `evidencia_ref` apontando
  para o contrato). Sem consentimento vigente, não há coleta.

### 7.2 O que cada certificado permite
| Titular do certificado | Consulta | Recebe | Manifesta? |
|---|---|---|---|
| Empresa | o CNPJ da própria empresa (e filiais de mesma raiz **[VALIDAR]**) | notas **contra** ela + eventos | **Sim** (ciência automática possível com consentimento) |
| Escritório | o CNPJ do escritório | notas de clientes que puseram o escritório no `autXML` (**saídas** deles) + o que for do próprio escritório | **Não** pelos clientes |

Uma empresa pode estar coberta pelos dois ao mesmo tempo (o certificado dela para as entradas e o
`autXML` do escritório para as saídas). O catálogo junta tudo.

### 7.3 Roteamento pelo conteúdo (invariante 10)
O documento obtido na consulta de `cnpj_consultado` é atribuído a empresas do tenant pelos CNPJs **no XML**:
- `emit/CNPJ` ∈ empresas do tenant → arquivo da empresa emitente, direção **SAÍDA**.
- `dest/CNPJ` (ou transportador, ou autorizado) ∈ empresas do tenant → direção **ENTRADA** para aquela empresa.
- **As duas coisas** (transferência entre empresas ou filiais do mesmo tenant): a nota é **SAÍDA** para
  uma e **ENTRADA** para a outra. É um documento canônico só, com dois vínculos `documento_empresa`.
- Nenhuma empresa do tenant aparece no XML (ex.: nota do próprio escritório, ou de um cliente que ainda não foi cadastrado):
  - nota do escritório: atribuir à empresa `ESCRITORIO`;
  - caso contrário: **quarentena** `SEM_EMPRESA` (guardar, nunca descartar), com aviso ao tenant ("cadastre o CNPJ X").
- Resumo (`resNFe`) e evento usam os CNPJs que eles trazem. Eventos se vinculam pela chave.

### 7.4 Cofre (`CofreDeCertificados`)
- Upload `.pfx` + senha → **valida**: abre? cadeia ICP-Brasil? CNPJ do certificado == CNPJ do titular?
  está dentro da validade? → **envelope encryption** (DEK por certificado; KEK em KMS/Vault ou, no self-host,
  chave local) → guarda só o cifrado + metadados (fingerprint, validade, titular).
- Decifra **só em memória**, no momento do uso. `char[]`/`byte[]` zerados depois. Nunca em log.
- **Cache de `SSLContext`** por certificado com LRU/TTL e invalidação na renovação.
- **Alertas de vencimento** em 60/30/15/7/1 dias. Vencido → `CERT_VENCIDO` → a coleta para.
- Rotação: o certificado novo substitui o antigo **sem perder cursor ou ledger** (o cursor pertence ao CNPJ consultado, não ao certificado).
- Interface no domínio. Implementações: **local cifrada** (dev/self-host) e plugáveis (Vault/KMS).

---

## 8. Modelo de dados (Postgres — rascunho para revisão)

> Nomes em português. `tenant_id` em toda tabela de negócio, com RLS (seção 13). Revise os índices com o volume previsto.

```sql
-- ===== Cadastro, consentimento, certificados =====
tenant(id, nome, criado_em, ativo)
empresa(id, tenant_id, cnpj, raiz_cnpj, razao_social, tipo,        -- EMPRESA | ESCRITORIO
        ambiente, criado_em, ativo, UNIQUE (tenant_id, cnpj))
consentimento(id, tenant_id, empresa_id, finalidade,               -- COLETA_DFE | CIENCIA_AUTOMATICA | ENTREGA_INTEGRADOR | CREDITO
              escopo, concedido_por, concedido_em, valido_ate,
              revogado_em, evidencia_ref)                          -- append-only; revogação = nova linha
certificado(id, tenant_id, titular_empresa_id, cnpj, fingerprint,
            pfx_cifrado_ref, senha_cifrada_ref, valido_de, valido_ate,
            enviado_em, enviado_por, status)                       -- nada em claro
preferencia_manifestacao(empresa_id, ciencia_automatica boolean DEFAULT false,
                         atualizado_em, atualizado_por)

-- ===== Coleta pull (estado quente, mutável só por CAS) =====
estado_coleta(fonte, cnpj_consultado, ambiente, certificado_id, cursor_token,
              ult_nsu, max_nsu, versao, estado, bloqueado_ate, due_at,
              lease_owner, lease_ate, atualizado_em,
              PRIMARY KEY (fonte, cnpj_consultado, ambiente))
ledger_nsu(id bigserial, fonte, cnpj_consultado, ambiente, nsu, status, schema_doc,
           chave_acesso, blob_ref, sha256, recebido_em, tentativa_id,
           UNIQUE (fonte, cnpj_consultado, ambiente, nsu))          -- ON CONFLICT DO NOTHING
ledger_tentativa(id, fonte, cnpj_consultado, ambiente, iniciada_em, cursor_in, cursor_out,
                 desfecho, cstat_bruto, qtd_docs, duracao_ms, worker_id, blob_envelope_ref)

-- ===== Recebimento push =====
agente(id, tenant_id, nome, so, versao, ultimo_heartbeat, status,
       credencial_hash, ativado_em, revogado_em)
agente_empresa(agente_id, empresa_id)                              -- CNPJs que ele pode enviar
recebimento(id, tenant_id, canal,                                  -- AGENTE | EMAIL | UPLOAD
            agente_id, sha256, blob_ref, nome_original, caminho_origem,
            tamanho, recebido_em, status, motivo_rejeicao,         -- RECEBIDO | ACEITO | REJEITADO | DUPLICADO
            UNIQUE (tenant_id, sha256))

-- ===== Catálogo (a "verdade" que a API expõe) =====
documento(id, tenant_id, chave_acesso, modelo, serie, numero, emissao,
          emitente_cnpj, destinatario_doc, valor_total, situacao,  -- SO_RESUMO | AUTORIZADA | CANCELADA | DENEGADA
          versao_layout, atualizado_em,
          UNIQUE (tenant_id, chave_acesso))
documento_empresa(documento_id, empresa_id, direcao,               -- ENTRADA | SAIDA
                  PRIMARY KEY (documento_id, empresa_id, direcao))
arquivo(id, documento_id, tipo,                                    -- RESUMO | COMPLETO | EVENTO | RESUMO_EVENTO
        evento_tipo, evento_seq, blob_ref, sha256, recebido_em,
        UNIQUE (documento_id, sha256))
proveniencia(id, arquivo_id, origem,                               -- DFE_AN | AGENTE | EMAIL | UPLOAD
             confianca, ledger_nsu_id, recebimento_id, recebido_em) -- append-only
inutilizacao(id, tenant_id, empresa_id, modelo, serie, num_ini, num_fim,
             blob_ref, sha256, recebido_em)
quarentena(id, tenant_id, arquivo_id, motivo, criado_em, resolvido_em)

-- ===== Entrega =====
feed(seq bigint PRIMARY KEY, tenant_id, empresa_id, documento_id,
     tipo_mudanca, ocorrido_em)                                    -- seq atribuído pelo sequenciador (11.3)
integrador(id, nome, client_id, segredo_hash, status, criado_em)
autorizacao_integrador(id, tenant_id, integrador_id, empresa_id, escopos,
                       concedida_por, concedida_em, revogada_em)   -- append-only
webhook_assinatura(id, integrador_id, tenant_id, url, segredo_ref, eventos,
                   xml_inline boolean, status, criado_em)
webhook_entrega(id, assinatura_id, evento_id, seq, tentativa, status_http,
                duracao_ms, ocorrido_em)                           -- append-only
confirmacao_importacao(documento_id, integrador_id, confirmado_em)

-- ===== Transversal =====
auditoria(id, tenant_id, ator, acao, alvo, detalhe_json, ocorrido_em)  -- append-only
outbox(id, topico, chave, payload, criado_em, publicado_em)
```

Observações:
- **Ledger (pull) ≠ catálogo.** O ledger controla o NSU. O catálogo é o que o produto expõe. Um NSU de resumo e
  um NSU de XML completo apontam para o **mesmo** documento.
- `documento.situacao` e as tabelas derivadas são **reconstruíveis** a partir do bruto. Se o parser mudar, reprocesse.
- `status` do ledger segue `StatusNsu`: `BAIXADO | RESUMO | EVENTO | DESCARTADO | FORA_PRAZO | GAP`.
- Particionamento (`ledger_nsu`, `arquivo`, `feed`): só com medição. Projete pensando em hash por tenant/CNPJ.

### 8.1 Object store (MinIO/S3)
```
raw/pull/{tenant}/{cnpj_consultado}/{aaaa}/{mm}/{dd}/{tentativa_id}.envelope.xml.gz
raw/push/{tenant}/{aaaa}/{mm}/{dd}/{sha256}.bin                        # exatamente como chegou
doc/{tenant}/{modelo}/{aaaa}/{mm}/{chave}/{tipo}-{sha256}.xml.gz      # cada arquivo normalizado
```
- SHA-256 em todo objeto, gravado no Postgres. Imutável (versionamento/WORM quando possível). Criptografia em repouso.
- Gravar a mesma chave com o mesmo hash não faz nada.
- **Tiering:** por ora, objetos individuais e **regra de ciclo de vida** da própria storage. Pacotes ZIP mensais
  só com medição e com **catálogo de indireção** (o `BlobRef` lógico aponta para pacote + offset), em pacotes
  **apenas de acréscimo** (eventos chegam depois do "fechamento" do mês).

### 8.2 Tópicos (Kafka quando entrar; antes, tabelas de fila via outbox)
| Tópico | Chave | Conteúdo |
|---|---|---|
| `fetch.comandos` | `cnpj_consultado` | "coletar (fonte, cnpj)" |
| `docs.coletados` | `cnpj_consultado` | metadado + `BlobRef` (sem bytes) |
| `push.recebidos` | `tenant_id` | id do recebimento + `BlobRef` |
| `catalogo.mudancas` | `empresa_id` | documento criado/atualizado → sequenciador do feed |
| `manifestacao.comandos` | `cnpj` | "manifestar chave X tipo Y" |
| `webhook.entregas` | `assinatura_id` | evento a entregar |
| `*.dlq` | — | falha com motivo |

Mensagens versionadas (`versao_schema`) e chave de idempotência em todas.

---

## 9. Contrato do adapter (porta pull — mantém o da v1)

`SourceAdapter` com `descritor()`, `autenticar(Credencial)`, `buscarLote`, `buscarUm`, `manifestar`.
O `Desfecho` é sealed (`Ok`, `Vazio`, `LimiteAtingido`, `IndisponivelTemporario`, `IndisponivelDefinitivo`,
`ErroAutenticacao`, `ServicoFora`). O `Cursor` é opaco. O `Documento` só tem metadado + `BlobRef`.
Veja `docs/ARCHITECTURE.md`, seção 5.

**Ajustes da v2:**
- `ContextoConsulta` carrega o `cnpj_consultado` e o `certificado_id`, nunca a empresa "dona". A atribuição
  acontece depois, no processor (seção 7.3).
- **`Credencial`** = bytes do `.pfx` + senha, só em memória, entregues pelo cofre. Nunca um caminho de disco no domínio.
- Porta **`BlobStore`** no domínio (`gravar(chave, bytes, sha256) → BlobRef`), com implementação em memória nos testes.

**Porta push** (nova): `ReceberArquivoUseCase(canal, credencialOrigem, bytes) → Recebimento` e
`ProcessarRecebimentoUseCase`, com as portas `ParserDfe` (classifica e extrai metadado), `ValidadorAssinatura`
e `Catalogo`. Mesmas regras: sem XML no domínio além do necessário para extrair metadados no processor
(o processor é borda, não core).

---

## 10. Agente local (`fiscal-agent`, Go — repositório separado)

### 10.1 Princípio
**Não integrar com o ERP, e sim com o que todo ERP precisa produzir: o XML autorizado em disco.** Isso
funciona com TOTVS, Senior ou qualquer outro sem código específico. Os perfis de ERP são **dicas**, nunca requisito.

### 10.2 Funções
1. **Descoberta:** na instalação (e sob demanda), varre os discos locais e de rede mapeados **por amostragem**,
   procurando XML de DF-e, agrupa por CNPJ emitente e pasta e sugere: *"12.340 NF-e do CNPJ X em `D:\ERP\XML`. Monitorar?"*.
   Usa perfis de caminhos conhecidos (TOTVS, Senior...) para acelerar. Os padrões de caminho encontrados
   (anonimizados) alimentam os perfis.
2. **Vigilância:** `fsnotify` **e** varredura periódica (o `fsnotify` perde eventos em pasta de rede e em alguns ERPs).
   Espera o arquivo **ficar estável** (tamanho e mtime parados) antes de ler.
3. **Classificação pelo conteúdo:** lê só o necessário em streaming, ou seja, o elemento raiz e poucos campos:
   - raízes: `nfeProc` (`mod` 55/65), `procEventoNFe`, `procInutNFe`, `cteProc`, `procEventoCTe`, `CFe`...;
   - codificação: ISO-8859-1 ou UTF-8, com ou sem BOM;
   - abre `.zip` e trata cada arquivo de dentro.
   Raiz desconhecida: ignora e conta na telemetria (é assim que se descobrem layouts novos).
4. **Filtro mínimo:** descarta só o óbvio (XML de nota sem protocolo de autorização, que é rascunho do ERP).
   **Todo o resto quem decide é a plataforma.**
5. **Envio:** `POST /v1/ingestao/arquivos` com o sha256 como chave de idempotência. Fila local com retentativa e
   backoff. Funciona offline e envia depois.
6. **Estado local:** SQLite (`modernc.org/sqlite`, Go puro, sem CGO) com `caminho, tamanho, mtime, sha256,
   status_envio`. É **só otimização**: quem decide duplicidade é o servidor.
7. **Carga do histórico:** em ritmo controlado, com limite de banda e de I/O e janela de horário configurável.
8. **Heartbeat:** versão, última varredura, contagens por tipo, erros, espaço da fila.
9. **Configuração remota:** pastas, inclusões e exclusões, limites e janelas vêm do portal. O agente só busca.

### 10.3 Comportamento no servidor do cliente
- Prioridade baixa de CPU e I/O. Em PME, costuma ser a mesma máquina do ERP.
- **Só leitura** nas pastas. **Só saída HTTPS.** Nenhuma porta aberta.
- Roda como serviço (`kardianos/service`) com uma conta de privilégio mínimo.

### 10.4 Instalação, identidade e atualização
- **Instalador assinado** (MSI no Windows; `.deb`/`.rpm` + systemd no Linux numa fase seguinte).
- **Código de ativação de uso único**, gerado no portal pelo tenant. O agente o troca por uma credencial
  própria (par de chaves ou token rotacionável), guardada protegida pelo SO (DPAPI no Windows; arquivo 0600 no Linux).
- Credencial revogável pelo tenant. A plataforma recusa XML cujo emitente não esteja em `agente_empresa`.
- **Auto-update** com manifesto assinado e liberação em etapas (canário → todos). Rollback automático se não houver heartbeat.

### 10.5 Plataformas
- **Windows primeiro.** **Linux logo em seguida, na mesma fase** (o Go compila para os dois; o código
  específico de SO fica isolado em `internal/platform/{windows,linux}`).

### 10.6 Stack e estrutura sugerida
```
fiscal-agent/
├── cmd/agent/               # main: serviço + CLI (instalar, ativar, diagnosticar)
├── internal/
│   ├── discovery/           # varredura por amostragem + perfis de ERP
│   ├── watch/               # fsnotify + varredura periódica + detecção de estabilidade
│   ├── classify/            # sniffing de raiz XML, encoding, zip
│   ├── store/               # SQLite local (estado + fila)
│   ├── uplink/              # cliente HTTP, idempotência, backoff, limite de banda
│   ├── config/              # configuração remota + local
│   ├── update/              # auto-update assinado
│   └── platform/            # windows/linux (serviço, DPAPI, prioridade de I/O)
├── api/openapi.yaml         # CÓPIA versionada do contrato da plataforma
└── testdata/                # XMLs sintéticos (golden files)
```
Testes: `go test` com tabelas, golden files de classificação, servidor HTTP fake para o uplink e sistema
de arquivos em diretório temporário para a vigilância. **Mesmo TDD do projeto Java.**

### 10.7 Auditoria de saídas (o que garante que o agente não perdeu nada)
- **Lacuna de numeração** por `empresa + modelo + série`: um buraco é pendência até aparecer a nota ou uma
  inutilização. Notas denegadas também consomem número. Defina um **ponto de corte** no cadastro, para não
  apontar lacunas anteriores à adesão.
- **Cruzamento:** se uma saída chegou pelo `autXML` ou pelo destinatário e **não** pelo agente, o agente ou a pasta estão errados → alerta.
- **Agente sem heartbeat** há mais de 24h → alerta ao tenant.

---

## 11. API de entrega e integradores

### 11.1 Autenticação e autorização
- O **integrador** se cadastra uma vez e recebe credenciais **OAuth2 client credentials**.
- O **tenant autoriza** o integrador para algumas empresas e escopos (`documentos:ler`, `webhooks`, `confirmacao`).
  Isso gera uma linha em `autorizacao_integrador` e um `consentimento` com finalidade `ENTREGA_INTEGRADOR`.
- Todo token é verificado contra a autorização **vigente** a cada requisição (invariante 12). Revogou, o próximo request já é negado.

### 11.2 Endpoints (v1)
```
GET  /v1/feed?desde=<seq>&limite=500        # mudanças em ordem; devolve itens + proximo_desde
GET  /v1/documentos/{chave}                 # metadado + arquivos + eventos + proveniências
GET  /v1/documentos/{chave}/xml             # XML completo original, byte a byte
GET  /v1/arquivos/{id}/xml                  # qualquer arquivo (evento, resumo)
POST /v1/exportacoes                        # ZIP por empresa/período (assíncrono)
GET  /v1/exportacoes/{id}
POST /v1/documentos/{chave}/confirmacao     # "importei no meu sistema"
POST /v1/webhooks / GET / DELETE            # gerenciar assinaturas
POST /v1/webhooks/{id}/reenviar?desde=<seq>
```
Paginação por cursor (nunca por offset). Versionamento na URL. Erros em RFC 9457 (problem+json).

### 11.3 Feed sem buracos (invariante 11)
**Problema clássico:** sequences do Postgres são atribuídas antes do commit. Uma transação com `seq=11` pode
confirmar antes da de `seq=10`. Um leitor que já leu até 11 **perderia o 10** para sempre.
**Solução escolhida:** um **sequenciador único**. Os processors gravam mudanças no outbox. Um único worker
(com lease) consome em ordem e grava em `feed` com `seq = último + 1`, numa transação serial. O feed
só expõe itens já commitados por esse único escritor, então não há buraco.
(A alternativa, uma marca d'água por `pg_snapshot_xmin`, fica registrada no ADR, mas não é a escolha.)
**Teste de propriedade:** com N escritores concorrentes e leitores intercalados, nenhum leitor pula um item.

### 11.4 Webhooks
- **Eventos:** `documento.recebido` (nota nova, de entrada ou saída, para aquela empresa) e `documento.atualizado`
  (chegou o XML completo, cancelamento, CC-e ou manifestação).
- **Payload:**
  - `evento_id`, `tipo`, `seq`, `ocorrido_em`, `empresa`, `direcao`;
  - metadados (`chave`, `modelo`, `emitente`, `destinatario`, `valor`, `situacao`);
  - `xml_url` (link assinado, de vida curta);
  - XML inline em base64 se a assinatura de webhook tiver `xml_inline=true` e o arquivo couber no limite.
- **Garantias documentadas:**
  - **entrega pelo menos uma vez:** o integrador deduplica por `evento_id`;
  - **sem garantia de ordem:** use `seq`;
  - **assinatura HMAC-SHA256** de `timestamp + "." + corpo`, com cabeçalho de timestamp e janela de tolerância (contra replay);
  - **retentativa** com backoff exponencial + jitter por até ~72h; depois disso fica como `FALHOU`, visível nos painéis;
  - endpoint que falha sempre é **suspenso** com aviso; reenvio sob demanda por `seq`.
- Origem: gerado pelo **outbox** na mesma transação do feed, e um despachante entrega. Todo o log fica em `webhook_entrega`.
- **Regra para o integrador:** *webhook para reagir rápido, feed para conferir.* O feed é a fonte da verdade.

### 11.5 Status de entrega no painel do escritório
`recebida → entregue ao integrador (2xx) → importada (confirmação)`. Responde ao "cadê a nota?".

### 11.6 Adoção pelo integrador
- **Sandbox** com tenant, empresas e notas sintéticas e o botão "disparar webhook de teste".
- **OpenAPI** público, exemplos em `curl` e verificação de HMAC em pelo menos 3 linguagens.
- **Validador de endpoint**: testa a resposta, a verificação de HMAC e a deduplicação.

---

## 12. (reservado) E-mail em cópia — fase 9

Caixa `xml+{token_empresa}@dominio`. Só aceita anexos XML/ZIP/PDF dentro dos limites, com antivírus e
proteção contra zip bomb, e uma whitelist de remetentes por empresa (opcional). Cai na mesma porta push com canal `EMAIL`.
Os PDFs (DANFE) ficam guardados como anexo informativo, **nunca** como fonte de dado.

---

## 13. Multi-tenancy, segurança e privilégio mínimo

- `tenant_id` em toda tabela de negócio e **Row-Level Security** como defesa em profundidade. Teste
  provando que o tenant A não lê nada do B, e que um integrador não lê empresa sem autorização.
- Prefixo de tenant no object store e, se possível, chave de cifra por tenant.
- **Superfícies não confiáveis:** tudo que chega por push (agente, e-mail, upload) e o corpo das respostas
  dos integradores. Parser XML com **DTD/entidades externas desligadas**, limites de tamanho, profundidade e
  quantidade de arquivos em ZIP.
- Fetchers só recebem o certificado do CNPJ que vão consultar. Processors e a API **não** acessam certificados.
- Credenciais de infra via variável de ambiente ou secret store, nunca no repositório.
- Rate limit na API de recebimento (por agente) e na API de entrega (por integrador).

---

## 14. Consentimento, contrato, LGPD e crédito (requisito desde já)

- **Finalidades separadas** em `consentimento.finalidade`: `COLETA_DFE`, `CIENCIA_AUTOMATICA`,
  `ENTREGA_INTEGRADOR`, `CREDITO` (futuro). O acesso a dado para uma finalidade **verifica** essa finalidade na hora do acesso.
- **Uso de certificado de empresa por terceiro:** só com contrato e consentimento registrados
  (`evidencia_ref`). O código **materializa** o que o contrato definir. Quem é controlador e quem é
  operador deve ser alinhado com o jurídico.
- **Revogação:** a coleta para (`CONSENTIMENTO_REVOGADO`) e a entrega para o integrador para. A retenção
  e o expurgo seguem uma política definida (guarda fiscal costuma ser de 5 anos **[VALIDAR com jurídico]**), com expurgo auditado.
- **Trilha:** quem acessou, baixou, manifestou ou entregou, quando e com qual consentimento. Imutável.
- **Titular:** exportação e expurgo por empresa, com auditoria.
- **Dados pessoais em NF-e** (nomes, CPFs, endereços): mascarados em logs; ambientes de teste só com dados sintéticos.
- **Crédito:** não implementar. Só garantir `CREDITO` como finalidade e `confianca` na proveniência.

---

## 15. Estratégia de testes

**Java:**
1. **Unitários** (JUnit 5 + AssertJ/Mockito) do domínio puro. **Preservar os existentes** (≈38 + 5 + 10 + 5).
2. **Propriedade** (jqwik): `GapCalculator`, `AvancarPonteiro` (o cursor nunca decresce), idempotência do
   ledger e do catálogo, `TokenBucket`, **feed sem buracos** com concorrência, **roteamento pelo conteúdo**.
3. **Contrato do `SourceAdapter`:** suíte abstrata rodando contra o `FakeSourceAdapter` e o `NfeNacionalAdapter` (WireMock).
4. **Adapter com WireMock:** 138 com vários `docZip`, 137, 656, 632/108/109, timeout, 5xx, resposta
   malformada, `docZip` corrompido, certificado inválido.
5. **Porta push:** XML válido, duplicado, sem protocolo, assinatura inválida, emitente fora do agente,
   **XXE**, zip bomb, ISO-8859-1, BOM.
6. **Integração** (Testcontainers): Postgres (CAS, RLS, outbox, sequenciador), MinIO; Kafka e Redis quando entrarem.
7. **Caos:**
   - queda entre gravar o bruto e registrar → relê, sem perda e sem duplicata;
   - dois fetchers no mesmo CNPJ → só um avança;
   - relay publica duas vezes → consumidor idempotente;
   - integrador fora do ar por 3 dias → retentativa, depois reenvio por `seq`, sem perda.
8. **Contrato da API** (OpenAPI): testes gerados ou verificados contra a especificação; o agente usa a mesma especificação.
9. **Carga** (fase 10): N mil CNPJs com adapter fake de latência controlada; carga de histórico do agente.
10. **Smoke manual em homologação** (fora do CI): certificados, `cStat` reais, `autXML`.

**Go (agente):** tabelas + golden files; FS temporário; servidor HTTP fake; teste de retomada da fila depois de matar o processo.

**Regras:** teste falhando **antes**; nomes descritivos em pt-BR; **`Clock` injetável** (sem `Thread.sleep`
e sem `Instant.now()` solto no domínio); cada invariante da seção 2.2 com teste dedicado.

---

## 16. Plano de fases

> Cada fase em TDD, com Definição de Pronto (DoD) e parada para resumo e OK. **Sem prazo**: a fase só
> termina quando o DoD estiver verde.

| Fase | Conteúdo | DoD resumido |
|---|---|---|
| 0 ✅ | Domínio puro | revalidar `:domain:test` |
| 1 ✅ | Contrato do adapter + fake | revalidar |
| 1.5 ✅ | Saneamento: wrapper, `settings`, auditoria, correção dos docs | concluída em 2026-10-22 |
| **2** | **Adapter NF-e AN** — 2A ✅ CStatMapper + JAXB · **2B** `buscarLote` (envelope `distDFeInt`, decode de `docZip`, classificação, porta `BlobStore`, envelope bruto) · **2C** `buscarUm` + `consChNFe` · **2D** carregador de `.pfx` (inclui legado) + `SSLContext` por certificado + cache · **2E** `manifestar` (XML-DSig, C14N, algoritmo **[VALIDAR]**) | suíte de contrato verde; nenhum `cStat` no core; smoke de homologação documentado, **incluindo o `autXML`** |
| **3** | **Persistência**: 3A Postgres (`estado_coleta`, `ledger_*`, CAS, outbox, Flyway, RLS) · 3B MinIO (`BlobStore`, layout, sha256) · 3C **catálogo** + processor de parse + **roteamento pelo conteúdo** + quarentena | testes de integração e de caos verdes; 1 CNPJ (empresa) e 1 CNPJ (escritório/`autXML`) ponta a ponta local |
| **4** | **Scheduler + cofre**: claim `SKIP LOCKED` + lease, `due_at`, fairness; `CofreDeCertificados` local cifrado; consentimento; estados por CNPJ; alertas de vencimento | coleta contínua de vários CNPJs sem 656 em simulação |
| **5** | **Porta push + upload manual** (`/v1/ingestao/arquivos`), validação de assinatura, proveniência e confiança | todos os casos do item 5 da seção 15 verdes |
| **6** | **API de entrega v1**: feed com sequenciador, documentos/XML, OAuth de integrador, autorização, sandbox, OpenAPI · depois **webhooks** (HMAC, retentativa, reenvio, painel de entregas) | teste de propriedade do feed; caos "integrador fora do ar" |
| **7** | **Agente Go v1** (Windows + Linux): descoberta, vigilância, classificação, fila, envio, heartbeat, ativação, instalador | carga de histórico + vigilância contínua contra a plataforma local |
| **8** | **Auditoria de saídas**: lacunas por série, cruzamento entre canais, alertas; auto-update do agente; perfis de ERP | relatório de lacunas confiável em base sintética |
| **9** | E-mail em cópia; confirmação de importação; export ZIP; perfis TOTVS/Senior refinados | — |
| **10** | Endurecimento: observabilidade, carga, runbooks, Kafka/Redis **se a medição pedir** | — |
| **11+** | CT-e (`CTeDistribuicaoDFe`), conector de banco no agente, NFS-e ADN, estaduais | sem tocar no core |

**Regra de ordem:** a **janela de retenção** do AN pune quem demora a coletar, então o esqueleto de
coleta (Fases 2 → 3) vem antes de tudo.

---

## 17. Decisões (ADRs)

### 17.1 Vigentes (`docs/DECISIONS.md`)
ADR-001 Java 21 + virtual threads · ADR-002 Gradle KTS multi-módulo · ADR-003 blob gravado pelo
adapter/infra · **ADR-004 manifestação por CNPJ: ALTERAR o padrão para ciência automática DESLIGADA,
exigindo consentimento explícito** · ADR-005 ledger append-only com reinserção ignorada · ADR-006
pacote `br.com.doc` · ADR-007 escopo inicial NF-e 55 AN · ADR-008 self-host via docker-compose.

### 17.2 Novas (registrar em `docs/DECISIONS.md` — data 2026-10-20)
| ADR | Decisão | Status |
|---|---|---|
| 009 | **Plataforma em Java 21; agente local em Go.** O Go foi descartado para o motor (maturidade de XML-DSig/SOAP, projeto de referência em Java) e escolhido para o agente (binário único, sem runtime, serviço leve em Windows/Linux) | Aceito |
| 010 | **Postgres é a fonte da verdade.** Cursor, `bloqueado_ate`, ledger, catálogo e feed vivem nele; lock em Redis é otimização; a proteção final é o CAS | Aceito |
| 011 | **Dois estágios: fetch/receive e process** | Aceito |
| 012 | **Grave primeiro, avance depois** | Aceito |
| 013 | **Multi-tenant desde o início** (tenant = escritório; RLS; prefixos de blob) | Aceito |
| 014 | **Cofre de certificados e consentimento** como capacidades de primeira classe; **N certificados por tenant**, com titular empresa ou escritório | Aceito |
| 015 | **Outbox transacional**; consumidores idempotentes | Aceito |
| 016 | **Finalidades de consentimento separadas** (coleta, ciência, entrega, crédito), verificadas na hora do acesso | Aceito |
| 017 | **Porta push** como segunda entrada; agente, e-mail e upload convergem nela | Aceito |
| 018 | **Catálogo canônico** (1 documento por tenant+chave, N arquivos, N proveniências com confiança); **ledger ≠ catálogo**; **atribuição pelo conteúdo** | Aceito |
| 019 | **`autXML` com certificado do escritório** como canal de saídas sem instalação | Aceito (validar em homologação) |
| 020 | **Feed sequencial com sequenciador único** como fonte da verdade da entrega; webhook só notifica | Aceito |
| 021 | **Kafka e Redis adiáveis**: começa com outbox + filas no Postgres e portas prontas; entram quando a medição pedir | Proposto |
| 022 | **Licença**: avaliar *open core* (motor Apache-2.0, partes de produto fechadas) × **AGPL-3.0** (impede que alguém hospede o motor e concorra sem abrir) | Proposto — decidir antes do primeiro release público |
| 023 | **Integrador como ator de primeira classe** (OAuth2 client credentials + autorização por tenant/empresa/escopo) | Aceito |
| 024 | **Agente sem regra fiscal**: só descobre, vigia, classifica e envia; tudo o mais na plataforma | Aceito |
| 025 | **JDBI + Flyway** para acesso a dados (sem JPA) | Aceito (2026-10-21) |
| 026 | **jqwik** para testes de propriedade | Aceito (2026-10-21) |
| 027 | **Spring Boot só nos `app-*`**; resto sem Spring (ArchUnit) | Aceito (2026-10-21) |
| 028 | **SLF4J + Logback JSON**; Prometheus/Loki/Grafana na Fase 10 | Aceito (2026-10-21) |
| 029 | Modo autônomo com **2 janelas/dia** (08:00 e 13:00) | Aceito (2026-10-21) |

---

## 18. Correções pendentes nos docs existentes (Fase 1.5)

- **README:** atualizar o status (Fases 0, 1 e 2A concluídas), incluir a visão de hub (seção 1), apontar para este arquivo, licença "em decisão (ADR-022)".
- **ARCHITECTURE.md:**
  - 3.1: trocar "~1 lote/hora" por **bloqueio por CNPJ consultado (~1h) quando não há documento novo**, configurável.
  - 5.3: revisar o mapeamento do `632` (seção 3.2 deste arquivo).
  - 4.3/4.4: incluir fetch × process, outbox, Postgres como fonte da verdade, **porta push, catálogo, feed**.
  - 6: ADR-004 com o padrão desligado.
  - 8: módulos da seção 19 + repositório separado `fiscal-agent`.
- **DECISIONS.md:** ADRs 009–024 da seção 17.2; marcar como resolvidas as pendências antigas:
  - `Credencial` = bytes + senha em memória;
  - lib SOAP = HTTP + JAXB + Santuario;
  - `BlobRef` = chave determinística + sha256;
  - particionamento só com medição.
- **`.clinerules`:** substituir por um ponteiro para este arquivo (ou remover), para não haver duas fontes de regra.

---

## 19. Módulos

```
fiscal-ingest/                         (este repositório)
├── domain/                  # PURO: Nsu, Cursor, Desfecho, ledger, Documento canônico, Direcao,
│                            #       Proveniencia, Confianca, Consentimento, portas (BlobStore, Catalogo...)
├── adapter-fake/
├── adapter-nfe-nacional/    # pull — SOAP distDFe/consNSU/consChNFe/RecepcaoEvento
├── ingest-push/             # parse seguro, classificação, validação de assinatura, roteamento
├── cert-vault/              # CofreDeCertificados (local cifrado + plugáveis)
├── infra-postgres/          # ledger, estado_coleta, catálogo, feed, outbox, RLS, Flyway
├── infra-blob-minio/
├── infra-stream-kafka/      # (fase 10, se medido)
├── infra-ratelimit-redis/   # (fase 10, se medido)
├── app-scheduler/           # control plane
├── app-worker/              # fetcher + processor (+ sequenciador do feed, + despachante de webhook)
├── app-api/                 # API de recebimento (agente/upload) + API de entrega (integradores)
├── api-contract/            # openapi.yaml versionado (fonte para o agente e para os integradores)
└── docker-compose.yml

fiscal-agent/                          (repositório separado, Go — seção 10.6)
```
**Regra de dependência:** `domain` não depende de nada; `adapter-*`, `ingest-push`, `cert-vault` e `infra-*`
dependem de `domain`; os `app-*` compõem tudo. Verificar com ArchUnit.
Começar com **poucos apps** (`app-worker` faz fetch + process). Separar só quando a medição pedir.

---

## 20. Observabilidade e operação

**Métricas** (por tenant e fonte, com cardinalidade controlada):
- **Coleta:**
  - lag de NSU (`max_nsu - ult_nsu`);
  - idade do último sucesso;
  - **contagem de `656` (deve ser ZERO)**;
  - documentos por hora;
  - `Desfecho` por tipo;
  - latência do AN (p50/p95/p99);
  - CNPJs por estado;
  - certificados a vencer.
- **Push:**
  - recebimentos por canal e status;
  - taxa de rejeição por motivo;
  - agentes sem heartbeat;
  - versão dos agentes;
  - fila local reportada.
- **Catálogo:**
  - documentos em quarentena;
  - resumos sem XML completo há mais de X dias;
  - lacunas de numeração abertas.
- **Entrega:**
  - lag do feed por integrador (`seq` atual − último lido/confirmado);
  - taxa de sucesso dos webhooks;
  - assinaturas suspensas;
  - idade do outbox não publicado.
- **Recursos:** cache de `SSLContext`, GC, threads virtuais.

**Logs:** JSON estruturado com `tenant`, `cnpj` **mascarado**, `fonte`, `nsu`, `tentativa_id`, `recebimento_id`,
`evento_id`. **Nunca** segredo ou XML.
**Tracing:** `correlation_id` propagado em fetch/receive → processor → feed → webhook.
**Runbooks** (fase 10): CNPJ bloqueado, certificado vencido, AN indisponível, DLQ crescendo, agente offline,
integrador falhando, reprocessar a partir do bruto, restore.

---

## 21. Verificação nas fontes oficiais (antes de congelar testes)

Registrar cada item (fonte + data) em `docs/FONTES-OFICIAIS.md`. Se a norma mudar, mude **o teste primeiro**.
1. Duração do bloqueio depois do `137` e o comportamento com `ultNSU < maxNSU`.
2. Limites e `cStat` de `consNSU`/`consChNFe`; duração do bloqueio por `656`.
3. Lista vigente de `cStat` (em especial o `632`) e o mapeamento para `Desfecho`.
4. Atores atendidos pela distribuição (NT 2014.002 e atualizações); se o **emitente** recebe os eventos das próprias notas.
5. **`autXML`:** limite de ocorrências; confirmação de que o autorizado recebe a NF-e pela distribuição; comportamento para CT-e.
6. Manifestação: códigos, prazos, efeito sobre a liberação do `procNFe`; quem pode assinar.
7. Assinatura: algoritmo (SHA-1 × SHA-256), C14N, formato do `Id` do evento.
8. Qual certificado consulta qual CNPJ (raiz, filiais, procurador).
9. URLs e versões dos WSDLs/XSDs (produção e homologação) e a política de versionamento das NTs.
10. Janela real de retenção do AN.
11. Limites por IP ou volume global.
12. Serviço de **consulta de situação/protocolo** por chave para confirmar documentos recebidos por push (limites).
13. Distribuição de **NFCom/NF3e**; cronograma do **IBS/CBS** na NF-e; adesão real ao **ADN da NFS-e**.
14. Prazo legal de guarda dos XML (com o jurídico).

---

## 22. Perguntas em aberto (não bloqueiam; responder quando a fase chegar)

1. Nome, caminho e licença do **projeto de referência importado do GitHub** (Fase 2).
2. **Cofre** de produção: Vault, KMS de qual nuvem, ou só local cifrado por ora? (Fase 4)
3. **Volume inicial real** (tenants, CNPJs, docs por mês) para calibrar pools e índices (Fase 3/10).
4. **Contrato** de uso de certificado por terceiros, e de ciência automática: modelo, controlador/operador, retenção (Fase 4).
5. **Homologação:** certificado de teste e CNPJ habilitado para smoke, incluindo o teste de `autXML` (Fase 2).
6. **Licença** (ADR-022) — antes do primeiro release público.
7. Domínio e provedor de **e-mail** para o canal D (Fase 9).

---

## 23. Convenções

- **Java 21:** `record`, `sealed`, pattern matching; imutabilidade no domínio; `Clock` injetado.
- Sem framework no `domain` nem nos módulos de biblioteca. **Spring Boot 3.x só nos `app-*`** (ADR-027);
  persistência com **JDBI + Flyway** (ADR-025); testes de propriedade com **jqwik** (ADR-026);
  logs com **SLF4J + Logback JSON** (ADR-028).
- O domínio comunica falha por `Desfecho` e por tipos de resultado, não por exceção de transporte.
- Nomes de domínio em português; termos técnicos genéricos podem ficar em inglês.
- **Go:** `gofmt`/`go vet`/`staticcheck`; erros com contexto (`fmt.Errorf("...: %w", err)`); sem pânico em fluxo normal.
- **Commits** pequenos, em pt-BR (`feat/fix/test/docs/refactor`). **Um incremento TDD = um commit**.
- Antes de cada parada: testes verdes, resumo curto, **próximo passo proposto**.

## 24. Checklist de "pronto" para qualquer incremento

- [ ] Teste escrito **antes**, visto falhar e depois passando.
- [ ] Testes antigos continuam verdes.
- [ ] Nenhuma invariante da seção 2.2 enfraquecida (existe teste para a invariante relevante).
- [ ] Nada de `cStat`/HTTP no core; nada de bytes no `Documento`.
- [ ] Entrada push tratada como não confiável; parser sem XXE.
- [ ] Sem segredo, XML real ou dado pessoal em código, log ou fixture.
- [ ] Docs e ADRs atualizados se houve decisão.
- [ ] Resumo entregue ao autor, com o próximo passo.

---

*Fim. Comece pela seção 0. Na dúvida entre "avançar" e "perguntar", **pergunte**.*
