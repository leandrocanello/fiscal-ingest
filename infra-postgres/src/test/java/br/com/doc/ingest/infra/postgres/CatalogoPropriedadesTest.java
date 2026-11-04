package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.EstadoDocumento;
import br.com.doc.ingest.domain.catalogo.FichaDocumento;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.ProvenienciaInformada;
import br.com.doc.ingest.domain.catalogo.RegistroArquivo;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do catálogo (CLAUDE.md §15 item 2, inv. 8): sequências aleatórias de registros de poucas
 * chaves e tenants, com reentregas e embaralhadas, escrevendo como usuário sem superuser. Os totais são
 * conferidos como superuser, que enxerga todos os tenants.
 *
 * <p>Um mesmo sha256 é sempre o mesmo arquivo (tipo e blob), como na vida real. Os registros confiáveis
 * concordam entre si, e os declarados também, mas os declarados divergem dos confiáveis.
 *
 * <p>{@code ShrinkingMode.FULL}: no modo padrão, o shrinking que estoura o limite de tempo segue em segundo
 * plano depois de uma falha e disputa o schema com a propriedade seguinte (deadlock no {@code truncate}).
 */
class CatalogoPropriedadesTest extends PostgresTestBase {

    private static final String SCHEMA = "catalogo_propriedades";
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant RECEBIDO_EM = Instant.parse("2026-11-03T08:00:00Z");
    private static final List<ChaveAcesso> CHAVES = List.of(
            new ChaveAcesso("35260111222333000181550010000000011000000011"),
            new ChaveAcesso("35260111222333000181650010000000021000000021"));

    private static final MetadadosDocumento VERDADE = new MetadadosDocumento(
            Optional.of(1),
            Optional.of(123L),
            Optional.of(OffsetDateTime.parse("2026-10-20T10:00:00-03:00")),
            Optional.of(new Cnpj("11222333000181")),
            Optional.of("12345678909"),
            Optional.of(new BigDecimal("1234.56")),
            Optional.of("4.00"));

    private static final MetadadosDocumento DECLARACAO = new MetadadosDocumento(
            Optional.of(9),
            Optional.of(999L),
            Optional.of(OffsetDateTime.parse("2020-01-01T00:00:00-03:00")),
            Optional.of(new Cnpj("11222333000262")),
            Optional.of("98765432100"),
            Optional.of(new BigDecimal("1.00")),
            Optional.of("3.10"));

    private static Jdbi admin;
    private static Catalogo catalogo;
    private static List<Tenant> tenants;
    private static List<Canal> canais;

    private record Tenant(long id, long empresaA, long empresaB) { }

    /** De onde um registro vem, sem o instante: repetir o canal é a mesma proveniência. */
    private record Canal(OrigemArquivo origem, Confianca confianca, Optional<Long> ledgerNsuId, Optional<Long> recebimentoId) { }

    /** Um registro, só com índices e máscaras: vira {@link RegistroArquivo} depois de o banco estar pronto. */
    record Recebido(int tenant, int chave, int arquivo, int canal, int campos, int vinculos,
                    SituacaoDocumento situacao, int minuto) { }

    /** A ficha sem ids e sem instantes, que dependem da ordem de chegada. */
    private record FichaComparavel(long tenantId, ChaveAcesso chave, EstadoDocumento estado,
                                   List<VinculoEmpresa> vinculos, List<ArquivoComparavel> arquivos) {

        static FichaComparavel de(FichaDocumento ficha) {
            return new FichaComparavel(ficha.tenantId(), ficha.chave(), ficha.estado(), ficha.vinculos(),
                    ficha.arquivos().stream().map(ArquivoComparavel::de).toList());
        }
    }

    private record ArquivoComparavel(TipoArquivo tipo, Optional<String> eventoTipo, Optional<Integer> eventoSeq,
                                     BlobRef blob, List<Canal> proveniencias) {

        static ArquivoComparavel de(ArquivoFicha arquivo) {
            return new ArquivoComparavel(arquivo.tipo(), arquivo.eventoTipo(), arquivo.eventoSeq(), arquivo.blob(),
                    arquivo.proveniencias().stream()
                            .map(p -> new Canal(p.origem(), p.confianca(), p.ledgerNsuId(), p.recebimentoId()))
                            .toList());
        }
    }

    private static synchronized void garantePronto() {
        if (admin == null) {
            UsuarioAppCatalogo.garantirPapeis();
            var ds = dataSourceNoSchema(SCHEMA);
            Migracoes.aplicar(ds);
            admin = Jdbi.create(ds);
            catalogo = new JdbiCatalogo(UsuarioAppCatalogo.jdbi(admin, SCHEMA), RELOGIO);
            tenants = List.of(inserirTenant("A"), inserirTenant("B"));
            canais = List.of(
                    new Canal(OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.of(inserirLedgerNsu("11222333000181")), Optional.empty()),
                    new Canal(OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.of(inserirLedgerNsu("11222333000262")), Optional.empty()),
                    new Canal(OrigemArquivo.AGENTE, Confianca.VALIDADO_ASSINATURA, Optional.empty(), Optional.of(1L)),
                    new Canal(OrigemArquivo.UPLOAD, Confianca.VALIDADO_ASSINATURA, Optional.empty(), Optional.of(2L)),
                    new Canal(OrigemArquivo.UPLOAD, Confianca.CONFIRMADO_SEFAZ, Optional.empty(), Optional.of(2L)),
                    new Canal(OrigemArquivo.EMAIL, Confianca.DECLARADO, Optional.empty(), Optional.of(3L)));
        }
    }

    private static Tenant inserirTenant(String nome) {
        long id = admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values (:n)")
                .bind("n", nome)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        return new Tenant(id, inserirEmpresa(id, "11222333000181"), inserirEmpresa(id, "11222333000262"));
    }

    private static long inserirEmpresa(long tenant, String cnpj) {
        return admin.withHandle(h -> h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :c, substr(:c, 1, 8), 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant).bind("c", cnpj)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private static long inserirLedgerNsu(String cnpjConsultado) {
        return admin.withHandle(h -> h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values ('nfe-an', :c, 'PRODUCAO', 1, 'BAIXADO', now())")
                .bind("c", cnpjConsultado)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    /** Só as tabelas do catálogo: tenants, empresas e ledger ficam, para os ids se manterem entre as execuções. */
    private static void limparCatalogo() {
        admin.useHandle(h -> h.execute("truncate proveniencia, arquivo, documento_empresa, documento"));
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        limparCatalogo();
    }

    @Provide
    Arbitrary<List<Recebido>> registros() {
        return Combinators.combine(
                        Arbitraries.integers().between(0, 1),
                        Arbitraries.integers().between(0, 1),
                        Arbitraries.integers().between(0, 2),
                        Arbitraries.integers().between(0, 5),
                        Arbitraries.integers().between(0, 127),
                        Arbitraries.integers().between(0, 3),
                        Arbitraries.of(SituacaoDocumento.class),
                        Arbitraries.integers().between(0, 59))
                .as(Recebido::new)
                .list().ofMinSize(1).ofMaxSize(12);
    }

    private static RegistroArquivo registro(Recebido r) {
        var tenant = tenants.get(r.tenant());
        var canal = canais.get(r.canal());
        var proveniencia = new ProvenienciaInformada(canal.origem(), canal.confianca(),
                canal.ledgerNsuId(), canal.recebimentoId(), RECEBIDO_EM.plusSeconds(60L * r.minuto()));
        var blob = new BlobRef("doc/" + r.arquivo() + ".xml.gz", sha256(r));
        var base = canal.confianca() == Confianca.DECLARADO ? DECLARACAO : VERDADE;
        var tipo = switch (r.arquivo()) {
            case 0 -> TipoArquivo.RESUMO;
            case 1 -> TipoArquivo.COMPLETO;
            default -> TipoArquivo.EVENTO;
        };
        boolean evento = tipo == TipoArquivo.EVENTO;
        return new RegistroArquivo(tenant.id(), CHAVES.get(r.chave()), subconjunto(base, r.campos()), r.situacao(), tipo,
                evento ? Optional.of("110111") : Optional.empty(), evento ? Optional.of(1) : Optional.empty(),
                blob, vinculos(tenant, r.vinculos()), proveniencia);
    }

    private static String sha256(Recebido r) {
        return "%064x".formatted(r.arquivo() + 1);
    }

    private static MetadadosDocumento subconjunto(MetadadosDocumento m, int mascara) {
        return new MetadadosDocumento(
                m.serie().filter(v -> (mascara & 1) != 0),
                m.numero().filter(v -> (mascara & 2) != 0),
                m.emissao().filter(v -> (mascara & 4) != 0),
                m.emitenteCnpj().filter(v -> (mascara & 8) != 0),
                m.destinatarioDoc().filter(v -> (mascara & 16) != 0),
                m.valorTotal().filter(v -> (mascara & 32) != 0),
                m.versaoLayout().filter(v -> (mascara & 64) != 0));
    }

    private static List<VinculoEmpresa> vinculos(Tenant tenant, int mascara) {
        var vinculos = new ArrayList<VinculoEmpresa>();
        if ((mascara & 1) != 0) {
            vinculos.add(new VinculoEmpresa(tenant.empresaA(), Direcao.SAIDA));
        }
        if ((mascara & 2) != 0) {
            vinculos.add(new VinculoEmpresa(tenant.empresaB(), Direcao.ENTRADA));
        }
        return vinculos;
    }

    /** A sequência embaralhada, com a primeira metade entregue de novo (relay que publica duas vezes). */
    private static List<Recebido> comReentregas(List<Recebido> registros, long semente) {
        var sequencia = new ArrayList<>(registros);
        sequencia.addAll(registros.subList(0, (registros.size() + 1) / 2));
        Collections.shuffle(sequencia, new Random(semente));
        return sequencia;
    }

    private static void registrarTodos(List<Recebido> sequencia) {
        sequencia.forEach(r -> catalogo.registrar(registro(r)));
    }

    private static List<String> linhasDoBanco(String sql, String... colunas) {
        return admin.withHandle(h -> h.createQuery(sql)
                .map((rs, ctx) -> {
                    var valores = new Object[colunas.length];
                    for (int i = 0; i < colunas.length; i++) {
                        valores[i] = rs.getObject(colunas[i]);
                    }
                    return Arrays.toString(valores);
                })
                .list());
    }

    private static List<String> distintos(List<Recebido> registros, Function<Recebido, Object[]> colunas) {
        return registros.stream().map(colunas).map(Arrays::toString).distinct().toList();
    }

    @Property(tries = 15, shrinking = ShrinkingMode.FULL)
    void umDocumentoPorChave(@ForAll("registros") List<Recebido> registros, @ForAll long semente) {
        registrarTodos(comReentregas(registros, semente));

        assertThat(linhasDoBanco("select tenant_id, chave_acesso from documento", "tenant_id", "chave_acesso"))
                .containsExactlyInAnyOrderElementsOf(distintos(registros, r -> new Object[] {
                        tenants.get(r.tenant()).id(), CHAVES.get(r.chave()).valor()}));
    }

    @Property(tries = 15, shrinking = ShrinkingMode.FULL)
    void arquivosSaoOsShasDistintos(@ForAll("registros") List<Recebido> registros, @ForAll long semente) {
        registrarTodos(comReentregas(registros, semente));

        assertThat(linhasDoBanco("""
                select d.tenant_id, d.chave_acesso, a.sha256
                from arquivo a join documento d on d.id = a.documento_id
                """, "tenant_id", "chave_acesso", "sha256"))
                .containsExactlyInAnyOrderElementsOf(distintos(registros, r -> new Object[] {
                        tenants.get(r.tenant()).id(), CHAVES.get(r.chave()).valor(), sha256(r)}));
    }

    @Property(tries = 15, shrinking = ShrinkingMode.FULL)
    void provenienciasSaoAsDistintas(@ForAll("registros") List<Recebido> registros, @ForAll long semente) {
        registrarTodos(comReentregas(registros, semente));

        assertThat(linhasDoBanco("""
                select d.tenant_id, d.chave_acesso, a.sha256, p.origem, p.confianca, p.ledger_nsu_id, p.recebimento_id
                from proveniencia p
                join arquivo a on a.id = p.arquivo_id
                join documento d on d.id = a.documento_id
                """, "tenant_id", "chave_acesso", "sha256", "origem", "confianca", "ledger_nsu_id", "recebimento_id"))
                .containsExactlyInAnyOrderElementsOf(distintos(registros, r -> {
                    var canal = canais.get(r.canal());
                    return new Object[] {tenants.get(r.tenant()).id(), CHAVES.get(r.chave()).valor(), sha256(r),
                            canal.origem().name(), canal.confianca().name(),
                            canal.ledgerNsuId().orElse(null), canal.recebimentoId().orElse(null)};
                }));
    }

    @Property(tries = 15, shrinking = ShrinkingMode.FULL)
    void ordemDeChegadaNaoMudaAFicha(@ForAll("registros") List<Recebido> registros, @ForAll long semente) {
        registrarTodos(registros);
        var fichas = fichasDe(registros);
        limparCatalogo();

        registrarTodos(comReentregas(registros, semente));

        assertThat(fichasDe(registros)).isEqualTo(fichas);
    }

    /** Uma ficha por par (tenant, chave) dos registros, sempre na ordem dos índices. */
    private static List<FichaComparavel> fichasDe(List<Recebido> registros) {
        var fichas = new ArrayList<FichaComparavel>();
        for (int t = 0; t < tenants.size(); t++) {
            for (int c = 0; c < CHAVES.size(); c++) {
                int tenant = t;
                int chave = c;
                if (registros.stream().anyMatch(r -> r.tenant() == tenant && r.chave() == chave)) {
                    fichas.add(FichaComparavel.de(catalogo.consultar(tenants.get(t).id(), CHAVES.get(c)).orElseThrow()));
                }
            }
        }
        return fichas;
    }
}
