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
        Nsu nsu = lerNsu(docZip.getNsu());
        String prefixo = "docZip NSU " + nsu.valor();
        String conteudo = docZip.getContent();
        if (conteudo == null || conteudo.isBlank()) {
            throw new DocZipInvalidoException(prefixo + ": conteúdo vazio");
        }
        byte[] gzip;
        try {
            gzip = Base64.getMimeDecoder().decode(conteudo);
        } catch (IllegalArgumentException e) {
            throw new DocZipInvalidoException(prefixo + ": base64 inválido");
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            byte[] xml = in.readAllBytes();
            return new DocZipDecodificado(nsu,
                    ClassificadorSchema.classificar(docZip.getSchema()), docZip.getSchema(), xml);
        } catch (IOException e) {
            throw new DocZipInvalidoException(prefixo + ": gzip corrompido");
        }
    }

    private static Nsu lerNsu(String bruto) {
        try {
            return new Nsu(Long.parseLong(bruto.strip()));
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new DocZipInvalidoException("docZip com NSU ausente ou inválido");
        }
    }
}
