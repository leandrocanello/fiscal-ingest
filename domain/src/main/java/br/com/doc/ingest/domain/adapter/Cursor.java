package br.com.doc.ingest.domain.adapter;

/**
 * Cursor opaco do adapter.
 * O core so guarda e devolve; NUNCA interpreta o conteudo.
 */
public record Cursor(String token) {
    public static Cursor inicial() { return new Cursor(""); }
}
