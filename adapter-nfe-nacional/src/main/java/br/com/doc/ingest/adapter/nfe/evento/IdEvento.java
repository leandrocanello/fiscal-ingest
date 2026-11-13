package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.ChaveAcessoNfe;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;

import java.util.Objects;

/**
 * Atributo {@code Id} do {@code infEvento}: {@code "ID" + tpEvento + chave (44) + nSeqEvento (2)}, 54 caracteres.
 * É o alvo da {@code Reference} da assinatura XML-DSig.
 *
 * <p>Fonte: MOC 7.0 p. 100 (decisão E2-S11 D2). [VALIDAR docs/FONTES-OFICIAIS.md item 7].
 * A chave segue o formato da NT Conjunta 2025.001 (CNPJ alfanumérico) com DV obrigatório (DA-38).
 */
public record IdEvento(TipoManifestacao tipo, String chave, int seq) {

    public IdEvento {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(chave, "chave");
        if (!ChaveAcessoNfe.valida(chave)) {
            // não ecoa a chave (inv. 13)
            throw new IllegalArgumentException("Chave de acesso inválida (formato da NT 2025.001 ou DV)");
        }
        if (seq < 1 || seq > 99) {
            throw new IllegalArgumentException("nSeqEvento fora de 1..99: " + seq);
        }
    }

    public String valor() {
        return "ID" + ContratoEvento.tpEvento(tipo) + chave + String.format("%02d", seq);
    }
}
