package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do CStatMapper - traduz cStat da SEFAZ para o vocabulario unico de Desfecho.
 *
 * Este e o coracao do adapter: e onde o dialeto da SEFAZ vira linguagem do core.
 * O core NUNCA ve cStat - so ve Desfecho.
 *
 * Mapeamentos (distDFe / consNSU):
 * - 138: documento localizado (Ok)
 * - 137: nenhum documento encontrado (Vazio)
 * - 656: consumo indevido (LimiteAtingido - NAO avancar ponteiro!)
 * - 632: excesso de consultas (LimiteAtingido)
 * - 108: servico paralisado momentaneamente (IndisponivelTemporario - MOC 7.0)
 * - 109: servico paralisado (ServicoFora)
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
            "109, ServicoFora"
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
    }
}
