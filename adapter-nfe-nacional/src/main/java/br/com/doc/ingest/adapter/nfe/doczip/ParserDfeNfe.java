package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.catalogo.CnpjsDoConteudo;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.parser.DfeAnalisado;
import br.com.doc.ingest.domain.parser.ParserDfe;
import br.com.doc.ingest.domain.parser.ResultadoParse;

import java.util.List;
import java.util.Optional;

/**
 * {@link ParserDfe} da NF-e do Ambiente Nacional: {@link ClassificadorSchema} + {@link ExtratorMetadados}
 * (StAX sem DTD) + {@link SituacaoNfe}. Nunca lança por conteúdo ruim e nunca ecoa conteúdo no motivo.
 *
 * <p>Em evento, só entram campos da nota: o emitente vem da chave de acesso (posições 7–20); o autor,
 * a data e a versão do evento ficam de fora, para a fusão não depender da ordem de chegada (achado 1
 * do revisor da E3-S06).
 */
public final class ParserDfeNfe implements ParserDfe {

    @Override
    public ResultadoParse analisar(String schema, byte[] xml) {
        TipoSchemaDfe tipo = ClassificadorSchema.classificar(schema);
        if (tipo == TipoSchemaDfe.DESCONHECIDO) {
            return new ResultadoParse.Desconhecido();
        }
        try {
            MetadadosDfe m = ExtratorMetadados.extrair(tipo, xml);
            return new ResultadoParse.Analisado(switch (tipo) {
                case RES_NFE -> resumo(m);
                case PROC_NFE -> completo(m);
                case RES_EVENTO -> evento(m, TipoArquivo.RESUMO_EVENTO);
                case PROC_EVENTO_NFE -> evento(m, TipoArquivo.EVENTO);
                case DESCONHECIDO -> throw new IllegalStateException("tratado acima");
            });
        } catch (DocZipInvalidoException e) {
            return new ResultadoParse.Invalido(e.getMessage());
        } catch (IllegalArgumentException e) {
            // mensagens dos value objects podem ecoar o valor (ex.: Cnpj): não repassar (inv. 13)
            return new ResultadoParse.Invalido("Metadados fora do formato");
        }
    }

    private static DfeAnalisado resumo(MetadadosDfe m) {
        Optional<Cnpj> emitente = cnpj(m.cnpjEmitente());
        var metadados = new MetadadosDocumento(Optional.empty(), Optional.empty(), m.emissao(), emitente,
                Optional.empty(), m.valorTotal(), Optional.empty());
        return new DfeAnalisado(new ChaveAcesso(m.chave()), metadados, SituacaoNfe.doResumo(m.situacao()),
                TipoArquivo.RESUMO, Optional.empty(), Optional.empty(),
                new CnpjsDoConteudo(emitente, Optional.empty(), Optional.empty(), List.of()));
    }

    private static DfeAnalisado completo(MetadadosDfe m) {
        Optional<Cnpj> emitente = cnpj(m.cnpjEmitente());
        var metadados = new MetadadosDocumento(m.serie(), m.numero(), m.emissao(), emitente,
                m.documentoDestinatario(), m.valorTotal(), m.versaoLayout());
        List<Cnpj> autorizados = m.cnpjsAutorizados().stream().map(ParserDfeNfe::cnpj).toList();
        return new DfeAnalisado(new ChaveAcesso(m.chave()), metadados, SituacaoNfe.doProtocolo(m.cStatProtocolo()),
                TipoArquivo.COMPLETO, Optional.empty(), Optional.empty(),
                new CnpjsDoConteudo(emitente, m.documentoDestinatario(), cnpj(m.cnpjTransportador()), autorizados));
    }

    private static DfeAnalisado evento(MetadadosDfe m, TipoArquivo tipo) {
        if (m.tipoEvento().isEmpty() || m.seqEvento().isEmpty()) {
            throw new DocZipInvalidoException("Evento sem tipo ou sequência");
        }
        Optional<Cnpj> emitente = Optional.of(cnpj(m.chave().substring(6, 20)));
        var metadados = new MetadadosDocumento(Optional.empty(), Optional.empty(), Optional.empty(), emitente,
                Optional.empty(), Optional.empty(), Optional.empty());
        SituacaoDocumento situacao = SituacaoNfe.doEvento(m.tipoEvento().get());
        return new DfeAnalisado(new ChaveAcesso(m.chave()), metadados, situacao, tipo,
                m.tipoEvento(), m.seqEvento(),
                new CnpjsDoConteudo(emitente, Optional.empty(), Optional.empty(), List.of()));
    }

    private static Optional<Cnpj> cnpj(Optional<String> valor) {
        return valor.map(ParserDfeNfe::cnpj);
    }

    private static Cnpj cnpj(String valor) {
        if (!valor.matches("\\d{14}")) {
            throw new DocZipInvalidoException("CNPJ fora do formato");
        }
        return new Cnpj(valor);
    }
}
