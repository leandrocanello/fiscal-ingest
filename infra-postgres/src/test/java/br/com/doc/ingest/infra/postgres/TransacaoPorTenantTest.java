package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TransacaoPorTenant}: o tenant vale só dentro da transação (set_config local) e não vaza para a
 * próxima transação na mesma conexão. Leituras como {@code fiscal_app_teste} (superuser ignora RLS).
 */
class TransacaoPorTenantTest extends PostgresTestBase {

    private static final String SCHEMA = "transacao_tenant";

    private static Jdbi admin;
    private static Jdbi app;

    private long tenantA;
    private List<Long> empresasA;
    private Connection conexao;

    @BeforeAll
    static void migrar() {
        var ds = dataSourceNoSchema(SCHEMA);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        app = Jdbi.create(dataSourceComoApp(SCHEMA));
    }

    @BeforeEach
    void semear() {
        admin.useHandle(h -> {
            h.execute("truncate empresa, tenant restart identity cascade");
            tenantA = inserirTenant(h, "Escritório A");
            long tenantB = inserirTenant(h, "Escritório B");
            empresasA = List.of(inserirEmpresa(h, tenantA, "11111111000101"), inserirEmpresa(h, tenantA, "11111111000202"));
            inserirEmpresa(h, tenantB, "22222222000101");
        });
    }

    @AfterEach
    void fecharConexao() throws SQLException {
        if (conexao != null) {
            conexao.close();
        }
    }

    @Test
    void executaComTenantDaSessaoEVeSoOsSeus() {
        var transacao = new TransacaoPorTenant(app);

        List<Long> ids = transacao.emTransacao(tenantA, h -> h.createQuery("select id from empresa order by id")
                .mapTo(Long.class).list());

        assertThat(ids).containsExactlyElementsOf(empresasA);
    }

    @Test
    void contextoNaoVazaParaATransacaoSeguinteNaMesmaConexao() throws SQLException {
        Jdbi umaConexao = jdbiDeConexaoUnica();
        var transacao = new TransacaoPorTenant(umaConexao);

        long dentro = transacao.emTransacao(tenantA, TransacaoPorTenantTest::contarEmpresas);

        assertThat(dentro).isEqualTo(empresasA.size());
        assertThat(umaConexao.withHandle(TransacaoPorTenantTest::tenantDaSessao)).isNullOrEmpty();
        assertThat(umaConexao.withHandle(TransacaoPorTenantTest::contarEmpresas)).isZero();
    }

    @Test
    void rollbackTambemLimpaOContexto() throws SQLException {
        Jdbi umaConexao = jdbiDeConexaoUnica();
        var transacao = new TransacaoPorTenant(umaConexao);

        assertThatThrownBy(() -> transacao.emTransacao(tenantA, h -> {
            contarEmpresas(h);
            throw new IllegalStateException("falha simulada");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(umaConexao.withHandle(TransacaoPorTenantTest::tenantDaSessao)).isNullOrEmpty();
        assertThat(umaConexao.withHandle(TransacaoPorTenantTest::contarEmpresas)).isZero();
    }

    @Test
    void tenantIdNaoPositivoEhRecusado() {
        var chamado = new AtomicBoolean(false);
        var transacao = new TransacaoPorTenant(app);

        for (long invalido : new long[] {0L, -1L}) {
            assertThatThrownBy(() -> transacao.emTransacao(invalido, h -> {
                chamado.set(true);
                return null;
            })).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(chamado).isFalse();
    }

    /** Jdbi sobre uma conexão só (que o Jdbi não fecha), para provar o que sobra entre transações. */
    private Jdbi jdbiDeConexaoUnica() throws SQLException {
        conexao = dataSourceComoApp(SCHEMA).getConnection();
        return Jdbi.create(conexao);
    }

    private static long contarEmpresas(Handle h) {
        return h.createQuery("select count(*) from empresa").mapTo(Long.class).one();
    }

    private static String tenantDaSessao(Handle h) {
        return h.createQuery("select current_setting('app.tenant_id', true)").mapTo(String.class).findOne().orElse(null);
    }

    private static long inserirTenant(Handle h, String nome) {
        return h.createQuery("insert into tenant (nome) values (:nome) returning id")
                .bind("nome", nome).mapTo(Long.class).one();
    }

    private static long inserirEmpresa(Handle h, long tenantId, String cnpj) {
        return h.createQuery("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :cnpj, :raiz, 'Empresa sintética', 'EMPRESA', 'PRODUCAO') returning id")
                .bind("t", tenantId).bind("cnpj", cnpj).bind("raiz", cnpj.substring(0, 8))
                .mapTo(Long.class).one();
    }
}
