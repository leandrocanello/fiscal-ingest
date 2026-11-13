package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.model.Cnpj;

import java.time.Instant;

/** Aviso de vencimento de um certificado. Não carrega fingerprint nem referência de blob. */
public record AlertaVencimento(long tenantId, long titularEmpresaId, long certificadoId, Cnpj cnpj,
                               Instant validoAte, TipoAlerta tipo) {

    @Override
    public String toString() {
        return "AlertaVencimento[tenantId=" + tenantId + ", titularEmpresaId=" + titularEmpresaId
                + ", certificadoId=" + certificadoId + ", validoAte=" + validoAte + ", tipo=" + tipo + "]";
    }
}
