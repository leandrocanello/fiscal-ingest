package br.com.doc.ingest.domain.cofre;

/**
 * Desfecho de {@link CofreDeCertificados#guardar}. Em {@link Recusado}, nada foi gravado.
 */
public sealed interface ResultadoGuarda {

    /** Cifrado gravado e metadados registrados (ou o registro já existente, em reenvio). */
    record Guardado(CertificadoGuardado certificado) implements ResultadoGuarda { }

    /** O .pfx não passou na validação. */
    record Recusado(MotivoRecusa motivo) implements ResultadoGuarda { }
}
