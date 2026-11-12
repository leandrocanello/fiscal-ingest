package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.Cnpj;
import java.util.Objects;

/**
 * Empresa ativa de um tenant, como o roteamento precisa vê-la.
 *
 * @param id   id da empresa
 * @param cnpj CNPJ exato (sem agrupar por raiz)
 * @param tipo empresa comum ou o próprio escritório
 */
public record EmpresaDoTenant(long id, Cnpj cnpj, TipoEmpresa tipo) {

    public EmpresaDoTenant {
        Objects.requireNonNull(cnpj, "cnpj");
        Objects.requireNonNull(tipo, "tipo");
    }
}
