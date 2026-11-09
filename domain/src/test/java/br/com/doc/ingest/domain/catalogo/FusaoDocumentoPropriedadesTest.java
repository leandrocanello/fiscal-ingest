package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.model.Cnpj;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Propriedades da {@link FusaoDocumento} (DA-27): o resultado não depende da ordem de chegada.
 *
 * <p>Os registros confiáveis concordam entre si, e os declarados também, mas os declarados divergem
 * dos confiáveis: um push não validado pode trazer qualquer coisa, sem influir no resultado.
 */
class FusaoDocumentoPropriedadesTest {

    private static final MetadadosDocumento VERDADE = new MetadadosDocumento(
            Optional.of(1),
            Optional.of(123L),
            Optional.of(OffsetDateTime.parse("2026-10-20T10:00:00-03:00")),
            Optional.of(new Cnpj("11111111000191")),
            Optional.of("12345678909"),
            Optional.of(new BigDecimal("1234.56")),
            Optional.of("4.00"));

    private static final MetadadosDocumento DECLARACAO = new MetadadosDocumento(
            Optional.of(9),
            Optional.of(999L),
            Optional.of(OffsetDateTime.parse("2020-01-01T00:00:00-03:00")),
            Optional.of(new Cnpj("22222222000191")),
            Optional.of("98765432100"),
            Optional.of(new BigDecimal("1.00")),
            Optional.of("3.10"));

    /** Um registro da mesma nota, com só alguns campos (a máscara diz quais). */
    record Recebido(MetadadosDocumento metadados, SituacaoDocumento situacao, Confianca confianca) {

        static Recebido de(int mascara, SituacaoDocumento situacao, Confianca confianca) {
            var base = confianca == Confianca.DECLARADO ? DECLARACAO : VERDADE;
            return new Recebido(subconjunto(base, mascara), situacao, confianca);
        }

        private static MetadadosDocumento subconjunto(MetadadosDocumento m, int mascara) {
            return new MetadadosDocumento(
                    m.serie().filter(v -> (mascara & 1) != 0),
                    m.numero().filter(v -> (mascara & 2) != 0),
                    m.emissao().filter(v -> (mascara & 4) != 0),
                    m.emitenteCnpj().filter(v -> (mascara & 8) != 0),
                    m.destinatarioDoc().filter(v -> (mascara & 16) != 0),
                    m.valorTotal().filter(v -> (mascara & 32) != 0),
                    m.versaoLayout().filter(v -> (mascara & 64) != 0));
        }
    }

    @Provide
    Arbitrary<List<Recebido>> registros() {
        return Combinators.combine(
                        Arbitraries.integers().between(0, 127),
                        Arbitraries.of(SituacaoDocumento.class),
                        Arbitraries.of(Confianca.class))
                .as(Recebido::de)
                .list().ofMinSize(1).ofMaxSize(12);
    }

    private static EstadoDocumento acumular(List<Recebido> registros) {
        var primeiro = registros.get(0);
        var estado = FusaoDocumento.inicial(primeiro.metadados(), primeiro.situacao(), primeiro.confianca());
        for (var registro : registros.subList(1, registros.size())) {
            estado = fundir(estado, registro);
        }
        return estado;
    }

    private static EstadoDocumento fundir(EstadoDocumento estado, Recebido registro) {
        return FusaoDocumento.fundir(estado, registro.metadados(), registro.situacao(), registro.confianca());
    }

    @Property
    void ordemDeChegadaNaoMudaOResultado(@ForAll("registros") List<Recebido> registros, @ForAll long semente) {
        var embaralhados = new ArrayList<>(registros);
        Collections.shuffle(embaralhados, new Random(semente));

        assertThat(acumular(embaralhados)).isEqualTo(acumular(registros));
    }

    @Property
    void fusaoEhIdempotente(@ForAll("registros") List<Recebido> registros) {
        var estado = acumular(registros);

        for (var registro : registros) {
            assertThat(fundir(estado, registro)).isEqualTo(estado);
        }
    }
}
