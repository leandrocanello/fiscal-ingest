package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.model.FixturesFiscais;
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
        return procNFe(CHAVE, "11111111000191", destinatario, "33333333000133", "44444444000144", "55555555000155");
    }

    private static String procNFe(String chave, String emitente, String destinatario, String transportador,
                                  String autorizado1, String autorizado2) {
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><NFe><infNFe Id=\"NFe" + chave
                + "\" versao=\"4.00\">"
                + "<ide><cUF>35</cUF><mod>55</mod><serie>1</serie><nNF>123</nNF>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi></ide>"
                + "<emit><CNPJ>" + emitente + "</CNPJ><xNome>Emitente</xNome></emit>"
                + "<dest>" + destinatario + "<xNome>Destinatario</xNome></dest>"
                + "<total><ICMSTot><vNF>1234.56</vNF></ICMSTot></total>"
                + "<transp><transporta><CNPJ>" + transportador + "</CNPJ></transporta></transp>"
                + "<autXML><CNPJ>" + autorizado1 + "</CNPJ></autXML>"
                + "<autXML><CNPJ>" + autorizado2 + "</CNPJ></autXML>"
                + "</infNFe></NFe><protNFe versao=\"4.00\"><infProt><chNFe>" + chave + "</chNFe>"
                + "<cStat>100</cStat></infProt></protNFe></nfeProc>";
    }

    @Test
    @DisplayName("leVersaoDoLayoutDoInfNFe")
    void leVersaoDoLayoutDoInfNFe() {
        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE,
                bytes(procNFe("<CNPJ>22222222000122</CNPJ>").replace("infNFe Id=\"NFe" + CHAVE
                        + "\" versao=\"4.00\"", "infNFe Id=\"NFe" + CHAVE + "\" versao=\"4.01\"")));

        assertThat(m.versaoLayout()).contains("4.01");
    }

    @Test
    @DisplayName("leCStatDoProtocolo")
    void leCStatDoProtocolo() {
        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE,
                bytes(procNFe("<CNPJ>22222222000122</CNPJ>")));

        assertThat(m.cStatProtocolo()).contains("100");
    }

    @Test
    @DisplayName("resumoEEventoNaoTemVersaoNemCStat")
    void resumoEEventoNaoTemVersaoNemCStat() {
        String res = "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<chNFe>" + CHAVE + "</chNFe><CNPJ>11111111000191</CNPJ><cSitNFe>1</cSitNFe></resNFe>";
        String evento = "<procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\"><evento>"
                + "<infEvento Id=\"ID110111" + CHAVE + "01\"><CNPJ>11111111000191</CNPJ>"
                + "<chNFe>" + CHAVE + "</chNFe><tpEvento>110111</tpEvento><nSeqEvento>1</nSeqEvento>"
                + "</infEvento></evento><retEvento><infEvento><cStat>135</cStat>"
                + "</infEvento></retEvento></procEventoNFe>";

        MetadadosDfe resumo = ExtratorMetadados.extrair(TipoSchemaDfe.RES_NFE, bytes(res));
        MetadadosDfe proc = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_EVENTO_NFE, bytes(evento));

        for (MetadadosDfe m : java.util.List.of(resumo, proc)) {
            assertThat(m.versaoLayout()).isEmpty();
            assertThat(m.cStatProtocolo()).isEmpty();
        }
    }

    private static String resNFe(String chave, String emitente) {
        return "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<chNFe>" + chave + "</chNFe><CNPJ>" + emitente + "</CNPJ>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi><tpNF>1</tpNF><vNF>10.00</vNF><cSitNFe>1</cSitNFe></resNFe>";
    }

    private static String resEvento(String chave, String autor) {
        return "<resEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<cOrgao>91</cOrgao><CNPJ>" + autor + "</CNPJ><chNFe>" + chave + "</chNFe>"
                + "<dhEvento>2026-10-21T09:00:00-03:00</dhEvento><tpEvento>210210</tpEvento>"
                + "<nSeqEvento>1</nSeqEvento></resEvento>";
    }

    private static String procEvento(String chave, String autor) {
        return "<procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\"><evento>"
                + "<infEvento Id=\"ID210210" + chave + "01\"><cOrgao>91</cOrgao><CNPJ>" + autor + "</CNPJ>"
                + "<chNFe>" + chave + "</chNFe><tpEvento>210210</tpEvento><nSeqEvento>1</nSeqEvento>"
                + "</infEvento></evento></procEventoNFe>";
    }

    /** CNPJs alfanuméricos sintéticos (NT Conjunta 2025.001), com DV certo. */
    static final String EMIT_ALFA = FixturesFiscais.cnpj("12ABC34501DE");
    static final String DEST_ALFA = FixturesFiscais.cnpj("AB12CD34EF56");
    static final String TRANSP_ALFA = FixturesFiscais.cnpj("ZZ00YY11XX22");
    static final String AUT1_ALFA = FixturesFiscais.cnpj("A1B2C3D40001");
    static final String AUT2_ALFA = FixturesFiscais.cnpj("9K8J7H6G0001");
    static final String CHAVE_ALFA = FixturesFiscais.chave("352604" + EMIT_ALFA + "55001000000001100000001");

    @Test
    @DisplayName("leChaveAlfanumericaDeProcNFe")
    void leChaveAlfanumericaDeProcNFe() {
        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE, bytes(procNFe(CHAVE_ALFA,
                EMIT_ALFA, "<CNPJ>" + DEST_ALFA + "</CNPJ>", TRANSP_ALFA, AUT1_ALFA, AUT2_ALFA)));

        assertThat(m.chave()).isEqualTo(CHAVE_ALFA);
        assertThat(m.modelo()).isEqualTo("55");
    }

    @Test
    @DisplayName("leChaveAlfanumericaDeResNFeEEvento")
    void leChaveAlfanumericaDeResNFeEEvento() {
        for (var par : java.util.List.of(
                java.util.Map.entry(TipoSchemaDfe.RES_NFE, resNFe(CHAVE_ALFA, EMIT_ALFA)),
                java.util.Map.entry(TipoSchemaDfe.RES_EVENTO, resEvento(CHAVE_ALFA, DEST_ALFA)),
                java.util.Map.entry(TipoSchemaDfe.PROC_EVENTO_NFE, procEvento(CHAVE_ALFA, DEST_ALFA)))) {
            MetadadosDfe m = ExtratorMetadados.extrair(par.getKey(), bytes(par.getValue()));

            assertThat(m.chave()).isEqualTo(CHAVE_ALFA);
            assertThat(m.modelo()).isEqualTo("55");
        }
    }

    @Test
    @DisplayName("leCnpjsAlfanumericos: emitente, destinatário, autXML, transportador e autor do evento")
    void leCnpjsAlfanumericos() {
        MetadadosDfe nota = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE, bytes(procNFe(CHAVE_ALFA,
                EMIT_ALFA, "<CNPJ>" + DEST_ALFA + "</CNPJ>", TRANSP_ALFA, AUT1_ALFA, AUT2_ALFA)));
        MetadadosDfe resumo = ExtratorMetadados.extrair(TipoSchemaDfe.RES_NFE, bytes(resNFe(CHAVE_ALFA, EMIT_ALFA)));
        MetadadosDfe evento = ExtratorMetadados.extrair(TipoSchemaDfe.PROC_EVENTO_NFE,
                bytes(procEvento(CHAVE_ALFA, DEST_ALFA)));

        assertThat(nota.cnpjEmitente()).contains(EMIT_ALFA);
        assertThat(nota.documentoDestinatario()).contains(DEST_ALFA);
        assertThat(nota.cnpjsAutorizados()).containsExactly(AUT1_ALFA, AUT2_ALFA);
        assertThat(nota.cnpjTransportador()).contains(TRANSP_ALFA);
        assertThat(resumo.cnpjEmitente()).contains(EMIT_ALFA);
        assertThat(evento.cnpjAutorEvento()).contains(DEST_ALFA);
    }

    @Test
    @DisplayName("chaveComDvErradoAindaEhExtraida (DA-38: na entrada não se recusa documento pelo DV)")
    void chaveComDvErradoAindaEhExtraida() {
        char dv = CHAVE_ALFA.charAt(43);
        String dvErrado = CHAVE_ALFA.substring(0, 43) + (dv == '9' ? '0' : (char) (dv + 1));

        MetadadosDfe m = ExtratorMetadados.extrair(TipoSchemaDfe.RES_NFE, bytes(resNFe(dvErrado, EMIT_ALFA)));

        assertThat(m.chave()).isEqualTo(dvErrado);
    }

    @Test
    @DisplayName("chaveMinusculaContinuaInvalida")
    void chaveMinusculaContinuaInvalida() {
        String minuscula = CHAVE_ALFA.toLowerCase(java.util.Locale.ROOT);

        assertThatThrownBy(() -> ExtratorMetadados.extrair(TipoSchemaDfe.RES_NFE, bytes(resNFe(minuscula, EMIT_ALFA))))
                .isInstanceOf(DocZipInvalidoException.class)
                .hasMessageNotContaining(minuscula);
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

    @Test
    @DisplayName("schemaQueDivergeDaRaizLancaDocZipInvalido")
    void schemaQueDivergeDaRaizLancaDocZipInvalido() {
        String xml = "<resNFe><chNFe>" + CHAVE + "</chNFe></resNFe>";

        assertThatThrownBy(() -> ExtratorMetadados.extrair(TipoSchemaDfe.PROC_NFE, bytes(xml)))
                .isInstanceOf(DocZipInvalidoException.class);
    }

    @Test
    @DisplayName("schemaDesconhecidoEhClassificadoENaoDescartado")
    void schemaDesconhecidoEhClassificadoENaoDescartado() {
        byte[] xml = bytes("<algoNovo><x>1</x></algoNovo>");
        byte[] copia = xml.clone();

        var m = ExtratorMetadados.extrairSeConhecido(TipoSchemaDfe.DESCONHECIDO, xml);

        assertThat(m).isEmpty();
        assertThat(xml).isEqualTo(copia);
    }
}
