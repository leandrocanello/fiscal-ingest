package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.MotivoRecusa;

import java.time.Instant;
import java.util.Objects;

/** Desfecho de {@link ValidadorPfx#validar}. Nenhum dos dois carrega bytes do .pfx, a senha ou a chave privada. */
public sealed interface ResultadoValidacao {

    /**
     * O .pfx abriu e passou nas regras.
     *
     * @param fingerprint SHA-256 hexadecimal dos bytes do .pfx
     */
    record PfxValidado(String fingerprint, Instant validoDe, Instant validoAte) implements ResultadoValidacao {
        public PfxValidado {
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(validoDe, "validoDe");
            Objects.requireNonNull(validoAte, "validoAte");
        }
    }

    record Recusado(MotivoRecusa motivo) implements ResultadoValidacao {
        public Recusado {
            Objects.requireNonNull(motivo, "motivo");
        }
    }
}
