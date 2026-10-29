## O que muda
`NfeNacionalAdapter implements SourceAdapter`, com `buscarLote` (distNSU) completo. O fluxo é: envia pelo `TransporteSoap`, **grava o envelope bruto antes de interpretar**, lê o `retDistDFeInt` com um DOM seguro (sem DOCTYPE), grava cada docZip descompactado (sha256 na chave) e devolve só metadado + `BlobRef` + `Desfecho`.
Transporte, HTTP e cStat viram `Desfecho` num ponto só (`TradutorRespostaDistDfe`, DA-14/16). `autenticar` liga o `CacheSslContext` (mTLS) ao transporte.
No domain, `BuscarLoteResultado` ganha `Optional<BlobRef> envelope`. O construtor antigo foi mantido, então nenhum chamador muda.
Testado contra WireMock: 138, 137, 656, 108, 109, cStat desconhecido, timeout, 5xx, 403, malformado, SOAP Fault, docZip corrompido e falha do BlobStore. Há também propriedade jqwik e caos.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:126` — é a guarda da inv. 1: só `Ok`/`Vazio` mudam o cursor. Em qualquer outro desfecho, o `ultNSU` da resposta é ignorado. Vale ler junto com a linha 140 (`ultNSU` < cursor → `IndisponivelDefinitivo`).
- `NfeNacionalAdapter.java:143` — o 137 avança o cursor até o `ultNSU` da resposta, sem documentos. Confirme se é a leitura certa do MOC: o 137 pode trazer `ultNSU` maior que o enviado?
- `NfeNacionalAdapter.java:113`, `:137`, `:160` — os `catch (RuntimeException)` viram `IndisponivelTemporario` **sem log**. Não perdem nada (o envelope já está guardado e nada avança), mas na operação fica impossível saber o motivo. Sugiro um WARN só com o tipo da exceção.
- `NfeNacionalAdapter.java:92` — cursor corrompido lança `IllegalArgumentException` (não ecoa o token) em vez de devolver `Desfecho`. O scheduler da Fase 4 precisa tratar isso.
- `NfeNacionalAdapter.java:156` — o CNPJ **consultado** vai para `Documento.cnpjDestinatario`. No caso `autXML`, o nome engana. A atribuição real é a inv. 10 (E3-S07).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/soap/LeitorRespostaDistDfe.java:87` — o `retDistDFeInt` é procurado em qualquer nível do `Body`, sem amarrar o nome do wrapper [VALIDAR item 9]. As features anti-XXE ficam nas linhas 102–112.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/TradutorRespostaDistDfe.java:59` — 4xx (fora 401/403/408/429) → `IndisponivelDefinitivo`, ou seja, o CNPJ cai em `ERRO_DEFINITIVO`. 3xx → temporário (DA-16).
- `domain/src/main/java/br/com/doc/ingest/domain/adapter/BuscarLoteResultado.java:27` — é a única mudança no domain.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 Não-retrocesso de NSU | `NfeNacionalAdapterPropriedadesTest.cursorNuncaRetrocedeSoOkEVazioMudamEDocumentoTemBlob`, `NfeNacionalAdapterTest.limiteAtingidoNaoAvanca`, `ultNsuMenorQueCursorEhCursorInconsistente`, `vazioComUltNsuMenorQueCursorEhCursorInconsistente` |
| 3 XML fora do core | `BuscarLoteResultadoTest` (só `BlobRef`); ArchUnit do domain verde |
| 4 Core desacoplado da origem | `TradutorRespostaDistDfeTest.desfechoNaoCarregaXmlNemCorpo` e uma linha por caso da DA-14 |
| 5 Grave primeiro | `envelopeGravadoAntesDoParse`, `falhaDoBlobStoreNaoDevolveDocumentoNemAvanca`, `falhaDoBlobStoreNoEnvelopeNaoAvanca`, `docZipDesconhecidoGuardadoByteAByte`, `NfeNacionalAdapterPropriedadesTest.falhaEmQualquerGravacaoNaoAvancaNemDevolveDocumento` |
| 7 Blob imutável | `ChavesBlobPullTest.chaveContemSha256`, `NfeNacionalAdapterTest.reconsultaMesmoLoteEhIdempotente` |
| 13 Sem segredo/XML em log | `LeitorRespostaDistDfeTest.respostaMalformadaNaoVazaConteudo`, `FornecedorTransporteMtlsTest.pfxInvalidoEhCertificadoInvalidoSemSenhaNaMensagem` |
| 14 Nunca SEFAZ real | só WireMock em localhost; `SemUrlDeSefazNoCodigoTest` verde |

## Decisões do agente nesta subetapa
- DA-13: layout das chaves `raw/pull/{fonte}/{ambiente}/{cnpj}/...` com sha256, sem tenant e sem gzip (há uma DÚVIDA aberta em PARA-VOCE).
- DA-14: tabela transporte/HTTP/leitura → `Desfecho`. Doczip corrompido ou falha do BlobStore derrubam o lote inteiro, sem avançar. `Cursor` = `ultNSU` em decimal.
- DA-15: `Optional<BlobRef> envelope` no `BuscarLoteResultado`. `adapter-fake` entra como dependência de teste.
- DA-16: 3xx → `IndisponivelTemporario`. API do tradutor.
- DA-17: `modelo = "55"` fixo.

## O que NÃO foi feito
- `buscarUm`/`consNSU`/`consChNFe` (E2-S09): devolvem `IndisponivelDefinitivo` por enquanto. `manifestar` fica para a E2-S13. A suíte de contrato fake × real fica para a E2-S08.
- Extração da chave de acesso e dos CNPJs do XML: `Documento.chave` fica vazio. Roteamento pelo conteúdo fica para a E3-S07.
- As chaves de blob ainda não têm prefixo de tenant nem gzip (DA-13). MinIO fica para a E3-S05.
- Falha de handshake TLS ainda não se distingue de falha de conexão: as duas viram temporário. Timeout de leitura do corpo inteiro, também fora.
- Backoff, bloqueio, `estado_coleta` e `ledger_tentativa` ficam para a Fase 3/4. Também não há smoke em homologação.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
