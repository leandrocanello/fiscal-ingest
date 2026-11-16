## O que muda
O `manifestar` agora grava os bytes exatos do evento assinado no `BlobStore` (`.../evento/{aaaa}/{mm}/{dd}/{sha256}.assinado.xml`)
**antes** de chamar a SEFAZ. Se essa gravação falhar, nada é enviado e o desfecho é `IndisponivelTemporario(5 min)`.
O `ManifestarResultado` ganha `Optional<BlobRef> eventoAssinado` (DA-64). Ele fica preenchido mesmo em timeout ou quando a
resposta não pôde ser gravada, para a auditoria da E4 apontar o que foi declarado em nome do destinatário.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/EnvioManifestacao.java:77-88` — este é o centro da mudança: a ordem gravar→enviar e o `catch` que impede o envio. Confira se nada sai pela rede antes da linha 88.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/EnvioManifestacao.java:80` — a data da chave vem de um `relogio.instant()` separado do `dhEvento` assinado. Em produção, os dois podem divergir na virada do dia em UTC (só muda a pasta; o sha garante a unicidade).
- `domain/src/main/java/br/com/doc/ingest/domain/adapter/ManifestarResultado.java:14-26` — muda a API do domínio: há um componente novo e um construtor de 2 argumentos para compatibilidade. O Javadoc diz "vazio quando nada foi assinado", mas o campo também fica vazio quando a assinatura deu certo e a gravação falhou. Vale ajustar o texto?
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapterManifestarPropriedadesTest.java:151` — é a asserção feita **dentro** do transporte fake que prova a inv. 5 no momento do envio. Foi a mutação manual "gravar depois" que a derrubou.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapterManifestarTest.java:539` — o timeout usa um atraso real de 2 s no WireMock contra um limite de 200 ms. Pode ser lento ou instável em CI carregado.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 5 — grave primeiro | `NfeNacionalAdapterManifestarTest.{eventoAssinadoGravadoAntesDoEnvio, falhaAoGravarEventoNaoEnvia, timeoutAindaDeixaEventoGuardado, falhaAoGravarRespostaMantemEventoGuardado}` · jqwik `eventoGuardadoAntesDeQualquerEnvio` |
| 7 — blob imutável | `ChavesBlobPullTest.chaveDoEventoAssinadoIncluiShaEDifereDoRetorno` |
| 3 — XML fora do core | `ManifestarResultadoTest.eventoAssinadoSoComoBlobRef` · `ArquiteturaDominioTest` |
| 4 — sem cStat/HTTP no core | `falhaAoGravarEventoNaoEnvia` (a falha vira `Desfecho`) |
| 13 — sem segredo em log/exceção | `falhaAoGravarEventoNaoEnvia` (`toString` sem texto da exceção) · `desfechosELogsSemXmlNemSegredo` |
| 14 — nunca SEFAZ real | só WireMock/fake · `SemUrlDeSefazNoCodigoTest` |

## Decisões do agente nesta subetapa
- DA-64: `ManifestarResultado` leva o `BlobRef` do evento assinado (decidida no planejamento, 2026-11-13).

## O que NÃO foi feito
- Não foi visto falhando o teste `falhaAoGravarEventoNaoEnvia`: o `catch` entrou no commit anterior, junto com a gravação. A mutação manual cobre a ordem gravar→enviar.
- Não foi renomeado `ChavesBlobPull.envioEvento` (ele guarda o retorno). Fica para uma refatoração própria.
- Não foram feitos auditoria de quem manifestou, consentimento `CIENCIA_AUTOMATICA` nem retentativa: ficam para a E4.
- Não foi capturada a `RuntimeException` do transporte no `manifestar`. Fica para uma subetapa própria.
- O `envEvento` SOAP inteiro não é guardado, só o evento assinado (o envelope pode ser remontado a partir dele).
- Não houve smoke em homologação: fica para a E2-S12.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
