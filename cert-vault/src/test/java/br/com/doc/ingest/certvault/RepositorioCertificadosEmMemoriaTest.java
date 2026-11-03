package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RepositorioCertificadosEmMemoria (dublê da porta de metadados)")
class RepositorioCertificadosEmMemoriaTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FIM = Instant.parse("2027-01-01T00:00:00Z");

    private final RepositorioCertificadosEmMemoria repositorio = new RepositorioCertificadosEmMemoria();

    private static NovoCertificado novo(long tenant, long titular, char hexFingerprint) {
        String hex = String.valueOf(hexFingerprint).repeat(64);
        return new NovoCertificado(tenant, titular, new Cnpj("11222333000181"), hex,
                new BlobRef("cofre/" + tenant + "/pfx.env", hex), new BlobRef("cofre/" + tenant + "/senha.env", hex),
                INICIO, FIM, INICIO, "operador");
    }

    @Test
    @DisplayName("registrar de novo o mesmo (tenant, titular, fingerprint) devolve o registro atual")
    void reenvioDevolveORegistroAtual() {
        CertificadoGuardado primeiro = repositorio.registrarSeAusente(novo(1, 10, 'a'));
        CertificadoGuardado segundo = repositorio.registrarSeAusente(novo(1, 10, 'a'));
        CertificadoGuardado outroFingerprint = repositorio.registrarSeAusente(novo(1, 10, 'b'));
        CertificadoGuardado outroTenant = repositorio.registrarSeAusente(novo(2, 10, 'a'));

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(List.of(primeiro.id(), outroFingerprint.id(), outroTenant.id())).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("buscar só encontra o certificado no tenant dono")
    void buscarRespeitaOTenant() {
        CertificadoGuardado guardado = repositorio.registrarSeAusente(novo(1, 10, 'a'));

        assertThat(repositorio.buscar(1, guardado.id())).contains(guardado);
        assertThat(repositorio.buscar(2, guardado.id())).isEmpty();
        assertThat(repositorio.buscar(1, guardado.id() + 1)).isEmpty();
    }

    @Test
    @DisplayName("registros concorrentes do mesmo certificado recebem o mesmo id")
    void concorrenciaDevolveOMesmoId() throws Exception {
        List<Future<CertificadoGuardado>> futuros = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                futuros.add(executor.submit(() -> repositorio.registrarSeAusente(novo(1, 10, 'a'))));
            }
        }

        long id = futuros.get(0).get().id();
        for (Future<CertificadoGuardado> futuro : futuros) {
            assertThat(futuro.get().id()).isEqualTo(id);
        }
    }
}
