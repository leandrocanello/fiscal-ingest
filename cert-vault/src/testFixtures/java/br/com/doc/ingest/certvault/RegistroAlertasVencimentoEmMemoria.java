package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.RegistroAlertasVencimento;
import br.com.doc.ingest.domain.cofre.TipoAlerta;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** {@link RegistroAlertasVencimento} em memória, só de acréscimo. */
public final class RegistroAlertasVencimentoEmMemoria implements RegistroAlertasVencimento {

    private final Map<Long, Set<TipoAlerta>> emitidos = new ConcurrentHashMap<>();

    @Override
    public boolean registrarSeAusente(long certificadoId, TipoAlerta tipo) {
        return emitidos.computeIfAbsent(certificadoId, id -> ConcurrentHashMap.newKeySet()).add(tipo);
    }

    @Override
    public Set<TipoAlerta> emitidos(long certificadoId) {
        return Set.copyOf(emitidos.getOrDefault(certificadoId, Set.of()));
    }
}
