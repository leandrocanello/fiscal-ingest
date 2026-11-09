package br.com.doc.ingest.domain.catalogo;

import java.util.Objects;

/**
 * Estado fundido de um documento do catálogo (DA-27).
 *
 * @param metadados   metadados vigentes
 * @param situacao    situação vigente
 * @param soDeclarado verdadeiro enquanto só chegaram registros {@link Confianca#DECLARADO}
 */
public record EstadoDocumento(MetadadosDocumento metadados, SituacaoDocumento situacao, boolean soDeclarado) {

    public EstadoDocumento {
        Objects.requireNonNull(metadados, "metadados");
        Objects.requireNonNull(situacao, "situacao");
    }
}
