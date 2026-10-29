package br.com.doc.ingest.adapter.nfe.soap;

/**
 * Constantes do protocolo NFeDistribuicaoDFe, isoladas aqui (DA-7).
 * [VALIDAR docs/FONTES-OFICIAIS.md item 9]: valores usuais do protocolo, a confirmar no WSDL e no smoke (E2-S12).
 */
public final class ContratoDistDfe {

    public static final String NS_SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    public static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    public static final String NS_WSDL = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe";
    public static final String OPERACAO = "nfeDistDFeInteresse";
    public static final String ACTION = NS_WSDL + "/" + OPERACAO;
    public static final String VERSAO_LAYOUT = "1.01";

    private ContratoDistDfe() {
    }
}
