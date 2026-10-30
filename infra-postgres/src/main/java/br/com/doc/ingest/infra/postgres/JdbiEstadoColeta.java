package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.EstadoColetaRepositorio;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;

/**
 * {@link EstadoColetaRepositorio} em Postgres. O cursor só muda por CAS na {@code versao}
 * (CLAUDE.md §6.5); o banco ainda recusa retrocesso de {@code ult_nsu} (V3).
 */
public final class JdbiEstadoColeta implements EstadoColetaRepositorio {

    private final Jdbi jdbi;
    private final Clock clock;

    public JdbiEstadoColeta(Jdbi jdbi, Clock clock) {
        this.jdbi = jdbi;
        this.clock = clock;
    }

    @Override
    public void criarSeAusente(ChaveColeta chave) {
        jdbi.useHandle(h -> h.createUpdate("""
                        insert into estado_coleta (fonte, cnpj_consultado, ambiente, cursor_token, ult_nsu, versao, atualizado_em)
                        values (:fonte, :cnpj, :ambiente, :cursor, 0, 0, :atualizadoEm)
                        on conflict (fonte, cnpj_consultado, ambiente) do nothing
                        """)
                .bind("fonte", chave.fonte().id())
                .bind("cnpj", chave.cnpj().valor())
                .bind("ambiente", chave.ambiente().name())
                .bind("cursor", Cursor.inicial().token())
                .bind("atualizadoEm", Timestamp.from(clock.instant()))
                .execute());
    }

    @Override
    public Optional<PosicaoColeta> carregar(ChaveColeta chave) {
        return jdbi.withHandle(h -> h.createQuery("""
                        select cursor_token, ult_nsu, versao from estado_coleta
                        where fonte = :fonte and cnpj_consultado = :cnpj and ambiente = :ambiente
                        """)
                .bind("fonte", chave.fonte().id())
                .bind("cnpj", chave.cnpj().valor())
                .bind("ambiente", chave.ambiente().name())
                .map((rs, ctx) -> new PosicaoColeta(chave,
                        new Cursor(rs.getString("cursor_token")),
                        new Nsu(rs.getLong("ult_nsu")),
                        rs.getLong("versao")))
                .findOne());
    }

    @Override
    public ResultadoCas confirmarLote(ConfirmacaoLote confirmacao) {
        var lida = confirmacao.lida();
        var chave = lida.chave();
        var agora = clock.instant();
        return jdbi.inTransaction(h -> {
            for (EntradaLedger entrada : confirmacao.entradas()) {
                LedgerSql.inserir(h, chave.fonte(), chave.cnpj(), chave.ambiente(), entrada, agora);
            }
            int afetadas = h.createUpdate("""
                            update estado_coleta
                            set cursor_token = :cursor, ult_nsu = :novo, versao = versao + 1, atualizado_em = :atualizadoEm
                            where fonte = :fonte and cnpj_consultado = :cnpj and ambiente = :ambiente
                              and versao = :versao and ult_nsu <= :novo
                            """)
                    .bind("cursor", confirmacao.novoCursor().token())
                    .bind("novo", confirmacao.novoUltNsu().valor())
                    .bind("atualizadoEm", Timestamp.from(agora))
                    .bind("fonte", chave.fonte().id())
                    .bind("cnpj", chave.cnpj().valor())
                    .bind("ambiente", chave.ambiente().name())
                    .bind("versao", lida.versao())
                    .execute();
            if (afetadas != 1) {
                // Conflito/retrocesso com diagnóstico: próximo passo da E3-S02. A exceção desfaz o ledger.
                throw new IllegalStateException("CAS do cursor não aplicado");
            }
            return new ResultadoCas.Aplicado(lida.versao() + 1);
        });
    }
}
