package br.com.doc.ingest.adapter.nfe.soap;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * Response do distDFe (retorno da distribuicao de DF-e).
 *
 * <p>XML:
 * <pre>{@code
 * <retDistDFeInt versao="1.01" xmlns="http://www.portalfiscal.inf.br/nfe">
 *     <tpAmb>1</tpAmb>
 *     <verAplic>1.0</verAplic>
 *     <cStat>138</cStat>
 *     <xMotivo>Documentos localizados</xMotivo>
 *     <dhResp>2026-01-01T12:00:00-03:00</dhResp>
 *     <ultNSU>5</ultNSU>
 *     <maxNSU>10</maxNSU>
 *     <loteDistDFeInt>
 *         <docZip NSU="1" schema="resNFe">dGVzdGU=</docZip>
 *     </loteDistDFeInt>
 * </retDistDFeInt>
 * }</pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "retDistDFeInt", namespace = "http://www.portalfiscal.inf.br/nfe")
public class RetDistDfeInt {

    @XmlAttribute(name = "versao")
    private String versao;

    @XmlElement(name = "tpAmb")
    private String tpAmb;

    @XmlElement(name = "verAplic")
    private String verAplic;

    @XmlElement(name = "cStat")
    private String cStat;

    @XmlElement(name = "xMotivo")
    private String xMotivo;

    @XmlElement(name = "dhResp")
    private String dhResp;

    @XmlElement(name = "ultNSU")
    private String ultNSU;

    @XmlElement(name = "maxNSU")
    private String maxNSU;

    @XmlElement(name = "loteDistDFeInt")
    private LoteDistDFeInt lote;

    public String getVersao() { return versao; }
    public String getTpAmb() { return tpAmb; }
    public String getVerAplic() { return verAplic; }
    public String getCStat() { return cStat; }
    public String getxMotivo() { return xMotivo; }
    public String getDhResp() { return dhResp; }
    public String getUltNSU() { return ultNSU; }
    public String getMaxNSU() { return maxNSU; }

    public List<DocZip> getDocumentos() {
        return lote != null ? lote.documentos : null;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class LoteDistDFeInt {
        @XmlElement(name = "docZip")
        private List<DocZip> documentos;
    }
}
