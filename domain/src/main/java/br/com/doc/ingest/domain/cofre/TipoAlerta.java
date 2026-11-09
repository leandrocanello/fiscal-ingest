package br.com.doc.ingest.domain.cofre;

import java.time.Duration;

/** Tipo de alerta de vencimento: um marco de antecedência ou o vencimento em si. */
public sealed interface TipoAlerta {

    /** Marco de antecedência cruzado (ex.: 15 dias antes de {@code validoAte}). */
    record Marco(Duration antecedencia) implements TipoAlerta {
    }

    /** O certificado vigente do titular já venceu. */
    record Vencido() implements TipoAlerta {
    }
}
