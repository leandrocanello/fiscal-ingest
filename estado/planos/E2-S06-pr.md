## O que muda
No `adapter-nfe-nacional`: `TipoAmbiente` (tpAmb 1/2), ordem de schema explícita no `DistDfeInt`, `EnvelopeDistDfe` que monta o
`distDFeInt` por ultNSU (15 dígitos) e o envelope SOAP 1.2 completo, e `ContratoDistDfe` com as constantes do protocolo
(marcadas [VALIDAR item 9], DA-7). Nova porta interna `TransporteSoap` + `TransporteHttpJdk` (`java.net.http`, `HttpClient`
injetado) que devolve `RespostaHttp(status, corpo)` ou `FalhaTransporte(TIMEOUT|CONEXAO|RESPOSTA_GRANDE_DEMAIS)`, sem interpretar nada.
Nenhuma mudança no `domain`.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/soap/ContratoDistDfe.java:9-13` — namespace do WSDL, action e versão `1.01` vieram "de memória" (DA-7). Conferir contra o WSDL oficial; erro aqui só aparece no smoke E2-S12.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/transporte/TransporteHttpJdk.java:32` e `:39` — o `timeout` do `HttpRequest` só vale até os cabeçalhos; a leitura do corpo não tem prazo. Decidir se isso entra na E2-S07/E2-S10.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/soap/EnvelopeDistDfe.java:34` — `String.format` sem `Locale.ROOT`; em locale com outros dígitos o ultNSU sairia errado (risco baixo, mas é o campo da inv. 1).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/soap/EnvelopeDistDfe.java:49-57` — envelope por concatenação de strings; seguro hoje (miolo escapado pelo JAXB, resto constante), mas não deixe entrar valor externo aqui.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/transporte/ResultadoTransporte.java:9-34` — record com `byte[]`: cópia defensiva, `equals` por conteúdo e `toString` sem bytes (inv. 13). Note que `corpo()` copia a cada chamada (até 16 MiB).

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3/4 — XML e transporte fora do core | `git diff main -- domain/` vazio; ArchUnit existente verde; `status5xxE4xxVoltamComoRespostaHttp` (transporte não decide) |
| 5 — grave primeiro (preparação) | `TransporteHttpJdkTest.respostaDevolveCorpoByteAByte` |
| 13 — sem conteúdo em log/exceção | `falhaNaoExpoeCorpoNemEnvelope`, `toStringDaRespostaNaoMostraCorpo` |
| 14 — nunca SEFAZ real | todos os testes em WireMock localhost; `SemUrlDeSefazNoCodigoTest.nenhumaUrlDeSefazNoCodigo` |

## Decisões do agente nesta subetapa
- DA-7: constantes do protocolo de memória, isoladas em `ContratoDistDfe` e marcadas [VALIDAR item 9]; sem `cUFAutor`; nenhuma URL no código; teto de resposta padrão 16 MiB.

## O que NÃO foi feito
- Desembrulhar a resposta SOAP, parse seguro do `retDistDFeInt`, mapear para `Desfecho`, gravar no `BlobStore` e `buscarLote` — E2-S07.
- `consNSU`/`consChNFe` (E2-S09); mTLS/`SSLContext`/`Credencial` (E2-S10); URL real do endpoint (smoke E2-S12).
- Timeout de leitura do corpo e `Locale.ROOT` no ultNSU — achados do revisor, não bloqueantes, ficam para a E2-S07.
- `ambiente` ainda não está no `ContextoConsulta` do domain (anotado para E2-S07/E3).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
