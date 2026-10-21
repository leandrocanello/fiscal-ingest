package br.com.doc.ingest.domain.model;

/**
 * Value object imutável representando um NSU (Número Sequencial Único).
 *
 * <p>Invariante de segurança crítica: NSU nunca pode ser negativo.
 * O valor zero representa o NSU inicial/vazio na SEFAZ (antes de qualquer documento).
 *
 * <p>Este value object é usado no controle de retrocesso — a regra de ouro:
 * <b>nunca enviar à SEFAZ um NSU anterior ao já consumido</b> (gera cStat 656
 * e bloqueia o CNPJ). Os métodos {@link #ehMaiorQue(Nsu)}, {@link #ehMenorQue(Nsu)}
 * e {@link #proximo()} suportam essa lógica no domínio.
 *
 * @param valor o número sequencial (>= 0)
 */
public record Nsu(long valor) implements Comparable<Nsu> {

    /**
     * Compact constructor — valida a invariante de não-negatividade.
     */
    public Nsu {
        if (valor < 0) {
            throw new IllegalArgumentException(
                    "NSU não pode ser negativo: " + valor);
        }
    }

    /**
     * Verifica se este NSU é estritamente maior que o outro.
     * Usado no controle de ponteiro para garantir que nunca retrocedemos.
     */
    public boolean ehMaiorQue(Nsu outro) {
        return this.valor > outro.valor;
    }

    /**
     * Verifica se este NSU é estritamente menor que o outro.
     */
    public boolean ehMenorQue(Nsu outro) {
        return this.valor < outro.valor;
    }

    /**
     * Retorna o próximo NSU na sequência (valor + 1).
     */
    public Nsu proximo() {
        return new Nsu(this.valor + 1);
    }

    @Override
    public int compareTo(Nsu outro) {
        return Long.compare(this.valor, outro.valor);
    }

    @Override
    public String toString() {
        return "Nsu[" + valor + "]";
    }
}
