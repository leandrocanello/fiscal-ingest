package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.catalogo.FalhaCatalogoException;
import br.com.doc.ingest.domain.catalogo.MotivoQuarentena;
import br.com.doc.ingest.domain.catalogo.Quarentena;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;

/**
 * {@link Quarentena} em Postgres (V10). Toda operação roda numa transação com o tenant fixado, para a
 * RLS valer; falhas viram {@link FalhaCatalogoException}, como no {@link JdbiCatalogo}.
 */
public final class JdbiQuarentena implements Quarentena {

    private final Jdbi jdbi;
    private final Clock clock;

    public JdbiQuarentena(Jdbi jdbi, Clock clock) {
        this.jdbi = jdbi;
        this.clock = clock;
    }

    /**
     * Trava o documento do arquivo (a mesma trava do {@link JdbiCatalogo#registrar}) e só então confere o
     * vínculo: quem leu a ficha sem vínculo e chega aqui depois de outro processor ter vinculado e resolvido
     * não deixa quarentena aberta (corrida vista no {@code CaosProcessadorTest}).
     */
    @Override
    public boolean abrir(long tenantId, long arquivoId, MotivoQuarentena motivo) {
        return noTenant(tenantId, h -> {
            h.createQuery("""
                            select d.id
                            from documento d
                            join arquivo a on a.documento_id = d.id and a.tenant_id = d.tenant_id
                            where a.id = :arquivo and a.tenant_id = :tenant
                            for update of d
                            """)
                    .bind("tenant", tenantId)
                    .bind("arquivo", arquivoId)
                    .mapTo(Long.class)
                    .findOne();
            return h.createUpdate("""
                            insert into quarentena (tenant_id, arquivo_id, motivo, criado_em)
                            select :tenant, :arquivo, :motivo, :agora
                            where not exists (
                                select 1
                                from documento_empresa de
                                join arquivo a on a.documento_id = de.documento_id and a.tenant_id = de.tenant_id
                                where a.id = :arquivo and a.tenant_id = :tenant)
                            on conflict (tenant_id, arquivo_id) where resolvido_em is null do nothing
                            """)
                    .bind("tenant", tenantId)
                    .bind("arquivo", arquivoId)
                    .bind("motivo", motivo.name())
                    .bind("agora", Timestamp.from(clock.instant()))
                    .execute() > 0;
        });
    }

    @Override
    public int resolverDoDocumento(long tenantId, long documentoId) {
        return noTenant(tenantId, h -> h.createUpdate("""
                        update quarentena q
                        set resolvido_em = :agora
                        from arquivo a
                        where a.id = q.arquivo_id and a.tenant_id = q.tenant_id
                          and q.tenant_id = :tenant and a.documento_id = :documento and q.resolvido_em is null
                        """)
                .bind("tenant", tenantId)
                .bind("documento", documentoId)
                .bind("agora", Timestamp.from(clock.instant()))
                .execute());
    }

    // Mesmo set_config do JdbiCatalogo; vira TransacaoPorTenant depois do merge da E3-S03.
    private <R> R noTenant(long tenantId, HandleCallback<R, RuntimeException> acao) {
        try {
            return jdbi.inTransaction(h -> {
                h.createQuery("select set_config('app.tenant_id', :tenant, true)")
                        .bind("tenant", Long.toString(tenantId))
                        .mapTo(String.class)
                        .one();
                return acao.withHandle(h);
            });
        } catch (JdbiException e) {
            // A causa fica de fora: o texto do JDBI traz os binds, e o do Postgres, a linha (inv. 13).
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
