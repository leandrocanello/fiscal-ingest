# E3-S02 — CAS do cursor + testes de caos (dois fetchers, um avança)

## O que muda
- Domínio: pacote `domain.coleta` com `ChaveColeta`, `PosicaoColeta`, `ConfirmacaoLote`, `ResultadoCas` (sealed) e a porta `EstadoColetaRepositorio` (DA-23).
- `JdbiEstadoColeta.confirmarLote`: ledger (idempotente) + CAS do cursor (`versao = :v and ult_nsu <= :novo`) numa **única transação**; conflito ou retrocesso → rollback total e `ConflitoDeVersao`/`RetrocessoRecusado`.
- Migration `V3`: `ult_nsu`/`cursor_token` obrigatórios + trigger que recusa `ult_nsu` menor (inv. 1 no banco).
- SQL de inserção do ledger extraído para `LedgerSql` (compartilhado com `JdbiNsuLedger`, sem mudança de comportamento).
- Caos (dois fetchers / N fetchers / queda no meio) e propriedades jqwik do CAS.

## Olhar com atenção
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiEstadoColeta.java:70` — entradas inseridas na ordem recebida, antes do CAS. Com NSUs repetidos em ordens diferentes entre dois fetchers, pode haver deadlock (sai como exceção, não como `ConflitoDeVersao`). Hoje é inofensivo (AN entrega em ordem); sugiro ordenar por NSU na E3-S08.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiEstadoColeta.java:87-89` — CAS falhou → exceção interna sem stack trace (`:121`) para forçar o rollback do `inTransaction`, e diagnóstico com novo `select`. Confira se o padrão "exceção como controle de fluxo" é aceitável para você.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiEstadoColeta.java:77` — `ult_nsu <= :novo`: NSU **igual** é aplicado e sobe a versão (espelha `NAO_AVANCOU`/Vazio). Decisão da DA-23.
- `infra-postgres/src/main/resources/db/migration/V3__estado_coleta_cas.sql:15` — trigger recusa todo retrocesso, inclusive correção manual de cursor (ex.: `589`). Defesa em profundidade desejada, mas o runbook precisa prever a saída.
- `domain/src/main/java/br/com/doc/ingest/domain/coleta/ConfirmacaoLote.java:24` — só recusa entrada com NSU **maior** que o novo `ultNsu`; NSU ≤ `ultNsu` lido é aceito de propósito (reprocessamento de lote relido).
- `infra-postgres/src/test/java/br/com/doc/ingest/infra/postgres/CaosDoisFetchersTest.java` — o teste mais importante do PR (inv. 2); roda 50× com barreira + virtual threads.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — não-retrocesso de NSU | `JdbiEstadoColetaTest.confirmarComUltNsuMenorEhRecusadoSemEfeito`, `NaoRetrocessoNoBancoTest.updateQueDiminuiUltNsuEhRecusadoPeloBanco`, `EstadoColetaCasPropriedadesTest.ultNsuNuncaDecresce` |
| 2 — um CNPJ por vez (proteção final por CAS, §6.5 c) | `CaosDoisFetchersTest.doisFetchersNaMesmaVersaoSoUmAvanca`, `nFetchersConcorrentesSoUmAvancaPorVersao` |
| 5 — sem perda (ledger + cursor atômicos) | `CaosFalhaNoMeioTest.falhaAntesDoCasNaoAvancaNemRegistraEReprocessarFunciona` |
| 6 — append-only | `JdbiEstadoColetaTest.reconfirmarNsuJaRegistradoMantemPrimeiroStatus`, `AppendOnlyTest` (existente) |
| 3/4 — sem XML/cStat no core | `ArquiteturaDominioTest` (existente, cobre `domain.coleta`) |

## Decisões do agente nesta subetapa
- DA-23: porta `EstadoColetaRepositorio.confirmarLote → ResultadoCas` (ledger + cursor numa transação) em vez de unidade de trabalho genérica; trigger V3 contra retrocesso; `ult_nsu` igual é aceito e sobe a versão.

## O que NÃO foi feito
- Lease/claim `SKIP LOCKED`, `due_at`, `bloqueado_ate`, fairness → E4-S01. Aqui só o CAS (proteção final).
- Máquina de estados de `estado_coleta` → E4-S03.
- `ledger_tentativa` e `blob_ref`/`sha256`/`chave_acesso`/`tentativa_id` no ledger → E3-S08. A parte "blob antes do ledger" da inv. 5 **não** é coberta aqui.
- `AvancarPonteiroUseCase` não foi ligado à nova porta (E3-S08/E4-S01).
- Outbox na mesma transação → E3-S04; RLS e revogar `TRUNCATE` → E3-S03.
- Não dá para provar pelo histórico que cada teste foi visto falhar antes; os 4 commits `test(...)` finais passaram de primeira (esperado).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
