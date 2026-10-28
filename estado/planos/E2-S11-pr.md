## O que muda
Parte "offline" do `manifestar` (fase 2E): monta o evento de Ciência da Operação (210210) e assina o `infEvento` com
XML-DSig enveloped (padrão RSA-SHA1, conforme MOC 7.0 Tab. 4-2; RSA-SHA256 disponível por parâmetro), devolvendo bytes
UTF-8 prontos para o `envEvento`. No domínio entra só o `enum TipoManifestacao`, sem código da SEFAZ. Também entra uma
fábrica de DOM sem XXE (`FabricaDocumentoSeguro`). Nada é enviado: o envio SOAP fica para a próxima subetapa.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/ContratoEvento.java:19-41` — constantes do leiaute (`cOrgao` 91, `versao`/`verEvento` 1.00, `descEvento` sem acento), tiradas do MOC 7.0 p. 100/113. Um erro aqui só aparece como rejeição da SEFAZ; confira contra o PDF (marcado [VALIDAR item 6/7]).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/AssinadorXml.java:42-56` — o coração da assinatura: Reference `#Id`, transforms enveloped + C14N inclusivo, `KeyInfo` só com o certificado final (EndCertOnly), `Signature` pendurada em `evento`.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/AssinadorXml.java:57-60` — `catch (Exception)` sem encadear causa (inv. 13). A mensagem leva só o nome simples da classe da exceção. Avalie se a perda de diagnóstico é aceitável.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/MontadorEvento.java:52-53` — `dhEvento` vem do `Clock` injetado, no fuso informado, truncado em segundos (`-03:00`). Quem chamar em produção precisa passar `America/Sao_Paulo`.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/evento/AssinadorXmlTest.java:81` — a validação nos testes desliga `secureValidation` porque o JDK 21 recusa SHA-1. Isso está só no teste, mas vai virar decisão quando o adapter precisar **validar** assinaturas (retEvento, push na Fase 5).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/FabricaDocumentoSeguro.java:46-56` — sem XXE, porém **sem limite de tamanho/profundidade**. Hoje só relê bytes gerados pelo próprio adapter; antes de servir à porta push, mover para um pacote comum e adicionar limites.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/cert/GeradorPfxDeTeste.java:41` — passou a ser `public` (só código de teste) para ser reusado pelo pacote `evento`.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 — XML fora do core | `ArquiteturaDominioTest` (domínio sem XML); bytes só em `EventoManifestacao`, dentro do adapter |
| 4 — core desacoplado da origem | `TipoManifestacaoTest` (enum sem código) + `ContratoEventoTest.mapeiaCienciaPara210210` (mapeamento no adapter) |
| 13 — nada de segredo em log/exceção | `AssinadorXmlTest.falhaNaoExpoeChaveNemSenha`; `FabricaDocumentoSeguroTest.recusaDoctypeEEntidadeExterna` (sem causa, sem conteúdo) |
| 14 — nunca SEFAZ real | nenhum envio; pfx gerado em memória por `GeradorPfxDeTeste`; nenhum pfx/pem versionado |
| §13 — parser sem XXE | `FabricaDocumentoSeguroTest` (entidade externa e DTD externo recusados, arquivo temporário não lido) |

Propriedades jqwik: `IdEventoPropriedadesTest`, `AssinadorXmlPropriedadesTest` (sempre valida; alterar qualquer texto do `infEvento` sempre invalida).
Suíte: `./gradlew test` verde (264 testes, 0 falhas).

## Decisões do agente nesta subetapa
- DA-12: reusar `soap.TipoAmbiente` em vez de criar um `Ambiente` novo; `Clock.fixed` nos testes em vez de tornar `RelogioManual` público.
- Decisões do autor aplicadas (PARA-VOCE 2026-10-27): D1 `AlgoritmoAssinatura` com padrão RSA_SHA1; D2 leiaute do evento.

## O que NÃO foi feito
- Envio SOAP ao `RecepcaoEvento` (`envEvento`/`idLote`), mapeamento do `retEvento` para `Desfecho`, `SourceAdapter.manifestar` + `ManifestarResultado` (A11): dependem do transporte. O plano chama a subetapa de `E2-S11b`, e a resposta do autor, de `E2-S13`; alinhar o nome no ROADMAP.
- Eventos 210200/210220/210240 (inclui `xJust`): `ContratoEvento` lança `IllegalArgumentException` para eles.
- Conferência de que o CNPJ autor é o titular do certificado e do DV da chave: fica para o `manifestar`.
- Consentimento `CIENCIA_AUTOMATICA`, preferência e auditoria de quem manifestou: E4.
- Validação da cadeia ICP-Brasil do certificado assinante: E4-S02.
- Nada foi conferido na SEFAZ: o leiaute e o algoritmo só serão confirmados no smoke de homologação (E2-S12).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
