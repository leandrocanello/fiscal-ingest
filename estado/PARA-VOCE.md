# PARA VOCÊ — fila do humano

> O que só você pode fazer ou decidir. Responda **aqui mesmo** (edite no GitHub, branch `auto/estado`),
> abaixo de cada item, em "Resposta:". Depois ajuste o status no ROADMAP (ex.: `[h]` → `[x]`).
> Itens resolvidos podem ser movidos para o fim, em "Resolvidos".

### [DUVIDA] E2-S03 — 2026-10-27 — atualizar docs protegidos com o mapeamento de cStat decidido
- **O que é preciso:** a E2-S03 aplica no código a decisão E2-S02, mas `CLAUDE.md` §3.2 ("`632` → a confirmar"; `108`/`109` juntos)
  e `docs/ARCHITECTURE.md` §5.3 (l.168 e nota l.176–178) seguem desatualizados, e o agente não pode editá-los.
- **Opções:** (a) você atualiza os dois num PR `docs/` depois do merge da E2-S03; (b) deixar para a revisão geral de docs.
- **Recomendação:** (a) — texto sugerido: `108 → IndisponivelTemporario`, `109 → ServicoFora`, `632 (só consChNFe, fora de prazo)
  → IndisponivelDefinitivo`, `589 → IndisponivelDefinitivo (cursor inconsistente)`, desconhecido → `IndisponivelTemporario(5 min)` + alerta; fonte MOC 7.0.
- Resposta:

## Resolvidos

- **E1-S01 / E1-S02** — PRs #1 e #2 mergeados pelo autor em 2026-10-21 (testes verdes no PC dele).
- **E1-S03 (D1–D5)** — 2026-10-21: D1 JDBI + Flyway (ADR-025) · D2 jqwik (ADR-026) ·
  D3 Spring Boot só nos `app-*` (ADR-027) · D4 SLF4J + Logback JSON, Grafana na Fase 10 (ADR-028) ·
  D5 duas janelas por dia, 08:00 e 13:00 (ADR-029).
- **[BLOQUEIO] E1-S05** — 2026-10-22: era bug do script (passo sem código); corrigido em `fix/passo-sem-codigo` e E1-S05 mergeada.
- **[DUVIDA] E2-S00** — 2026-10-22: CLAUDE.md §5 atualizado (sem CXF) no PR `ajustes/fila-e-docs`.
- **[DECISAO] E1-S06** — 2026-10-23: opção A + `enum MotivoAvanco`; igualdade em `Vazio` = não avançou. PR #15 mergeado em 2026-10-26.
- **[BLOQUEIO] E1-S06** — 2026-10-23: era o plano esperando a decisão; corrigido no script (PR #14).
- **[HUMANO] E2-S05** — 2026-10-26: PR #13 já estava mergeado; loop de REVISAR corrigido no script (PR #14).
- **[DECISAO] E2-S02** — 2026-10-26, autor, com base no MOC 7.0 (fonte oficial; `docs/FONTES-OFICIAIS.md`, PR #17):
  D1 (a) `108 → IndisponivelTemporario` (backoff), `109 → ServicoFora` · D2 (a) `632` ("fora de prazo", só consChNFe) →
  `IndisponivelDefinitivo("fora de prazo")` para aquela consulta, sem bloquear o CNPJ e sem retentar · D3 (a) `589 →
  IndisponivelDefinitivo("cursor inconsistente: NSU > maxNSU")`, sem nova variante de `Desfecho` · fallback: sim,
  `IndisponivelTemporario(5 min)` + log de alerta com o cStat bruto (sem XML).
- **[DUVIDA] E2-S01 (prazo de guarda)** — 2026-10-26, autor: **5 anos** (o que a lei exige do contribuinte; mínimo
  necessário para a LGPD). O prazo fica **configurável** na política de retenção; o expurgo é auditado.
