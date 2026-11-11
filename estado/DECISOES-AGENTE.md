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

### DA-23 · 2026-10-30 · E3-S02
- **Contexto:** o §6.3 (passo 4) pede ledger + CAS do cursor na mesma transação, mas o domínio não tem porta para o estado da coleta nem unidade de trabalho; os docs não dizem a forma da porta.
- **Opções:** (a) porta `EstadoColetaRepositorio` com `confirmarLote(ConfirmacaoLote) → ResultadoCas` (sealed: `Aplicado`, `ConflitoDeVersao`, `RetrocessoRecusado`), que grava ledger + cursor numa transação e desfaz tudo em conflito; (b) porta genérica `UnidadeDeTrabalho` envolvendo `NsuLedger` + repositório de cursor; (c) CAS só do cursor, ledger fora da transação.
- **Escolha:** (a). Menor superfície, não vaza transação para o domínio e casa com o pseudocódigo da §6.7. Pacote novo `domain.coleta`; `NsuLedger` e `AvancarPonteiroUseCase` intocados. Também: trigger no banco (V3) recusando `ult_nsu` menor (defesa em profundidade da inv. 1); `ult_nsu` igual é aceito e sobe a versão (espelha `NAO_AVANCOU`).
- **Como desfazer:** trocar a porta por (b) movendo o SQL do `JdbiEstadoColeta` para dois repositórios; remover o trigger com uma migration nova.

### DA-24 · 2026-10-30 · E3-S03
- **Contexto:** ADR-013/§13 pedem RLS por tenant, mas os docs não dizem como o tenant chega à sessão, qual papel a aplicação usa, nem o que fazer com as tabelas da coleta (`estado_coleta`, `ledger_*`), que **não têm** `tenant_id` (§8 e decisão do autor de 2026-10-29 na §8.1: coleta é por CNPJ consultado, sem tenant). O revisor da E3-S01 pediu revogar `TRUNCATE`/`DELETE` do papel da aplicação.
- **Opções:** (a) variável de sessão `app.tenant_id` **local à transação** (`set_config(..., true)`), política `tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint` (sem tenant = zero linhas, *fail-closed*), `ENABLE` + `FORCE ROW LEVEL SECURITY` só nas tabelas com `tenant_id` (+ `tenant` pela `id`); papel de grupo `fiscal_app` (`NOLOGIN NOBYPASSRLS`) criado pela migration de forma idempotente, usuários de login fora do Flyway; no ledger só `SELECT, INSERT`; nenhum `TRUNCATE`/`DELETE`; (b) `tenant_id` também nas tabelas da coleta; (c) RLS por papel de banco por tenant.
- **Escolha:** (a). Segue a §8/§8.1 (coleta sem tenant, isolamento a partir do catálogo), é o padrão mais comum com pool de conexões e falha fechado. As tabelas sem `tenant_id` ficam numa lista explícita testada, para tabela nova de negócio não escapar da RLS.
- **Como desfazer:** migration nova com `disable row level security`/`drop policy` e `revoke`; trocar o nome da variável num só lugar (`TransacaoPorTenant`).

### DA-25 · 2026-10-30 · E3-S04
- **Contexto:** ADR-015/021 e §8 pedem outbox + relay no Postgres, mas não definem: forma do payload, como o outbox entra na transação do CAS (DA-23), proteção da tabela, ordem com vários relays, consumo idempotente, nem o número da migration (a E3-S03, ainda não mesclada, ocupa V4/V5).
- **Opções:** payload (a) `jsonb` montado fora do domínio × (b) `bytea`; transação (a) `ConfirmacaoLote` ganha `mensagens` × (b) porta `UnidadeDeTrabalho`; relay (a) uma transação por mensagem com `SKIP LOCKED` + `not exists` de anterior pendente da mesma chave × (b) relay único sem garantia de ordem; consumo (a) tabela `outbox_consumo(consumidor, chave_idempotencia)` na transação do efeito × (b) cada consumidor se vira.
- **Escolha:** (a) em todos. `jsonb` recusa XML/lixo no banco (defesa da inv. 3) e o domínio segue sem lib JSON; limite de 64 KiB no `MensagemOutbox`. Trigger só deixa `publicado_em` ir de nulo para valor; sem `DELETE`. Migrations V6/V7 (pulam V4/V5 da E3-S03; Flyway aceita lacuna) e `grant` a `fiscal_app` condicional à existência do papel.
- **Como desfazer:** migration nova trocando a coluna/trigger; remover o componente `mensagens` do `ConfirmacaoLote` (o construtor de 4 argumentos já é o caminho antigo); renumerar as migrations antes da primeira instalação real.

### DA-26 · 2026-10-30 · E3-S04
Contexto: mensagem "venenosa" (falha persistente do Publicador) na menor pendente travava o relay inteiro.
Opções: (a) excluir a chave `(topico, chave)` que falhou do resto da rodada e relançar a 1ª falha ao fim; (b) devolver resultado com falhas.
Escolha: (a) — contrato de `publicarPendentes` preservado (lança), mas as outras chaves saem antes. Exclusão via `bindArray` no claim.
Como desfazer: reverter o commit `fix(E3-S04)` do relay.

### DA-27 · 2026-11-02 · E3-S06
- **Contexto:** a §8 é rascunho e não define: `tenant_id` em `arquivo`/`documento_empresa`/`proveniencia` (o desenho não traz, mas o cabeçalho pede "em toda tabela de negócio" e o meta-teste da E3-S03 recusa tabela sem ele); como evitar proveniência duplicada numa tabela append-only; como juntar metadados e `situacao` quando chegam vários registros, fora de ordem e com confianças diferentes; como fixar o tenant, já que `TransacaoPorTenant` (E3-S03) ainda não está na main.
- **Opções:** esquema (a) `tenant_id` nas quatro tabelas, com FK composta × (b) só em `documento`, com política por subconsulta. Fusão (a) "fusão dos registros confiáveis; se não há nenhum, fusão dos declarados" × (b) o primeiro registro vence × (c) o último vence. Tenant (a) `set_config` privado no `JdbiCatalogo` × (b) copiar `TransacaoPorTenant` × (c) esperar a E3-S03.
- **Escolha:** (a) em todos.
  - **Esquema (V8):** FKs compostas `(tenant_id, documento_id)`, `(tenant_id, arquivo_id)` e `(tenant_id, empresa_id)`, com `unique (tenant_id, id)` em `documento`, `arquivo` e `empresa`: o banco recusa vínculo entre tenants. `proveniencia` tem `unique nulls not distinct (arquivo_id, origem, confianca, ledger_nsu_id, recebimento_id)` e checks: `OFICIAL` só com `DFE_AN`; `DFE_AN` só com `OFICIAL` e `ledger_nsu_id`; push só com `recebimento_id`. Coluna nova `documento.so_declarado`.
  - **Fusão (função pura, inv. 9 e §4 "armazenado, mas sinalizado"):** confiável = qualquer nível exceto `DECLARADO`. Confiável sobre declarado substitui tudo; declarado sobre confiável não muda o documento (arquivo, vínculo e proveniência são gravados); mesma classe preenche só os vazios e a `situacao` fica na maior de `SO_RESUMO < AUTORIZADA < CANCELADA < DENEGADA`. Assim a ordem de chegada não importa e um push não validado não "cancela" uma nota.
  - **Numeração:** V8/V9, porque E3-S03 ocupa V4/V5 e E3-S04, V6/V7 (DA-25).
- **Como desfazer:** o estado de `documento` é reconstruível a partir do bruto (§8): trocar `FusaoDocumento` e reprocessar. O esquema muda por migration nova, ou por renumeração antes da primeira instalação real. O `set_config` privado sai quando `TransacaoPorTenant` entrar.

### DA-28 · 2026-11-02 · E3-S06
- **Contexto:** o plano pede `FusaoDocumento.fundir(atual, novo, confianca)`, sem definir o tipo de `novo`. Um `EstadoDocumento` em `novo` traria um `soDeclarado` redundante, que poderia contradizer a `confianca`.
- **Opções:** (a) `fundir(atual, metadados, situacao, confianca)` + `inicial(metadados, situacao, confianca)` × (b) `novo` como `EstadoDocumento` × (c) `novo` como `RegistroArquivo`, que repete a confiança e pesa nos testes.
- **Escolha:** (a). `novo` vira dois parâmetros, sem estado contraditório; `inicial` dá ao `JdbiCatalogo` o estado do primeiro registro com a mesma regra.
- **Como desfazer:** trocar a assinatura; só `JdbiCatalogo` (passo 7) e os testes da fusão a usam.

### DA-29 · 2026-11-02 · E3-S06
- **Contexto:** a DA-27 não define se `proveniencia.ledger_nsu_id` tem FK (só diz que `recebimento_id` fica sem), nem a precisão de `documento.valor_total`.
- **Opções:** FK (a) para `ledger_nsu (id)` × (b) sem FK; valor (a) `numeric` sem escala × (b) `numeric(15,2)`.
- **Escolha:** (a) em ambos. A tabela do ledger já existe e a FK impede proveniência apontando para o nada; `numeric` sem escala guarda o valor como veio, sem arredondar. Consequência: os testes dos próximos passos precisam criar a linha do ledger antes de registrar `DFE_AN`.
- **Como desfazer:** migration nova com `drop constraint proveniencia_ledger_nsu_id_fkey` e `alter column valor_total type numeric(15,2)`; ou editar a V8 antes da primeira instalação real.

### DA-30 · 2026-11-03 · E3-S06
- **Contexto:** o plano pede `FalhaCatalogoException` com só o `SQLState`, sem dizer em que pacote ela fica. Quem chama a porta `Catalogo` (E3-S07 e E5) não deve depender de `infra`.
- **Opções:** (a) `domain.catalogo`, com um `codigo()` genérico que no Postgres é o SQLState, como a `BlobConflitanteException` em `domain.blob` × (b) `infra.postgres`, com `sqlState()`, obrigando quem captura a depender da infra.
- **Escolha:** (a). A tradução alcança `registrar` e `consultar`, porque fica no método privado `noTenant`. Sem SQLState na cadeia (raro), o código é `desconhecido`. Acrescentei o teste `falhaAoConsultarTambemEhTraduzida`, que não estava no plano.
- **Como desfazer:** mover a classe para `infra.postgres` e ajustar o import no `JdbiCatalogo`, no teste e no javadoc da porta.

### DA-31 · 2026-11-03 · E3-S06
- **Contexto:** em `CatalogoPropriedadesTest`, as propriedades dividem um schema. Depois de uma falha, o shrinking padrão do jqwik (`BOUNDED`) durou ~10s e, ao que tudo indica, seguiu em segundo plano, disputando o schema com a propriedade seguinte: `deadlock detected` no `truncate` e propriedades sem relação com o defeito reprovadas. Só aparece com teste vermelho. A causa foi inferida da duração e confirmada pelo efeito da troca, não lida no código do jqwik.
- **Opções:** (a) `ShrinkingMode.FULL` × (b) `ShrinkingMode.OFF`, sem contraexemplo mínimo × (c) um schema por propriedade, mais lento e sem resolver a concorrência do shrinking dentro da mesma propriedade.
- **Escolha:** (a). Com a mutação "último vence", só `ordemDeChegadaNaoMudaAFicha` reprova, sem deadlock. Não mexi em `EstadoColetaCasPropriedadesTest` nem em `JdbiNsuLedgerPropriedadesTest`, que têm o mesmo desenho (schema compartilhado + `truncate`) e podem sofrer o mesmo efeito quando falharem.
- **Como desfazer:** tirar `shrinking = ShrinkingMode.FULL` das quatro propriedades e o parágrafo correspondente do javadoc.

### DA-32 · 2026-11-03 · E3-S07
- **Contexto:** o processor lê bytes do blob e precisa do extrator, que está em `adapter-nfe-nacional`. A §9 diz que ele "é borda, não core" e cita a porta `ParserDfe`, mas não diz em que módulo ele fica; a §19 só permite que `adapter-*` dependa de `domain`.
- **Opções:** (a) `app-worker`, sem Spring por ora, só com portas; `ParserDfe` no domínio (`byte[]` só no parâmetro, como no `BlobStore`) e implementação no adapter × (b) módulo novo `processador`, fora da §19 × (c) dentro do `adapter-nfe-nacional`, que amarraria o processor à NF-e e impediria o reúso pela E5.
- **Escolha:** (a). A §19 já dá ao `app-worker` o fetch + process. O código de produção do módulo só depende de `domain` (ArchUnit); adapter e infra entram como `testImplementation` até existir composição (E3-S08).
- **Como desfazer:** mover o pacote para outro módulo e ajustar `settings.gradle.kts`; nenhuma porta muda.

### DA-33 · 2026-11-03 · E3-S07
- **Contexto:** a E3-S06 deixou para cá o mapeamento para `SituacaoDocumento`. Os códigos não estão em `docs/FONTES-OFICIAIS.md`: a tabela abaixo vem do conhecimento do planejador sobre o leiaute, sem conferência no MOC.
- **Opções:** (a) tabela isolada, marcada [VALIDAR], e conferência no smoke E2-S12 × (b) bloquear até confirmar a fonte × (c) tudo que não for resumo vira `AUTORIZADA`.
- **Escolha:** (a), porque a situação é reconstruível a partir do bruto (§8). `SituacaoNfe` no adapter: `cSitNFe` 1 → `SO_RESUMO`, 2 → `DENEGADA`, 3 → `CANCELADA`; protocolo 100/150 → `AUTORIZADA`, 110/205/301/302/303 → `DENEGADA`; eventos 110111/110112 → `CANCELADA`; outro evento ou código desconhecido → `SO_RESUMO`, que nunca sobrepõe situação melhor na fusão (DA-27).
- **Como desfazer:** trocar a tabela em `SituacaoNfe` e reprocessar a partir do bruto.

### DA-34 · 2026-11-03 · E4-S01
- **Contexto:** a §6.5/§6.7 pedem claim com lease e CAS como proteção final, mas não dizem se o claim mexe na `versao`, o que acontece com o resultado de um dono cujo lease venceu, nem o que é o "ciclo" do teto por tenant.
- **Opções:** (a) o claim não muda `versao`; o dono vencido ainda confirma o lote e seu bloqueio sempre vale; teto por chamada de `reivindicar` × (b) o claim incrementa `versao` (fencing), recusando o dono vencido × (c) teto sobre leases vigentes do tenant.
- **Escolha:** (a). O fetcher vencido já consumiu aquele NSU na SEFAZ: descartar a confirmação (b) deixaria o cursor para trás, e a próxima consulta pediria NSU já consumido (inv. 1). Pelo mesmo motivo, o bloqueio informado pela SEFAZ não depende de dono, e `registrarBloqueio` nunca encurta, para não haver nova consulta dentro da janela. O teto por chamada segue o texto da §6.5 ("por ciclo") e é o mais simples (ADR-021); com vários schedulers ele vale por instância.
- **Como desfazer:** (b) é uma linha no `update` do claim, mais o teste `donoVencidoAindaConfirmaOLoteQueJaConsumiu`. (c) troca a subconsulta do teto. Nada fica gravado em formato novo.

### DA-35 · 2026-11-03 · E4-S02
- **Contexto:** a §7.4 pede envelope encryption com "DEK por certificado" e KEK local, e a §8 só traz `pfx_cifrado_ref` e `senha_cifrada_ref`. Os docs não fixam algoritmo, formato, onde fica a DEK embrulhada nem como o `cert-vault` abre o `.pfx`: o `CarregadorPfx` está no adapter, e a §19 não deixa um módulo de borda depender do outro. A tabela `certificado` depende da E3-S03, que não está na base; `estado_coleta.certificado_id` é `bigint` (V1).
- **Opções:** cifra (a) AES-256-GCM do JDK × (b) lib externa (Tink). Guarda (a) dois blobs autocontidos, pela porta `BlobStore` × (b) `bytea` no Postgres × (c) coluna nova para a DEK. Abertura (a) `KeyStore` próprio no `cert-vault` × (b) mover o `CarregadorPfx` para o `domain` × (c) módulo compartilhado novo. Metadados (a) porta + repositório em memória nos testes, com Postgres em subetapa própria × (b) migration já aqui.
- **Escolha:** (a) em todos.
  - **Envelope:** `versão(1) | kekId | nonce+DEK embrulhada | nonce | cifrado+tag`, GCM em ambas as camadas. Mesma DEK nas duas partes de um certificado, com nonces aleatórios.
  - **AAD:** `tenant|titular|fingerprint|PFX ou SENHA`, conhecida antes de o `id` existir. Assim, trocar blobs entre certificados ou partes não decifra.
  - **Chave:** `cofre/{tenant}/{fingerprint}/{parte}-{sha256}.env`. A inv. 7 vale porque a chave contém o hash do cifrado. Processors e API podem alcançar o `BlobStore`, mas só veem cifrado; a KEK fica fora do alcance deles.
  - **Abertura:** cerca de 30 linhas duplicadas, em troca de manter a regra de dependência da §19. Um teste confere que a credencial aberta passa no `CarregadorPfx` do adapter com o mesmo fingerprint.
  - **Reenvio:** `(tenant, titular, fingerprint)` já existente devolve o registro atual.
- **Como desfazer:** a versão no envelope permite um formato novo sem perder os antigos, e a guarda fica atrás de portas. Antes de existir certificado real, basta trocar `EnvelopeCifrado`. O `KeyStore` próprio sai se um módulo compartilhado for criado.

### DA-36 · 2026-11-03 · E4-S02
- **Contexto:** a §7.4 pede "cadeia ICP-Brasil" e "CNPJ do certificado == CNPJ do titular". Os docs não dizem de onde vem o CNPJ, se há checagem de revogação, nem o que fazer com filial de mesma raiz. FONTES-OFICIAIS item 8 diz que o e-CNPJ da matriz consulta as filiais, mas isso é consulta, não titularidade.
- **Opções:** CNPJ (a) só do `otherName` `2.16.76.1.3.3` × (b) também do CN. Leitura (a) leitor DER mínimo próprio × (b) `bcprov` em produção. Revogação (a) desligada × (b) LCR/OCSP online no upload. Titular (a) igualdade exata dos 14 dígitos × (b) mesma raiz. Validade e cadeia (a) `Clock` injetado em PKIX, com raízes e intermediárias em `ConfiancaIcp` × (b) só a cadeia que vem no `.pfx`.
- **Escolha:** (a) em todos. O CN é texto livre, então fica fora. O OID vem de memória (DOC-ICP-04), sem conferência em fonte oficial: fica numa constante [VALIDAR], e um erro falha fechado (`SEM_CNPJ`), sem aceitar certificado indevido. Um certificado revogado passa no upload, mas a SEFAZ deve recusá-lo no mTLS. Igualdade exata é o texto da §7.4; a dúvida da filial está em PARA-VOCE. O acervo aceita `.pfx` sem intermediárias, que existem na prática.
- **Como desfazer:** tudo em `ValidadorPfx` e `ExtratorCnpjCertificado`: trocar a constante do OID, ligar `setRevocationEnabled(true)` ou comparar a raiz, com o teste correspondente.

### DA-37 · 2026-11-04 · E1-S07
- **Contexto:** o autor pediu para decidir e registrar como tratar minúsculas e letras proibidas. A NT Conjunta 2025.001 v1.00 traz `[A-Z0-9]{12}[0-9]{2}` e diz que a exclusão de I, O, U, Q e F "precisa ser confirmada". A FAQ da Receita (pergunta 45) manda considerar todas as letras; as combinações proibidas são controle interno dela.
- **Opções:** minúscula/máscara (a) recusar no `Cnpj` × (b) normalizar. Letras (a) aceitar as 26, com `LETRAS_VEDADAS` vazia e isolada × (b) já vedar as cinco. Zeros (a) recusar só os 14 zeros × (b) recusar raiz zerada.
- **Escolha:** (a) em todos. O valor canônico evita duas grafias para a mesma chave de banco e de blob, e a NT só aceita maiúsculas; a normalização, se precisar, fica na borda do cadastro. Vedar letras sem confirmação poderia recusar CNPJ legítimo e perder nota. `00000000000191` passa no DV, e o Anexo I da NT só recusa os 14 zeros.
- **Como desfazer:** preencher `LETRAS_VEDADAS`, ou normalizar antes de chamar o construtor, ajustando `CnpjTest`.

### DA-38 · 2026-11-04 · E1-S07
- **Contexto:** o pedido cita o DV da chave. Até aqui nenhum record de chave confere o DV (DA-20). É preciso decidir se uma chave com DV errado vinda de fora é recusada, e o objetivo nº 1 é nunca perder documento.
- **Opções:** (a) DV obrigatório só no que **sai** para a SEFAZ (`ChaveAcessoNfe`, `IdEvento`); no que **entra** (`ChaveAcesso`, `ExtratorMetadados`), só formato, com `ChaveAcesso.temDvValido()` disponível; (b) DV obrigatório em todos os construtores.
- **Escolha:** (a). Na saída, conferir o DV evita gastar consulta num pedido que seria rejeitado. Na entrada, a SEFAZ já validou o que vem pelo AN, e recusar por um erro nosso de cálculo travaria o documento fora do catálogo. A porta push (Fase 5, inv. 9) usa `temDvValido()` para decidir a confiança ou a rejeição.
- **Como desfazer:** chamar a checagem de DV no construtor de `ChaveAcesso` e ajustar `ExtratorMetadadosTest.chaveComDvErradoAindaEhExtraida` e as fixtures do catálogo.

### DA-39 · 2026-11-04 · E1-S07
- **Contexto:** a `main` não compila os testes do `infra-postgres`: o helper `private static dataSourceComoApp` do `CatalogoRlsTest` (E3-S06) conflita com o de `PostgresTestBase` (E3-S03). Sem compilar, a suíte inteira não roda. A regra proíbe alterar teste antigo para "passar", salvo pelo plano, e o plano não cita isso.
- **Opções:** (a) renomear o helper privado, sem tocar em asserção; (b) não mexer e terminar sem rodar os testes do módulo; (c) trocar pelo helper da base, o que muda o usuário de banco do teste.
- **Escolha:** (a), para `dataSourceComoLoginDeTeste`. O teste continua com o mesmo login e as mesmas asserções, e não fica mais fraco. As 2 falhas de asserção que apareceram depois **não** foram alteradas (PARA-VOCE).
- **Como desfazer:** reverter as 2 linhas de `CatalogoRlsTest` no commit `feat(domain): DV módulo 11 alfanumérico`.
- **Situação (2026-11-04):** o commit foi revertido pelo `verificar.sh` às 08:13 e a renomeação **não está no código**. Ela volta junto com a decisão em PARA-VOCE "[DECISAO] E1-S07".

### DA-40 · 2026-11-04 · E2-S13
- **Contexto:** o `FakeSourceAdapter` declara `fazManifestacao = true` (testado em `deveRetornarDescritor`), mas, com o default de D3, `manifestar` devolve `Optional.empty()` (`naoManifesta`, pedido pelo plano). A capacidade e o comportamento do fake ficam incoerentes.
- **Opções:** (a) manter os dois como estão; (b) mudar a capacidade do fake para `false`, alterando teste antigo que o plano não cita.
- **Escolha:** (a). Nenhum código consome a capacidade para manifestar hoje.
- **Como desfazer:** trocar a capacidade para `false` no fake e a asserção em `deveRetornarDescritor`.

### DA-41 · 2026-11-04 · E2-S13
- **Contexto:** o plano pede `soap12ComOperacaoDoWsdl` e D2 cita a operação `nfeRecepcaoEvento`, mas não diz se ela vira elemento no `Body` (no `distDFe` vira). Além disso, o C14N inclusivo leva os namespaces em escopo para o digest: com `xmlns:soap12` no `Envelope`, a assinatura falha quando conferida no próprio documento (testado: `assinaturaContinuaValidaDentroDoEnvelope` fica vermelho com prefixo).
- **Opções:** (a) `nfeDadosMsg` direto no `Body`, operação só na `ACTION`, envelope só com namespace padrão; (b) elemento da operação em volta, como no `distDFe`; (c) prefixo `soap12:` e validar o evento extraído do envelope.
- **Escolha:** (a). Fonte secundária para o `nfeDadosMsg` direto: [frones/nfe](https://github.com/frones/nfe/blob/1a5347eea6c9/soap.go) e [fórum ACBr 78871](https://www.projetoacbr.com.br/forum/topic/78871-qual-%C3%A9-o-erro-do-meu-xml/). O WSDL não foi aberto (regra de FONTES-OFICIAIS); confirmar no smoke E2-S12.
- **Como desfazer:** mudar as strings de `EnvelopeEnvEvento.soap12` e a asserção de `soap12ComOperacaoDoWsdl`.

### DA-42 · 2026-11-04 · E2-S13
- **Contexto:** o plano define `RetornoEvento` sem dizer o que fazer com evento rejeitado sem chave/tipo/seq, nem com mais de um `retEvento` no lote. De memória (sem abrir o XSD), `chNFe`, `tpEvento`, `nSeqEvento` e `nProt` são opcionais no `retEvento`.
- **Opções:** (a) esses campos `Optional`, só `cStat` obrigatório, e mais de um `retEvento` → `Invalida`; (b) todos obrigatórios; (c) pegar o primeiro `retEvento`.
- **Escolha:** (a). Com (b), uma rejeição legítima viraria leitura inválida e seria retentada; com (c), uma resposta incoerente poderia passar por sucesso (D2: um evento por lote). Confirmar o leiaute no smoke E2-S12.
- **Como desfazer:** ajustar `LeitorRetEnvEvento.ler` e os testes `eventoRejeitadoSoComCStat` e `maisDeUmRetEventoEhInvalida`.

### DA-43 · 2026-11-04 · E2-S13
- **Contexto:** D1 não dá código para "demais rejeições do lote", "chave inexistente/inválida" e "autor ≠ certificado", e o MOC não está disponível aqui. Além disso, "demais rejeições → definitivo" colide com "desconhecido → temporário" no lote. `ErroAutenticacao` leva o `estado_coleta` a `CERT_*` e para a coleta do CNPJ (§6.7).
- **Opções:** (a) listas explícitas, e fora delas o fallback temporário; (b) no lote, tudo que não está na tabela vira definitivo. E para `575`: (x) `IndisponivelDefinitivo`; (y) `ErroAutenticacao`.
- **Escolha:** (a) + (x). Lote definitivo: `215`, `225`, `243`, `252`. Evento definitivo: `217`, `236`, `614`–`619`. `ErroAutenticacao` só em `213`. Com (b), um código novo pararia de vez o que poderia ser retentado. `575` diz respeito a uma nota (o CNPJ não é o destinatário dela) e não ao certificado: com (y), uma única nota pararia a coleta do CNPJ. Sentido dos códigos: `252`, `217`, `236`, `614`–`619` em FONTES-OFICIAIS (Tab. 5-29, distribuição); `575`, `650`, `655` em fonte secundária (buscadorncm.com.br); `596` só por indício; **`213`, `215`, `225`, `243` de memória**. Conferir tudo na tabela do MOC/smoke E2-S12.
- **Como desfazer:** ajustar os `case` de `CStatEventoMapper` e as linhas de `CStatEventoMapperTest`.

### DA-44 · 2026-11-04 · E2-S13
- **Contexto:** o plano pede que `retEvento` de outra chave/tipo vire temporário, mas o `LeitorRetEnvEvento` aceita rejeição sem chave, tipo ou sequência, e `573` pode vir sem eles. As regras de HTTP são iguais às do `TradutorRespostaDistDfe`.
- **Opções:** (a) `Ok` exige chave, tipo e sequência presentes e iguais; rejeição só cai em temporário se um campo presente divergir; (b) exigir identificação também na rejeição; (c) só comparar os campos presentes, inclusive no `Ok`.
- **Escolha:** (a). Com (c), um `135` sem chave viraria `Ok` sem confirmar a nota (a propriedade do plano proíbe). Com (b), uma rejeição definitiva sem identificação seria retentada a cada 5 min. Risco: `135`/`573` real sem esses campos fica em temporário; conferir no smoke E2-S12. O `deStatus` ficou duplicado, pois extrair não deixaria o código menor (regra do plano). `nSeqEvento` é comparado como texto ("01" diverge de "1").
- **Como desfazer:** ajustar `TradutorRespostaEvento.deRetorno` e as linhas de `retEventoDeOutraChaveOuTipoEhTemporario`.

### DA-45 · 2026-11-04 · E2-S13
- **Contexto:** o `NFeRecepcaoEvento` é outro serviço, mas o adapter só recebe o endpoint da distribuição. Abrir o .pfx no `autenticar` quebraria os testes antigos, que usam credencial falsa (`new byte[] {1}`), e `descritorDeclaraCapacidades` espera `fazManifestacao = false`. O plano também deixa para a DA o layout da chave e o `idLote`.
- **Opções:** (a) construtor novo com `endpointEvento`, e o de 6 argumentos continua sem manifestar (`Optional.empty()`, capacidade `false`, sem abrir o .pfx); (b) trocar o construtor e alterar os testes antigos; (c) derivar a URL do evento da URL da distribuição.
- **Escolha:** (a). Testes antigos intactos e a capacidade reflete o que o adapter faz; `true` só com o endpoint injetado (D2: URL fora do código). Chave: `raw/pull/{fonte}/{ambiente}/{cnpj}/evento/{aaaa}/{mm}/{dd}/{sha256}.retorno.xml` (data em UTC). `idLote` = `relogio.millis()`: só identifica o lote, sem garantia de unicidade entre envios no mesmo milissegundo.
- **Como desfazer:** remover o construtor de 6 argumentos e ajustar os testes; ou mudar `ChavesBlobPull.envioEvento` e `EnvioManifestacao.enviar`.

### DA-46 · 2026-11-04 · E2-S13
- **Contexto:** o DV da chave precisa ser conferido antes de chamar a SEFAZ (achado d da E2-S11), mas o `IdEvento` documenta que só confere o formato, e o `IdEventoTest` conta com isso. O plano não define a ordem das pré-condições nem o caso de chave malformada.
- **Opções:** (a) conferir em `EnvioManifestacao`, na ordem credencial → chave → tipo; (b) conferir no `IdEvento` e mudar os testes dele.
- **Escolha:** (a). Testes antigos intactos. Chave malformada recebe o mesmo desfecho do DV errado. Motivos fixos, sem a chave (inv. 13). Não tratado: `AssinaturaInvalidaException` ainda sai do `manifestar` como exceção se a assinatura falhar; está fora deste passo, fica para o revisor avaliar.
- **Como desfazer:** mover `chaveValida` para o `IdEvento` e ajustar o `IdEventoTest`.

### DA-47 · 2026-11-04 · E2-S13
- **Contexto:** o teste da inv. 13 reprovou `ManifestarResultado.toString()`, porque a chave do `BlobRef` traz o CNPJ em claro (layout da §8.1).
- **Opções:** (a) verificar só desfechos e logs, como pede o plano; (b) mascarar o `toString` do `BlobRef`, no domínio e fora deste passo.
- **Escolha:** (a). Nenhum log atual imprime o `BlobRef`, mas quem logar um resultado inteiro expõe o CNPJ (§20 pede mascarado); olhar isso ao criar os logs do fetcher.
- **Como desfazer:** trocar `.desfecho().toString()` por `.toString()` em `desfechosELogsSemXmlNemSegredo` e mascarar o `BlobRef`.

### DA-48 · 2026-11-04 · E2-S13
- **Contexto:** falha ao assinar ou serializar o evento saía do `manifestar` como exceção (parecer do revisor, inv. 4). O plano pede um desfecho com motivo fixo e deixa a escolha para a DA.
- **Opções:** (a) `IndisponivelDefinitivo("falha ao assinar o evento")`, capturando `RuntimeException`; (b) `ErroAutenticacao`; (c) temporário; (d) capturar só `AssinaturaInvalidaException` e `IllegalStateException`.
- **Escolha:** (a). A falha é permanente para aquele certificado, então (c) retentaria sem fim; (b) levaria o `estado_coleta` a `CERT_*` e pararia a coleta do CNPJ, embora a chave possa servir para o mTLS. `RuntimeException` segue o `buscarLote` e cobre as duas exceções do plano; só o caminho da assinatura tem teste, pois a falha de serialização não é provocável sem injetar dependência. A E4 não pode tratar esse desfecho como `ERRO_DEFINITIVO` do CNPJ (ressalva do revisor).
- **Como desfazer:** trocar `FALHA_AO_ASSINAR` e o `catch` em `EnvioManifestacao.enviar`, e ajustar `falhaAoAssinarEhDefinitivoSemHttp`.

### DA-49 · 2026-11-09 · E4-S02
- **Contexto:** o plano pede `abreEDevolveMesmoPfxESenha` com senha não ASCII, mas o `KeyStore` PKCS12 do JDK 21 recusa senha não ASCII ao abrir (`getSecretKey failed: Password is not ASCII`), inclusive em `.pfx` legado gerado pelo BC. O `ValidadorPfx` recusa esse `.pfx` com `SENHA_INCORRETA`, e o `CarregadorPfx` do adapter também não o abriria.
- **Opções:** (a) no teste, gravar os envelopes direto no formato do cofre e testar só a volta UTF-8 → `char[]` do `abrir`; (b) abrir o `.pfx` com BouncyCastle no validador e no adapter (dependência nova em produção).
- **Escolha:** (a). Senha vazia e ASCII passam pelo `guardar` real. Na prática, um `.pfx` com senha não ASCII não sobe no cofre (recusa `SENHA_INCORRETA`, que engana o usuário). Avaliar uma recusa específica ou o BC numa subetapa futura, se aparecer caso real.
- **Como desfazer:** remover `registrarSemValidar` do `CofreLocalAbrirTest` e passar o caso não ASCII pelo `guardar`, depois de adotar (b).

### DA-50 · 2026-11-09 · E4-S03
- **Contexto:** a §8 traz `consentimento` com `revogado_em` na mesma linha, mas diz "append-only; revogação = nova linha". Não diz como a linha de revogação aponta para a concessão.
- **Opções:** (a) linha de revogação com `revoga_id` (FK composta com `tenant_id` para a concessão), `revogado_em` preenchido e os demais campos copiados; no máximo uma revogação por concessão (`unique (revoga_id)`) × (b) `update` de `revogado_em` na concessão, contrariando a inv. 6 × (c) tabela separada `consentimento_revogacao`.
- **Escolha:** (a). Vigente em `t` = concessão com `concedido_em <= t`, `valido_ate` nulo ou `> t`, e sem revogação com `revogado_em <= t`. Renovar = nova concessão.
- **Como desfazer:** antes da primeira instalação real, trocar a V14 e o `JdbiConsentimentos`; a regra de vigência fica isolada em `Consentimento.vigenteEm`.

### DA-51 · 2026-11-09 · E4-S03
- **Contexto:** a §6.6 lista `BLOQUEADO_ATE(t)` como estado, mas a E4-S01 (DA-34) só grava a coluna `bloqueado_ate` e deixa o `estado` em `ATIVO`.
- **Opções:** (a) `BLOQUEADO_ATE` é derivado (`estado = ATIVO` e `agora < bloqueado_ate`), nunca gravado em `estado`; a máquina recusa transição para ele × (b) gravar o estado e criar um job para voltar a `ATIVO`, com duas fontes de verdade para o mesmo fato.
- **Escolha:** (a). Não há transição para auditar na volta a `ATIVO`; o bloqueio já fica no ledger de tentativas. O `check` da V1 continua aceitando o valor.
- **Como desfazer:** liberar a transição na `MaquinaEstadosColeta` e gravar o estado no `registrarBloqueio` da E4-S01.

### DA-52 · 2026-11-09 · E4-S03
- **Contexto:** a §6.6 lista os estados, mas não as transições permitidas nem a prioridade quando há vários motivos.
- **Opções:** (a) estado calculado por prioridade a partir de fatos, com matriz explícita × (b) transições livres.
- **Escolha:** (a). Prioridade: `ERRO_DEFINITIVO` (só sai por ação de operador com motivo) > `CONSENTIMENTO_REVOGADO` > `PAUSADO_PELO_CLIENTE` > `AGUARDANDO_CERTIFICADO` > `CERT_VENCIDO` > `ATIVO`. Qualquer estado vai para `ERRO_DEFINITIVO`; os demais transitam entre si quando os fatos mudam; transição para o mesmo estado é no-op, sem linha de auditoria. Transição nunca mexe em cursor, `ult_nsu`, `versao`, lease nem `bloqueado_ate` (inv. 1).
- **Como desfazer:** a matriz e a prioridade ficam só em `MaquinaEstadosColeta`, com teste de tabela.

### DA-53 · 2026-11-09 · E4-S04
- **Contexto:** a §7.4 pede alertas em 60/30/15/7/1 dias e rotação sem perder cursor, mas não diz qual certificado vale quando o titular tem vários, se os alertas se repetem nem o que fazer quando o job fica parado e cruza vários marcos de uma vez.
- **Opções:** vigente (a) válido no instante (`validoDe <= agora <= validoAte`, inclusivo como no `CofreLocal.abrir`) com o maior `validoAte`; empate por `enviadoEm`, depois `id` × (b) o último enviado. Alerta (a) uma vez por marco, só para o vigente; marcos cruzados juntos emitem só o mais urgente; ordem notificar → registrar (pelo menos uma vez) × (b) alertar todo dia dentro da janela × (c) registrar antes de notificar (no máximo uma vez).
- **Escolha:** (a) em ambos. O último enviado pode ser um `.pfx` antigo reenviado por engano; o maior `validoAte` sempre adia o vencimento. Alerta repetido diariamente vira ruído; perder um alerta é pior que receber dois.
- **Como desfazer:** a regra fica só em `CertificadoVigente.escolher` e `PoliticaAlertaVencimento`, com testes de tabela.

### DA-54 · 2026-11-09 · E4-S04
- **Contexto:** `estado_coleta` não tem tenant, e a `[DECISAO] E4-S03` (D1: CNPJ em dois tenants) segue aberta. Uma rotação de um tenant poderia sobrescrever o certificado escolhido por outro.
- **Opções:** (a) a rotação troca por CAS (`certificado_id is not distinct from :esperado`) e só quando o certificado atual é nulo ou do mesmo tenant (conferido por `RepositorioCertificados.buscar`); senão devolve `OutroTenant` sem efeito × (b) o último tenant a rotacionar vence.
- **Escolha:** (a). É conservadora e não decide a D1 no lugar do autor. A troca nunca toca cursor, `ult_nsu`, `versao`, lease nem `bloqueado_ate` (inv. 1 e 2).
- **Como desfazer:** quando a D1 for decidida, trocar a checagem de tenant no `RotacionarCertificadoUseCase`; o SQL do CAS fica igual.

### DA-55 · 2026-11-09 · E4-S04
- **Contexto:** DA-53 não diz o que `escolher` devolve quando há vencido e renovação futura, sem nenhum válido.
- **Opções:** (a) `Vencido` (há vencido → coleta já está parada) × (b) `AindaNaoValido` (a renovação é a novidade).
- **Escolha:** (a). O titular está sem certificado utilizável agora; a E4-S03 precisa saber disso. `AindaNaoValido` só quando não há nenhum vencido. Teste `vencidoComRenovacaoFuturaEhVencido`.
- **Como desfazer:** trocar a ordem dos dois ramos finais em `CertificadoVigente.escolher`.

### DA-56 · 2026-11-11 · E1-S07
- **Contexto:** o passo de docs pede "as URLs" da NT Conjunta 2025.001 e da FAQ da Receita, mas o planejamento (2026-11-04) não as registrou em nenhum arquivo de estado. A regra de `docs/FONTES-OFICIAIS.md` proíbe link que não foi aberto na sessão.
- **Opções:** (a) registrar as fontes por nome, versão e data, sem URL, com a lacuna explícita; (b) pesquisar de novo as URLs agora; (c) escrever URLs de memória.
- **Escolha:** (a). O plano diz para não pesquisar de novo, e (c) viola a regra do arquivo. A NT 2014.002 v1.40 já tinha URL no item 8 e foi referenciada.
- **Como desfazer:** abrir as URLs oficiais e incluí-las na tabela do item 15 de `docs/FONTES-OFICIAIS.md`.

### DA-57 · 2026-11-11 · E3-S05
- **Contexto:** o módulo S3 precisa de imagem de teste com tag fixa e de nome de módulo (MinIO foi arquivado).
- **Opções:** tag numérica `chrislusf/seaweedfs:4.53` (a mais nova no Docker Hub hoje) × `latest`; módulo `infra-blob-s3` × `infra-blob-minio`.
- **Escolha:** `4.53`, declarada só em `SeaweedFsTestBase`; comando `server -s3 -s3.config=... -dir=/data` com autenticação ligada (identidade sintética em `s3.json`). Módulo `infra-blob-s3` (autorizado pelo autor). O SDK (checksum CRC32 padrão) funcionou sem `WHEN_REQUIRED`; `If-None-Match: *` em chave existente devolve 412 e preserva o conteúdo.
- **Como desfazer:** trocar a tag na constante `IMAGEM`; renomear o módulo em `settings.gradle.kts`.
