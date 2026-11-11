package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.model.FixturesFiscais;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

class IdEventoPropriedadesTest {

    @Provide
    Arbitrary<String> chaves() {
        Arbitrary<String> uf = Arbitraries.strings().numeric().ofLength(6);
        Arbitrary<String> cnpj = Arbitraries.strings().numeric().withCharRange('A', 'Z').ofLength(12);
        Arbitrary<String> resto = Arbitraries.strings().numeric().ofLength(25);
        return Combinators.combine(uf, cnpj, resto)
                .as((u, c, r) -> FixturesFiscais.chave(u + c + r));
    }

    @Property
    void idTem54CaracteresPrefixoDeCienciaESeqEmDoisDigitos(@ForAll("chaves") String chave,
                                                            @ForAll @IntRange(min = 1, max = 99) int seq) {
        String id = new IdEvento(TipoManifestacao.CIENCIA, chave, seq).valor();

        assertThat(id).hasSize(54).startsWith("ID210210" + chave).endsWith(String.format("%02d", seq));
    }
}
