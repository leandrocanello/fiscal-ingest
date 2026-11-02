package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.blob.BlobRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Arquivo de um documento do catálogo, como aparece na {@link FichaDocumento}.
 *
 * @param id            identificador do arquivo
 * @param tipo          tipo do arquivo
 * @param eventoTipo    tipo do evento, só em arquivo de evento
 * @param eventoSeq     sequência do evento, só em arquivo de evento
 * @param blob          onde o bruto está gravado
 * @param recebidoEm    momento da primeira chegada
 * @param proveniencias canais pelos quais o arquivo chegou
 */
public record ArquivoFicha(
        long id,
        TipoArquivo tipo,
        Optional<String> eventoTipo,
        Optional<Integer> eventoSeq,
        BlobRef blob,
        Instant recebidoEm,
        List<ProvenienciaInformada> proveniencias) {

    public ArquivoFicha {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(eventoTipo, "eventoTipo");
        Objects.requireNonNull(eventoSeq, "eventoSeq");
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(recebidoEm, "recebidoEm");
        Objects.requireNonNull(proveniencias, "proveniencias");
        proveniencias = List.copyOf(proveniencias);
    }
}
