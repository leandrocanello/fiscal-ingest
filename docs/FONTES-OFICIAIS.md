# FONTES-OFICIAIS — registro de fontes dos [VALIDAR]

> **RASCUNHO** (esqueleto criado em 2026-10-23, subetapa E2-S01). Nenhum item foi pesquisado ainda.

## Propósito
Registrar, para cada item da CLAUDE.md §21, **onde** está a resposta oficial (documento, versão, seção/página,
link, data de acesso) e o que o texto diz. Base das decisões E2-S02 (`cStat`), E2-S11 (assinatura) e E2-S12 (smoke).
Se a norma mudar, mude **o teste primeiro**.

## Regras
- Nunca inventar link, número de NT, versão ou seção. Não achou → `NÃO LOCALIZADO` + o que foi buscado.
- Link só entra se foi aberto na sessão de pesquisa; anotar a **data de acesso** de cada link.
- Preferir fonte primária (portal da NF-e, NT, MOC, legislação). Fonte secundária só como pista, rotulada `(secundária)`.
- Citação curta (até ~2 linhas) + leitura proposta. Divergência com a CLAUDE.md → `⚠ DIVERGE DO CLAUDE.md §x`;
  a CLAUDE.md não é editada aqui.
- **Proibido** acessar URL de webservice (`*.asmx`, `?wsdl`, `/ws/`): só se anota como texto (item 9).

## Legenda de status
- `PENDENTE` — nada achado.
- `FONTE LOCALIZADA` — link aberto e trecho citado.
- `CONFERIDO PELO AUTOR` — **somente o autor** marca.

## Índice
| Nº | Tema | Status | Afeta |
|---|---|---|---|
| 1 | Bloqueio após `137`; comportamento com `ultNSU < maxNSU` | PENDENTE | CStatMapperTest; E2-S02; E4 (scheduler, `PoliticaRateLimit`) |
| 2 | Limites de `consNSU`/`consChNFe`; duração do bloqueio por `656` | PENDENTE | E2-S02; E2-S0x (`buscarUm`); E4 |
| 3 | Lista vigente de `cStat` (107/108/109/137/138/589/632/656) e mapeamento para `Desfecho` | PENDENTE | CStatMapperTest; E2-S02 (DECISAO); E2-S03; achados A1/A2/A3 |
| 4 | Atores atendidos pela distribuição; emitente recebe eventos das próprias notas? | PENDENTE | Roteamento pelo conteúdo (§7.3); canais A/B |
| 5 | `autXML`: limite de ocorrências, recebimento pela distribuição, CT-e | PENDENTE | ADR-019; E2-S12 (smoke); canal B |
| 6 | Manifestação: códigos, prazos, efeito no `procNFe`, quem assina | PENDENTE | E2-S09/E2-S11 (`manifestar`); ADR-004 |
| 7 | Assinatura: SHA-1 × SHA-256, C14N, formato do `Id` do evento | PENDENTE | E2-S11 (XML-DSig); validador de assinatura da porta push |
| 8 | Qual certificado consulta qual CNPJ (raiz, filiais, procurador) | PENDENTE | E2D (carregador `.pfx`); cofre (§7); `estado_coleta` |
| 9 | URLs e versões de WSDL/XSD (produção e homologação); política de versão das NTs | PENDENTE | Adapter 2B–2E; E2-S12; vendorização de XSD |
| 10 | Janela real de retenção do AN | PENDENTE | Backfill; prioridade da Fase 2→3 |
| 11 | Limites por IP ou volume global | PENDENTE | Backoff global por endpoint; dimensionamento (§6.2); Fase 10 |
| 12 | Serviço de consulta de situação/protocolo por chave (limites) | PENDENTE | `CONFIRMADO_SEFAZ` (§4); Fase 5 |
| 13 | NFCom/NF3e; cronograma IBS/CBS na NF-e; adesão ao ADN da NFS-e | PENDENTE | Parser versionado; Fase 11+ |
| 14 | Prazo legal de guarda dos XML (validar com jurídico) | PENDENTE | Política de retenção/expurgo (§14) |

## Rastreio: [VALIDAR] de origem → item
| Origem | Item(ns) |
|---|---|
| CLAUDE.md §3 (l.174) — regra geral dos [VALIDAR] | todos |
| §3.1 (l.184) — retenção ~90 dias com `ultNSU = 0` | 10 |
| §3.1 (l.186) — lista vigente de atores; emitente recebe eventos | 4 |
| §3.1 (l.188) — XML emitido não volta para a empresa | 4, 5 |
| §3.2 (l.194) — duração do bloqueio após `137` e `ultNSU < maxNSU` | 1 |
| §3.2 (l.198) — limites ~20/h de `consNSU`/`consChNFe` | 2 |
| §3.2 (l.199) — mapeamento de cada `cStat` (incl. `632`, `589`, `656`) | 3 (e 2 para o `656`) |
| §3.3 (l.212) — prazos dos eventos de manifestação | 6 |
| §3.3 (l.214) — certificado do escritório não manifesta pelo cliente | 6, 8 |
| §3.4 (l.223) — limite de ocorrências do `autXML` | 5 |
| §3.5 (l.232) — qual certificado consulta qual CNPJ | 8 |
| §3.6 (l.241) — NFCom/NF3e: serviço de distribuição | 13 |
| §4 (l.260) — serviço e limites da consulta de protocolo (`CONFIRMADO_SEFAZ`) | 12 |
| §6.2 (l.310) — limite do AN por IP ou volume global | 11 |
| §7.2 (l.392) — filiais de mesma raiz | 8 |
| §14 (l.706) — guarda fiscal de 5 anos, validar com jurídico | 14 |
| §16 (l.752) — 2E: algoritmo de assinatura do `manifestar` | 7 |
| §21 — itens 1–14 | 1–14 (idem) |
| ARCHITECTURE.md l.41 — loop continua enquanto `ultNSU < maxNSU` | 1 |
| ARCHITECTURE.md l.43 — consultas pontuais ~20/h | 2 |
| ARCHITECTURE.md l.178 — `108` ≠ serviço em operação; `589` erro de consistência | 3 |
| AUDITORIA A1 — `108` mapeado para `Ok` | 3 |
| AUDITORIA A2 — `632` → `LimiteAtingido` | 3 |
| AUDITORIA A3 — falta `589` | 3 |
| AUDITORIA A5 — `Vazio` no `consNSU` vira `DESCARTADO`; o que o AN responde para NSU inexistente | 1, 2, 3 |

## Itens

### Item 1 — Bloqueio após `137`; comportamento com `ultNSU < maxNSU`
- **Pergunta:** Bloqueio após `137`; comportamento com `ultNSU < maxNSU`.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** CStatMapperTest; E2-S02; E4 (scheduler, `PoliticaRateLimit`)

### Item 2 — Limites de `consNSU`/`consChNFe`; duração do bloqueio por `656`
- **Pergunta:** Limites de `consNSU`/`consChNFe`; duração do bloqueio por `656`.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** E2-S02; E2-S0x (`buscarUm`); E4

### Item 3 — Lista vigente de `cStat` (107/108/109/137/138/589/632/656) e mapeamento para `Desfecho`
- **Pergunta:** Lista vigente de `cStat` (107/108/109/137/138/589/632/656) e mapeamento para `Desfecho`.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** CStatMapperTest; E2-S02 (DECISAO); E2-S03; achados A1/A2/A3

### Item 4 — Atores atendidos pela distribuição; emitente recebe eventos das próprias notas?
- **Pergunta:** Atores atendidos pela distribuição; emitente recebe eventos das próprias notas?.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** Roteamento pelo conteúdo (§7.3); canais A/B

### Item 5 — `autXML`: limite de ocorrências, recebimento pela distribuição, CT-e
- **Pergunta:** `autXML`: limite de ocorrências, recebimento pela distribuição, CT-e.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** ADR-019; E2-S12 (smoke); canal B

### Item 6 — Manifestação: códigos, prazos, efeito no `procNFe`, quem assina
- **Pergunta:** Manifestação: códigos, prazos, efeito no `procNFe`, quem assina.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** E2-S09/E2-S11 (`manifestar`); ADR-004

### Item 7 — Assinatura: SHA-1 × SHA-256, C14N, formato do `Id` do evento
- **Pergunta:** Assinatura: SHA-1 × SHA-256, C14N, formato do `Id` do evento.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** E2-S11 (XML-DSig); validador de assinatura da porta push

### Item 8 — Qual certificado consulta qual CNPJ (raiz, filiais, procurador)
- **Pergunta:** Qual certificado consulta qual CNPJ (raiz, filiais, procurador).
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** E2D (carregador `.pfx`); cofre (§7); `estado_coleta`

### Item 9 — URLs e versões de WSDL/XSD (produção e homologação); política de versão das NTs
- **Pergunta:** URLs e versões de WSDL/XSD (produção e homologação); política de versão das NTs.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** Adapter 2B–2E; E2-S12; vendorização de XSD

### Item 10 — Janela real de retenção do AN
- **Pergunta:** Janela real de retenção do AN.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** Backfill; prioridade da Fase 2→3

### Item 11 — Limites por IP ou volume global
- **Pergunta:** Limites por IP ou volume global.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** Backoff global por endpoint; dimensionamento (§6.2); Fase 10

### Item 12 — Serviço de consulta de situação/protocolo por chave (limites)
- **Pergunta:** Serviço de consulta de situação/protocolo por chave (limites).
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** `CONFIRMADO_SEFAZ` (§4); Fase 5

### Item 13 — NFCom/NF3e; cronograma IBS/CBS na NF-e; adesão ao ADN da NFS-e
- **Pergunta:** NFCom/NF3e; cronograma IBS/CBS na NF-e; adesão ao ADN da NFS-e.
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** Parser versionado; Fase 11+

### Item 14 — Prazo legal de guarda dos XML (validar com jurídico)
- **Pergunta:** Prazo legal de guarda dos XML (validar com jurídico).
- **Fonte:** _(PENDENTE)_
- **Trecho:** _(PENDENTE)_
- **Leitura proposta:** _(PENDENTE)_
- **Status:** PENDENTE
- **Afeta:** Política de retenção/expurgo (§14)
