package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.catalogo.CadastroEmpresas;
import br.com.doc.ingest.domain.catalogo.EmpresaDoTenant;
import br.com.doc.ingest.domain.catalogo.TipoEmpresa;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code JdbiCadastroEmpresas.empresasDoTenant} como usuário sem superuser, com RLS em {@code empresa}. */
class JdbiCadastroEmpresasTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private Jdbi admin;
    private CadastroEmpresas cadastro;
    private long tenantA;
    private long tenantB;

    @BeforeEach
    void prepara() {
        String schema = "cadastro_empresas_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        UsuarioAppCatalogo.ligarRlsDeEmpresa(admin, schema);
        cadastro = new JdbiCadastroEmpresas(UsuarioAppCatalogo.jdbi(admin, schema));
        tenantA = novoTenant();
        tenantB = novoTenant();
    }

    private long novoTenant() {
        return admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long empresa(long tenant, String cnpj, String tipo, String ambiente, boolean ativo) {
        return admin.withHandle(h -> h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente, ativo) "
                        + "values (:t, :c, :r, 'Empresa Sintetica', :tipo, :a, :ativo)")
                .bind("t", tenant).bind("c", cnpj).bind("r", cnpj.substring(0, 8))
                .bind("tipo", tipo).bind("a", ambiente).bind("ativo", ativo)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    @Test
    void empresasDoTenantSoAsAtivasDoAmbiente() {
        long escritorio = empresa(tenantA, "11222333000181", "ESCRITORIO", "PRODUCAO", true);
        long cliente = empresa(tenantA, "44555666000172", "EMPRESA", "PRODUCAO", true);
        empresa(tenantA, "77888999000163", "EMPRESA", "PRODUCAO", false);
        empresa(tenantA, "22333444000154", "EMPRESA", "HOMOLOGACAO", true);

        assertThat(cadastro.empresasDoTenant(tenantA, Ambiente.PRODUCAO)).containsExactly(
                new EmpresaDoTenant(escritorio, new Cnpj("11222333000181"), TipoEmpresa.ESCRITORIO),
                new EmpresaDoTenant(cliente, new Cnpj("44555666000172"), TipoEmpresa.EMPRESA));
    }

    @Test
    void tenantANaoListaEmpresasDeB() {
        long deA = empresa(tenantA, "11222333000181", "EMPRESA", "PRODUCAO", true);
        empresa(tenantB, "44555666000172", "EMPRESA", "PRODUCAO", true);
        empresa(tenantB, "11222333000181", "EMPRESA", "PRODUCAO", true);

        assertThat(cadastro.empresasDoTenant(tenantA, Ambiente.PRODUCAO)).extracting(EmpresaDoTenant::id)
                .containsExactly(deA);
    }
}
