package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.catalogo.CadastroEmpresas;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Função {@code tenants_do_cnpj_consultado} (V11; D3 (a) da E3-S07): o processor descobre os tenants de um
 * CNPJ consultado sem ter tenant e sem ler a {@code empresa}. Sempre como usuário sem superuser, com RLS em
 * {@code empresa}.
 */
class TenantsDoCnpjConsultadoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Cnpj CONSULTADO = new Cnpj("11222333000181");
    private static final Cnpj OUTRO = new Cnpj("44555666000172");

    private Jdbi admin;
    private Jdbi app;
    private CadastroEmpresas cadastro;
    private long tenantA;
    private long tenantB;
    private long tenantC;

    @BeforeEach
    void prepara() {
        String schema = "tenants_cnpj_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        UsuarioAppCatalogo.ligarRlsDeEmpresa(admin, schema);
        app = UsuarioAppCatalogo.jdbi(admin, schema);
        cadastro = new JdbiCadastroEmpresas(app);
        tenantA = novoTenant();
        tenantB = novoTenant();
        tenantC = novoTenant();
    }

    private long novoTenant() {
        return admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private void empresa(long tenant, Cnpj cnpj, String ambiente, boolean ativo) {
        admin.useHandle(h -> h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente, ativo) "
                        + "values (:t, :c, :r, 'Empresa Sintetica', 'EMPRESA', :a, :ativo)")
                .bind("t", tenant).bind("c", cnpj.valor()).bind("r", cnpj.valor().substring(0, 8))
                .bind("a", ambiente).bind("ativo", ativo).execute());
    }

    @Test
    void devolveTodosOsTenantsDoCnpj() {
        empresa(tenantA, CONSULTADO, "PRODUCAO", true);
        empresa(tenantB, CONSULTADO, "PRODUCAO", true);
        empresa(tenantC, OUTRO, "PRODUCAO", true);

        assertThat(cadastro.tenantsDoCnpjConsultado(CONSULTADO, Ambiente.PRODUCAO))
                .containsExactly(tenantA, tenantB);
    }

    @Test
    void ignoraEmpresaInativa() {
        empresa(tenantA, CONSULTADO, "PRODUCAO", true);
        empresa(tenantB, CONSULTADO, "PRODUCAO", false);

        assertThat(cadastro.tenantsDoCnpjConsultado(CONSULTADO, Ambiente.PRODUCAO)).containsExactly(tenantA);
    }

    @Test
    void ignoraOutroAmbiente() {
        empresa(tenantA, CONSULTADO, "HOMOLOGACAO", true);
        empresa(tenantB, CONSULTADO, "PRODUCAO", true);

        assertThat(cadastro.tenantsDoCnpjConsultado(CONSULTADO, Ambiente.HOMOLOGACAO)).containsExactly(tenantA);
    }

    @Test
    void devolveSoIdsDeTenant() {
        empresa(tenantA, CONSULTADO, "PRODUCAO", true);

        // Direto no banco, como o papel da aplicação: uma coluna só, bigint; nada de CNPJ, razão social ou id de empresa.
        List<String> colunas = app.withHandle(h -> h.createQuery(
                        "select * from tenants_do_cnpj_consultado(:c, 'PRODUCAO')")
                .bind("c", CONSULTADO.valor())
                .scanResultSet((rs, ctx) -> {
                    var meta = rs.get().getMetaData();
                    List<String> lista = new java.util.ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        lista.add(meta.getColumnTypeName(i));
                    }
                    return lista;
                }));

        assertThat(colunas).containsExactly("int8");
    }

    @Test
    void papelDaAplicacaoSemTenantNaoLeEmpresa() {
        empresa(tenantA, CONSULTADO, "PRODUCAO", true);

        long visiveis = app.withHandle(h -> h.createQuery("select count(*) from empresa").mapTo(Long.class).one());

        assertThat(visiveis).isZero();
        assertThat(cadastro.tenantsDoCnpjConsultado(CONSULTADO, Ambiente.PRODUCAO)).containsExactly(tenantA);
    }

    @Test
    void cnpjSemEmpresaDevolveVazio() {
        empresa(tenantA, OUTRO, "PRODUCAO", true);

        assertThat(cadastro.tenantsDoCnpjConsultado(CONSULTADO, Ambiente.PRODUCAO)).isEmpty();
    }
}
