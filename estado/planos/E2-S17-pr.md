## O que muda
- `TruststoreIcpBrasil` (src/main do adapter): ACs raiz ICP-Brasil v5/v10/v11/v12 do ITI versionadas no classpath com `indice.txt` (sha256) e `LEIAME.md`; sobrescrita por `FISCAL_TRUSTSTORE` (diretório com índice ou PKCS12 de senha vazia — DA-63).
- Harness do smoke manual (todo em `src/test`, pacote `nfe.smoke`): `ConfiguracaoSmoke`, `CursorLocal` (recusa NSU menor), `BlobStoreEmDiscoSmoke` (fora de repositório git), `RoteiroSmoke` e `SmokeHomologacaoTest` (`@Tag("homologacao")` + `-Dsmoke.homologacao=true`).
- Task `:adapter-nfe-nacional:smokeHomologacao`, fora de `test`/`check`; `test` passa a excluir a tag `homologacao`.
- `docs/SMOKE-HOMOLOGACAO.md`: comando, variáveis, onde ficam cursor e brutos. **Nada foi rodado contra a SEFAZ.**

## Olhar com atenção
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/smoke/SmokeHomologacaoTest.java:93` — `distNsuDepoisDaCiencia` consulta de novo **no mesmo processo** logo depois de `distNsu`. Se o primeiro terminou em `137`/`ultNSU == maxNSU`, a segunda cai na janela de ~1h → provável `656` no CNPJ de homologação. Nada local impede reexecutar a task dentro da 1h. Só use `FISCAL_SMOKE_MANIFESTAR=true` quando fizer sentido, ou peça uma subetapa curta com `bloqueado-ate-<cnpj>.txt`.
- `adapter-nfe-nacional/build.gradle.kts:35` — `excludeTags` / task `smokeHomologacao`: é o que garante a inv. 14. Conferido: o `test` não executa a classe e, sem a flag, a task marca 4/4 como *skipped*.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/cert/TruststoreIcpBrasil.java:46` — sobrescrita por PKCS12 de senha vazia (DA-63): aceita o arquivo sem conferir sha256 nem se são só certificados de CA (diferente do caminho por diretório).
- `adapter-nfe-nacional/src/main/resources/icp-brasil/LEIAME.md:1` — vale conferir os sha256 com o site do ITI (`acraiz.icpbrasil.gov.br`). Só raízes; as intermediárias ficam como [VALIDAR no smoke].
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/smoke/BlobStoreEmDiscoSmoke.java:26` — proteção “pasta dentro de repositório” (procura `.git` nos ancestrais), que impede XML real de cair num commit. A mensagem da linha 47 inclui a chave com o CNPJ completo (só no console local).
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/smoke/ConfiguracaoSmoke.java:16` — senha guardada como `String` (não dá para zerar); aceitável porque vem de `System.getenv()`.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — não-retrocesso de NSU | `CursorLocalTest`, `CursorLocalPropriedadesTest` (jqwik), `RoteiroSmokeTest.respostaComUltNsuMenorQueOSalvoAbortaSemGravar` |
| 3 — XML fora do core | `RoteiroSmokeTest.saidaNaoVazaXmlNemCnpj` (saída sem `<`); brutos só no `BlobStoreEmDiscoSmoke` |
| 7 — blob imutável | `BlobStoreEmDiscoSmokeTest` (mesma chave + conteúdo diferente → recusa) |
| 13 — sem segredo em log/exceção | `ConfiguracaoSmokeTest` (erro nomeia a variável, nunca o valor; `toString` mascara), `TruststoreIcpBrasilTest` (mensagens só com o nome do arquivo) |
| 14 — nunca SEFAZ real em teste automatizado | `excludeTags("homologacao")` + `@EnabledIfSystemProperty`; `ConfiguracaoSmokeTest.recusaAmbienteDeProducao`; `SemUrlDeSefazNoCodigoTest` verde |

## Decisões do agente nesta subetapa
- DA-63: formato do truststore (diretório com `indice.txt` sha256 ou PKCS12 de senha vazia) e nomes das variáveis `FISCAL_SMOKE_*` / `FISCAL_TRUSTSTORE`.

## O que NÃO foi feito
- O smoke não foi rodado (é a E2-S12, HUMANO) e nenhuma URL da SEFAZ foi acessada.
- Não há proteção local contra consultar de novo dentro da janela de ~1h depois de `137` (ver "Olhar com atenção").
- ACs intermediárias, CRL/OCSP e atualização automática do truststore ficam fora (plano). O `TruststoreIcpBrasil` não está ligado a nenhum app nem ao cofre.
- A task gradle não tem teste automatizado: a exclusão foi comprovada rodando as tasks.
- Pequenas limpezas não feitas: `ConfiguracaoSmoke.truststore()` não é usado, `credencial.limpar()` não é chamado e `BlobStoreEmDiscoSmoke.ler` só procura na pasta do dia.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
