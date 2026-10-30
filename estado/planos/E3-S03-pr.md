## O que muda
Liga o Row-Level Security por tenant em `tenant` e `empresa`. O tenant vem de `app.tenant_id`, local à transação, e a regra falha fechada: sem tenant, nenhuma linha.
Cria o papel `fiscal_app` (`nologin`, sem superuser e sem BYPASSRLS) com privilégios mínimos: ledger só `SELECT/INSERT`, e nada de `DELETE/TRUNCATE` em nenhuma tabela.
Nova classe `TransacaoPorTenant` em `infra-postgres`. Um meta-teste trava tabela futura com `tenant_id` sem RLS.
As tabelas da coleta continuam sem RLS (§8.1, DA-24). `./gradlew test` verde.

## Olhar com atenção
- `infra-postgres/src/main/resources/db/migration/V5__rls_tenant.sql:13-15`: é o coração do isolamento. Confira `nullif(...)::bigint` (vazio ou ausente vira NULL, ou seja, zero linhas) e o `with check`, que impede mover empresa para outro tenant. O literal `app.tenant_id` se repete aqui e no Java.
- `infra-postgres/src/main/resources/db/migration/V4__papel_app.sql:6-19`: o papel é global no cluster (criação idempotente) e os grants são por schema. Revise se a lista de privilégios bate com o que você quer em produção. `estado_coleta` com `UPDATE` é necessário para o CAS.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/TransacaoPorTenant.java:22-31`: API pública que os `app-*` vão usar. `set_config(..., true)` é feito por bind e recusa tenant ≤ 0.
- `infra-postgres/src/test/java/br/com/doc/ingest/infra/postgres/TodaTabelaComTenantIdTemRlsTest.java:25`: lista explícita das tabelas sem `tenant_id`. Na linha 47 o teste aceita qualquer política (não confere se ela usa `tenant_id`) e não verifica grants.
- `infra-postgres/src/test/java/br/com/doc/ingest/infra/postgres/PostgresTestBase.java:16,59`: login e senha só de teste, criados por concatenação de constantes. Fica em `src/test`, sem valor fora do container.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| Isolamento de tenant (ADR-013, §13; base da 12) | `RlsTenantTest` (9 casos: leitura, escrita, falha fechada), `TransacaoPorTenantTest`, `IsolamentoTenantPropriedadesTest` (jqwik), `TodaTabelaComTenantIdTemRlsTest` |
| 6 — append-only | `PapelAppTest.ledgerRecusaUpdateDeleteETruncateParaOPapelApp`, `estadoColetaSemDeleteNemTruncate` + `AppendOnlyTest` existente |
| 1/2/5 — coleta | `PapelAppTest.confirmarLoteFuncionaComoPapelApp` + suítes da E3-S02 inalteradas |
| 13 — sem segredo | nada novo em log; senha só de teste em `src/test` |

## Decisões do agente nesta subetapa
- DA-24: tenant via `app.tenant_id` local à transação com falha fechada; papel de grupo `fiscal_app` criado pela migration; tabelas da coleta sem RLS numa lista explícita testada.

## O que NÃO foi feito
- Leitura entre tenants pelo processor (roteamento §7.3) e papel de plataforma/admin com BYPASSRLS auditado: ficam para a E3-S07/E4. Hoje, sem isso, o processor não lê `empresa` de vários tenants.
- RLS do catálogo, do feed e do outbox: nascem com as tabelas (E3-S04, E3-S06), presas pelo meta-teste. Os **grants** para `fiscal_app` nessas tabelas não são verificados automaticamente.
- Logins e senhas de produção e init do docker-compose: ficam para os `app-*`.
- Integrador não lê empresa sem autorização: fica para a E5-S06.
- Achados menores sem bloqueio: o literal `app.tenant_id` repetido no SQL e no Java; helpers de teste duplicados em 3 classes; `MigracoesTest` não confere a V5 explicitamente.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
