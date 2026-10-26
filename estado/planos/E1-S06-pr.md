# E1-S06 — invariante 1: guarda de não-retrocesso de NSU no AvancarPonteiroUseCase (auditoria A4)

## O que muda
- `BuscarLoteResultado` ganha `Nsu ultNsu` (não nulo), a posição explícita. O `Cursor` continua opaco (opção A do PARA-VOCE).
- `AvancarPonteiroUseCase.executar` recebe o `Nsu ultNsuAtual` e só avança se novo > atual. Igual = `NAO_AVANCOU`; recuo = `RETROCESSO_REJEITADO`, sem registrar nada no ledger.
- `ResultadoAvanco` passa a ter `ultNsu` + `MotivoAvanco` (enum novo). `avancou()` continua existindo, agora derivado do motivo.
- Testes de propriedade jqwik para a invariante 1, e o `FakeSourceAdapter` ajustado ao novo contrato.

## Olhar com atenção
- `domain/src/main/java/br/com/doc/ingest/domain/usecase/AvancarPonteiroUseCase.java:42` — a guarda roda **antes** de `registrarDocumentos`. Um lote com recuo não grava nada no ledger. Confira se é isso que você quer, já que o bruto já foi gravado pelo adapter e vai sobrar no blob sem entrada no ledger.
- `domain/src/main/java/br/com/doc/ingest/domain/usecase/AvancarPonteiroUseCase.java:45` — Ok com o **mesmo** NSU registra os documentos (idempotente, ADR-005), mas devolve `NAO_AVANCOU`.
- `domain/src/main/java/br/com/doc/ingest/domain/usecase/ResultadoAvanco.java:18` — `avancou()` agora é `false` também no Vazio "normal" (137 com o mesmo `ultNsu`). Na Fase 4, o loop tem que decidir pelo `Desfecho`/`MotivoAvanco`, e não por `avancou()`, senão confunde Vazio com falha.
- `adapter-fake/src/main/java/br/com/doc/ingest/adapter/fake/FakeSourceAdapter.java:64` — devolve `Nsu(0)` quando o lote vem vazio, porque o fake não conhece o NSU atual. Se for encadeado com o caso de uso partindo de NSU > 0, dá `RETROCESSO_REJEITADO`. A limitação está documentada.
- `domain/src/test/java/br/com/doc/ingest/domain/usecase/AvancarPonteiroUseCasePropriedadesTest.java:150` — os lotes de `cursorNuncaRecua` não têm documentos. O registro com documentos fica coberto pelas outras duas propriedades.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — não-retrocesso de NSU | `AvancarPonteiroUseCaseTest.Retrocesso` (`deveRejeitarRetrocessoEmOk`, `deveRejeitarRetrocessoEmVazio`, `vazioComMesmoNsuNaoAvancaSemErro`, `okComMesmoNsuRegistraENaoAvanca`); `AvancarPonteiroUseCasePropriedadesTest` (`cursorNuncaRecua`, `retrocessoNaoRegistraNada`, `desfechoDeParadaNaoRegistraNada`) |
| 3/4 — XML fora do core; core sem cStat | regras ArchUnit da E1-S04 (continuam verdes) |

## Decisões do agente nesta subetapa
- nenhuma. As decisões (opção A, igualdade = `NAO_AVANCOU`, enum `MotivoAvanco`) foram do autor, no PARA-VOCE "[DECISAO] E1-S06".

## O que NÃO foi feito
- CAS do cursor no Postgres (Fase 3A).
- Tratar `RETROCESSO_REJEITADO` no loop/scheduler (`ERRO_DEFINITIVO` + alerta) — Fase 4.
- Validar documento com `nsu > ultNsu` do lote, ou fora da faixa — ficou só como ideia.
- Mapear `cStat 589` para erro de consistência no adapter — E2-S02/E2-S03.
- O `FakeSourceAdapter` ainda não recebe o NSU atual (limitação acima).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
