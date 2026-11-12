package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.catalogo.CadastroEmpresas;
import br.com.doc.ingest.domain.catalogo.EmpresaDoTenant;
import br.com.doc.ingest.domain.catalogo.FalhaCatalogoException;
import br.com.doc.ingest.domain.catalogo.TipoEmpresa;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link CadastroEmpresas} em Postgres. Os tenants do CNPJ consultado vêm da função da V11, sem tenant fixado;
 * as empresas do tenant são lidas numa transação com o tenant fixado, para a RLS valer. Falhas viram
 * {@link FalhaCatalogoException}, como no {@link JdbiCatalogo}.
 */
public final class JdbiCadastroEmpresas implements CadastroEmpresas {

    private final Jdbi jdbi;

    public JdbiCadastroEmpresas(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public List<Long> tenantsDoCnpjConsultado(Cnpj cnpjConsultado, Ambiente ambiente) {
        return traduzindo(() -> jdbi.withHandle(h -> h.createQuery(
                        "select * from tenants_do_cnpj_consultado(:cnpj, :ambiente)")
                .bind("cnpj", cnpjConsultado.valor())
                .bind("ambiente", ambiente.name())
                .mapTo(Long.class)
                .list()));
    }

    @Override
    public List<EmpresaDoTenant> empresasDoTenant(long tenantId, Ambiente ambiente) {
        // Mesmo set_config do JdbiCatalogo; vira TransacaoPorTenant depois do merge da E3-S03.
        return traduzindo(() -> jdbi.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', :tenant, true)")
                    .bind("tenant", Long.toString(tenantId))
                    .mapTo(String.class)
                    .one();
            return h.createQuery("""
                            select id, cnpj, tipo from empresa
                            where tenant_id = :tenant and ambiente = :ambiente and ativo
                            order by id
                            """)
                    .bind("tenant", tenantId)
                    .bind("ambiente", ambiente.name())
                    .map((rs, ctx) -> new EmpresaDoTenant(rs.getLong("id"), new Cnpj(rs.getString("cnpj")),
                            TipoEmpresa.valueOf(rs.getString("tipo"))))
                    .list();
        }));
    }

    private static <R> R traduzindo(Supplier<R> acao) {
        try {
            return acao.get();
        } catch (JdbiException e) {
            // A causa fica de fora: o texto do JDBI traz os binds (CNPJ), e o do Postgres, a linha (inv. 13).
            throw new FalhaCatalogoException(sqlState(e));
        }
    }

    private static String sqlState(Throwable falha) {
        for (Throwable t = falha; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "desconhecido";
    }
}
