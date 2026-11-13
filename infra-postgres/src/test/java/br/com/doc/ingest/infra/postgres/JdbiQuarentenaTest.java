package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.catalogo.FalhaCatalogoException;
import br.com.doc.ingest.domain.catalogo.MotivoQuarentena;
import br.com.doc.ingest.domain.catalogo.Quarentena;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** {@code JdbiQuarentena} contra Postgres real, sempre como usuário sem superuser (a RLS vale). */
class JdbiQuarentenaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CHAVE = "35260111222333000181550010000000011000000011";
    private static final String CHAVE_2 = "35260111222333000181550010000000021000000022";
    private static final Instant AGORA = Instant.parse("2026-11-02T15:00:00Z");
    private static final Instant DEPOIS = AGORA.plusSeconds(3600);

    private Jdbi admin;
    private Quarentena quarentena;
    private Quarentena quarentenaDepois;
    private long tenant;
    private long documento;
    private long resumo;
    private long evento;
    private long outroDocumento;
    private long arquivoDoOutro;

    private record Linha(long arquivoId, String motivo, Instant criadoEm, Instant resolvidoEm) {
    }

    @BeforeEach
    void prepara() {
        String schema = "jdbi_quarentena_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        Jdbi app = UsuarioAppCatalogo.jdbi(admin, schema);
        quarentena = new JdbiQuarentena(app, Clock.fixed(AGORA, ZoneOffset.UTC));
        quarentenaDepois = new JdbiQuarentena(app, Clock.fixed(DEPOIS, ZoneOffset.UTC));
        tenant = admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        documento = inserirDocumento(CHAVE);
        resumo = inserirArquivo(documento, "a");
        evento = inserirArquivo(documento, "b");
        outroDocumento = inserirDocumento(CHAVE_2);
        arquivoDoOutro = inserirArquivo(outroDocumento, "c");
    }

    private long inserirDocumento(String chave) {
        return admin.withHandle(h -> h.createUpdate("insert into documento (tenant_id, chave_acesso, modelo, situacao, so_declarado, atualizado_em) "
                        + "values (:t, :c, '55', 'SO_RESUMO', false, now())")
                .bind("t", tenant).bind("c", chave)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long inserirArquivo(long doc, String sha) {
        return admin.withHandle(h -> h.createUpdate("insert into arquivo (tenant_id, documento_id, tipo, blob_ref, sha256, recebido_em) "
                        + "values (:t, :d, 'RESUMO', 'doc/x.xml.gz', :s, now())")
                .bind("t", tenant).bind("d", doc).bind("s", sha.repeat(64))
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private List<Linha> linhas() {
        return admin.withHandle(h -> h.createQuery(
                        "select arquivo_id, motivo, criado_em, resolvido_em from quarentena order by id")
                .map((rs, ctx) -> new Linha(rs.getLong("arquivo_id"), rs.getString("motivo"),
                        rs.getTimestamp("criado_em").toInstant(),
                        rs.getTimestamp("resolvido_em") == null ? null : rs.getTimestamp("resolvido_em").toInstant()))
                .list());
    }

    @Test
    void abrirEhIdempotente() {
        boolean primeira = quarentena.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA);
        boolean segunda = quarentenaDepois.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA);

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        assertThat(linhas()).containsExactly(new Linha(resumo, "SEM_EMPRESA", AGORA, null));
    }

    @Test
    void resolverFechaTodasDoDocumento() {
        quarentena.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA);
        quarentena.abrir(tenant, evento, MotivoQuarentena.SEM_EMPRESA);
        quarentena.abrir(tenant, arquivoDoOutro, MotivoQuarentena.SEM_EMPRESA);

        int fechadas = quarentenaDepois.resolverDoDocumento(tenant, documento);

        assertThat(fechadas).isEqualTo(2);
        assertThat(linhas()).containsExactly(
                new Linha(resumo, "SEM_EMPRESA", AGORA, DEPOIS),
                new Linha(evento, "SEM_EMPRESA", AGORA, DEPOIS),
                new Linha(arquivoDoOutro, "SEM_EMPRESA", AGORA, null));
    }

    @Test
    void resolverSemAbertaEhNoOp() {
        quarentena.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA);
        quarentena.resolverDoDocumento(tenant, documento);

        int deNovo = quarentenaDepois.resolverDoDocumento(tenant, documento);
        int semNenhuma = quarentenaDepois.resolverDoDocumento(tenant, outroDocumento);

        assertThat(deNovo).isZero();
        assertThat(semNenhuma).isZero();
        assertThat(linhas()).containsExactly(new Linha(resumo, "SEM_EMPRESA", AGORA, AGORA));
    }

    @Test
    void reabreDepoisDeResolvida() {
        quarentena.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA);
        quarentena.resolverDoDocumento(tenant, documento);

        boolean reaberta = quarentenaDepois.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA);

        assertThat(reaberta).isTrue();
        assertThat(linhas()).containsExactly(
                new Linha(resumo, "SEM_EMPRESA", AGORA, AGORA),
                new Linha(resumo, "SEM_EMPRESA", DEPOIS, null));
    }

    @Test
    void naoAbreEmDocumentoComVinculo() {
        long empresa = admin.withHandle(h -> h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, '11222333000181', '11222333', 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        admin.useHandle(h -> h.createUpdate("insert into documento_empresa (tenant_id, documento_id, empresa_id, direcao) "
                        + "values (:t, :d, :e, 'ENTRADA')")
                .bind("t", tenant).bind("d", documento).bind("e", empresa).execute());

        boolean abriu = quarentena.abrir(tenant, evento, MotivoQuarentena.SEM_EMPRESA);

        assertThat(abriu).isFalse();
        assertThat(linhas()).isEmpty();
    }

    @Test
    void falhaNaoExpoeParametros() {
        admin.useHandle(h -> h.execute("alter table quarentena add constraint recusa_arquivo "
                + "check (arquivo_id <> " + resumo + ")"));

        var falha = catchThrowableOfType(FalhaCatalogoException.class,
                () -> quarentena.abrir(tenant, resumo, MotivoQuarentena.SEM_EMPRESA));

        assertThat(falha.codigo()).isEqualTo("23514");
        assertThat(falha.getCause()).isNull();
        assertThat(falha.getSuppressed()).isEmpty();
        assertThat(pilhaImpressa(falha)).doesNotContain("Failing row", "arquivo_id", "SEM_EMPRESA", "tenant");

        admin.useHandle(h -> h.execute("revoke update on quarentena from fiscal_app"));
        var falhaAoResolver = catchThrowableOfType(FalhaCatalogoException.class,
                () -> quarentena.resolverDoDocumento(tenant, documento));
        assertThat(falhaAoResolver.codigo()).isEqualTo("42501");
        assertThat(falhaAoResolver.getCause()).isNull();
    }

    private static String pilhaImpressa(Throwable falha) {
        var saida = new StringWriter();
        falha.printStackTrace(new PrintWriter(saida));
        return saida.toString();
    }
}
