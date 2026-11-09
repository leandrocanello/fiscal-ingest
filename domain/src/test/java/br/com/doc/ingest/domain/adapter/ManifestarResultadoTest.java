package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.blob.BlobRef;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ManifestarResultado (só desfecho + referência do envelope)")
class ManifestarResultadoTest {

    private static final BlobRef ENVELOPE = new BlobRef(
            "raw/pull/NFE_AN/PRODUCAO/11111111000191/2026/11/04/" + "a".repeat(64) + ".envelope.xml",
            "a".repeat(64));

    @Test
    @DisplayName("guarda o desfecho e o BlobRef do envelope")
    void guardaDesfechoEEnvelope() {
        var resultado = new ManifestarResultado(new Desfecho.Ok(), Optional.of(ENVELOPE));

        assertThat(resultado.desfecho()).isEqualTo(new Desfecho.Ok());
        assertThat(resultado.envelope()).contains(ENVELOPE);
    }

    @Test
    @DisplayName("desfecho e envelope nulos são rejeitados (use Optional.empty())")
    void desfechoEEnvelopeObrigatorios() {
        assertThatThrownBy(() -> new ManifestarResultado(null, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("desfecho");
        assertThatThrownBy(() -> new ManifestarResultado(new Desfecho.Ok(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("envelope");
    }

    @Test
    @DisplayName("não carrega bytes nem XML: só Desfecho + Optional<BlobRef> (inv. 3)")
    void semBytes() {
        var componentes = ManifestarResultado.class.getRecordComponents();

        assertThat(Arrays.stream(componentes).map(RecordComponent::getGenericType).map(Object::toString))
                .containsExactly(
                        Desfecho.class.toString(),
                        "java.util.Optional<" + BlobRef.class.getName() + ">");
    }
}
