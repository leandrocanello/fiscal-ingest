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
 * - 108: servico em operacao (Ok - servico disponivel)
 * - 109: servico paralisado (ServicoFora)
 */
@DisplayName("CStatMapper (cStat -> Desfecho)")
class CStatMapperTest {

    private final CStatMapper mapper = new CStatMapper();

    @Nested
    @DisplayName("Casos de sucesso (Ok / Vazio)")
    class CasosSucesso {

        @Test
        @DisplayName("cStat 138 deve mapear para Ok (documento localizado)")
        void cStat138_DeveMapearParaOk() {
            Desfecho resultado = mapper.mapear("138");
            assertThat(resultado).isInstanceOf(Desfecho.Ok.class);
        }

        @Test
        @DisplayName("cStat 137 deve mapear para Vazio (nenhum documento)")
        void cStat137_DeveMapearParaVazio() {
            Desfecho resultado = mapper.mapear("137");
            assertThat(resultado).isInstanceOf(Desfecho.Vazio.class);
        }

        @Test
        @DisplayName("cStat 108 deve mapear para Ok (servico em operacao)")
        void cStat108_DeveMapearParaOk() {
            Desfecho resultado = mapper.mapear("108");
            assertThat(resultado).isInstanceOf(Desfecho.Ok.class);
        }
    }

    @Nested
    @DisplayName("Casos de limite (LimiteAtingido - NAO avancar ponteiro)")
    class CasosLimite {

        @Test
        @DisplayName("cStat 656 deve mapear para LimiteAtingido (consumo indevido)")
        void cStat656_DeveMapearParaLimiteAtingido() {
            Desfecho resultado = mapper.mapear("656");
            assertThat(resultado).isInstanceOf(Desfecho.LimiteAtingido.class);
            assertThat(((Desfecho.LimiteAtingido) resultado).retryApos())
                    .isNotNull()
                    .isGreaterThan(Duration.ZERO);
        }

        @Test
        @DisplayName("cStat 632 deve mapear para LimiteAtingido (excesso de consultas)")
        void cStat632_DeveMapearParaLimiteAtingido() {
            Desfecho resultado = mapper.mapear("632");
            assertThat(resultado).isInstanceOf(Desfecho.LimiteAtingido.class);
        }
    }

    @Nested
    @DisplayName("Casos de indisponibilidade")
    class CasosIndisponibilidade {

        @Test
        @DisplayName("cStat 109 deve mapear para ServicoFora (servico paralisado)")
        void cStat109_DeveMapearParaServicoFora() {
            Desfecho resultado = mapper.mapear("109");
            assertThat(resultado).isInstanceOf(Desfecho.ServicoFora.class);
        }

        @Test
        @DisplayName("cStat 108 ja testado como Ok acima (servico em operacao)")
        void cStat108_JaTestado() {
            // Apenas confirmacao - 108 = servico em operacao = Ok
            Desfecho resultado = mapper.mapear("108");
            assertThat(resultado).isInstanceOf(Desfecho.Ok.class);
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
