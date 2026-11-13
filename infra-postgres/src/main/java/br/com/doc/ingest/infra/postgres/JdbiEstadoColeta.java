package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.EstadoColetaRepositorio;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.coleta.ResultadoTroca;
import br.com.doc.ingest.domain.coleta.VinculoCertificado;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
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
        try {
            return jdbi.inTransaction(h -> {
                for (EntradaLedger entrada : confirmacao.entradas()) {
                    LedgerSql.inserir(h, chave.fonte(), chave.cnpj(), chave.ambiente(), entrada, agora);
                }
                for (var mensagem : confirmacao.mensagens()) {
                    OutboxSql.inserir(h, mensagem, agora);
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
                    // Lança para o inTransaction desfazer também as entradas do ledger.
                    throw new CasNaoAplicado(diagnosticar(h, lida));
                }
                return new ResultadoCas.Aplicado(lida.versao() + 1);
            });
        } catch (CasNaoAplicado e) {
            return e.resultado;
        }
    }

    @Override
    public ResultadoTroca trocarCertificado(ChaveColeta chave, Long esperado, long novo) {
        return jdbi.inTransaction(h -> {
            int afetadas = h.createUpdate("""
                            update estado_coleta set certificado_id = :novo, atualizado_em = :agora
                            where fonte = :fonte and cnpj_consultado = :cnpj and ambiente = :ambiente
                              and certificado_id is not distinct from cast(:esperado as bigint)
                              and certificado_id is distinct from :novo
                            """)
                    .bind("novo", novo)
                    .bindByType("esperado", esperado, Long.class)
                    .bind("agora", Timestamp.from(clock.instant()))
                    .bind("fonte", chave.fonte().id())
                    .bind("cnpj", chave.cnpj().valor())
                    .bind("ambiente", chave.ambiente().name())
                    .execute();
            if (afetadas == 1) {
                return new ResultadoTroca.Trocado();
            }
            var atual = h.createQuery("""
                            select certificado_id from estado_coleta
                            where fonte = :fonte and cnpj_consultado = :cnpj and ambiente = :ambiente
                            """)
                    .bind("fonte", chave.fonte().id())
                    .bind("cnpj", chave.cnpj().valor())
                    .bind("ambiente", chave.ambiente().name())
                    .map((rs, ctx) -> Optional.ofNullable((Long) rs.getObject("certificado_id")))
                    .findOne();
            if (atual.isEmpty()) {
                return new ResultadoTroca.NaoEncontrada();
            }
            Long id = atual.get().orElse(null);
            return id != null && id == novo ? new ResultadoTroca.SemMudanca() : new ResultadoTroca.Conflito(id);
        });
    }

    @Override
    public List<VinculoCertificado> certificadosDoCnpj(Cnpj cnpj) {
        return jdbi.withHandle(h -> h.createQuery("""
                        select fonte, ambiente, certificado_id from estado_coleta
                        where cnpj_consultado = :cnpj
                        """)
                .bind("cnpj", cnpj.valor())
                .map((rs, ctx) -> new VinculoCertificado(
                        new ChaveColeta(new FonteId(rs.getString("fonte")), cnpj,
                                Ambiente.valueOf(rs.getString("ambiente"))),
                        (Long) rs.getObject("certificado_id")))
                .list());
    }

    private static ResultadoCas diagnosticar(Handle h, PosicaoColeta lida) {
        var chave = lida.chave();
        var atual = h.createQuery("""
                        select versao, ult_nsu from estado_coleta
                        where fonte = :fonte and cnpj_consultado = :cnpj and ambiente = :ambiente
                        """)
                .bind("fonte", chave.fonte().id())
                .bind("cnpj", chave.cnpj().valor())
                .bind("ambiente", chave.ambiente().name())
                .map((rs, ctx) -> new long[] {rs.getLong("versao"), rs.getLong("ult_nsu")})
                .findOne()
                .orElseThrow(() -> new IllegalStateException("estado_coleta inexistente para a chave"));
        if (atual[0] != lida.versao()) {
            return new ResultadoCas.ConflitoDeVersao(atual[0]);
        }
        return new ResultadoCas.RetrocessoRecusado(new Nsu(atual[1]));
    }

    /** Carrega o desfecho do CAS para fora do {@code inTransaction}, forçando o rollback. */
    private static final class CasNaoAplicado extends RuntimeException {
        private final transient ResultadoCas resultado;

        CasNaoAplicado(ResultadoCas resultado) {
            super(null, null, false, false);
            this.resultado = resultado;
        }
    }
}
