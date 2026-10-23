package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.adapter.nfe.soap.DocZip;
import br.com.doc.ingest.domain.model.Nsu;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/** Decodifica o conteúdo de um {@link DocZip}: base64 (MIME) + gzip. */
public final class DocZipDecoder {

    public DocZipDecodificado decodificar(DocZip docZip) {
        try {
            Nsu nsu = new Nsu(Long.parseLong(docZip.getNsu().strip()));
            byte[] gzip = Base64.getMimeDecoder().decode(docZip.getContent());
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
                byte[] xml = in.readAllBytes();
                return new DocZipDecodificado(nsu,
                        ClassificadorSchema.classificar(docZip.getSchema()), docZip.getSchema(), xml);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
