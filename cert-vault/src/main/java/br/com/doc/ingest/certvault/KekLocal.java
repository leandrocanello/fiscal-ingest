package br.com.doc.ingest.certvault;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * KEKs locais para dev e self-host (CLAUDE.md §7.4), recebidas por construtor: ler a variável
 * de ambiente é papel dos {@code app-*}.
 */
public final class KekLocal implements ProvedorKek {

    private final Kek atual;
    private final Map<String, Kek> porId;

    /**
     * @param idAtual id da KEK usada nas novas cifragens; precisa estar em {@code chaves}
     * @param chaves  chaves por id, incluindo as antigas ainda necessárias para decifrar
     */
    public KekLocal(String idAtual, Map<String, byte[]> chaves) {
        if (chaves == null) {
            throw new IllegalArgumentException("chaves não pode ser nulo");
        }
        Map<String, Kek> keks = new HashMap<>();
        chaves.forEach((id, chave) -> keks.put(id, new Kek(id, chave)));
        this.porId = Map.copyOf(keks);
        this.atual = keks.get(idAtual);
        if (atual == null) {
            throw new IllegalArgumentException("a KEK atual precisa estar entre as chaves");
        }
    }

    @Override
    public Kek atual() {
        return atual;
    }

    @Override
    public Optional<Kek> porId(String id) {
        return Optional.ofNullable(porId.get(id));
    }
}
