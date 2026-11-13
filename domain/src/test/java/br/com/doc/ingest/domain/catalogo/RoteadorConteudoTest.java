package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.model.Cnpj;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Roteamento pelo conteúdo (inv. 10, §7.3), com as regras D1 (a) e D2 (a) da E3-S07. */
class RoteadorConteudoTest {

    private static final Cnpj ESCRITORIO = new Cnpj("99999999000191");
    private static final Cnpj EMPRESA_A = new Cnpj("11111111000191");
    private static final Cnpj EMPRESA_B = new Cnpj("22222222000191");
    private static final Cnpj FILIAL_A = new Cnpj("11111111000272");
    private static final Cnpj DE_FORA = new Cnpj("33333333000191");
    private static final Cnpj OUTRO_DE_FORA = new Cnpj("44444444000191");

    private static final List<EmpresaDoTenant> EMPRESAS = List.of(
            new EmpresaDoTenant(1, ESCRITORIO, TipoEmpresa.ESCRITORIO),
            new EmpresaDoTenant(10, EMPRESA_A, TipoEmpresa.EMPRESA),
            new EmpresaDoTenant(20, EMPRESA_B, TipoEmpresa.EMPRESA));

    private static CnpjsDoConteudo conteudo(Cnpj emitente, String destinatario, Cnpj transportador, Cnpj... autorizados) {
        return new CnpjsDoConteudo(
                Optional.ofNullable(emitente),
                Optional.ofNullable(destinatario),
                Optional.ofNullable(transportador),
                List.of(autorizados));
    }

    private static List<VinculoEmpresa> rotear(TipoArquivo tipo, CnpjsDoConteudo c, Cnpj consultado) {
        return RoteadorConteudo.rotear(tipo, c, consultado, EMPRESAS);
    }

    @Test
    void emitenteDoTenantViraSaida() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(EMPRESA_A, DE_FORA.valor(), null), ESCRITORIO);
        assertThat(v).containsExactly(new VinculoEmpresa(10, Direcao.SAIDA));
    }

    @Test
    void destinatarioDoTenantViraEntrada() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(DE_FORA, EMPRESA_A.valor(), null), EMPRESA_A);
        assertThat(v).containsExactly(new VinculoEmpresa(10, Direcao.ENTRADA));
    }

    @Test
    void transportadorDoTenantViraEntrada() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(DE_FORA, OUTRO_DE_FORA.valor(), EMPRESA_B), EMPRESA_B);
        assertThat(v).containsExactly(new VinculoEmpresa(20, Direcao.ENTRADA));
    }

    @Test
    void empresaNoAutXmlViraEntrada() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(DE_FORA, OUTRO_DE_FORA.valor(), null, EMPRESA_B), ESCRITORIO);
        assertThat(v).containsExactly(new VinculoEmpresa(20, Direcao.ENTRADA));
    }

    @Test
    void transferenciaTemDoisVinculos() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(EMPRESA_A, EMPRESA_B.valor(), null), EMPRESA_B);
        assertThat(v).containsExactly(
                new VinculoEmpresa(10, Direcao.SAIDA),
                new VinculoEmpresa(20, Direcao.ENTRADA));
    }

    @Test
    void escritorioNoAutXmlNaoGeraVinculo() {
        var v = rotear(TipoArquivo.COMPLETO,
                conteudo(EMPRESA_A, DE_FORA.valor(), null, ESCRITORIO), ESCRITORIO);
        assertThat(v).containsExactly(new VinculoEmpresa(10, Direcao.SAIDA));
    }

    @Test
    void escritorioComoDestinatarioViraEntrada() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(DE_FORA, ESCRITORIO.valor(), null), ESCRITORIO);
        assertThat(v).containsExactly(new VinculoEmpresa(1, Direcao.ENTRADA));
    }

    @Test
    void escritorioComoEmitenteViraSaida() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(ESCRITORIO, DE_FORA.valor(), null), ESCRITORIO);
        assertThat(v).containsExactly(new VinculoEmpresa(1, Direcao.SAIDA));
    }

    @Test
    void clienteNaoCadastradoFicaSemVinculo() {
        var v = rotear(TipoArquivo.COMPLETO,
                conteudo(DE_FORA, OUTRO_DE_FORA.valor(), null, ESCRITORIO), ESCRITORIO);
        assertThat(v).isEmpty();
    }

    @Test
    void resumoVinculaEmpresaConsultadaComoEntrada() {
        var v = rotear(TipoArquivo.RESUMO, conteudo(DE_FORA, null, null), EMPRESA_A);
        assertThat(v).containsExactly(new VinculoEmpresa(10, Direcao.ENTRADA));
    }

    @Test
    void resumoNaoVinculaEscritorioConsultado() {
        var v = rotear(TipoArquivo.RESUMO, conteudo(DE_FORA, null, null), ESCRITORIO);
        assertThat(v).isEmpty();
    }

    @Test
    void resumoDeEmissaoPropriaNaoGeraEntrada() {
        var v = rotear(TipoArquivo.RESUMO, conteudo(EMPRESA_A, null, null), EMPRESA_A);
        assertThat(v).containsExactly(new VinculoEmpresa(10, Direcao.SAIDA));
    }

    @Test
    void completoNaoUsaOCnpjConsultado() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(DE_FORA, OUTRO_DE_FORA.valor(), null), EMPRESA_A);
        assertThat(v).isEmpty();
    }

    @Test
    void eventoVinculaEmitenteDaChave() {
        // no evento, o parser põe em "emitente" o CNPJ lido da chave de acesso
        var v = rotear(TipoArquivo.EVENTO, conteudo(EMPRESA_A, null, null), ESCRITORIO);
        assertThat(v).containsExactly(new VinculoEmpresa(10, Direcao.SAIDA));
        var r = rotear(TipoArquivo.RESUMO_EVENTO, conteudo(EMPRESA_A, null, null), ESCRITORIO);
        assertThat(r).containsExactly(new VinculoEmpresa(10, Direcao.SAIDA));
    }

    @Test
    void eventoNaoUsaOAutorNemOCnpjConsultado() {
        var v = rotear(TipoArquivo.EVENTO,
                conteudo(DE_FORA, EMPRESA_A.valor(), EMPRESA_B, EMPRESA_B), EMPRESA_A);
        assertThat(v).isEmpty();
        var r = rotear(TipoArquivo.RESUMO_EVENTO, conteudo(DE_FORA, null, null), EMPRESA_A);
        assertThat(r).isEmpty();
    }

    @Test
    void comparaCnpjExatoSemRaiz() {
        var v = rotear(TipoArquivo.COMPLETO, conteudo(FILIAL_A, FILIAL_A.valor(), null, FILIAL_A), FILIAL_A);
        assertThat(v).isEmpty();
    }
}
