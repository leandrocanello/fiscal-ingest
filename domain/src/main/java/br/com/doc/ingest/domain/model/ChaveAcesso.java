package br.com.doc.ingest.domain.model;

/**
 * Chave de acesso de um DF-e: 44 dígitos. Sem conferência de DV (DA-20).
 *
 * @param valor os 44 dígitos da chave
 */
public record ChaveAcesso(String valor) {

    private static final int TAMANHO = 44;

    public ChaveAcesso {
        if (valor == null || valor.length() != TAMANHO || !valor.chars().allMatch(c -> c >= '0' && c <= '9')) {
            // não ecoa o valor recebido (inv. 13)
            throw new IllegalArgumentException("chave de acesso deve ter " + TAMANHO + " dígitos");
        }
    }

    /** Modelo do documento: posições 21–22 da chave (ex.: 55 NF-e, 65 NFC-e). */
    public String modelo() {
        return valor.substring(20, 22);
    }

    @Override
    public String toString() {
        return "ChaveAcesso[***]";
    }
}
