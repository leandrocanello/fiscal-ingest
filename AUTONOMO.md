# AUTONOMO.md — Desenvolvimento autônomo do Fiscal-Ingest

> Como fazer o Claude Code desenvolver o projeto **sozinho, de segunda a sexta, em duas janelas de 5h**,
> com **você revisando 1x por dia à noite (1h–1h20)**, principalmente **lendo o que foi feito**, e não conversando.
> Sábado e domingo o agente fica parado.
>
> Este arquivo é o **projeto do processo**. O projeto do produto está no `CLAUDE.md` v2 (que vira `docs/PROJETO.md`, seção 3).
> Itens marcados com **[VERIFICAR]** dependem da versão do Claude Code e devem ser testados antes de confiar neles.

---

## 1. A ideia em 6 regras

1. **Um script decide e a IA executa.** O script lê o estado (arquivos + git), escolhe a próxima ação e chama o
   Claude com o papel certo. Cada chamada faz **uma** coisa e termina.
2. **Todo commit é verde.** Depois de cada chamada, o **script** (não o agente) roda a suíte inteira. Se estiver vermelha, ele desfaz a chamada.
3. **O agente nunca escreve no `main`.** Cada subetapa vira um **Pull Request** no GitHub. **Só você faz o merge.**
4. **O que o agente não consegue fazer, ele mostra e pula.** Vai para `autonomo/PARA-VOCE.md` e o agente segue para
   a próxima subetapa independente. Ele só fica parado se não houver mais nada desbloqueado.
5. **A produção diária cabe na sua revisão.** Há um orçamento diário de diff: quando atinge o limite, o agente para de
   produzir código novo. Sem isso, a fila de revisão cresce sem fim e os erros se acumulam.
6. **A comunicação é assíncrona, por arquivo e PR.** Você responde nos comentários do PR e no `PARA-VOCE.md`. O agente lê
   isso no começo do dia seguinte, antes de qualquer outra coisa.

---

## 2. Rotina semanal

| Quando | O que acontece | Quem |
|---|---|---|
| Seg–Sex **08:00** | Janela 1: script em loop até o fim da janela, o orçamento ou o limite de uso | Agente |
| Seg–Sex **13:00** | Janela 2: idem | Agente |
| Seg–Sex **17:50** | Fechamento: relatório do dia + PRs atualizados + push | Script (+ agente, se houver cota) |
| Seg–Sex **noite (1h–1h20)** | Revisão | **Você** |
| Sáb–Dom | Agente parado. Se quiser, use as suas 2h para os itens `HUMANO` (homologação, fontes oficiais) | Você (opcional) |

> A janela de 5h começa na primeira mensagem. Disparando às 08:00 e às 13:00, as duas janelas ficam
> encostadas, e o seu uso à noite cai numa janela nova. **Existe também um limite semanal:** 10 janelas pesadas
> por semana podem passar dele. A primeira semana serve para medir (seção 11).

### 2.1 Sua revisão noturna (roteiro de ~70 min)

| Min | Faça | Onde |
|---|---|---|
| 0–10 | Leia o relatório do dia | `autonomo/revisao/AAAA-MM-DD.md` |
| 10–55 | Revise os PRs em `aguardando-revisao`, começando pelos arquivos que o relatório marcou em **"olhar com atenção"** | GitHub |
| — | Aprovou → **merge**. Quer mudança → **"Request changes"** com comentários objetivos | GitHub |
| 55–70 | Responda o `PARA-VOCE.md` (decisões, itens HUMANO) e confira o `DECISOES-AGENTE.md` | repositório |

Na manhã seguinte, a **primeira** coisa que o agente faz é tratar os seus "Request changes".

---

## 3. Estrutura de arquivos

```
fiscal-ingest/
├── CLAUDE.md                      # CURTO: regras do ciclo + ponteiro para docs/PROJETO.md
├── docs/
│   ├── PROJETO.md                 # o CLAUDE.md v2 renomeado — SOMENTE LEITURA para o agente
│   ├── ARCHITECTURE.md            # SOMENTE LEITURA
│   ├── DECISIONS.md               # SOMENTE LEITURA
│   └── FONTES-OFICIAIS.md         # o agente pode RASCUNHAR; quem confirma é você
├── autonomo/
│   ├── ROADMAP.md                 # subetapas, com status e dependências (formato lido pelo script)
│   ├── planos/E2-S04.md           # plano de cada subetapa
│   ├── PARA-VOCE.md               # fila do humano: decisões, itens HUMANO, bloqueios
│   ├── DECISOES-AGENTE.md         # escolhas que o agente fez sozinho (revisar)
│   ├── PROGRESSO.md               # log curto por chamada
│   ├── estado.json                # estado do SCRIPT (falhas, chamadas, orçamento) — o agente não edita
│   ├── revisao/AAAA-MM-DD.md      # relatório diário
│   ├── papeis/*.md                # prompt de cada papel
│   ├── bin/*.sh                   # scripts
│   ├── PARAR                      # (arquivo vazio) se existir, o script não roda — seu botão de emergência
│   └── logs/
└── .claude/
    └── settings.json              # hooks (travas)
```

---

## 4. Ambiente e segurança (pré-requisito)

O agente roda com `--dangerously-skip-permissions`. **Isso não isola nada.** Ele pode fazer tudo o que o usuário
que o executa consegue fazer. Por isso o isolamento vem do ambiente:

1. **Uma distro WSL2 dedicada** (ex.: `fiscal-dev`), com usuário `dev`.
   - Em `/etc/wsl.conf`, desligar a montagem do C: e a interoperabilidade com o Windows:
     ```ini
     [automount]
     enabled = false
     [interop]
     enabled = false
     appendWindowsPath = false
     ```
     Assim o agente **não enxerga** os seus arquivos do Windows nem a outra conta do PC.
   - Instalar Java 21, Git, `gh`, `jq` e o Claude Code **dentro** da distro.
   - Docker Desktop com integração WSL **só para essa distro** (necessário para o Testcontainers a partir da Fase 3).
2. **Repositório no GitHub (privado).**
   - **Proteção do `main`:** exige PR, exige 1 aprovação, bloqueia push direto e force-push.
   - O agente usa um **token fine-grained** com acesso **só a este repositório** (Contents: write, Pull requests: write).
     Com a proteção ligada, mesmo que o agente tente, não consegue escrever no `main`.
3. **Nada sensível no ambiente do agente:** sem `.pfx`, sem XML real, sem senha de homologação. Os testes usam certificados
   **autoassinados gerados no próprio teste**.
4. **Conta pessoal:** `CLAUDE_CONFIG_DIR=/home/dev/.claude-pessoal`, fazendo login uma vez com a conta Pro/Max.
   Como fica em outro usuário de outro sistema, não mistura com a conta do Windows.
5. **Hooks como travas** (seção 8). Eles rodam **mesmo** com `--dangerously-skip-permissions` **[VERIFICAR]**.

> **Alternativa a avaliar:** rodar como *tarefa agendada na nuvem* do próprio Claude, com o repositório no GitHub. A vantagem
> é que o PC não precisa ficar ligado e o container é descartável. Pontos a confirmar: suporte a Docker para o
> Testcontainers, duração máxima por execução e consumo da mesma cota. Comece pelo WSL. Se o PC ligado virar problema, reavalie.

---

## 5. O que roda sozinho e o que fica com você

Cada subetapa do roadmap tem um **tipo**:

| Tipo | Significado | O agente... |
|---|---|---|
| `AUTO` | Totalmente automatizável | Planeja, implementa, verifica e abre o PR |
| `AUTO+` | Automatizável, mas sensível (concorrência, criptografia, segurança) | Igual, mas o plano usa **Opus** no desenvolvimento e o PR leva o selo **"revisão forte"** |
| `RASCUNHO` | O agente pesquisa e redige, você confirma | Escreve o rascunho (ex.: `FONTES-OFICIAIS.md` com links) e abre o PR para você validar |
| `DECISAO` | Precisa de uma escolha sua **antes** | Escreve em `PARA-VOCE.md` as opções e a recomendação, e **pula** até você responder |
| `HUMANO` | Só você pode fazer (SEFAZ real, certificado real, jurídico) | Prepara o necessário (roteiro, script, checklist), coloca em `PARA-VOCE.md` e **pula** |

**Regra de ouro do agente:** se o que fazer não está claro, ele não chuta.
- **Decisão pequena e reversível:** decide, registra em `DECISOES-AGENTE.md` e segue.
- **Decisão que contradiz `docs/` ou muda arquitetura, contrato, esquema público ou segurança:** marca `[!]` com o motivo, cria um item em `PARA-VOCE.md` e **vai para outra subetapa**.

---

## 6. Formato do ROADMAP (o script lê este formato)

Uma linha por subetapa:
```
- [STATUS] ID | TIPO | deps: ID,ID | título
```
| Status | Significado | Quem define |
|---|---|---|
| `[ ]` | pendente | — |
| `[~]` | em andamento | script |
| `[r]` | aguardando sua revisão (PR aberto) | script |
| `[c]` | você pediu mudanças | script (lendo o GitHub) |
| `[x]` | aprovado e mergeado | script (lendo o GitHub) — **o agente nunca marca `[x]`** |
| `[!]` | bloqueado (motivo em `PARA-VOCE.md`) | script/agente |

**Uma subetapa só pode começar** quando todas as dependências estiverem em `[x]`, **ou** em `[r]` desde que a fila
de revisão esteja abaixo do limite (seção 7.3). Isso é o *empilhamento*.

### 6.1 Rascunho inicial do ROADMAP (revisar comigo na implantação)

```
## Etapa 1 — Saneamento (Fase 1.5)  → feito COM VOCÊ antes de ligar o autônomo
- [ ] E1-S01 | HUMANO   | deps: -      | git init, GitHub, proteção do main, wrapper, build verde
- [ ] E1-S02 | HUMANO   | deps: E1-S01 | correções dos docs (CLAUDE.md v2 §18) + renomear para docs/PROJETO.md
- [ ] E1-S03 | DECISAO  | deps: -      | decisões que destravam o agente (seção 6.2)
- [ ] E1-S04 | AUTO     | deps: E1-S01 | ArchUnit: regra "domain não depende de nada"
- [ ] E1-S05 | AUTO     | deps: E1-S01 | jqwik + propriedades do que já existe (GapCalculator, AvancarPonteiro, TokenBucket)

## Etapa 2 — Adapter NF-e Ambiente Nacional (Fase 2)
- [ ] E2-S01 | RASCUNHO | deps: -      | docs/FONTES-OFICIAIS.md: rascunho com links do MOC/NTs para os itens [VALIDAR]
- [ ] E2-S02 | AUTO     | deps: E1-S01 | porta BlobStore no domain + implementação em memória
- [ ] E2-S03 | AUTO     | deps: E1-S01 | decode de docZip (base64+gzip) + classificador de schema + extração de metadados
- [ ] E2-S04 | AUTO     | deps: E2-S03 | interface de transporte SOAP + montagem do envelope distDFeInt
- [ ] E2-S05 | AUTO     | deps: E2-S02,E2-S04 | buscarLote com WireMock (137/138/656/108/109/timeout/5xx/malformado)
- [ ] E2-S06 | AUTO     | deps: E2-S05 | suíte de contrato do SourceAdapter (fake + nfe-nacional)
- [ ] E2-S07 | AUTO     | deps: E2-S05 | buscarUm (consNSU) + consChNFe
- [ ] E2-S08 | AUTO+    | deps: E1-S01 | carregador de .pfx (inclui legado) + SSLContext por certificado + cache LRU/TTL
- [ ] E2-S09 | AUTO+    | deps: E2-S08,E2-S01 | manifestar: evento 210210 + XML-DSig (com chave de teste)
- [ ] E2-S10 | HUMANO   | deps: E2-S07,E2-S09 | smoke em homologação: distNSU, consChNFe, ciência, autXML

## Etapa 3 — Persistência e catálogo (Fase 3)
- [ ] E3-S01 | AUTO     | deps: E1-S03 | Flyway + esquema base (tenant, empresa, estado_coleta, ledger_*)
- [ ] E3-S02 | AUTO+    | deps: E3-S01 | CAS do cursor + testes de caos (dois fetchers, um avança)
- [ ] E3-S03 | AUTO+    | deps: E3-S01 | RLS por tenant + teste "A não lê B"
- [ ] E3-S04 | AUTO     | deps: E3-S01 | outbox transacional + relay (fila no Postgres)
- [ ] E3-S05 | AUTO     | deps: E2-S02 | BlobStore MinIO (Testcontainers) + sha256 + idempotência
- [ ] E3-S06 | AUTO+    | deps: E3-S01 | catálogo: documento/arquivo/proveniência/documento_empresa
- [ ] E3-S07 | AUTO+    | deps: E3-S06,E2-S03 | processor de parse + ROTEAMENTO PELO CONTEÚDO + quarentena
- [ ] E3-S08 | AUTO     | deps: E3-S02..07 | ponta a ponta local: 1 CNPJ empresa + 1 CNPJ escritório (fake)

## Etapa 4 — Scheduler, cofre, consentimento (Fase 4)
- [ ] E4-S01 | AUTO     | deps: E3-S02 | claim SKIP LOCKED + lease + due_at + fairness por tenant
- [ ] E4-S02 | AUTO+    | deps: E2-S08 | CofreDeCertificados local (envelope encryption) — sem segredo em log
- [ ] E4-S03 | AUTO     | deps: E3-S01 | consentimento por finalidade + máquina de estados de estado_coleta
- [ ] E4-S04 | AUTO     | deps: E4-S02 | alertas de vencimento + rotação sem perder cursor
- [ ] E4-S05 | AUTO     | deps: E4-S01..04 | simulação de N CNPJs sem nenhum 656

## Etapa 5 — Porta push + API de entrega (Fases 5 e 6)
- [ ] E5-S01 | DECISAO  | deps: -      | framework HTTP dos app-* (seção 6.2)
- [ ] E5-S02 | AUTO+    | deps: E3-S06,E5-S01 | POST /v1/ingestao/arquivos + parser seguro (XXE, zip bomb)
- [ ] E5-S03 | AUTO+    | deps: E5-S02 | validação de assinatura + confiança + CNPJ do agente
- [ ] E5-S04 | AUTO+    | deps: E3-S04 | sequenciador do feed + teste de propriedade "sem buracos"
- [ ] E5-S05 | AUTO     | deps: E5-S04 | GET /v1/feed, /documentos, /xml + OpenAPI
- [ ] E5-S06 | AUTO+    | deps: E5-S05 | OAuth do integrador + autorização por empresa/escopo
- [ ] E5-S07 | AUTO+    | deps: E5-S06 | webhooks: HMAC, retentativa, reenvio, log de entregas
- [ ] E5-S08 | AUTO     | deps: E5-S07 | sandbox de integrador

## Etapa 6 — Agente Go (Fase 7) — repositório fiscal-agent, processo próprio (seção 12)
```

### 6.2 Decisões que destravam o agente (responder antes de ligar)

| # | Decisão | Opções | Minha recomendação |
|---|---|---|---|
| D1 | Acesso a dados | JDBC puro · JDBI · jOOQ · JPA/Hibernate | **JDBC puro (ou JDBI) + Flyway**. Você quer `ON CONFLICT`, CAS e append-only explícitos; ORM atrapalha |
| D2 | Teste de propriedade | jqwik · nada | **jqwik** |
| D3 | Framework HTTP (só Fase 6) | Javalin · Helidon SE · Quarkus · Spring Boot | **Javalin ou Helidon SE** (leve, coerente com "sem framework pesado"); Spring só se você quiser estudar Spring |
| D4 | Logs | SLF4J + Logback JSON · outro | **SLF4J + Logback com encoder JSON** |
| D5 | Ritmo inicial | 1 janela/dia na semana 1 · 2 janelas desde já | **1 janela na semana 1**, para calibrar |

---

## 7. O ciclo

### 7.1 Ordem de prioridade do script (a cada chamada)
```
0. Existe autonomo/PARAR, ou é sábado/domingo?             → sair
1. Algum PR em "changes requested"?                        → CORRIGIR (papel: desenvolvedor)
2. Algum PR mergeado desde a última vez?                   → marcar [x]; rebase das branches empilhadas
3. Orçamento diário de diff estourado?                     → só CORRIGIR/RASCUNHO; senão sair
4. Há subetapa [~]?
     sem plano                                             → PLANEJAR (planejador)
     passo "- [ ]" em Desenvolvimento                      → DESENVOLVER 1 passo (desenvolvedor)
     passo "- [ ]" em Verificação                          → VERIFICAR (testador)
     tudo marcado                                          → REVISAR + abrir PR (revisor) → [r]
5. Senão, pegar a próxima subetapa elegível:
     tipo AUTO/AUTO+/RASCUNHO, deps ok, fila de revisão < limite → [~] e voltar ao 4
     tipo DECISAO/HUMANO                                   → PREPARAR item em PARA-VOCE (planejador) e pular
6. Nada elegível                                           → sair (ocioso; o relatório registra)
```

### 7.2 Depois de **cada** chamada (o script verifica; o agente não "declara" nada)
1. **Docs protegidos intactos?** `git diff --quiet HEAD@{antes} -- docs/PROJETO.md docs/ARCHITECTURE.md docs/DECISIONS.md`. Se mudaram, reverte.
2. **Suíte inteira verde?** `./gradlew test --no-daemon`. Se estiver vermelha:
   `git reset --hard <commit de antes>`, `falhas[subetapa]++` em `estado.json`, registra no `PROGRESSO.md`.
3. **3 falhas no mesmo passo** → `[!]` + item em `PARA-VOCE.md` com o log resumido, e segue para outra subetapa.
4. **Chamada atingiu o limite de uso?** Sai do loop sem contar falha **[VERIFICAR a mensagem/código de saída]**.
5. Atualiza `estado.json` (chamadas, linhas de diff do dia).

### 7.3 Limites (os botões que você ajusta)
| Parâmetro | Inicial | Para quê |
|---|---|---|
| `MAX_CHAMADAS_JANELA` | 20 | não queimar a cota semanal |
| `TIMEOUT_CHAMADA` | 45 min | chamada travada não come a janela |
| `--max-turns` por chamada | 60 **[VERIFICAR]** | idem |
| `ORCAMENTO_DIFF_DIA` | ~800 linhas de código de produção (testes não contam) | **caber na sua revisão de 1h** |
| `FILA_REVISAO_MAX` | 2 PRs em `[r]`/`[c]` | limitar o empilhamento e o retrabalho em cascata |
| `PARAR_ANTES_DO_FIM` | 15 min antes do fim da janela | deixar o commit limpo |

### 7.4 TDD dentro do ciclo (compatível com o `docs/PROJETO.md`)
- **Cada passo de Desenvolvimento** = escrever o teste → rodar e **ver falhar** (colar a saída no `PROGRESSO.md`) →
  implementar → suíte verde → **1 commit** com teste e código juntos.
- **A seção Verificação** do plano **não** é "escrever os testes depois". Ela serve para: rodar a suíte inteira, acrescentar os
  testes de propriedade, contrato e caos que a subetapa exige, e conferir as invariantes do `PROJETO.md §2.2`.

---

## 8. Hooks (`.claude/settings.json`)

O script define `FI_PAPEL=planejador|desenvolvedor|testador|revisor` antes de cada chamada. Os hooks usam isso.

```json
{
  "hooks": {
    "PreToolUse": [
      { "matcher": "Edit|Write|MultiEdit|NotebookEdit",
        "hooks": [{ "type": "command", "command": "autonomo/bin/hook-arquivos.sh" }] },
      { "matcher": "Bash",
        "hooks": [{ "type": "command", "command": "autonomo/bin/hook-bash.sh" }] }
    ]
  }
}
```

`autonomo/bin/hook-arquivos.sh` — bloqueia docs protegidos e restringe o que cada papel edita:
```bash
#!/usr/bin/env bash
# Recebe o JSON da ferramenta na stdin. Saída 2 = bloqueia e a mensagem volta para o agente.
f=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')
case "$f" in
  *docs/PROJETO.md|*docs/ARCHITECTURE.md|*docs/DECISIONS.md|*autonomo/estado.json|*.claude/settings.json|*autonomo/bin/*)
    echo "BLOQUEADO: $f é somente leitura. Se precisa mudar, registre em autonomo/PARA-VOCE.md." >&2; exit 2;;
esac
if [[ "$FI_PAPEL" == "planejador" || "$FI_PAPEL" == "revisor" ]]; then
  case "$f" in
    *autonomo/*|*docs/FONTES-OFICIAIS.md) exit 0;;
    *) echo "BLOQUEADO: o papel $FI_PAPEL não edita código ($f)." >&2; exit 2;;
  esac
fi
exit 0
```

`autonomo/bin/hook-bash.sh` — bloqueia comandos perigosos:
```bash
#!/usr/bin/env bash
c=$(jq -r '.tool_input.command // ""')
if grep -Eq 'git push.*(main|--force|-f( |$))|git (reset --hard|rebase).*main|rm -rf (/|~|\.\.)|gh pr merge|gh repo|curl .*\| *(ba)?sh' <<<"$c"; then
  echo "BLOQUEADO pelo hook: comando não permitido no modo autônomo: $c" >&2; exit 2
fi
exit 0
```
> O push e o reset das branches de trabalho são feitos **pelo script**, não pelo agente. O agente só faz `git add`/`git commit` na branch atual.

---

## 9. Papéis

Cada papel = um arquivo em `autonomo/papeis/` + um modelo + ferramentas. O script chama o Claude assim **[VERIFICAR as flags com `claude --help`]**:
```bash
FI_PAPEL=desenvolvedor claude -p "$(cat autonomo/prompts/desenvolver.md)" \
  --append-system-prompt "$(cat autonomo/papeis/desenvolvedor.md)" \
  --model "$MODELO" --max-turns 60 --output-format json \
  --dangerously-skip-permissions
```
(Alternativa: subagentes em `.claude/agents/*.md`. Para um script determinístico, chamar com o papel explícito é mais simples de controlar e testar.)

| Papel | Modelo | Pode editar | Entrada | Saída |
|---|---|---|---|---|
| **planejador** | Opus | só `autonomo/` e `FONTES-OFICIAIS.md` | ROADMAP + seções relevantes do `PROJETO.md` | `planos/Ex-Sy.md`; itens em `PARA-VOCE.md` |
| **desenvolvedor** | Sonnet (Opus se o plano disser `AUTO+`) | código + testes + `PROGRESSO.md` + `DECISOES-AGENTE.md` + checkbox do plano | **só o plano** e as seções que ele cita | 1 passo, 1 commit |
| **testador** | Sonnet | testes + checkbox do plano | plano + diff da subetapa | testes de propriedade, contrato e caos; suíte verde; commit |
| **revisor** | Opus | só `autonomo/` | diff da subetapa (`git diff base...HEAD`) + `PROJETO.md` + `ARCHITECTURE.md` | parecer no plano; descrição do PR; "olhar com atenção" |

### 9.1 `autonomo/papeis/desenvolvedor.md` (essência)
```markdown
Você é o DESENVOLVEDOR do Fiscal-Ingest em modo autônomo. Ninguém vai responder perguntas.
- Leia o plano indicado e APENAS as seções do docs/PROJETO.md que ele cita.
- Faça SOMENTE o primeiro passo "- [ ]" da seção Desenvolvimento.
- TDD: escreva o teste, rode e veja FALHAR (cole as 5 linhas relevantes no PROGRESSO.md),
  implemente, rode a SUÍTE INTEIRA (./gradlew test), deixe verde.
- Marque o passo [x] e faça 1 commit em pt-BR: "feat(E2-S05): <passo>".
- Decisão pequena e reversível: decida e registre em autonomo/DECISOES-AGENTE.md (contexto, opções, escolha, como desfazer).
- Decisão que contradiz docs/, ou muda arquitetura, contrato público, esquema, segurança: NÃO faça.
  Escreva o item em autonomo/PARA-VOCE.md, marque o passo como "- [!] motivo" e termine.
- Nunca: chamar a SEFAZ real, commitar segredo/XML real, editar docs/, mexer em outro passo.
- Termine com 3 linhas no PROGRESSO.md: feito / testes / próximo.
```

### 9.2 Modelo de plano (`autonomo/planos/E2-S05.md`)
```markdown
# E2-S05 — buscarLote com WireMock
Tipo: AUTO · Modelo dev: sonnet · Base: auto/E2-S04
Ler: PROJETO.md §2.2 (1–5, 14), §3.1, §3.2, §6.7, §9 · ARCHITECTURE.md §5
Invariantes que esta subetapa toca: 1, 3, 4, 5

## Desenvolvimento
- [ ] Teste: 138 com 2 docZip → Ok com 2 Documentos (só metadado + BlobRef)
- [ ] Teste: envelope bruto gravado no BlobStore ANTES do retorno
- [ ] Teste: 137 → Vazio, cursor inalterado
- [ ] ...

## Verificação
- [ ] Suíte de contrato do SourceAdapter roda contra este adapter
- [ ] Nenhuma classe de domain referencia cStat (ArchUnit)
- [ ] Suíte inteira verde

## Parecer do revisor
(preenchido pelo revisor)
```

---

## 10. Relatório diário e PRs (o que você lê à noite)

### 10.1 PR por subetapa
- Branch `auto/E2-S05`, criada a partir do `main` ou da branch da dependência (empilhamento).
- Título: `E2-S05 · buscarLote com WireMock [AUTO]` (ou `[AUTO+ · revisão forte]`).
- Corpo (gerado pelo revisor):
  - o que muda, em 5 linhas;
  - **olhar com atenção**: arquivos e linhas, com o motivo;
  - invariantes tocadas e o teste que as cobre;
  - decisões do agente nesta subetapa;
  - o que **não** foi feito, e por quê.
- Rótulos: `aguardando-revisao`, `revisao-forte`, `empilhado-sobre:#N`.

### 10.2 `autonomo/revisao/AAAA-MM-DD.md` (gerado às 17:50)
A parte **determinística** sempre sai, porque é o script que monta (mesmo sem cota):
```markdown
# Relatório 2026-10-27 (seg)
Chamadas: 31 (J1: 17, J2: 14) · Falhas revertidas: 2 · Parou por: orçamento de diff
Suíte: 142 testes, 0 falhas · Diff de produção hoje: 763 linhas

## PRs para revisar (ordem sugerida)
1. #12 E2-S04 envelope distDFeInt [AUTO] — 210 linhas
2. #13 E2-S05 buscarLote [AUTO] — 553 linhas (empilhado sobre #12)

## Mudou de status
- E2-S03 [r]→[x] (você mergeou ontem)
- E2-S08 [ ]→[!] bloqueado: ver PARA-VOCE #4

## PARA-VOCE (novos)
- #4 [DECISAO] .pfx legado: BouncyCastle como dependência do adapter? (recomendação: sim)

## Decisões do agente hoje (DECISOES-AGENTE.md)
- DA-07 Nome do pacote do decodificador: ...soap.zip
```
A parte **narrativa** (resumo e "olhar com atenção") é escrita pelo revisor, se ainda houver cota. Se não houver, fica só a parte do script.

---

## 11. Script de janela (rascunho — testar manualmente antes de agendar)

`autonomo/bin/janela.sh`
```bash
#!/usr/bin/env bash
set -uo pipefail
cd /home/dev/fiscal-ingest
export CLAUDE_CONFIG_DIR=/home/dev/.claude-pessoal
source autonomo/bin/config.sh          # MAX_CHAMADAS_JANELA, ORCAMENTO_DIFF_DIA, FILA_REVISAO_MAX...
LOG="autonomo/logs/$(date +%F_%H%M).log"; exec >>"$LOG" 2>&1

dow=$(date +%u); [[ $dow -ge 6 || -f autonomo/PARAR ]] && exit 0
FIM=$(( $(date +%s) + 5*3600 - PARAR_ANTES_DO_FIM*60 ))

autonomo/bin/sincronizar.sh             # fetch, lê o status dos PRs (gh), marca [x]/[c], rebase dos empilhados

for ((i=1; i<=MAX_CHAMADAS_JANELA; i++)); do
  [[ $(date +%s) -ge $FIM || -f autonomo/PARAR ]] && break
  read -r ACAO PAPEL MODELO SUB < <(autonomo/bin/proxima-acao.sh) || break   # seção 7.1, determinístico
  [[ "$ACAO" == "SAIR" ]] && break
  antes=$(git rev-parse HEAD)
  saida=$(FI_PAPEL=$PAPEL FI_SUB=$SUB timeout "${TIMEOUT_CHAMADA}m" claude -p \
          "$(autonomo/bin/prompt.sh "$ACAO" "$SUB")" \
          --append-system-prompt "$(cat autonomo/papeis/$PAPEL.md)" \
          --model "$MODELO" --max-turns 60 --output-format json \
          --dangerously-skip-permissions)
  rc=$?
  if autonomo/bin/e-limite-de-uso.sh "$rc" "$saida"; then echo "limite de uso"; break; fi   # [VERIFICAR]
  autonomo/bin/verificar.sh "$antes" "$ACAO" "$SUB" || echo "chamada revertida"            # seção 7.2
  autonomo/bin/push-branch.sh "$SUB"   # push só de auto/*; abre/atualiza o PR quando a ação for REVISAR
done
```
`autonomo/bin/fechamento.sh` (17:50): sincroniza, gera o relatório determinístico, chama o revisor para a parte
narrativa (se houver cota), faz commit e push na branch `auto/relatorios`.

**A verificar na implantação:**
- **[VERIFICAR]** a mensagem e o código de saída do `claude -p` quando o limite é atingido (para o `e-limite-de-uso.sh`).
- **[VERIFICAR]** se os hooks disparam com `--dangerously-skip-permissions` (teste: peça para editar `docs/PROJETO.md`).
- **[VERIFICAR]** os nomes das flags (`--append-system-prompt`, `--max-turns`, `--output-format`) na sua versão.
- **[VERIFICAR]** se dá para ler o % de uso para parar antes do limite (opcional).

---

## 12. Agendador de Tarefas do Windows

Três tarefas, **segunda a sexta**:
| Tarefa | Hora | Ação |
|---|---|---|
| `fiscal-janela-1` | 08:00 | `wsl.exe -d fiscal-dev -u dev -- bash -lc "/home/dev/fiscal-ingest/autonomo/bin/janela.sh"` |
| `fiscal-janela-2` | 13:00 | idem |
| `fiscal-fechamento` | 17:50 | `... autonomo/bin/fechamento.sh` |

Configurações:
- "Executar estando o usuário conectado ou não";
- "Ativar o computador para executar esta tarefa";
- "Parar a tarefa se executar por mais de 5 horas";
- "Se a tarefa já estiver em execução: não iniciar nova instância".
No Windows: plano de energia sem suspensão durante o dia (ou temporizadores de ativação ligados) e Docker Desktop iniciando com o sistema.

**Agente Go (Fase 7):** repositório `fiscal-agent` com o mesmo processo e o próprio `autonomo/`, comando de teste `go test ./...`.
Ele só entra quando a Etapa 5 estiver mergeada. Até lá, o script só olha o `fiscal-ingest`.

---

## 13. Implantação (cabe nas suas noites de 1h–1h20)

| Noite | O que fazemos (comigo, conversando) | Resultado |
|---|---|---|
| 1 | E1-S01: `git init`, GitHub privado, proteção do `main`, wrapper, `./gradlew test` verde | build confiável |
| 2 | E1-S02 + E1-S03: docs corrigidos, renomear o `CLAUDE.md` v2 → `docs/PROJETO.md`, responder D1–D5, revisar o ROADMAP | fonte de verdade estável |
| 3 | WSL `fiscal-dev` + isolamento + Claude login + `gh` com token fine-grained | ambiente seguro |
| 4 | Criar `CLAUDE.md` curto, `autonomo/` (papéis, hooks, scripts), testar os 4 [VERIFICAR] | processo pronto |
| 5 | Rodar `janela.sh` **manualmente** com `MAX_CHAMADAS_JANELA=3`; revisar o primeiro PR | confiança no ciclo |
| Semana 1 | Agendar **só a janela das 08:00** + fechamento | calibrar orçamento e cota |
| Semana 2+ | Ligar a janela das 13:00 | ritmo cheio |

**Ajuste depois da semana 1**, olhando os relatórios:
- Sobrou tempo na sua revisão? Aumente o `ORCAMENTO_DIFF_DIA`.
- Você revisou correndo? Diminua.
- A cota semanal acabou na quinta? Diminua o `MAX_CHAMADAS_JANELA`.

---

## 14. Quando desligar ou intervir (sinais)

- Mais de 2 PRs seguidos com "Request changes" pelo **mesmo** tipo de erro → o problema está no plano ou no papel, não no código. Ajuste `papeis/*.md` ou o roadmap.
- `DECISOES-AGENTE.md` crescendo rápido → falta decisão sua nos docs. Transforme essas escolhas em ADR.
- Muitos `[!]` na mesma área → aquela área precisa de uma sessão conversada com você.
- Qualquer `656`, segredo em log ou edição em `docs/` que tenha passado pelas travas → **crie `autonomo/PARAR`** e revisamos juntos.

---

*Este processo também é código: mude os papéis, os limites e o roadmap como mudaria qualquer outro arquivo, por PR revisado por você.*
