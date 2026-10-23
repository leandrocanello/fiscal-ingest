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
- **Fonte:** (secundária) Senior, "NT 2014.002 - Atualização das Regras de Uso Indevido do Web Service NFeDistribuicaoDFe" (10/03/2022) — https://documentacao.senior.com.br/exigenciaslegais/noticias/federal/2022/2022-03-10-nt-2014-002-atualizacao-das-regras-de-uso-indevido-do-web-service-nfedistribuicaodfe (acessado 2026-10-23); (secundária) NetCPA, mesma NT (07/03/2022, atualizado 23/10/2023) — https://netcpa.com.br/colunas/nf-e-04032022-atualizacao-das-regras-de-uso-indevido-do-web-service-nfedistribuicaodfe-nt-2014002/13214 (acessado 2026-10-23). **Fonte primária (texto da NT no portal nfe.fazenda.gov.br): NÃO LOCALIZADO** — a página do portal deu "Too many redirects" no WebFetch.
- **Trecho:** "Se não existir mais documentos a serem retornados (cStat=137) o usuário deve aguardar uma hora para realizar nova consulta." · "A realização de novas consultas em 1h, após receber a mensagem cStat137, gerará o uso indevido, retornando cStat=656." Para `ultNSU == maxNSU`: o texto manda aguardar 1h e diz que consultas fora da sequência de NSU serão bloqueadas.
- **Leitura proposta:** Confirma o ~1h após `137` (CLAUDE.md §3.2). Sobre `ultNSU < maxNSU`: as fontes **não trazem trecho explícito**; só tratam a igualdade. A leitura (continuar de imediato a partir do `ultNSU` retornado) é inferência, **NÃO confirmada**. Duração do bloqueio por `656`: 1h segundo a resposta de busca (secundária), sem trecho literal da NT.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; `ultNSU < maxNSU` NÃO LOCALIZADO)
- **Afeta:** CStatMapperTest; E2-S02; E4 (scheduler, `PoliticaRateLimit`)

### Item 2 — Limites de `consNSU`/`consChNFe`; duração do bloqueio por `656`
- **Pergunta:** Limites de `consNSU`/`consChNFe`; duração do bloqueio por `656`.
- **Fonte:** (secundária) Senior, "NT 2014.002 - Atualização das Regras de Uso Indevido do Web Service NFeDistribuicaoDFe" (10/03/2022) — https://documentacao.senior.com.br/exigenciaslegais/noticias/federal/2022/2022-03-10-nt-2014-002-atualizacao-das-regras-de-uso-indevido-do-web-service-nfedistribuicaodfe (acessado 2026-10-23); (secundária) NetCPA, mesma NT (07/03/2022, atualizado 23/10/2023) — https://netcpa.com.br/colunas/nf-e-04032022-atualizacao-das-regras-de-uso-indevido-do-web-service-nfedistribuicaodfe-nt-2014002/13214 (acessado 2026-10-23). **Fonte primária (texto da NT no portal nfe.fazenda.gov.br): NÃO LOCALIZADO** — a página do portal deu "Too many redirects" no WebFetch.
- **Trecho:** "Será permitido um número limitado de consultas por chave de acesso ou NSU em um período de 1hora." · "656 - Consumo indevido - ultrapassou o limite de 20 consultas por hora." · "período retroativo máximo de 90 (noventa) dias" (NetCPA).
- **Leitura proposta:** Confirma ~20 consultas/hora para `consNSU`/`consChNFe` (CLAUDE.md §3.2), com o mesmo `656`. Não se sabe se o limite é por CNPJ, por certificado ou por IP, nem se a contagem é conjunta entre `consNSU` e `consChNFe`: **NÃO LOCALIZADO**. Duração do bloqueio do `656` por excesso de pontuais: NÃO LOCALIZADO.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias)
- **Afeta:** E2-S02; E2-S0x (`buscarUm`); E4

### Item 3 — Lista vigente de `cStat` (107/108/109/137/138/589/632/656) e mapeamento para `Desfecho`
- **Pergunta:** Lista vigente de `cStat` (107/108/109/137/138/589/632/656) e mapeamento para `Desfecho`.
- **Fonte:** (secundária) NCM Buscador, rejeição 589 (cita "MOC NF-e/NFC-e, tabela de códigos e regras de validação, consolidação de 09/10/2026") — https://buscadorncm.com.br/nfe/rejeicoes/589 (acessado 2026-10-23); NT 2014.002 via Senior/NetCPA (links no item 1, acessados 2026-10-23). **Fonte primária (MOC/Anexo de rejeições no portal): NÃO LOCALIZADO.** Buscas por `107`, `108`, `109` e `632` no contexto da distribuição não devolveram fonte aberta e confiável.
- **Trecho:** `589`: "Número do NSU informado superior ao maior NSU do Ambiente Nacional" (rejeição). `137`/`138`/`656`: ver itens 1–2 (a NT não cita `138`). `107`, `108`, `109`, `632`: **NÃO LOCALIZADO** nesta sessão (os textos usuais — 107 "Serviço em Operação", 108 "Paralisado Momentaneamente", 109 "Paralisado sem Previsão" — vêm da memória e **não** foram verificados; não usar).
- **Leitura proposta:** Proposta (para a E2-S02 decidir, nada foi alterado): `589` → erro de consistência, nunca retentar (coerente com CLAUDE.md §3.2 e ARCHITECTURE l.178; auditoria A3). Hoje `CStatMapper` mapeia `108` → `Ok` (A1; sem fonte localizada que o sustente) e `632` → `LimiteAtingido` (A2; sem fonte localizada) — `CStatMapperTest` cobre esses casos e depende de conferir os itens 1–3. `656` → `LimiteAtingido` consistente com a NT. ⚠ Nenhuma divergência confirmada com o CLAUDE.md nesta etapa; `632` segue em aberto.
- **Status:** FONTE LOCALIZADA (parcial: só `589`/`656`/`137`; `107/108/109/632` NÃO LOCALIZADO)
- **Afeta:** CStatMapperTest; E2-S02 (DECISAO); E2-S03; achados A1/A2/A3

### Item 4 — Atores atendidos pela distribuição; emitente recebe eventos das próprias notas?
- **Pergunta:** Atores atendidos pela distribuição; emitente recebe eventos das próprias notas?
- **Fonte:** (secundária) Senior, resumo da NT 2014.002 (10/03/2022) — https://documentacao.senior.com.br/exigenciaslegais/noticias/federal/2022/2022-03-10-nt-2014-002-atualizacao-das-regras-de-uso-indevido-do-web-service-nfedistribuicaodfe (acessado 2026-10-23); (secundária) resumos de busca sobre NT 2014.002 v1.13–1.15 (Tecnospeed, Focus NFe, Portal SPED; acessados via WebSearch em 2026-10-23, páginas não abertas individualmente). PDFs da NT (v1.13–1.15 em inventti.com.br; v1.40, jul/2026, em reformatributaria.com) são **cópias de terceiros**; o de v1.13–1.15 foi baixado, mas o texto está comprimido e **não pôde ser extraído** (sem `pdftotext`/`pypdf` no ambiente); o v1.40 não foi aberto. **Fonte primária (NT no portal nfe.fazenda.gov.br): NÃO LOCALIZADO.**
- **Trecho:** Senior: "do mesmo ator (emitente ou destinatário ou transportador na NF-e ou indicado no campo autxml)". Resumo de busca da NT v1.13 (parafraseado, não literal): eventos gerados pelo Fisco são distribuídos ao emitente independentemente da manifestação do destinatário, inclusive quando emitente e destinatário são o mesmo (vigência 09/12/2021).
- **Leitura proposta:** Atores: emitente, destinatário, transportador e terceiros no `autXML` (consistente com CLAUDE.md §3.1). Emitente **recebe eventos** das próprias notas (ao menos os gerados pelo Fisco) — confirma a hipótese do **[VALIDAR]** só em parte: eventos de manifestação do destinatário ao emitente **NÃO LOCALIZADO**. Sobre o XML da nota emitida não voltar ao emitente: só há um relato de fórum (ACBr, secundária) de que o emitente não gera NSU para a própria nota; **não confirmado** na NT. Disponibilidade: "até 3 meses" após recepção no AN (resumo de busca/blog; confirmar na NT — ver item 10). ⚠ Nenhuma divergência confirmada com o CLAUDE.md §3.1.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; NT primária não lida)
- **Afeta:** Roteamento pelo conteúdo (§7.3); canais A/B

### Item 5 — `autXML`: limite de ocorrências, recebimento pela distribuição, CT-e
- **Pergunta:** `autXML`: limite de ocorrências, recebimento pela distribuição, CT-e.
- **Fonte:** (secundária) Portal SPED Brasil, fórum (resultado de busca, https://portalspedbrasil.com.br/?p=16028, acessado 2026-10-23, via WebSearch; página não aberta); (secundária) UpSeller, "Como autorizar CNPJ/CPF de terceiros para baixar nota fiscal (AutXML)" — https://upseller.com/pt/help-doc-article-1219 (acessado 2026-10-23, via WebSearch; página não aberta); Senior (link no item 4). **Fonte primária (MOC/leiaute, grupo GA01; NT 2020.007): NÃO LOCALIZADO.**
- **Trecho:** Fórum: emitente pode informar "até 10" CNPJ/CPF; UpSeller: "máximo de 9" autorizações — **fontes discordam** (paráfrase de resumo de busca). Rejeições citadas: 423 (CNPJ/CPF já autorizado) e 421 (CNPJ do próprio emitente no `autXML`) — não verificadas. Resumo da NT 2014.002: terceiro só recebe a NF-e se seu CNPJ/CPF constar no `autXML` (nota de rodapé de tabela da NT).
- **Leitura proposta:** Terceiro no `autXML` recebe pela distribuição (coerente com §3.4/ADR-019), em consulta com o próprio certificado. Limite: **10 vs 9 — NÃO CONFIRMADO** (CLAUDE.md diz ~10). CT-e: **NÃO LOCALIZADO**. Ocorrências do `autXML` além do limite e efeito sobre o escritório (se o ERP já usa vagas) ficam para o autor/smoke. Sem divergência confirmada.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; limite 9×10 em conflito; CT-e NÃO LOCALIZADO)
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
