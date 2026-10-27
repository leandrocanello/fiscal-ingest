# Fiscal-Ingest — Arquitetura e Especificação

> Plataforma open source de **ingestão (download) de documentos fiscais eletrônicos brasileiros**.
> Especificação técnica do motor de coleta e dos contratos do core.
> **A visão completa do produto (v2 — hub de DF-e) está em [`CLAUDE.md`](../CLAUDE.md)**, que prevalece
> em caso de conflito. Este arquivo foi alinhado à v2 na Fase 1.5 (2026-10-21).

---

## 1. Visão Geral

Sistema de baixa automática de documentos fiscais (NF-e, CT-e, MDF-e, NFS-e, etc.) via webservices do governo, com foco em:

- **Corretude**: respeito às regras da SEFAZ (rate-limit, não-retrocesso de NSU, manifestação).
- **Segurança**: certificado digital mTLS, tratamento de credenciais, prevenção de bloqueio de CNPJ.
- **Performance**: separação control plane × data plane; XML nunca passa pelo core.
- **Extensibilidade**: adicionar novas fontes (estados, esferas) **sem tocar no core**.
- **Auditabilidade**: NSU Ledger append-only permite replay e auditoria.

**Não é um MVP apressado.** É uma base sólida, testável e extensível, construída com TDD estrito.

---

## 2. Princípios de Engenharia

1. **TDD OBRIGATÓRIO** — teste primeiro, vê falhar, implementa até passar.
2. **Passos pequenos e revisáveis** — entrego incrementos, PARO e resumo ao fim de cada um.
3. **Decisões explícitas** — nenhuma decisão arquitetural ambígua é assumida; PERGUNTO.
4. **Não reinvento a SEFAZ** — uso libs open source nos adapters; meu valor é a orquestração.
5. **Propor antes de codar** — design é proposto e aguardado OK antes de implementar.

---

## 3. Domínio — Fatos que Moldam o Design

### 3.1 NSU (Número Sequencial Único)
- Cada CNPJ tem uma sequência de NSUs por fonte no Ambiente Nacional.
- **Não se pode retroceder**: pedir NSU já consumido → `cStat 656` (Consumo Indevido) → **bloqueia o CNPJ**.
- **Bloqueio por CNPJ consultado:** quando a consulta não traz documento novo (`cStat 137`, tipicamente
  `ultNSU == maxNSU`), novas consultas daquele CNPJ ficam bloqueadas por ~1h. **Não** é um limite fixo de
  "1 lote por hora": enquanto houver documentos (`ultNSU < maxNSU`), o loop continua **[VALIDAR]**.
  A duração é configurável por fonte (`PoliticaRateLimit`), nunca fixa no código.
- Consultas pontuais (consNSU/consChNFe): ~20/hora **[VALIDAR]**.
- Documentos ficam ~90 dias disponíveis; depois retornam "fora do prazo".

### 3.2 Fluxo NF-e (manifestação é o gatilho)
1. Chega o **resumo** (resNFe) — sem XML completo.
2. O destinatário faz a **manifestação** (ciência de operação realizada).
3. Só então o **XML completo** (procNFe) é liberado, chegando num **NSU posterior**.
- A manifestação **é o gatilho**; o controle de NSU só recupera o que já existe.

### 3.3 Concorrência
- **1 CNPJ processado por vez** (nunca em paralelo) para evitar 656.

### 3.4 Transportes por esfera
- **SOAP** — NF-e (55), CT-e (57/67), NFCOM (62), NF3e (66): federais, quase idênticos.
- **REST** — NFS-e nacional (ADN).
- **Estadual** — SC (inclui NFC-e 65), RS.

### 3.5 Autenticação
- Certificado digital mTLS (`.pfx`). Senha/charset são sensíveis.

---

## 4. Arquitetura

### 4.1 Clean Architecture + Ports & Adapters (Hexagonal)

```
                    ┌─────────────────────────────────┐
   Adapters ───────▶│        Domínio (PURO)           │◀────── Casos de Uso
   (bordas)         │  entidades + regras + portas    │       (orquestração)
                    └─────────────────────────────────┘
                         Sem I/O · Sem framework
```

- **Domínio puro** no centro: entidades, value objects, portas (interfaces), casos de uso.
- **Adapters** nas bordas: traduzem o dialeto de cada origem para o vocabulário único de `Desfecho`.

### 4.2 Camadas (de baixo → cima)

| # | Camada | Responsabilidade |
|---|--------|------------------|
| 1 | Credencial/Certificado | Carrega/gerencia `.pfx`, sessão mTLS |
| 2 | Transporte | SOAP/REST genérico |
| 3 | Protocolo por esfera | Federal (SOAP), ADN (REST), Estadual |
| 4 | Adapters de fonte | Implementam a porta `SourceAdapter`; mapeiam desfechos |
| 5 | Core de domínio | Contrato, NSU Ledger, gap-recovery, orquestração, rate-limit |
| 6 | Entrega | API + eventos |

### 4.3 Separação Control Plane × Data Plane

**Control Plane** (baixo volume, coordenação):
- Scheduler decide "de quem é a vez" via **token bucket + due-time por CNPJ**.
- Emite o comando de busca. Trafega **só metadado/NSU**.

**Data Plane** (alto volume, documentos):
- Documentos publicados num **stream** (Kafka, particionado por CNPJ).
- **Workers stateless** fazem parse em streaming e persistem.
- O **XML nunca passa pelo core** — vai direto ao object store (MinIO/S3).
- Só **metadado + NSU** tocam o domínio/ledger.

> É essa separação que impede o core de virar gargalo.

**Complementos da v2** (detalhes em `CLAUDE.md` §6):
- **Postgres é a fonte da verdade** (ADR-010). Redis e Kafka são aceleradores e podem ser adiados (ADR-021):
  a primeira versão usa outbox + filas no Postgres.
- **Dois estágios** (ADR-011): *fetch/receive* (chama a fonte ou recebe o arquivo, grava o bruto, registra)
  e *process* (parse, catálogo, manifestação, feed).
- **Grave primeiro, avance depois** (ADR-012) e **outbox transacional** (ADR-015).

### 4.3.1 Porta push, catálogo e feed (v2)

- **Porta push** (ADR-017): o agente local (Go), o e-mail em cópia e o upload manual entram por
  `POST /v1/ingestao/arquivos` e convergem no mesmo pipeline.
- **Catálogo canônico** (ADR-018): 1 documento por `(tenant, chave)`, N arquivos, N proveniências com
  nível de confiança. O ledger de NSU controla a coleta; o catálogo é o que o produto expõe.
  A empresa dona é definida **pelos CNPJs dentro do XML** (necessário para o `autXML`, ADR-019).
- **Feed sequencial** (ADR-020): `GET /v1/feed?desde=<seq>`, escrito por um sequenciador único;
  webhooks só notificam.

### 4.4 NSU Ledger (espinha dorsal)

- Append-only. Registra **TODO NSU** por `(fonte, cnpj)` + desfecho.
- Status por NSU: `BAIXADO | RESUMO | EVENTO | DESCARTADO | FORA_PRAZO | GAP`.
- Habilita: "pedir só o que falta", replay, auditoria.
- Fontes `SEQUENTIAL` ativam gap-recovery no core de graça.

---

## 5. Contrato do Adapter (a porta única)

### 5.1 Descriptor

```java
public record AdapterDescriptor(
    FonteId fonteId,
    Set<Modelo> modelos,
    TipoCursor tipoCursor,          // SEQUENCIAL ativa ledger + gap-recovery
    Capacidades capacidades,
    PoliticaRateLimit rateLimit,
    TipoAutenticacao auth
) {}
```

### 5.2 Capacidades

```java
public record Capacidades(
    boolean fazLote,            // distNSU
    boolean fazPontual,         // consNSU
    boolean fazManifestacao,    // ciência etc.
    boolean permiteRetrocesso   // SEFAZ AN = false (crítico p/ 656)
) {}
```

### 5.3 Desfecho (vocabulário único — sealed)

O adapter **traduz** o dialeto da origem (`cStat` 656/137/632, HTTP 429...). O core **nunca** vê `cStat`/HTTP.

```java
public sealed interface Desfecho
    permits Ok, Vazio, LimiteAtingido, IndisponivelTemporario,
            IndisponivelDefinitivo, ErroAutenticacao, ServicoFora {

    record Ok()                                  implements Desfecho {}
    record Vazio()                               implements Desfecho {} // 137: nada novo
    record LimiteAtingido(Duration retryApos)    implements Desfecho {} // 656: NÃO avança ponteiro
    record IndisponivelTemporario(Duration r)    implements Desfecho {} // 108, timeout/5xx/429, cStat desconhecido
    record IndisponivelDefinitivo(String motivo) implements Desfecho {} // 589, 632 (consChNFe), schema incompatível
    record ErroAutenticacao(String motivo)       implements Desfecho {} // certificado
    record ServicoFora(String motivo)            implements Desfecho {} // 109: paralisado
}
```

> **Mapeamento de `cStat`** (decisão E2-S02, 2026-10-26; fonte MOC 7.0, Tab. 5-20 e 5-29): `137 → Vazio`,
> `138 → Ok`, `108 → IndisponivelTemporario`, `109 → ServicoFora`, `656 → LimiteAtingido(1h)`,
> `632` (fora de prazo, só `consChNFe`) e `589` (cursor inconsistente) → `IndisponivelDefinitivo`; desconhecido →
> `IndisponivelTemporario(5 min)` + alerta. Só `137`/`138` avançam o cursor (teste de propriedade no `CStatMapper`).

### 5.4 Cursor (opaco para o core)

```java
public record Cursor(String token) {                 // core só guarda/encaminha; NUNCA parseia
    public static Cursor inicial() { return new Cursor(""); }
}
```

Para fontes `SEQUENTIAL`, cada `Documento` carrega seu `Nsu` (numérico); o ledger reconstrói a sequência.

### 5.5 A porta `SourceAdapter`

```java
public interface SourceAdapter {
    AdapterDescriptor descritor();
    void autenticar(Credencial credencial);

    // lote: devolve SÓ metadado + BlobRef; o XML já foi ao object store
    BuscarLoteResultado buscarLote(ContextoConsulta ctx, Cursor cursor);

    // pontual: gap-fill de um NSU específico
    BuscarUmResultado buscarUm(ContextoConsulta ctx, ReferenciaDocumento ref);

    // opcional: só adapters com capacidades.fazManifestacao
    Optional<ManifestarResultado> manifestar(ContextoConsulta ctx,
                                             ReferenciaDocumento ref,
                                             TipoManifestacao tipo);
}
```

### 5.6 Resultados

```java
public record BuscarLoteResultado(
    List<Documento> documentos,   // só metadado + Nsu + BlobRef; NUNCA bytes/XML
    Cursor proximoCursor,
    boolean temMais,
    Desfecho desfecho
) {}

public record BuscarUmResultado(Optional<Documento> documento, Desfecho desfecho) {}
public record ManifestarResultado(Desfecho desfecho) {}
```

### 5.7 Documento canônico (metadado somente)

```java
public record Documento(
    DocumentoId id,
    FonteId fonte,
    Cnpj cnpjDestinatario,
    Modelo modelo,
    Nsu nsu,
    Instant recebidoEm,
    TipoConteudo tipoConteudo,        // RESUMO | COMPLETO | EVENTO
    Optional<ChaveAcesso> chave,
    Optional<BlobRef> blobRef         // handle p/ o XML no object store
) {}
```

> Materializa a regra "XML nunca passa pelo core": só uma referência opaca ao blob.

---

## 6. Decisões do Usuário (registro)

| # | Decisão | Detalhe |
|---|---------|---------|
| 1 | Build tool | **Gradle** (Kotlin DSL), multi-módulo. Java 21+. |
| 2 | Object store writer | O **adapter (infra)** escreve o XML no blob store e devolve só `BlobRef`. Core nunca vê bytes. |
| 3 | Manifestação | Por **CNPJ**. Ciência automática **desligada por padrão**; só liga com consentimento explícito da empresa (ADR-004 revisado). Demais manifestações sempre manuais e auditadas. |
| 4 | Ledger idempotência | Re-inserir mesmo NSU = **ignora silenciosamente** (não lança erro). Ledger vazio no `GapCalculator` = retorna `[]`. |
| 5 | Pacote raiz | **`br.com.doc`**. |
| 6 | Escopo v1 | NF-e (55) Ambiente Nacional: distNSU + consNSU + manifestação + ledger + gap-recovery. |
| 7 | Deploy | Self-host via docker-compose, open source, **sem deploy em servidor por ora**. |

---

## 7. Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21+ (virtual threads) |
| Build | Gradle (Kotlin DSL) |
| Ledger/Metadados/Catálogo/Feed | Postgres (fonte da verdade; particionar só com medição) |
| Data plane / Stream | Outbox + filas no Postgres; Kafka quando a medição pedir (ADR-021) |
| Object storage | MinIO / S3 (XML gzip, imutável, sha256) |
| Estado quente / rate-limit / locks | Postgres (lease + CAS); Redis quando a medição pedir (ADR-021) |
| Agente local | Go (repositório separado `fiscal-agent`, ADR-009) |
| Scheduler | Dedicado (due-time + token bucket por CNPJ) |
| Unit tests | JUnit 5 + Mockito |
| Integration tests | Testcontainers (Postgres/Kafka/MinIO) |
| SEFAZ mock | WireMock (NUNCA bater na SEFAZ real) |
| Empacotamento | Docker / docker-compose |

---

## 8. Estrutura de Módulos

```
fiscal-ingest/                         (este repositório)
├── CLAUDE.md                          visão v2, regras e plano (prevalece)
├── docs/
│   ├── ARCHITECTURE.md                este arquivo
│   ├── DECISIONS.md                   ADRs
│   └── AUDITORIA-1.5.md               estado do código recuperado
├── domain/                            ★ PURO — sem I/O, sem framework
├── adapter-fake/                      SourceAdapter em memória
├── adapter-nfe-nacional/              pull: distNSU/consNSU/consChNFe/manifestação
├── ingest-push/                       (previsto) parse seguro, classificação, assinatura, roteamento
├── cert-vault/                        (previsto) CofreDeCertificados
├── infra-postgres/                    (previsto) ledger, estado_coleta, catálogo, feed, outbox, RLS
├── infra-blob-minio/                  (previsto)
├── infra-stream-kafka/                (fase 10, se medido)
├── infra-ratelimit-redis/             (fase 10, se medido)
├── app-scheduler/ app-worker/ app-api/  (previstos) composição
├── api-contract/                      (previsto) openapi.yaml
└── docker-compose.yml                 (previsto)

fiscal-agent/                          repositório separado, Go (CLAUDE.md §10)
```

### Pacote raiz Java
`br.com.doc.ingest`

---

## 9. Fases de Entrega

O plano de fases vigente está em `CLAUDE.md` §16 (0 ✅ · 1 ✅ · 1.5 saneamento · 2 adapter NF-e AN
(2A ✅) · 3 persistência e catálogo · 4 scheduler e cofre · 5 porta push · 6 API de entrega e webhooks ·
7 agente Go · 8 auditoria de saídas · 9+ ...). Cada fase em TDD, com parada para revisão.

---

## 10. Invariantes Críticas (nunca violar)

1. **Não-retrocesso de NSU**: nunca enviar cursor/NSU anterior ao consumido → 656 bloqueia CNPJ.
2. **1 CNPJ por vez** por fonte.
3. **XML fora do core**: só metadado + NSU tocam o domínio/scheduler.
4. **Core desacoplado de origens**: nenhum `cStat`/HTTP vaza do adapter.
5. **TDD**: nada é implementado sem teste anterior falhando.

> Lista completa (14 invariantes, incluindo catálogo, push e entrega): `CLAUDE.md` §2.2.

---

## 11. O que NÃO fazer

- Colocar XML no caminho do core/scheduler.
- Acoplar o core a uma origem/transporte específico.
- Implementar todos os documentos/estados de uma vez.
- Gerar código sem os testes correspondentes ANTES.
- Tomar decisão arquitetural grande sem perguntar.
