# Fiscal-Ingest — Arquitetura e Especificação

> Plataforma open source de **ingestão (download) de documentos fiscais eletrônicos brasileiros**.
> Este documento é a **fonte da verdade** do projeto. O Cline o lê para manter contexto.

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
- Limites: ~20 consultas pontuais (consNSU)/hora; ~1 lote (distNSU)/hora.
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
    record LimiteAtingido(Duration retryApos)    implements Desfecho {} // 656/632: NÃO avança ponteiro
    record IndisponivelTemporario(Duration r)    implements Desfecho {} // timeout/5xx/429
    record IndisponivelDefinitivo(String motivo) implements Desfecho {} // schema incompatível
    record ErroAutenticacao(String motivo)       implements Desfecho {} // certificado
    record ServicoFora(String motivo)            implements Desfecho {} // 109: paralisado
}
```

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
| 3 | Manifestação | Por **CNPJ**: define ciência **automática (default)** ou manual. Preferência no banco; backend consulta antes de agir. |
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
| Ledger/Metadados | Postgres (particionado) |
| Data plane / Stream | Kafka (particionado por CNPJ, com replay) |
| Object storage | MinIO / S3 (XML gzip) |
| Estado quente / rate-limit / locks | Redis |
| Scheduler | Dedicado (due-time + token bucket por CNPJ) |
| Unit tests | JUnit 5 + Mockito |
| Integration tests | Testcontainers (Postgres/Kafka/MinIO) |
| SEFAZ mock | WireMock (NUNCA bater na SEFAZ real) |
| Empacotamento | Docker / docker-compose |

---

## 8. Estrutura de Módulos

```
fiscal-ingest/                         (raiz, settings.gradle.kts)
├── .clinerules                        REGRAS lidas pelo Cline em toda conversa
├── docs/
│   ├── ARCHITECTURE.md                este arquivo
│   └── DECISIONS.md                   registro de decisões (ADR leve)
├── domain/                            ★ PURO — centro, sem I/O, sem framework
│   └── src/{main,test}/java/br/com/doc/ingest/domain/...
├── adapter-fake/                      Fase 1: SourceAdapter em memória
├── adapter-nfe-nacional/              Fase 2: SOAP distNSU/consNSU/manifestação
├── infra-ledger-postgres/             Fase 3
├── infra-blob-minio/                  Fase 3
├── infra-ratelimit-redis/             Fase 3
├── infra-stream-kafka/                Fase 3
├── scheduler/                         control plane
├── delivery-api/                      Fase 4 (REST + eventos)
├── docker-compose.yml
├── settings.gradle.kts
├── build.gradle.kts
└── README.md
```

### Pacote raiz Java
`br.com.doc.ingest`

---

## 9. Fases de Entrega (cada uma em TDD)

### Fase 0 — Domínio puro (COMEÇAR AQUI)
Entidades (`Documento`, `Nsu`/`Cursor`, `Ledger`) e casos de uso (`GapCalculator`, `AvancarPonteiro`, `RecuperarGap`, `RateLimitPolicy`/`TokenBucket`).
**Sem I/O, sem framework.** Testes unitários primeiro.

### Fase 1 — Contrato do adapter + adapter FAKE em memória
Testa o core sem SEFAZ.

### Fase 2 — Adapter real NF-e nacional (SOAP)
`distNSU`/`consNSU` + manifestação, mapeando `cStat` 137/138/656/632/108/109 → `Desfecho`.
WireMock cobre os cenarios.

### Fase 3 — Infra
Ledger (Postgres), blob (MinIO), scheduler + rate-limit (Redis), stream (Kafka). Testcontainers.

### Fase 4 — Entrega
API + eventos; docker-compose ponta a ponta.

---

## 10. Invariantes Críticas (nunca violar)

1. **Não-retrocesso de NSU**: nunca enviar cursor/NSU anterior ao consumido → 656 bloqueia CNPJ.
2. **1 CNPJ por vez** por fonte.
3. **XML fora do core**: só metadado + NSU tocam o domínio/scheduler.
4. **Core desacoplado de origens**: nenhum `cStat`/HTTP vaza do adapter.
5. **TDD**: nada é implementado sem teste anterior falhando.

---

## 11. O que NÃO fazer

- Colocar XML no caminho do core/scheduler.
- Acoplar o core a uma origem/transporte específico.
- Implementar todos os documentos/estados de uma vez.
- Gerar código sem os testes correspondentes ANTES.
- Tomar decisão arquitetural grande sem perguntar.
