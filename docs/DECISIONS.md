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

## ADR-004 — Manifestação por CNPJ (ciência automática default)

**Data:** 2026-07-09
**Contexto:** Resumo (resNFe) chega sem XML completo; a manifestação (ciência) é o gatilho para liberar o procNFe num NSU posterior. Necessidade de flexibilidade: alguns CNPjs querem automático, outros manual por nota.
**Decisão:** Preferência de manifestação é **por CNPJ**, persistida no banco. Default = **ciência automática**. Backend consulta antes de agir.
**Consequências:** Novo campo de configuração no cadastro de CNPJ; caso de uso de manifestação lê essa preferência. JSON com notas pendentes pode ser exposto para manifestação manual.

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

## Pendências de decisão (a confirmar)

- [ ] **Credencial/certificado**: formato exato do value object `Credencial` (caminho `.pfx` + senha? bytes?). Cache de sessão SOAP no adapter (transient)?
- [ ] **Libs SOAP fiscais**: qual base open source usar no adapter NF-e nacional? (ex.: [DFe4j](https://github.com/Sharepoint-Brasil/DFe4j), [Java-NFe](https://github.com/Samuel-Oliveira/Java_NFe), WSDL bruto via JAX-WS?)
- [ ] **Formato do `BlobRef`**: UUID? caminho S3? hash?
- [ ] **Estratégia de particionamento do Postgres** (ledger): por `cnpj`? por mês?
