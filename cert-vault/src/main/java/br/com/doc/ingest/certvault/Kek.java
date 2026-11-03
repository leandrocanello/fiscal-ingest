package br.com.doc.ingest.certvault;

import java.util.Arrays;

/**
 * Chave de 256 bits que embrulha as DEKs dos certificados (CLAUDE.md §7.4, DA-35).
 *
 * <p>Vive só em memória. O array é copiado na entrada e no acessor, e {@link #toString()}
 * não expõe a chave (invariante 13).
 *
 * @param id    identificador gravado no envelope, para achar a KEK ao decifrar
 * @param chave 32 bytes
 */
public record Kek(String id, byte[] chave) {

    private static final int TAMANHO_BYTES = 32;

    public Kek {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id não pode ser nulo nem em branco");
        }
        if (chave == null || chave.length != TAMANHO_BYTES) {
            throw new IllegalArgumentException("chave deve ter " + TAMANHO_BYTES + " bytes");
        }
        chave = chave.clone();
    }

    /** @return cópia da chave; quem chama deve zerá-la após o uso */
    @Override
    public byte[] chave() {
        return chave.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Kek outra
                && id.equals(outra.id)
                && Arrays.equals(chave, outra.chave);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(chave);
    }

    @Override
    public String toString() {
        return "Kek[id=" + id + ", chave=***]";
    }
}
