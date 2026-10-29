package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

class IdEventoPropriedadesTest {

    @Provide
    Arbitrary<String> chaves() {
        return Arbitraries.strings().numeric().ofLength(44);
    }

    @Property
    void idTem54CaracteresPrefixoDeCienciaESeqEmDoisDigitos(@ForAll("chaves") String chave,
                                                            @ForAll @IntRange(min = 1, max = 99) int seq) {
        String id = new IdEvento(TipoManifestacao.CIENCIA, chave, seq).valor();

        assertThat(id).hasSize(54).startsWith("ID210210" + chave).endsWith(String.format("%02d", seq));
    }
}
