package br.com.doc.ingest.adapter.nfe.evento;

/**
 * Constantes do protocolo NFeRecepcaoEvento, isoladas aqui (mesmo padrão de {@code ContratoDistDfe}).
 * A URL não entra no código: é injetada.
 *
 * <p>Fonte: decisão E2-S13 D2 (2026-11-03).
 * [VALIDAR docs/FONTES-OFICIAIS.md item 9]: valores a confirmar no WSDL e no smoke (E2-S12).
 */
public final class ContratoRecepcaoEvento {

    public static final String NS_SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    public static final String NS_WSDL = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    public static final String OPERACAO = "nfeRecepcaoEvento";
    public static final String ACTION = NS_WSDL + "/" + OPERACAO;
    public static final String VERSAO_LOTE = "1.00";

    private ContratoRecepcaoEvento() {
    }
}
