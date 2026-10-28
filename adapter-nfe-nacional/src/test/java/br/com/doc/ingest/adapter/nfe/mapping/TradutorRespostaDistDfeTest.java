package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.adapter.nfe.soap.LeitorRespostaDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.LeitorRespostaDistDfe.Invalida;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte.FalhaTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte.Motivo;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte.RespostaHttp;
import br.com.doc.ingest.domain.adapter.Desfecho;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tradução de transporte/HTTP/leitura do distDFe para Desfecho (DA-14)")
class TradutorRespostaDistDfeTest {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    private static final Duration CINCO_MIN = Duration.ofMinutes(5);
    private static final byte[] CORPO_SECRETO = "<x>SEGREDO-XYZ</x>".getBytes(StandardCharsets.UTF_8);

    private final TradutorRespostaDistDfe tradutor = new TradutorRespostaDistDfe(new CStatMapper());

    private static byte[] resposta(String cStat, String xMotivo) {
        return ("<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>"
                + "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\"><tpAmb>1</tpAmb><verAplic>1</verAplic>"
                + "<cStat>" + cStat + "</cStat><xMotivo>" + xMotivo + "</xMotivo>"
                + "<ultNSU>000000000000000</ultNSU><maxNSU>000000000000000</maxNSU></retDistDFeInt>"
                + "</soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(Motivo.class)
    @DisplayName("falha de transporte (timeout, conexão, resposta grande demais) → IndisponivelTemporario")
    void falhaDeTransporteEhTemporaria(Motivo motivo) {
        assertThat(tradutor.deTransporte(new FalhaTransporte(motivo)))
                .contains(new Desfecho.IndisponivelTemporario(CINCO_MIN));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408, 429})
    @DisplayName("HTTP 5xx, 408 e 429 → IndisponivelTemporario")
    void http5xx408e429SaoTemporarios(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO)))
                .contains(new Desfecho.IndisponivelTemporario(CINCO_MIN));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    @DisplayName("HTTP 401/403 → ErroAutenticacao")
    void http401e403SaoErroDeAutenticacao(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO)))
                .get().isInstanceOf(Desfecho.ErroAutenticacao.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 405, 415})
    @DisplayName("outro HTTP 4xx → IndisponivelDefinitivo")
    void outro4xxEhDefinitivo(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO)))
                .get().isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    @DisplayName("HTTP 2xx não decide: o corpo segue para a leitura")
    void http2xxSegueParaLeitura(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO))).isEmpty();
    }

    @Test
    @DisplayName("HTTP 3xx (redirecionamento não seguido) → IndisponivelTemporario")
    void http3xxEhTemporario() {
        assertThat(tradutor.deTransporte(new RespostaHttp(302, CORPO_SECRETO)))
                .contains(new Desfecho.IndisponivelTemporario(CINCO_MIN));
    }

    @Test
    @DisplayName("leitura Invalida (malformado, Fault, DOCTYPE) → IndisponivelTemporario")
    void leituraInvalidaEhTemporaria() {
        assertThat(tradutor.deLeitura(new Invalida("SOAP Fault")))
                .isEqualTo(new Desfecho.IndisponivelTemporario(CINCO_MIN));
    }

    @Test
    @DisplayName("HTTP 200 com cStat delega ao CStatMapper (138, 137, 656, 108, 109, desconhecido)")
    void cStatDelegaAoMapper() {
        assertThat(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta("138", "ok")))).isEqualTo(new Desfecho.Ok());
        assertThat(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta("137", "nada")))).isEqualTo(new Desfecho.Vazio());
        assertThat(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta("656", "consumo"))))
                .isEqualTo(new Desfecho.LimiteAtingido(Duration.ofHours(1)));
        assertThat(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta("108", "paralisado"))))
                .isEqualTo(new Desfecho.IndisponivelTemporario(CINCO_MIN));
        assertThat(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta("109", "paralisado"))))
                .isInstanceOf(Desfecho.ServicoFora.class);
        assertThat(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta("999", "?"))))
                .isEqualTo(new Desfecho.IndisponivelTemporario(CINCO_MIN));
    }

    @Test
    @DisplayName("nenhum desfecho carrega XML, xMotivo ou corpo HTTP (inv. 4 e 13)")
    void desfechoNaoCarregaXmlNemCorpo() {
        var desfechos = new java.util.ArrayList<Desfecho>();
        for (int status : new int[] {400, 401, 403, 404, 500}) {
            tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO)).ifPresent(desfechos::add);
        }
        desfechos.add(tradutor.deLeitura(LeitorRespostaDistDfe.ler(CORPO_SECRETO)));
        for (String cStat : new String[] {"109", "589", "632", "999"}) {
            desfechos.add(tradutor.deLeitura(LeitorRespostaDistDfe.ler(resposta(cStat, "SEGREDO-XYZ"))));
        }

        assertThat(desfechos).hasSize(10).allSatisfy(d -> assertThat(d.toString())
                .doesNotContain("SEGREDO-XYZ").doesNotContain("<").doesNotContain("retDistDFeInt"));
    }
}
