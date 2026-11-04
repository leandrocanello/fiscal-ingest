package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.model.Cnpj;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FusaoDocumentoTest {

    private static final MetadadosDocumento VAZIOS = new MetadadosDocumento(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());

    private static final MetadadosDocumento COMPLETOS = new MetadadosDocumento(
            Optional.of(1),
            Optional.of(123L),
            Optional.of(OffsetDateTime.parse("2026-10-20T10:00:00-03:00")),
            Optional.of(new Cnpj("11111111000191")),
            Optional.of("12345678909"),
            Optional.of(new BigDecimal("1234.56")),
            Optional.of("4.00"));

    private static final MetadadosDocumento DIVERGENTES = new MetadadosDocumento(
            Optional.of(9),
            Optional.of(999L),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(new BigDecimal("1.00")),
            Optional.empty());

    private static MetadadosDocumento soSerieENumero() {
        return new MetadadosDocumento(
                Optional.of(1), Optional.of(123L), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static MetadadosDocumento soValor(String valor) {
        return new MetadadosDocumento(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(new BigDecimal(valor)), Optional.empty());
    }

    @Test
    void primeiroRegistroDefineOEstado() {
        assertThat(FusaoDocumento.inicial(COMPLETOS, SituacaoDocumento.AUTORIZADA, Confianca.OFICIAL))
                .isEqualTo(new EstadoDocumento(COMPLETOS, SituacaoDocumento.AUTORIZADA, false));
        assertThat(FusaoDocumento.inicial(COMPLETOS, SituacaoDocumento.AUTORIZADA, Confianca.DECLARADO))
                .isEqualTo(new EstadoDocumento(COMPLETOS, SituacaoDocumento.AUTORIZADA, true));
    }

    @Test
    void confiavelMaisConfiavelPreencheSoOsVazios() {
        var atual = new EstadoDocumento(soSerieENumero(), SituacaoDocumento.SO_RESUMO, false);

        var fundido = FusaoDocumento.fundir(
                atual, DIVERGENTES, SituacaoDocumento.SO_RESUMO, Confianca.VALIDADO_ASSINATURA);

        assertThat(fundido.metadados().serie()).contains(1);
        assertThat(fundido.metadados().numero()).contains(123L);
        assertThat(fundido.metadados().valorTotal()).contains(new BigDecimal("1.00"));
        assertThat(fundido.metadados().emissao()).isEmpty();
        assertThat(fundido.soDeclarado()).isFalse();
    }

    @Test
    void situacaoNuncaRegride() {
        var cancelada = new EstadoDocumento(VAZIOS, SituacaoDocumento.CANCELADA, false);

        var fundido = FusaoDocumento.fundir(
                cancelada, COMPLETOS, SituacaoDocumento.AUTORIZADA, Confianca.OFICIAL);

        assertThat(fundido.situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(fundido.metadados()).isEqualTo(COMPLETOS);
    }

    @Test
    void situacaoAvancaEntreRegistrosDaMesmaClasse() {
        var autorizada = new EstadoDocumento(COMPLETOS, SituacaoDocumento.AUTORIZADA, false);

        var fundido = FusaoDocumento.fundir(
                autorizada, VAZIOS, SituacaoDocumento.CANCELADA, Confianca.OFICIAL);

        assertThat(fundido.situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
    }

    @Test
    void declaradoNaoAlteraDocumentoConfiavel() {
        var confiavel = new EstadoDocumento(soSerieENumero(), SituacaoDocumento.AUTORIZADA, false);

        var fundido = FusaoDocumento.fundir(
                confiavel, COMPLETOS, SituacaoDocumento.CANCELADA, Confianca.DECLARADO);

        assertThat(fundido).isEqualTo(confiavel);
    }

    @Test
    void confiavelSubstituiTudoQueEraDeclarado() {
        var declarado = new EstadoDocumento(COMPLETOS, SituacaoDocumento.CANCELADA, true);

        var fundido = FusaoDocumento.fundir(
                declarado, soValor("10.00"), SituacaoDocumento.AUTORIZADA, Confianca.CONFIRMADO_SEFAZ);

        assertThat(fundido).isEqualTo(
                new EstadoDocumento(soValor("10.00"), SituacaoDocumento.AUTORIZADA, false));
        assertThat(fundido.metadados().destinatarioDoc()).isEmpty();
    }

    @Test
    void declaradoMaisDeclaradoFunde() {
        var declarado = new EstadoDocumento(soSerieENumero(), SituacaoDocumento.AUTORIZADA, true);

        var fundido = FusaoDocumento.fundir(
                declarado, soValor("10.00"), SituacaoDocumento.CANCELADA, Confianca.DECLARADO);

        assertThat(fundido.metadados().serie()).contains(1);
        assertThat(fundido.metadados().valorTotal()).contains(new BigDecimal("10.00"));
        assertThat(fundido.situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(fundido.soDeclarado()).isTrue();
    }

    @Test
    void semMudancaDevolveOMesmoEstado() {
        var confiavel = new EstadoDocumento(COMPLETOS, SituacaoDocumento.AUTORIZADA, false);
        var declarado = new EstadoDocumento(COMPLETOS, SituacaoDocumento.AUTORIZADA, true);

        assertThat(FusaoDocumento.fundir(confiavel, COMPLETOS, SituacaoDocumento.AUTORIZADA, Confianca.OFICIAL))
                .isSameAs(confiavel);
        assertThat(FusaoDocumento.fundir(confiavel, DIVERGENTES, SituacaoDocumento.DENEGADA, Confianca.DECLARADO))
                .isSameAs(confiavel);
        assertThat(FusaoDocumento.fundir(declarado, VAZIOS, SituacaoDocumento.SO_RESUMO, Confianca.DECLARADO))
                .isSameAs(declarado);
    }
}
