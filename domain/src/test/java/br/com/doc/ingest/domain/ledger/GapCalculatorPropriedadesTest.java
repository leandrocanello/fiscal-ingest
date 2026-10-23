package br.com.doc.ingest.domain.ledger;

import br.com.doc.ingest.domain.model.Nsu;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do {@link GapCalculator} (ADR-005 — ledger append-only com reinserção ignorada; ADR-026).
 *
 * <p>Os gaps são exatamente o intervalo [menor, maior] menos os NSUs conhecidos, independentemente
 * de ordem, duplicatas e {@link StatusNsu}.
 */
class GapCalculatorPropriedadesTest {

    private final GapCalculator calculadora = new GapCalculator();

    @Provide
    Arbitrary<List<EntradaLedger>> ledgers() {
        Arbitrary<Long> base = Arbitraries.longs().between(0L, 1_000_000_000_000L);
        Arbitrary<StatusNsu> status = Arbitraries.of(StatusNsu.class);
        return base.flatMap(b -> Arbitraries.longs().between(0L, 2000L)
                .flatMap(off -> status.map(s -> new EntradaLedger(new Nsu(b + off), s)))
                .list().ofMinSize(1).ofMaxSize(60));
    }

    private static Set<Long> valores(List<EntradaLedger> entradas) {
        return entradas.stream().map(e -> e.nsu().valor()).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<Long> valoresGaps(Set<Nsu> gaps) {
        return gaps.stream().map(Nsu::valor).collect(Collectors.toCollection(TreeSet::new));
    }

    @Property
    void gapsNaoIntersectamConhecidos(@ForAll("ledgers") List<EntradaLedger> entradas) {
        Set<Long> conhecidos = valores(entradas);
        assertThat(valoresGaps(calculadora.calcular(entradas))).doesNotContainAnyElementsOf(conhecidos);
    }

    @Property
    void gapsMaisConhecidosCobremExatamenteOIntervalo(@ForAll("ledgers") List<EntradaLedger> entradas) {
        TreeSet<Long> conhecidos = (TreeSet<Long>) valores(entradas);
        Set<Long> uniao = new TreeSet<>(conhecidos);
        uniao.addAll(valoresGaps(calculadora.calcular(entradas)));
        Set<Long> esperado = LongStream.rangeClosed(conhecidos.first(), conhecidos.last())
                .boxed().collect(Collectors.toCollection(TreeSet::new));
        assertThat(uniao).isEqualTo(esperado);
    }

    @Property
    void todoGapEstaEstritamenteEntreMenorEMaior(@ForAll("ledgers") List<EntradaLedger> entradas) {
        TreeSet<Long> conhecidos = (TreeSet<Long>) valores(entradas);
        assertThat(valoresGaps(calculadora.calcular(entradas)))
                .allMatch(g -> g > conhecidos.first() && g < conhecidos.last());
    }

    @Property
    void independeDePermutacaoEDeDuplicatas(@ForAll("ledgers") List<EntradaLedger> entradas,
                                            @ForAll Random random) {
        Set<Nsu> original = calculadora.calcular(entradas);

        List<EntradaLedger> embaralhada = new ArrayList<>(entradas);
        Collections.shuffle(embaralhada, random);
        assertThat(calculadora.calcular(embaralhada)).isEqualTo(original);

        List<EntradaLedger> duplicada = new ArrayList<>(entradas);
        duplicada.addAll(entradas);
        Collections.shuffle(duplicada, random);
        assertThat(calculadora.calcular(duplicada)).isEqualTo(original);
    }

    @Property
    void independeDoStatus(@ForAll("ledgers") List<EntradaLedger> entradas, @ForAll StatusNsu novoStatus) {
        List<EntradaLedger> trocado = entradas.stream()
                .map(e -> new EntradaLedger(e.nsu(), novoStatus))
                .toList();
        assertThat(calculadora.calcular(trocado)).isEqualTo(calculadora.calcular(entradas));
    }

    @Property
    void umUnicoElementoNaoTemGaps(@ForAll("ledgers") List<EntradaLedger> entradas) {
        assertThat(calculadora.calcular(List.of(entradas.get(0)))).isEmpty();
    }
}
