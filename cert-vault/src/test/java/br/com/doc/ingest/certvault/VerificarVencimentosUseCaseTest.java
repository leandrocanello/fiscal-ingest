package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.cofre.AlertaVencimento;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.NotificadorVencimento;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.cofre.PoliticaAlertaVencimento;
import br.com.doc.ingest.domain.cofre.TipoAlerta;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.usecase.VerificarVencimentosUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificarVencimentosUseCase (DA-53)")
class VerificarVencimentosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");
    private static final Cnpj CNPJ = new Cnpj("12345678000199");

    private RepositorioCertificadosEmMemoria certificados;
    private RegistroAlertasVencimentoEmMemoria registro;
    private List<AlertaVencimento> notificados;
    private NotificadorVencimento notificador;
    private VerificarVencimentosUseCase useCase;

    @BeforeEach
    void preparar() {
        certificados = new RepositorioCertificadosEmMemoria();
        registro = new RegistroAlertasVencimentoEmMemoria();
        notificados = new ArrayList<>();
        notificador = notificados::add;
        montar(notificador);
    }

    private void montar(NotificadorVencimento n) {
        useCase = new VerificarVencimentosUseCase(certificados, PoliticaAlertaVencimento.padrao(), registro, n,
                new RelogioManual(AGORA));
    }

    private CertificadoGuardado guardar(long tenant, long titular, int seq, Instant de, Instant ate) {
        return certificados.registrarSeAusente(new NovoCertificado(tenant, titular, CNPJ,
                String.format("%064x", tenant * 1000 + titular * 100 + seq),
                new BlobRef("cofre/pfx-" + seq + ".env", "a".repeat(64)),
                new BlobRef("cofre/senha-" + seq + ".env", "b".repeat(64)),
                de, ate, AGORA.minus(Duration.ofDays(30)), "operador"));
    }

    @Test
    @DisplayName("rodar duas vezes no mesmo instante notifica uma vez")
    void alertaUmaVezPorMarco() {
        var c = guardar(1, 10, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(10)));
        useCase.executar();
        useCase.executar();
        assertThat(notificados).hasSize(1);
        assertThat(notificados.get(0).certificadoId()).isEqualTo(c.id());
        assertThat(notificados.get(0).tipo()).isEqualTo(new TipoAlerta.Marco(Duration.ofDays(15)));
    }

    @Test
    @DisplayName("certificado já substituído por um que vence depois não alerta")
    void alertaSoOVigente() {
        guardar(1, 10, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(5)));
        guardar(1, 10, 2, AGORA.minus(Duration.ofDays(1)), AGORA.plus(Duration.ofDays(300)));
        useCase.executar();
        assertThat(notificados).isEmpty();
    }

    @Test
    @DisplayName("renovação ainda não válida não cala o alerta do vigente")
    void alertaOVigenteMesmoComRenovacaoAindaNaoValida() {
        var vigente = guardar(1, 10, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(5)));
        guardar(1, 10, 2, AGORA.plus(Duration.ofDays(2)), AGORA.plus(Duration.ofDays(400)));
        useCase.executar();
        assertThat(notificados).hasSize(1);
        assertThat(notificados.get(0).certificadoId()).isEqualTo(vigente.id());
        assertThat(notificados.get(0).tipo()).isEqualTo(new TipoAlerta.Marco(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("vencido notifica uma vez como VENCIDO")
    void vencidoNotificaUmaVezComoVencido() {
        var c = guardar(1, 10, 1, AGORA.minus(Duration.ofDays(400)), AGORA.minus(Duration.ofDays(1)));
        useCase.executar();
        useCase.executar();
        assertThat(notificados).hasSize(1);
        assertThat(notificados.get(0).certificadoId()).isEqualTo(c.id());
        assertThat(notificados.get(0).tipo()).isEqualTo(new TipoAlerta.Vencido());
    }

    @Test
    @DisplayName("dois tenants com o mesmo CNPJ alertam cada um o seu")
    void doisTenantsDoMesmoCnpjAlertamCadaUmOSeu() {
        guardar(1, 10, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(5)));
        guardar(2, 20, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(5)));
        useCase.executar();
        assertThat(notificados).extracting(AlertaVencimento::tenantId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("falha no notificador não registra o marco e tenta de novo na próxima rodada")
    void falhaNoNotificadorNaoRegistraOMarco() {
        guardar(1, 10, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(5)));
        var tentativas = new AtomicInteger();
        montar(alerta -> {
            if (tentativas.incrementAndGet() == 1) {
                throw new IllegalStateException("canal fora");
            }
            notificados.add(alerta);
        });
        useCase.executar();
        assertThat(notificados).isEmpty();
        useCase.executar();
        assertThat(notificados).hasSize(1);
    }

    @Test
    @DisplayName("alerta não tem fingerprint nem referência de blob no toString")
    void alertaNaoTemSegredo() {
        var c = guardar(1, 10, 1, AGORA.minus(Duration.ofDays(300)), AGORA.plus(Duration.ofDays(5)));
        useCase.executar();
        String texto = notificados.get(0).toString();
        assertThat(texto).doesNotContain(c.fingerprint()).doesNotContain("cofre/").doesNotContain("a".repeat(64));
    }
}
