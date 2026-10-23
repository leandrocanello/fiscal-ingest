package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.adapter.nfe.soap.DocZip;
import br.com.doc.ingest.domain.model.Nsu;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/** Decodifica o conteúdo de um {@link DocZip}: base64 (MIME) + gzip. */
public final class DocZipDecoder {

    /** Teto de bytes após descompactar (DA-6), contra gzip bomb. */
    public static final int LIMITE_PADRAO_BYTES = 10 * 1024 * 1024;

    private final int limiteBytes;

    public DocZipDecoder() {
        this(LIMITE_PADRAO_BYTES);
    }

    public DocZipDecoder(int limiteBytes) {
        if (limiteBytes <= 0) {
            throw new IllegalArgumentException("limiteBytes deve ser positivo");
        }
        this.limiteBytes = limiteBytes;
    }

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
            byte[] xml = lerComTeto(in, prefixo);
            return new DocZipDecodificado(nsu,
                    ClassificadorSchema.classificar(docZip.getSchema()), docZip.getSchema(), xml);
        } catch (IOException e) {
            throw new DocZipInvalidoException(prefixo + ": gzip corrompido");
        }
    }

    private byte[] lerComTeto(GZIPInputStream in, String prefixo) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] bloco = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(bloco)) != -1) {
            total += n;
            if (total > limiteBytes) {
                throw new DocZipInvalidoException(
                        prefixo + ": conteúdo descompactado excede o limite de " + limiteBytes + " bytes");
            }
            saida.write(bloco, 0, n);
        }
        return saida.toByteArray();
    }

    private static Nsu lerNsu(String bruto) {
        try {
            return new Nsu(Long.parseLong(bruto.strip()));
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new DocZipInvalidoException("docZip com NSU ausente ou inválido");
        }
    }
}
