# Registro de Decisões (ADR leve)

> Formato simples: contexto → decisão → consequência.
> Numerar sequencialmente. Decisões antigas não são apagadas (histórico).

---

## ADR-001 — Java 21+ com virtual threads

**Data:** 2026-07-09
**Contexto:** Carga I/O-bound (SOAP/REST, blob store, stream). Necessidade de concorrência barata para milhares de CNPJs, reaproveitando ecossistema SOAP/JAXB e mTLS.
**Decisão:** Java 21+ com virtual threads. Alternativas consideradas: Go (falta de maturidade em libs fiscais), Kotlin (viável, mas usuário preferiu Java).
**Consequências:** Projeta concorrência massiva sem thread pool tuning; uso de `Executors.newVirtualThreadPerTaskExecutor`. Bibliotecas SOAP/JAXB maduras disponíveis.

---

## ADR-002 — Gradle (Kotlin DSL) multi-módulo

**Data:** 2026-07-09
**Contexto:** Necessidade de isolar o domínio puro das bordas (adapters, infra) para garantir Clean Architecture. Build reproduzível e tipado.
**Decisão:** Gradle com Kotlin DSL, layout multi-módulo (domain no centro, adapters e infra dependentes).
**Alternativas:** Maven (mais verboso para multi-módulo, menos flexível).
**Consequências:** O módulo `domain` tem **zero dependência de framework/I/O** — aplicável como regra de build (verificar com `gradle dependencies`).

---

## ADR-003 — Object store escrito pelo adapter (infra), não pelo core

**Data:** 2026-07-09
**Contexto:** O XML é volumoso e nunca deve passar pelo core/scheduler. Quem persiste o blob?
**Decisão:** O **adapter (infra)** escreve o XML no object store e devolve apenas `BlobRef` ao core.
**Consequências:** Core trafega só metadado; data plane workers podem atuar sobre `BlobRef` para parse/transformação sem onerar o control plane.

---

## ADR-004 — Manifestação por CNPJ (revisado: ciência automática desligada por padrão)

**Data:** 2026-07-09
**Contexto:** Resumo (resNFe) chega sem XML completo; a manifestação (ciência) é o gatilho para liberar o procNFe num NSU posterior. Necessidade de flexibilidade: alguns CNPjs querem automático, outros manual por nota.
**Decisão:** Preferência de manifestação é **por CNPJ**, persistida no banco. Default = **ciência automática**. Backend consulta antes de agir.
**Consequências:** Novo campo de configuração no cadastro de CNPJ; caso de uso de manifestação lê essa preferência. JSON com notas pendentes pode ser exposto para manifestação manual.

> **Revisado em 2026-10-21 (ver ADR-016):** o padrão passa a ser **ciência automática DESLIGADA**.
> A ciência é uma declaração em nome do destinatário e só pode ser ligada com consentimento explícito
> da empresa (finalidade `CIENCIA_AUTOMATICA`). Confirmação, desconhecimento e operação não realizada
> são sempre manuais, com auditoria. Só o certificado da própria empresa pode manifestar.

---

## ADR-005 — Ledger append-only com idempotência "ignora silenciosamente"

**Data:** 2026-07-09
**Contexto:** Reentrega de NSUs pode ocorrer (replay, retentativa). Como tratar duplicidade?
**Decisão:** Re-inserir mesmo `(fonte, cnpj, nsu)` é **ignorado silenciosamente** (não lança erro). `GapCalculator` com ledger vazio retorna `[]`.
**Consequências:** Ledger é tolerante a reentregas; auditoria sempre disponível; não há "falso gap" por reprocessamento.

---

## ADR-006 — Pacote raiz `br.com.doc`

**Data:** 2026-07-09
**Contexto:** Definição de namespace Java.
**Decisão:** Pacote raiz `br.com.doc.ingest` (base `br.com.doc`).
**Consequências:** Todos os módulos sob `br.com.doc.ingest.<modulo>`.

---

## ADR-007 — Escopo v1 = NF-e (55) Ambiente Nacional

**Data:** 2026-07-09
**Contexto:** Não implementar tudo de uma vez. Foco inicial.
**Decisão:** v1 cobre **NF-e (modelo 55)** do Ambiente Nacional: `distNSU` + `consNSU` + manifestação + ledger + gap-recovery.
**Consequências:** Demais fontes (CT-e, MDF-e, NFS-e, estaduais) entram pela **mesma porta** `SourceAdapter`, sem tocar no core — apenas em fases futuras.

---

## ADR-008 — Self-host via docker-compose, open source, sem deploy em servidor (por ora)

**Data:** 2026-07-09
**Contexto:** Projeto open source, foco em código/testes, sem necessidade de produção imediata.
**Decisão:** Entrega via `docker-compose.yml` para subir tudo localmente. Sem pipeline de deploy em servidor nesta fase.
**Consequências:** Configurações de produção (k8s, CI/CD de deploy) ficam para depois; foco em qualidade e testes agora.

---


## Decisões da v2 (2026-10-21)

> Detalhes em `CLAUDE.md` §17.2.

## ADR-009 — Plataforma em Java 21; agente local em Go

**Data:** 2026-10-21
**Contexto:** Avaliação de Go × Java para o motor e para o agente que roda no cliente.
**Decisão:** Motor em **Java 21** (maturidade de XML-DSig/SOAP/JAXB, projeto de referência em Java, equipe produtiva). Agente local em **Go** (binário único, sem runtime, serviço leve em Windows e Linux).
**Consequências:** Dois repositórios e duas toolchains; contrato entre eles por OpenAPI versionado.

---

## ADR-010 — Postgres é a fonte da verdade

**Data:** 2026-10-21
**Contexto:** Escala com Kafka/Redis/Postgres; quem decide o estado?
**Decisão:** Cursor, `bloqueado_ate`, ledger, catálogo e feed vivem no Postgres. Lock em Redis é otimização; a proteção final contra corrida é o **CAS por versão**.
**Consequências:** Se Redis/Kafka caírem, o sistema para de andar mas não corrompe estado.

---

## ADR-011 — Dois estágios: fetch/receive e process

**Data:** 2026-10-21
**Contexto:** Parse e regras não podem atrasar a coleta.
**Decisão:** O estágio de coleta/recebimento só obtém, grava o bruto e registra. Parse, catálogo, manifestação e feed rodam no estágio de processamento.
**Consequências:** Parser lento nunca trava a coleta; reprocessamento a partir do bruto.

---

## ADR-012 — Grave primeiro, avance depois

**Data:** 2026-10-21
**Contexto:** Falha entre receber e registrar não pode perder documento.
**Decisão:** O bruto é persistido no object store **antes** de cursor, ledger ou catálogo avançarem.
**Consequências:** Falha no meio = reler o lote; idempotência obrigatória no registro.

---

## ADR-013 — Multi-tenant desde o início

**Data:** 2026-10-21
**Contexto:** Milhares de escritórios contábeis.
**Decisão:** Tenant = escritório. `tenant_id` em toda tabela de negócio, RLS no Postgres, prefixo de tenant no object store.
**Consequências:** Teste obrigatório de isolamento entre tenants.

---

## ADR-014 — Cofre de certificados; N certificados por tenant

**Data:** 2026-10-21
**Contexto:** Escritórios usam o próprio certificado e o das empresas (com contrato).
**Decisão:** `CofreDeCertificados` como capacidade de primeira classe; certificado tem titular (empresa ou escritório); envelope encryption; decifra só em memória.
**Consequências:** Rotação sem perder cursor; alertas de vencimento; uso de certificado de terceiro exige consentimento registrado.

---

## ADR-015 — Outbox transacional

**Data:** 2026-10-21
**Contexto:** Publicar eventos sem perder nem duplicar estado.
**Decisão:** Eventos gravados na mesma transação do estado; um relay publica. Consumidores idempotentes.
**Consequências:** Entrega pelo menos uma vez em todo o pipeline.

---

## ADR-016 — Finalidades de consentimento separadas

**Data:** 2026-10-21
**Contexto:** Coleta, ciência automática, entrega a integradores e crédito são usos distintos (LGPD).
**Decisão:** `consentimento.finalidade` ∈ {COLETA_DFE, CIENCIA_AUTOMATICA, ENTREGA_INTEGRADOR, CREDITO}, verificada na hora do acesso. Revisa o ADR-004 (ciência automática desligada por padrão).
**Consequências:** Nenhum uso de dado sem a finalidade correspondente vigente.

---

## ADR-017 — Porta push como segunda entrada

**Data:** 2026-10-21
**Contexto:** Saídas da empresa não vêm pela distribuição do AN.
**Decisão:** Agente local, e-mail em cópia e upload convergem em `POST /v1/ingestao/arquivos`, tratados como entrada **não confiável** até validação.
**Consequências:** Parser seguro (sem XXE), limites, validação de assinatura e do CNPJ do agente.

---

## ADR-018 — Catálogo canônico com proveniência

**Data:** 2026-10-21
**Contexto:** A mesma nota pode chegar por vários canais.
**Decisão:** 1 documento por `(tenant, chave)`, N arquivos, N proveniências com `confianca`. Ledger de NSU ≠ catálogo. Empresa dona definida **pelo conteúdo do XML**.
**Consequências:** Sem duplicatas; base para auditoria e para o futuro uso em crédito.

---

## ADR-019 — Saídas via `autXML` com certificado do escritório

**Data:** 2026-10-21
**Contexto:** Canal de saídas sem instalar nada no cliente.
**Decisão:** O ERP inclui o CNPJ do escritório no `autXML`; a coleta pelo certificado do escritório recebe essas notas e o processor as roteia pelo conteúdo.
**Consequências:** Só prospectivo; depende do ERP; **validar em homologação**.

---

## ADR-020 — Feed sequencial com sequenciador único

**Data:** 2026-10-21
**Contexto:** Integradores não podem perder notas; sequences do Postgres confirmam fora de ordem.
**Decisão:** `feed.seq` atribuído por um único escritor (com lease). `GET /v1/feed?desde=` é a fonte da verdade; webhooks só notificam.
**Consequências:** Teste de propriedade "nenhum leitor pula item".

---

## ADR-021 — Kafka e Redis adiáveis *(Proposto)*

**Data:** 2026-10-21
**Contexto:** Complexidade operacional antes de haver volume.
**Decisão:** Começar com outbox + filas no Postgres (`SKIP LOCKED`) e portas definidas; Kafka/Redis entram quando a medição pedir.
**Consequências:** Menos infraestrutura no início, sem mudar o domínio depois.

---

## ADR-022 — Licença *(Proposto)*

**Data:** 2026-10-21
**Contexto:** Projeto open source com possível produto pago em cima.
**Decisão:** Avaliar *open core* (motor Apache-2.0) × AGPL-3.0. Decidir antes do primeiro release público.
**Consequências:** —

---

## ADR-023 — Integrador como ator de primeira classe

**Data:** 2026-10-21
**Contexto:** Softwares contábeis de terceiros recebem as notas por webhook.
**Decisão:** OAuth2 client credentials para o integrador + autorização do tenant por empresa e escopo, verificada a cada requisição.
**Consequências:** Revogação imediata; trilha de auditoria por entrega.

---

## ADR-024 — Agente sem regra fiscal

**Data:** 2026-10-21
**Contexto:** Agente roda em ambientes heterogêneos e é difícil de atualizar.
**Decisão:** O agente só descobre, vigia, classifica pelo conteúdo e envia. Validação, deduplicação e regras ficam na plataforma.
**Consequências:** Menos suporte; regras mudam sem atualizar o agente.

---

## ADR-025 — Acesso a dados: JDBI + Flyway (D1)

**Data:** 2026-10-21
**Contexto:** O núcleo depende de SQL explícito: `ON CONFLICT DO NOTHING`, CAS por versão, `SKIP LOCKED`, tabelas append-only, RLS.
**Decisão:** **JDBI 3** (camada fina sobre JDBC: mapeamento, transações, parâmetros nomeados) + **Flyway** para migrations. Sem JPA/Hibernate e sem Spring Data JPA.
**Consequências:** SQL visível e testável (Testcontainers); nada de dirty checking/cache de 1º nível escondendo escritas.

---

## ADR-026 — Testes de propriedade com jqwik (D2)

**Data:** 2026-10-21
**Contexto:** Invariantes como "cursor nunca decresce", "ledger idempotente" e "feed sem buracos" são propriedades, não exemplos.
**Decisão:** **jqwik** (integra ao JUnit Platform) para testes de propriedade.
**Consequências:** Cada invariante da `CLAUDE.md` §2.2 que for uma propriedade ganha um `@Property`.

---

## ADR-027 — Spring Boot nos módulos de aplicação (D3)

**Data:** 2026-10-21
**Contexto:** A API de entrega precisa de OAuth2 para integradores, métricas e robustez de longo prazo; o autor já usa Spring Boot.
**Decisão:** **Spring Boot 3.x somente nos `app-*`** (composição). `domain`, `adapter-*`, `ingest-push`, `cert-vault` e `infra-*` continuam **sem Spring** (verificado por ArchUnit). Persistência via JDBI (ADR-025). Threads virtuais (`spring.threads.virtual.enabled=true`). OAuth dos integradores avaliado com Spring Authorization Server; métricas por Actuator + Micrometer.
**Consequências:** Ecossistema maduro; custo de memória/startup aceito nos apps. Revisa a recomendação inicial de "framework leve" (CLAUDE.md §23).

---

## ADR-028 — Logs com SLF4J + Logback JSON; observabilidade com Grafana no fim (D4)

**Data:** 2026-10-21
**Contexto:** Logs estruturados com campos de correlação e mascaramento (CLAUDE.md §20).
**Decisão:** **SLF4J** (API) + **Logback** com encoder **JSON** (logstash-logback-encoder). Na Fase 10: Prometheus (métricas do Actuator) + Loki (logs) + **Grafana**.
**Consequências:** O `domain` não loga (sem dependência); logs nas bordas, nunca com segredo ou XML.

---

## ADR-029 — Ritmo do modo autônomo: duas janelas desde o início (D5)

**Data:** 2026-10-21
**Contexto:** O autor quer o máximo de avanço por dia útil; revisão noturna de ~1h.
**Decisão:** Segunda a sexta, janelas às **08:00** e **13:00**, fechamento às 17:50. Freios mantidos em `autonomo/config.sh` (fila de 2 PRs, ~800 linhas/dia).
**Consequências:** Risco de esgotar o limite **semanal** da assinatura antes de sexta; se acontecer, reduzir `MAX_CHAMADAS_JANELA`.

---

## Pendências de decisão (a confirmar)

- [x] **Credencial/certificado** → bytes do `.pfx` + senha, só em memória, entregues pelo `CofreDeCertificados`; nunca caminho de disco no domínio. Cache de `SSLContext` por certificado no adapter (LRU/TTL). *(2026-10-21)*
- [x] **Libs SOAP fiscais** → sem lib fiscal grande: HTTP (`java.net.http`) + JAXB + Santuario; projetos de terceiros só como referência de comportamento, com checagem de licença. *(2026-10-21)*
- [x] **Formato do `BlobRef`** → chave determinística e legível + sha256 registrado no Postgres (layout em `CLAUDE.md` §8.1). *(2026-10-21)*
- [x] **Particionamento do Postgres** → começar sem particionar; projetar para hash por tenant/CNPJ; ativar só com medição. *(2026-10-21)*
