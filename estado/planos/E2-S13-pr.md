## O que muda
`NfeNacionalAdapter.manifestar` envia a Ciência (210210, assinada na E2-S11) ao `NFeRecepcaoEvento` por mTLS, grava a resposta bruta antes de interpretar e traduz `retEnvEvento`/`retEvento` para `Desfecho` (decisões D1–D3).
No domínio entram só `ManifestarResultado` e o método default `SourceAdapter.manifestar`. A lógica fica em classes novas de `evento/` e `mapping/`.
Fecha o achado A11 e a fase 2E, exceto o smoke (E2-S12). Testado só contra WireMock: **nada aqui foi conferido na SEFAZ**.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:98` — deve dar conflito com a main, que está 81 commits à frente. A resolução certa é `Capacidades(true, true, envioManifestacao != null, false)`. O merge não foi simulado: só comparei os arquivos por leitura.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/EnvioManifestacao.java:77` — **o evento assinado não é guardado**, só a resposta. Se a resposta se perde por timeout, não fica cópia do que foi declarado em nome do destinatário (§1.5, §3.3, §14). O plano só pedia a resposta; decisão sua em PARA-VOCE ([DECISAO] E2-S13-EVENTO).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/ContratoRecepcaoEvento.java:14` — a action pode ser `nfeRecepcaoEventoNF` no AN ([DUVIDA] E2-S13, relato de fórum). Se estiver errada, toda manifestação falha. Conferir no WSDL antes do smoke.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/EnvelopeEnvEvento.java:27` — `nfeDadosMsg` direto no `Body`, sem prefixo, escolhido com fonte secundária (DA-41). Os bytes do evento são concatenados para não mudar o digest.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/CStatEventoMapper.java:40` e `:56-62` — `213`, `215`, `225`, `243` são de memória e `596` só por indício (DA-43). A faixa 28x (certificado) cai no fallback de 5 min, então certificado vencido seria retentado sem parar, embora o §3.2 peça `ErroAutenticacao`/`IndisponivelDefinitivo`.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/TradutorRespostaEvento.java:75` — `135`/`573` sem chave, tipo ou sequência ficam em temporário (DA-44). `nSeqEvento` é comparado como texto: `01` diverge de `1`. Se a SEFAZ responder assim, a ciência aceita nunca vira `Ok`.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/EnvioManifestacao.java:75` — aqui `IndisponivelDefinitivo` vale para uma nota ou um certificado, não para o CNPJ. Pelo §6.7 esse desfecho leva a `ERRO_DEFINITIVO`: a E4 não pode reaproveitar esse tratamento, ou uma nota cancelada para a coleta (DA-43, DA-48).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:107` — a chave privada aberta fica em memória durante a vida do adapter (D3). Ela só é aberta com o endpoint de evento configurado.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 — XML fora do core | `ManifestarResultadoTest`, `ArquiteturaDominioTest` |
| 4 — core desacoplado da origem | `CStatEventoMapperTest`, `TradutorRespostaEventoTest`, `NfeNacionalAdapterManifestarTest.falhaAoAssinarEhDefinitivoSemHttp` |
| 5 — grave primeiro | `NfeNacionalAdapterManifestarTest.{gravaRespostaBrutaAntesDeInterpretar, falhaDoBlobStoreNaoViraOk}`, `NfeNacionalAdapterManifestarPropriedadesTest` |
| 7 — blob imutável | `ChavesBlobPullTest.chaveDoEnvioDeEventoIncluiSha` |
| 13 — sem segredo em log ou exceção | `NfeNacionalAdapterManifestarTest.desfechosELogsSemXmlNemSegredo`, `LeitorRetEnvEventoTest.toStringSemConteudo` |
| 14 — nunca SEFAZ real | `SemUrlDeSefazNoCodigoTest`; só WireMock e fakes, com pfx gerado no teste |

Inv. 1 e 2 não foram tocadas: `manifestar` não lê nem altera o cursor. Suíte da branch: 552 testes, 0 falhas (sem a main integrada).

## Decisões do agente nesta subetapa
- DA-40: `FakeSourceAdapter` segue com `fazManifestacao = true` e `manifestar` vazio (incoerência conhecida).
- DA-41: `nfeDadosMsg` direto no `Body`, envelope só com namespace padrão.
- DA-42: no `retEvento`, só `cStat` é obrigatório; mais de um `retEvento` é leitura inválida.
- DA-43: listas explícitas de cStat, o resto cai no fallback temporário; `575` é definitivo, não `ErroAutenticacao`.
- DA-44: `Ok` exige chave, tipo e sequência iguais aos enviados; rejeição sem identificação é aceita.
- DA-45: construtor novo com `endpointEvento`; layout `.../evento/{aaaa}/{mm}/{dd}/{sha256}.retorno.xml`; `idLote` vem do relógio.
- DA-46: DV da chave conferido em `EnvioManifestacao`; ordem credencial → chave → tipo.
- DA-47: `toString` do `BlobRef` traz o CNPJ em claro; olhar ao criar os logs do fetcher.
- DA-48: falha ao assinar vira `IndisponivelDefinitivo` com motivo fixo, capturando `RuntimeException`.

## O que NÃO foi feito
- **Smoke em homologação e WSDL:** nada foi conferido na SEFAZ. Action, envelope e parte dos cStat seguem [VALIDAR] (E2-S12).
- **Integração com a main:** nem o merge nem a suíte integrada foram executados nas revisões.
- **Eventos 210200/210220/210240 e `nSeqEvento` > 1:** depois do smoke.
- **Quem decide manifestar** (consentimento `CIENCIA_AUTOMATICA`, auditoria, agendamento e retentativa): E4. Nada aqui chama `manifestar` ainda.
- **Certificado do titular e cadeia ICP-Brasil:** cofre (E4-S02).
- **Assinatura do `retEvento` e limites do parser para push:** Fase 5.
- **`RuntimeException` do transporte:** não é capturada em `manifestar`, igual ao `buscarLote`. Falha de serialização também não tem teste: não dá para provocá-la sem injetar dependência (DA-48).
- **`docs/ARCHITECTURE.md` §5.5–5.6:** PR `docs/` seu (D3).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
