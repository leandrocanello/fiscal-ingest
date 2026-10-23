package br.com.doc.ingest.adapter.nfe.doczip;

/** docZip que não pôde ser decodificado. A mensagem nunca carrega conteúdo do documento. */
public class DocZipInvalidoException extends RuntimeException {

    public DocZipInvalidoException(String mensagem) {
        super(mensagem);
    }

    public DocZipInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
