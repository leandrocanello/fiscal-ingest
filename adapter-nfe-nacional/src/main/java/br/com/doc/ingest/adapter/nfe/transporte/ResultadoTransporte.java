package br.com.doc.ingest.adapter.nfe.transporte;

import java.util.Arrays;

/** Resultado bruto do transporte: não interpreta status HTTP nem cStat (isso é do adapter). */
public sealed interface ResultadoTransporte {

    /** O corpo pode conter dados fiscais: cópia defensiva e {@code toString} sem bytes (inv. 13). */
    record RespostaHttp(int status, byte[] corpo) implements ResultadoTransporte {

        public RespostaHttp {
            corpo = corpo.clone();
        }

        @Override
        public byte[] corpo() {
            return corpo.clone();
        }

        @Override
        public boolean equals(Object outro) {
            return outro instanceof RespostaHttp r && status == r.status && Arrays.equals(corpo, r.corpo);
        }

        @Override
        public int hashCode() {
            return 31 * status + Arrays.hashCode(corpo);
        }

        @Override
        public String toString() {
            return "RespostaHttp[status=" + status + ", tamanho=" + corpo.length + "]";
        }
    }

    record FalhaTransporte(Motivo motivo) implements ResultadoTransporte {}

    enum Motivo { TIMEOUT, CONEXAO, RESPOSTA_GRANDE_DEMAIS }
}
