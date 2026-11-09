package br.com.doc.ingest.adapter.nfe.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.adapter.Desfecho;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/** Propriedades do CStatEventoMapper: só os códigos de sucesso de D1 dão Ok; nada lança. */
class CStatEventoMapperPropriedadesTest {

    private static final Set<String> SUCESSO = Set.of("135", "136", "573");
    private static final Set<String> CONHECIDOS = Set.of("128", "135", "136", "573", "108", "109", "656", "215", "225",
            "243", "252", "650", "655", "596", "217", "236", "614", "615", "616", "617", "618", "619", "575", "213",
            "297", "298");

    private final CStatEventoMapper mapper = new CStatEventoMapper();

    @Property
    @Label("evento: só 135/136/573 viram Ok e nenhum cStat lança")
    void eventoSoSucessoViraOk(@ForAll("qualquer") String cStat) {
        Desfecho d = mapper.mapearEvento(cStat);
        assertThat(d instanceof Desfecho.Ok).isEqualTo(SUCESSO.contains(cStat));
    }

    @Property
    @Label("lote: nunca vira Ok; só 128 devolve vazio (quem decide é o retEvento)")
    void loteNuncaViraOk(@ForAll("qualquer") String cStat) {
        var d = mapper.mapearLote(cStat);
        assertThat(d.map(x -> x instanceof Desfecho.Ok).orElse(false)).isFalse();
        assertThat(d.isEmpty()).isEqualTo("128".equals(cStat));
    }

    @Property
    @Label("nulo e strings arbitrárias não lançam")
    void nuloNaoLanca() {
        assertThat(mapper.mapearEvento(null)).isNotInstanceOf(Desfecho.Ok.class);
        assertThat(mapper.mapearLote(null)).isPresent();
    }

    @Provide
    Arbitrary<String> qualquer() {
        return Arbitraries.frequencyOf(
                Tuple.of(3, Arbitraries.strings().ofMaxLength(20)),
                Tuple.of(4, Arbitraries.strings().numeric().ofLength(3)),
                Tuple.of(3, Arbitraries.of(CONHECIDOS)));
    }
}
