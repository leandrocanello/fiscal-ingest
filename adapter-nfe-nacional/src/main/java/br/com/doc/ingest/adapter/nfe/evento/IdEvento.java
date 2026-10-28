package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.domain.adapter.TipoManifestacao;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Atributo {@code Id} do {@code infEvento}: {@code "ID" + tpEvento + chave (44) + nSeqEvento (2)}, 54 caracteres.
 * É o alvo da {@code Reference} da assinatura XML-DSig.
 *
 * <p>Fonte: MOC 7.0 p. 100 (decisão E2-S11 D2). [VALIDAR docs/FONTES-OFICIAIS.md item 7].
 * Não confere o DV da chave: só formato (44 dígitos).
 */
public record IdEvento(TipoManifestacao tipo, String chave, int seq) {

    private static final Pattern CHAVE_44 = Pattern.compile("\\d{44}");

    public IdEvento {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(chave, "chave");
        if (!CHAVE_44.matcher(chave).matches()) {
            throw new IllegalArgumentException("Chave de acesso deve ter 44 dígitos numéricos");
        }
        if (seq < 1 || seq > 99) {
            throw new IllegalArgumentException("nSeqEvento fora de 1..99: " + seq);
        }
    }

    public String valor() {
        return "ID" + ContratoEvento.tpEvento(tipo) + chave + String.format("%02d", seq);
    }
}
