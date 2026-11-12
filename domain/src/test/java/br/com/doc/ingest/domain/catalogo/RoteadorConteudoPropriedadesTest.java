package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.model.Cnpj;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propriedades do {@link RoteadorConteudo} (inv. 10). */
class RoteadorConteudoPropriedadesTest {

    /** Universo pequeno de CNPJs, para que conteúdo e empresas se cruzem com frequência. */
    private static final List<Cnpj> UNIVERSO = IntStream.rangeClosed(1, 6)
            .mapToObj(i -> new Cnpj(String.valueOf(i).repeat(8) + "000191"))
            .toList();

    /** CNPJ que nunca é empresa do tenant. */
    private static final Cnpj NEUTRO = new Cnpj("77777777000191");

    record Cenario(TipoArquivo tipo, CnpjsDoConteudo conteudo, Cnpj consultado, List<EmpresaDoTenant> empresas) {
    }

    @Provide
    Arbitrary<Cenario> cenarios() {
        Arbitrary<Cnpj> cnpj = Arbitraries.of(UNIVERSO);
        Arbitrary<CnpjsDoConteudo> conteudo = Combinators.combine(
                        cnpj.optional(),
                        cnpj.map(Cnpj::valor).optional(),
                        cnpj.optional(),
                        cnpj.list().ofMaxSize(4))
                .as(CnpjsDoConteudo::new);
        Arbitrary<List<EmpresaDoTenant>> empresas = Arbitraries.of(UNIVERSO).set().ofMaxSize(UNIVERSO.size())
                .flatMap(cnpjs -> Arbitraries.of(TipoEmpresa.class).list().ofSize(cnpjs.size())
                        .map(tipos -> {
                            var lista = new ArrayList<EmpresaDoTenant>();
                            int i = 0;
                            for (var c : cnpjs) {
                                lista.add(new EmpresaDoTenant(100 + i, c, tipos.get(i)));
                                i++;
                            }
                            return List.copyOf(lista);
                        }));
        return Combinators.combine(Arbitraries.of(TipoArquivo.class), conteudo, cnpj, empresas).as(Cenario::new);
    }

    @Property
    void cnpjConsultadoSoVinculaResumoDeEmpresa(@ForAll("cenarios") Cenario c) {
        var comConsultado = RoteadorConteudo.rotear(c.tipo(), c.conteudo(), c.consultado(), c.empresas());
        var semConsultado = RoteadorConteudo.rotear(c.tipo(), c.conteudo(), NEUTRO, c.empresas());

        var aMais = new ArrayList<>(comConsultado);
        aMais.removeAll(semConsultado);
        assertThat(comConsultado).containsAll(semConsultado);

        var empresaConsultada = c.empresas().stream()
                .filter(e -> e.cnpj().equals(c.consultado()))
                .findFirst();
        boolean podeVincular = c.tipo() == TipoArquivo.RESUMO
                && empresaConsultada.map(e -> e.tipo() == TipoEmpresa.EMPRESA).orElse(false);
        if (podeVincular) {
            assertThat(aMais).isSubsetOf(new VinculoEmpresa(empresaConsultada.get().id(), Direcao.ENTRADA));
        } else {
            assertThat(aMais).isEmpty();
        }
    }

    @Property
    void todoVinculoEhDeEmpresaDoTenant(@ForAll("cenarios") Cenario c) {
        var ids = c.empresas().stream().map(EmpresaDoTenant::id).toList();
        var vinculos = RoteadorConteudo.rotear(c.tipo(), c.conteudo(), c.consultado(), c.empresas());
        assertThat(vinculos).allSatisfy(v -> assertThat(ids).contains(v.empresaId()));
    }

    @Property
    void semRepeticaoEOrdemEstavel(@ForAll("cenarios") Cenario c, @ForAll long semente) {
        var vinculos = RoteadorConteudo.rotear(c.tipo(), c.conteudo(), c.consultado(), c.empresas());
        assertThat(new HashSet<>(vinculos)).hasSameSizeAs(vinculos);

        var embaralhadas = new ArrayList<>(c.empresas());
        Collections.shuffle(embaralhadas, new Random(semente));
        assertThat(RoteadorConteudo.rotear(c.tipo(), c.conteudo(), c.consultado(), embaralhadas))
                .containsExactlyElementsOf(vinculos);
    }

    @Property
    void conteudoSemEmpresaNaoGeraVinculoForaDoResumo(@ForAll("cenarios") Cenario c) {
        var vazio = new CnpjsDoConteudo(Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        var vinculos = RoteadorConteudo.rotear(c.tipo(), vazio, c.consultado(), c.empresas());
        if (c.tipo() != TipoArquivo.RESUMO) {
            assertThat(vinculos).isEmpty();
        }
    }
}
