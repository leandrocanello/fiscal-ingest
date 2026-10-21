package br.com.doc.ingest.adapter.nfe.soap;

import jakarta.xml.bind.annotation.*;

/**
 * Request do distDFe (distribuicao de DF-e).
 *
 * <p>XML:
 * <pre>{@code
 * <distDFeInt versao="1.01" xmlns="http://www.portalfiscal.inf.br/nfe">
 *     <tpAmb>1</tpAmb>
 *     <CNPJ>12345678000199</CNPJ>
 *     <distNSU>
 *         <ultNSU>0</ultNSU>
 *     </distNSU>
 * </distDFeInt>
 * }</pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "distDFeInt", namespace = "http://www.portalfiscal.inf.br/nfe")
public class DistDfeInt {

    @XmlAttribute(name = "versao")
    private String versao;

    @XmlElement(name = "tpAmb")
    private String tpAmb;

    @XmlElement(name = "CNPJ")
    private String cnpj;

    @XmlElement(name = "distNSU")
    private DistNsu distNsu;

    public String getVersao() { return versao; }
    public void setVersao(String versao) { this.versao = versao; }

    public String getTpAmb() { return tpAmb; }
    public void setTpAmb(String tpAmb) { this.tpAmb = tpAmb; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getUltNsu() { return distNsu != null ? distNsu.ultNSU : null; }
    public void setUltNsu(String ultNsu) {
        if (distNsu == null) distNsu = new DistNsu();
        distNsu.ultNSU = ultNsu;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DistNsu implements java.io.Serializable {
        @XmlElement(name = "ultNSU")
        private String ultNSU;
    }
}
