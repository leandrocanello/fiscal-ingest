package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.postgresql.ds.PGSimpleDataSource;

/** Cria o {@link Jdbi}. Sem pool por ora; o pool entra com os módulos {@code app-*}. */
public final class FabricaJdbi {

    private FabricaJdbi() {
    }

    public static Jdbi criar(PostgresConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.url());
        ds.setUser(config.usuario());
        ds.setPassword(new String(config.senha()));
        return Jdbi.create(ds).installPlugin(new PostgresPlugin());
    }
}
