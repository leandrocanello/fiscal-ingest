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

### DA-8 · 2026-10-27 · E2-S03
- **Contexto:** a decisão E2-S02 pede "log de alerta com o cStat bruto" no fallback, mas o `adapter-nfe-nacional` não tem dependência de log.
- **Opções:** (a) `slf4j-api` no adapter + `logback-classic` só em teste (ADR-028); (b) porta/callback de observação injetada no mapper; (c) não logar agora.
- **Escolha:** (a) — segue o ADR-028, é o mínimo, e o teste captura o WARN com `ListAppender`. Mensagem com o código truncado (≤ 16 chars) e sem `<`, para não vazar XML (inv. 13).
- **Como desfazer:** remover as duas linhas do `adapter-nfe-nacional/build.gradle.kts`, o `Logger` do `CStatMapper` e o teste `fallbackLogaSoOCStatBruto`.

### DA-9 · 2026-10-27 · E2-S10
- **Contexto:** o plano da E2-S10 precisa gerar `.pfx` sintéticos (moderno e legado), carregar o legado e fazer cache de `SSLContext`; os docs citam BouncyCastle (§3.5) e "LRU/TTL" (§7.4), mas não fixam lib nem forma.
- **Opções:** (a) `bcpkix` só em `testImplementation` para gerar os pfx; `bcprov` em `implementation` **só se** o JDK 21 recusar o pfx legado (o teste decide), sem provider global; cache LRU+TTL próprio com `Clock` injetado; carregador exige exatamente uma chave privada no pfx (mais de uma = `AMBIGUO`); (b) Caffeine para o cache; (c) gerar pfx com `keytool` via processo externo.
- **Escolha:** (a) — sem dependência nova em produção se não for preciso; cache pequeno e testável com relógio manual; pfx com duas chaves não tem titular claro.
- **Como desfazer:** trocar `CacheSslContext` por Caffeine; remover `bcpkix`/`bcprov` do `adapter-nfe-nacional/build.gradle.kts`; aceitar o primeiro alias em `CarregadorPfx`.

### DA-10 · 2026-10-27 · E3-S01
- **Contexto:** o esquema (§8) chaveia `estado_coleta`/`ledger_*` por `(fonte, cnpj_consultado, ambiente)`, mas o domínio não tem `Ambiente` e a porta `NsuLedger` só recebe `(FonteId, Cnpj)`. Mudar a porta mexe nos dois use cases e em `ContextoConsulta` (ajuste v2 da §9, ainda sem subetapa própria).
- **Opções:** (a) criar `enum Ambiente {PRODUCAO, HOMOLOGACAO}` no domínio agora, mas **sem** mudar a porta: `JdbiNsuLedger(Jdbi, Ambiente)` recebe o ambiente no construtor; (b) mudar a porta e os use cases já na E3-S01; (c) coluna `ambiente` com default fixo.
- **Escolha:** (a) — a coluna e a chave ficam certas desde a V1 (sem migração depois), e a subetapa não cresce. A troca da porta fica para quando `ContextoConsulta` ganhar `ambiente`/`certificado_id`.
- **Como desfazer:** passar `Ambiente` como parâmetro de `NsuLedger.registrar/listarEntradas` e remover do construtor do `JdbiNsuLedger`.

### DA-11 · 2026-10-27 · E3-S01
- **Contexto:** os docs fixam JDBI + Flyway + Testcontainers, mas não a versão do Postgres nem como garantir o append-only (inv. 6) no banco. O Docker da máquina é o Engine 29 (API mínima 1.44), que quebra Testcontainers antigos.
- **Opções:** imagem `postgres:17-alpine` × 16; append-only só por convenção no código × **trigger** que rejeita `UPDATE`/`DELETE` em `ledger_nsu`/`ledger_tentativa`.
- **Escolha:** `postgres:17-alpine` fixada num único lugar dos testes; trigger `BEFORE UPDATE OR DELETE ... RAISE EXCEPTION` nas tabelas append-only (defesa em profundidade, teste dedicado); Testcontainers na versão mais nova compatível com Docker 29 (2.0.x).
- **Como desfazer:** trocar a tag da imagem; nova migration `DROP TRIGGER`.

### DA-12 · 2026-10-28 · E2-S11
- **Contexto:** o plano pede `MontadorEvento.montar(Ambiente, ...)` com "`Ambiente` local ao adapter se ainda não existir" e `RelogioManual` no teste; o adapter já tem `soap.TipoAmbiente` (com `codigoTpAmb()`) e `RelogioManual` é package-private em `cert`.
- **Opções:** criar outro enum de ambiente × reusar `TipoAmbiente`; tornar `RelogioManual` público × `Clock.fixed`.
- **Escolha:** reusar `TipoAmbiente` (evita dois mapeamentos de `tpAmb`); `Clock.fixed` no teste (relógio não precisa andar).
- **Como desfazer:** mover `TipoAmbiente` para pacote comum ou trocar a assinatura de `montar`.

### DA-13 · 2026-10-28 · E2-S07
- **Contexto:** a §8.1 pede `raw/pull/{tenant}/{cnpj_consultado}/{aaaa}/{mm}/{dd}/{tentativa_id}.envelope.xml.gz`, mas o adapter não conhece tenant nem `tentativa_id` (`ContextoConsulta` = fonte + CNPJ; `estado_coleta` não tem tenant). A §6.3 também pede cada docZip no blob store, sem layout próprio para o pull.
- **Opções:** (a) chaves montadas numa só classe `ChavesBlobPull`, sem tenant, com sha256 na chave: `raw/pull/{fonte}/{ambiente}/{cnpj}/{aaaa}/{mm}/{dd}/{sha256}.envelope.xml` e `raw/pull/{fonte}/{ambiente}/{cnpj}/nsu/{nsu15}-{sha256}.xml`; data em UTC pelo `Clock`; sem gzip agora; (b) mudar `ContextoConsulta` para levar tenant/tentativa já na E2-S07.
- **Escolha:** (a) — chave determinística com sha256 (inv. 7), sem crescer o domínio; o pull é por CNPJ consultado, não por tenant. Item [DUVIDA] em PARA-VOCE sobre o prefixo de tenant.
- **Como desfazer:** alterar só `ChavesBlobPull` (e o teste dele); objetos antigos continuam legíveis pelo `BlobRef` gravado.

### DA-14 · 2026-10-28 · E2-S07
- **Contexto:** os docs mapeiam `cStat` (E2-S02) e dizem "timeout, 5xx, 429 → `IndisponivelTemporario`" (javadoc do `Desfecho`) e "rejeição de certificado → `ErroAutenticacao`" (§3.2), mas não fixam o resto: 4xx, resposta malformada, SOAP Fault, docZip corrompido, `ultNSU` da resposta menor que o cursor, formato do `Cursor`.
- **Opções / Escolha:** `Cursor.token` = `ultNSU` decimal sem zeros (`""` = 0); timeout/conexão/resposta grande/5xx/408/429/SOAP Fault/XML malformado/DOCTYPE → `IndisponivelTemporario(5 min)`; 401/403 → `ErroAutenticacao`; outro 4xx → `IndisponivelDefinitivo`; **docZip corrompido ou falha do `BlobStore` → o lote inteiro vira `IndisponivelTemporario`, sem `Documento` e sem avançar** (o envelope bruto já ficou guardado; nada se perde); `ultNSU` da resposta < cursor → `IndisponivelDefinitivo("cursor inconsistente")`. Em todo desfecho que não seja `Ok`/`Vazio`, `proximoCursor` = cursor de entrada. Alternativa descartada: pular o docZip ruim marcando GAP (perderia o NSU até a E2-S09 existir).
- **Como desfazer:** tudo em `TradutorRespostaDistDfe` (uma classe, testes unitários por linha da tabela).

### DA-15 · 2026-10-28 · E2-S07
- **Contexto:** a §6.3/§8 grava `blob_envelope_ref` em `ledger_tentativa`, mas `BuscarLoteResultado` não tem onde levar a referência do envelope bruto; e os testes do adapter real precisam do `BlobStoreEmMemoria`, que mora em `adapter-fake` (DA-4).
- **Opções:** (a) novo componente `Optional<BlobRef> envelope` em `BuscarLoteResultado`, mantendo o construtor antigo de 5 argumentos (envelope vazio), e `testImplementation(project(":adapter-fake"))` no adapter real; (b) deixar a referência para a E3 (o envelope ficaria órfão).
- **Escolha:** (a) — só `BlobRef` (sem bytes, inv. 3); nenhum chamador existente muda.
- **Como desfazer:** remover o componente e o construtor extra; tirar a dependência de teste.

### DA-16 · 2026-10-28 · E2-S07
- **Contexto:** a DA-14 não cobre HTTP 3xx (o `HttpClient` não segue redirecionamento) nem fixa a API do tradutor.
- **Opções:** 3xx → `IndisponivelDefinitivo` (endpoint mal configurado) × `IndisponivelTemporario` (seguro: não avança e retenta).
- **Escolha:** 3xx → `IndisponivelTemporario(5 min)`. API: `deTransporte(ResultadoTransporte) → Optional<Desfecho>` (vazio = 2xx, ler o corpo) e `deLeitura(LeitorRespostaDistDfe.Resultado) → Desfecho`; classe em `adapter.nfe.mapping`.
- **Como desfazer:** um `if` em `TradutorRespostaDistDfe.deStatus` + o teste `http3xxEhTemporario`.

### DA-17 · 2026-10-28 · E2-S07
- **Contexto:** o plano não fixa `Documento.modelo` nem o que fazer, no passo 6, com desfechos fora do 138.
- **Opções:** `modelo` "55" fixo × extraído do XML; desfecho não tratado → `IllegalStateException` × já ligar o tradutor.
- **Escolha:** `modelo = "55"` (distribuição NF-e só entrega modelo 55; extração do XML fica para E3-S07); exceção explícita (nada avança) até os passos 8–9 ligarem o `TradutorRespostaDistDfe`, para os testes deles falharem antes.
- **Como desfazer:** constante `MODELO_NFE` e os `throw` em `NfeNacionalAdapter.buscarLote`.

### DA-18 · 2026-10-29 · E2-S08
- **Contexto:** a §15 item 3 pede uma suíte abstrata de contrato do `SourceAdapter` rodando contra o fake e o `NfeNacionalAdapter`, mas não diz em que módulo ela fica. Ela precisa de JUnit e AssertJ, e o domain é puro (só JDK, ArchUnit).
- **Opções:** (a) `java-test-fixtures` no `adapter-fake` (`src/testFixtures`), consumida com `testImplementation(testFixtures(project(":adapter-fake")))`; (b) `java-test-fixtures` no domain; (c) módulo novo `adapter-contrato`.
- **Escolha:** (a). Segue a DA-4 (os fakes de teste moram no `adapter-fake`), não toca o domain e não cria módulo novo.
- **Como desfazer:** mover `SourceAdapterContrato` para o módulo escolhido e trocar a dependência de teste nos dois adapters.

### DA-19 · 2026-10-29 · E2-S08
- **Contexto:** o `FakeSourceAdapter` devolve o cursor de entrada mesmo com `Ok` (relê sempre os mesmos documentos), devolve `ultNsu = 0` quando vazio e não tem como simular falha. Assim ele não passaria num contrato que valha também para o adapter real.
- **Opções:** (a) o fake passa a ter cursor de verdade: token = `ultNsu` decimal (`""` = 0, como na DA-14), devolve só NSU > cursor, `Vazio` mantém o cursor e o NSU de entrada, e ganha `programarDesfecho(cnpj, Desfecho)` (falha sem documentos e sem avançar); (b) a suíte de contrato pula os cenários que o fake não suporta.
- **Escolha:** (a). O fake é o dublê do core nas Fases 3–4, então precisa obedecer à mesma regra de não-retrocesso. Os testes atuais do fake continuam válidos.
- **Como desfazer:** reverter `FakeSourceAdapter.buscarLote` e remover `programarDesfecho` (+ testes).

### DA-20 · 2026-10-29 · E2-S09
- **Contexto:** a porta do domínio só tem `buscarUm(FonteId, Cnpj, Nsu)` (`ConsultaPontualPort`). A ARCHITECTURE §5.5 sugere `buscarUm(ctx, ReferenciaDocumento)`, mas o consChNFe ainda não tem nenhum chamador no core, e o domínio não tem tipo de chave de acesso.
- **Opções:** (a) o consChNFe vira um método público do `NfeNacionalAdapter` (`buscarPorChave(ContextoConsulta, ChaveAcessoNfe)`), com o record `ChaveAcessoNfe` no adapter (só confere os 44 dígitos), e o domínio fica intocado; (b) já trocar a porta para `ReferenciaDocumento` (mexe no `RecuperarGapUseCase`, no fake e nos testes do domain).
- **Escolha:** (a). É pequeno, não muda o domínio e deixa a porta para quando o core precisar dela (processor ou gap-recovery por chave).
- **Como desfazer:** subir `ChaveAcessoNfe` para o domain e criar a porta; o `buscarPorChave` passa a implementar essa porta.

### DA-21 · 2026-10-29 · E2-S09
- **Contexto:** os docs não dizem o que fazer quando o consNSU ou o consChNFe responde `138` com 0 docZips, com mais de um docZip ou (no consNSU) com um docZip de NSU diferente do pedido.
- **Opções:** tratar como `Ok` sem documento; `IndisponivelTemporario(5 min)`; `IndisponivelDefinitivo`.
- **Escolha:** `IndisponivelTemporario(5 min)`, sem documento. É uma resposta inconsistente. O envelope já foi gravado, então nada se perde e dá para diagnosticar. Não vira `Ok`, para o gap não ser dado como recuperado.
- **Como desfazer:** um `if` no `buscarUm`/`buscarPorChave` e os testes da DA-21.

### DA-22 · 2026-10-30 · E2-S15
**Contexto:** a DA-5 adiou a troca de `Documento.blobRef` (`Optional<String>`) para a E3; o autor escolheu a opção (a) no PARA-VOCE E2-S08 (2026-10-29).
**Opções:** (a) `Optional<BlobRef>` já, com os dois adapters devolvendo o `BlobRef` do `BlobStore.gravar` e o contrato lendo via `blobs().ler(ref)`; (b) manter a string e o gancho `lerBlob`.
**Escolha:** (a) — feito na E2-S15; **fecha a DA-5** e a parte `blobRef` do achado A8 (o resto do A8 segue para a E3).
**Como desfazer:** reverter os commits `(E2-S15)` da branch auto/E2-S15.
