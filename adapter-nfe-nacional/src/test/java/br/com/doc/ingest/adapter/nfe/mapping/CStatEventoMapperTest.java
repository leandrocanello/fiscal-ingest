package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabela do retorno do NFeRecepcaoEvento (decisão D1 da E2-S13): o cStat do lote e o do evento são lidos em separado,
 * porque o mesmo código não tem o mesmo sentido nos dois níveis.
 */
@DisplayName("CStatEventoMapper (cStat do retEnvEvento/retEvento -> Desfecho)")
class CStatEventoMapperTest {

    private final CStatEventoMapper mapper = new CStatEventoMapper();

    private final Logger logger = (Logger) LoggerFactory.getLogger(CStatEventoMapper.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void anexarAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void removerAppender() {
        logger.detachAppender(appender);
    }

    private List<ILoggingEvent> warns() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @ParameterizedTest(name = "lote {0} -> {1}")
    @DisplayName("Lote: cada código de D1 vira o tipo decidido")
    @CsvSource({
            "108, IndisponivelTemporario",
            "109, ServicoFora",
            "656, LimiteAtingido",
            "215, IndisponivelDefinitivo",
            "225, IndisponivelDefinitivo",
            "243, IndisponivelDefinitivo",
            "252, IndisponivelDefinitivo"
    })
    void tabelaDoLote(String cStat, String tipoEsperado) {
        assertThat(mapper.mapearLote(cStat)).hasValueSatisfying(
                d -> assertThat(d.getClass().getSimpleName()).isEqualTo(tipoEsperado));
    }

    @Test
    @DisplayName("Lote 128 não decide nada: quem decide é o retEvento")
    void lote128NaoTemDesfechoProprio() {
        assertThat(mapper.mapearLote("128")).isEmpty();
    }

    @Test
    @DisplayName("Lote 656 bloqueia por exatamente 1h e 108 pede nova tentativa em 5 min")
    void duracoesDoLote() {
        assertThat(mapper.mapearLote("656")).contains(new Desfecho.LimiteAtingido(Duration.ofHours(1)));
        assertThat(mapper.mapearLote("108")).contains(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
    }

    @ParameterizedTest(name = "lote {0} não é Ok")
    @DisplayName("Códigos de sucesso do evento não valem no lote")
    @ValueSource(strings = {"135", "136", "573"})
    void sucessoDoEventoNaoValeNoLote(String cStat) {
        assertThat(mapper.mapearLote(cStat)).contains(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
    }

    @ParameterizedTest(name = "evento {0} -> {1}")
    @DisplayName("Evento: cada código de D1 vira o tipo decidido")
    @CsvSource({
            "135, Ok",
            "136, Ok",
            "573, Ok",
            "650, IndisponivelDefinitivo",
            "655, IndisponivelDefinitivo",
            "596, IndisponivelDefinitivo",
            "217, IndisponivelDefinitivo",
            "236, IndisponivelDefinitivo",
            "614, IndisponivelDefinitivo",
            "615, IndisponivelDefinitivo",
            "616, IndisponivelDefinitivo",
            "617, IndisponivelDefinitivo",
            "618, IndisponivelDefinitivo",
            "619, IndisponivelDefinitivo",
            "297, IndisponivelDefinitivo",
            "298, IndisponivelDefinitivo",
            "575, IndisponivelDefinitivo",
            "213, ErroAutenticacao"
    })
    void tabelaDoEvento(String cStat, String tipoEsperado) {
        assertThat(mapper.mapearEvento(cStat).getClass().getSimpleName()).isEqualTo(tipoEsperado);
    }

    @ParameterizedTest(name = "evento {0} não é Ok")
    @DisplayName("Códigos do lote não valem no evento: 128 sem evento de sucesso nunca é Ok")
    @ValueSource(strings = {"128", "138"})
    void codigoDoLoteNaoValeNoEvento(String cStat) {
        assertThat(mapper.mapearEvento(cStat)).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
    }

    @ParameterizedTest(name = "assinatura {0}")
    @DisplayName("Rejeição de assinatura é bug nosso: alerta com o código e não retenta")
    @ValueSource(strings = {"297", "298"})
    void assinaturaRejeitadaAlerta(String cStat) {
        assertThat(mapper.mapearEvento(cStat)).isInstanceOf(Desfecho.IndisponivelDefinitivo.class);

        assertThat(warns()).hasSize(1);
        assertThat(warns().get(0).getArgumentArray()).containsExactly(cStat);
    }

    @Test
    @DisplayName("Código da tabela sem alerta não gera WARN")
    void codigoConhecidoNaoAlerta() {
        mapper.mapearEvento("135");
        mapper.mapearEvento("650");
        mapper.mapearLote("128");
        mapper.mapearLote("215");

        assertThat(warns()).isEmpty();
    }

    @ParameterizedTest(name = "cStat [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "999"})
    @DisplayName("Desconhecido, nulo ou vazio: 5 min e WARN, no lote e no evento")
    void desconhecidoViraTemporario(String cStat) {
        Desfecho.IndisponivelTemporario esperado = new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5));

        assertThat(mapper.mapearEvento(cStat)).isEqualTo(esperado);
        assertThat(mapper.mapearLote(cStat)).contains(esperado);
        assertThat(warns()).hasSize(2);
    }

    @Test
    @DisplayName("Alerta do desconhecido só leva o código sanitizado (<= 16 alfanuméricos, inv. 13)")
    void desconhecidoViraTemporarioComAlertaSanitizado() {
        String lixo = "<x>" + "a".repeat(493) + "</x>";

        Desfecho resultado = mapper.mapearEvento(lixo);

        assertThat(resultado).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
        assertThat(warns()).hasSize(1);
        ILoggingEvent evento = warns().get(0);
        assertThat(evento.getArgumentArray()).hasSize(1);
        assertThat(String.valueOf(evento.getArgumentArray()[0])).hasSizeLessThanOrEqualTo(16).matches("[A-Za-z0-9]*");
        assertThat(evento.getFormattedMessage()).doesNotContain("<").doesNotContain("a".repeat(17));
    }
}
