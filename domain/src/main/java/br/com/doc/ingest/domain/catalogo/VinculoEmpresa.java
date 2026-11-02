package br.com.doc.ingest.domain.catalogo;

import java.util.Objects;

/**
 * Vínculo de um documento com uma empresa do tenant.
 *
 * @param empresaId empresa vinculada
 * @param direcao   direção do documento para essa empresa
 */
public record VinculoEmpresa(long empresaId, Direcao direcao) {

    public VinculoEmpresa {
        Objects.requireNonNull(direcao, "direcao");
    }
}
