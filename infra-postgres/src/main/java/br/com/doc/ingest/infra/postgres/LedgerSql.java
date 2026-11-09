package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import org.jdbi.v3.core.Handle;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * SQL do {@code ledger_nsu} compartilhado por {@link JdbiNsuLedger} e {@link JdbiEstadoColeta}:
 * inserção idempotente em que o primeiro status prevalece (ADR-005).
 */
final class LedgerSql {

    private LedgerSql() {
    }

    static void inserir(Handle h, FonteId fonte, Cnpj cnpj, Ambiente ambiente,
                        EntradaLedger entrada, Instant recebidoEm) {
        h.createUpdate("""
                        insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em)
                        values (:fonte, :cnpj, :ambiente, :nsu, :status, :recebidoEm)
                        on conflict (fonte, cnpj_consultado, ambiente, nsu) do nothing
                        """)
                .bind("fonte", fonte.id())
                .bind("cnpj", cnpj.valor())
                .bind("ambiente", ambiente.name())
                .bind("nsu", entrada.nsu().valor())
                .bind("status", entrada.status().name())
                .bind("recebidoEm", Timestamp.from(recebidoEm))
                .execute();
    }
}
