## O que muda
Adiciona ao `domain` 4 regras ArchUnit, que quebram o build se o domínio deixar de ser puro: (1) só depende do JDK e de `br.com.doc.ingest.domain..`; (2) não faz I/O (`java.io`, `java.nio.file/channels`, `java.net`, `java.sql`, `javax`); (3) não usa relógio solto (`*.now()` sem `Clock`, `System.currentTimeMillis/nanoTime`, `Clock.system*`, `Thread.sleep`); (4) nenhum nome de classe, campo ou método contém `cstat`.
Cada regra tem meta-teste sobre uma fixture violadora em `src/test`. ArchUnit entra só como `testImplementation`. Nenhuma linha de produção mudou.
Suíte: domain 47 (38 + 9 novos), adapter-fake 5, adapter-nfe-nacional 15, tudo verde.

## Olhar com atenção
- `domain/build.gradle.kts:7` — `archunit-junit5:1.5.1` traz o BOM do JUnit e sobe **só o domain** para JUnit 5.14.4 (os outros módulos ficam em 5.10.2). Como não usamos `@ArchTest`, trocar por `com.tngtech.archunit:archunit` evita a diferença. Confirme também se a versão 1.5.1 é mesmo a que você quer.
- `domain/src/test/java/br/com/doc/ingest/domain/arquitetura/ArquiteturaDominioTest.java:19` — `DoNotIncludeTests` é o que impede as fixtures violadoras de quebrar a regra de produção. Se alguém mudar o import (ex.: `importPackagesOf`), as fixtures entram e os testes ficam vermelhos.
- `domain/src/test/java/br/com/doc/ingest/domain/arquitetura/RegrasDoDominio.java:38` — lista de chamadas de relógio proibidas. Faltam `LocalTime.now()`, `Year/YearMonth.now()` e `Clock.system(ZoneId)`.
- `domain/src/test/java/br/com/doc/ingest/domain/arquitetura/RegrasDoDominio.java:57` — a regra de cStat olha só nomes, não literais `String` nem anotações. Decida se isso basta para a inv. 4.
- `domain/src/test/java/br/com/doc/ingest/domain/arquitetura/RegrasDoDominio.java:34` — `javax..` inteiro está proibido. Hoje está ok, mas bloquearia, por exemplo, `javax.annotation` se um dia for desejado.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 — XML/I-O fora do core | `ArquiteturaDominioTest.dominioNaoFazIo` + `metaTeste_regraDeIoFalhaEmFixtureQueLeArquivo` |
| 4 — core desacoplado da origem | `dominioSoDependeDoJdkEDeSiMesmo`, `nenhumaClasseDoDominioFalaDeCStat` + meta-testes |
| (§15 — `Clock` injetável) | `dominioNaoUsaRelogioSolto` + 2 meta-testes (falha com `Instant.now()`, passa com `Instant.now(clock)`) |

## Decisões do agente nesta subetapa
- DA-1: ArchUnit só como `testImplementation` do `domain`, com as regras em `domain/src/test/.../arquitetura/`.

## O que NÃO foi feito
- Regras para adapters, `infra-*` e "Spring só nos `app-*`" (ADR-027): esses módulos ainda não existem.
- Não há módulo compartilhado de regras nem `FreezingArchRule` (fora do escopo).
- A regra de cStat não varre literais nem anotações. A de relógio não cobre `LocalTime`/`Year`/`YearMonth`/`Clock.system(ZoneId)`.
- O "ver falhar" de cada passo foi por compilação. A falha real de cada regra é demonstrada pelos meta-testes.
- No meio do desenvolvimento houve um passo revertido por suíte vermelha (PROGRESSO 23:01). O estado final está verde.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
