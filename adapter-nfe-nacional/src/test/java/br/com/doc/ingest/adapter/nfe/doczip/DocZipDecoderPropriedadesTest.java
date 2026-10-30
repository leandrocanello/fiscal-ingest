package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.model.Nsu;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** Propriedades do {@link DocZipDecoder} (ADR-026). */
class DocZipDecoderPropriedadesTest {

    private final DocZipDecoder decoder = new DocZipDecoder();

    @Property
    void idaEVoltaPreservaBytes(@ForAll @Size(max = 4096) byte[] xml,
                                @ForAll @LongRange(min = 0, max = Long.MAX_VALUE) long nsu,
                                @ForAll boolean comQuebras) throws IOException {
        DocZipDecodificado r = decoder.decodificar(DocZipDecoderTest.docZip(
                Long.toString(nsu), "procNFe_v4.00.xsd", DocZipDecoderTest.gzipBase64(xml, comQuebras)));

        assertThat(r.xml()).isEqualTo(xml);
        assertThat(r.nsu()).isEqualTo(new Nsu(nsu));
    }

    @Property
    void nuncaLancaOutraExcecao(@ForAll("conteudos") String conteudo,
                                @ForAll("nsus") String nsu,
                                @ForAll("schemas") String schema) {
        try {
            decoder.decodificar(DocZipDecoderTest.docZip(nsu, schema, conteudo));
        } catch (DocZipInvalidoException esperada) {
            // único tipo de falha permitido
        }
    }

    @Provide
    Arbitrary<String> conteudos() {
        return Arbitraries.oneOf(
                Arbitraries.strings().ofMaxLength(200),
                Arbitraries.strings().withCharRange('A', 'Z').withChars('+', '/', '=', '\n').ofMaxLength(200),
                Arbitraries.just(null));
    }

    @Provide
    Arbitrary<String> nsus() {
        return Arbitraries.oneOf(
                Arbitraries.strings().ofMaxLength(25),
                Arbitraries.longs().map(String::valueOf),
                Arbitraries.just(null));
    }

    @Provide
    Arbitrary<String> schemas() {
        return Arbitraries.oneOf(Arbitraries.strings().ofMaxLength(30), Arbitraries.just(null));
    }
}
