package br.com.doc.ingest.domain.model;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Value object imutavel representando um CNPJ no formato da NT Conjunta 2025.001: raiz e ordem
 * alfanumericas ({@code [A-Z0-9]{12}}) e DV numerico de 2 digitos, conferido por modulo 11
 * (valor do caractere = ASCII - 48). Os CNPJs numericos antigos continuam validos. So o valor
 * canonico (maiusculo, sem mascara) e aceito (DA-37); o valor nunca vai para a mensagem de erro
 * (inv. 13).
 *
 * @param valor o CNPJ como string de 14 caracteres
 */
public record Cnpj(String valor) {

    private static final Pattern FORMATO = Pattern.compile("[A-Z0-9]{12}[0-9]{2}");

    private static final String QUATORZE_ZEROS = "00000000000000";

    /**
     * Letras que a Receita venha a vedar na raiz e na ordem. Vazia de proposito (DA-37): a
     * exclusao de I, O, U, Q e F nao foi confirmada, e vedar sem confirmacao perderia nota.
     */
    static final Set<Character> LETRAS_VEDADAS = Set.of();

    public Cnpj {
        if (!valido(valor)) {
            throw new IllegalArgumentException(
                    "CNPJ invalido: esperado [A-Z0-9]{12} + DV numerico de 2 digitos conferido");
        }
    }

    /** Confere formato e DV sem lancar excecao, para quem recebe texto de fora. */
    public static boolean valido(String valor) {
        if (valor == null || !FORMATO.matcher(valor).matches() || QUATORZE_ZEROS.equals(valor)) {
            return false;
        }
        for (int i = 0; i < 12; i++) {
            if (LETRAS_VEDADAS.contains(valor.charAt(i))) {
                return false;
            }
        }
        int dv1 = DigitoVerificador.modulo11(valor.subSequence(0, 12));
        int dv2 = DigitoVerificador.modulo11(valor.subSequence(0, 13));
        return valor.charAt(12) - '0' == dv1 && valor.charAt(13) - '0' == dv2;
    }

    @Override
    public String toString() {
        return "Cnpj[" + valor + "]";
    }
}
