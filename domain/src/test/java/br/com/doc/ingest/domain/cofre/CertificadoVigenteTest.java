package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CertificadoVigente.escolher (DA-53)")
class CertificadoVigenteTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");
    private static final Instant ENVIO = Instant.parse("2026-10-01T00:00:00Z");

    static CertificadoGuardado cert(long id, Instant de, Instant ate, Instant enviadoEm) {
        return new CertificadoGuardado(id, 1L, 2L, new Cnpj("12345678000199"), String.format("%064x", id),
                new BlobRef("cofre/1/pfx-" + id + ".env", "a".repeat(64)),
                new BlobRef("cofre/1/senha-" + id + ".env", "b".repeat(64)),
                de, ate, enviadoEm, "operador");
    }

    private static CertificadoGuardado cert(long id, Instant de, Instant ate) {
        return cert(id, de, ate, ENVIO);
    }

    @Test
    @DisplayName("sem certificado é Ausente")
    void semCertificadoEhAusente() {
        assertThat(CertificadoVigente.escolher(List.of(), AGORA)).isInstanceOf(SituacaoTitular.Ausente.class);
    }

    @Test
    @DisplayName("só vencidos devolve Vencido com o maior validoAte")
    void soVencidosDevolveVencidoComOMaiorValidoAte() {
        var a = cert(1, AGORA.minusSeconds(900), AGORA.minusSeconds(300));
        var b = cert(2, AGORA.minusSeconds(900), AGORA.minusSeconds(100));
        assertThat(CertificadoVigente.escolher(List.of(a, b), AGORA))
                .isEqualTo(new SituacaoTitular.Vencido(AGORA.minusSeconds(100)));
    }

    @Test
    @DisplayName("renovação ainda não válida devolve AindaNaoValido")
    void soFuturoNaoEhVigente() {
        var futuro = cert(1, AGORA.plusSeconds(60), AGORA.plusSeconds(9000));
        assertThat(CertificadoVigente.escolher(List.of(futuro), AGORA))
                .isEqualTo(new SituacaoTitular.AindaNaoValido(AGORA.plusSeconds(60)));
    }

    @Test
    @DisplayName("validoDe e validoAte são inclusivos")
    void limitesValidoDeEValidoAteSaoInclusivos() {
        var noDe = cert(1, AGORA, AGORA.plusSeconds(10));
        var noAte = cert(2, AGORA.minusSeconds(10), AGORA);
        assertThat(CertificadoVigente.escolher(List.of(noDe), AGORA)).isEqualTo(new SituacaoTitular.Vigente(noDe));
        assertThat(CertificadoVigente.escolher(List.of(noAte), AGORA)).isEqualTo(new SituacaoTitular.Vigente(noAte));
    }

    @Test
    @DisplayName("entre vários vigentes ganha o maior validoAte")
    void entreVariosVigentesGanhaOMaiorValidoAte() {
        var a = cert(1, AGORA.minusSeconds(10), AGORA.plusSeconds(100));
        var b = cert(2, AGORA.minusSeconds(10), AGORA.plusSeconds(500));
        var vencido = cert(3, AGORA.minusSeconds(900), AGORA.minusSeconds(1));
        assertThat(CertificadoVigente.escolher(List.of(a, vencido, b), AGORA)).isEqualTo(new SituacaoTitular.Vigente(b));
    }

    @Test
    @DisplayName("empate desempata por enviadoEm e depois por id")
    void empateDesempataPorEnviadoEmDepoisPorId() {
        var ate = AGORA.plusSeconds(100);
        var antigo = cert(5, AGORA.minusSeconds(10), ate, ENVIO);
        var recente = cert(1, AGORA.minusSeconds(10), ate, ENVIO.plusSeconds(1));
        assertThat(CertificadoVigente.escolher(List.of(antigo, recente), AGORA))
                .isEqualTo(new SituacaoTitular.Vigente(recente));
        var idMaior = cert(9, AGORA.minusSeconds(10), ate, ENVIO);
        assertThat(CertificadoVigente.escolher(List.of(antigo, idMaior), AGORA))
                .isEqualTo(new SituacaoTitular.Vigente(idMaior));
    }

    @Test
    @DisplayName("vencido com renovação futura é Vencido (coleta parada até a renovação valer)")
    void vencidoComRenovacaoFuturaEhVencido() {
        var vencido = cert(1, AGORA.minusSeconds(900), AGORA.minusSeconds(5));
        var futuro = cert(2, AGORA.plusSeconds(60), AGORA.plusSeconds(9000));
        assertThat(CertificadoVigente.escolher(List.of(vencido, futuro), AGORA))
                .isEqualTo(new SituacaoTitular.Vencido(AGORA.minusSeconds(5)));
    }
}
