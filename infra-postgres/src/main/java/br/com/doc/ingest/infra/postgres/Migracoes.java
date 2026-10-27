package br.com.doc.ingest.infra.postgres;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/** Aplica as migrations Flyway (classpath:db/migration) no banco. */
public final class Migracoes {

    private Migracoes() {
    }

    /** @return quantidade de migrations aplicadas nesta chamada */
    public static int aplicar(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
