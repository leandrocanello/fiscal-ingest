package br.com.doc.ingest.infra.postgres;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento entre tenants (ADR-013, §13): para qualquer distribuição de empresas — inclusive o mesmo CNPJ
 * em tenants diferentes —, cada tenant lê só as suas. Semeia como superuser e lê via {@link TransacaoPorTenant}
 * como {@code fiscal_app_teste} (superuser ignora RLS e não provaria nada).
 */
class IsolamentoTenantPropriedadesTest extends PostgresTestBase {

    private static final String SCHEMA = "isolamento_propriedades";

    // Poucos CNPJs sintéticos: força repetição do mesmo CNPJ entre tenants.
    private static final List<String> CNPJS = List.of(
            "11111111000101", "22222222000101", "33333333000101", "44444444000101", "55555555000101");

    private static Jdbi admin;
    private static TransacaoPorTenant transacao;

    private static synchronized void garantePronto() {
        if (admin == null) {
            var ds = dataSourceNoSchema(SCHEMA);
            Migracoes.aplicar(ds);
            admin = Jdbi.create(ds);
            transacao = new TransacaoPorTenant(Jdbi.create(dataSourceComoApp(SCHEMA)));
        }
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        admin.useHandle(h -> h.execute("truncate empresa, tenant restart identity cascade"));
    }

    /** Uma entrada por tenant (2..5): o conjunto de CNPJs das suas empresas (pode ser vazio). */
    @Provide
    Arbitrary<List<Set<String>>> distribuicao() {
        return Arbitraries.of(CNPJS).set().ofMaxSize(CNPJS.size()).list().ofMinSize(2).ofMaxSize(5);
    }

    @Property(tries = 15)
    void cadaTenantVeExatamenteAsSuas(@ForAll("distribuicao") List<Set<String>> distribuicao) {
        Map<Long, Set<Long>> semeado = semear(distribuicao);

        semeado.forEach((tenant, ids) -> assertThat(idsVistos(tenant)).isEqualTo(ids));
    }

    @Property(tries = 15)
    void somaDasVisoesIgualAoTotal(@ForAll("distribuicao") List<Set<String>> distribuicao) {
        Map<Long, Set<Long>> semeado = semear(distribuicao);
        long total = admin.withHandle(h -> h.createQuery("select count(*) from empresa").mapTo(Long.class).one());

        long soma = 0;
        for (long tenant : semeado.keySet()) {
            soma += transacao.emTransacao(tenant,
                    h -> h.createQuery("select count(*) from empresa").mapTo(Long.class).one());
        }

        assertThat(soma).isEqualTo(total);
    }

    @Property(tries = 15)
    void nenhumaLinhaDeOutroTenant(@ForAll("distribuicao") List<Set<String>> distribuicao) {
        Map<Long, Set<Long>> semeado = semear(distribuicao);

        for (long tenant : semeado.keySet()) {
            List<Long> tenantsVistos = transacao.emTransacao(tenant, h -> h.createQuery(
                            "select tenant_id from empresa union all select id from tenant")
                    .mapTo(Long.class).list());
            assertThat(tenantsVistos).isNotEmpty().containsOnly(tenant);
        }
    }

    private static Set<Long> idsVistos(long tenant) {
        return new HashSet<>(transacao.emTransacao(tenant,
                h -> h.createQuery("select id from empresa").mapTo(Long.class).list()));
    }

    /** Semeia como superuser; devolve, por tenant, os ids das empresas criadas para ele. */
    private static Map<Long, Set<Long>> semear(List<Set<String>> distribuicao) {
        return admin.inTransaction(h -> {
            Map<Long, Set<Long>> porTenant = new LinkedHashMap<>();
            for (int i = 0; i < distribuicao.size(); i++) {
                long tenant = h.createQuery("insert into tenant (nome) values (:nome) returning id")
                        .bind("nome", "Escritório " + i).mapTo(Long.class).one();
                Set<Long> ids = new HashSet<>();
                for (String cnpj : distribuicao.get(i)) {
                    ids.add(inserirEmpresa(h, tenant, cnpj));
                }
                porTenant.put(tenant, ids);
            }
            return porTenant;
        });
    }

    private static long inserirEmpresa(Handle h, long tenantId, String cnpj) {
        return h.createQuery("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :cnpj, :raiz, 'Empresa sintética', 'EMPRESA', 'PRODUCAO') returning id")
                .bind("t", tenantId).bind("cnpj", cnpj).bind("raiz", cnpj.substring(0, 8))
                .mapTo(Long.class).one();
    }
}
