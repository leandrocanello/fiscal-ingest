package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * Resultado do AvancarPonteiroUseCase: cursor e NSU atuais + se avancou.
 */
public record ResultadoAvanco(
    Cursor cursorAtual,
    Nsu ultNsu,
    boolean avancou
) {}
