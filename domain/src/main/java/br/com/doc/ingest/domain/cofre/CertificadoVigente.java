package br.com.doc.ingest.domain.cofre;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regra do certificado vigente de um titular (DA-53): válido no instante
 * ({@code validoDe <= agora <= validoAte}, inclusivo como em {@code CofreLocal.abrir}) com o maior
 * {@code validoAte}; empate por {@code enviadoEm} e depois por {@code id}, ambos maiores vencem.
 */
public final class CertificadoVigente {

    private static final Comparator<CertificadoGuardado> PRECEDENCIA = Comparator
            .comparing(CertificadoGuardado::validoAte)
            .thenComparing(CertificadoGuardado::enviadoEm)
            .thenComparingLong(CertificadoGuardado::id);

    private CertificadoVigente() {
    }

    public static SituacaoTitular escolher(List<CertificadoGuardado> certificados, Instant agora) {
        if (certificados.isEmpty()) {
            return new SituacaoTitular.Ausente();
        }
        Optional<CertificadoGuardado> vigente = certificados.stream()
                .filter(c -> !agora.isBefore(c.validoDe()) && !agora.isAfter(c.validoAte()))
                .max(PRECEDENCIA);
        if (vigente.isPresent()) {
            return new SituacaoTitular.Vigente(vigente.get());
        }
        Optional<Instant> vencido = certificados.stream()
                .map(CertificadoGuardado::validoAte)
                .filter(agora::isAfter)
                .max(Comparator.naturalOrder());
        if (vencido.isPresent()) {
            return new SituacaoTitular.Vencido(vencido.get());
        }
        return new SituacaoTitular.AindaNaoValido(certificados.stream()
                .map(CertificadoGuardado::validoDe).min(Comparator.naturalOrder()).orElseThrow());
    }
}
