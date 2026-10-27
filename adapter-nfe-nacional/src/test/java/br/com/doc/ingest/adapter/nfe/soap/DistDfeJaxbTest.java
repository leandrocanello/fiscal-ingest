package br.com.doc.ingest.adapter.nfe.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Modelos JAXB do distDFe")
class DistDfeJaxbTest {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";

    private JAXBContext ctx(Class<?> c) throws JAXBException {
        return JAXBContext.newInstance(c);
    }

    @Nested
    @DisplayName("Request")
    class RequestDistDfe {

        @Test
        @DisplayName("unmarshal request distNSU")
        void deveUnmarshalRequestDistNsu() throws Exception {
            String xml = "<distDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\"><tpAmb>1</tpAmb><CNPJ>12345678000199</CNPJ><distNSU><ultNSU>0</ultNSU></distNSU></distDFeInt>";
            Unmarshaller u = ctx(DistDfeInt.class).createUnmarshaller();
            DistDfeInt req = (DistDfeInt) u.unmarshal(new StringReader(xml));
            assertThat(req.getVersao()).isEqualTo("1.01");
            assertThat(req.getTpAmb()).isEqualTo("1");
            assertThat(req.getCnpj()).isEqualTo("12345678000199");
            assertThat(req.getUltNsu()).isEqualTo("0");
        }

        @Test
        @DisplayName("marshal request para XML")
        void deveMarshalRequestDistNsu() throws Exception {
            DistDfeInt req = new DistDfeInt();
            req.setVersao("1.01");
            req.setTpAmb("1");
            req.setCnpj("12345678000199");
            req.setUltNsu("100");
            Marshaller m = ctx(DistDfeInt.class).createMarshaller();
            StringWriter sw = new StringWriter();
            m.marshal(req, sw);
            String xml = sw.toString();
            assertThat(xml).contains("distDFeInt").contains("12345678000199").contains("ultNSU").contains("100");
        }

        @Test
        @DisplayName("marshal respeita a ordem do schema: tpAmb, CNPJ, distNSU")
        void marshalRespeitaOrdemDoSchema() throws Exception {
            DistDfeInt req = new DistDfeInt();
            req.setVersao("1.01");
            req.setTpAmb("1");
            req.setCnpj("11111111000191");
            req.setUltNsu("100");
            Marshaller m = ctx(DistDfeInt.class).createMarshaller();
            StringWriter sw = new StringWriter();
            m.marshal(req, sw);
            String xml = sw.toString();
            assertThat(xml).contains("versao=\"1.01\"");
            assertThat(xml.indexOf("<tpAmb>")).isNotNegative().isLessThan(xml.indexOf("<CNPJ>"));
            assertThat(xml.indexOf("<CNPJ>")).isLessThan(xml.indexOf("<distNSU>"));
        }
    }

    @Nested
    @DisplayName("Response")
    class ResponseRetDistDfe {

        @Test
        @DisplayName("unmarshal response 138 com documentos")
        void deveUnmarshalResponseComDocumentos() throws Exception {
            String xml = "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\"><tpAmb>1</tpAmb><verAplic>1.0</verAplic><cStat>138</cStat><xMotivo>Doc localizados</xMotivo><dhResp>2026-01-01T12:00:00-03:00</dhResp><ultNSU>5</ultNSU><maxNSU>10</maxNSU><loteDistDFeInt><docZip NSU=\"1\" schema=\"resNFe\">dGVzdGU=</docZip><docZip NSU=\"2\" schema=\"procNFe\">dGVzdGUy</docZip></loteDistDFeInt></retDistDFeInt>";
            Unmarshaller u = ctx(RetDistDfeInt.class).createUnmarshaller();
            RetDistDfeInt resp = (RetDistDfeInt) u.unmarshal(new StringReader(xml));
            assertThat(resp.getCStat()).isEqualTo("138");
            assertThat(resp.getxMotivo()).isEqualTo("Doc localizados");
            assertThat(resp.getUltNSU()).isEqualTo("5");
            assertThat(resp.getMaxNSU()).isEqualTo("10");
            assertThat(resp.getDocumentos()).hasSize(2);
            assertThat(resp.getDocumentos().get(0).getNsu()).isEqualTo("1");
            assertThat(resp.getDocumentos().get(0).getSchema()).isEqualTo("resNFe");
            assertThat(resp.getDocumentos().get(1).getNsu()).isEqualTo("2");
        }

        @Test
        @DisplayName("unmarshal response 137 vazio")
        void deveUnmarshalResponseVazio() throws Exception {
            String xml = "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\"><tpAmb>1</tpAmb><verAplic>1.0</verAplic><cStat>137</cStat><xMotivo>Nenhum doc</xMotivo><dhResp>2026-01-01T12:00:00-03:00</dhResp><ultNSU>0</ultNSU><maxNSU>0</maxNSU></retDistDFeInt>";
            Unmarshaller u = ctx(RetDistDfeInt.class).createUnmarshaller();
            RetDistDfeInt resp = (RetDistDfeInt) u.unmarshal(new StringReader(xml));
            assertThat(resp.getCStat()).isEqualTo("137");
            assertThat(resp.getDocumentos()).isNull();
        }

        @Test
        @DisplayName("unmarshal response 656 consumo indevido")
        void deveUnmarshalResponseConsumoIndevido() throws Exception {
            String xml = "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\"><tpAmb>1</tpAmb><verAplic>1.0</verAplic><cStat>656</cStat><xMotivo>Consumo indevido</xMotivo><dhResp>2026-01-01T12:00:00-03:00</dhResp><ultNSU>0</ultNSU><maxNSU>0</maxNSU></retDistDFeInt>";
            Unmarshaller u = ctx(RetDistDfeInt.class).createUnmarshaller();
            RetDistDfeInt resp = (RetDistDfeInt) u.unmarshal(new StringReader(xml));
            assertThat(resp.getCStat()).isEqualTo("656");
            assertThat(resp.getxMotivo()).isEqualTo("Consumo indevido");
        }
    }
}
