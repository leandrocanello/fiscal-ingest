# PARA VOCÊ — fila do humano

> O que só você pode fazer ou decidir. Responda **aqui mesmo** (edite no GitHub, branch `auto/estado`),
> abaixo de cada item, em "Resposta:". Depois ajuste o status no ROADMAP (ex.: `[h]` → `[x]`).
> Itens resolvidos podem ser movidos para o fim, em "Resolvidos".

### [DECISAO] E1-S06 — 2026-10-23 — Como o core compara posições se o `Cursor` é opaco (guarda de não-retrocesso)

**Contexto.** A invariante 1 exige que o cursor nunca recue (auditoria A4). Hoje o `AvancarPonteiroUseCase` aceita
qualquer `proximoCursor` em `Ok`/`Vazio`. O `Cursor` é opaco (`Cursor(String token)`, ARCHITECTURE §5.4): o core
não pode compará-lo. A auditoria deixou "design a propor". A escolha muda o contrato do domínio
(`BuscarLoteResultado`/`ResultadoAvanco`), por isso não decidi sozinho.

**Opções.**
- **A — NSU explícito no resultado** (recomendada): `BuscarLoteResultado` ganha `Nsu ultNsu`, que é a posição
  depois do lote. O caso de uso recebe o `Nsu` atual e só avança se `novo >= atual`.
  Prós: bate com `estado_coleta.ult_nsu` (§8) e com o CAS "só se novo > atual" (§6.5). A guarda fica 100% no core,
  sem confiar no adapter, e o jqwik testa direto. O token continua opaco.
  Contras: o contrato fica preso a fontes sequenciais. Uma fonte futura sem NSU precisaria de `Optional`/`TipoCursor`.
- **B — `Cursor(String token, Nsu posicao)`**: a posição viaja dentro do cursor.
  Prós: um valor só. Contras: o cursor deixa de ser opaco (contraria §5.4) e mexe em todos os `new Cursor(...)`.
- **C — comparador fornecido pelo adapter** (`Comparator<Cursor>` no descritor). Prós: o cursor fica totalmente
  opaco. Contras: quem protege a invariante é o próprio adapter, que pode ter o bug. Fica mais difícil de testar por propriedade.

**Sub-decisões (vêm junto com a escolha):**
1. Igualdade: `Vazio` com `ultNsu == atual` conta como "não avançou, sem erro". **Recomendo: sim.**
2. Ao detectar recuo: não avança, **não registra nada no ledger** (lote inconsistente; o bruto já está no blob)
   e devolve um sinal explícito de erro de consistência, para o loop tratar como `ERRO_DEFINITIVO`/alerta (nunca retentar).
   **Recomendo:** um componente novo em `ResultadoAvanco`, por exemplo `boolean retrocessoRejeitado`, ou um
   `enum MotivoAvanco {AVANCOU, NAO_AVANCOU, RETROCESSO_REJEITADO}`. Recomendo o enum.

**Recomendação:** A + enum. O plano `planos/E1-S06.md` já está escrito para essa escolha.

**Roteiro para você:**
1. Responda abaixo: "A/B/C" + sub-decisões 1 e 2.
2. Se não for A, eu replanejo os passos 2–4 do plano.
3. No ROADMAP, troque E1-S06 de `[h]`/`[!]` para `[ ]` para liberar o dev.

Resposta:

## Resolvidos

- **E1-S01 / E1-S02** — PRs #1 e #2 mergeados pelo autor em 2026-10-21 (testes verdes no PC dele).
- **E1-S03 (D1–D5)** — 2026-10-21: D1 JDBI + Flyway (ADR-025) · D2 jqwik (ADR-026) ·
  D3 Spring Boot só nos `app-*` (ADR-027) · D4 SLF4J + Logback JSON, Grafana na Fase 10 (ADR-028) ·
  D5 duas janelas por dia, 08:00 e 13:00 (ADR-029).
- **[BLOQUEIO] E1-S05** — 2026-10-22: era bug do script (passo sem código); corrigido em `fix/passo-sem-codigo` e E1-S05 mergeada.
- **[DUVIDA] E2-S00** — 2026-10-22: CLAUDE.md §5 atualizado (sem CXF) no PR `ajustes/fila-e-docs`.
