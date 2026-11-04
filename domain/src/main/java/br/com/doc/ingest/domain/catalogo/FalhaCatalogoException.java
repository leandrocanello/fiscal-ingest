package br.com.doc.ingest.domain.catalogo;

/**
 * O catálogo não conseguiu concluir a operação; a transação foi desfeita e pode ser reprocessada.
 * Traz só um código técnico, <b>sem causa</b>: o texto original pode conter dados pessoais (inv. 13).
 */
public class FalhaCatalogoException extends RuntimeException {

    private final String codigo;

    /** @param codigo código da falha na implementação da porta (no Postgres, o SQLState) */
    public FalhaCatalogoException(String codigo) {
        super("Falha no catálogo: codigo=" + codigo);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
