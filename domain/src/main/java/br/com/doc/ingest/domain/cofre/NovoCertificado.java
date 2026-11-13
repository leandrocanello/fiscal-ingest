package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Cnpj;

import java.time.Instant;

/**
 * Metadados de um certificado já cifrado e gravado no blob store, ainda sem id.
 * {@link #toString()} não mostra as referências dos blobs.
 *
 * @param fingerprint sha256 do .pfx, 64 hex minúsculos
 * @param pfxCifrado  envelope com o .pfx
 * @param senhaCifrada envelope com a senha
 */
public record NovoCertificado(long tenantId, long titularEmpresaId, Cnpj cnpj, String fingerprint,
                              BlobRef pfxCifrado, BlobRef senhaCifrada, Instant validoDe, Instant validoAte,
                              Instant enviadoEm, String enviadoPor) {

    public NovoCertificado {
        validar(tenantId, titularEmpresaId, cnpj, fingerprint, pfxCifrado, senhaCifrada,
                validoDe, validoAte, enviadoEm, enviadoPor);
    }

    static void validar(long tenantId, long titularEmpresaId, Cnpj cnpj, String fingerprint,
                        BlobRef pfxCifrado, BlobRef senhaCifrada, Instant validoDe, Instant validoAte,
                        Instant enviadoEm, String enviadoPor) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId deve ser positivo");
        }
        if (titularEmpresaId <= 0) {
            throw new IllegalArgumentException("titularEmpresaId deve ser positivo");
        }
        if (cnpj == null) {
            throw new IllegalArgumentException("cnpj não pode ser nulo");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint deve ter 64 hex minúsculos");
        }
        if (pfxCifrado == null || senhaCifrada == null) {
            throw new IllegalArgumentException("Referências dos blobs cifrados não podem ser nulas");
        }
        if (validoDe == null || validoAte == null || enviadoEm == null) {
            throw new IllegalArgumentException("Datas do certificado não podem ser nulas");
        }
        if (validoAte.isBefore(validoDe)) {
            throw new IllegalArgumentException("validoAte não pode ser anterior a validoDe");
        }
        if (enviadoPor == null || enviadoPor.isBlank()) {
            throw new IllegalArgumentException("enviadoPor não pode ser nulo nem em branco");
        }
    }

    @Override
    public String toString() {
        return "NovoCertificado[tenantId=" + tenantId + ", titularEmpresaId=" + titularEmpresaId
                + ", validoAte=" + validoAte + "]";
    }
}
