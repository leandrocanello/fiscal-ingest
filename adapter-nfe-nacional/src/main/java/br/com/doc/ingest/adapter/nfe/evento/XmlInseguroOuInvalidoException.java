package br.com.doc.ingest.adapter.nfe.evento;

/**
 * XML recusado pelo {@link FabricaDocumentoSeguro} (malformado, com DOCTYPE ou entidade externa).
 *
 * <p>Não encadeia a causa do parser: a mensagem dela pode trazer trechos do conteúdo (invariante 13).
 */
public final class XmlInseguroOuInvalidoException extends RuntimeException {

    public XmlInseguroOuInvalidoException(String mensagem) {
        super(mensagem);
    }
}
