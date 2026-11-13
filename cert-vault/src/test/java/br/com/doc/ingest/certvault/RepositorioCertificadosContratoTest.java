package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.cofre.RepositorioCertificados;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Contrato de RepositorioCertificados: consultas por titular e por vencimento")
abstract class RepositorioCertificadosContratoTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T00:00:00Z");

    protected abstract RepositorioCertificados novoRepositorio();

    private final RepositorioCertificados repositorio = novoRepositorio();

    private CertificadoGuardado guardar(long tenant, long titular, char hexFingerprint, Instant validoAte) {
        String hex = String.valueOf(hexFingerprint).repeat(64);
        return repositorio.registrarSeAusente(new NovoCertificado(tenant, titular, new Cnpj("11222333000181"), hex,
                new BlobRef("cofre/" + tenant + "/pfx.env", hex), new BlobRef("cofre/" + tenant + "/senha.env", hex),
                INICIO, validoAte, INICIO, "operador"));
    }

    @Test
    @DisplayName("doTitular devolve todos os certificados do titular no tenant")
    void doTitularDevolveTodosOsCertificadosDoTitularNoTenant() {
        CertificadoGuardado a = guardar(1, 10, 'a', Instant.parse("2027-01-01T00:00:00Z"));
        CertificadoGuardado b = guardar(1, 10, 'b', Instant.parse("2028-01-01T00:00:00Z"));

        assertThat(repositorio.doTitular(1, 10)).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("doTitular ignora outro tenant e outro titular")
    void doTitularIgnoraOutroTenantEOutroTitular() {
        CertificadoGuardado meu = guardar(1, 10, 'a', Instant.parse("2027-01-01T00:00:00Z"));
        guardar(2, 10, 'a', Instant.parse("2027-01-01T00:00:00Z"));
        guardar(1, 11, 'c', Instant.parse("2027-01-01T00:00:00Z"));

        assertThat(repositorio.doTitular(1, 10)).containsExactly(meu);
    }

    @Test
    @DisplayName("doTitular de titular sem certificado é vazio")
    void doTitularDeTitularSemCertificadoEhVazio() {
        guardar(1, 10, 'a', Instant.parse("2027-01-01T00:00:00Z"));

        assertThat(repositorio.doTitular(1, 99)).isEmpty();
    }

    @Test
    @DisplayName("vencendoAte devolve só os com validoAte até o limite (inclusive)")
    void vencendoAteDevolveSoOsComValidoAteAteOLimite() {
        Instant limite = Instant.parse("2027-01-01T00:00:00Z");
        CertificadoGuardado antes = guardar(1, 10, 'a', limite.minusSeconds(1));
        CertificadoGuardado noLimite = guardar(1, 11, 'b', limite);
        guardar(1, 12, 'c', limite.plusSeconds(1));

        List<CertificadoGuardado> resultado = repositorio.vencendoAte(limite);

        assertThat(resultado).containsExactlyInAnyOrder(antes, noLimite);
    }
}
