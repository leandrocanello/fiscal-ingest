# ROADMAP — modo autônomo

> Lido e escrito pelo **script**. Você pode editar à vontade (no GitHub, branch `auto/estado`), por exemplo:
> marcar `[x]` uma subetapa HUMANO/DECISAO que você concluiu, ou trocar `[!]` por `[ ]`/`[~]` depois de destravar.
> O agente nunca marca `[x]`.
>
> Formato: `- [S] ID | TIPO | deps: A,B | título`
> Status: `[ ]` pendente · `[~]` em andamento · `[r]` PR aguardando revisão · `[c]` você pediu mudanças ·
> `[x]` aprovado (merge) · `[!]` bloqueado · `[h]` aguardando você (DECISAO/HUMANO preparado)
> Tipos: AUTO · AUTO+ (sensível: Opus + revisão forte) · RASCUNHO · DECISAO · HUMANO

## Etapa 1 — Saneamento (Fase 1.5)
- [x] E1-S01 | HUMANO   | deps: -      | build: wrapper, settings, arquivos recuperados (PR fase-1.5/build) — rodar ./gradlew test e mergear
- [x] E1-S02 | HUMANO   | deps: -      | docs alinhados à v2 (PR fase-1.5/docs) — revisar e mergear
- [x] E1-S03 | DECISAO  | deps: -      | decisões que destravam o agente (D1–D5: ADRs 025–029)
- [x] E1-S04 | AUTO     | deps: E1-S01 | ArchUnit: regra "domain não depende de nada"
- [x] E1-S05 | AUTO     | deps: E1-S01,E1-S03 | jqwik (ADR-026) + propriedades do que já existe (GapCalculator, TokenBucket, Nsu)
- [x] E1-S06 | AUTO+    | deps: E1-S05 | invariante 1: guarda + teste de propriedade de não-retrocesso no AvancarPonteiroUseCase (auditoria A4)

## Etapa 2 — Adapter NF-e Ambiente Nacional (Fase 2)
- [x] E2-S00 | AUTO     | deps: E1-S01,E1-S02 | remover CXF (javax) do adapter: HTTP + JAXB + Santuario (auditoria A9)
- [x] E2-S01 | RASCUNHO | deps: -      | docs/FONTES-OFICIAIS.md: rascunho com links do MOC/NTs para cada [VALIDAR] do CLAUDE.md §21
- [x] E2-S02 | DECISAO  | deps: E2-S01 | mapeamento de cStat (108/632/589) a partir das fontes oficiais (auditoria A1–A3)
- [x] E2-S03 | AUTO+    | deps: E2-S02 | aplicar o mapeamento decidido no CStatMapper (testes primeiro)
- [x] E2-S04 | AUTO     | deps: E1-S01 | porta BlobStore no domain + implementação em memória
- [x] E2-S05 | AUTO     | deps: E1-S01 | decode de docZip (base64+gzip) + classificador de schema + extração de metadados
- [x] E2-S06 | AUTO     | deps: E2-S05 | interface de transporte SOAP + montagem do envelope distDFeInt
- [r] E2-S07 | AUTO+    | deps: E2-S04,E2-S06,E2-S03 | buscarLote com WireMock (137/138/656/108/109/timeout/5xx/malformado)
- [ ] E2-S08 | AUTO     | deps: E2-S07 | suíte de contrato do SourceAdapter (fake + nfe-nacional)
- [ ] E2-S09 | AUTO     | deps: E2-S07 | buscarUm (consNSU) + consChNFe
- [x] E2-S10 | AUTO+    | deps: E1-S01 | Credencial = bytes do .pfx + senha; carregador (inclui legado) + SSLContext por certificado + cache
- [r] E2-S11 | AUTO+    | deps: E2-S10,E2-S01 | manifestar: evento 210210 + XML-DSig (com chave de teste gerada no teste)
- [ ] E2-S13 | AUTO+    | deps: E2-S07,E2-S11 | enviar o evento de manifestação (RecepcaoEvento SOAP) + mapear o retEvento
- [ ] E2-S12 | HUMANO   | deps: E2-S09,E2-S13 | smoke em homologação: distNSU, consChNFe, ciência, autXML

## Etapa 3 — Persistência e catálogo (Fase 3)
- [x] E3-S01 | AUTO     | deps: E1-S03 | JDBI + Flyway (ADR-025) + esquema base (tenant, empresa, estado_coleta, ledger_*)
- [ ] E3-S02 | AUTO+    | deps: E3-S01 | CAS do cursor + testes de caos (dois fetchers, um avança)
- [ ] E3-S03 | AUTO+    | deps: E3-S01 | RLS por tenant + teste "A não lê B"
- [ ] E3-S04 | AUTO     | deps: E3-S01 | outbox transacional + relay (fila no Postgres)
- [ ] E3-S05 | AUTO     | deps: E2-S04 | BlobStore MinIO (Testcontainers) + sha256 + idempotência
- [ ] E3-S06 | AUTO+    | deps: E3-S01 | catálogo: documento/arquivo/proveniência/documento_empresa
- [ ] E3-S07 | AUTO+    | deps: E3-S06,E2-S05 | processor de parse + ROTEAMENTO PELO CONTEÚDO + quarentena
- [ ] E3-S08 | AUTO     | deps: E3-S02..07 | ponta a ponta local: 1 CNPJ empresa + 1 CNPJ escritório (fake)

## Etapa 4 — Scheduler, cofre, consentimento (Fase 4)
- [ ] E4-S01 | AUTO     | deps: E3-S02 | claim SKIP LOCKED + lease + due_at + fairness por tenant
- [ ] E4-S02 | AUTO+    | deps: E2-S10 | CofreDeCertificados local (envelope encryption) — sem segredo em log
- [ ] E4-S03 | AUTO     | deps: E3-S01 | consentimento por finalidade + máquina de estados de estado_coleta
- [ ] E4-S04 | AUTO     | deps: E4-S02 | alertas de vencimento + rotação sem perder cursor
- [ ] E4-S05 | AUTO     | deps: E4-S01..04 | simulação de N CNPJs sem nenhum 656

## Etapa 5 — Porta push + API de entrega (Fases 5 e 6)
- [x] E5-S01 | DECISAO  | deps: -      | framework HTTP dos app-*: Spring Boot só nos app-* (ADR-027)
- [ ] E5-S02 | AUTO+    | deps: E3-S06,E5-S01 | POST /v1/ingestao/arquivos + parser seguro (XXE, zip bomb)
- [ ] E5-S03 | AUTO+    | deps: E5-S02 | validação de assinatura + confiança + CNPJ do agente
- [ ] E5-S04 | AUTO+    | deps: E3-S04 | sequenciador do feed + teste de propriedade "sem buracos"
- [ ] E5-S05 | AUTO     | deps: E5-S04 | GET /v1/feed, /documentos, /xml + OpenAPI
- [ ] E5-S06 | AUTO+    | deps: E5-S05 | OAuth do integrador + autorização por empresa/escopo
- [ ] E5-S07 | AUTO+    | deps: E5-S06 | webhooks: HMAC, retentativa, reenvio, log de entregas
- [ ] E5-S08 | AUTO     | deps: E5-S07 | sandbox de integrador
