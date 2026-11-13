package br.com.doc.ingest.domain.coleta;

/** Desfecho de {@link EstadoColetaRepositorio#trocarCertificado}. Só {@link Trocado} grava algo. */
public sealed interface ResultadoTroca {

    /** O {@code certificado_id} passou de {@code esperado} para {@code novo}. */
    record Trocado() implements ResultadoTroca { }

    /** O certificado atual já era o novo; nada foi gravado. */
    record SemMudanca() implements ResultadoTroca { }

    /** O certificado atual não é o esperado (outra rotação passou antes); nada foi gravado. */
    record Conflito(Long atual) implements ResultadoTroca { }

    /** Não existe estado de coleta para a chave. */
    record NaoEncontrada() implements ResultadoTroca { }
}
