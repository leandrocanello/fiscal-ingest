package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do CStatMapper (inv. 1: nao-retrocesso de NSU).
 *
 * So 137/138 podem virar Ok/Vazio - os unicos Desfechos que deixam o loop
 * avancar o cursor. Qualquer outro codigo cai num Desfecho que nao avanca.
 */
class CStatMapperPropriedadesTest {

    private static final Set<String> SUCESSO = Set.of("137", "138");
    private static final Set<String> MAPEADOS = Set.of("137", "138", "656", "632", "589", "108", "109",
            "217", "236", "614", "615", "616", "617", "618", "619", "640", "641", "653", "654");

    private final CStatMapper mapper = new CStatMapper();

    @Property
    @Label("so 137/138 viram Ok ou Vazio; nenhum outro cStat pode avancar o cursor")
    void soCodigosDeSucessoViramOkOuVazio(@ForAll("foraDoSucesso") String cStat) {
        Desfecho resultado = mapper.mapear(cStat);

        assertThat(resultado)
                .isNotInstanceOf(Desfecho.Ok.class)
                .isNotInstanceOf(Desfecho.Vazio.class);
    }

    @Property
    @Label("qualquer cStat fora dos mapeados vira IndisponivelTemporario de 5 min")
    void codigosDesconhecidosViramFallback(@ForAll("desconhecidos") String cStat) {
        assertThat(mapper.mapear(cStat))
                .isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
    }

    @Provide
    Arbitrary<String> foraDoSucesso() {
        return qualquerCStat().filter(s -> !SUCESSO.contains(s));
    }

    @Provide
    Arbitrary<String> desconhecidos() {
        return qualquerCStat().filter(s -> !MAPEADOS.contains(s));
    }

    /** Strings livres + numericas de 3 digitos (onde moram os cStat reais) + os proprios codigos mapeados. */
    private static Arbitrary<String> qualquerCStat() {
        return Arbitraries.frequencyOf(
                Tuple.of(3, Arbitraries.strings().ofMaxLength(20)),
                Tuple.of(5, Arbitraries.strings().numeric().ofLength(3)),
                Tuple.of(2, Arbitraries.of(MAPEADOS)));
    }
}
