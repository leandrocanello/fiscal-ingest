package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.FalhaAbertura;

/** Desfecho de {@link EnvelopeCifrado#decifrar}. Falha nunca carrega causa nem conteúdo. */
public sealed interface ResultadoDecifra {

    /**
     * Conteúdo em claro, sem cópia: quem recebe deve zerar o array após o uso, e
     * {@link #toString()} não o expõe (invariante 13).
     */
    record Decifrado(byte[] conteudo) implements ResultadoDecifra {
        @Override
        public String toString() {
            return "Decifrado[conteudo=***]";
        }
    }

    record Falha(FalhaAbertura motivo) implements ResultadoDecifra { }
}
