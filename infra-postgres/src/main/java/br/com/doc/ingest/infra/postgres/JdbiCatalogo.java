package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.EstadoDocumento;
import br.com.doc.ingest.domain.catalogo.FichaDocumento;
import br.com.doc.ingest.domain.catalogo.FusaoDocumento;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.ProvenienciaInformada;
import br.com.doc.ingest.domain.catalogo.RegistroArquivo;
import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link Catalogo} em Postgres. Toda operação roda numa transação com o tenant fixado, para a RLS da V9
 * valer. As listas da ficha têm ordem que não depende da ordem de chegada.
 */
public final class JdbiCatalogo implements Catalogo {

    private final Jdbi jdbi;
    private final Clock clock;

    public JdbiCatalogo(Jdbi jdbi, Clock clock) {
        this.jdbi = jdbi;
        this.clock = clock;
    }

    @Override
    public ResultadoRegistro registrar(RegistroArquivo registro) {
        return noTenant(registro.tenantId(), h -> {
            long documentoId = inserirDocumento(h, registro);
            long arquivoId = inserirArquivo(h, registro, documentoId);
            for (VinculoEmpresa vinculo : registro.vinculos()) {
                inserirVinculo(h, registro.tenantId(), documentoId, vinculo);
            }
            inserirProveniencia(h, registro, arquivoId);
            return new ResultadoRegistro(documentoId, arquivoId, true, false, true, true, registro.vinculos());
        });
    }

    @Override
    public Optional<FichaDocumento> consultar(long tenantId, ChaveAcesso chave) {
        return noTenant(tenantId, h -> h.createQuery("""
                        select id, serie, numero, emissao, emitente_cnpj, destinatario_doc, valor_total,
                               versao_layout, situacao, so_declarado, atualizado_em
                        from documento
                        where tenant_id = :tenant and chave_acesso = :chave
                        """)
                .bind("tenant", tenantId)
                .bind("chave", chave.valor())
                .map((rs, ctx) -> new DocumentoLido(rs.getLong("id"), lerEstado(rs), rs.getTimestamp("atualizado_em")))
                .findOne()
                .map(d -> new FichaDocumento(d.id(), tenantId, chave, d.estado(),
                        lerVinculos(h, d.id()), lerArquivos(h, d.id()), d.atualizadoEm().toInstant())));
    }

    private <R> R noTenant(long tenantId, HandleCallback<R, RuntimeException> acao) {
        return jdbi.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', :tenant, true)")
                    .bind("tenant", Long.toString(tenantId))
                    .mapTo(String.class)
                    .one();
            return acao.withHandle(h);
        });
    }

    private long inserirDocumento(Handle h, RegistroArquivo registro) {
        var confianca = registro.proveniencia().confianca();
        var estado = FusaoDocumento.inicial(registro.metadados(), registro.situacao(), confianca);
        var metadados = estado.metadados();
        return h.createUpdate("""
                        insert into documento (tenant_id, chave_acesso, modelo, serie, numero, emissao, emitente_cnpj,
                                               destinatario_doc, valor_total, situacao, so_declarado, versao_layout,
                                               atualizado_em)
                        values (:tenant, :chave, :modelo, :serie, :numero, :emissao, :emitente,
                                :destinatario, :valor, :situacao, :soDeclarado, :versaoLayout, :atualizadoEm)
                        """)
                .bind("tenant", registro.tenantId())
                .bind("chave", registro.chave().valor())
                .bind("modelo", registro.chave().modelo())
                .bind("serie", metadados.serie().orElse(null))
                .bind("numero", metadados.numero().orElse(null))
                .bind("emissao", metadados.emissao().map(e -> Timestamp.from(e.toInstant())).orElse(null))
                .bind("emitente", metadados.emitenteCnpj().map(Cnpj::valor).orElse(null))
                .bind("destinatario", metadados.destinatarioDoc().orElse(null))
                .bind("valor", metadados.valorTotal().orElse(null))
                .bind("situacao", estado.situacao().name())
                .bind("soDeclarado", estado.soDeclarado())
                .bind("versaoLayout", metadados.versaoLayout().orElse(null))
                .bind("atualizadoEm", Timestamp.from(clock.instant()))
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
    }

    private static long inserirArquivo(Handle h, RegistroArquivo registro, long documentoId) {
        return h.createUpdate("""
                        insert into arquivo (tenant_id, documento_id, tipo, evento_tipo, evento_seq, blob_ref, sha256, recebido_em)
                        values (:tenant, :documento, :tipo, :eventoTipo, :eventoSeq, :blobRef, :sha256, :recebidoEm)
                        """)
                .bind("tenant", registro.tenantId())
                .bind("documento", documentoId)
                .bind("tipo", registro.tipo().name())
                .bind("eventoTipo", registro.eventoTipo().orElse(null))
                .bind("eventoSeq", registro.eventoSeq().orElse(null))
                .bind("blobRef", registro.blob().chave())
                .bind("sha256", registro.blob().sha256())
                .bind("recebidoEm", Timestamp.from(registro.proveniencia().recebidoEm()))
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
    }

    private static void inserirVinculo(Handle h, long tenantId, long documentoId, VinculoEmpresa vinculo) {
        h.createUpdate("""
                        insert into documento_empresa (tenant_id, documento_id, empresa_id, direcao)
                        values (:tenant, :documento, :empresa, :direcao)
                        """)
                .bind("tenant", tenantId)
                .bind("documento", documentoId)
                .bind("empresa", vinculo.empresaId())
                .bind("direcao", vinculo.direcao().name())
                .execute();
    }

    private static void inserirProveniencia(Handle h, RegistroArquivo registro, long arquivoId) {
        var proveniencia = registro.proveniencia();
        h.createUpdate("""
                        insert into proveniencia (tenant_id, arquivo_id, origem, confianca, ledger_nsu_id, recebimento_id, recebido_em)
                        values (:tenant, :arquivo, :origem, :confianca, :ledger, :recebimento, :recebidoEm)
                        """)
                .bind("tenant", registro.tenantId())
                .bind("arquivo", arquivoId)
                .bind("origem", proveniencia.origem().name())
                .bind("confianca", proveniencia.confianca().name())
                .bind("ledger", proveniencia.ledgerNsuId().orElse(null))
                .bind("recebimento", proveniencia.recebimentoId().orElse(null))
                .bind("recebidoEm", Timestamp.from(proveniencia.recebidoEm()))
                .execute();
    }

    private static EstadoDocumento lerEstado(ResultSet rs) throws SQLException {
        var metadados = new MetadadosDocumento(
                Optional.ofNullable(rs.getObject("serie", Integer.class)),
                Optional.ofNullable(rs.getObject("numero", Long.class)),
                Optional.ofNullable(rs.getObject("emissao", OffsetDateTime.class)),
                Optional.ofNullable(rs.getString("emitente_cnpj")).map(Cnpj::new),
                Optional.ofNullable(rs.getString("destinatario_doc")),
                Optional.ofNullable(rs.getObject("valor_total", BigDecimal.class)),
                Optional.ofNullable(rs.getString("versao_layout")));
        return new EstadoDocumento(
                metadados, SituacaoDocumento.valueOf(rs.getString("situacao")), rs.getBoolean("so_declarado"));
    }

    private static List<VinculoEmpresa> lerVinculos(Handle h, long documentoId) {
        return h.createQuery("""
                        select empresa_id, direcao from documento_empresa
                        where documento_id = :documento
                        order by empresa_id, direcao
                        """)
                .bind("documento", documentoId)
                .map((rs, ctx) -> new VinculoEmpresa(rs.getLong("empresa_id"), Direcao.valueOf(rs.getString("direcao"))))
                .list();
    }

    private static List<ArquivoFicha> lerArquivos(Handle h, long documentoId) {
        var proveniencias = lerProveniencias(h, documentoId);
        return h.createQuery("""
                        select id, tipo, evento_tipo, evento_seq, blob_ref, sha256, recebido_em from arquivo
                        where documento_id = :documento
                        order by sha256
                        """)
                .bind("documento", documentoId)
                .map((rs, ctx) -> new ArquivoFicha(
                        rs.getLong("id"),
                        TipoArquivo.valueOf(rs.getString("tipo")),
                        Optional.ofNullable(rs.getString("evento_tipo")),
                        Optional.ofNullable(rs.getObject("evento_seq", Integer.class)),
                        new BlobRef(rs.getString("blob_ref"), rs.getString("sha256")),
                        rs.getTimestamp("recebido_em").toInstant(),
                        proveniencias.getOrDefault(rs.getLong("id"), List.of())))
                .list();
    }

    private static Map<Long, List<ProvenienciaInformada>> lerProveniencias(Handle h, long documentoId) {
        return h.createQuery("""
                        select p.arquivo_id, p.origem, p.confianca, p.ledger_nsu_id, p.recebimento_id, p.recebido_em
                        from proveniencia p
                        join arquivo a on a.id = p.arquivo_id
                        where a.documento_id = :documento
                        order by p.origem, p.confianca, p.ledger_nsu_id, p.recebimento_id
                        """)
                .bind("documento", documentoId)
                .map((rs, ctx) -> Map.entry(rs.getLong("arquivo_id"), new ProvenienciaInformada(
                        OrigemArquivo.valueOf(rs.getString("origem")),
                        Confianca.valueOf(rs.getString("confianca")),
                        Optional.ofNullable(rs.getObject("ledger_nsu_id", Long.class)),
                        Optional.ofNullable(rs.getObject("recebimento_id", Long.class)),
                        rs.getTimestamp("recebido_em").toInstant())))
                .list()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private record DocumentoLido(long id, EstadoDocumento estado, Timestamp atualizadoEm) {
    }
}
