package br.com.doc.ingest.domain.model;

/**
 * Gera CNPJ e chave de acesso sinteticos com DV certo (modulo 11 da NT Conjunta 2025.001),
 * para os testes nao dependerem de literais com DV errado.
 */
public final class FixturesFiscais {

    private FixturesFiscais() {
    }

    /** CNPJ de 14 caracteres a partir da base de 12 (raiz + ordem). */
    public static String cnpj(String base12) {
        exigirTamanho(base12, 12);
        String com13 = base12 + DigitoVerificador.modulo11(base12);
        return com13 + DigitoVerificador.modulo11(com13);
    }

    /** Chave de acesso de 44 caracteres a partir dos 43 primeiros. */
    public static String chave(String base43) {
        exigirTamanho(base43, 43);
        return base43 + DigitoVerificador.modulo11(base43);
    }

    private static void exigirTamanho(String base, int tamanho) {
        if (base == null || base.length() != tamanho) {
            throw new IllegalArgumentException("base deve ter " + tamanho + " caracteres");
        }
    }
}
