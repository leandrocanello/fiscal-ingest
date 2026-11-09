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
- Resposta (2026-10-27, autor): D1 (c) `AlgoritmoAssinatura {RSA_SHA1, RSA_SHA256}` com padrão **RSA_SHA1** — MOC 7.0 Tab. 4-2, p. 54–55 (rsa-sha1, digest sha1, enveloped + C14N `REC-xml-c14n-20010315`, EndCertOnly). D2 como proposto — confirmado no MOC 7.0 p. 100 (`Id` = "ID" + tpEvento + chave + nSeqEvento, 54 posições; `cOrgao` 91 = AN; `dhEvento` com TZD; `nSeqEvento` = 1); `descEvento` "Ciencia da Operacao" (Tab. 5-41, p. 113). Constantes isoladas com [VALIDAR item 6/7] para o smoke. Envio do evento: nova subetapa E2-S13.

### [DUVIDA] E2-S07 — 2026-10-28 — `ExtratorMetadados` (E2-S05) não está na `main`
- **O que é preciso:** os commits `79550f6`, `420e705` e `34558b3` (extrator de metadados de resNFe/procNFe/eventos) não estão no histórico da `main`, embora a E2-S05 esteja `[x]`. Na `main` há só `DocZipDecoder`/`ClassificadorSchema`.
- **Opções:** (a) recuperar o extrator numa subetapa curta (cherry-pick revisado) antes da E3-S07; (b) deixar o processor (E3-S07) extrair os metadados.
- **Recomendação:** (a). Não bloqueia a E2-S07: lá o `Documento.chave` fica vazio e a chave é extraída depois.
- Resposta (2026-10-29, Claude/autor): confirmado — 6 commits da E2-S05 nunca chegaram à `main` (PR #13 mergeado antes deles), e o mesmo aconteceu com E2-S08/E2-S09 (PRs #25/#26 entraram em `auto/E2-S07`). Tudo recuperado na branch `auto/E2-S14`; a subetapa E2-S14 só verifica e abre o PR.

### [DUVIDA] E2-S07 — 2026-10-28 — prefixo de tenant nas chaves `raw/pull/`
- **O que é preciso:** a §8.1 põe `{tenant}` em `raw/pull/...`, mas a coleta é por `(fonte, cnpj_consultado, ambiente)` e o adapter não conhece tenant (DA-13). Um mesmo CNPJ consultado pode servir mais de um tenant?
- **Opções:** (a) pull sem tenant (`raw/pull/{fonte}/{ambiente}/{cnpj}/...`), tenant só a partir do catálogo (`doc/{tenant}/...`); (b) levar tenant ao `ContextoConsulta` e ao estado de coleta.
- **Recomendação:** (a) agora (já aplicado na E2-S07, fácil de mudar) e ajustar a §8.1 num PR `docs/`.
- Resposta (2026-10-29, autor): (a) — bruto da coleta sem tenant (`raw/pull/{fonte}/{ambiente}/{cnpj}/...`), tenant só no catálogo. CLAUDE.md §8.1 corrigido no PR #28.

### [DUVIDA] E2-S08 — 2026-10-29 — formato de `Documento.blobRef` difere entre adapters
- **O que é preciso:** `Documento.blobRef` é `Optional<String>`. O fake grava `chave@sha256`; o `NfeNacionalAdapter` grava só a chave (sha no nome do arquivo). A suíte de contrato contorna com o gancho `lerBlob` por subclasse, mas o core/processor não saberá montar o `BlobRef` de forma uniforme.
- **Opções:** (a) `Documento.blobRef` passa a ser `Optional<BlobRef>` (mexe no domain; subetapa própria); (b) fixar convenção "só a chave" e o `BlobStore.ler` aceitar só a chave; (c) deixar como está até a E3 (processor).
- **Recomendação:** (a), antes da E3-S07; aí o gancho `lerBlob` some do contrato.
- Resposta (2026-10-29, autor): (a) — `Documento.blobRef` vira `Optional<BlobRef>`; subetapa E2-S15 (antes da E3-S07), e o gancho `lerBlob` sai do contrato.

### [DECISAO] E2-S09 — 2026-10-29 — cStat específicos do consChNFe (não bloqueia)
- **O que é preciso:** a Tab. 5-29 do MOC 7.0 (p. 96) lista rejeições do consChNFe que hoje caem no fallback decidido (`IndisponivelTemporario(5 min)` + alerta): `217` NF-e inexistente, `236`/`614`–`619` chave inválida, `252` ambiente divergente, `593` raiz do CNPJ difere do certificado, `640` interessado sem permissão, `641` NF-e indisponível para o emitente, `653`/`654` cancelada/denegada. Se o scheduler retentar a cada 5 min, gasta o limite de ~20/h dos pontuais.
- **Opções:** (a) `217`, `236`, `614`–`619`, `640`, `641`, `653`, `654` → `IndisponivelDefinitivo` (para aquela consulta, sem bloquear o CNPJ), `252`/`593` → `ErroAutenticacao`/`IndisponivelDefinitivo` (configuração); (b) manter o fallback e só alertar; (c) (a), mas confirmando antes os textos numa NT mais nova que o MOC de 2020.
- **Recomendação:** (a), em uma subetapa curta depois da E2-S09 (uma linha por código no `CStatMapper`, com teste). A E2-S09 segue com o fallback.
- Resposta (2026-10-29, autor): (a) — `217`, `236`, `614`–`619`, `640`, `641`, `653`, `654` → `IndisponivelDefinitivo` daquela consulta (sem bloquear o CNPJ); `593` (raiz do CNPJ ≠ certificado) → `ErroAutenticacao`; `252` (ambiente divergente) → `IndisponivelDefinitivo` (configuração). Fonte: MOC 7.0 Tab. 5-29, p. 96. Subetapa E2-S16.

### [DECISAO] E2-S13 — 2026-10-30 — envio do evento: contrato do RecepcaoEvento, mapeamento do retEvento e assinatura da porta `manifestar`
- **O que é preciso:** a E2-S13 envia o evento de Ciência (já montado e assinado na E2-S11) ao `NFeRecepcaoEvento` do AN e
  traduz a resposta para `Desfecho`. Os docs não registram o contrato SOAP do serviço nem os cStat da resposta
  (`docs/FONTES-OFICIAIS.md` só tem a distribuição e o leiaute de entrada, p. 100). Um erro aqui vira rejeição na SEFAZ ou,
  pior, retentativa que gasta limite. O plano fica parado até a resposta.
- **D1 — cStat → `Desfecho`** (de memória; conferir na tabela de validações do `NFeRecepcaoEvento` no MOC 7.0, logo depois da p. 100):
  - lote (`retEnvEvento`): `128` lote processado → olhar o `retEvento`; `108` → `IndisponivelTemporario`; `109` → `ServicoFora`;
    `656` → `LimiteAtingido(1h)`; demais rejeições do lote (schema etc.) → `IndisponivelDefinitivo`.
  - evento (`retEvento/infEvento`): `135` registrado e vinculado → `Ok`; `136` registrado, não vinculado → `Ok`;
    `573` duplicidade de evento → **`Ok`** (a ciência já existe; torna o envio idempotente); `650`/`655` (nota cancelada/denegada,
    ou ciência após manifestação conclusiva), `596` (fora de prazo), chave inexistente/inválida → `IndisponivelDefinitivo`
    (não retentar); `297`/`298` (assinatura) → `IndisponivelDefinitivo` + alerta (é bug nosso); autor ≠ certificado → `ErroAutenticacao`.
  - desconhecido → `IndisponivelTemporario(5 min)` + alerta com o código bruto (mesma regra da distribuição, E2-S02).
  - Opções: (a) como acima; (b) `573` → `IndisponivelDefinitivo` (trata duplicidade como erro); (c) (a) com os códigos ajustados ao MOC.
- **D2 — contrato SOAP** (constantes isoladas em `ContratoRecepcaoEvento`, como o `ContratoDistDfe`, marcadas [VALIDAR item 9]):
  namespace do WSDL `http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4`, operação `nfeRecepcaoEvento`,
  `envEvento versao="1.00"`, `idLote` numérico até 15 dígitos, um evento por lote. A URL **não** entra no código (é injetada,
  como hoje). Opções: (a) estes valores; (b) os que o MOC trouxer.
- **D3 — porta e certificado:** (a) `SourceAdapter.manifestar(ContextoConsulta ctx, String chaveAcesso, TipoManifestacao tipo)
  → Optional<ManifestarResultado>` como **método default** que devolve `Optional.empty()` (o fake e a suíte de contrato não quebram);
  `ManifestarResultado(Desfecho desfecho, Optional<BlobRef> envelope)` (como o `BuscarLoteResultado`); `nSeqEvento` = 1 fixo;
  o adapter guarda em memória o `CertificadoCarregado` aberto no `autenticar` (o `SSLContext` já guarda a mesma chave privada).
  (b) `manifestar` recebe a `Credencial` a cada chamada (abre o .pfx toda vez; não guarda chave). A docs/ARCHITECTURE.md §5.5 cita
  `ReferenciaDocumento`, que não existe; com (a), a §5.5–5.6 precisa de um PR `docs/` seu depois do merge.
- **Roteiro:** abra o MOC 7.0 no capítulo do `NFeRecepcaoEvento` (perto da p. 100): confira a tabela de cStat do retorno (D1) e o
  WSDL/leiaute do `envEvento` (D2); anote as páginas em FONTES-OFICIAIS (item 6/9) num PR `docs/`; responda abaixo D1, D2, D3.
- **Recomendação:** D1 (c) partindo de (a), com `573 → Ok`; D2 (a), conferido no smoke E2-S12; D3 (a).
- Resposta (2026-11-03, autor): conforme a recomendação — D1 (c) partindo de (a), com `573 → Ok` ("sem complexidade desnecessária"); D2 (a), conferido no smoke E2-S12; D3 (a).

### [DECISAO] E3-S05 — 2026-10-30 — cliente S3 e imagem de teste do `BlobStore` MinIO
- **O que é preciso:** a E3-S05 cria o módulo `infra-blob-minio` (implementação real da porta `BlobStore`). Os docs dizem
  "MinIO/S3" e "Testcontainers MinIO" (ARCHITECTURE §7), mas **não escolhem a biblioteca cliente** nem como garantir a
  imutabilidade (inv. 7) com dois fetchers gravando a mesma chave. O plano fica parado até a resposta.
- **D1 — biblioteca cliente:**
  - (a) **AWS SDK for Java v2** (`software.amazon.awssdk:s3`, Apache-2.0) com `forcePathStyle` e endpoint configurável.
    Pró: neutro de fornecedor (MinIO, S3, Garage, Ceph…), suporta escrita condicional `If-None-Match: *` e
    `ChecksumSHA256` (o servidor confere o hash). Contra: dependência grande (netty/http-client; dá para usar o
    `url-connection-client` e excluir o netty).
  - (b) **MinIO Java SDK** (`io.minio:minio`, Apache-2.0). Pró: leve, API simples. Contra: amarra ao MinIO; o projeto
    MinIO comunitário entrou em modo de manutenção/sem imagens oficiais novas no fim de 2025 **[VALIDAR]**.
  - (c) HTTP cru (`java.net.http`) + assinatura SigV4 própria. Pró: zero dependência (como o SOAP). Contra: reinventa
    SigV4 — contraria §2.3 e é fonte de bug.
- **D2 — imagem do servidor nos testes:** (a) `minio/minio` com **tag fixa** (última `RELEASE.*` disponível, registrada
  num lugar só, como o Postgres na DA-11); (b) outro S3 compatível (ex.: Garage/SeaweedFS) se a imagem do MinIO sumir.
  O código não muda entre (a) e (b) se D1 = (a).
- **D3 — imutabilidade concorrente:** (a) `PUT` com `If-None-Match: *` (escrita condicional; quem perde recebe 412, lê o
  sha256 gravado e devolve o mesmo `BlobRef` ou lança `BlobConflitanteException`); (b) `HEAD` antes do `PUT` (tem janela
  de corrida: dois fetchers podem gravar conteúdos diferentes na mesma chave). Como a chave já contém o sha256 (DA-13),
  conflito real é quase impossível, mas a inv. 7 pede a garantia.
- **Roteiro:** confira na página do MinIO/Docker Hub se `minio/minio` ainda publica tags e qual a última; responda D1–D3
  abaixo. Se escolher D1 (b), avise se aceita depender do MinIO em produção.
- **Recomendação:** D1 (a) com `url-connection-client` · D2 (a) com tag fixa · D3 (a).
- Resposta (2026-11-03, autor): conforme a recomendação — D1 (a) com `url-connection-client`; D2 (a) com tag fixa; D3 (a).

### [DUVIDA] E3-S03/E3-S04 — 2026-11-02 — `outbox` quebra o meta-teste de RLS quando as duas forem mescladas (não bloqueia)
- **O que é preciso:** cada PR passa sozinho, mas juntos não: `TodaTabelaComTenantIdTemRlsTest.tabelasSemTenantSaoSoAsDeclaradas` (E3-S03) só aceita `tenant`, `estado_coleta`, `ledger_nsu`, `ledger_tentativa` e `flyway_schema_history`, e a E3-S04 cria `outbox` e `outbox_consumo` sem `tenant_id`. O teste foi lido, não executado com as duas branches juntas.
- **Opções:** (a) incluir as duas tabelas em `TABELAS_SEM_TENANT`, com comentário: o outbox é infraestrutura e a coleta nem tem tenant; (b) pôr `tenant_id` no outbox, mas as mensagens da coleta não têm tenant (§8.1).
- **Recomendação:** (a), no PR que for mesclado por último. Com a E3-S04 sem RLS, confira que o payload continua só com metadados.
- Resposta:

### [DECISAO] E3-S07 — 2026-11-03 — regras do roteamento que a §7.3 não fecha
- **O que é preciso:** o plano `planos/E3-S07.md` está pronto, mas parado no passo `[!]`. As três regras abaixo definem onde cada nota vai parar, e a §7.3, lida ao pé da letra, dá resultado errado nos dois fluxos principais. O plano assume as recomendações; outra resposta muda só os passos indicados nele.
- **D1 — resumo e evento não trazem o CNPJ da empresa destinatária.** O `resNFe` só traz o CNPJ do emitente (`ExtratorMetadados`: `resNFe/CNPJ`). Como a §7.3 manda usar "os CNPJs que eles trazem", todo resumo de entrada cairia em quarentena `SEM_EMPRESA`, com o aviso falso "cadastre o CNPJ X", e sem empresa a ciência não teria a quem atender (§3.3). No evento, o `CNPJ` é o do autor: num cancelamento feito pelo emitente, a regra daria ENTRADA para quem emitiu.
  - (a) **Resumo:** além do conteúdo, a empresa do `cnpj_consultado` recebe ENTRADA se for do tipo `EMPRESA` e não for a emitente. **Evento:** só o emitente, lido da chave de acesso, gera vínculo (SAÍDA); o autor é ignorado e os demais vínculos vêm da nota. Pró: o fluxo normal funciona; a §7.3 já dá ENTRADA a todos os destinatários do resumo (destinatário, transportador, autorizado). Contra: é exceção à inv. 10, que diz "nunca **só** pelo CNPJ que fez a consulta"; o vínculo não pode ser desfeito (`documento_empresa` não tem `delete`); e quem recebe resumo ainda é [VALIDAR] no smoke E2-S12.
  - (b) **Literal:** resumo sem empresa no conteúdo fica em quarentena até chegar o `procNFe`. Pró: inv. 10 sem exceção. Contra: o aviso falso é a regra, e a nota sem ciência nunca sai da quarentena, porque o XML só vem depois da manifestação.
  - (c) Como (b), mas com um motivo próprio `AGUARDANDO_XML`, sem aviso. Pró: não há vínculo inferido. Contra: a empresa continua sem ver o resumo no catálogo e na API, e a ciência fica sem empresa.
- **D2 — escritório no `autXML`.** Consultando com o certificado do escritório, o CNPJ dele **sempre** está no `autXML`. Ao pé da letra ("autorizado ∈ empresas → ENTRADA"), toda saída de cliente vira também ENTRADA do escritório, e a quarentena de "cliente não cadastrado" nunca dispara, o que contradiz a própria §7.3.
  - (a) `autXML` só gera vínculo para empresa do tipo `EMPRESA`. Empresa `ESCRITORIO` só é vinculada como emitente, destinatária ou transportadora. Pró: as saídas vão só para o cliente, e o não cadastrado cai em quarentena como a §7.3 quer. Contra: é uma exceção por tipo de empresa.
  - (b) Literal, com ENTRADA para o escritório. Contra: o integrador do escritório recebe as notas dos clientes como compras, e a quarentena morre.
  - (c) Nova direção `AUTORIZADO`. Pró: registra o fato. Contra: muda enum, esquema e contrato da API, sem uso hoje.
- **D3 — como descobrir os tenants de um CNPJ consultado.** O bruto e o ledger não têm tenant (§8.1, sua decisão de 2026-10-29), e a `empresa` terá RLS forçada (V5 da E3-S03). Mas o processor precisa perguntar "quais tenants têm esse CNPJ?" antes de ter um tenant. Assumo que a nota vai a **todos** os tenants ativos que têm o CNPJ consultado e a **nenhum outro**, mesmo que outro tenant tenha o emitente: ele não consultou nem autorizou esse certificado.
  - (a) Função `security definer` que devolve só os ids de tenant de um CNPJ, dona de um papel `nologin` novo, que tem uma política de leitura só para ele e `select` só nas colunas necessárias. Pró: privilégio mínimo, mesmo pool, sem listar empresas nem CNPJs. Contra: um papel e uma política a mais. A função precisa desse papel porque o `force` da RLS também vale para o dono da tabela.
  - (b) Segundo usuário/pool com `BYPASSRLS` para o processor. Pró: simples. Contra: uma falha ali lê todos os tenants, o que enfraquece a §13.
  - (c) Tabela nova `coleta_assinante (cnpj_consultado, ambiente, tenant_id)`, mantida no cadastro. Pró: explícita, e serve para o expurgo ("nenhum tenant ativo usa o CNPJ"). Contra: mais uma fonte de verdade para sincronizar com `empresa`, e ela mesma ainda precisaria de leitura sem tenant.
- **Roteiro:** responda D1–D3 abaixo. Se aceitar D1 (a), diga se a exceção à inv. 10 deve constar da §2.2/§7.3 (você atualiza o CLAUDE.md; o agente não edita). Depois troque a E3-S07 de `[h]` para `[~]` no ROADMAP. Vale conferir as premissas do D1 no smoke E2-S12, mas ele não precisa vir antes: o catálogo é reconstruível a partir do bruto.
- **Recomendação:** D1 (a) · D2 (a) · D3 (a).
- **Para ver junto (não exige decisão):** o outbox saiu desta subetapa e foi para a E3-S08, porque a E3-S04 não está nesta base. As migrations V10/V11 reforçam o achado 2 do revisor da E3-S06: mescle E3-S03 e E3-S04 **antes** da E3-S06, senão banco já migrado quebra.
- Resposta (2026-11-03, autor): conforme a recomendação — D1 (a), D2 (a), D3 (a). Autor: "é o mais complicado, mas pelas opções descritas não vejo caminho melhor por hora" — registrar como revisável quando o catálogo rodar com dados do smoke.

### [DECISAO] E4-S01 — 2026-11-03 — fairness por tenant numa coleta que não tem tenant
- **O que é preciso:** o plano `planos/E4-S01.md` está pronto, mas parado no passo `[!]`. A §6.5 pede "teto de claims por tenant", só que `estado_coleta` não tem tenant (sua decisão da §8.1, de 2026-10-29), um CNPJ pode servir a dois tenants e a `empresa` terá RLS forçada (V5 da E3-S03). Os docs não dizem como o scheduler descobre o tenant de cada chave. Claim, lease e `due_at` não dependem disso. A fairness não protege nenhuma invariante: ela é só operacional.
- **D1 — de onde vem o tenant da chave.**
  - (a) `empresa`, por `(cnpj, ambiente)`, pela função `tenants_do_cnpj_consultado` da E3-S07 (D3 (a) dela). Pró: um só mecanismo, sem nova fonte de verdade, e a função só expõe ids de tenant. Contra: depende da resposta à E3-S07 e da V11 na base; uma chamada por chave elegível a cada ciclo (medir na E4-S05).
  - (b) `estado_coleta.certificado_id` → `certificado.tenant_id`. Pró: quem paga é o dono do certificado usado, sem ambiguidade. Contra: espera a E4-S02, a coluna é nula em `AGUARDANDO_CERTIFICADO` e também precisa atravessar a RLS.
  - (c) Coluna de tenant em `estado_coleta`. Pró: SQL simples. Contra: contraria a §8.1 e não representa o CNPJ compartilhado.
- **D2 — CNPJ em dois tenants: quem gasta o teto.** A coleta é uma só nos dois casos.
  - (a) O menor `tenant_id` ativo. Pró: uma linha de SQL, determinístico, e pode mudar a qualquer hora, porque nada fica gravado. Contra: arbitrário, mas o caso é raro e só afeta o ritmo.
  - (b) Todos os tenants: a chave só sai se todos tiverem folga. Pró: conta exata. Contra: `row_number` não resolve, exige seleção gulosa, e um tenant cheio atrasa o outro.
- **D3 — chave sem empresa ativa em nenhum tenant.**
  - (a) Não é reivindicada; o cursor fica intacto e ela volta quando houver empresa. Pró: coerente com a §7.1 (sem consentimento, sem coleta) e não gasta consulta. Contra: enquanto ninguém a usa, documentos podem sair da janela de retenção do AN.
  - (b) É reivindicada sem contar em teto nenhum. Pró: não corre risco com a retenção. Contra: consulta com certificado sem nenhum tenant que responda por ela.
- **D4 — ordem de entrega.**
  - (a) Fatiar: a E4-S01 entrega claim, lease e `due_at` sem esperar nada, e os dois passos de fairness viram uma subetapa nova (sugestão: E4-S01b, deps E4-S01 e E3-S07). Pró: a E3-S08 e a E4-S05 não ficam presas à fila de PRs; até lá vale o `limite` global, sem risco enquanto não há produção. Contra: o critério "fairness" só fecha depois.
  - (b) Tudo junto: a E4-S01 espera a E3-S03 e a E3-S07 mescladas. Pró: subetapa inteira de uma vez. Contra: fica parada mais tempo.
- **Roteiro:** responda D1–D4 abaixo; responda junto com o D3 da E3-S07, porque D1 (a) depende dele. Depois troque a E4-S01 de `[h]` para `[~]` no ROADMAP. Se escolher D4 (a), crie também a linha da E4-S01b; o desenvolvedor pula os passos marcados "só se D4 = b". A E4-S01 usa a migration V12 (V10/V11 são da E3-S07).
- **Recomendação:** D1 (a) · D2 (a) · D3 (a) · D4 (a).
- Resposta (2026-11-03, autor): conforme a recomendação — D1 (a), D2 (a), D3 (a), D4 (a).

### [DUVIDA] E4-S02 — 2026-11-03 — quatro pontos do cofre (não bloqueiam)
- **O que é preciso:** o plano `planos/E4-S02.md` segue sem esperar resposta, com as escolhas das DA-35 e DA-36. Estes pontos ficam fora dele e pedem a sua decisão.
- **Q1 — persistência dos metadados.** A tabela `certificado` precisa de `tenant`/`empresa` (E3-S03, ainda sem merge). Sem ela, o cofre desta subetapa só tem repositório em memória e ainda não serve para produção. A E4-S04 também depende disso. (a) Criar a subetapa `E4-S02b | AUTO+ | deps: E4-S02,E3-S03`, com migration, `JdbiRepositorioCertificados` e RLS. (b) Incluir essa parte na E4-S04. **Recomendação:** (a).
- **Q2 — filial sem certificado próprio.** Pelo MOC, o e-CNPJ da matriz consulta as filiais de mesma raiz (FONTES item 8), mas a §7.4 exige CNPJ do certificado == titular, e o plano segue isso. Falta definir como nasce o `estado_coleta` da filial. (a) O certificado continua da matriz e a E4-S03 cria um estado por filial cadastrada, apontando para ele. (b) A filial vira titular com o certificado da matriz, afrouxando o cofre para "mesma raiz". **Recomendação:** (a), depois de conferir no smoke E2-S12.
- **Q3 — CNPJ alfanumérico.** `Cnpj` só aceita 14 dígitos. O nome do PDF da NT 2014.002 v1.40 indica CNPJ alfanumérico (FONTES item 8, texto não extraído). Hoje, um certificado assim seria recusado como `SEM_CNPJ`, e o resto do sistema também não o representaria. (a) Abrir uma subetapa transversal para o `Cnpj` antes de produção. (b) Esperar um caso real. **Recomendação:** (a), depois de conferir a NT na fonte primária.
- **Q4 — KEK em produção (§22, pergunta 2).** A porta aceita Vault ou KMS depois. Perdida a KEK local, nenhum certificado guardado abre: cada cliente precisa reenviar o seu. (a) Só KEK local por ora, por variável de ambiente, com backup fora do servidor documentado no runbook. (b) Definir já um KMS. **Recomendação:** (a), coerente com o self-host (ADR-008).
- **Para conferir (só você pode):** com um `.pfx` real, na sua máquina e fora do repositório, confirme o OID `2.16.76.1.3.3` e a codificação do valor (DA-36), de preferência no smoke E2-S12.
- Resposta (2026-11-09, autor): Q1 (a) → subetapa E4-S06 (tabela `certificado` + RLS) · Q2 (a), conferir no smoke · Q3 já é a E1-S07 · Q4 (a) KEK local por variável de ambiente, backup documentado.
### [HUMANO→AGENTE] E1-S07 — 2026-11-03 — CNPJ alfanumérico: o que o autor pediu
- **Pedido do autor:** "precisamos de CNPJ já validado para alfanumérico [...] validar isso 100% no back-end". A coluna `empresa.cnpj` já é texto (ok).
- **Fatos de partida (fonte secundária: Machado Meyer, resumo da NT Conjunta 2025.001 v1.00, de 08/05/2025):** 14 posições; 8 de raiz e 4 de ordem **alfanuméricas**, 2 DV **numéricos**; no DV, cada caractere vale (código ASCII − 48); homologação desde 06/04/2026, produção desde 06/07/2026; só para inscrições novas (os numéricos continuam válidos); a chave de acesso passa a aceitar letras nas 12 primeiras posições do CNPJ e o DV da chave segue a mesma lógica; o Encat pediu para excluir algumas letras (I, O, U, Q, F?), **sem confirmação**.
- **[VALIDAR]** pesos e módulo 11 do DV (a leitura usual é a mesma do CNPJ numérico: pesos 2–9 da direita para a esquerda) e a lista de letras permitidas — registrar a fonte em FONTES-OFICIAIS (item 8). Na dúvida, aceitar `[0-9A-Z]` maiúsculo e deixar a lista de letras proibidas como constante isolada.
- **Planejador:** cobrir no mínimo `Cnpj` (domínio, com propriedades jqwik: numéricos antigos continuam válidos; DV errado é sempre rejeitado; minúsculas/caracteres fora do alfabeto rejeitados ou normalizados — decidir e registrar em DA), `ChaveAcessoNfe` e o DV da chave, extração do CNPJ do certificado (OID 2.16.76.1.3.3), `ExtratorMetadados`, `ChavesBlobPull`, colunas e `CHECK` no Postgres, e máscara de CNPJ em log (inv. 13).
- Resposta: (autor) criar já, na frente das demais.

### [DUVIDA] E1-S07 → E4-S02 — 2026-11-04 — certificado alfanumérico e docs protegidos (não bloqueia)
- **Contexto:** o plano `planos/E1-S07.md` segue sem esperar resposta, com as escolhas das DA-37 e DA-38. O `ExtratorCnpjCertificado` só existe na branch `auto/E4-S02`, não na `main`, e recusa qualquer byte fora de `0`–`9`. Um certificado alfanumérico continuaria como `SEM_CNPJ`. Depois da E1-S07, um `otherName` com DV errado lançaria exceção em vez de devolver vazio.
- **Q1 — onde corrigir o extrator.**
  - (a) Na própria E4-S02, ao rebasear sobre a `main` depois da E1-S07: trocar o laço por `Cnpj.valido` e usar CNPJs de teste com DV certo. Pró: a correção entra junto com o código. Contra: a E4-S02 precisa esperar a E1-S07.
  - (b) Subetapa nova depois das duas. Pró: não mexe na E4-S02. Contra: a `main` fica com o extrator recusando esses certificados até lá.
  - **Recomendação:** (a).
- **Q2 — texto de docs protegidos.** O CLAUDE.md não cobre o formato. Sugestão para a §3.5: "CNPJ: `[A-Z0-9]{12}[0-9]{2}`, DV módulo 11 com valor = ASCII − 48 (NT Conjunta 2025.001); numéricos antigos continuam válidos". Só você edita.
- **Para conferir (só você pode):** se há versão da NT Conjunta 2025.001 posterior à v1.00 no portal da NF-e. Se confirmar letras vedadas, basta preencher `LETRAS_VEDADAS`. Para o achado de 2026-11-04: a NT 2014.002 v1.40 cita `OID=2.16.76.1.3.3` (regra A07), o que reforça a constante da DA-36.
- Resposta:


### [DECISAO] E1-S07 — 2026-11-04 — `main` com 2 testes vermelhos no infra-postgres (bloqueia "suíte verde")
- **O que é preciso:** autorizar a correção de 2 testes antigos. Eles quebraram no merge da E3-S04/E3-S06, não pela E1-S07. A `main` nem compilava esses testes (corrigido só com renomeação, DA-39). Como os testes de `infra-postgres` não rodavam, a falha não aparecia.
  - `MigracoesTest.aplicaV7ECriaOutboxEConsumo`: espera a última migration `7`, e já existe a `9`.
  - `TodaTabelaComTenantIdTemRlsTest.tabelasSemTenantSaoSoAsDeclaradas`: `outbox` e `outbox_consumo` não têm `tenant_id` e não estão em `TABELAS_SEM_TENANT`.
- **Por que não corrigi:** muda asserção de teste antigo, o que a regra só permite pelo plano. O segundo teste é guarda de RLS (§13): incluir tabelas na lista é decisão de segurança.
- **Opções:** (a) `MigracoesTest` passa a conferir que as tabelas existem, sem fixar a última versão, e as 2 tabelas entram na lista, se você confirmar que o outbox é mesmo sem tenant; (b) dar `tenant_id` e RLS ao outbox.
- **Recomendação:** (a), como passo do plano da E1-S07 ou numa correção direta na `main`. O `MigracoesTest` vai quebrar de novo com a V13 se continuar fixando a versão.
- **Efeito até lá (corrigido em 2026-11-04):** a E1-S07 está **parada**, sem nenhum código entregue. O texto anterior dizia que os passos terminariam com as 2 falhas, mas o `verificar.sh` reverte qualquer commit com a suíte vermelha: o passo 1 foi revertido às 08:13, junto com a renomeação da DA-39. Toda subetapa com base na `main` deve cair no mesmo problema.
- **Conferido em 2026-11-04, sem nenhuma alteração, em b839918 (igual a `origin/main`):** `./gradlew test` falha em `:infra-postgres:compileTestJava` (`CatalogoRlsTest.java:63: dataSourceComoApp(String) ... attempting to assign weaker access privileges`). As 2 falhas de asserção vêm do log da tentativa anterior, depois da renomeação; não as rodei de novo.
- **São 3 correções, não 2:** (1) renomear o helper do `CatalogoRlsTest` (DA-39, sem mudar asserção); (2) `MigracoesTest.aplicaV7ECriaOutboxEConsumo`; (3) `TodaTabelaComTenantIdTemRlsTest.TABELAS_SEM_TENANT`.
- **A favor da opção (a):** a §8 do CLAUDE.md define `outbox` sem `tenant_id`. Ela não cita `outbox_consumo` (V7): falta só você confirmar essa tabela.
- **Como destravar:** corrija as 3 na `main`, ou responda aqui autorizando (a) e inclua no plano um passo inicial com as 3 correções. Depois troque o `[!]` do passo 1 por `[ ]` e o status no ROADMAP de `[h]` ou `[!]` para `[~]`.
- **Problema no script (`autonomo/`, protegido, só você edita):** o papel manda marcar `[!]` e terminar sem commit, mas o `verificar.sh` trata isso como falha ("nenhum commit feito e nenhum passo fechado") e desfaz o `[!]` do plano. O desvio para `[h]` do `proxima-acao.sh` não chega a rodar. Assim, a E1-S07 deve repetir chamadas até `MAX_FALHAS` (3) e cair em `[BLOQUEIO]`. Sugestão: aceitar chamada sem commit quando o plano ganhou um `- [!]`.
- **Reconferido em 2026-11-04 (chamada 4 do dia):** sem resposta e `main` ainda em b839918; `:infra-postgres:compileTestJava` falha no mesmo ponto (`CatalogoRlsTest.java:63`). Nada mudou no código. Esta chamada também deve contar como falha no script, rumo ao `[BLOQUEIO]`.
- Resposta (2026-11-09, autor): (a) — as 3 correções foram feitas pelo Claude no PR #40 (`auto/E3-S09`); `outbox` e `outbox_consumo` confirmadas sem tenant.
### [BLOQUEIO] E1-S07 — 2026-11-04 — 3 falhas seguidas
Último motivo: nenhum commit feito e nenhum passo fechado no plano
Logs: `estado/logs/` (arquivos *-E1-S07-*.json). Para destravar: ajuste o plano `estado/planos/E1-S07.md`
ou responda aqui, e troque o status no ROADMAP de `[!]` para `[~]`.

### [DUVIDA] E2-S13 — 2026-11-04 — action do AN pode ser `nfeRecepcaoEventoNF` (não bloqueia)
- **O que é preciso:** D2 fixou a operação `nfeRecepcaoEvento`, e o código segue D2. Um usuário do [fórum ACBr (tópico 78871)](https://www.projetoacbr.com.br/forum/topic/78871-qual-%C3%A9-o-erro-do-meu-xml/) relata que, no endpoint nacional, a action documentada é `.../NFeRecepcaoEvento4/nfeRecepcaoEventoNF`. É relato de fórum, não fonte oficial, e o WSDL não foi aberto.
- **Opções:** (a) manter D2 e conferir no WSDL/smoke E2-S12; (b) trocar agora a constante `ContratoRecepcaoEvento.OPERACAO`.
- **Recomendação:** (a), conferindo o WSDL no roteiro do smoke. A troca é de uma linha mais o `ContratoRecepcaoEventoTest`. Ver também DA-41 (`nfeDadosMsg` direto no `Body`).
- Resposta (2026-11-09, autor): (a) — manter `nfeRecepcaoEvento` e conferir no WSDL durante o smoke.

### [DECISAO] E2-S13-EVENTO — 2026-11-04 — guardar o evento de manifestação assinado antes de enviar (não bloqueia)
- **O que é preciso:** o `manifestar` guarda só a resposta. Se ela se perde por timeout, não fica cópia do que foi declarado em nome do destinatário (§1.5 "guardar tudo", §3.3, §14).
- **Opções:** (a) gravar o evento assinado no blob antes do envio, em subetapa curta depois do merge; (b) deixar para a E4, junto com a auditoria de quem manifestou; (c) não guardar, pois a SEFAZ devolve o evento pela distribuição.
- **Recomendação:** (a), com falha na gravação impedindo o envio. Antes de ligar a manifestação na E4, a cópia precisa existir; (c) não cobre a falha de envio nem o que foi tentado.
- Resposta (2026-11-09, autor): (a) — subetapa E2-S18: gravar o evento assinado no blob antes do envio; falha na gravação impede o envio.

### [DECISAO] E3-S05-IMAGEM — 2026-11-09 — imagem MinIO da D2 não existe mais no Docker Hub (bloqueia E3-S05)
- **O que é preciso:** a D2 aceita (`minio/minio` com tag fixa) falha: `docker pull minio/minio` → "pull access denied/repository does not exist"; `quay.io/minio/minio:<tag>` e `:latest` → 401 UNAUTHORIZED (sem login). O módulo e o `MinioDisponivelTest` estavam prontos (Testcontainers 2.0.2: classe `org.testcontainers.containers.MinIOContainer`, não `.minio.`); só a imagem não baixa. Nada foi commitado; a árvore foi limpa.
- **Opções:** (a) você faz `docker login quay.io` (ou libera o acesso) e eu uso `quay.io/minio/minio:RELEASE.<tag fixa>`; (b) `chainguard/minio` (baixa aqui, mas só tem `latest` — quebra a tag fixa da DA-11); (c) espelhar a imagem num registry próprio com tag fixa; (d) trocar por outro S3 compatível (ex.: SeaweedFS, Garage) e rever os passos 3, 5 e 6 (o `If-None-Match` precisa ser suportado).
- **Recomendação:** (a) ou (c) — mantém MinIO e tag fixa. Depois, desmarque o `[!]` do passo 2 do plano.
- Resposta (2026-11-09, autor): o login no quay não resolve: o MinIO arquivou o projeto (abr/2026) e tirou as imagens (Docker Hub set/2026; quay 401). **Decisão: SeaweedFS com tag fixa** nos testes (e no futuro docker-compose). O `BlobStore` fica genérico "S3 compatível" (D1 mantido: AWS SDK + `url-connection-client`); o módulo pode se chamar `infra-blob-s3` em vez de `infra-blob-minio`. **Primeiro passo:** teste que confirma se o SeaweedFS honra `PUT` com `If-None-Match: *` (D3); se não honrar, parar com `[!]` e descrever alternativas antes de seguir. Replanejar a subetapa (plano antigo era de MinIO).

### [BLOQUEIO] E3-S05 — 2026-11-09 — 3 falhas seguidas
Último motivo: nenhum commit feito e nenhum passo fechado no plano
Logs: `estado/logs/` (arquivos *-E3-S05-*.json). Para destravar: ajuste o plano `estado/planos/E3-S05.md`
ou responda aqui, e troque o status no ROADMAP de `[!]` para `[~]`.

### [HUMANO] E2-S12 — 2026-11-09 — smoke em homologação: distNSU, consChNFe, ciência, autXML
- **O que é preciso:** confirmar contra o AN de **homologação** (tpAmb=2, sem valor fiscal) o que hoje só foi testado com
  WireMock e está marcado [VALIDAR]. É manual, fora do CI e com flag explícita (inv. 14). Só você pode fazer: exige A1
  ICP-Brasil **real** (em homologação não existe certificado "de teste") e notas emitidas em homologação.
- **Pré-requisitos que ainda faltam:**
  1. **E2-S13 mergeada** (está `[r]`): sem ela não há envio da ciência.
  2. **Não existe como rodar o adapter:** ainda não há `app-*` nem harness. Falta também o **truststore ICP-Brasil**
     decidido na E2-S10 (opção c), que não foi implementado (não há `src/main/resources` no adapter).
  3. Certificados: A1 de uma **empresa** (destinatária, que consulta e manifesta) e A1 do **escritório** (para o `autXML`).
- **D1 — como rodar o smoke:**
  - (a) **Subetapa AUTO nova `E2-S17`** (antes da E2-S12) que cria um teste JUnit `@Tag("homologacao")`, fora da task
    `test`, só rodado por `./gradlew :adapter-nfe-nacional:smokeHomologacao -Dsmoke.homologacao=true`. Ele lê o caminho do
    `.pfx`, a senha, o CNPJ, a UF e as URLs de variáveis de ambiente. Grava o cursor em `~/fiscal-smoke/cursor-<cnpj>.txt` e
    **recusa NSU menor** (inv. 1). Salva as respostas brutas em `~/fiscal-smoke/AAAA-MM-DD/`, fora do repositório. O agente
    escreve, mas nunca roda. Prós: usa o adapter de verdade (envelope, mapper, assinatura) e pode ser repetido. Contra:
    mais uma subetapa.
  - (b) `main` avulsa num módulo `smoke`. Prós: mais simples de chamar. Contras: código fora do padrão de teste e risco
    de virar app.
  - (c) Manual com SoapUI/curl. Prós: nada a codar. Contra: **não testa o nosso código**, só a SEFAZ.
- **D2 — truststore ICP-Brasil (já decidido em c, falta implementar):** (a) incluir na E2-S17 o arquivo das ACs públicas
  do ITI, versionado, com o caminho sobrescrito por variável de ambiente; (b) você baixa e aponta pela variável só no smoke.
- **D3 — de onde vêm as notas de teste:** (a) **emitir em homologação** pelo ERP/emissor de uma empresa sua, com
  `dest` = CNPJ da empresa-teste e `autXML` = CNPJ do escritório (o emitente precisa estar habilitado para homologação na
  SEFAZ da UF dele, [conferir]); (b) pedir a um parceiro que emita em homologação; (c) só `distNSU` vazio (`137`). Contra
  de (c): valida pouco, sem `138`, ciência nem `autXML`.
- **Recomendação:** D1 (a) · D2 (a) · D3 (a). Se faltar o A1 do escritório, faça o smoke parcial e deixe o `autXML` para
  depois (ADR-019 fica "validar em homologação").
- **Roteiro (depois da E2-S13 e da E2-S17 mergeadas):**
  1. **URLs de homologação:** confira no portal, em [Relação de Serviços Web](https://www.nfe.fazenda.gov.br/portal/webServices.aspx),
     as do Ambiente Nacional para `NFeDistribuicaoDFe` e `NFeRecepcaoEvento4`. A pista atual é
     `https://hom1.nfe.fazenda.gov.br/NFeDistribuicaoDFe/NFeDistribuicaoDFe.asmx` e `.../NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx`
     [conferir; FONTES item 9 tem `hom.` de 2014].
  2. **WSDL** (com mTLS): `read -s PFX_SENHA; curl --cert-type P12 --cert "empresa.pfx:$PFX_SENHA" "<URL>?wsdl" -o ~/fiscal-smoke/recepcao.wsdl`.
     Confira a **action**: `nfeRecepcaoEvento` ou `nfeRecepcaoEventoNF` (DÚVIDA E2-S13 acima). Confira também se o
     `nfeDadosMsg` vai direto no `Body` (DA-41). Faça o mesmo com o WSDL da distribuição (`nfeDistDFeInteresse`).
  3. **Truststore:** baixe as ACs no [repositório da ICP-Brasil (ITI)](https://www.gov.br/iti/pt-br/assuntos/repositorio)
     e confira se o handshake fecha sem `PKIX path building failed`.
  4. **Emita 1–2 NF-e em homologação** (D3). Anote as chaves num arquivo **local**.
  5. **distNSU com o A1 da empresa**, a partir de `ultNSU=0`. Espere `138` com `resNFe` (ou `137`). Depois de um `137`,
     **pare por 1h**: anote se o bloqueio aparece e quanto dura (FONTES itens 1 e 2). Anote também o comportamento com
     `ultNSU < maxNSU`.
  6. **consChNFe** com a chave emitida: espere `138` com `resNFe`. São no máximo ~20 por hora; não repita à toa.
  7. **Ciência (210210)** da mesma chave: espere `135`. **Reenvie uma vez:** espere `573` → `Ok` (idempotência decidida na
     E2-S13). Uma rejeição `297`/`298` indica erro nosso de assinatura (RSA_SHA1, C14N, `Id`): guarde o bruto.
  8. **distNSU de novo** a partir do último NSU salvo (o harness lê o arquivo): espere `procNFe` + `procEventoNFe` em NSU
     posterior. Confira a classificação, o `ExtratorMetadados` e a tabela `SituacaoNfe` (DA-33).
  9. **autXML:** distNSU com o A1 do **escritório**, a partir de 0. Espere o `procNFe` da nota emitida (FONTES item 5).
  10. (Opcional) distNSU com o A1 do **emitente**: ele recebe a ciência das próprias notas? (FONTES item 4).
  11. **Registro:** não commite nenhuma resposta bruta (são XML reais com CNPJ). Abra um PR `docs/` com
      `docs/SMOKE-HOMOLOGACAO.md` (data, passos, cStat obtidos, CNPJs mascarados) e atualize `docs/FONTES-OFICIAIS.md`
      nos itens 1, 2, 4, 5, 7 e 9. Responda abaixo com cada divergência, e eu abro as subetapas de correção. Depois
      marque a E2-S12 `[x]` no ROADMAP.
- **O que conferir (checklist):** URLs e actions (item 9) · `nfeDadosMsg` no `Body` (DA-41) · truststore · `137`/`138` e
  bloqueio de ~1h · `resNFe` → ciência → `procNFe` · `135`/`573` e campos do `retEvento` (DA-47/48) · RSA_SHA1/C14N/`Id`
  (item 7) · `autXML` (item 5) · eventos para o emitente (item 4) · `SituacaoNfe` (DA-33).
- Resposta (2026-11-09, autor): D1 (a) → subetapa E2-S17 (harness de smoke) · D2 (a) truststore ICP-Brasil versionado na E2-S17 · D3 (a). O smoke em si continua com o autor, quando tiver o A1.
### [DECISAO] E4-S03 — 2026-11-09 — consentimento numa coleta sem tenant e auditoria das transições
- **O que é preciso:** o plano `planos/E4-S03.md` está pronto, mas parado no passo `[!]`. A §7.1 diz "sem consentimento vigente, não há coleta", e o consentimento é **por empresa** (tem tenant). Só que o `estado_coleta` é **por CNPJ consultado, sem tenant** (§8.1, sua decisão de 2026-10-29), e um CNPJ pode servir a dois tenants. A §6.6 pede auditoria de toda transição, mas a `auditoria` da §8 tem `tenant_id`. As escolhas menores (revogação como linha nova, matriz de transições, `BLOQUEADO_ATE` derivado) ficaram nas DA-50 a DA-52.
- **D1 — CNPJ em dois tenants: quando a coleta está consentida.**
  - (a) Basta **um** tenant ativo, com empresa ativa daquele `(cnpj, ambiente)`, ter `COLETA_DFE` vigente. Pró: a coleta é uma só, e quem revogou deixa de receber pelo catálogo e pela entrega (inv. 10/12), sem tirar a coleta do outro. Contra: o certificado em uso pode ser justamente o do tenant que revogou. Mitigação: na E4-S04 (escolha do certificado vigente), só vale certificado de tenant com consentimento.
  - (b) Exige o consentimento do tenant **dono do certificado em uso** (`estado_coleta.certificado_id`). Pró: juridicamente exato. Contra: depende da tabela `certificado` (E4-S02b, ainda não planejada); até lá, a subetapa fica parada.
  - (c) **Qualquer** revogação para a coleta. Pró: o mais conservador. Contra: um tenant derruba a coleta do outro.
  - Mecanismo nos três: função `security definer` `coleta_consentida(cnpj, ambiente, agora)`, que só devolve booleano, no mesmo papel `fiscal_roteador` da V11 (E3-S07, D3 (a)), criado de forma idempotente se a V11 ainda não estiver na base.
- **D2 — o escritório consultando o próprio CNPJ precisa de `COLETA_DFE`?** A §7.1 exige consentimento para certificado de empresa usado **por terceiro**. O escritório, com o próprio certificado, não é terceiro dele mesmo, mas a plataforma é.
  - (a) Sim, todo titular, inclusive `ESCRITORIO` (evidência = termo de adesão). Pró: regra única, sem exceção por tipo, e a trilha fica completa. Contra: um passo a mais no cadastro do escritório.
  - (b) Só empresas do tipo `EMPRESA`. Pró: menos atrito. Contra: exceção por tipo, e a coleta do `autXML` fica sem registro de finalidade.
- **D3 — onde fica a auditoria das transições de `estado_coleta`.**
  - (a) Tabela própria append-only `estado_coleta_transicao` (`fonte, cnpj_consultado, ambiente, de, para, motivo, ator, ocorrido_em`), **sem tenant**, como `ledger_tentativa`. Ela entra em `TABELAS_SEM_TENANT` do `TodaTabelaComTenantIdTemRlsTest` (guarda de RLS da §13: por isso a decisão é sua). Pró: coerente com a §8.1; uma linha por transição. Contra: a §8 não lista a tabela, e o tenant não vê a trilha direto (precisa da mesma função de tenants do CNPJ).
  - (b) `auditoria` da §8 com `tenant_id` nulo. Contra: quebra a RLS (linha sem tenant fica invisível ou exige exceção na política).
  - (c) Uma linha de `auditoria` por tenant assinante. Pró: cada tenant vê a sua. Contra: N linhas por transição, e um tenant cadastrado depois não vê o histórico.
- **Roteiro:** responda D1–D3 abaixo. Se escolher D1 (b), crie antes a linha da E4-S02b (Q1 da `[DUVIDA] E4-S02`) e ponha-a nas deps da E4-S03. Depois troque a E4-S03 de `[h]` para `[~]` no ROADMAP. Se escolher D3 (a), considere citar a tabela na §8 do CLAUDE.md (só você edita). **Atenção:** enquanto a `[DECISAO] E1-S07` (testes do `infra-postgres` sem compilar na `main`) não for resolvida, esta subetapa também não fecha "suíte verde".
- **Recomendação:** D1 (a) · D2 (a) · D3 (a).
- Resposta (2026-11-09, autor): conforme a recomendação — D1 (a), D2 (a), D3 (a).