package br.com.doc.ingest.domain.model;

/**
 * Value object imutavel representando um CNPJ (14 digitos).
 *
 * @param valor o CNPJ como string de 14 digitos numericos
 */
public record Cnpj(String valor) {

    public Cnpj {
        if (valor == null || !valor.matches("\\d{14}")) {
            throw new IllegalArgumentException(
                    "CNPJ deve ter 14 digitos numericos: " + valor);
        }
    }

    @Override
    public String toString() {
        return "Cnpj[" + valor + "]";
    }
}
