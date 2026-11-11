package br.com.doc.ingest.domain.model;

import java.util.regex.Pattern;

/**
 * Chave de acesso de um DF-e no formato da NT Conjunta 2025.001: 44 caracteres, com o CNPJ do
 * emitente (posições 7–18 alfanuméricas) e o restante numérico. O construtor só confere o
 * formato; o DV fica em {@link #temDvValido()}, porque na entrada nunca se recusa documento por
 * DV (DA-38).
 *
 * @param valor os 44 caracteres da chave
 */
public record ChaveAcesso(String valor) {

    private static final Pattern FORMATO = Pattern.compile("[0-9]{6}[A-Z0-9]{12}[0-9]{26}");

    public ChaveAcesso {
        if (!formatoValido(valor)) {
            // não ecoa o valor recebido (inv. 13)
            throw new IllegalArgumentException(
                    "chave de acesso inválida: esperado [0-9]{6}[A-Z0-9]{12}[0-9]{26}");
        }
    }

    /** Confere só o formato da NT (sem DV), sem lançar exceção. */
    public static boolean formatoValido(String valor) {
        if (valor == null || !FORMATO.matcher(valor).matches()) {
            return false;
        }
        for (int i = 6; i < 18; i++) {
            if (Cnpj.LETRAS_VEDADAS.contains(valor.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Confere o DV (último dígito) por módulo 11 alfanumérico. */
    public boolean temDvValido() {
        return valor.charAt(43) - '0' == DigitoVerificador.modulo11(valor.subSequence(0, 43));
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
