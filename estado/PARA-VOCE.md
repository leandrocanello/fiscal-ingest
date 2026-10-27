# PARA VOCÊ — fila do humano

> O que só você pode fazer ou decidir. Responda **aqui mesmo** (edite no GitHub, branch `auto/estado`),
> abaixo de cada item, em "Resposta:". Depois ajuste o status no ROADMAP (ex.: `[h]` → `[x]`).
> Itens resolvidos podem ser movidos para o fim, em "Resolvidos".

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

### [DUVIDA] E2-S03 — 2026-10-27 — atualizar docs protegidos com o mapeamento de cStat decidido
- **O que é preciso:** a E2-S03 aplica no código a decisão E2-S02, mas `CLAUDE.md` §3.2 ("`632` → a confirmar"; `108`/`109` juntos)
  e `docs/ARCHITECTURE.md` §5.3 (l.168 e nota l.176–178) seguem desatualizados, e o agente não pode editá-los.
- **Opções:** (a) você atualiza os dois num PR `docs/` depois do merge da E2-S03; (b) deixar para a revisão geral de docs.
- **Recomendação:** (a) — texto sugerido: `108 → IndisponivelTemporario`, `109 → ServicoFora`, `632 (só consChNFe, fora de prazo)
  → IndisponivelDefinitivo`, `589 → IndisponivelDefinitivo (cursor inconsistente)`, desconhecido → `IndisponivelTemporario(5 min)` + alerta; fonte MOC 7.0.
- Resposta (2026-10-27, autor): (a) — Claude abre o PR `docs/` com CLAUDE.md §3.2 e ARCHITECTURE §5.3 atualizados.

### [DUVIDA] E2-S10 — 2026-10-27 — truststore ICP-Brasil para o mTLS com o AN
- **O que é preciso:** o servidor do AN usa certificado ICP-Brasil, que não está no `cacerts` do JDK. A E2-S10 deixa o
  `KeyStore` de confiança **injetado** (testes usam o do WireMock); falta decidir de onde vem o real.
- **Opções:** (a) arquivo de ACs ICP-Brasil (baixado do ITI) versionado em `adapter-nfe-nacional/src/main/resources` (é público, não é segredo);
  (b) caminho configurável por variável de ambiente; (c) as duas: (a) como padrão e (b) para sobrescrever.
- **Recomendação:** (c), validado no smoke E2-S12. Não bloqueia a E2-S10.
- Resposta (2026-10-27, autor): (c) — ACs ICP-Brasil (públicas, do ITI) versionadas como padrão + caminho por variável de ambiente para sobrescrever; validar no smoke E2-S12.

### [DECISAO] E2-S11 — 2026-10-27 — padrão da assinatura XML-DSig do evento de manifestação
- **O que é preciso:** a E2-S11 monta e assina o evento 210210 (Ciência). `docs/FONTES-OFICIAIS.md` item 7 deixou
  explicitamente "a decisão do algoritmo para a E2-S11, após conferência do autor"; só há fontes secundárias.
  Mudaria o XML gerado (e um erro só aparece na SEFAZ, como rejeição 297/298).
- **D1 — algoritmo:** (a) `rsa-sha1` + digest `sha1` (o que a NF-e usa historicamente, de memória); (b) `rsa-sha256` + digest
  `sha256` (resumo da SEF-SC, não confirmado); (c) **parâmetro** `AlgoritmoAssinatura {RSA_SHA1, RSA_SHA256}` no assinador,
  com o padrão escolhido por você. Prós de (c): troca em um lugar e o teste cobre os dois; contra: um enum a mais.
- **D2 — confirmar o resto do padrão** (de memória, fontes só secundárias): transforms `enveloped-signature` + C14N
  inclusivo `xml-c14n-20010315` (sem comentários), `Reference URI="#ID..."`, `KeyInfo/X509Data` só com o certificado do
  titular; `Id` do evento = `"ID" + tpEvento(6) + chNFe(44) + nSeqEvento(2 dígitos)`; `cOrgao=91` (AN), `versao="1.00"`,
  `verEvento="1.00"`, `descEvento="Ciencia da Operacao"` (sem acento), `dhEvento` com fuso (`AAAA-MM-DDThh:mm:ss-03:00`).
- **Roteiro:** abra o PDF do MOC 7.0 que você já tem, seção do "Padrão de Assinatura Digital" (cap. 4, perto do
  `nfeRecepcaoEvento`) e o leiaute do evento (`infEvento`); confira D1 e D2; registre a página em FONTES-OFICIAIS item 7
  (num PR `docs/`) e responda abaixo.
- **Recomendação:** D1 (c) com padrão = o que o MOC disser (provável `RSA_SHA1`); D2 como acima, com as constantes isoladas
  numa classe marcada [VALIDAR item 6/7] e conferidas no smoke E2-S12. Observação: o **envio** do evento (SOAP
  `RecepcaoEvento` + mapeamento do `retEvento`) ficou fora da E2-S11, porque o transporte da E2-S06 ainda não está na base;
  sugiro uma subetapa nova `E2-S11b` (AUTO+, deps E2-S07, E2-S11) no ROADMAP.
- Resposta (2026-10-27, autor): D1 (c) `AlgoritmoAssinatura {RSA_SHA1, RSA_SHA256}` com padrão **RSA_SHA1** — MOC 7.0 Tab. 4-2, p. 54–55 (rsa-sha1, digest sha1, enveloped + C14N `REC-xml-c14n-20010315`, EndCertOnly). D2 como proposto — confirmado no MOC 7.0 p. 100 (`Id` = "ID" + tpEvento + chave + nSeqEvento, 54 posições; `cOrgao` 91 = AN; `dhEvento` com TZD; `nSeqEvento` = 1); `descEvento` "Ciencia da Operacao" (p. 109). Constantes isoladas com [VALIDAR item 6/7] para o smoke. Envio do evento: nova subetapa E2-S13.
