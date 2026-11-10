package br.com.doc.ingest.domain.model;

/**
 * Digito verificador modulo 11 do CNPJ e da chave de acesso, no formato alfanumerico da
 * NT Conjunta 2025.001: cada caractere vale (codigo ASCII - 48), pesos de 2 a 9 da direita
 * para a esquerda, reiniciando em 2; resto 0 ou 1 vira 0. Para digitos, da o mesmo
 * resultado do calculo numerico antigo.
 */
final class DigitoVerificador {

    private DigitoVerificador() {
    }

    /** Calcula o DV da base; quem chama garante que ela so tem {@code [0-9A-Z]}. */
    static int modulo11(CharSequence base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
