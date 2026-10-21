# PARA VOCÊ — fila do humano

> O que só você pode fazer ou decidir. Responda **aqui mesmo** (edite no GitHub, branch `auto/estado`),
> abaixo de cada item, em "Resposta:". Depois ajuste o status no ROADMAP (ex.: `[h]` → `[x]`).
> Itens resolvidos podem ser movidos para o fim, em "Resolvidos".

### [HUMANO] E1-S01 — 2026-10-21 — revisar e mergear o PR `fase-1.5/build`
No seu PC (fora do WSL serve): `git fetch && git checkout fase-1.5/build && .\gradlew.bat test`.
Leia `docs/AUDITORIA-1.5.md`. Se verde, mergeie. Se algo falhar, anote aqui o módulo e o erro.
Resposta:

### [HUMANO] E1-S02 — 2026-10-21 — revisar e mergear o PR `fase-1.5/docs`
README, ARCHITECTURE e DECISIONS alinhados à v2 (ADRs 009–024; ADR-004 revisado).
Resposta:

### [DECISAO] E1-S03 — 2026-10-21 — decisões que destravam o agente
| # | Decisão | Opções | Recomendação |
|---|---|---|---|
| D1 | Acesso a dados | JDBC puro · JDBI · jOOQ · JPA | **JDBC puro (ou JDBI) + Flyway** |
| D2 | Teste de propriedade | jqwik · nenhum | **jqwik** |
| D3 | Framework HTTP (Fase 6) | Javalin · Helidon SE · Quarkus · Spring Boot | **Javalin ou Helidon SE** |
| D4 | Logs | SLF4J + Logback JSON · outro | **SLF4J + Logback JSON** |
| D5 | Ritmo inicial | 1 janela na semana 1 · 2 janelas | **1 janela na semana 1** |
Resposta (ex.: "D1 JDBC, D2 jqwik, D3 decidir depois, D4 ok, D5 1 janela"):

## Resolvidos
