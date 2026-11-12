package br.com.doc.ingest.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/** Propriedades da {@link ChaveAcesso} (E1-S07; DA-38). */
class ChaveAcessoPropriedadesTest {

    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Provide
    Arbitrary<String> base43() {
        Arbitrary<String> uf = Arbitraries.strings().numeric().ofLength(6);
        Arbitrary<String> cnpj = Arbitraries.strings().withChars(ALNUM).ofLength(12);
        Arbitrary<String> resto = Arbitraries.strings().numeric().ofLength(25);
        return Combinators(uf, cnpj, resto);
    }

    private static Arbitrary<String> Combinators(Arbitrary<String> a, Arbitrary<String> b, Arbitrary<String> c) {
        return net.jqwik.api.Combinators.combine(a, b, c).as((x, y, z) -> x + y + z);
    }

    @Provide
    Arbitrary<String> chaveValida() {
        return base43().map(FixturesFiscais::chave);
    }

    @Property
    void formatoDaNtEAceito(@ForAll("chaveValida") String chave) {
        assertThat(ChaveAcesso.formatoValido(chave)).isTrue();
        assertThat(new ChaveAcesso(chave).temDvValido()).isTrue();
    }

    @Property
    void letraForaDasPosicoes7a18ERecusada(
            @ForAll("chaveValida") String chave, @ForAll int posicao,
            @ForAll("letra") char letra) {
        int pos = Math.floorMod(posicao, 44);
        if (pos >= 6 && pos < 18) {
            return;
        }
        String alterada = chave.substring(0, pos) + letra + chave.substring(pos + 1);
        assertThat(ChaveAcesso.formatoValido(alterada)).isFalse();
    }

    @Provide
    Arbitrary<Character> letra() {
        return Arbitraries.chars().range('A', 'Z');
    }

    @Property
    void alterarOUltimoDigitoQuebraODv(@ForAll("chaveValida") String chave, @ForAll("digito") char novo) {
        if (chave.charAt(43) == novo) {
            return;
        }
        String alterada = chave.substring(0, 43) + novo;
        ChaveAcesso c = new ChaveAcesso(alterada);
        assertThat(c.temDvValido()).isFalse();
    }

    @Provide
    Arbitrary<Character> digito() {
        return Arbitraries.chars().range('0', '9');
    }

    @Property
    void chaveNumericaComDvCertoContinuaAceita(@ForAll("baseNumerica") String base) {
        String chave = FixturesFiscais.chave(base);
        assertThat(ChaveAcesso.formatoValido(chave)).isTrue();
        assertThat(new ChaveAcesso(chave).temDvValido()).isTrue();
    }

    @Provide
    Arbitrary<String> baseNumerica() {
        return Arbitraries.strings().numeric().ofLength(43);
    }
}
