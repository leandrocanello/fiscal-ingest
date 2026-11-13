package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;

import java.util.List;
import java.util.Objects;

/** Resultado de {@link ProcessadorColetado#processar}. Nenhum caso carrega o XML (inv. 3). */
public sealed interface ResultadoProcessamento {

    /** Registrado no catálogo de cada tenant do CNPJ consultado, na ordem dos tenants. */
    record Registrado(List<ResultadoRegistro> resultados) implements ResultadoProcessamento {
        public Registrado {
            resultados = List.copyOf(resultados);
        }
    }

    /** Nenhum tenant ativo tem o CNPJ consultado: nada foi registrado; o bruto continua guardado. */
    record SemTenant() implements ResultadoProcessamento {
    }

    /** O parser não conhece o schema: nada foi registrado; o bruto continua guardado para reprocesso. */
    record SchemaDesconhecido() implements ResultadoProcessamento {
    }

    /** Conteúdo inválido: nada foi registrado. O motivo nunca ecoa o conteúdo (inv. 13). */
    record Invalido(String motivo) implements ResultadoProcessamento {
        public Invalido {
            Objects.requireNonNull(motivo, "motivo");
        }
    }
}
