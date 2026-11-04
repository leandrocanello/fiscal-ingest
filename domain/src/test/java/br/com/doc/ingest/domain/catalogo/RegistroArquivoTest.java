package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RegistroArquivoTest {

    private static final ChaveAcesso CHAVE = new ChaveAcesso("35261011111111000191550010000001231000001230");
    private static final BlobRef BLOB = new BlobRef("doc/1/55/2026/10/chave/completo.xml.gz", "a".repeat(64));
    private static final Instant RECEBIDO_EM = Instant.parse("2026-11-02T12:00:00Z");
    private static final MetadadosDocumento METADADOS = new MetadadosDocumento(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());

    private static ProvenienciaInformada proveniencia(
            OrigemArquivo origem, Confianca confianca, Optional<Long> ledgerNsuId, Optional<Long> recebimentoId) {
        return new ProvenienciaInformada(origem, confianca, ledgerNsuId, recebimentoId, RECEBIDO_EM);
    }

    private static ProvenienciaInformada oficial() {
        return proveniencia(OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.of(7L), Optional.empty());
    }

    private static RegistroArquivo registro(
            TipoArquivo tipo, Optional<String> eventoTipo, Optional<Integer> eventoSeq, List<VinculoEmpresa> vinculos) {
        return new RegistroArquivo(
                1L, CHAVE, METADADOS, SituacaoDocumento.AUTORIZADA, tipo, eventoTipo, eventoSeq, BLOB, vinculos, oficial());
    }

    @ParameterizedTest
    @EnumSource(value = OrigemArquivo.class, names = {"AGENTE", "EMAIL", "UPLOAD"})
    void pushNaoPodeSeDeclararOficial(OrigemArquivo origem) {
        assertThatThrownBy(() -> proveniencia(origem, Confianca.OFICIAL, Optional.empty(), Optional.of(9L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Confianca.class, names = {"CONFIRMADO_SEFAZ", "VALIDADO_ASSINATURA", "DECLARADO"})
    void pushAceitaAsConfiancasDePush(Confianca confianca) {
        assertThatCode(() -> proveniencia(OrigemArquivo.UPLOAD, confianca, Optional.empty(), Optional.of(9L)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = Confianca.class, names = {"CONFIRMADO_SEFAZ", "VALIDADO_ASSINATURA", "DECLARADO"})
    void dfeAnExigeOficialELedgerNsu(Confianca confianca) {
        assertThatThrownBy(() -> proveniencia(OrigemArquivo.DFE_AN, confianca, Optional.of(7L), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proveniencia(OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(RegistroArquivoTest::oficial).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = OrigemArquivo.class, names = {"AGENTE", "EMAIL", "UPLOAD"})
    void pushExigeRecebimento(OrigemArquivo origem) {
        assertThatThrownBy(() -> proveniencia(origem, Confianca.DECLARADO, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoPermiteLedgerERecebimentoJuntos() {
        assertThatThrownBy(() -> proveniencia(OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.of(7L), Optional.of(9L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proveniencia(OrigemArquivo.UPLOAD, Confianca.DECLARADO, Optional.of(7L), Optional.of(9L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TipoArquivo.class, names = {"EVENTO", "RESUMO_EVENTO"})
    void eventoExigeTipoESeq(TipoArquivo tipo) {
        assertThatThrownBy(() -> registro(tipo, Optional.empty(), Optional.of(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro(tipo, Optional.of("110111"), Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> registro(tipo, Optional.of("110111"), Optional.of(1), List.of()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = TipoArquivo.class, names = {"RESUMO", "COMPLETO"})
    void naoEventoRecusaTipoESeq(TipoArquivo tipo) {
        assertThatThrownBy(() -> registro(tipo, Optional.of("110111"), Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro(tipo, Optional.empty(), Optional.of(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> registro(tipo, Optional.empty(), Optional.empty(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void aceitaVinculosVazios() {
        // caso SEM_EMPRESA da §7.3: o documento fica guardado, sem vínculo
        assertThat(registro(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty(), List.of()).vinculos()).isEmpty();
    }

    @Test
    void copiaListaDeVinculos() {
        VinculoEmpresa saida = new VinculoEmpresa(10L, Direcao.SAIDA);
        List<VinculoEmpresa> vinculos = new ArrayList<>(List.of(saida));

        RegistroArquivo registro = registro(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty(), vinculos);
        vinculos.add(new VinculoEmpresa(11L, Direcao.ENTRADA));

        assertThat(registro.vinculos()).containsExactly(saida);
        assertThatThrownBy(() -> registro.vinculos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
