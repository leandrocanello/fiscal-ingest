package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.adapter.nfe.soap.DocZip;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Decoder de docZip (base64 + gzip)")
class DocZipDecoderTest {

    private final DocZipDecoder decoder = new DocZipDecoder();

    static String gzipBase64(byte[] xml, boolean comQuebras) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(saida)) {
            gz.write(xml);
        }
        if (comQuebras) {
            return Base64.getMimeEncoder(16, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(saida.toByteArray());
        }
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    static DocZip docZip(String nsu, String schema, String conteudo) {
        DocZip dz = new DocZip();
        dz.setNsu(nsu);
        dz.setSchema(schema);
        dz.setContent(conteudo);
        return dz;
    }

    @Test
    @DisplayName("decodificaBase64GzipDevolveXmlOriginal")
    void decodificaBase64GzipDevolveXmlOriginal() throws IOException {
        byte[] xml = "<resNFe><chNFe>123</chNFe></resNFe>".getBytes(StandardCharsets.UTF_8);

        for (boolean quebras : new boolean[]{false, true}) {
            DocZipDecodificado r = decoder.decodificar(
                    docZip("7", "resNFe_v1.01.xsd", gzipBase64(xml, quebras)));
            assertThat(r.xml()).isEqualTo(xml);
            assertThat(r.tipo()).isEqualTo(TipoSchemaDfe.RES_NFE);
            assertThat(r.schemaBruto()).isEqualTo("resNFe_v1.01.xsd");
        }
    }

    @Test
    @DisplayName("nsuDoAtributoViraNsu")
    void nsuDoAtributoViraNsu() throws IOException {
        DocZipDecodificado r = decoder.decodificar(
                docZip("12345", "procNFe_v4.00.xsd", gzipBase64(new byte[]{1, 2, 3}, false)));
        assertThat(r.nsu()).isEqualTo(new Nsu(12345));
        assertThat(r.tipo()).isEqualTo(TipoSchemaDfe.PROC_NFE);
    }
}
