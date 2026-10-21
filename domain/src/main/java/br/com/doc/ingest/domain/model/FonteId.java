package br.com.doc.ingest.domain.model;

/**
 * Value object imutável identificando uma fonte de documentos fiscais.
 *
 * <p>Exemplos: "NFE_AN" (NF-e Ambiente Nacional), "CTE_AN", "NFSE_ADN".
 *
 * @param id identificador único da fonte
 */
public record FonteId(String id) {

    public FonteId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("FonteId não pode ser nulo ou vazio");
        }
    }

    @Override
    public String toString() {
        return "FonteId[" + id + "]";
    }
}
