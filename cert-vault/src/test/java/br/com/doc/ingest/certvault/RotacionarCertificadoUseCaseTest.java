package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.CofreDeCertificados;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.cofre.SituacaoTitular;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import br.com.doc.ingest.domain.usecase.DesfechoRotacao;
import br.com.doc.ingest.domain.usecase.ResultadoRotacao;
import br.com.doc.ingest.domain.usecase.RotacionarCertificadoUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RotacionarCertificadoUseCase (DA-53, DA-54)")
class RotacionarCertificadoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");
    private static final Cnpj CNPJ = new Cnpj("12345678000199");
    private static final long TENANT = 1;
    private static final long TITULAR = 10;
    private static final ChaveColeta NFE_PROD = new ChaveColeta(new FonteId("NFE_AN"), CNPJ, Ambiente.PRODUCAO);
    private static final ChaveColeta NFE_HOMOL = new ChaveColeta(new FonteId("NFE_AN"), CNPJ, Ambiente.HOMOLOGACAO);
    private static final ChaveColeta CTE_PROD = new ChaveColeta(new FonteId("CTE_AN"), CNPJ, Ambiente.PRODUCAO);

    private RepositorioCertificadosEmMemoria certificados;
    private FakeEstadoColeta estados;
    private RelogioManual relogio;
    private RotacionarCertificadoUseCase useCase;

    @BeforeEach
    void preparar() {
        certificados = new RepositorioCertificadosEmMemoria();
        estados = new FakeEstadoColeta();
        relogio = new RelogioManual(AGORA);
        useCase = new RotacionarCertificadoUseCase(certificados, estados, relogio);
    }

    private CertificadoGuardado guardar(long tenant, long titular, int seq, Instant de, Instant ate) {
        return certificados.registrarSeAusente(new NovoCertificado(tenant, titular, CNPJ,
                String.format("%064x", tenant * 1000 + titular * 100 + seq),
                new BlobRef("cofre/pfx-" + seq + ".env", "a".repeat(64)),
                new BlobRef("cofre/senha-" + seq + ".env", "b".repeat(64)),
                de, ate, AGORA.minus(Duration.ofDays(30)), "operador"));
    }

    private CertificadoGuardado vigente(int seq, long diasAteVencer) {
        return guardar(TENANT, TITULAR, seq, AGORA.minus(Duration.ofDays(100)), AGORA.plus(Duration.ofDays(diasAteVencer)));
    }

    private void criar(ChaveColeta chave, Long certificadoId) {
        estados.criarSeAusente(chave);
        estados.definirCertificado(chave, certificadoId);
    }

    private Long certificadoDe(ChaveColeta chave) {
        return estados.certificadosDoCnpj(CNPJ).stream()
                .filter(v -> v.chave().equals(chave)).findFirst().orElseThrow().certificadoId();
    }

    @Test
    @DisplayName("novo vigente substitui o antigo em todas as chaves do CNPJ")
    void novoVigenteSubstituiOAntigoEmTodasAsChavesDoCnpj() {
        var antigo = vigente(1, 5);
        var novo = vigente(2, 365);
        criar(NFE_PROD, antigo.id());
        criar(NFE_HOMOL, antigo.id());
        criar(CTE_PROD, null);

        ResultadoRotacao r = useCase.executar(TENANT, TITULAR);

        assertThat(r.porChave()).containsOnlyKeys(NFE_PROD, NFE_HOMOL, CTE_PROD)
                .allSatisfy((k, d) -> assertThat(d).isInstanceOf(DesfechoRotacao.Trocada.class));
        assertThat(certificadoDe(NFE_PROD)).isEqualTo(novo.id());
        assertThat(certificadoDe(NFE_HOMOL)).isEqualTo(novo.id());
        assertThat(certificadoDe(CTE_PROD)).isEqualTo(novo.id());
    }

    @Test
    @DisplayName("cursor e versão ficam intactos após a rotação")
    void cursorEVersaoIntactosAposRotacao() {
        var antigo = vigente(1, 5);
        criar(NFE_PROD, antigo.id());
        PosicaoColeta lida = estados.carregar(NFE_PROD).orElseThrow();
        estados.confirmarLote(new ConfirmacaoLote(lida, new Cursor("c42"), new Nsu(42), List.of()));
        PosicaoColeta antes = estados.carregar(NFE_PROD).orElseThrow();
        vigente(2, 365);

        useCase.executar(TENANT, TITULAR);

        assertThat(estados.carregar(NFE_PROD)).contains(antes);
        assertThat(antes.versao()).isEqualTo(1);
    }

    @Test
    @DisplayName("renovação ainda não válida não troca; troca quando chega o validoDe")
    void renovacaoAindaNaoValidaNaoTroca() {
        var antigo = vigente(1, 5);
        var renovacao = guardar(TENANT, TITULAR, 2, AGORA.plus(Duration.ofDays(2)), AGORA.plus(Duration.ofDays(400)));
        criar(NFE_PROD, antigo.id());

        ResultadoRotacao r = useCase.executar(TENANT, TITULAR);

        assertThat(r.porChave().get(NFE_PROD)).isInstanceOf(DesfechoRotacao.SemMudanca.class);
        assertThat(certificadoDe(NFE_PROD)).isEqualTo(antigo.id());

        relogio.avancar(Duration.ofDays(2));
        // o antigo ainda vale por mais 3 dias, mas a renovação vence depois e passa a ser a vigente
        r = useCase.executar(TENANT, TITULAR);
        assertThat(r.porChave().get(NFE_PROD)).isInstanceOf(DesfechoRotacao.Trocada.class);
        assertThat(certificadoDe(NFE_PROD)).isEqualTo(renovacao.id());
    }

    @Test
    @DisplayName("sem certificado vigente não troca")
    void semCertificadoVigenteNaoTroca() {
        var vencido = guardar(TENANT, TITULAR, 1, AGORA.minus(Duration.ofDays(400)), AGORA.minus(Duration.ofDays(1)));
        criar(NFE_PROD, vencido.id());

        ResultadoRotacao r = useCase.executar(TENANT, TITULAR);

        assertThat(r.situacao()).isInstanceOf(SituacaoTitular.Vencido.class);
        assertThat(r.porChave()).isEmpty();
        assertThat(certificadoDe(NFE_PROD)).isEqualTo(vencido.id());
    }

    @Test
    @DisplayName("chave apontando para certificado de outro tenant não troca")
    void chaveApontandoParaCertificadoDeOutroTenantNaoTroca() {
        var deOutro = guardar(2, 20, 1, AGORA.minus(Duration.ofDays(100)), AGORA.plus(Duration.ofDays(100)));
        vigente(2, 365);
        criar(NFE_PROD, deOutro.id());

        ResultadoRotacao r = useCase.executar(TENANT, TITULAR);

        assertThat(r.porChave().get(NFE_PROD)).isEqualTo(new DesfechoRotacao.OutroTenant(deOutro.id()));
        assertThat(certificadoDe(NFE_PROD)).isEqualTo(deOutro.id());
    }

    @Test
    @DisplayName("conflito relê uma vez e termina")
    void conflitoReleUmaVezETermina() {
        var antigo = vigente(1, 5);
        var intermediario = vigente(3, 30);
        var novo = vigente(2, 365);
        criar(NFE_PROD, antigo.id());

        // outra rotação passa na frente uma vez: a segunda tentativa, já relida, vence
        AtomicInteger chamadas = new AtomicInteger();
        estados.antesDaTroca(chave -> {
            if (chamadas.getAndIncrement() == 0) {
                estados.definirCertificado(chave, intermediario.id());
            }
        });
        assertThat(useCase.executar(TENANT, TITULAR).porChave().get(NFE_PROD))
                .isInstanceOf(DesfechoRotacao.Trocada.class);
        assertThat(chamadas).hasValue(2);
        assertThat(certificadoDe(NFE_PROD)).isEqualTo(novo.id());

        // se perde de novo, desiste com Conflito (sem laço)
        criar(CTE_PROD, antigo.id());
        chamadas.set(0);
        estados.antesDaTroca(chave -> {
            if (chave.equals(CTE_PROD)) {
                chamadas.incrementAndGet();
                estados.definirCertificado(chave, chamadas.get() == 1 ? intermediario.id() : antigo.id());
            }
        });
        assertThat(useCase.executar(TENANT, TITULAR).porChave().get(CTE_PROD))
                .isEqualTo(new DesfechoRotacao.Conflito(antigo.id()));
        assertThat(chamadas).hasValue(2);
    }

    @Test
    @DisplayName("rotação não apaga o certificado antigo")
    void rotacaoNaoApagaOAntigo() {
        var antigo = vigente(1, 5);
        vigente(2, 365);
        criar(NFE_PROD, antigo.id());

        useCase.executar(TENANT, TITULAR);

        assertThat(certificados.buscar(TENANT, antigo.id())).contains(antigo);
        assertThat(certificados.doTitular(TENANT, TITULAR)).hasSize(2);
    }

    @Test
    @DisplayName("rotação não depende do cofre, então nunca abre o .pfx")
    void rotacaoNaoChamaAbrir() {
        assertThat(RotacionarCertificadoUseCase.class.getDeclaredConstructors()[0].getParameterTypes())
                .doesNotContain(CofreDeCertificados.class);
    }
}
