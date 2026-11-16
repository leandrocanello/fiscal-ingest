## O que muda
Deixa a `main` verde de novo depois do merge do #44 (E3-S07), que tinha sido integrado sem rodar a suíte. Mexe **só em testes**, em 10 arquivos:
- `MetadadosDfeTest`: o construtor de `MetadadosDfe` agora recebe os 2 campos novos (`versaoLayout`, `cStatProtocolo`). Era a falha de compilação do `adapter-nfe-nacional`.
- CNPJs e chave sintéticos com DV inválido desde a E1-S07 tiveram o DV recalculado, com a mesma raiz.
`./gradlew test --rerun-tasks` passou em `HEAD` (1569 testes segundo o dev; o revisor rodou de novo, EXIT 0).

## Olhar com atenção
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/doczip/ParserDfeNfeTest.java:25` — a chave mudou só no último dígito (DV `0` → `3`). Os CNPJs de transportador e `autXML` (linhas 51-52) e as asserções das linhas 107-131 e 235-238 acompanham as constantes. Confira que nenhuma asserção mudou de sentido.
- `infra-postgres/src/test/java/br/com/doc/ingest/infra/postgres/JdbiCadastroEmpresasTest.java:56` — quatro CNPJs agora terminam em `…000181`. Continuam distintos entre si, e o teste de isolamento entre tenants (linha 66) segue com o mesmo desenho.
- `app-worker/src/test/java/br/com/doc/ingest/worker/processador/ProcessadorPropriedadesTest.java:65` — teste de propriedade do roteamento (inv. 10). Os 5 CNPJs continuam diferentes; só o DV mudou.
- Processo: o #44 foi mergeado com conflitos resolvidos sem rodar a suíte. Pode valer exigir `./gradlew test` verde (CI) antes de qualquer merge.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| Nenhuma enfraquecida (só fixtures). A inv. 10 continua coberta e agora volta a rodar | `ProcessadorPropriedadesTest`, `ProcessadorColetadoIntegracaoTest`, `CaosProcessadorTest` |

## Decisões do agente nesta subetapa
- nenhuma

## O que NÃO foi feito
- Não migrei as fixtures para `FixturesFiscais`: ficaria fora do "mínimo" do plano. Pode virar uma limpeza futura.
- Nenhum refactor e nenhum comportamento novo (fora do escopo).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
