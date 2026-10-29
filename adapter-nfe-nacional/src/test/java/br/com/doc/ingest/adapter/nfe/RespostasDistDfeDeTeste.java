package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.adapter.nfe.soap.ContratoDistDfe;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/** Montadores de resposta SOAP 1.2 do distDFe com docZips sintéticos (gzip + base64), só para testes. */
final class RespostasDistDfeDeTeste {

    static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";

    private RespostasDistDfeDeTeste() {
    }

    static String gzipBase64(String xml) {
        var saida = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(saida)) {
            gz.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    static String docZip(int nsu, String schema, String xml) {
        return "<docZip NSU=\"" + String.format("%015d", nsu) + "\" schema=\"" + schema + "\">"
                + gzipBase64(xml) + "</docZip>";
    }

    /** Envelope completo; {@code lote} são os docZips já montados (vazio = sem loteDistDFeInt). */
    static String resposta(String cStat, int ultNsu, int maxNsu, String lote) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"" + ContratoDistDfe.NS_SOAP12 + "\"><soap:Body>"
                + "<nfeDistDFeInteresseResponse xmlns=\"" + ContratoDistDfe.NS_WSDL + "\"><nfeDistDFeInteresseResult>"
                + "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\">"
                + "<tpAmb>1</tpAmb><verAplic>1.0</verAplic><cStat>" + cStat + "</cStat>"
                + "<xMotivo>Documento localizado</xMotivo><dhResp>2026-10-28T12:00:00-03:00</dhResp>"
                + "<ultNSU>" + String.format("%015d", ultNsu) + "</ultNSU>"
                + "<maxNSU>" + String.format("%015d", maxNsu) + "</maxNSU>"
                + (lote.isEmpty() ? "" : "<loteDistDFeInt>" + lote + "</loteDistDFeInt>")
                + "</retDistDFeInt></nfeDistDFeInteresseResult></nfeDistDFeInteresseResponse>"
                + "</soap:Body></soap:Envelope>";
    }
}
