package br.com.doc.ingest.domain.parser;

import java.util.Objects;

/** Resultado de {@link ParserDfe#analisar}. Nenhum caso carrega o XML. */
public sealed interface ResultadoParse {

    /** Documento reconhecido e com metadados válidos. */
    record Analisado(DfeAnalisado dfe) implements ResultadoParse {
        public Analisado {
            Objects.requireNonNull(dfe, "dfe");
        }
    }

    /** Schema que o parser não conhece; o bruto continua guardado para reprocesso. */
    record Desconhecido() implements ResultadoParse {
    }

    /** Schema conhecido, mas conteúdo inválido. O motivo nunca ecoa o conteúdo (inv. 13). */
    record Invalido(String motivo) implements ResultadoParse {
        public Invalido {
            Objects.requireNonNull(motivo, "motivo");
        }
    }
}
