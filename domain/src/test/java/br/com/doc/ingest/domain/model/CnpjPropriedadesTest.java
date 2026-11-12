package br.com.doc.ingest.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Propriedades do {@link Cnpj} alfanumérico (E1-S07; inv. 13 para a mensagem de erro). */
class CnpjPropriedadesTest {

    private static final int[] PESOS_13 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Provide
    Arbitrary<String> baseNumerica() {
        return Arbitraries.strings().numeric().ofLength(12).filter(b -> !b.equals("000000000000"));
    }

    @Provide
    Arbitrary<String> baseAlfanumerica() {
        return Arbitraries.strings().withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofLength(12).filter(b -> !FixturesFiscais.cnpj(b).equals("00000000000000"));
    }

    /** Algoritmo antigo, com pesos fixos (independente de DigitoVerificador). */
    private static int dvAntigo(String digitos) {
        int soma = 0;
        int n = digitos.length();
        for (int i = 0; i < n; i++) {
            soma += (digitos.charAt(i) - '0') * PESOS_13[PESOS_13.length - n + i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    @Property
    void numericoValidoPeloAlgoritmoAntigoContinuaValido(@ForAll("baseNumerica") String base) {
        String com13 = base + dvAntigo(base);
        String cnpj = com13 + dvAntigo(com13);
        assertThat(cnpj).isEqualTo(FixturesFiscais.cnpj(base));
        assertThat(Cnpj.valido(cnpj)).isTrue();
        assertThat(new Cnpj(cnpj).valor()).isEqualTo(cnpj);
    }

    @Property
    void baseAlfanumericaComDvCalculadoEAceita(@ForAll("baseAlfanumerica") String base) {
        assertThat(Cnpj.valido(FixturesFiscais.cnpj(base))).isTrue();
    }

    @Property
    void alterarUmDosDoisUltimosCaracteresERecusado(
            @ForAll("baseAlfanumerica") String base,
            @ForAll boolean primeiro,
            @ForAll("qualquerCaractere") char novo) {
        String cnpj = FixturesFiscais.cnpj(base);
        int pos = primeiro ? 12 : 13;
        if (cnpj.charAt(pos) == novo) {
            return;
        }
        String alterado = cnpj.substring(0, pos) + novo + cnpj.substring(pos + 1);
        assertThat(Cnpj.valido(alterado)).isFalse();
    }

    @Provide
    Arbitrary<Character> qualquerCaractere() {
        return Arbitraries.chars().ascii();
    }

    @Property
    void caractereForaDeAZ09ERecusado(
            @ForAll("baseAlfanumerica") String base, @ForAll int posicao,
            @ForAll("foraDoAlfabeto") char intruso) {
        String cnpj = FixturesFiscais.cnpj(base);
        int pos = Math.floorMod(posicao, 14);
        String alterado = cnpj.substring(0, pos) + intruso + cnpj.substring(pos + 1);
        assertThat(Cnpj.valido(alterado)).isFalse();
    }

    @Provide
    Arbitrary<Character> foraDoAlfabeto() {
        return Arbitraries.chars().filter(c -> !((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')));
    }

    @Property
    void mensagemDeErroNuncaContemOValor(@ForAll("candidatos") String candidato) {
        if (Cnpj.valido(candidato) || candidato.length() < 8) {
            return;
        }
        assertThatThrownBy(() -> new Cnpj(candidato))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(candidato));
    }

    @Property
    void validoConcordaComOConstrutor(@ForAll("candidatos") String candidato) {
        boolean construiu;
        try {
            new Cnpj(candidato);
            construiu = true;
        } catch (IllegalArgumentException e) {
            construiu = false;
        }
        assertThat(Cnpj.valido(candidato)).isEqualTo(construiu);
    }

    @Provide
    Arbitrary<String> candidatos() {
        Arbitrary<String> aleatorio = Arbitraries.strings().ofMinLength(8).ofMaxLength(20)
                .withChars("ABCDEFXYZ0123456789abc.-/ ");
        return Arbitraries.oneOf(aleatorio, baseAlfanumerica().map(FixturesFiscais::cnpj));
    }
}
