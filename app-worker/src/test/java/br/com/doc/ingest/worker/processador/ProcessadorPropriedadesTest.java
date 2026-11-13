package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.doczip.ParserDfeNfe;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.EstadoDocumento;
import br.com.doc.ingest.domain.catalogo.FichaDocumento;
import br.com.doc.ingest.domain.catalogo.ProvenienciaInformada;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import br.com.doc.ingest.infra.postgres.JdbiCadastroEmpresas;
import br.com.doc.ingest.infra.postgres.JdbiCatalogo;
import br.com.doc.ingest.infra.postgres.JdbiQuarentena;
import br.com.doc.ingest.infra.postgres.Migracoes;
import br.com.doc.ingest.infra.postgres.PostgresTestBase;
import br.com.doc.ingest.infra.postgres.UsuarioAppCatalogo;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.lifecycle.BeforeTry;
import org.jdbi.v3.core.Jdbi;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do processor (CLAUDE.md §15 item 2, inv. 8 e 10): sequências de resumo, completo e eventos de
 * poucas notas, com repetições e embaralhadas, passando pelo {@link ParserDfeNfe} real e pelo Postgres
 * (Testcontainers) como usuário sem superuser. XML e CNPJs sintéticos.
 *
 * <p>As notas cobrem as empresas do tenant como emitente (pelo {@code autXML} do escritório), destinatária e
 * autorizada, além de uma nota sem empresa nenhuma. O vínculo pode depender de qual arquivo chegou (D1: o
 * evento só vincula o emitente da chave), então a comparação de ordens usa sempre o mesmo conjunto de arquivos.
 *
 * <p>Os itens (bruto + linha do ledger) são criados uma vez só; entre as tentativas só o catálogo é limpo, para
 * que a proveniência aponte o mesmo {@code ledger_nsu_id} em todas as ordens. {@code ShrinkingMode.FULL}
 * conforme a DA-31.
 */
class ProcessadorPropriedadesTest extends PostgresTestBase {

    private static final String SCHEMA = "processador_propriedades";
    private static final Instant AGORA = Instant.parse("2026-11-13T12:00:00Z");
    private static final FonteId FONTE = new FonteId("NFE_AN");

    private static final Cnpj ESCRITORIO = new Cnpj("44444444000144");
    private static final Cnpj CLIENTE = new Cnpj("11111111000191");
    private static final Cnpj DESTINATARIA = new Cnpj("55555555000155");
    private static final Cnpj TERCEIRO = new Cnpj("66666666000166");
    private static final Cnpj OUTRO_TERCEIRO = new Cnpj("77777777000177");

    /** Uma nota: quem emite, para quem, quem está no {@code autXML} e com qual CNPJ ela é consultada. */
    private record Nota(String chave, Cnpj emitente, Cnpj destinatario, List<Cnpj> autorizados, Cnpj consultado) { }

    private static final List<Nota> NOTAS = List.of(
            // destinatária do tenant, consultando com o próprio certificado (entrada)
            new Nota(ProcessadorColetadoIntegracaoTest.chave(TERCEIRO, 101), TERCEIRO, DESTINATARIA, List.of(),
                    DESTINATARIA),
            // cliente emitente, saída que chega pelo autXML do escritório
            new Nota(ProcessadorColetadoIntegracaoTest.chave(CLIENTE, 102), CLIENTE, TERCEIRO, List.of(ESCRITORIO),
                    ESCRITORIO),
            // empresa do tenant como autorizada no autXML (entrada)
            new Nota(ProcessadorColetadoIntegracaoTest.chave(TERCEIRO, 103), TERCEIRO, OUTRO_TERCEIRO,
                    List.of(DESTINATARIA), DESTINATARIA),
            // nenhuma empresa do tenant no conteúdo: quarentena
            new Nota(ProcessadorColetadoIntegracaoTest.chave(OUTRO_TERCEIRO, 104), OUTRO_TERCEIRO, TERCEIRO,
                    List.of(ESCRITORIO), ESCRITORIO));

    private static Jdbi admin;
    private static Catalogo catalogo;
    private static ProcessadorColetado processador;
    private static List<Long> tenants;
    private static List<ItemColetado> itens;

    private static synchronized void garantePronto() {
        if (admin != null) {
            return;
        }
        UsuarioAppCatalogo.garantirPapeis();
        var ds = dataSourceNoSchema(SCHEMA);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        UsuarioAppCatalogo.ligarRlsDeEmpresa(admin, SCHEMA);
        Jdbi app = UsuarioAppCatalogo.jdbi(admin, SCHEMA);
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        catalogo = new JdbiCatalogo(app, clock);
        var blobs = new BlobStoreEmMemoria();
        processador = new ProcessadorColetado(blobs, new ParserDfeNfe(), catalogo, new JdbiCadastroEmpresas(app),
                new JdbiQuarentena(app, clock));

        long a = novoTenant();
        empresa(a, ESCRITORIO, "ESCRITORIO");
        empresa(a, CLIENTE, "EMPRESA");
        empresa(a, DESTINATARIA, "EMPRESA");
        // O CNPJ da destinatária serve a dois tenants: cada um tem o seu documento.
        long b = novoTenant();
        empresa(b, DESTINATARIA, "EMPRESA");
        tenants = List.of(a, b);

        var criados = new ArrayList<ItemColetado>();
        long nsu = 0;
        for (Nota n : NOTAS) {
            criados.add(item(blobs, n.consultado(), ++nsu, "resNFe_v1.01.xsd",
                    ProcessadorColetadoIntegracaoTest.resNFe(n.chave(), n.emitente())));
            criados.add(item(blobs, n.consultado(), ++nsu, "procNFe_v4.00.xsd",
                    ProcessadorColetadoIntegracaoTest.procNFe(n.chave(), n.emitente(), n.destinatario(),
                            n.autorizados(), "100")));
            criados.add(item(blobs, n.consultado(), ++nsu, "procEventoNFe_v1.00.xsd",
                    ProcessadorColetadoIntegracaoTest.procEvento(n.chave(), n.emitente(), "210210")));
            criados.add(item(blobs, n.consultado(), ++nsu, "procEventoNFe_v1.00.xsd",
                    ProcessadorColetadoIntegracaoTest.procEvento(n.chave(), n.emitente(), "110111")));
        }
        itens = List.copyOf(criados);
    }

    /** Grava o bruto e o NSU no ledger, como o fetcher faria antes do processor (inv. 5). */
    private static ItemColetado item(BlobStoreEmMemoria blobs, Cnpj consultado, long nsu, String schema, String xml) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        String sha = Sha256.hex(bytes);
        var blob = blobs.gravar("raw/pull/nfe/" + sha + ".xml", bytes, sha);
        long ledgerId = admin.withHandle(h -> h.createUpdate("insert into ledger_nsu "
                        + "(fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values (:f, :c, 'PRODUCAO', :n, 'BAIXADO', now())")
                .bind("f", FONTE.id()).bind("c", consultado.valor()).bind("n", nsu)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        return new ItemColetado(FONTE, consultado, Ambiente.PRODUCAO, new Nsu(nsu), ledgerId, blob, schema, AGORA);
    }

    private static long novoTenant() {
        return admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private static void empresa(long tenantId, Cnpj cnpj, String tipo) {
        admin.useHandle(h -> h.createUpdate("insert into empresa "
                        + "(tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente, ativo) "
                        + "values (:t, :c, :r, 'Empresa Sintetica', :tipo, 'PRODUCAO', true)")
                .bind("t", tenantId).bind("c", cnpj.valor()).bind("r", cnpj.valor().substring(0, 8))
                .bind("tipo", tipo).execute());
    }

    /** Só o catálogo e a quarentena: tenants, empresas e ledger ficam, para os ids se manterem. */
    private static void limparCatalogo() {
        admin.useHandle(h -> h.execute("truncate quarentena, proveniencia, arquivo, documento_empresa, documento"));
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        limparCatalogo();
    }

    /** Índices em {@link #itens}: repetições acontecem naturalmente. */
    @Provide
    Arbitrary<List<Integer>> chegadas() {
        return Arbitraries.integers().between(0, NOTAS.size() * 4 - 1).list().ofMinSize(1).ofMaxSize(12);
    }

    /** A mesma sequência embaralhada, com a primeira metade entregue de novo (relay que publica duas vezes). */
    private static List<Integer> comReentregas(List<Integer> chegadas, long semente) {
        var sequencia = new ArrayList<>(chegadas);
        sequencia.addAll(chegadas.subList(0, (chegadas.size() + 1) / 2));
        Collections.shuffle(sequencia, new Random(semente));
        return sequencia;
    }

    private static void processarTodos(List<Integer> sequencia) {
        sequencia.forEach(i -> assertThat(processador.processar(itens.get(i)))
                .isInstanceOf(ResultadoProcessamento.Registrado.class));
    }

    // ===== propriedades =====

    @Property(tries = 10, shrinking = ShrinkingMode.FULL)
    void ordemDeChegadaNaoMudaAFicha(@ForAll("chegadas") List<Integer> chegadas, @ForAll long semente) {
        processarTodos(chegadas);
        var fichas = fichas();
        limparCatalogo();

        processarTodos(comReentregas(chegadas, semente));

        assertThat(fichas()).isEqualTo(fichas);
    }

    @Property(tries = 10, shrinking = ShrinkingMode.FULL)
    void documentoComVinculoNuncaFicaComQuarentenaAberta(@ForAll("chegadas") List<Integer> chegadas,
                                                         @ForAll long semente) {
        processarTodos(comReentregas(chegadas, semente));

        for (FichaDocumento ficha : todasAsFichas()) {
            if (!ficha.vinculos().isEmpty()) {
                assertThat(quarentenasAbertasDoDocumento(ficha.documentoId()))
                        .as("documento %d tem vínculo", ficha.documentoId()).isZero();
            }
        }
    }

    @Property(tries = 10, shrinking = ShrinkingMode.FULL)
    void documentoSemVinculoSempreTemQuarentenaAberta(@ForAll("chegadas") List<Integer> chegadas,
                                                      @ForAll long semente) {
        processarTodos(comReentregas(chegadas, semente));

        for (FichaDocumento ficha : todasAsFichas()) {
            if (ficha.vinculos().isEmpty()) {
                // Cada arquivo chegou com o documento ainda sem vínculo: todos ficam em quarentena, uma vez só.
                for (ArquivoFicha arquivo : ficha.arquivos()) {
                    assertThat(quarentenasAbertasDoArquivo(arquivo.id()))
                            .as("arquivo %d de documento sem vínculo", arquivo.id()).isEqualTo(1);
                }
            }
        }
    }

    @Property(tries = 10, shrinking = ShrinkingMode.FULL)
    void umDocumentoPorTenantEChave(@ForAll("chegadas") List<Integer> chegadas, @ForAll long semente) {
        processarTodos(comReentregas(chegadas, semente));

        var esperados = new ArrayList<String>();
        chegadas.stream().map(itens::get).distinct().forEach(item -> {
            String chave = NOTAS.get(itens.indexOf(item) / 4).chave();
            tenantsDe(item.cnpjConsultado()).forEach(t -> esperados.add(t + ":" + chave));
        });
        List<String> documentos = admin.withHandle(h -> h.createQuery(
                "select tenant_id || ':' || chave_acesso from documento").mapTo(String.class).list());
        assertThat(documentos)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(esperados.stream().distinct().toList());
    }

    // ===== apoio =====

    private static List<Long> tenantsDe(Cnpj consultado) {
        return consultado.equals(DESTINATARIA) ? tenants : List.of(tenants.getFirst());
    }

    private static List<FichaDocumento> todasAsFichas() {
        var fichas = new ArrayList<FichaDocumento>();
        for (long t : tenants) {
            for (Nota n : NOTAS) {
                catalogo.consultar(t, new ChaveAcesso(n.chave())).ifPresent(fichas::add);
            }
        }
        return fichas;
    }

    /** Todas as fichas sem ids, na ordem (tenant, nota). */
    private static List<FichaComparavel> fichas() {
        return todasAsFichas().stream().map(FichaComparavel::de).toList();
    }

    private record FichaComparavel(long tenantId, ChaveAcesso chave, EstadoDocumento estado,
                                   List<VinculoEmpresa> vinculos, List<ArquivoComparavel> arquivos) {

        static FichaComparavel de(FichaDocumento f) {
            return new FichaComparavel(f.tenantId(), f.chave(), f.estado(), f.vinculos(),
                    f.arquivos().stream().map(ArquivoComparavel::de).toList());
        }
    }

    private record ArquivoComparavel(TipoArquivo tipo, Optional<String> eventoTipo, Optional<Integer> eventoSeq,
                                     BlobRef blob, Instant recebidoEm, List<ProvenienciaInformada> proveniencias) {

        static ArquivoComparavel de(ArquivoFicha a) {
            return new ArquivoComparavel(a.tipo(), a.eventoTipo(), a.eventoSeq(), a.blob(), a.recebidoEm(),
                    a.proveniencias());
        }
    }

    private static long quarentenasAbertasDoDocumento(long documentoId) {
        return admin.withHandle(h -> h.createQuery("select count(*) from quarentena q "
                        + "join arquivo a on a.id = q.arquivo_id "
                        + "where q.resolvido_em is null and a.documento_id = :d")
                .bind("d", documentoId).mapTo(Long.class).one());
    }

    private static long quarentenasAbertasDoArquivo(long arquivoId) {
        return admin.withHandle(h -> h.createQuery("select count(*) from quarentena "
                        + "where resolvido_em is null and arquivo_id = :a and motivo = 'SEM_EMPRESA'")
                .bind("a", arquivoId).mapTo(Long.class).one());
    }
}
