package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Testes do CStatMapper - traduz cStat da SEFAZ para o vocabulario unico de Desfecho.
 *
 * Este e o coracao do adapter: e onde o dialeto da SEFAZ vira linguagem do core.
 * O core NUNCA ve cStat - so ve Desfecho.
 *
 * Mapeamentos (MOC 7.0, decisao E2-S02):
 * - 138: documento localizado (Ok)
 * - 137: nenhum documento encontrado (Vazio)
 * - 656: consumo indevido (LimiteAtingido de 1h - NAO avancar ponteiro!)
 * - 632: NF-e fora do prazo de distribuicao (IndisponivelDefinitivo - Tab. 5-29 H15)
 * - 589: NSU maior que o maxNSU (IndisponivelDefinitivo "cursor inconsistente" - Tab. 5-29 H06)
 * - 108: servico paralisado momentaneamente (IndisponivelTemporario - Tab. 5-20 K03)
 * - 109: servico paralisado sem previsao (ServicoFora - Tab. 5-20 K03)
 * - 217, 236, 614-619, 640, 641, 653, 654: rejeicoes do consChNFe (IndisponivelDefinitivo - Tab. 5-29,
 *   decisao do autor 2026-10-29, E2-S16)
 * - qualquer outro / nulo / vazio: fallback IndisponivelTemporario de 5 min + WARN sanitizado
 */
@DisplayName("CStatMapper (cStat -> Desfecho)")
class CStatMapperTest {

    private final CStatMapper mapper = new CStatMapper();

    @ParameterizedTest(name = "cStat {0} -> {1}")
    @DisplayName("Tabela MOC 7.0: cada cStat mapeado vira o tipo de Desfecho decidido (E2-S02)")
    @CsvSource({
            "138, Ok",
            "137, Vazio",
            "656, LimiteAtingido",
            "632, IndisponivelDefinitivo",
            "589, IndisponivelDefinitivo",
            "108, IndisponivelTemporario",
            "109, ServicoFora",
            "217, IndisponivelDefinitivo",
            "236, IndisponivelDefinitivo",
            "614, IndisponivelDefinitivo",
            "615, IndisponivelDefinitivo",
            "616, IndisponivelDefinitivo",
            "617, IndisponivelDefinitivo",
            "618, IndisponivelDefinitivo",
            "619, IndisponivelDefinitivo",
            "640, IndisponivelDefinitivo",
            "641, IndisponivelDefinitivo",
            "653, IndisponivelDefinitivo",
            "654, IndisponivelDefinitivo",
            "252, IndisponivelDefinitivo"
    })
    void tabelaDeCodigosMapeados(String cStat, String tipoEsperado) {
        assertThat(mapper.mapear(cStat).getClass().getSimpleName()).isEqualTo(tipoEsperado);
    }

    @Nested
    @DisplayName("Casos de limite (LimiteAtingido - NAO avancar ponteiro)")
    class CasosLimite {

        @Test
        @DisplayName("cStat 656 vira LimiteAtingido de exatamente 1h (consumo indevido)")
        void cStat656ViraLimiteAtingidoDeUmaHora() {
            Desfecho resultado = mapper.mapear("656");
            assertThat(resultado).isInstanceOf(Desfecho.LimiteAtingido.class);
            assertThat(((Desfecho.LimiteAtingido) resultado).retryApos()).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("cStat 632 vira IndisponivelDefinitivo 'fora de prazo' (MOC 7.0 Tab. 5-29 H15)")
        void cStat632ViraIndisponivelDefinitivoForaDePrazo() {
            Desfecho resultado = mapper.mapear("632");
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
            assertThat(((Desfecho.IndisponivelDefinitivo) resultado).motivo())
                    .containsIgnoringCase("fora de prazo");
        }

        @Test
        @DisplayName("cStat 632 nao bloqueia o CNPJ (nao e LimiteAtingido)")
        void cStat632NaoEhLimiteAtingido() {
            assertThat(mapper.mapear("632")).isNotInstanceOf(Desfecho.LimiteAtingido.class);
        }

        @Test
        @DisplayName("cStat 589 vira IndisponivelDefinitivo 'cursor inconsistente' (MOC 7.0 Tab. 5-29 H06)")
        void cStat589ViraIndisponivelDefinitivoCursorInconsistente() {
            Desfecho resultado = mapper.mapear("589");
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
            assertThat(((Desfecho.IndisponivelDefinitivo) resultado).motivo())
                    .containsIgnoringCase("cursor inconsistente");
        }
    }

    @Nested
    @DisplayName("Rejeicoes de configuracao (consChNFe)")
    class CasosConfiguracao {

        @Test
        @DisplayName("cStat 252 vira IndisponivelDefinitivo citando ambiente divergente (MOC 7.0 Tab. 5-29)")
        void cStat252ViraIndisponivelDefinitivoAmbienteDivergente() {
            Desfecho resultado = mapper.mapear("252");
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
            assertThat(((Desfecho.IndisponivelDefinitivo) resultado).motivo())
                    .containsIgnoringCase("ambiente");
        }

        @Test
        @DisplayName("cStat 593 vira ErroAutenticacao citando raiz do CNPJ diferente da do certificado")
        void cStat593ViraErroAutenticacaoRaizDivergente() {
            Desfecho resultado = mapper.mapear("593");
            assertThat(resultado).isInstanceOf(Desfecho.ErroAutenticacao.class);
            assertThat(((Desfecho.ErroAutenticacao) resultado).motivo())
                    .containsIgnoringCase("raiz do CNPJ consultado difere da do certificado");
        }
    }

    @Nested
    @DisplayName("Casos de indisponibilidade")
    class CasosIndisponibilidade {

        @Test
        @DisplayName("cStat 109 vira ServicoFora com motivo citando 'sem previsao'")
        void cStat109ViraServicoForaComMotivoSemPrevisao() {
            // MOC 7.0 Tab. 5-20 K03: 109 = "Servico Paralisado sem Previsao".
            Desfecho resultado = mapper.mapear("109");
            assertThat(resultado).isInstanceOf(Desfecho.ServicoFora.class);
            assertThat(((Desfecho.ServicoFora) resultado).motivo()).containsIgnoringCase("sem previsão");
        }

        @Test
        @DisplayName("cStat 108 (servico paralisado momentaneamente) nao e sucesso: vira IndisponivelTemporario")
        void cStat108NaoEhSucesso() {
            // MOC 7.0 Tab. 5-20 K03: 108 = "Servico Paralisado Momentaneamente (curto prazo)".
            // Tratar como Ok faria o loop avancar sem documento (achado A1 da auditoria).
            Desfecho resultado = mapper.mapear("108");
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelTemporario.class);
            assertThat(((Desfecho.IndisponivelTemporario) resultado).retryApos())
                    .isGreaterThan(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("Casos de erro / desconhecido")
    class CasosErro {

        @Test
        @DisplayName("cStat desconhecido deve mapear para IndisponivelTemporario")
        void cStatDesconhecido_DeveMapearParaIndisponivelTemporario() {
            Desfecho resultado = mapper.mapear("999");
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelTemporario.class);
        }

        @Test
        @DisplayName("cStat nulo deve mapear para IndisponivelTemporario")
        void cStatNulo_DeveMapearParaIndisponivelTemporario() {
            Desfecho resultado = mapper.mapear(null);
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelTemporario.class);
        }

        @Test
        @DisplayName("cStat vazio deve mapear para IndisponivelTemporario")
        void cStatVazio_DeveMapearParaIndisponivelTemporario() {
            Desfecho resultado = mapper.mapear("");
            assertThat(resultado).isInstanceOf(Desfecho.IndisponivelTemporario.class);
        }

        @Nested
        @DisplayName("Log de alerta do fallback (inv. 13: so o cStat bruto, sem XML)")
        class LogDoFallback {

            private final Logger logger = (Logger) LoggerFactory.getLogger(CStatMapper.class);
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

            @Test
            @DisplayName("cStat 999 cai no fallback de 5 min e emite 1 WARN com o codigo bruto e texto fixo")
            void fallbackLogaSoOCStatBruto() {
                Desfecho resultado = mapper.mapear("999");

                assertThat(resultado).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
                assertThat(warns()).hasSize(1);
                ILoggingEvent evento = warns().get(0);
                assertThat(evento.getFormattedMessage()).contains("999");
                // Unico dado variavel e o codigo; o resto da mensagem e o template fixo
                assertThat(evento.getArgumentArray()).containsExactly("999");
            }

            @ParameterizedTest(name = "cStat [{0}]")
            @NullAndEmptySource
            @ValueSource(strings = {"   "})
            @DisplayName("cStat nulo/vazio cai no fallback de 5 min e tambem emite WARN")
            void fallbackNuloOuVazioTambemLoga(String cStat) {
                Desfecho resultado = mapper.mapear(cStat);

                assertThat(resultado).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
                assertThat(warns()).hasSize(1);
            }

            @Test
            @DisplayName("cStat longo com XML e logado truncado (<= 16 chars) e sem '<'")
            void fallbackTruncaESanitizaEntradaLonga() {
                String lixo = ("<x>" + "a".repeat(493) + "</x>");
                assertThat(lixo).hasSize(500);

                Desfecho resultado = mapper.mapear(lixo);

                assertThat(resultado).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
                assertThat(warns()).hasSize(1);
                ILoggingEvent evento = warns().get(0);
                assertThat(evento.getArgumentArray()).hasSize(1);
                String logado = String.valueOf(evento.getArgumentArray()[0]);
                assertThat(logado).hasSizeLessThanOrEqualTo(16).doesNotContain("<");
                assertThat(evento.getFormattedMessage()).doesNotContain("<").doesNotContain("a".repeat(17));
            }
        }
    }
}
