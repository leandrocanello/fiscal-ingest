package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import java.util.List;

/**
 * Porta de leitura do cadastro de empresas, como o processor da coleta precisa (§7.3, D3 da E3-S07).
 * A coleta não tem tenant (§8.1): a nota vai a todos os tenants ativos que têm o CNPJ consultado, e a nenhum outro.
 */
public interface CadastroEmpresas {

    /**
     * Ids dos tenants com empresa ativa nesse CNPJ e ambiente, em ordem crescente e sem repetição.
     * Não exige tenant e não expõe nada além dos ids.
     *
     * @throws FalhaCatalogoException se a leitura falhar
     */
    List<Long> tenantsDoCnpjConsultado(Cnpj cnpjConsultado, Ambiente ambiente);

    /**
     * Empresas ativas do tenant no ambiente, em ordem de id.
     *
     * @throws FalhaCatalogoException se a leitura falhar
     */
    List<EmpresaDoTenant> empresasDoTenant(long tenantId, Ambiente ambiente);
}
