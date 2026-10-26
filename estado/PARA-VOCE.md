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
4. Em `planos/E1-S06.md`, troque também os dois `[!]` (linha "aguardando decisão" → `[x]`; passo 1 → `[ ]`).

Obs. (2026-10-23 08:02): o ROADMAP está com E1-S06 em `[~]`, então o script continua chamando o DESENVOLVER,
que não tem como avançar. Sugiro `[h]` até você responder.

Resposta:

### [DECISAO] E2-S02 — 2026-10-23 — Mapeamento de `cStat` 108 / 109 / 632 / 589 para `Desfecho` (auditoria A1–A3)

**O que é preciso:** decidir a tabela `cStat → Desfecho` que a E2-S03 vai congelar em teste no `CStatMapper`
(hoje: `108 → Ok`, `632 → LimiteAtingido`, `589` cai no default `IndisponivelTemporario(5 min)` e seria retentado para sempre).
A E2-S01 (`docs/FONTES-OFICIAIS.md`, item 3) só achou fonte (secundária) para `137`, `656` e `589`.
**`107/108/109/632` ficaram sem fonte** — por isso preciso que você confira no MOC antes de decidir.

**Decisão 1 — `108` e `109` (A1)**
- (a) `108 → IndisponivelTemporario(backoff)`, `109 → ServicoFora` (circuit breaker da fonte). Coerente com a leitura usual
  (107 em operação / 108 paralisado momentaneamente / 109 paralisado sem previsão) e com CLAUDE.md §3.2.
  Pró: nunca trata indisponibilidade como sucesso. Contra: texto do MOC ainda não conferido.
- (b) Os dois → `ServicoFora`. Pró: simples. Contra: abre o circuito da fonte inteira por uma paralisação curta.
- (c) Manter `108 → Ok`. **Não recomendo**: um `Ok` sem documentos pode ser tratado como sucesso no loop (§6.7).
- **Recomendação: (a).** Mesmo que o MOC não confirme o texto, (a) é o lado seguro (não avança cursor).

**Decisão 2 — `632` (A2)**
- Lembrança minha, **NÃO verificada**: no contexto de download/distribuição o `632` seria "Solicitação fora de prazo,
  a NF-e não está mais disponível para download" — isto é, documento fora da janela de retenção, não excesso de consultas.
  (O `StatusNsu` já tem `FORA_PRAZO`, o que reforça essa leitura.)
- (a) Se o MOC confirmar "fora de prazo": `632 → IndisponivelDefinitivo("fora de prazo")` **para aquela consulta**
  (relevante em `consChNFe`/`consNSU`, E2-S0x `buscarUm`), sem bloquear o CNPJ e sem retentar.
- (b) Se o MOC disser "excesso de consultas": manter `LimiteAtingido(1h)`.
- (c) Sem fonte: deixar `632` no default (`IndisponivelTemporario`) e registrar como `[VALIDAR]`.
  Contra: retenta algo que pode nunca dar certo.
- **Recomendação:** conferir o texto e aplicar (a) ou (b); se não achar, (b) é o lado mais conservador
  (bloqueia 1h em vez de martelar a SEFAZ — protege a invariante "nunca bloquear CNPJ").

**Decisão 3 — `589` (A3)** — "NSU informado superior ao maior NSU do AN" (fonte secundária, MOC consolidado 09/10/2026).
Significa cursor corrompido: nunca retentar. O `Desfecho` sealed hoje não tem variante de "erro de consistência".
- (a) `589 → IndisponivelDefinitivo("cursor inconsistente: NSU > maxNSU")`. Pró: zero mudança no domínio;
  o loop (§6.7) já leva a `ERRO_DEFINITIVO` + DLQ. Contra: o motivo vira texto; o scheduler não distingue de outros definitivos.
- (b) Nova variante `Desfecho.ErroConsistencia(String motivo)`. Pró: alerta crítico dedicado (como o `656`), métrica própria.
  Contra: mexe no contrato do adapter (sealed ⇒ todos os `switch` do core precisam tratar), mais um ADR.
- **Recomendação: (a) agora**, com o motivo padronizado; reavaliar (b) na Fase 4 se o scheduler precisar distinguir.

**Sub-decisão — cStat desconhecido:** manter `IndisponivelTemporario(5 min)` como fallback? **Recomendo sim**, mas
com log de alerta do cStat bruto (sem XML) para descobrir códigos novos.

**Roteiro para você (~20–30 min):**
1. Abra o portal da NF-e: https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=ndIjl+iEFdE=
   (Documentos → Manual de Orientação do Contribuinte / Anexos) e baixe o **MOC 7.0 – Anexo I/tabela de códigos de
   status e rejeições** mais recente (consolidação de 09/10/2026, se houver).
2. Na mesma área (Notas Técnicas), baixe a **NT 2014.002** (Distribuição de DF-e, versão mais recente, ex.: v1.40).
3. No PDF, procure (Ctrl+F) por `107`, `108`, `109`, `632`, `589`, `656` e anote o texto literal de cada um.
4. Copie os trechos para `docs/FONTES-OFICIAIS.md` item 3 (fonte + data) — ou cole-os aqui na resposta que eu registro.
5. Responda abaixo no formato: `D1: a · D2: a|b|c · D3: a|b · fallback: sim|não` (+ os trechos do passo 3, se tiver).
6. No ROADMAP, marque E2-S02 como `[x]` para liberar a E2-S03 (o planejador escreve o plano dela com a sua escolha).
   Se escolher D3 (b), avise: vira subetapa AUTO+ com ADR antes da E2-S03.

Resposta:

## Resolvidos

- **E1-S01 / E1-S02** — PRs #1 e #2 mergeados pelo autor em 2026-10-21 (testes verdes no PC dele).
- **E1-S03 (D1–D5)** — 2026-10-21: D1 JDBI + Flyway (ADR-025) · D2 jqwik (ADR-026) ·
  D3 Spring Boot só nos `app-*` (ADR-027) · D4 SLF4J + Logback JSON, Grafana na Fase 10 (ADR-028) ·
  D5 duas janelas por dia, 08:00 e 13:00 (ADR-029).
- **[BLOQUEIO] E1-S05** — 2026-10-22: era bug do script (passo sem código); corrigido em `fix/passo-sem-codigo` e E1-S05 mergeada.
- **[DUVIDA] E2-S00** — 2026-10-22: CLAUDE.md §5 atualizado (sem CXF) no PR `ajustes/fila-e-docs`.

### [BLOQUEIO] E1-S06 — 2026-10-23 — 3 falhas seguidas
Último motivo: nenhum commit feito e nenhum passo fechado no plano
Logs: `estado/logs/` (arquivos *-E1-S06-*.json). Para destravar: ajuste o plano `estado/planos/E1-S06.md`
ou responda aqui, e troque o status no ROADMAP de `[!]` para `[~]`.

### [DUVIDA] E2-S01 — 2026-10-23 — Prazo de guarda do XML: 5 anos (CLAUDE.md §14) × 11 anos?
Em `docs/FONTES-OFICIAIS.md` (item 14), fontes secundárias citam o Ajuste SINIEF 2/2025 com 132 meses (11 anos) contados da autorização; o §14 diz "costuma ser 5 anos". Alcance incerto (contribuinte ou só o Fisco); o texto primário não foi lido.
Opções: (a) manter 5 anos como mínimo e tratar o prazo como configurável por política; (b) adotar 11 anos; (c) esperar o parecer do jurídico.
Recomendação: (a) com expurgo desligado até o parecer. Não editei o CLAUDE.md.
Resposta:

### [HUMANO] E2-S05 — 2026-10-26 — PR aprovado mas não aberto; REVISAR roda em loop
- O que é preciso: a E2-S05 está aprovada desde 2026-10-23 e já passou por 17 revisões idênticas (última: 2026-10-26) (HEAD `7db3c65` sem mudança). O script parece não abrir o PR a partir de `planos/E2-S05-pr.md` e volta a chamar o REVISAR (ROADMAP ainda `[~]`).
- Opções: (a) conferir no log do script por que o passo "abrir PR" não rodou/falhou e abrir o PR manualmente; (b) marcar a subetapa como pronta para o script avançar.
- Recomendação: (a). O arquivo do PR está pronto e válido; cada nova revisão só gasta janela.
