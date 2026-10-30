package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;

/**
 * Abre uma transação com o tenant fixado em {@value #VARIAVEL_TENANT} (ADR-013, V5). O valor é local à
 * transação ({@code set_config(..., true)}): some no commit ou no rollback e não vaza para a próxima
 * transação da mesma conexão. Sempre por bind, nunca concatenado.
 */
public final class TransacaoPorTenant {

    static final String VARIAVEL_TENANT = "app.tenant_id";

    private final Jdbi jdbi;

    public TransacaoPorTenant(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public <R, X extends Exception> R emTransacao(long tenantId, HandleCallback<R, X> callback) throws X {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId deve ser positivo");
        }
        return jdbi.inTransaction(h -> {
            h.createQuery("select set_config(:variavel, :tenant, true)")
                    .bind("variavel", VARIAVEL_TENANT)
                    .bind("tenant", Long.toString(tenantId))
                    .mapTo(String.class).one();
            return callback.withHandle(h);
        });
    }
}
