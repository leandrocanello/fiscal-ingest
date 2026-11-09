package br.com.doc.ingest.adapter.nfe;

/**
 * Chave de acesso da NF-e: 44 dígitos. Sem conferência de DV nesta etapa (DA-20).
 */
public record ChaveAcessoNfe(String valor) {

    private static final int TAMANHO = 44;

    public ChaveAcessoNfe {
        if (valor == null || valor.length() != TAMANHO || !valor.chars().allMatch(c -> c >= '0' && c <= '9')) {
            // não ecoa o valor recebido (inv. 13)
            throw new IllegalArgumentException("chave de acesso deve ter " + TAMANHO + " dígitos");
        }
    }

    @Override
    public String toString() {
        return "ChaveAcessoNfe[***]";
    }
}
