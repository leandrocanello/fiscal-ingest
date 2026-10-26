package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * Resultado do AvancarPonteiroUseCase: cursor e NSU atuais + motivo do (não) avanço.
 */
public record ResultadoAvanco(
    Cursor cursorAtual,
    Nsu ultNsu,
    MotivoAvanco motivo
) {
    public boolean avancou() {
        return motivo == MotivoAvanco.AVANCOU;
    }
}
