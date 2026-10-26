package br.com.doc.ingest.adapter.nfe.soap;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Montagem do distDFeInt por ultNSU")
class EnvelopeDistDfeTest {

    private static final Cnpj CNPJ = new Cnpj("11111111000191");

    private String monta(TipoAmbiente ambiente, Nsu nsu) {
        return new String(EnvelopeDistDfe.distNsu(ambiente, CNPJ, nsu), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("ultNSU vai com quinze dígitos")
    void ultNsuVaiComQuinzeDigitos() {
        assertThat(monta(TipoAmbiente.PRODUCAO, new Nsu(100)))
                .contains("<ultNSU>000000000000100</ultNSU>");
        assertThat(monta(TipoAmbiente.PRODUCAO, new Nsu(0)))
                .contains("<ultNSU>000000000000000</ultNSU>");
    }

    @Test
    @DisplayName("CNPJ, ambiente e versão vão no corpo, sem declaração XML")
    void cnpjEAmbienteVaoNoCorpo() {
        String xml = monta(TipoAmbiente.HOMOLOGACAO, new Nsu(1));
        assertThat(xml)
                .contains("<tpAmb>2</tpAmb>")
                .contains("<CNPJ>11111111000191</CNPJ>")
                .contains("versao=\"1.01\"")
                .doesNotContain("<?xml");
    }
}
