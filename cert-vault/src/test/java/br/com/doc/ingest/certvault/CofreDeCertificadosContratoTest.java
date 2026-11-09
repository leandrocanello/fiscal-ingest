package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.CofreDeCertificados;
import br.com.doc.ingest.domain.cofre.PedidoGuarda;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de {@link CofreDeCertificados}: toda implementação (local hoje; Vault/KMS depois) estende esta
 * suíte e só fornece o cofre montado sobre a cadeia sintética do gerador.
 */
abstract class CofreDeCertificadosContratoTest {

    protected static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    protected static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    protected static final Instant AGORA = Instant.parse("2026-06-01T12:00:00Z");
    private static final String CNPJ = "11222333000181";
    private static final String OUTRO_CNPJ = "99888777000166";
    private static final char[] SENHA = "s3nh@-sintetica".toCharArray();
    private static final long TENANT = 7;
    private static final long TITULAR = 42;

    protected final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(DE, ATE);
    protected final RelogioManual relogio = new RelogioManual(AGORA);
    private CofreDeCertificados cofre;
    private CertificadoTitular titular;

    /** Cofre novo e vazio, que confia na raiz e na intermediária do {@link #gerador} e usa o {@link #relogio}. */
    protected abstract CofreDeCertificados novoCofre();

    @BeforeEach
    void montar() {
        cofre = novoCofre();
        titular = gerador.titular(CNPJ);
    }

    private PedidoGuarda pedido(long tenant, byte[] pfx, char[] senha, String cnpj) {
        return new PedidoGuarda(tenant, TITULAR, new Cnpj(cnpj), pfx, senha, "operador");
    }

    private CertificadoGuardado guardado(ResultadoGuarda resultado) {
        assertThat(resultado).isInstanceOf(ResultadoGuarda.Guardado.class);
        return ((ResultadoGuarda.Guardado) resultado).certificado();
    }

    @Test
    @DisplayName("guardar e abrir devolve o mesmo pfx e a mesma senha")
    void guardarEAbrirDevolveAMesmaCredencial() {
        byte[] pfx = gerador.pfxModerno(titular, SENHA);
        CertificadoGuardado c = guardado(cofre.guardar(pedido(TENANT, pfx, SENHA, CNPJ)));

        ResultadoAbertura aberto = cofre.abrir(TENANT, c.id());

        assertThat(aberto).isInstanceOf(ResultadoAbertura.Aberto.class);
        Credencial credencial = ((ResultadoAbertura.Aberto) aberto).credencial();
        assertThat(credencial.pfx()).isEqualTo(pfx);
        assertThat(credencial.senha()).isEqualTo(SENHA);
        assertThat(c.tenantId()).isEqualTo(TENANT);
        assertThat(c.titularEmpresaId()).isEqualTo(TITULAR);
        assertThat(c.cnpj()).isEqualTo(new Cnpj(CNPJ));
        assertThat(c.enviadoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("o pedido de quem chama continua intacto depois de guardar")
    void pedidoDoChamadorContinuaIntacto() {
        byte[] pfx = gerador.pfxModerno(titular, SENHA);
        PedidoGuarda pedido = pedido(TENANT, pfx, SENHA, CNPJ);

        cofre.guardar(pedido);

        assertThat(pedido.pfx()).isEqualTo(pfx);
        assertThat(pedido.senha()).isEqualTo(SENHA);
    }

    @Test
    @DisplayName("senha errada e CNPJ divergente são recusados")
    void recusasVoltamComoResultado() {
        byte[] pfx = gerador.pfxModerno(titular, SENHA);

        assertThat(cofre.guardar(pedido(TENANT, pfx, "errada".toCharArray(), CNPJ)))
                .isInstanceOf(ResultadoGuarda.Recusado.class);
        assertThat(cofre.guardar(pedido(TENANT, pfx, SENHA, OUTRO_CNPJ)))
                .isInstanceOf(ResultadoGuarda.Recusado.class);
    }

    @Test
    @DisplayName("reenvio do mesmo pfx devolve o mesmo id")
    void reenvioDevolveOMesmoId() {
        byte[] pfx = gerador.pfxModerno(titular, SENHA);

        long primeiro = guardado(cofre.guardar(pedido(TENANT, pfx, SENHA, CNPJ))).id();
        long segundo = guardado(cofre.guardar(pedido(TENANT, pfx, SENHA, CNPJ))).id();

        assertThat(segundo).isEqualTo(primeiro);
    }

    @Test
    @DisplayName("outro tenant e id inexistente recebem NaoEncontrado")
    void outroTenantNaoEncontra() {
        CertificadoGuardado c = guardado(cofre.guardar(pedido(TENANT, gerador.pfxModerno(titular, SENHA), SENHA,
                CNPJ)));

        assertThat(cofre.abrir(TENANT + 1, c.id())).isEqualTo(new ResultadoAbertura.NaoEncontrado());
        assertThat(cofre.abrir(TENANT, c.id() + 1000)).isEqualTo(new ResultadoAbertura.NaoEncontrado());
    }

    @Test
    @DisplayName("depois da validade, abrir devolve Vencido")
    void vencidoNaoAbre() {
        CertificadoGuardado c = guardado(cofre.guardar(pedido(TENANT, gerador.pfxModerno(titular, SENHA), SENHA,
                CNPJ)));
        relogio.avancar(Duration.between(AGORA, c.validoAte()).plusSeconds(1));

        assertThat(cofre.abrir(TENANT, c.id())).isEqualTo(new ResultadoAbertura.Vencido(c.validoAte()));
    }
}
