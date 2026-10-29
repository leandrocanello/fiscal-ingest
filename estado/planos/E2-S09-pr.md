## O que muda
- `NfeNacionalAdapter.buscarUm` faz o **consNSU** de verdade (antes devolvia "não suportado"); `descritor().fazPontual = true`.
- Novo `buscarPorChave(ContextoConsulta, ChaveAcessoNfe)` para o **consChNFe**. Fica fora da porta do domínio (DA-20) e usa o novo record `ChaveAcessoNfe` (44 dígitos, sem DV).
- Refactor: `consultar(...)` (envia → grava o envelope → traduz → lê) e `gravarDocZip(...)` agora são comuns ao lote e aos pontuais.
- `EnvelopeDistDfe`/`DistDfeInt` montam os grupos `consNSU` e `consChNFe`; o NSU é formatado com `Locale.ROOT`.
- O domínio não muda.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:148` — `consultar` passou a ser o único caminho do envelope bruto (inv. 5) para o lote e para os pontuais. Vale conferir que o refactor não mudou nada no `buscarLote` (os testes antigos do lote passam sem alteração).
- `NfeNacionalAdapter.java:217` — `buscarPorChave` grava a chave **pedida** no `Documento` sem conferir se o docZip devolvido é dessa chave. O consNSU confere o NSU (`:238`), mas o consChNFe não confere nada. Isso é aceitável enquanto o catálogo atribuir pelo conteúdo (inv. 10), mas o metadado pode estar errado.
- `NfeNacionalAdapter.java:228-243` — regras da DA-21: `138` com 0 ou com mais de 1 docZip, ou com NSU ≠ pedido, vira `IndisponivelTemporario(5 min)`, não `Ok`. Isso é uma decisão do agente; confira se concorda.
- `NfeNacionalAdapter.java:210` — `buscarPorChave` não confere `contexto.fonte()`, ao contrário do `buscarUm` (`:198`). Hoje o `buscarLote` também não confere.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/soap/EnvelopeDistDfe.java:36,43` — os nomes `consNSU/NSU` e `consChNFe/chNFe` são **[VALIDAR item 9]**, sem conferência no XSD. Se estiverem errados, só o smoke (E2-S12) vai mostrar.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapterTest.java` — dois testes antigos foram substituídos (`descritorSoComLote`, `buscarUmAindaNaoSuportado`), porque agora o comportamento existe.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 Não-retrocesso de NSU | `buscarUm138DevolveODocumentoDoNsu` e `consChNFeNuncaEnviaDistNsu` (corpo sem `distNSU`/`ultNSU`); `buscarUmNaoMudaOProximoBuscarLote` |
| 3 XML fora do core | `BuscarUmResultado` só com `Documento` (metadado + chave do blob); diff do `domain/` vazio |
| 4 Core desacoplado | desfechos do consNSU e do consChNFe via `TradutorRespostaDistDfe` (`um137`…`um109`, `chave632`, `chave137e656`); ArchUnit verde |
| 5 Grave primeiro | `buscarUmGravaEnvelopeAntesDoParse`, `falhaDoBlobStoreNoBuscarUmNaoDevolveDocumento`, `buscarPorChaveGravaEnvelopePrimeiro`, propriedade `buscarUmSoDevolveDocumentoNoOkComNsuPedidoEBlobGravado` |
| 7 Blob imutável | `buscarUmUsaAMesmaChaveDoLote` |
| 13 Nada de segredo em log/exceção | `umFalhasDeTransporteELeitura` e `chaveFalhasDeTransporte` (`SEGREDO-XYZ`); `ChaveAcessoNfeTest.invalidaNaoEcoaOValor` |
| 14 Nunca SEFAZ real | só WireMock em localhost e transporte fake; `SemUrlDeSefazNoCodigoTest` verde |

## Decisões do agente nesta subetapa
- DA-20: o consChNFe vira método público do adapter (`buscarPorChave`) com `ChaveAcessoNfe` no adapter; a porta do domínio fica intocada.
- DA-21: `138` pontual com 0 ou mais de 1 docZip, ou com NSU diferente do pedido, vira `IndisponivelTemporario(5 min)`, sem documento.

## O que NÃO foi feito
- cStat específicos do consChNFe (217, 236, 252, 593, 614–619, 640, 641, 653, 654): por enquanto caem no fallback `IndisponivelTemporario(5 min)`, sem teste congelado. Aguardam a DECISAO E2-S09 em PARA-VOCE.
- A chave da resposta do consChNFe não é conferida contra a pedida, porque ainda não existe `ExtratorMetadados`.
- O DV da chave de acesso não é conferido.
- O limite de ~20/h dos pontuais e a contagem conjunta consNSU/consChNFe (item 2 [VALIDAR]) ficam para o scheduler, na Fase 4.
- A consulta por chave não foi levada à porta do domínio (`ReferenciaDocumento`), por causa da DA-20.
- Achado A5 (`Vazio`/`589` no `RecuperarGapUseCase`): é regra do core e fica para quando o gap-recovery for ligado.
- Contrato da E2-S08 (`buscarUmCoerenteComCapacidades` com `fazPontual=true`): a E2-S08 não está na base, então isso precisa ser conferido no merge.
- Nenhum teste contra o XSD ou a SEFAZ (smoke na E2-S12).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
