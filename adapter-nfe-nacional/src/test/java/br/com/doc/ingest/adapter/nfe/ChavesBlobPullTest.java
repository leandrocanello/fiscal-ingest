package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("ChavesBlobPull (layout de chaves do pull, DA-13)")
class ChavesBlobPullTest {

    private static final String SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");

    @Test
    @DisplayName("envelope: fonte/ambiente/cnpj/aaaa/mm/dd (UTC)/sha.envelope.xml")
    void chaveDoEnvelope() {
        // 23:30 em -03:00 já é dia 28 em UTC
        var instante = Instant.parse("2026-10-28T02:30:00Z");

        var chave = ChavesBlobPull.envelope(FONTE, TipoAmbiente.PRODUCAO, CNPJ, instante, SHA);

        assertThat(chave).isEqualTo(
                "raw/pull/NFE_AN/PRODUCAO/11111111000191/2026/10/28/" + SHA + ".envelope.xml");
    }

    @Test
    @DisplayName("documento: fonte/ambiente/cnpj/nsu/<nsu 15 dígitos>-sha.xml")
    void chaveDoDocumento() {
        var chave = ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(42), SHA);

        assertThat(chave).isEqualTo(
                "raw/pull/NFE_AN/PRODUCAO/11111111000191/nsu/000000000000042-" + SHA + ".xml");
    }

    @Test
    @DisplayName("ambiente entra na chave: homologação não colide com produção")
    void ambienteSeparaAsChaves() {
        var prod = ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(1), SHA);
        var homo = ChavesBlobPull.documento(FONTE, TipoAmbiente.HOMOLOGACAO, CNPJ, new Nsu(1), SHA);

        assertThat(prod).isNotEqualTo(homo);
        assertThat(homo).contains("/HOMOLOGACAO/");
    }

    @Test
    @DisplayName("inv. 7: toda chave contém o sha256 do conteúdo")
    void chaveContemSha256() {
        var instante = Instant.parse("2026-10-28T12:00:00Z");

        assertThat(ChavesBlobPull.envelope(FONTE, TipoAmbiente.PRODUCAO, CNPJ, instante, SHA)).contains(SHA);
        assertThat(ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(7), SHA)).contains(SHA);
    }

    @Test
    @DisplayName("as chaves geradas são aceitas por BlobRef")
    void chaveAceitaPorBlobRef() {
        var instante = Instant.parse("2026-10-28T12:00:00Z");

        assertThatCode(() -> new BlobRef(
                ChavesBlobPull.envelope(FONTE, TipoAmbiente.PRODUCAO, CNPJ, instante, SHA), SHA))
                .doesNotThrowAnyException();
        assertThatCode(() -> new BlobRef(
                ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(Long.MAX_VALUE), SHA), SHA))
                .doesNotThrowAnyException();
    }
}
