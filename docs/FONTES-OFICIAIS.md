# FONTES-OFICIAIS — registro de fontes dos [VALIDAR]

> **RASCUNHO** (E2-S01, 2026-10-23). Todos os itens pesquisados; nenhum conferido pelo autor. Só há fontes secundárias: nenhuma NT/MOC primária foi lida.

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
| 1 | Bloqueio após `137`; comportamento com `ultNSU < maxNSU` | FONTE LOCALIZADA (parcial) | CStatMapperTest; E2-S02; E4 (scheduler, `PoliticaRateLimit`) |
| 2 | Limites de `consNSU`/`consChNFe`; duração do bloqueio por `656` | FONTE LOCALIZADA (parcial) | E2-S02; E2-S0x (`buscarUm`); E4 |
| 3 | Lista vigente de `cStat` (107/108/109/137/138/589/632/656) e mapeamento para `Desfecho` | FONTE LOCALIZADA (parcial) | CStatMapperTest; E2-S02 (DECISAO); E2-S03; achados A1/A2/A3 |
| 4 | Atores atendidos pela distribuição; emitente recebe eventos das próprias notas? | FONTE LOCALIZADA (parcial) | Roteamento pelo conteúdo (§7.3); canais A/B |
| 5 | `autXML`: limite de ocorrências, recebimento pela distribuição, CT-e | FONTE LOCALIZADA (parcial) | ADR-019; E2-S12 (smoke); canal B |
| 6 | Manifestação: códigos, prazos, efeito no `procNFe`, quem assina | FONTE LOCALIZADA (parcial) | E2-S09/E2-S11 (`manifestar`); ADR-004 |
| 7 | Assinatura: SHA-1 × SHA-256, C14N, formato do `Id` do evento | FONTE LOCALIZADA (parcial) | E2-S11 (XML-DSig); validador de assinatura da porta push |
| 8 | Qual certificado consulta qual CNPJ (raiz, filiais, procurador) | PENDENTE | E2D (carregador `.pfx`); cofre (§7); `estado_coleta` |
| 9 | URLs e versões de WSDL/XSD (produção e homologação); política de versão das NTs | PENDENTE | Adapter 2B–2E; E2-S12; vendorização de XSD |
| 10 | Janela real de retenção do AN | FONTE LOCALIZADA (parcial) | Backfill; prioridade da Fase 2→3 |
| 11 | Limites por IP ou volume global | PENDENTE | Backoff global por endpoint; dimensionamento (§6.2); Fase 10 |
| 12 | Serviço de consulta de situação/protocolo por chave (limites) | PENDENTE | `CONFIRMADO_SEFAZ` (§4); Fase 5 |
| 13 | NFCom/NF3e; cronograma IBS/CBS na NF-e; adesão ao ADN da NFS-e | FONTE LOCALIZADA (parcial) | Parser versionado; Fase 11+ |
| 14 | Prazo legal de guarda dos XML (validar com jurídico) | FONTE LOCALIZADA (parcial) ⚠ | Política de retenção/expurgo (§14) |

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
- **Fonte:** (secundária) Tecnospeed, "Nota Técnica 2020.001" — https://blog.tecnospeed.com.br/nota-tecnica-2020-001 (acessado 2026-10-23); (secundária) resumo de busca sobre NT 2020.001 v1.10 (notagateway.com.br; página deu 504, **não aberta**). A NT 2020.001 unificou as antigas NT 2012.002 e 2013.001 (resumo de busca, não verificado na NT). **Fonte primária (NT 2020.001 no portal nfe.fazenda.gov.br): NÃO LOCALIZADO.**
- **Trecho:** "reduzindo o limite de 180 dias para 90 dias, contados da data de autorização da NF-e" (manifestação conclusiva, v1.60). Ciência: 10 dias, só no texto alternativo de imagem (frágil). Quem registra: o destinatário (CNPJ/CPF).
- **Leitura proposta:** Prazos: conclusivas até 90 dias da autorização; Ciência 10 dias (**não confiável**; um resumo antigo citava 10/35/70/15 dias, regime anterior). Os códigos 210200/210210/210220/210240, o efeito da Ciência na liberação do `procNFe` e a regra de que só o certificado do destinatário assina: **NÃO LOCALIZADO** (continuam vindo da memória/CLAUDE.md §3.3). Resumo de busca: uma manifestação conclusiva pode ser retificada uma vez (NT 2020.001 v1.50), vale a última — não verificado. Sem divergência confirmada.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; códigos, efeito no `procNFe` e quem assina NÃO LOCALIZADO)
- **Afeta:** E2-S09/E2-S11 (`manifestar`); ADR-004

### Item 7 — Assinatura: SHA-1 × SHA-256, C14N, formato do `Id` do evento
- **Pergunta:** Assinatura: SHA-1 × SHA-256, C14N, formato do `Id` do evento.
- **Fonte:** (secundária) NCM Buscador, rejeição 298 (cita "MOC NF-e/NFC-e, consolidação de 09/10/2026", sem versão) — https://buscadorncm.com.br/nfe/rejeicoes/298 (acessado 2026-10-23); (secundária) resumo de busca de ato da SEF-SC (AtoDiat 26/056, Anexo I; PDF **não aberto**). **Fonte primária (MOC, anexo de assinatura digital): NÃO LOCALIZADO.**
- **Trecho:** Rejeição 298, regra F01: "Não assinado o atributo "Id" (falta "Reference URI" na assinatura)" · "Faltam os "Transform Algorithm" previstos na assinatura ("C14N" e "Enveloped")".
- **Leitura proposta:** A assinatura deve referenciar o `Id` e conter os transforms Enveloped + C14N (xml-c14n-20010315, segundo resumo de busca não verificado). SHA-1 × SHA-256: o resumo da SEF-SC cita RSA + SHA-256, mas **não confirmado** em fonte aberta; a página do MOC não menciona algoritmo. Formato do `Id` do evento (`ID`+tpEvento+chNFe+nSeqEvento): **NÃO LOCALIZADO** (só da memória). Decisão do algoritmo fica para E2-S11 após conferência do autor.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; SHA-1×SHA-256 e `Id` do evento NÃO LOCALIZADO)
- **Afeta:** E2-S11 (XML-DSig); validador de assinatura da porta push

### Item 8 — Qual certificado consulta qual CNPJ (raiz, filiais, procurador)
- **Pergunta:** Qual certificado consulta qual CNPJ (raiz, filiais, procurador).
- **Fonte:** (secundária) resumo de busca (WebSearch, 2026-10-23) sobre NT 2014.002 v1.40 (jul/2026) — PDF de cópia de terceiros https://www.reformatributaria.com/wp-content/uploads/2026/07/NT2014.002_v1.40-WsNFeDistribuicaoDFe-CNPJ-alfa.pdf (acessado 2026-10-23; **texto não extraível** pelo WebFetch, PDF comprimido); (secundária) fórum ACBr https://www.projetoacbr.com.br/forum/topic/40491-pode-usar-o-certificado-digital-da-matriz-apra-assinal-nfenfce-e-vice-versa/ (resultado de busca, página não aberta). **Fonte primária (seção "Validação do Certificado de Transmissão" da NT 2014.002 no portal): NÃO LOCALIZADO.**
- **Trecho:** nenhum trecho literal obtido. Resumo de busca (parafraseado): a distribuição entrega ao ator cujo CNPJ/CPF consta em emit/dest/transporta/autXML; um relato de fórum diz que certificado de filial foi aceito para documento da matriz em homologação (anedótico).
- **Leitura proposta:** Regra raiz/filial/procurador **NÃO LOCALIZADA** em fonte aberta. Mantém-se a premissa segura do CLAUDE.md §3.5/§7.2 (cada certificado consulta o CNPJ do próprio titular). Conferir a seção de validação do certificado na NT 2014.002 v1.40 lendo o PDF manualmente. Sem divergência confirmada.
- **Status:** PENDENTE (nada primário; só pista secundária)
- **Afeta:** E2D (carregador `.pfx`); cofre (§7); `estado_coleta`

### Item 9 — URLs e versões de WSDL/XSD (produção e homologação); política de versão das NTs
- **Pergunta:** URLs e versões de WSDL/XSD (produção e homologação); política de versão das NTs.
- **Fonte:** (secundária) fórum ACBr, "Web Service de Distribuição de Documentos Fiscais Eletrônicos" — https://www.projetoacbr.com.br/forum/topic/15776-web-service-de-distribui%C3%A7%C3%A3o-de-documentos-fiscais-eletr%C3%B4nicos/ (acessado 2026-10-23); NT 2014.002 v1.40 (PDF de terceiros, ver item 8; vigência 08/07/2026 em teste e produção, segundo resumo de busca). **Portal da NF-e (página de web services / download de schemas `PL_*`): NÃO LOCALIZADO** — buscas por "PL_009" e URLs nacionais só devolveram páginas estaduais e fóruns antigos.
- **Trecho:** URL de homologação citada no fórum (**texto copiado, NÃO ACESSAR**): `https://hom.nfe.fazenda.gov.br/NFeDistribuicaoDFe/NFeDistribuicaoDFe.asmx`. URL de produção: **NÃO LOCALIZADA** (o fórum só diz que a de produção sairia depois, 06/10/2014, sem endereço). Texto de 2014; pode estar desatualizada.
- **Leitura proposta:** Só há uma URL de homologação, de fonte secundária de 2014: confirmar no portal antes de usar. Pacote de schemas vigente (`PL_*`), versão do WSDL e política de versionamento das NTs: **NÃO LOCALIZADO**. Neste doc nenhuma URL de webservice foi aberta (invariante 14).
- **Status:** PENDENTE (URL de homologação só como pista secundária; produção, pacote `PL_*` e política de versão NÃO LOCALIZADO)
- **Afeta:** Adapter 2B–2E; E2-S12; vendorização de XSD

### Item 10 — Janela real de retenção do AN
- **Pergunta:** Janela real de retenção do AN.
- **Fonte:** (secundária) resultado de busca (WebSearch, 2026-10-23; páginas não abertas): Portal SPED https://portalspedbrasil.com.br/?p=17633 · nfe.io https://nfe.io/docs/distribuicao-processamento-e-periodicidade/index.md · TekSystem https://teksystem.com.br/tekwiki/images/c/c7/Distribui%C3%A7%C3%A3o_de_DFe.pdf · NT 2014.002 v1.02c (cópia Senior; trechos recuperados não mostram o texto sobre retenção) https://documentacao.senior.com.br/exigenciaslegais/resources/documentos%20para%20download/nt2014_002_v1.02c_wsnfedistribuicaodfe.pdf. Links do item 2 (NetCPA, "período retroativo máximo de 90 dias"). **Fonte primária (NT no portal): NÃO LOCALIZADO.**
- **Trecho:** Paráfrase de resumo de busca: com `ultNSU` zero/antigo o AN devolve o que foi recebido nos "últimos 3 meses"; consultas pontuais (`consChNFe`/`consNSU`) com "período retroativo máximo de 90 (noventa) dias".
- **Leitura proposta:** ~3 meses (≈90 dias) é coerente com o CLAUDE.md §3.1 ("~90 dias"). Fontes divergem levemente (3 meses × 90 dias) e nenhuma é primária; se conta da recepção no AN ou da emissão: NÃO LOCALIZADO. Sem divergência confirmada.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias, resumos de busca)
- **Afeta:** Backfill; prioridade da Fase 2→3

### Item 11 — Limites por IP ou volume global
- **Pergunta:** Limites por IP ou volume global.
- **Fonte:** (secundária) resultado de busca (WebSearch, 2026-10-23): NT 2014.002 via Senior (link no item 1), Portal SPED https://portalspedbrasil.com.br/?p=17443, Oobj https://oobj.com.br/nota-tecnica/alteracoes-na-classificacao-de-uso-indevido/ (páginas não abertas). **Fonte primária: NÃO LOCALIZADO.**
- **Trecho:** Nenhum trecho sobre IP ou volume global. As regras resumidas são por CNPJ: "Se consultar fora da sequência, será bloqueado"; várias aplicações no mesmo CNPJ "devem seguir a mesma sequência ascendente de NSU". Resumo: retomar a consulta antes de 1h zera a contagem.
- **Leitura proposta:** Nada indica limite por IP; as fontes só descrevem controle por CNPJ/NSU. Ausência de menção **não** prova ausência de limite: mantém-se a cautela do CLAUDE.md §6.2 (backoff global por endpoint). Item em aberto para o smoke (E2-S12). Sem divergência confirmada.
- **Status:** PENDENTE (sem fonte que trate de IP/volume global)
- **Afeta:** Backoff global por endpoint; dimensionamento (§6.2); Fase 10

### Item 12 — Serviço de consulta de situação/protocolo por chave (limites)
- **Pergunta:** Serviço de consulta de situação/protocolo por chave (limites).
- **Fonte:** (secundária) resultado de busca (WebSearch, 2026-10-23): Oobj https://oobj.com.br/?p=10469, Focus NFe https://focusnfe.com.br/blog/rejeicao-656/ (páginas não abertas). **Fonte primária (Manual de Orientação / NT do `NFeConsultaProtocolo`, NT 2013.007 citada na busca): NÃO LOCALIZADO.**
- **Trecho:** Resumo de busca, não confirmado: penalidades por consumo indevido citam NF-e consultada "mais de 10 vezes em 1 hora" (NT 2018.002, segundo fornecedor). Nada específico sobre limites do serviço por chave para terceiros.
- **Leitura proposta:** O serviço existe pelo nome (`NFeConsultaProtocolo`, a confirmar no portal), mas limites, quem pode consultar e a situação retornada: NÃO LOCALIZADO. O nível `CONFIRMADO_SEFAZ` (§4) segue dependendo desta conferência. Sem divergência confirmada.
- **Status:** PENDENTE (só pista secundária sobre ~10 consultas/h por NF-e, não verificada)
- **Afeta:** `CONFIRMADO_SEFAZ` (§4); Fase 5

### Item 13 — NFCom/NF3e; cronograma IBS/CBS na NF-e; adesão ao ADN da NFS-e
- **Pergunta:** NFCom/NF3e; cronograma IBS/CBS na NF-e; adesão ao ADN da NFS-e.
- **Fonte:** (secundária) resultados de busca (WebSearch, 2026-10-23; páginas não abertas):
  - NFCom/NF3e: Teknisa https://universidade.teknisa.com/wp-content/uploads/2026/03/ERP_REVIEW_022026_REVERP0225V1.pdf · SEF/SC https://www.sef.sc.gov.br/api-portal/Documento/Ver/6987 · Inventti (NFCom, Ajuste SINIEF 7/2022) https://inventti.com.br/?p=17429.
  - IBS/CBS: Systax https://www.systax.com.br/?p=42952 · Contábeis https://www.contabeis.com.br/artigos/76943/nf-e-novas-regras-e-cronogramas-para-ibs-e-cbs-em-2026-2027/ · Senior (NT 2025.002 v1.10) https://documentacao.senior.com.br/exigenciaslegais/noticias/federal/2025/2025-06-10-nota-tecnica-2025-002-v-1-10-reforma-tributaria-adequacao-nfe-nfce.
  - ADN: CRC-CE https://www.crc-ce.org.br/crcnovo/files/12052026.pdf · Convergência Digital https://convergenciadigital.com.br/?p=44440 · Jornal Contábil https://jornalcontabil.com.br/noticia/nfs-e-nacional-o-que-muda-na-emissao-de-notas-e-prazos/amp/.
  **Fontes primárias (portais NFCom/NF3e, NF-e, NFS-e nacional): NÃO LOCALIZADO.**
- **Trecho:** Paráfrases de resumo de busca: (NFCom/NF3e) Teknisa diz que a SEFAZ não disponibiliza canal de consulta para os modelos 62 e 66, ao contrário do 55; (IBS/CBS) NT 2025.002 v1.40 (20/05/2026) com datas críticas adiadas para 03/08/2026; Simples/MEI em produção em 04/01/2027; versões antigas previam 05/01/2026 — fontes divergem em versão e datas; (ADN) padrão nacional obrigatório para municípios desde 01/01/2026 (LC 214/2025), com opção de emissor próprio integrado; DF manteve emissor próprio.
- **Leitura proposta:** NFCom/NF3e: indício (fraco) de que **não há distribuição**, o que reforça push (CLAUDE.md §3.6) — não confirmado. IBS/CBS: parser versionado continua necessário; datas a conferir na NT vigente. ADN: obrigatório em 2026, mas cobertura efetiva por município não medida. Sem divergência confirmada.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; NFCom/NF3e e datas de IBS/CBS não verificadas)
- **Afeta:** Parser versionado; Fase 11+

### Item 14 — Prazo legal de guarda dos XML (validar com jurídico)
- **Pergunta:** Prazo legal de guarda dos XML (validar com jurídico).
- **Fonte:** (secundária) resultados de busca (WebSearch, 2026-10-23; páginas não abertas): Econet https://blog.econeteditora.com.br/padronizado-prazo-de-guarda/ · NetCPA https://netcpa.com.br/colunas/novo-prazo-para-guarda-de-documentos-eletronicos/25241 · Contábeis https://www.contabeis.com.br/artigos/70492/novo-ajuste-sinief-exige-guarda-de-documentos-fiscais-por-11-anos/ · Contábeis (fórum, Ajuste SINIEF 07/05) https://www.contabeis.com.br/forum/tributos-estaduais-municipais/65855/armazenamento-nf-e/. **Fonte primária (texto do Ajuste SINIEF 07/05, Ajuste SINIEF 2/2025, CTN): NÃO LOCALIZADO.**
- **Trecho:** Paráfrase: Ajuste SINIEF 07/05 — emitente e destinatário mantêm a NF-e em arquivo digital "pelo prazo estabelecido na legislação tributária"; CTN arts. 173/174: 5 anos; Ajuste SINIEF 2/2025 (segundo as fontes): 132 meses (11 anos) contados da autorização — fontes discordam se obriga o contribuinte ou só os procedimentos do Fisco.
- **Leitura proposta:** ⚠ DIVERGE DO CLAUDE.md §14 (guarda fiscal "costuma ser de 5 anos"): há indício de prazo de 11 anos (132 meses) em ato recente, de alcance incerto. Não alterar a CLAUDE.md; **validar com o jurídico** (§21 item 14). Até lá, não expurgar.
- **Status:** FONTE LOCALIZADA (parcial; só secundárias; alcance do prazo de 11 anos em conflito)
- **Afeta:** Política de retenção/expurgo (§14)

## Resumo para o autor

**Aviso geral:** nenhuma fonte primária (NT/MOC no portal da NF-e) foi lida; o portal deu "Too many redirects" e os PDFs de terceiros não puderam ser extraídos. Tudo abaixo é pista secundária.

### (a) Contradições com o CLAUDE.md (⚠)
- **Item 14 (guarda):** indício de 132 meses (11 anos, Ajuste SINIEF 2/2025) contra os "5 anos" do §14. Alcance incerto. Entrada `[DUVIDA] E2-S01` em PARA-VOCE.md. Até lá, não expurgar.
- Sem ⚠ nos demais. Atenção a dois pontos em aberto: `autXML` com limite 9 × 10 (item 5) e `108`/`632` sem fonte (item 3).

### (b) NÃO LOCALIZADO
- Texto primário da NT 2014.002 (itens 1–5, 8, 10, 11).
- `ultNSU < maxNSU` após 138 (item 1); duração do bloqueio do `656` por pontuais (item 2).
- `cStat` 107/108/109/632 (item 3).
- `autXML` para CT-e (item 5).
- Códigos 2102xx, efeito da Ciência no `procNFe` e quem assina (item 6).
- SHA-1 × SHA-256 e formato do `Id` do evento (item 7).
- Regra raiz/filial/procurador (item 8).
- URL de produção, pacote `PL_*` e política de versão (item 9).
- Limite por IP/volume (item 11) e serviço de protocolo (item 12).

### (c) Ordem sugerida de conferência
1. Item 3 (`cStat` 108/632/589) e itens 1–2: destravam a E2-S02.
2. Item 9 (URLs/`PL_*`) e item 8 (certificado): destravam o adapter e a E2D.
3. Itens 6–7: E2-S11.
4. Itens 4–5 e 11: smoke E2-S12.
5. Item 14 com o jurídico; itens 10, 12, 13 depois.
