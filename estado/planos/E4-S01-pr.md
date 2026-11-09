## O que muda
- Nova porta `FilaColeta` (`reivindicar`, `renovar`, `liberar`, `registrarBloqueio`) e os records `PedidoReivindicacao` e `ColetaReivindicada` em `domain.coleta`.
- `JdbiFilaColeta`: claim de estados `ATIVO` vencidos (`due_at`), não bloqueados e sem lease vigente, com `FOR UPDATE SKIP LOCKED` + lease. É a primeira linha da inv. 2; o CAS da E3-S02 continua sendo a última.
- `V12__fila_coleta.sql`: índice parcial `(due_at nulls first) where estado = 'ATIVO'`.
- Testes: unitários, caos com 8 threads concorrentes, CAS junto com o claim e propriedades jqwik da fila.

## Olhar com atenção
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiFilaColeta.java:55-60`: é o coração da inv. 2. O `skip locked` no CTE mais a reavaliação do `WHERE` na linha travada (READ COMMITTED) garantem um dono só. As mutações estão no PROGRESSO: sem `skip locked`, o claim trava; sem o lock, aparecem dois donos.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiFilaColeta.java:120-121` e `domain/.../coleta/FilaColeta.java:27`: `registrarBloqueio` **não confere o dono** e nunca encurta o bloqueio (DA-34). É intencional: a SEFAZ já bloqueou o CNPJ, seja qual for o dono. Confirme se concorda com a DA-34.
- `infra-postgres/src/test/java/br/com/doc/ingest/infra/postgres/FilaColetaCasTest.java:89`: um dono cujo lease venceu **ainda confirma** o lote (o claim não incrementa `versao`, sem fencing). É a escolha da DA-34 a favor da inv. 1. Se preferir fencing, a mudança é uma linha no `update` do claim.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiFilaColeta.java:37`: o desempate em Java usa o ordinal de `Ambiente`, e o SQL usa o texto. A ordem da lista diverge no caso raro de mesma fonte+CNPJ nos dois ambientes. Não afeta a correção.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiFilaColeta.java:106`: `liberar` pode pôr `due_at` antes de `bloqueado_ate`. É inofensivo, porque o claim também filtra `bloqueado_ate`.
- `infra-postgres/src/main/resources/db/migration/V12__fila_coleta.sql`: a numeração pula de V3 para V12 na `main`. As V4–V11 estão em outras branches e, sem `outOfOrder`, a ordem dos merges importa.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — Não-retrocesso de NSU | `JdbiFilaColetaTest.reivindicarNaoMudaVersaoNemCursor`, `FilaColetaCasTest.donoVencidoAindaConfirmaOLoteQueJaConsumiu`, `FilaColetaPropriedadesTest.claimNuncaMudaCursorNemVersao`; `EstadoColetaCasPropriedadesTest` segue verde |
| 2 — Um CNPJ por vez | `CaosClaimConcorrenteTest.nenhumaChaveTemDoisDonosAoMesmoTempo` / `reivindicacaoConcorrenteDeLeaseVencidoTemUmVencedor` / `claimNaoEsperaLinhaTravada`, `FilaColetaPropriedadesTest.chaveComLeaseVigenteNuncaEhReivindicada`, `JdbiFilaColetaTest.bloqueioRecebidoPorDonoVencidoValeMesmoAssim`; `CaosDoisFetchersTest` segue verde |
| 10 — Atribuição pelo conteúdo | não tocada: o diff não usa tenant |
| 13 — Nada de segredo em log | não há log novo no diff |

## Decisões do agente nesta subetapa
- DA-34: o claim não muda `versao`; o dono vencido ainda confirma o lote e o bloqueio dele sempre vale; o teto por tenant é por chamada de `reivindicar` (decisão de 2026-11-03).
- Decisões do autor em `[DECISAO] E4-S01` (D1–D4); a D4 = a separou a fairness para a E4-S01b.

## O que NÃO foi feito
- **Fairness por tenant** (teto por tenant no claim, `FilaColetaTenantTest`, `FairnessPorTenantTest`) ficou para a **E4-S01b** (D4 = a). Hoje o claim não considera tenant: um escritório grande ainda pode ocupar o lote inteiro.
- Teste como usuário `fiscal_app` (sem superuser): o `dataSourceComoApp` da E3-S03 ainda não está na `main`.
- O laço do scheduler, o valor do lease, o mapeamento de `Desfecho` para bloqueio/due, as transições de `estado` e a conferência de consentimento e certificado antes do claim ficam fora do escopo (E4-S02/S03/S05).
- O `dono` precisa ser único por instância. Isso fica a cargo de quem compuser o scheduler.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
