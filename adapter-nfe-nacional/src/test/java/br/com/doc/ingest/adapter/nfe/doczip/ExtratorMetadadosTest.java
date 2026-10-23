package br.com.doc.ingest.adapter.nfe.doczip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Extrator de metadados do DF-e")
class ExtratorMetadadosTest {

    /** Chave sintética: UF 35, AAMM 2610, CNPJ 11111111000191, mod 55, série 001, nNF 123. */
    static final String CHAVE = "35261011111111000191550010000001231123456780";

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("resNFeExtraiChaveEmitenteDataValorSituacao")
    void resNFeExtraiChaveEmitenteDataValorSituacao() {
        String xml = "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<chNFe>" + CHAVE + "</chNFe>"
                + "<CNPJ>11111111000191</CNPJ><xNome>Emitente Ficticio</xNome><IE>123</IE>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi><tpNF>1</tpNF><vNF>1234.56</vNF>"
                + "<digVal>abc</digVal><dhRecbto>2026-10-20T10:31:00-03:00</dhRecbto>"
                + "<nProt>135260000000001</nProt><cSitNFe>1</cSitNFe></resNFe>";

        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.RES_NFE, bytes(xml));

        assertThat(m.chave()).isEqualTo(CHAVE);
        assertThat(m.modelo()).isEqualTo("55");
        assertThat(m.cnpjEmitente()).contains("11111111000191");
        assertThat(m.emissao()).contains(OffsetDateTime.parse("2026-10-20T10:30:00-03:00"));
        assertThat(m.valorTotal()).contains(new BigDecimal("1234.56"));
        assertThat(m.situacao()).contains("1");
        assertThat(m.tipoOperacao()).contains("1");
        assertThat(m.documentoDestinatario()).isEmpty();
        assertThat(m.cnpjsAutorizados()).isEmpty();
        assertThat(m.tipoEvento()).isEmpty();
    }

    private static String procNFe(String destinatario) {
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><NFe><infNFe Id=\"NFe" + CHAVE
                + "\" versao=\"4.00\">"
                + "<ide><cUF>35</cUF><mod>55</mod><serie>1</serie><nNF>123</nNF>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi></ide>"
                + "<emit><CNPJ>11111111000191</CNPJ><xNome>Emitente</xNome></emit>"
                + "<dest>" + destinatario + "<xNome>Destinatario</xNome></dest>"
                + "<total><ICMSTot><vNF>1234.56</vNF></ICMSTot></total>"
                + "<transp><transporta><CNPJ>33333333000133</CNPJ></transporta></transp>"
                + "<autXML><CNPJ>44444444000144</CNPJ></autXML>"
                + "<autXML><CNPJ>55555555000155</CNPJ></autXML>"
                + "</infNFe></NFe><protNFe><infProt><chNFe>" + CHAVE + "</chNFe></infProt></protNFe></nfeProc>";
    }

    @Test
    @DisplayName("procNFeExtraiEmitenteDestinatarioAutXmlETransportador")
    void procNFeExtraiEmitenteDestinatarioAutXmlETransportador() {
        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE,
                bytes(procNFe("<CNPJ>22222222000122</CNPJ>")));

        assertThat(m.chave()).isEqualTo(CHAVE);
        assertThat(m.modelo()).isEqualTo("55");
        assertThat(m.serie()).contains(1);
        assertThat(m.numero()).contains(123L);
        assertThat(m.emissao()).contains(OffsetDateTime.parse("2026-10-20T10:30:00-03:00"));
        assertThat(m.cnpjEmitente()).contains("11111111000191");
        assertThat(m.documentoDestinatario()).contains("22222222000122");
        assertThat(m.cnpjsAutorizados()).containsExactly("44444444000144", "55555555000155");
        assertThat(m.cnpjTransportador()).contains("33333333000133");
        assertThat(m.valorTotal()).contains(new BigDecimal("1234.56"));
    }

    @Test
    @DisplayName("destinatário pessoa física usa dest/CPF")
    void destinatarioCpf() {
        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE,
                bytes(procNFe("<CPF>12345678901</CPF>")));

        assertThat(m.documentoDestinatario()).contains("12345678901");
    }

    @Test
    @DisplayName("toStringMascaraCpf")
    void toStringMascaraCpf() {
        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE,
                bytes(procNFe("<CPF>12345678901</CPF>")));

        assertThat(m.toString()).doesNotContain("12345678901").contains("8901");
    }

    @Test
    @DisplayName("chave que não tem 44 dígitos lança DocZipInvalido sem eco do valor")
    void chaveInvalida() {
        String xml = "<resNFe><chNFe>123SEGREDO</chNFe></resNFe>";

        assertThatThrownBy(() -> ExtratorMetadados.extrair(TipoSchemaDfe.RES_NFE, bytes(xml)))
                .isInstanceOf(DocZipInvalidoException.class)
                .hasMessageNotContaining("SEGREDO");
    }

    @Test
    @DisplayName("resEventoEProcEventoExtraemChaveTipoESequencia")
    void resEventoEProcEventoExtraemChaveTipoESequencia() {
        String res = "<resEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<cOrgao>91</cOrgao><CNPJ>22222222000122</CNPJ><chNFe>" + CHAVE + "</chNFe>"
                + "<dhEvento>2026-10-21T09:00:00-03:00</dhEvento><tpEvento>210210</tpEvento>"
                + "<nSeqEvento>1</nSeqEvento><xEvento>Ciencia da Operacao</xEvento></resEvento>";
        String proc = "<procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\"><evento>"
                + "<infEvento Id=\"ID210210" + CHAVE + "01\"><cOrgao>91</cOrgao><CNPJ>22222222000122</CNPJ>"
                + "<chNFe>" + CHAVE + "</chNFe><dhEvento>2026-10-21T09:00:00-03:00</dhEvento>"
                + "<tpEvento>210210</tpEvento><nSeqEvento>1</nSeqEvento></infEvento></evento>"
                + "<retEvento><infEvento><chNFe>" + CHAVE + "</chNFe><tpEvento>210210</tpEvento>"
                + "<nSeqEvento>1</nSeqEvento></infEvento></retEvento></procEventoNFe>";

        for (var par : java.util.List.of(
                java.util.Map.entry(TipoSchemaDfe.RES_EVENTO, res),
                java.util.Map.entry(TipoSchemaDfe.PROC_EVENTO_NFE, proc))) {
            MetadadosDfe m = ExtratorMetadados.extrair(par.getKey(), bytes(par.getValue()));

            assertThat(m.chave()).isEqualTo(CHAVE);
            assertThat(m.modelo()).isEqualTo("55");
            assertThat(m.tipoEvento()).contains("210210");
            assertThat(m.seqEvento()).contains(1);
            assertThat(m.cnpjAutorEvento()).contains("22222222000122");
            assertThat(m.cnpjEmitente()).isEmpty();
        }
    }
}
