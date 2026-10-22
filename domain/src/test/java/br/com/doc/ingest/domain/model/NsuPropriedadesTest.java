package br.com.doc.ingest.domain.model;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Negative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propriedades do {@link Nsu} (CLAUDE.md §2.2, invariante 1 — não-retrocesso de NSU; ADR-026).
 *
 * <p>Base do não-retrocesso: ordem total consistente e {@code proximo()} sempre estritamente
 * maior, sem nunca "dar a volta" para um NSU menor.
 */
class NsuPropriedadesTest {

    @Property
    void naoNegativoCriaSemExcecao(@ForAll @LongRange(min = 0) long valor) {
        assertThatCode(() -> new Nsu(valor)).doesNotThrowAnyException();
    }

    @Property
    void negativoLancaIllegalArgument(@ForAll @Negative long valor) {
        assertThatThrownBy(() -> new Nsu(valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void comparacaoEhCoerenteComOrdemTotal(
            @ForAll @LongRange(min = 0) long a,
            @ForAll @LongRange(min = 0) long b,
            @ForAll @LongRange(min = 0) long c) {
        Nsu x = new Nsu(a);
        Nsu y = new Nsu(b);
        Nsu z = new Nsu(c);

        int exatamenteUm = (x.ehMenorQue(y) ? 1 : 0) + (x.equals(y) ? 1 : 0) + (x.ehMaiorQue(y) ? 1 : 0);
        assertThat(exatamenteUm).isEqualTo(1);

        assertThat(x.ehMenorQue(y)).isEqualTo(x.compareTo(y) < 0);
        assertThat(x.ehMaiorQue(y)).isEqualTo(x.compareTo(y) > 0);
        assertThat(x.equals(y)).isEqualTo(x.compareTo(y) == 0);

        // antissimetria
        assertThat(Integer.signum(x.compareTo(y))).isEqualTo(-Integer.signum(y.compareTo(x)));

        // transitividade
        if (x.ehMenorQue(y) && y.ehMenorQue(z)) {
            assertThat(x.ehMenorQue(z)).isTrue();
        }
        if (x.ehMaiorQue(y) && y.ehMaiorQue(z)) {
            assertThat(x.ehMaiorQue(z)).isTrue();
        }
    }

    @Property
    void proximoEhEstritamenteMaior(@ForAll @LongRange(min = 0, max = Long.MAX_VALUE - 1) long valor) {
        Nsu n = new Nsu(valor);

        assertThat(n.proximo().ehMaiorQue(n)).isTrue();
        assertThat(n.proximo().valor()).isEqualTo(valor + 1);
    }

    @net.jqwik.api.Example
    void proximoDoMaximoNuncaDaAVolta() {
        assertThatThrownBy(() -> new Nsu(Long.MAX_VALUE).proximo())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
