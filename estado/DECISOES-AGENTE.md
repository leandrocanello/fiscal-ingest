# Decisões tomadas pelo agente

> Escolhas pequenas e reversíveis que o agente fez sozinho, para você revisar.
> Se discordar, responda abaixo da entrada; se a decisão for recorrente, transforme em ADR.
>
> Formato: `### DA-<n> · AAAA-MM-DD · <ID>` · Contexto · Opções · Escolha · Como desfazer

### DA-1 · 2026-10-21 · E1-S04
**Contexto:** a regra ArchUnit do domínio precisa morar em algum lugar sem sujar o `domain`.
**Opções:** (a) `testImplementation` no próprio `domain`; (b) módulo `arch-tests` separado que importa todos os módulos.
**Escolha:** (a) — o `domain` continua sem dependência de produção; um módulo separado só vale quando houver regras para vários módulos.
**Como desfazer:** mover `domain/src/test/.../arquitetura/` para um módulo novo e tirar o `testImplementation` do `domain/build.gradle.kts`.

### DA-2 · 2026-10-21 · E1-S05
**Contexto:** jqwik (ADR-026) precisa entrar no build; o PR da E1-S04 (ainda não mergeado) altera `domain/build.gradle.kts`.
**Opções:** (a) `testImplementation` no `subprojects` do `build.gradle.kts` raiz; (b) só no `domain/build.gradle.kts`.
**Escolha:** (a) — o ADR-026 vale para todos os módulos (adapters e infra também terão propriedades) e evita conflito de merge com a E1-S04. Se o JUnit Platform precisar de alinhamento, usar `junit-bom` no mesmo bloco.
**Como desfazer:** mover a linha do jqwik (e o BOM, se houver) para o `build.gradle.kts` de cada módulo.

### DA-3 · 2026-10-21 · E1-S05
**Contexto:** `TokenBucket` aceita intervalo de recarga < 1 ms, mas calcula com `toMillis()` → divisão por zero (`ArithmeticException`) no primeiro `tentarConsumir` após esgotar.
**Opções:** (a) rejeitar no construtor intervalo < 1 ms; (b) passar a calcular em nanos; (c) só restringir o gerador do teste.
**Escolha:** (a) — falha cedo e clara; nenhum limite real da SEFAZ é sub-milissegundo. Mudança de 1 linha com teste.
**Como desfazer:** remover a validação e o teste `intervaloSubMilissegundoRejeitado`; se preferir (b), trocar `toMillis` por `toNanos` nos dois cálculos.

### DA-4 · 2026-10-23 · E2-S04
**Contexto:** a CLAUDE.md §9 pede a porta `BlobStore` no domain "com implementação em memória nos testes", mas não diz em que módulo ela mora; a E2-S07 (adapter-nfe-nacional) vai precisar dela nos testes.
**Opções:** (a) `domain/src/test` (não reutilizável por outro módulo); (b) plugin `java-test-fixtures` no domain; (c) `adapter-fake` (main), módulo que já reúne os fakes, consumido via `testImplementation(project(":adapter-fake"))`.
**Escolha:** (c) — sem plugin novo, segue o padrão do `FakeSourceAdapter` e não põe estado mutável no domain.
**Como desfazer:** mover `BlobStoreEmMemoria` (+ teste) para `domain/src/testFixtures` com o plugin `java-test-fixtures`.

### DA-5 · 2026-10-23 · E2-S04
**Contexto:** formato do `BlobRef` já decidido (chave determinística + sha256, DECISIONS.md l.284), mas o `Documento` ainda usa `Optional<String> blobRef` (auditoria A8, prevista para E3).
**Opções:** (a) trocar o campo do `Documento` agora; (b) criar só o record `BlobRef(chave, sha256)` e deixar o `Documento` para E3.
**Escolha:** (b) — mantém a subetapa pequena e não mexe em `FakeSourceAdapter`/testes existentes; A8 continua aberta.
**Como desfazer:** trocar `Optional<String>` por `Optional<BlobRef>` no `Documento` e ajustar os usos.

### DA-6 · 2026-10-23 · E2-S05
**Contexto:** o decode de `docZip` precisa de um teto de descompressão (proteção contra gzip bomb, §13) e de uma regra para `schema` desconhecido ou divergente da raiz do XML; os docs não fixam nenhum dos dois.
**Opções:** (a) teto fixo de 10 MiB por documento descompactado, configurável no construtor; schema desconhecido → classificado como `DESCONHECIDO` (bytes mantidos, nunca descartados); schema que diverge da raiz → `DocZipInvalidoException`; (b) sem teto; (c) schema desconhecido lança exceção.
**Escolha:** (a) — protege a memória sem perder documento (inv. 5: o que não se reconhece é guardado, quem decide o status no ledger é a E2-S07); divergência é sinal de resposta corrompida e não deve virar metadado errado.
**Como desfazer:** mudar a constante `LIMITE_PADRAO_BYTES` em `DocZipDecoder`; trocar o ramo `DESCONHECIDO` do `ClassificadorSchema` por exceção.

### DA-7 · 2026-10-26 · E2-S06
**Contexto:** o envelope SOAP do `NFeDistribuicaoDFe` (namespace do WSDL, operação `nfeDistDFeInteresse`, `nfeDadosMsg`, action no `Content-Type` SOAP 1.2, formato de `ultNSU` com 15 dígitos, `cUFAutor` opcional) e as URLs ainda são PENDENTE em `docs/FONTES-OFICIAIS.md` item 9. Os docs também não fixam timeouts nem teto do corpo da resposta.
**Opções:** (a) seguir os valores usuais do protocolo (de memória), isolados em uma única classe de constantes marcada [VALIDAR item 9], sem `cUFAutor`, sem nenhuma URL embutida no código (endpoint vem do construtor), timeouts e teto de resposta (16 MiB) configuráveis; (b) parar a subetapa até o autor validar o WSDL.
**Escolha:** (a). Os testes só falam com WireMock (inv. 14); um valor errado aparece no smoke (E2-S12) e se corrige em um lugar só. Parar travaria a E2-S07 sem ganho.
**Como desfazer:** editar `ContratoDistDfe` (constantes) e os padrões de `TransporteHttpJdk`; incluir `cUFAutor` em `DistDfeInt` com `propOrder`.
