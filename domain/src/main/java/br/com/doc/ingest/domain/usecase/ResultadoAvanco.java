package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.Cursor;

/**
 * Resultado do AvancarPonteiroUseCase: cursor atual + se avancou.
 */
public record ResultadoAvanco(
    Cursor cursorAtual,
    boolean avancou
) {}
