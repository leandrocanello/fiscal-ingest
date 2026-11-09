package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ColetaReivindicada;
import br.com.doc.ingest.domain.coleta.FilaColeta;
import br.com.doc.ingest.domain.coleta.PedidoReivindicacao;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * {@link FilaColeta} em Postgres: claim com {@code FOR UPDATE SKIP LOCKED} e lease. Toda hora vem
 * do {@link Clock}, por bind. O claim não toca em cursor nem versão (DA-34).
 */
public final class JdbiFilaColeta implements FilaColeta {

    private final Jdbi jdbi;
    private final Clock clock;

    public JdbiFilaColeta(Jdbi jdbi, Clock clock) {
        this.jdbi = jdbi;
        this.clock = clock;
    }

    @Override
    public List<ColetaReivindicada> reivindicar(PedidoReivindicacao pedido) {
        Instant agora = clock.instant();
        Instant leaseAte = agora.plus(pedido.lease());
        return jdbi.inTransaction(h -> h.createQuery("""
                        with alvo as (
                            select fonte, cnpj_consultado, ambiente from estado_coleta
                            where estado = 'ATIVO'
                              and (due_at is null or due_at <= :agora)
                              and (bloqueado_ate is null or bloqueado_ate <= :agora)
                              and (lease_ate is null or lease_ate <= :agora)
                            for update skip locked
                        )
                        update estado_coleta e
                        set lease_owner = :dono, lease_ate = :leaseAte
                        from alvo
                        where e.fonte = alvo.fonte and e.cnpj_consultado = alvo.cnpj_consultado
                          and e.ambiente = alvo.ambiente
                        returning e.fonte, e.cnpj_consultado, e.ambiente, e.cursor_token, e.ult_nsu, e.versao
                        """)
                .bind("agora", Timestamp.from(agora))
                .bind("dono", pedido.dono())
                .bind("leaseAte", Timestamp.from(leaseAte))
                .map((rs, ctx) -> new ColetaReivindicada(
                        new PosicaoColeta(
                                new ChaveColeta(new FonteId(rs.getString("fonte")),
                                        new Cnpj(rs.getString("cnpj_consultado")),
                                        Ambiente.valueOf(rs.getString("ambiente"))),
                                new Cursor(rs.getString("cursor_token")),
                                new Nsu(rs.getLong("ult_nsu")),
                                rs.getLong("versao")),
                        pedido.dono(), leaseAte))
                .list());
    }

    @Override
    public boolean renovar(ChaveColeta chave, String dono, Duration lease) {
        throw new UnsupportedOperationException("E4-S01: próximo passo");
    }

    @Override
    public boolean liberar(ChaveColeta chave, String dono, Instant proximoDue) {
        throw new UnsupportedOperationException("E4-S01: próximo passo");
    }

    @Override
    public void registrarBloqueio(ChaveColeta chave, Instant bloqueadoAte) {
        throw new UnsupportedOperationException("E4-S01: próximo passo");
    }
}
