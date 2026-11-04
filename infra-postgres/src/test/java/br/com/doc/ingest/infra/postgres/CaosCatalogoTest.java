package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.ProvenienciaInformada;
import br.com.doc.ingest.domain.catalogo.RegistroArquivo;
import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caos (CLAUDE.md §15 item 7): escritores concorrentes na mesma chave viram um documento só (inv. 8),
 * sem exceção, sem deadlock e sem perder o que cada um trouxe.
 */
class CaosCatalogoTest extends PostgresTestBase {

    private static final int ESCRITORES = 8;
    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final ChaveAcesso CHAVE = new ChaveAcesso("35260111222333000181550010000000011000000011");
    private static final Instant RECEBIDO_EM = Instant.parse("2026-11-02T12:00:00Z");
    private static final Optional<Integer> SERIE = Optional.of(1);
    private static final Optional<Long> NUMERO = Optional.of(123L);
    private static final Optional<OffsetDateTime> EMISSAO =
            Optional.of(OffsetDateTime.of(2026, 1, 15, 13, 0, 0, 0, ZoneOffset.UTC));
    private static final Optional<Cnpj> EMITENTE = Optional.of(new Cnpj("11222333000181"));
    private static final Optional<String> DESTINATARIO = Optional.of("12345678909");
    private static final Optional<BigDecimal> VALOR = Optional.of(new BigDecimal("1234.50"));
    private static final Optional<String> LAYOUT = Optional.of("4.00");
    private static final MetadadosDocumento COMPLETOS =
            new MetadadosDocumento(SERIE, NUMERO, EMISSAO, EMITENTE, DESTINATARIO, VALOR, LAYOUT);

    private Jdbi admin;
    private Catalogo catalogo;
    private long tenant;
    private long empresa;
    private long empresa2;

    @BeforeEach
    void prepara() {
        String schema = "caos_catalogo_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        catalogo = new JdbiCatalogo(UsuarioAppCatalogo.jdbi(admin, schema),
                Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC));
        tenant = admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('Escritório')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        empresa = inserirEmpresa("11222333000181");
        empresa2 = inserirEmpresa("11222333000262");
    }

    private long inserirEmpresa(String cnpj) {
        return admin.withHandle(h -> h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :c, substr(:c, 1, 8), 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant).bind("c", cnpj)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long inserirLedgerNsu(long nsu) {
        return admin.withHandle(h -> h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values ('nfe-an', '11222333000181', 'PRODUCAO', :n, 'BAIXADO', now())")
                .bind("n", nsu)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long contar(String tabela) {
        return admin.withHandle(h -> h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one());
    }

    /** Cada escritor traz um só campo: a fusão só fica completa se nenhuma escrita sobrepuser a outra. */
    private static MetadadosDocumento umCampo(int indice) {
        return new MetadadosDocumento(
                indice == 0 ? SERIE : Optional.empty(),
                indice == 1 ? NUMERO : Optional.empty(),
                indice == 2 ? EMISSAO : Optional.empty(),
                indice == 3 ? EMITENTE : Optional.empty(),
                indice == 4 ? DESTINATARIO : Optional.empty(),
                indice == 5 ? VALOR : Optional.empty(),
                indice == 6 ? LAYOUT : Optional.empty());
    }

    /** Alterna os quatro canais; {@code DFE_AN} aponta para uma linha própria do ledger. */
    private ProvenienciaInformada provenienciaDoCanal(int indice) {
        return switch (indice % 4) {
            case 0 -> new ProvenienciaInformada(OrigemArquivo.DFE_AN, Confianca.OFICIAL,
                    Optional.of(inserirLedgerNsu(indice + 1L)), Optional.empty(), RECEBIDO_EM);
            case 1 -> push(OrigemArquivo.AGENTE, indice);
            case 2 -> push(OrigemArquivo.EMAIL, indice);
            default -> push(OrigemArquivo.UPLOAD, indice);
        };
    }

    private static ProvenienciaInformada push(OrigemArquivo origem, int indice) {
        return new ProvenienciaInformada(origem, Confianca.VALIDADO_ASSINATURA,
                Optional.empty(), Optional.of(indice + 1L), RECEBIDO_EM);
    }

    /** Vínculos em ordem invertida entre escritores: é a combinação que daria deadlock sem a trava do documento. */
    private List<VinculoEmpresa> vinculos(int indice) {
        var vinculos = new ArrayList<>(List.of(
                new VinculoEmpresa(empresa, Direcao.SAIDA), new VinculoEmpresa(empresa2, Direcao.ENTRADA)));
        if (indice % 2 == 0) {
            Collections.reverse(vinculos);
        }
        return vinculos;
    }

    /** Arquivo próprio do escritor: o 0 é o cancelamento; os outros alternam resumo e XML completo. */
    private RegistroArquivo registroDoEscritor(int indice) {
        var blob = new BlobRef("doc/" + indice + ".xml.gz", "%064x".formatted(indice + 1));
        var metadados = umCampo(indice);
        var proveniencia = provenienciaDoCanal(indice);
        if (indice == 0) {
            return new RegistroArquivo(tenant, CHAVE, metadados, SituacaoDocumento.CANCELADA, TipoArquivo.EVENTO,
                    Optional.of("110111"), Optional.of(1), blob, vinculos(indice), proveniencia);
        }
        boolean resumo = indice % 2 == 1;
        return new RegistroArquivo(tenant, CHAVE, metadados,
                resumo ? SituacaoDocumento.SO_RESUMO : SituacaoDocumento.AUTORIZADA,
                resumo ? TipoArquivo.RESUMO : TipoArquivo.COMPLETO,
                Optional.empty(), Optional.empty(), blob, vinculos(indice), proveniencia);
    }

    @RepeatedTest(20)
    void registrosConcorrentesDaMesmaChaveCriamUmDocumento() throws Exception {
        var registros = new ArrayList<RegistroArquivo>();
        for (int i = 0; i < ESCRITORES; i++) {
            registros.add(registroDoEscritor(i));
        }

        var resultados = concorrer(registros);

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(ESCRITORES);
        assertThat(contar("proveniencia")).isEqualTo(ESCRITORES);
        assertThat(contar("documento_empresa")).isEqualTo(2);
        assertThat(resultados).filteredOn(ResultadoRegistro::documentoCriado).hasSize(1);
        assertThat(resultados).allMatch(ResultadoRegistro::arquivoNovo).allMatch(ResultadoRegistro::provenienciaNova);
        assertThat(resultados.stream().map(ResultadoRegistro::documentoId).distinct()).hasSize(1);
        assertThat(resultados.stream().flatMap(r -> r.vinculosNovos().stream()))
                .containsExactlyInAnyOrder(
                        new VinculoEmpresa(empresa, Direcao.SAIDA), new VinculoEmpresa(empresa2, Direcao.ENTRADA));
        var estado = catalogo.consultar(tenant, CHAVE).orElseThrow().estado();
        assertThat(estado.metadados()).isEqualTo(COMPLETOS);
        assertThat(estado.situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(estado.soDeclarado()).isFalse();
    }

    @RepeatedTest(20)
    void reentregaConcorrenteDoMesmoRegistroEhNoOp() throws Exception {
        var registro = new RegistroArquivo(tenant, CHAVE, COMPLETOS, SituacaoDocumento.AUTORIZADA, TipoArquivo.COMPLETO,
                Optional.empty(), Optional.empty(), new BlobRef("doc/completo.xml.gz", "a".repeat(64)),
                vinculos(1), push(OrigemArquivo.UPLOAD, 1));

        var resultados = concorrer(Collections.nCopies(ESCRITORES, registro));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(contar("proveniencia")).isEqualTo(1);
        assertThat(contar("documento_empresa")).isEqualTo(2);
        assertThat(resultados).filteredOn(ResultadoRegistro::documentoCriado).hasSize(1);
        assertThat(resultados).noneMatch(ResultadoRegistro::documentoAlterado);
        assertThat(resultados).filteredOn(ResultadoRegistro::arquivoNovo).hasSize(1);
        assertThat(resultados).filteredOn(ResultadoRegistro::provenienciaNova).hasSize(1);
        assertThat(resultados.stream().flatMap(r -> r.vinculosNovos().stream())).hasSize(2);
        assertThat(resultados.stream().map(ResultadoRegistro::arquivoId).distinct()).hasSize(1);
    }

    /** Dispara todos ao mesmo tempo (barreira); qualquer exceção, inclusive deadlock, reprova o teste no {@code get}. */
    private List<ResultadoRegistro> concorrer(List<RegistroArquivo> registros) throws Exception {
        var barreira = new CyclicBarrier(registros.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futuros = new ArrayList<Future<ResultadoRegistro>>();
            for (var registro : registros) {
                futuros.add(executor.submit(() -> {
                    barreira.await(10, TimeUnit.SECONDS);
                    return catalogo.registrar(registro);
                }));
            }
            var resultados = new ArrayList<ResultadoRegistro>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        }
    }
}
