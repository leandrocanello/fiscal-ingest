## O que muda
Tabela `outbox` (V6) gravada na **mesma transação** do ledger + CAS do cursor (`ConfirmacaoLote.mensagens`), com trigger que só deixa `publicado_em` ir de nulo para valor e recusa DELETE.
`RelayOutbox`: claim `FOR UPDATE SKIP LOCKED`, uma transação por mensagem, entrega pelo menos uma vez, ordem por `(topico, chave)`, falha isolada por chave.
`RegistroDeConsumo` + `outbox_consumo` (V7, append-only) para consumidores idempotentes. Domínio ganha só `MensagemOutbox` (payload ≤ 64 KiB, sem lib JSON) e a porta `Publicador`.

## Olhar com atenção
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/RelayOutbox.java:75-86` — coração do relay: `SKIP LOCKED` + `not exists` de anterior pendente da mesma chave. É o que garante ordem por chave com N relays; a vazão fica em 1 mensagem por chave por vez (relevante p/ `autXML`, que concentra tudo numa chave).
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/RelayOutbox.java:47-71` — correção da revisão: chave que falha sai da rodada e a 1ª falha é relançada **ao fim** (DA-26). O número de publicadas da rodada se perde quando lança; o código usa arrays de 1 posição como parâmetro de saída (refatorável para um record).
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiEstadoColeta.java:74` — outbox inserido dentro do `inTransaction` do CAS (inv. 5); o rollback do `CasNaoAplicado` desfaz as mensagens.
- `infra-postgres/src/main/resources/db/migration/V6__outbox.sql:18-38` — trigger de imutabilidade. `new.payload = old.payload` compara `jsonb` (semântico, não byte a byte).
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/RegistroDeConsumo.java:28` — abre um 2º handle de propósito (comentário explica); dentro do relay são 2 conexões por thread → dimensionar o pool no `app-worker`. Parâmetro `x` não usado em `:29` (cosmético).
- Numeração V6/V7 com V4/V5 da E3-S03 ainda fora da main (DA-25): se esta PR entrar primeiro e algum banco for migrado, a V4/V5 depois exigirá `outOfOrder`. Decidir ordem de merge/renumerar antes da 1ª instalação real.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 5 — grave primeiro / sem perda | `JdbiEstadoColetaOutboxTest.conflitoDeVersaoNaoGravaMensagens`, `retrocessoRecusadoNaoGravaMensagens`, `CaosFalhaNoMeioTest.falhaAntesDoCasNaoGravaOutbox`, `CaosRelayTest.quedaDepoisDePublicarAntesDeMarcarRepublicaENaoPerde` |
| 3 — XML fora do core / da fila | `MensagemOutboxTest.rejeitaPayloadAcimaDoLimite`, `OutboxEsquemaTest.payloadQueNaoEhJsonEhRecusadoPeloBanco`, `ArquiteturaDominioTest` |
| 6 — append-only (defesa; outbox e `outbox_consumo`) | `OutboxEsquemaTest.soPublicadoEmPodeMudarEUmaVez`, `deleteEhRecusado`, `RegistroDeConsumoTest` (trigger da V2) |
| §15 caos "relay publica duas vezes" | `RegistroDeConsumoTest.relayPublicaDuasVezesConsumidorProcessaUmaVez`, `CaosRelayTest.doisRelaysConcorrentesNaoPublicamAMesmaMensagemDuasVezes` (`@RepeatedTest(20)`) |
| §6.5 falha isolada | `RelayOutboxTest.falhaPersistenteNumaChaveNaoParaAsOutras` |

## Decisões do agente nesta subetapa
- DA-25: payload `jsonb` montado fora do domínio; `ConfirmacaoLote` leva as mensagens; relay por mensagem com `SKIP LOCKED` + ordem por chave; `outbox_consumo`; migrations V6/V7; grant condicional a `fiscal_app`.
- DA-26: falha persistente exclui a `(topico, chave)` do resto da rodada e relança a 1ª falha ao fim.

## O que NÃO foi feito
- Payload real de `docs.coletados` e chamada a partir do fetcher → E3-S08 (testes usam payloads sintéticos).
- Dispatcher por tópico / processor de parse → E3-S07; loop, agendamento e lease de relay único → `app-worker`.
- DLQ, contagem de tentativas e backoff: mensagem venenosa retenta para sempre e segura só a sua chave → Fase 10 / quando houver consumidor real.
- Expurgo de mensagens publicadas e de `outbox_consumo` (sem DELETE para `fiscal_app`) → Fase 10.
- RLS: `outbox`/`outbox_consumo` não têm `tenant_id`; se a E3-S03 entrar antes, a lista de tabelas sem tenant do teste dela precisa incluí-las.
- Consumidores não devem hashear `payloadJson` relido: o `jsonb` normaliza espaços/ordem de chaves.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
