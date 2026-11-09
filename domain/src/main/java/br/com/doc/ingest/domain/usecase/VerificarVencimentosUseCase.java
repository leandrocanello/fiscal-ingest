package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.cofre.AlertaVencimento;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.CertificadoVigente;
import br.com.doc.ingest.domain.cofre.NotificadorVencimento;
import br.com.doc.ingest.domain.cofre.PoliticaAlertaVencimento;
import br.com.doc.ingest.domain.cofre.RegistroAlertasVencimento;
import br.com.doc.ingest.domain.cofre.RepositorioCertificados;
import br.com.doc.ingest.domain.cofre.SituacaoTitular;
import br.com.doc.ingest.domain.cofre.TipoAlerta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emite alertas de vencimento (DA-53): um por marco, só para o certificado vigente de cada titular, e um
 * {@code VENCIDO} quando não resta nenhum válido. A ordem é notificar e depois registrar, então a entrega
 * é pelo menos uma vez; falha no notificador deixa o marco pendente para a próxima rodada.
 */
public final class VerificarVencimentosUseCase {

    private record Titular(long tenantId, long titularEmpresaId) {
    }

    private final RepositorioCertificados certificados;
    private final PoliticaAlertaVencimento politica;
    private final RegistroAlertasVencimento registro;
    private final NotificadorVencimento notificador;
    private final Clock clock;

    public VerificarVencimentosUseCase(RepositorioCertificados certificados, PoliticaAlertaVencimento politica,
                                       RegistroAlertasVencimento registro, NotificadorVencimento notificador,
                                       Clock clock) {
        this.certificados = certificados;
        this.politica = politica;
        this.registro = registro;
        this.notificador = notificador;
        this.clock = clock;
    }

    public void executar() {
        Instant agora = clock.instant();
        Duration maior = politica.marcos().get(0);
        Set<Titular> titulares = certificados.vencendoAte(agora.plus(maior)).stream()
                .map(c -> new Titular(c.tenantId(), c.titularEmpresaId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Titular titular : titulares) {
            verificar(titular, agora);
        }
    }

    private void verificar(Titular titular, Instant agora) {
        List<CertificadoGuardado> todos = certificados.doTitular(titular.tenantId(), titular.titularEmpresaId());
        switch (CertificadoVigente.escolher(todos, agora)) {
            case SituacaoTitular.Vigente v -> {
                CertificadoGuardado c = v.certificado();
                Set<Duration> jaEmitidos = registro.emitidos(c.id()).stream()
                        .filter(TipoAlerta.Marco.class::isInstance)
                        .map(t -> ((TipoAlerta.Marco) t).antecedencia())
                        .collect(Collectors.toSet());
                Optional<Duration> marco = politica.marcoDevido(c.validoAte(), agora, jaEmitidos);
                marco.ifPresent(m -> emitir(c, new TipoAlerta.Marco(m)));
            }
            case SituacaoTitular.Vencido venc -> {
                CertificadoGuardado c = todos.stream()
                        .filter(x -> x.validoAte().equals(venc.validoAte()))
                        .max(Comparator.comparing(CertificadoGuardado::enviadoEm)
                                .thenComparingLong(CertificadoGuardado::id))
                        .orElseThrow();
                emitir(c, new TipoAlerta.Vencido());
            }
            case SituacaoTitular.AindaNaoValido a -> {
            }
            case SituacaoTitular.Ausente a -> {
            }
        }
    }

    private void emitir(CertificadoGuardado c, TipoAlerta tipo) {
        if (registro.emitidos(c.id()).contains(tipo)) {
            return;
        }
        try {
            notificador.notificar(new AlertaVencimento(c.tenantId(), c.titularEmpresaId(), c.id(), c.cnpj(),
                    c.validoAte(), tipo));
        } catch (RuntimeException falha) {
            return;
        }
        registro.registrarSeAusente(c.id(), tipo);
    }
}
