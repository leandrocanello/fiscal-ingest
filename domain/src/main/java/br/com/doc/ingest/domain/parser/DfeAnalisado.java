package br.com.doc.ingest.domain.parser;

import br.com.doc.ingest.domain.catalogo.CnpjsDoConteudo;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadado de um DF-e já analisado: o que o processor precisa para rotear e registrar, sem o XML (inv. 3).
 * Em evento, os metadados trazem só campos da nota (o emitente vem da chave), nunca o autor do evento.
 *
 * @param chave      chave de acesso da nota
 * @param metadados  metadados da nota que este arquivo traz
 * @param situacao   situação que este arquivo indica
 * @param tipo       tipo do arquivo
 * @param eventoTipo tipo do evento, só em evento
 * @param eventoSeq  sequência do evento, só em evento
 * @param cnpjs      participantes lidos do conteúdo, para o roteamento (inv. 10)
 */
public record DfeAnalisado(
        ChaveAcesso chave,
        MetadadosDocumento metadados,
        SituacaoDocumento situacao,
        TipoArquivo tipo,
        Optional<String> eventoTipo,
        Optional<Integer> eventoSeq,
        CnpjsDoConteudo cnpjs) {

    public DfeAnalisado {
        Objects.requireNonNull(chave, "chave");
        Objects.requireNonNull(metadados, "metadados");
        Objects.requireNonNull(situacao, "situacao");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(eventoTipo, "eventoTipo");
        Objects.requireNonNull(eventoSeq, "eventoSeq");
        Objects.requireNonNull(cnpjs, "cnpjs");
        boolean evento = tipo == TipoArquivo.EVENTO || tipo == TipoArquivo.RESUMO_EVENTO;
        if (evento != (eventoTipo.isPresent() && eventoSeq.isPresent())
                || !evento && (eventoTipo.isPresent() || eventoSeq.isPresent())) {
            throw new IllegalArgumentException("tipo e sequência do evento existem só em arquivo de evento");
        }
    }

    /** Sem chave nem CPF: {@code metadados} e {@code cnpjs} já mascaram o destinatário (inv. 13). */
    @Override
    public String toString() {
        return "DfeAnalisado[tipo=" + tipo + ", situacao=" + situacao
                + ", evento=" + eventoTipo.orElse("-") + "/" + eventoSeq.map(String::valueOf).orElse("-")
                + ", " + metadados + ", " + cnpjs + "]";
    }
}
