package br.com.doc.ingest.certvault;

import java.util.Optional;

/**
 * Fornece as KEKs do cofre: a atual, para cifrar, e qualquer uma pelo id, para decifrar
 * envelopes antigos.
 */
public interface ProvedorKek {

    /** @return a KEK usada nas novas cifragens */
    Kek atual();

    /** @return a KEK com esse id, ou vazio se ela não for conhecida */
    Optional<Kek> porId(String id);
}
