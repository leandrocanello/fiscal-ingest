package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.doczip.ParserDfeNfe;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.FalhaCatalogoException;
import br.com.doc.ingest.domain.catalogo.MotivoQuarentena;
import br.com.doc.ingest.domain.catalogo.Quarentena;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
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
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.doc.ingest.worker.processador.ProcessadorColetadoIntegracaoTest.chave;
import static br.com.doc.ingest.worker.processador.ProcessadorColetadoIntegracaoTest.procEvento;
import static br.com.doc.ingest.worker.processador.ProcessadorColetadoIntegracaoTest.procNFe;
import static br.com.doc.ingest.worker.processador.ProcessadorColetadoIntegracaoTest.resNFe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caos do processor (CLAUDE.md §15 item 7): queda no meio, relay repetido e processamentos concorrentes da
 * mesma nota terminam com um documento só (inv. 8), sem perda (inv. 5) e sem quarentena aberta em documento
 * que tem vínculo (§7.3). Postgres real, usuário sem superuser, {@link ParserDfeNfe} real, XML sintético.
 */
class CaosProcessadorTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Instant AGORA = Instant.parse("2026-11-13T12:00:00Z");
    private static final FonteId FONTE = new FonteId("NFE_AN");
    /** Empresa destinatária, consultando com o próprio certificado. */
    private static final Cnpj DESTINATARIA = new Cnpj("55555555000155");
    /** Emitente fora do tenant. */
    private static final Cnpj TERCEIRO = new Cnpj("66666666000166");
    private static final String CHAVE = chave(TERCEIRO, 1);

    private Jdbi admin;
    private Catalogo catalogo;
    private JdbiCadastroEmpresas cadastro;
    private JdbiQuarentena quarentena;
    private ProcessadorColetado processador;
    private final BlobStoreEmMemoria blobs = new BlobStoreEmMemoria();
    private long tenant;
    private long destinataria;
    private long nsu;

    @BeforeEach
    void prepara() {
        String schema = "caos_processador_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        UsuarioAppCatalogo.ligarRlsDeEmpresa(admin, schema);
        Jdbi app = UsuarioAppCatalogo.jdbi(admin, schema);
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        catalogo = new JdbiCatalogo(app, clock);
        cadastro = new JdbiCadastroEmpresas(app);
        quarentena = new JdbiQuarentena(app, clock);
        processador = processadorCom(quarentena);
        tenant = admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        destinataria = admin.withHandle(h -> h.createUpdate("insert into empresa "
                        + "(tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente, ativo) "
                        + "values (:t, :c, :r, 'Empresa Sintetica', 'EMPRESA', 'PRODUCAO', true)")
                .bind("t", tenant).bind("c", DESTINATARIA.valor()).bind("r", DESTINATARIA.valor().substring(0, 8))
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    // ===== cenários =====

    @Test
    void quedaEntreRegistrarEAbrirQuarentenaReprocessaSemPerda() {
        var interceptada = new QuarentenaInterceptada(quarentena);
        interceptada.falharUmaVez = true;
        var comQueda = processadorCom(interceptada);
        // O evento só vincula o emitente da chave (D1), que não é do tenant: precisa de quarentena.
        ItemColetado evento = evento("210210");

        assertThatThrownBy(() -> comQueda.processar(evento)).isInstanceOf(FalhaCatalogoException.class);
        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("quarentena")).isZero();

        comQueda.processar(evento);

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(contar("proveniencia")).isEqualTo(1);
        assertThat(quarentenasAbertas()).isEqualTo(1);
        assertThat(contar("quarentena")).isEqualTo(1);
    }

    @RepeatedTest(20)
    void relayEntregaDuasVezesEhNoOp() throws Exception {
        ItemColetado item = completo();

        concorrer(List.of(item, item));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(contar("proveniencia")).isEqualTo(1);
        assertThat(contar("documento_empresa")).isEqualTo(1);
        assertThat(contar("quarentena")).isZero();
    }

    @RepeatedTest(20)
    void processamentosConcorrentesDaMesmaNotaCriamUmDocumento() throws Exception {
        ItemColetado resumo = resumo();
        ItemColetado completo = completo();
        ItemColetado evento = evento("210210");

        concorrer(List.of(resumo, completo, evento, resumo, completo, evento));

        var ficha = catalogo.consultar(tenant, new ChaveAcesso(CHAVE)).orElseThrow();
        assertThat(contar("documento")).isEqualTo(1);
        assertThat(ficha.arquivos()).extracting(ArquivoFicha::tipo)
                .containsExactlyInAnyOrder(TipoArquivo.RESUMO, TipoArquivo.COMPLETO, TipoArquivo.EVENTO);
        assertThat(contar("proveniencia")).isEqualTo(3);
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        assertThat(ficha.vinculos()).containsExactly(new VinculoEmpresa(destinataria, Direcao.ENTRADA));
        assertThat(quarentenasAbertas()).isZero();
    }

    @RepeatedTest(20)
    void abrirEResolverConcorrentesTerminamFechados() throws Exception {
        ItemColetado evento = evento("210210");
        ItemColetado completo = completo();

        concorrer(List.of(evento, completo));

        assertThat(catalogo.consultar(tenant, new ChaveAcesso(CHAVE)).orElseThrow().vinculos())
                .containsExactly(new VinculoEmpresa(destinataria, Direcao.ENTRADA));
        assertThat(quarentenasAbertas()).isZero();
    }

    /**
     * A pior intercalação, forçada: o evento lê a ficha sem vínculo, a nota com vínculo chega, resolve (não há
     * nada aberto) e só então o evento abre a quarentena. Sem trava, a quarentena ficaria aberta para sempre.
     */
    @Test
    void abrirAtrasadoDepoisDoVinculoNaoDeixaQuarentenaAberta() {
        ItemColetado evento = evento("210210");
        ItemColetado completo = completo();
        var interceptada = new QuarentenaInterceptada(quarentena);
        interceptada.antesDeAbrir = () -> processador.processar(completo);

        processadorCom(interceptada).processar(evento);

        assertThat(catalogo.consultar(tenant, new ChaveAcesso(CHAVE)).orElseThrow().vinculos())
                .containsExactly(new VinculoEmpresa(destinataria, Direcao.ENTRADA));
        assertThat(quarentenasAbertas()).isZero();
    }

    // ===== apoio =====

    private ProcessadorColetado processadorCom(Quarentena q) {
        return new ProcessadorColetado(blobs, new ParserDfeNfe(), catalogo, cadastro, q);
    }

    private ItemColetado resumo() {
        return item("resNFe_v1.01.xsd", resNFe(CHAVE, TERCEIRO));
    }

    private ItemColetado completo() {
        return item("procNFe_v4.00.xsd", procNFe(CHAVE, TERCEIRO, DESTINATARIA, List.of(), "100"));
    }

    private ItemColetado evento(String tpEvento) {
        return item("procEventoNFe_v1.00.xsd", procEvento(CHAVE, TERCEIRO, tpEvento));
    }

    /** Grava o bruto e o NSU no ledger, como o fetcher faria antes do processor (inv. 5). */
    private ItemColetado item(String schema, String xml) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        String sha = Sha256.hex(bytes);
        var blob = blobs.gravar("raw/pull/nfe/" + sha + ".xml", bytes, sha);
        long nsuAtual = ++nsu;
        long ledgerId = admin.withHandle(h -> h.createUpdate("insert into ledger_nsu "
                        + "(fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values (:f, :c, 'PRODUCAO', :n, 'BAIXADO', now())")
                .bind("f", FONTE.id()).bind("c", DESTINATARIA.valor()).bind("n", nsuAtual)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        return new ItemColetado(FONTE, DESTINATARIA, Ambiente.PRODUCAO, new Nsu(nsuAtual), ledgerId, blob, schema,
                AGORA);
    }

    /** Dispara todos ao mesmo tempo (barreira); qualquer exceção, inclusive deadlock, reprova o teste no {@code get}. */
    private List<ResultadoProcessamento> concorrer(List<ItemColetado> itens) throws Exception {
        var barreira = new CyclicBarrier(itens.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futuros = new ArrayList<Future<ResultadoProcessamento>>();
            for (var item : itens) {
                futuros.add(executor.submit(() -> {
                    barreira.await(10, TimeUnit.SECONDS);
                    return processador.processar(item);
                }));
            }
            var resultados = new ArrayList<ResultadoProcessamento>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        }
    }

    private long contar(String tabela) {
        return admin.withHandle(h -> h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one());
    }

    private long quarentenasAbertas() {
        return admin.withHandle(h -> h.createQuery("select count(*) from quarentena where resolvido_em is null")
                .mapTo(Long.class).one());
    }

    /** Delegação com ganchos: queda simulada uma vez e ação antes de abrir (para forçar intercalações). */
    private static final class QuarentenaInterceptada implements Quarentena {
        private final Quarentena real;
        boolean falharUmaVez;
        Runnable antesDeAbrir = () -> { };

        QuarentenaInterceptada(Quarentena real) {
            this.real = real;
        }

        @Override
        public boolean abrir(long tenantId, long arquivoId, MotivoQuarentena motivo) {
            if (falharUmaVez) {
                falharUmaVez = false;
                throw new FalhaCatalogoException("08006");
            }
            antesDeAbrir.run();
            return real.abrir(tenantId, arquivoId, motivo);
        }

        @Override
        public int resolverDoDocumento(long tenantId, long documentoId) {
            return real.resolverDoDocumento(tenantId, documentoId);
        }
    }
}
