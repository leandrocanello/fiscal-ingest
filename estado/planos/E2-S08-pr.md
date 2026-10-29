## O que muda
Suíte abstrata `SourceAdapterContrato` (8 casos, em `adapter-fake/src/testFixtures`, DA-18) que fixa o que o core pode
esperar de qualquer `SourceAdapter`. Roda contra o `FakeSourceAdapter` e contra o `NfeNacionalAdapter` com WireMock, 8/8 verdes nos dois.
O fake agora tem cursor de verdade (`ultNsu` decimal, filtra NSU > cursor, DA-19) e `programarDesfecho` para simular falhas.
Os montadores de resposta distDFe dos testes nfe foram extraídos para `RespostasDistDfeDeTeste`. `domain/` não foi tocado.

## Olhar com atenção
- `adapter-fake/src/main/java/br/com/doc/ingest/adapter/fake/FakeSourceAdapter.java:81` — **muda o comportamento do fake**: antes devolvia todos os documentos pré-carregados em toda chamada e repetia o cursor de entrada; agora filtra por NSU > cursor e devolve `proximoCursor` = `ultNsu`. Testes antigos passaram sem alteração, mas quem usar o fake no core (E3/E4) depende disso.
- `adapter-fake/src/testFixtures/java/br/com/doc/ingest/adapter/contrato/SourceAdapterContrato.java:35` — o gancho `lerBlob` existe porque o formato de `Documento.blobRef` **diverge**: o fake usa `chave@sha` (`FakeSourceAdapterContratoTest.java:55`), o nfe só a chave (`NfeNacionalAdapterContratoTest.java:81` tira o sha do nome do arquivo). O contrato esconde a divergência; o core não vai conseguir ler o blob de forma uniforme. Ver PARA-VOCE (DUVIDA E2-S08).
- `SourceAdapterContrato.java:108` — `vazioNaoRetrocede` só verifica `ultNsu >= 0` partindo do cursor inicial: quase tautológico. Não há caso de Vazio/falha partindo de cursor > 0 no contrato (o fake cobre no próprio teste; o nfe não).
- `SourceAdapterContrato.java:32` — o gancho `blobs()` é abstrato mas nenhum caso o usa.
- `NfeNacionalAdapterContratoTest.java:93-97` — a retomada depende de prioridade de stub + `containing("<ultNSU>000000000000000</ultNSU>")`; se o formato do envelope de requisição mudar, o stub cai no 137 e o teste ainda passa em parte. Vale conferir se a leitura está clara.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — não-retrocesso de NSU | `SourceAdapterContrato.retomadaComProximoCursorNaoReentregaNemRetrocede`, `falhaNaoAvancaCursor` (nos 2 adapters); `FakeSourceAdapterTest.retomadaPeloProximoCursorSoTrazNsuMaior`, `desfechoProgramadoNaoAvanca` |
| 3 — XML fora do core | `SourceAdapterContrato.todoDocumentoTemBlobLegivel` (documento só com `blobRef`, bytes só no `BlobStore`) |
| 4 — core desacoplado | `SourceAdapterContrato` só importa domain/JUnit/AssertJ (conferido por grep); ArchUnit do domain verde |
| 5 — grave primeiro | `todoDocumentoTemBlobLegivel` (nenhum documento sem blob gravado) |
| 14 — nunca SEFAZ real | `NfeNacionalAdapterContratoTest` usa WireMock `dynamicPort` em `localhost`; `SemUrlDeSefazNoCodigoTest` verde |

## Decisões do agente nesta subetapa
- DA-18: suíte de contrato em `testFixtures` do `adapter-fake` (plugin `java-test-fixtures`).
- DA-19: cursor do fake = `ultNsu` decimal (`""` = 0); `Vazio` devolve cursor e NSU de entrada.

## O que NÃO foi feito
- TDD parcial no contrato: os 5 casos de `6bf0ed7` passaram de primeira no fake e toda a suíte passou de primeira no nfe (o comportamento já vinha da DA-14/DA-19). Nenhum desses casos foi visto falhar por comportamento. Só o `buscarUm` foi visto falhar, trocando a asserção temporariamente.
- Conferir no adapter nfe se o NSU de cada docZip está em `(entrada, ultNSU]`: pede decisão; o contrato só exige NSU ≤ `ultNsu`.
- `buscarUm`/`consNSU`/`consChNFe` reais (E2-S09); `manifestar` no contrato (E2-S13).
- Unificar o formato de `Documento.blobRef` (ver PARA-VOCE).
- Alinhar `docs/ARCHITECTURE.md` §5.5/5.6 (doc protegido).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
