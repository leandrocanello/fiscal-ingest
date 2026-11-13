package br.com.doc.ingest.domain.cofre;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Marcos de alerta de vencimento (DA-53), do mais distante ao mais urgente (padrão 60/30/15/7/1 dias).
 * Um marco está cruzado quando {@code validoAte - marco <= agora} (limite inclusivo). Vencido
 * ({@code agora > validoAte}) não é alerta de vencimento.
 */
public record PoliticaAlertaVencimento(List<Duration> marcos) {

    public PoliticaAlertaVencimento {
        marcos = List.copyOf(marcos);
        if (marcos.isEmpty()) {
            throw new IllegalArgumentException("ao menos um marco");
        }
        for (int i = 0; i < marcos.size(); i++) {
            if (!marcos.get(i).isPositive()) {
                throw new IllegalArgumentException("marcos devem ser positivos");
            }
            if (i > 0 && marcos.get(i).compareTo(marcos.get(i - 1)) >= 0) {
                throw new IllegalArgumentException("marcos devem ser estritamente decrescentes");
            }
        }
    }

    public static PoliticaAlertaVencimento padrao() {
        return new PoliticaAlertaVencimento(List.of(Duration.ofDays(60), Duration.ofDays(30),
                Duration.ofDays(15), Duration.ofDays(7), Duration.ofDays(1)));
    }

    /** O marco mais urgente já cruzado, se ainda não foi emitido. */
    public Optional<Duration> marcoDevido(Instant validoAte, Instant agora, Set<Duration> jaEmitidos) {
        if (agora.isAfter(validoAte)) {
            return Optional.empty();
        }
        Duration restante = Duration.between(agora, validoAte);
        Duration maisUrgente = null;
        for (Duration marco : marcos) {
            if (restante.compareTo(marco) <= 0) {
                maisUrgente = marco;
            }
        }
        if (maisUrgente == null || jaEmitidos.contains(maisUrgente)) {
            return Optional.empty();
        }
        return Optional.of(maisUrgente);
    }
}
