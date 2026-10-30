## O que muda
Recupera na `main` código que já foi revisado, mas ficou fora dela por dois acidentes de merge:
- os 6 commits finais da E2-S05: leitor StAX seguro (XXE), `ExtratorMetadados` de resNFe/procNFe/eventos, conferência da raiz contra o schema e propriedades jqwik do decode;
- a E2-S08 (suíte de contrato do `SourceAdapter`) e a E2-S09 (`buscarUm` por consNSU e consChNFe, `ChaveAcessoNfe`), que tinham sido mergeadas em `auto/E2-S07`.

Não há código novo. Os cherry-picks são idênticos aos originais (`git range-diff`), o merge não teve resolução manual e `./gradlew test` está verde.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/XmlSeguro.java:45`: só `next()` é protegido contra DTD e entidade. Hoje o extrator usa apenas `next()`. Se alguém passar a usar `nextTag()` ou `getElementText()`, a checagem é contornada (o DTD continua desligado na fábrica, então isso é a segunda camada de defesa).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/ExtratorMetadados.java:149`: a chave é validada por regex aqui e também em `ChaveAcessoNfe.java:10`. A duplicação é conhecida; unificar fica para depois.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:104`: é aqui que a inv. 1 é garantida no lote (só Ok e Vazio mudam o cursor). Vale um olhar porque agora o pipeline é compartilhado com o `buscarUm` (`:202`) e o `buscarPorChave` (`:210`).
- `adapter-fake/src/main/java/br/com/doc/ingest/adapter/fake/FakeSourceAdapter.java:49`: `programarDesfecho` muda o fake que outras suítes usam; os testes do fake continuam verdes.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — não-retrocesso de NSU | `NfeNacionalAdapterTest` (consulta pontual não retrocede o lote), `NfeNacionalAdapterPropriedadesTest`, `SourceAdapterContrato` (falha não avança cursor) |
| 3/4 — XML e cStat fora do core | `ArquiteturaDominioTest` (ArchUnit); `domain/` sem diff |
| 5 — grave primeiro | `NfeNacionalAdapterPropriedadesTest` (envelope primeiro), `SourceAdapterContrato` (blob legível) |
| 9 — parser sem XXE (preparo para o push) | `XmlSeguroTest.xmlComDoctypeEhRejeitado` |
| 13 — sem segredo ou XML em log/exceção | `ChaveAcessoNfeTest` (`toString` mascarado); as mensagens de `DocZipInvalidoException` são fixas |
| 14 — sem SEFAZ real | só WireMock e `FakeSourceAdapter` |

## Decisões do agente nesta subetapa
- nenhuma

## O que NÃO foi feito
- Unificar `ChaveAcessoNfe` com a validação do extrator (fora do escopo; é refactor).
- Ligar o `ExtratorMetadados` ao `NfeNacionalAdapter` para preencher `Documento.chave` (próxima subetapa). Até lá, o extrator fica sem uso no código de produção.
- O plano cita `FabricaDocumentoSeguroTest`, que não existe. A cobertura de XXE equivalente está em `XmlSeguroTest`.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
