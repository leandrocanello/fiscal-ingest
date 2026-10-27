## O que muda
- Novo módulo `infra-postgres` (JDBI 3 + Flyway, ADR-025; sem Spring/JPA, ADR-027) com testes de integração em Testcontainers (`postgres:17-alpine`).
- Migration `V1`: `tenant`, `empresa`, `estado_coleta`, `ledger_tentativa`, `ledger_nsu`, chaveados por `(fonte, cnpj_consultado, ambiente)`. `V2`: triggers que tornam o ledger append-only no próprio banco.
- `JdbiNsuLedger`, a primeira implementação real da porta `NsuLedger`: idempotente (`ON CONFLICT DO NOTHING`), o primeiro status prevalece.
- `enum Ambiente` no domínio (sem `tpAmb`). `PostgresConfig` (senha mascarada) + `FabricaJdbi`.

## Olhar com atenção
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiNsuLedger.java:26`: o ambiente vem pelo construtor, não pela porta (DA-10). Um ledger por ambiente. Confirme se aceita isso até a porta mudar.
- `infra-postgres/src/main/resources/db/migration/V2__ledger_append_only.sql:10`: o trigger bloqueia UPDATE/DELETE, mas **não** TRUNCATE (usado nos testes). Em produção, revogar TRUNCATE/DELETE do papel da aplicação na E3-S03.
- `infra-postgres/src/main/resources/db/migration/V1__esquema_base.sql:47`: `ledger_tentativa` também é append-only. Isso obriga o fetch (E3-S02) a gravar a tentativa já completa (desfecho, `cursor_out`, `duracao_ms`) e não a "abrir e fechar".
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/PostgresConfig.java:16`: o `toString` mostra a URL inteira, e uma URL com `?password=` vazaria a senha (inv. 13). Em `FabricaJdbi.java:17` a senha vira `String` e o `char[]` não é zerado.
- `infra-postgres/src/main/resources/db/migration/V1__esquema_base.sql:13`: ids de `tenant`/`empresa` são `bigint identity`, não `uuid`. A troca fica cara depois que houver dados e RLS.
- `infra-postgres/build.gradle.kts:14`: Testcontainers 2.0.2 (novos nomes de artefato), exigido pelo Docker 29 (DA-11).

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 4 (core sem código de protocolo) | `AmbienteTest.doisAmbientesPrevistos` |
| 5 (grave primeiro / releitura segura) | `JdbiNsuLedgerTest.reregistrarMesmoNsuNaoFalhaNemDuplica`, `JdbiNsuLedgerPropriedadesTest` |
| 6 (append-only) | `AppendOnlyTest.updateEDeleteEmLedgerSaoRecusadosPeloBanco`, `JdbiNsuLedgerTest.reregistrarComStatusDiferenteMantemOPrimeiro` |
| 13 (sem segredo em log) | `PostgresConfigTest.toStringNaoExpoeSenha` |
| Camadas (§19) | `ArquiteturaInfraTest` (sem Spring/JPA; domínio não depende de infra) |

## Decisões do agente nesta subetapa
- DA-10: `Ambiente` no domínio sem mudar a porta `NsuLedger`; o ambiente vai no construtor do `JdbiNsuLedger`.
- DA-11: `postgres:17-alpine`; append-only por trigger; Testcontainers 2.0.x por causa do Docker 29.

## O que NÃO foi feito
- CAS do cursor, lease e caos de dois fetchers ficam para a E3-S02. A coluna `versao` já existe, mas ainda sem lógica.
- RLS, `tenant_id` nas tabelas de coleta e papéis do banco (incluindo revogar TRUNCATE) ficam para a E3-S03.
- Outbox fica para a E3-S04 e o catálogo para a E3-S06. `consentimento`/`certificado` ficam para a E4.
- Pool de conexões (Hikari) entra com os `app-*`. Por ora usamos `PGSimpleDataSource`.
- A troca da assinatura de `NsuLedger`/`ContextoConsulta` vai para uma subetapa própria.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
