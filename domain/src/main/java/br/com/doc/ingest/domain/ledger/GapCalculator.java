package br.com.doc.ingest.domain.ledger;

import br.com.doc.ingest.domain.model.Nsu;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculadora PURA de gaps (buracos) na sequência de NSUs do ledger.
 *
 * <p><b>Regras:</b>
 * <ul>
 *   <li>Dado um conjunto de {@link EntradaLedger}, calcula quais NSUs estão
 *       faltando entre o menor e o maior NSU conhecido.</li>
 *   <li>Todos os {@link StatusNsu} preenchem a sequência (incluindo
 *       {@link StatusNsu#DESCARTADO} e {@link StatusNsu#FORA_PRAZO}).</li>
 *   <li>O teto é o maior NSU conhecido — nunca reporta gaps acima dele.</li>
 *   <li>Não reporta gaps antes do primeiro (menor) NSU conhecido.</li>
 *   <li>Ledger vazio retorna {@code Set} vazio (ADR-005).</li>
 * </ul>
 *
 * <p>Esta classe é stateless e thread-safe.
 */
public class GapCalculator {

    /**
     * Calcula os NSUs faltantes (gaps) na sequência representada pelas entradas.
     *
     * @param entradas as entradas do ledger (podem estar em qualquer ordem)
     * @return conjunto de NSUs faltantes; vazio se não houver gaps ou se o ledger estiver vazio
     */
    public Set<Nsu> calcular(List<EntradaLedger> entradas) {
        if (entradas == null || entradas.isEmpty()) {
            return Set.of();
        }

        // Coleta todos os valores de NSU conhecidos
        Set<Long> conhecidos = new HashSet<>();
        long menor = Long.MAX_VALUE;
        long maior = Long.MIN_VALUE;

        for (EntradaLedger entrada : entradas) {
            long valor = entrada.nsu().valor();
            conhecidos.add(valor);
            if (valor < menor) menor = valor;
            if (valor > maior) maior = valor;
        }

        // Varre de menor a maior procurando buracos
        Set<Nsu> gaps = new HashSet<>();
        for (long v = menor; v <= maior; v++) {
            if (!conhecidos.contains(v)) {
                gaps.add(new Nsu(v));
            }
        }

        return gaps;
    }
}
