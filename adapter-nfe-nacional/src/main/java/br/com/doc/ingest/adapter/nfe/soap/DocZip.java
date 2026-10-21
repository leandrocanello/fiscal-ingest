package br.com.doc.ingest.adapter.nfe.soap;

import jakarta.xml.bind.annotation.*;

/**
 * Documento zipado (base64) dentro do lote do distDFe.
 *
 * <p>XML:
 * <pre>{@code
 * <docZip NSU="1" schema="resNFe">dGVzdGU=</docZip>
 * }</pre>
 *
 * @param nsu     o NSU do documento
 * @param schema  o schema do documento (resNFe, procNFe, procEventoNFe, etc.)
 * @param content o conteudo zipado em base64
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class DocZip {

    @XmlAttribute(name = "NSU")
    private String nsu;

    @XmlAttribute(name = "schema")
    private String schema;

    @XmlValue
    private String content;

    public String getNsu() { return nsu; }
    public void setNsu(String nsu) { this.nsu = nsu; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
