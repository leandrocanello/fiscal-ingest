# Fiscal-Ingest

> Plataforma **open source** de ingestão (download) de **documentos fiscais eletrônicos brasileiros** via webservices do governo — rápida, segura e auditável.

[![Status](https://img.shields.io/badge/status-WIP%20(Fase%201.5)-orange)](docs/ARCHITECTURE.md)

---

## 🎯 O que é

Um **hub de DF-e**: reúne **todos os documentos fiscais eletrônicos de uma empresa** — os que ela
**recebe** (buscados no Ambiente Nacional por NSU) e os que ela **emite** (enviados por um agente local
que vigia a pasta do ERP, ou obtidos via `autXML`) —, guarda tudo com histórico e auditoria, e entrega
por **API e webhook** ao software contábil.

- **Corretude** — respeito às regras da SEFAZ (NSU, bloqueio por CNPJ, manifestação).
- **Nunca perder documento** — grava o bruto antes de avançar; ledger append-only.
- **Captura genérica** — funciona com qualquer ERP que grave o XML autorizado.
- **Entrega confiável** — feed sequencial sem buracos + webhooks assinados com retentativa.
- **Extensível** — novas fontes entram por portas, sem tocar no core.

Visão completa, regras e plano: **[CLAUDE.md](CLAUDE.md)**.

---

## 🧱 Arquitetura

Clean Architecture + Ports & Adapters (Hexagonal), com o domínio **puro** no centro (sem I/O, sem framework) e adapters nas bordas traduzindo o dialeto de cada origem para um vocabulário único de `Desfecho`.

```
   Adapters ───────▶  Domínio (PURO)  ◀────── Casos de Uso
   (SOAP/REST/        entidades + regras       (orquestração,
    estadual)         + portas                  gap-recovery,
                                                rate-limit)
```

Detalhes completos em **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

### Separação Control Plane × Data Plane

- **Control Plane** (baixo volume): *scheduler* decide "de quem é a vez" (token bucket + *due-time* por CNPJ) e trafega só metadado/NSU.
- **Data Plane** (alto volume): documentos vão a um *stream* (Kafka); *workers* stateless fazem parse/persistência. O **XML nunca passa pelo core**.

---

## 🚀 Status

| Fase | Descrição | Status |
|------|-----------|--------|
| 0 | Domínio puro | ✅ |
| 1 | Contrato do adapter + adapter fake | ✅ |
| 1.5 | Saneamento (build, auditoria, docs) | 🔄 |
| 2 | Adapter NF-e Ambiente Nacional | 🔄 (2A ✅) |
| 3 | Persistência e catálogo | ⏳ |
| 4 | Scheduler, cofre de certificados, consentimento | ⏳ |
| 5 | Porta push (agente/upload) | ⏳ |
| 6 | API de entrega + webhooks | ⏳ |
| 7 | Agente local em Go (`fiscal-agent`) | ⏳ |

Plano completo: `CLAUDE.md` §16.

---

## 🛠️ Stack

- **Java 21+** (virtual threads) · **Gradle** (Kotlin DSL, multi-módulo)
- **Postgres** (fonte da verdade: ledger, catálogo, feed, outbox) · **MinIO/S3** (XML gzip) · Kafka/Redis quando a medição pedir
- **Go** no agente local (repositório `fiscal-agent`)
- **JUnit 5 + Mockito** (unit) · **Testcontainers** (integração) · **WireMock** (SEFAZ mock)
- **Docker / docker-compose** (self-host)

---

## ▶️ Como rodar (em breve)

```bash
# Requisitos: Java 21+, Docker
./gradlew build
docker-compose up -d
```

---

## 📚 Documentação

- [**Arquitetura & Especificação**](docs/ARCHITECTURE.md) — fonte da verdade do projeto.
- [**Registro de Decisões (ADRs)**](docs/DECISIONS.md) — contexto, decisão e consequência de cada escolha.
- [**CLAUDE.md**](CLAUDE.md) — visão do produto, regras inegociáveis, arquitetura e plano de fases.
- [**Auditoria da Fase 1.5**](docs/AUDITORIA-1.5.md) — estado do código recuperado.

---

## 🤝 Contribuindo

Projeto open source, desenvolvido com **TDD estrito** (teste primeiro → falha → implementação) e passos pequenos e revisáveis. Veja as regras no [CLAUDE.md](CLAUDE.md) antes de começar.

---

## ⚠️ Aviso legal

Este projeto **não** tem afiliação com a SEFAZ/RFB. Uso por sua conta e risco — respeite os limites de consumo dos webservices para não bloquear seu CNPJ.

## 📄 Licença

Em decisão (ADR-022: *open core* Apache-2.0 × AGPL-3.0).
