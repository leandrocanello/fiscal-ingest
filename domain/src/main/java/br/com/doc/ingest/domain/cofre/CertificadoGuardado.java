package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Cnpj;

import java.time.Instant;

/**
 * Certificado registrado no cofre: só referências ao cifrado + metadados, nada em claro.
 * {@link #toString()} não mostra as referências dos blobs.
 *
 * @param fingerprint sha256 do .pfx, 64 hex minúsculos
 * @param pfxCifrado  envelope com o .pfx
 * @param senhaCifrada envelope com a senha
 */
public record CertificadoGuardado(long id, long tenantId, long titularEmpresaId, Cnpj cnpj, String fingerprint,
                                  BlobRef pfxCifrado, BlobRef senhaCifrada, Instant validoDe, Instant validoAte,
                                  Instant enviadoEm, String enviadoPor) {

    public CertificadoGuardado {
        if (id <= 0) {
            throw new IllegalArgumentException("id deve ser positivo");
        }
        NovoCertificado.validar(tenantId, titularEmpresaId, cnpj, fingerprint, pfxCifrado, senhaCifrada,
                validoDe, validoAte, enviadoEm, enviadoPor);
    }

    @Override
    public String toString() {
        return "CertificadoGuardado[id=" + id + ", tenantId=" + tenantId
                + ", titularEmpresaId=" + titularEmpresaId + ", validoAte=" + validoAte + "]";
    }
}
