package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.parser.DfeAnalisado;
import br.com.doc.ingest.domain.parser.ResultadoParse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParserDfe da NF-e")
class ParserDfeNfeTest {

    /** Chave sintética: UF 35, AAMM 2610, CNPJ 11111111000191, mod 55, série 001, nNF 123. */
    static final String CHAVE = "35261011111111000191550010000001231123456780";
    static final String EMITENTE = "11111111000191";
    static final String AUTOR = "22222222000122";
    static final String CPF = "98765432100";

    private final ParserDfeNfe parser = new ParserDfeNfe();

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static String resNFe(String cSitNFe) {
        return "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<chNFe>" + CHAVE + "</chNFe><CNPJ>" + EMITENTE + "</CNPJ><xNome>Emitente</xNome>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi><tpNF>1</tpNF><vNF>1234.56</vNF>"
                + "<cSitNFe>" + cSitNFe + "</cSitNFe></resNFe>";
    }

    private static String procNFe(String destinatario, String cStat) {
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><NFe><infNFe Id=\"NFe" + CHAVE
                + "\" versao=\"4.00\">"
                + "<ide><cUF>35</cUF><mod>55</mod><serie>1</serie><nNF>123</nNF>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi></ide>"
                + "<emit><CNPJ>" + EMITENTE + "</CNPJ><xNome>Emitente</xNome></emit>"
                + "<dest>" + destinatario + "<xNome>Destinatario</xNome></dest>"
                + "<total><ICMSTot><vNF>1234.56</vNF></ICMSTot></total>"
                + "<transp><transporta><CNPJ>33333333000133</CNPJ></transporta></transp>"
                + "<autXML><CNPJ>44444444000144</CNPJ></autXML>"
                + "</infNFe></NFe><protNFe versao=\"4.00\"><infProt><chNFe>" + CHAVE + "</chNFe>"
                + "<cStat>" + cStat + "</cStat></infProt></protNFe></nfeProc>";
    }

    private static String procEvento(String tpEvento, String nSeq) {
        return "<procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\"><evento versao=\"1.00\">"
                + "<infEvento Id=\"ID" + tpEvento + CHAVE + "01\"><cOrgao>91</cOrgao><CNPJ>" + AUTOR + "</CNPJ>"
                + "<chNFe>" + CHAVE + "</chNFe><dhEvento>2026-10-21T09:00:00-03:00</dhEvento>"
                + (tpEvento.isEmpty() ? "" : "<tpEvento>" + tpEvento + "</tpEvento>")
                + (nSeq.isEmpty() ? "" : "<nSeqEvento>" + nSeq + "</nSeqEvento>")
                + "<verEvento>1.00</verEvento></infEvento></evento>"
                + "<retEvento versao=\"1.00\"><infEvento><cStat>135</cStat></infEvento></retEvento></procEventoNFe>";
    }

    private static String resEvento(String tpEvento) {
        return "<resEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<cOrgao>91</cOrgao><CNPJ>" + AUTOR + "</CNPJ><chNFe>" + CHAVE + "</chNFe>"
                + "<dhEvento>2026-10-21T09:00:00-03:00</dhEvento><tpEvento>" + tpEvento + "</tpEvento>"
                + "<nSeqEvento>1</nSeqEvento><xEvento>Evento</xEvento></resEvento>";
    }

    private DfeAnalisado analisado(String schema, String xml) {
        ResultadoParse r = parser.analisar(schema, bytes(xml));
        assertThat(r).isInstanceOf(ResultadoParse.Analisado.class);
        return ((ResultadoParse.Analisado) r).dfe();
    }

    private String motivoInvalido(String schema, String xml) {
        ResultadoParse r = parser.analisar(schema, bytes(xml));
        assertThat(r).isInstanceOf(ResultadoParse.Invalido.class);
        return ((ResultadoParse.Invalido) r).motivo();
    }

    @Test
    void resumoViraRegistroSoResumo() {
        DfeAnalisado d = analisado("resNFe_v1.01.xsd", resNFe("1"));

        assertThat(d.chave()).isEqualTo(new ChaveAcesso(CHAVE));
        assertThat(d.tipo()).isEqualTo(TipoArquivo.RESUMO);
        assertThat(d.situacao()).isEqualTo(SituacaoDocumento.SO_RESUMO);
        assertThat(d.eventoTipo()).isEmpty();
        assertThat(d.eventoSeq()).isEmpty();
        assertThat(d.metadados().emitenteCnpj()).contains(new Cnpj(EMITENTE));
        assertThat(d.metadados().emissao()).contains(OffsetDateTime.parse("2026-10-20T10:30:00-03:00"));
        assertThat(d.metadados().valorTotal()).contains(new BigDecimal("1234.56"));
        assertThat(d.metadados().versaoLayout()).isEmpty();
        assertThat(d.cnpjs().emitente()).contains(new Cnpj(EMITENTE));
        assertThat(d.cnpjs().destinatario()).isEmpty();
        assertThat(d.cnpjs().autorizados()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "150"})
    void completoAutorizadoViraAutorizada(String cStat) {
        DfeAnalisado d = analisado("procNFe_v4.00.xsd", procNFe("<CNPJ>22222222000122</CNPJ>", cStat));

        assertThat(d.tipo()).isEqualTo(TipoArquivo.COMPLETO);
        assertThat(d.situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        assertThat(d.metadados().serie()).contains(1);
        assertThat(d.metadados().numero()).contains(123L);
        assertThat(d.metadados().destinatarioDoc()).contains("22222222000122");
        assertThat(d.metadados().versaoLayout()).contains("4.00");
        assertThat(d.cnpjs().emitente()).contains(new Cnpj(EMITENTE));
        assertThat(d.cnpjs().destinatario()).contains("22222222000122");
        assertThat(d.cnpjs().transportador()).contains(new Cnpj("33333333000133"));
        assertThat(d.cnpjs().autorizados()).containsExactly(new Cnpj("44444444000144"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"110", "205", "301", "302", "303"})
    void completoDenegadoViraDenegada(String cStat) {
        assertThat(analisado("procNFe_v4.00.xsd", procNFe("<CNPJ>22222222000122</CNPJ>", cStat)).situacao())
                .isEqualTo(SituacaoDocumento.DENEGADA);
    }

    @Test
    void cStatDesconhecidoNoProtocoloFicaSoResumo() {
        assertThat(analisado("procNFe_v4.00.xsd", procNFe("<CNPJ>22222222000122</CNPJ>", "999")).situacao())
                .isEqualTo(SituacaoDocumento.SO_RESUMO);
    }

    @Test
    void resumoCanceladoViraCancelada() {
        assertThat(analisado("resNFe_v1.01.xsd", resNFe("3")).situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
    }

    @Test
    void resumoDenegadoViraDenegada() {
        assertThat(analisado("resNFe_v1.01.xsd", resNFe("2")).situacao()).isEqualTo(SituacaoDocumento.DENEGADA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"110111", "110112"})
    void cancelamentoViraCancelada(String tpEvento) {
        DfeAnalisado proc = analisado("procEventoNFe_v1.00.xsd", procEvento(tpEvento, "1"));
        DfeAnalisado res = analisado("resEvento_v1.01.xsd", resEvento(tpEvento));

        assertThat(proc.tipo()).isEqualTo(TipoArquivo.EVENTO);
        assertThat(res.tipo()).isEqualTo(TipoArquivo.RESUMO_EVENTO);
        assertThat(proc.situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(res.situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(proc.eventoTipo()).contains(tpEvento);
        assertThat(proc.eventoSeq()).contains(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"210200", "210210", "210220", "210240", "110110"})
    void outrosEventosNaoMudamSituacao(String tpEvento) {
        assertThat(analisado("procEventoNFe_v1.00.xsd", procEvento(tpEvento, "1")).situacao())
                .isEqualTo(SituacaoDocumento.SO_RESUMO);
        assertThat(analisado("resEvento_v1.01.xsd", resEvento(tpEvento)).situacao())
                .isEqualTo(SituacaoDocumento.SO_RESUMO);
    }

    @Test
    void eventoTrazSoCamposDaNota() {
        for (DfeAnalisado d : List.of(
                analisado("procEventoNFe_v1.00.xsd", procEvento("210210", "1")),
                analisado("resEvento_v1.01.xsd", resEvento("210210")))) {
            var m = d.metadados();
            assertThat(m.emitenteCnpj()).isNotEqualTo(java.util.Optional.of(new Cnpj(AUTOR)));
            assertThat(m.versaoLayout()).isEmpty();
            assertThat(m.emissao()).isEmpty();
            assertThat(m.serie()).isEmpty();
            assertThat(m.numero()).isEmpty();
            assertThat(m.destinatarioDoc()).isEmpty();
            assertThat(m.valorTotal()).isEmpty();
            assertThat(d.cnpjs().destinatario()).isEmpty();
            assertThat(d.cnpjs().transportador()).isEmpty();
            assertThat(d.cnpjs().autorizados()).isEmpty();
        }
    }

    @Test
    void emitenteDoEventoVemDaChave() {
        for (DfeAnalisado d : List.of(
                analisado("procEventoNFe_v1.00.xsd", procEvento("110111", "1")),
                analisado("resEvento_v1.01.xsd", resEvento("110111")))) {
            assertThat(d.metadados().emitenteCnpj()).contains(new Cnpj(EMITENTE));
            assertThat(d.cnpjs().emitente()).contains(new Cnpj(EMITENTE));
        }
    }

    @Test
    void eventoSemTipoOuSequenciaViraInvalido() {
        motivoInvalido("procEventoNFe_v1.00.xsd", procEvento("", "1"));
        motivoInvalido("procEventoNFe_v1.00.xsd", procEvento("110111", ""));
    }

    @Test
    void schemaDesconhecidoViraDesconhecido() {
        assertThat(parser.analisar("algoNovo_v9.99.xsd", bytes("<algoNovo/>")))
                .isInstanceOf(ResultadoParse.Desconhecido.class);
        assertThat(parser.analisar(null, bytes("<algoNovo/>"))).isInstanceOf(ResultadoParse.Desconhecido.class);
    }

    @Test
    void raizDiferenteDoSchemaViraInvalido() {
        motivoInvalido("procNFe_v4.00.xsd", resNFe("1"));
    }

    @Test
    void xmlMalformadoViraInvalido() {
        motivoInvalido("resNFe_v1.01.xsd", "<resNFe><chNFe>" + CHAVE);
    }

    @Test
    void doctypeViraInvalido() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE resNFe [<!ENTITY x \"y\">]>" + resNFe("1");
        motivoInvalido("resNFe_v1.01.xsd", xml);
    }

    @Test
    void entidadeExternaViraInvalido() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE resNFe [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + resNFe("1").replace("<xNome>Emitente</xNome>", "<xNome>&xxe;</xNome>");
        assertThat(motivoInvalido("resNFe_v1.01.xsd", xml)).doesNotContain("root");
    }

    @Test
    void cnpjForaDoFormatoViraInvalido() {
        motivoInvalido("procNFe_v4.00.xsd",
                procNFe("<CNPJ>22222222000122</CNPJ>", "100").replace("<CNPJ>" + EMITENTE + "</CNPJ>",
                        "<CNPJ>123</CNPJ>"));
        motivoInvalido("procNFe_v4.00.xsd",
                procNFe("<CNPJ>22222222000122</CNPJ>", "100").replace("44444444000144", "4444X"));
        motivoInvalido("resNFe_v1.01.xsd", resNFe("1").replace("<CNPJ>" + EMITENTE, "<CNPJ>1111"));
    }

    @Test
    void invalidoNaoEcoaConteudo() {
        String cnpjRuim = procNFe("<CPF>" + CPF + "</CPF>", "100")
                .replace("<CNPJ>" + EMITENTE + "</CNPJ>", "<CNPJ>9SEGREDO9</CNPJ>");
        String destRuim = procNFe("<CPF>" + CPF + "9</CPF>", "100");
        String chaveRuim = "<resNFe><chNFe>123SEGREDO</chNFe></resNFe>";

        for (var par : List.of(List.of("procNFe_v4.00.xsd", cnpjRuim), List.of("procNFe_v4.00.xsd", destRuim),
                List.of("resNFe_v1.01.xsd", chaveRuim))) {
            assertThat(motivoInvalido(par.get(0), par.get(1)))
                    .doesNotContain("SEGREDO").doesNotContain(CPF).doesNotContain(CHAVE);
        }
    }

    @Test
    void analisadoNaoMostraCpfNoToString() {
        DfeAnalisado d = analisado("procNFe_v4.00.xsd", procNFe("<CPF>" + CPF + "</CPF>", "100"));

        assertThat(d.toString()).doesNotContain(CPF).doesNotContain(CHAVE);
    }
}
