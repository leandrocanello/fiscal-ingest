package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.Invalida;
import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.RetornoEvento;
import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.Valida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Leitura segura do retEnvEvento")
class LeitorRetEnvEventoTest {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    private static final String CHAVE = "35261012345678000195550010000000011000000015";

    private static final String RET_EVENTO_135 = "<retEvento versao=\"1.00\"><infEvento Id=\"ID891\">"
            + "<tpAmb>2</tpAmb><verAplic>AN_1.0</verAplic><cOrgao>91</cOrgao><cStat>135</cStat>"
            + "<xMotivo>Evento registrado e vinculado a NF-e SEGREDO-XYZ</xMotivo>"
            + "<chNFe>" + CHAVE + "</chNFe><tpEvento>210210</tpEvento><xEvento>Ciencia da Operacao</xEvento>"
            + "<nSeqEvento>1</nSeqEvento><dhRegEvento>2026-11-04T08:00:00-03:00</dhRegEvento>"
            + "<nProt>891260000000001</nProt></infEvento></retEvento>";

    private static String retEnvEvento(String cStatLote, String retEventos) {
        return "<retEnvEvento versao=\"1.00\" xmlns=\"" + NFE_NS + "\"><idLote>1</idLote><tpAmb>2</tpAmb>"
                + "<verAplic>AN_1.0</verAplic><cOrgao>91</cOrgao><cStat>" + cStatLote + "</cStat>"
                + "<xMotivo>Lote processado SEGREDO-XYZ</xMotivo>" + retEventos + "</retEnvEvento>";
    }

    private static byte[] envelope(String corpoDoBody) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>"
                + "<nfeResultMsg xmlns=\"" + ContratoRecepcaoEvento.NS_WSDL + "\">" + corpoDoBody + "</nfeResultMsg>"
                + "</soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
    }

    private static RetornoEvento retornoDe(byte[] corpo) {
        var resultado = LeitorRetEnvEvento.ler(corpo);
        assertThat(resultado).isInstanceOf(Valida.class);
        assertThat(((Valida) resultado).retornoEvento()).isPresent();
        return ((Valida) resultado).retornoEvento().orElseThrow();
    }

    @Test
    @DisplayName("lote 128 com retEvento 135: o cStat do lote e o do evento vêm separados")
    void lote128ComRetEvento135() {
        var resultado = LeitorRetEnvEvento.ler(envelope(retEnvEvento("128", RET_EVENTO_135)));

        assertThat(resultado).isInstanceOf(Valida.class);
        assertThat(((Valida) resultado).cStatLote()).isEqualTo("128");
        assertThat(((Valida) resultado).retornoEvento()).map(RetornoEvento::cStat).contains("135");
    }

    @Test
    @DisplayName("extrai cStat, nProt, chave, tpEvento e nSeqEvento do infEvento")
    void extraiCStatNProtChaveTpEventoSeq() {
        RetornoEvento retorno = retornoDe(envelope(retEnvEvento("128", RET_EVENTO_135)));

        assertThat(retorno.cStat()).isEqualTo("135");
        assertThat(retorno.nProt()).contains("891260000000001");
        assertThat(retorno.chaveAcesso()).contains(CHAVE);
        assertThat(retorno.tpEvento()).contains("210210");
        assertThat(retorno.nSeqEvento()).contains("1");
    }

    @Test
    @DisplayName("evento rejeitado pode vir só com cStat: os demais campos ficam vazios [VALIDAR item 9]")
    void eventoRejeitadoSoComCStat() {
        String rejeitado = "<retEvento versao=\"1.00\"><infEvento><cStat>596</cStat>"
                + "<xMotivo>Rejeicao</xMotivo></infEvento></retEvento>";

        RetornoEvento retorno = retornoDe(envelope(retEnvEvento("128", rejeitado)));

        assertThat(retorno.cStat()).isEqualTo("596");
        assertThat(retorno.nProt()).isEmpty();
        assertThat(retorno.chaveAcesso()).isEmpty();
        assertThat(retorno.tpEvento()).isEmpty();
        assertThat(retorno.nSeqEvento()).isEmpty();
    }

    @Test
    @DisplayName("lote rejeitado sem retEvento é válido e não tem retorno de evento")
    void loteRejeitadoSemRetEvento() {
        var resultado = LeitorRetEnvEvento.ler(envelope(retEnvEvento("656", "")));

        assertThat(resultado).isInstanceOf(Valida.class);
        assertThat(((Valida) resultado).cStatLote()).isEqualTo("656");
        assertThat(((Valida) resultado).retornoEvento()).isEmpty();
    }

    @Test
    @DisplayName("retEnvEvento é achado sem depender do wrapper [VALIDAR item 9]")
    void retEnvEventoSemWrapper() {
        byte[] direto = ("<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>"
                + retEnvEvento("128", RET_EVENTO_135) + "</soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);

        assertThat(retornoDe(direto).cStat()).isEqualTo("135");
    }

    @Test
    @DisplayName("malformado, vazio, não SOAP ou com DOCTYPE é inválido, sem expandir entidade")
    void malformadoOuDoctypeEhInvalida(@TempDir Path dir) throws Exception {
        Path arquivo = dir.resolve("segredo.txt");
        Files.writeString(arquivo, "SEGREDO-XYZ");
        String doctype = "<?xml version=\"1.0\"?><!DOCTYPE e [<!ENTITY x SYSTEM \"" + arquivo.toUri() + "\">]>"
                + "<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>"
                + retEnvEvento("&x;", RET_EVENTO_135) + "</soap:Body></soap:Envelope>";
        byte[][] corpos = {
                "<html>Service Unavailable".getBytes(StandardCharsets.UTF_8),
                new byte[0],
                retEnvEvento("128", RET_EVENTO_135).getBytes(StandardCharsets.UTF_8),
                ("<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"/>").getBytes(StandardCharsets.UTF_8),
                doctype.getBytes(StandardCharsets.UTF_8),
        };

        for (byte[] corpo : corpos) {
            var resultado = LeitorRetEnvEvento.ler(corpo);

            assertThat(resultado).isInstanceOf(Invalida.class);
            assertThat(((Invalida) resultado).motivo()).isNotBlank().hasSizeLessThanOrEqualTo(80)
                    .doesNotContain("SEGREDO-XYZ");
        }
    }

    @Test
    @DisplayName("soap12:Fault é inválido, mesmo que traga retEnvEvento no detalhe")
    void soapFaultEhInvalida() {
        String fault = "<soap:Fault xmlns:soap=\"" + SOAP12 + "\"><soap:Reason><soap:Text>erro</soap:Text></soap:Reason>"
                + "<soap:Detail>" + retEnvEvento("128", RET_EVENTO_135) + "</soap:Detail></soap:Fault>";

        var resultado = LeitorRetEnvEvento.ler(envelope(fault));

        assertThat(resultado).isInstanceOf(Invalida.class);
        assertThat(((Invalida) resultado).motivo()).containsIgnoringCase("fault");
    }

    @Test
    @DisplayName("envelope sem retEnvEvento é inválido")
    void semRetEnvEventoEhInvalida() {
        var resultado = LeitorRetEnvEvento.ler(envelope("<outra xmlns=\"" + NFE_NS + "\"/>"));

        assertThat(resultado).isInstanceOf(Invalida.class);
        assertThat(((Invalida) resultado).motivo()).contains("retEnvEvento");
    }

    @Test
    @DisplayName("lote sem cStat próprio é inválido: não herda o cStat do evento")
    void loteSemCStatProprioEhInvalida() {
        String semCStat = "<retEnvEvento versao=\"1.00\" xmlns=\"" + NFE_NS + "\"><idLote>1</idLote>"
                + RET_EVENTO_135 + "</retEnvEvento>";

        assertThat(LeitorRetEnvEvento.ler(envelope(semCStat))).isInstanceOf(Invalida.class);
    }

    @Test
    @DisplayName("retEvento sem infEvento ou sem cStat é inválido: não herda o cStat do lote")
    void retEventoSemCStatEhInvalida() {
        String semInf = "<retEvento versao=\"1.00\"/>";
        String semCStat = "<retEvento versao=\"1.00\"><infEvento><chNFe>" + CHAVE + "</chNFe></infEvento></retEvento>";

        assertThat(LeitorRetEnvEvento.ler(envelope(retEnvEvento("128", semInf)))).isInstanceOf(Invalida.class);
        assertThat(LeitorRetEnvEvento.ler(envelope(retEnvEvento("128", semCStat)))).isInstanceOf(Invalida.class);
    }

    @Test
    @DisplayName("mais de um retEvento é inválido: enviamos um evento por lote (D2)")
    void maisDeUmRetEventoEhInvalida() {
        var resultado = LeitorRetEnvEvento.ler(envelope(retEnvEvento("128", RET_EVENTO_135 + RET_EVENTO_135)));

        assertThat(resultado).isInstanceOf(Invalida.class);
    }

    @Test
    @DisplayName("toString e motivos não trazem conteúdo da resposta (inv. 13)")
    void toStringSemConteudo() {
        var valida = LeitorRetEnvEvento.ler(envelope(retEnvEvento("128", RET_EVENTO_135)));
        RetornoEvento retorno = ((Valida) valida).retornoEvento().orElseThrow();

        assertThat(valida.toString()).doesNotContain("SEGREDO-XYZ", CHAVE, "891260000000001");
        assertThat(retorno.toString()).doesNotContain("SEGREDO-XYZ", CHAVE, "891260000000001");

        byte[][] invalidos = {
                "SEGREDO-XYZ não é xml".getBytes(StandardCharsets.UTF_8),
                envelope("<retEnvEvento xmlns=\"" + NFE_NS + "\"><cStat>SEGREDO-XYZ</retEnvEvento>"),
                envelope("<soap:Fault xmlns:soap=\"" + SOAP12 + "\"><soap:Reason><soap:Text>SEGREDO-XYZ</soap:Text></soap:Reason></soap:Fault>"),
                envelope("<SEGREDO-XYZ/>"),
        };
        for (byte[] corpo : invalidos) {
            var resultado = LeitorRetEnvEvento.ler(corpo);

            assertThat(resultado).isInstanceOf(Invalida.class);
            assertThat(resultado.toString()).doesNotContain("SEGREDO-XYZ");
        }
    }

    @Test
    @DisplayName("cStat fora do esperado não aparece no toString (o saneamento é do mapper)")
    void cStatArbitrarioForaDoToString() {
        var valida = LeitorRetEnvEvento.ler(envelope(retEnvEvento("SEGREDO-XYZ", RET_EVENTO_135.replace("135", "SEGREDO-XYZ"))));

        assertThat(valida).isInstanceOf(Valida.class);
        assertThat(valida.toString()).doesNotContain("SEGREDO-XYZ");
        assertThat(((Valida) valida).retornoEvento().orElseThrow().toString()).doesNotContain("SEGREDO-XYZ");
    }
}
