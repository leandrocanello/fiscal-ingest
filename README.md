# Fiscal-Ingest

> Plataforma **open source** de ingestão (download) de **documentos fiscais eletrônicos brasileiros** via webservices do governo — rápida, segura e auditável.

[![Status](https://img.shields.io/badge/status-WIP%20(Fase%200)-orange)](docs/ARCHITECTURE.md)

---

## 🎯 O que é

Sistema para baixar automaticamente documentos fiscais (NF-e, CT-e, MDF-e, NFS-e, etc.) dos webservices da SEFAZ/RFB, com:

- **Corretude** — respeito às regras da SEFAZ (rate-limit, não-retrocesso de NSU, manifestação).
- **Segurança** — certificado digital mTLS; prevenção de bloqueio de CNPJ (`cStat 656`).
- **Performance** — separação *control plane × data plane*; XML nunca passa pelo core.
- **Extensibilidade** — adicionar fontes (estados/esferas) **sem tocar no core**.
- **Auditabilidade** — NSU Ledger *append-only* permite *replay* e auditoria.

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
| 0 | Domínio puro (entidades + casos de uso) | 🔄 em andamento |
| 1 | Contrato do adapter + adapter FAKE | ⏳ |
| 2 | Adapter NF-e nacional (SOAP) | ⏳ |
| 3 | Infra (Postgres/MinIO/Redis/Kafka) | ⏳ |
| 4 | Entrega (API + docker-compose) | ⏳ |

**Escopo v1:** NF-e (modelo 55) — Ambiente Nacional (`distNSU` + `consNSU` + manifestação + ledger + gap-recovery).

---

## 🛠️ Stack

- **Java 21+** (virtual threads) · **Gradle** (Kotlin DSL, multi-módulo)
- **Postgres** (ledger/metadados) · **Kafka** (data plane) · **MinIO/S3** (blob XML gzip) · **Redis** (rate-limit/locks)
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
- [**Regras do projeto (.clinerules)**](.clinerules) — convenções e invariantes lidas em toda sessão de desenvolvimento.

---

## 🤝 Contribuindo

Projeto open source, desenvolvido com **TDD estrito** (teste primeiro → falha → implementação) e passos pequenos e revisáveis. Veja as regras em [.clinerules](.clinerules) antes de começar.

---

## ⚠️ Aviso legal

Este projeto **não** tem afiliação com a SEFAZ/RFB. Uso por sua conta e risco — respeite os limites de consumo dos webservices para não bloquear seu CNPJ.

## 📄 Licença

A definir (provável MIT ou Apache 2.0).
