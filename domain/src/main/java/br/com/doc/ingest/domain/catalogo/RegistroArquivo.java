package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pedido de registro de um arquivo no catálogo: só metadado e {@link BlobRef}, nunca o XML (inv. 3).
 *
 * @param tenantId       tenant dono do documento
 * @param chave          chave de acesso da nota
 * @param metadados      metadados que este arquivo traz
 * @param situacao       situação que este arquivo indica
 * @param tipo           tipo do arquivo
 * @param eventoTipo     tipo do evento, só em arquivo de evento
 * @param eventoSeq      sequência do evento, só em arquivo de evento
 * @param blob           onde o bruto já foi gravado
 * @param vinculos       empresas do tenant no conteúdo; vazio é o caso {@code SEM_EMPRESA} da §7.3
 * @param proveniencia   de onde o arquivo veio
 */
public record RegistroArquivo(
        long tenantId,
        ChaveAcesso chave,
        MetadadosDocumento metadados,
        SituacaoDocumento situacao,
        TipoArquivo tipo,
        Optional<String> eventoTipo,
        Optional<Integer> eventoSeq,
        BlobRef blob,
        List<VinculoEmpresa> vinculos,
        ProvenienciaInformada proveniencia) {

    public RegistroArquivo {
        Objects.requireNonNull(chave, "chave");
        Objects.requireNonNull(metadados, "metadados");
        Objects.requireNonNull(situacao, "situacao");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(eventoTipo, "eventoTipo");
        Objects.requireNonNull(eventoSeq, "eventoSeq");
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(vinculos, "vinculos");
        Objects.requireNonNull(proveniencia, "proveniencia");
        boolean evento = tipo == TipoArquivo.EVENTO || tipo == TipoArquivo.RESUMO_EVENTO;
        if (evento && (eventoTipo.isEmpty() || eventoSeq.isEmpty())) {
            throw new IllegalArgumentException("arquivo de evento exige tipo e sequência do evento");
        }
        if (!evento && (eventoTipo.isPresent() || eventoSeq.isPresent())) {
            throw new IllegalArgumentException("arquivo que não é evento não tem tipo nem sequência de evento");
        }
        vinculos = List.copyOf(vinculos);
    }
}
