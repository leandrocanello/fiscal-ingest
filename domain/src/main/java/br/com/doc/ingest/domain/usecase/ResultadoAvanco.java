package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * Resultado do AvancarPonteiroUseCase: cursor e NSU atuais + motivo do (não) avanço.
 *
 * <p>Só avança se novo ≥ atual. Recuo = {@link MotivoAvanco#RETROCESSO_REJEITADO} (erro de
 * consistência, nunca retentar): {@code cursorAtual} e {@code ultNsu} são os de entrada. O NSU
 * devolvido nunca é menor que o recebido.
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
