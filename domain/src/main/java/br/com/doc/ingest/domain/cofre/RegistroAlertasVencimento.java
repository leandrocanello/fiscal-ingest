package br.com.doc.ingest.domain.cofre;

import java.util.Set;

/** Porta, só de acréscimo (invariante 6), dos alertas de vencimento já emitidos. */
public interface RegistroAlertasVencimento {

    /** Registra o alerta; {@code false} se já existia. Seguro sob concorrência. */
    boolean registrarSeAusente(long certificadoId, TipoAlerta tipo);

    /** Alertas já registrados para o certificado. */
    Set<TipoAlerta> emitidos(long certificadoId);
}
