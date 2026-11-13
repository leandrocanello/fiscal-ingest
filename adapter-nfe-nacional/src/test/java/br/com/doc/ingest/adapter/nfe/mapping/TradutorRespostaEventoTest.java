package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.Invalida;
import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.RetornoEvento;
import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.Valida;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte.FalhaTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte.Motivo;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte.RespostaHttp;
import br.com.doc.ingest.domain.adapter.Desfecho;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tradução de transporte/HTTP/leitura do RecepcaoEvento para Desfecho")
class TradutorRespostaEventoTest {

    private static final Desfecho TEMPORARIO = new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5));
    private static final byte[] CORPO_SECRETO = "<x>SEGREDO-XYZ</x>".getBytes(StandardCharsets.UTF_8);
    private static final String CHAVE = "35200114200166000187550010000000046271239906";
    private static final String OUTRA_CHAVE = "35200114200166000187550010000000056271239907";
    private static final TradutorRespostaEvento.EventoEnviado ENVIADO =
            new TradutorRespostaEvento.EventoEnviado(CHAVE, "210210", 1);

    private final TradutorRespostaEvento tradutor = new TradutorRespostaEvento(new CStatEventoMapper());

    private static Valida lote128(String cStatEvento, String chave, String tpEvento, String nSeq) {
        return new Valida("128", Optional.of(new RetornoEvento(cStatEvento, Optional.empty(),
                Optional.ofNullable(chave), Optional.ofNullable(tpEvento), Optional.ofNullable(nSeq))));
    }

    @ParameterizedTest
    @EnumSource(Motivo.class)
    @DisplayName("falha de transporte (timeout, conexão, resposta grande demais) → IndisponivelTemporario")
    void falhaDeTransporteETemporario(Motivo motivo) {
        assertThat(tradutor.deTransporte(new FalhaTransporte(motivo))).contains(TEMPORARIO);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    @DisplayName("HTTP 401/403 → ErroAutenticacao, sem o corpo no motivo")
    void http401e403SaoErroAutenticacao(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO))).get()
                .isInstanceOf(Desfecho.ErroAutenticacao.class)
                .asString().doesNotContain("SEGREDO");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 405, 415})
    @DisplayName("outro HTTP 4xx → IndisponivelDefinitivo, sem o corpo no motivo")
    void outro4xxEhDefinitivo(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO))).get()
                .isInstanceOf(Desfecho.IndisponivelDefinitivo.class)
                .asString().doesNotContain("SEGREDO");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408, 429, 302})
    @DisplayName("HTTP 5xx, 408, 429 e 3xx → IndisponivelTemporario")
    void http5xxEhTemporario(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO))).contains(TEMPORARIO);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    @DisplayName("HTTP 2xx não decide: o corpo segue para a leitura")
    void http2xxSegueParaLeitura(int status) {
        assertThat(tradutor.deTransporte(new RespostaHttp(status, CORPO_SECRETO))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"135", "136", "573"})
    @DisplayName("lote 128 com retEvento de sucesso da mesma chave/tipo/seq → Ok")
    void lote128UsaCStatDoEvento(String cStat) {
        assertThat(tradutor.deLeitura(lote128(cStat, CHAVE, "210210", "1"), ENVIADO)).isEqualTo(new Desfecho.Ok());
    }

    @Test
    @DisplayName("lote 128 com rejeição do evento → desfecho do cStat do evento, mesmo sem chave/tipo/seq na resposta")
    void lote128ComRejeicaoDoEventoSemIdentificacao() {
        assertThat(tradutor.deLeitura(lote128("650", null, null, null), ENVIADO))
                .isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
    }

    @Test
    @DisplayName("lote 128 sem retEvento → IndisponivelTemporario (não há o que confirmar)")
    void lote128SemRetEventoEhTemporario() {
        assertThat(tradutor.deLeitura(new Valida("128", Optional.empty()), ENVIADO)).isEqualTo(TEMPORARIO);
    }

    @Test
    @DisplayName("lote diferente de 128 usa o cStat do lote, mesmo com retEvento de sucesso")
    void loteDiferenteDe128UsaCStatDoLote() {
        Optional<RetornoEvento> sucesso = lote128("135", CHAVE, "210210", "1").retornoEvento();

        assertThat(tradutor.deLeitura(new Valida("108", sucesso), ENVIADO)).isEqualTo(TEMPORARIO);
        assertThat(tradutor.deLeitura(new Valida("109", sucesso), ENVIADO)).isInstanceOf(Desfecho.ServicoFora.class);
        assertThat(tradutor.deLeitura(new Valida("656", sucesso), ENVIADO)).isInstanceOf(Desfecho.LimiteAtingido.class);
        assertThat(tradutor.deLeitura(new Valida("215", sucesso), ENVIADO))
                .isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
            // sucesso de outro evento não confirma o nosso
            "135, " + OUTRA_CHAVE + ", 210210, 1",
            "135, " + CHAVE + ", 210220, 1",
            "135, " + CHAVE + ", 210210, 2",
            // sucesso sem identificação também não
            "135, -, 210210, 1",
            "135, " + CHAVE + ", -, 1",
            "135, " + CHAVE + ", 210210, -",
            "573, -, -, -",
            // rejeição de outro evento não diz nada sobre o nosso
            "650, " + OUTRA_CHAVE + ", 210210, 1",
    })
    @DisplayName("retEvento de outra chave/tipo/seq, ou sucesso sem identificação → IndisponivelTemporario, nunca Ok")
    void retEventoDeOutraChaveOuTipoEhTemporario(String cStat, String chave, String tpEvento, String nSeq) {
        assertThat(tradutor.deLeitura(lote128(cStat, chave, tpEvento, nSeq), ENVIADO)).isEqualTo(TEMPORARIO);
    }

    @Test
    @DisplayName("leitura inválida (malformado, SOAP Fault, DOCTYPE) → IndisponivelTemporario")
    void leituraInvalidaEhTemporario() {
        assertThat(tradutor.deLeitura(new Invalida("SOAP Fault"), ENVIADO)).isEqualTo(TEMPORARIO);
    }
}
