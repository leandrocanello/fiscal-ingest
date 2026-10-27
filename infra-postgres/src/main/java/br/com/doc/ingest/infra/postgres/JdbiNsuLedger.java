package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.NsuLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

/**
 * {@link NsuLedger} em Postgres. Append-only e idempotente: reinserir o mesmo NSU é no-op
 * e o primeiro status registrado prevalece (ADR-005, invariantes 5 e 6).
 */
public final class JdbiNsuLedger implements NsuLedger {

    private final Jdbi jdbi;
    private final Ambiente ambiente;
    private final Clock clock;

    public JdbiNsuLedger(Jdbi jdbi, Ambiente ambiente, Clock clock) {
        this.jdbi = jdbi;
        this.ambiente = ambiente;
        this.clock = clock;
    }

    @Override
    public void registrar(FonteId fonte, Cnpj cnpj, EntradaLedger entrada) {
        jdbi.useHandle(h -> h.createUpdate("""
                        insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em)
                        values (:fonte, :cnpj, :ambiente, :nsu, :status, :recebidoEm)
                        on conflict (fonte, cnpj_consultado, ambiente, nsu) do nothing
                        """)
                .bind("fonte", fonte.id())
                .bind("cnpj", cnpj.valor())
                .bind("ambiente", ambiente.name())
                .bind("nsu", entrada.nsu().valor())
                .bind("status", entrada.status().name())
                .bind("recebidoEm", Timestamp.from(clock.instant()))
                .execute());
    }

    @Override
    public List<EntradaLedger> listarEntradas(FonteId fonte, Cnpj cnpj) {
        return jdbi.withHandle(h -> h.createQuery("""
                        select nsu, status from ledger_nsu
                        where fonte = :fonte and cnpj_consultado = :cnpj and ambiente = :ambiente
                        order by nsu
                        """)
                .bind("fonte", fonte.id())
                .bind("cnpj", cnpj.valor())
                .bind("ambiente", ambiente.name())
                .map((rs, ctx) -> new EntradaLedger(
                        new Nsu(rs.getLong("nsu")), StatusNsu.valueOf(rs.getString("status"))))
                .list());
    }
}
