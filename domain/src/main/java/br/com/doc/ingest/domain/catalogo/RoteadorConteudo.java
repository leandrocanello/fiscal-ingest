package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.Cnpj;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Atribui um documento às empresas de um tenant pelos CNPJs do conteúdo (inv. 10, §7.3).
 *
 * <p>Regras (decisão E3-S07, D1 (a) e D2 (a), revisáveis com os dados do smoke):
 * <ul>
 *   <li>emitente ∈ empresas → SAÍDA; destinatário ou transportador ∈ empresas → ENTRADA;</li>
 *   <li>autorizado no {@code autXML} → ENTRADA só para empresa do tipo {@code EMPRESA}: o escritório
 *       está no {@code autXML} de todo cliente e não "compra" as notas deles;</li>
 *   <li>resumo: a empresa do CNPJ consultado também recebe ENTRADA, se for do tipo {@code EMPRESA} e
 *       não for a emitente (o {@code resNFe} não traz o destinatário). É a única exceção ao "pelo
 *       conteúdo";</li>
 *   <li>evento: só o emitente, lido da chave de acesso, gera vínculo (SAÍDA); o autor é ignorado e
 *       os demais vínculos vêm da própria nota.</li>
 * </ul>
 * O CNPJ é comparado exato, sem raiz. A ordem do resultado só depende do conteúdo, sem repetição.
 */
public final class RoteadorConteudo {

    private RoteadorConteudo() {
    }

    public static List<VinculoEmpresa> rotear(
            TipoArquivo tipo, CnpjsDoConteudo conteudo, Cnpj consultado, List<EmpresaDoTenant> empresas) {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(conteudo, "conteudo");
        Objects.requireNonNull(consultado, "consultado");
        var porCnpj = indexar(empresas);
        Set<VinculoEmpresa> vinculos = new LinkedHashSet<>();

        var emitente = conteudo.emitente().map(Cnpj::valor).map(porCnpj::get);
        emitente.ifPresent(e -> vinculos.add(new VinculoEmpresa(e.id(), Direcao.SAIDA)));
        if (tipo == TipoArquivo.EVENTO || tipo == TipoArquivo.RESUMO_EVENTO) {
            return List.copyOf(vinculos);
        }

        entrada(conteudo.destinatario().map(porCnpj::get), vinculos);
        entrada(conteudo.transportador().map(Cnpj::valor).map(porCnpj::get), vinculos);
        for (var autorizado : conteudo.autorizados()) {
            entrada(Optional.ofNullable(porCnpj.get(autorizado.valor()))
                    .filter(e -> e.tipo() == TipoEmpresa.EMPRESA), vinculos);
        }

        if (tipo == TipoArquivo.RESUMO) {
            var daConsulta = Optional.ofNullable(porCnpj.get(consultado.valor()))
                    .filter(e -> e.tipo() == TipoEmpresa.EMPRESA)
                    .filter(e -> !e.cnpj().equals(conteudo.emitente().orElse(null)));
            entrada(daConsulta, vinculos);
        }
        return List.copyOf(vinculos);
    }

    private static void entrada(Optional<EmpresaDoTenant> empresa, Set<VinculoEmpresa> vinculos) {
        empresa.ifPresent(e -> vinculos.add(new VinculoEmpresa(e.id(), Direcao.ENTRADA)));
    }

    private static Map<String, EmpresaDoTenant> indexar(List<EmpresaDoTenant> empresas) {
        Map<String, EmpresaDoTenant> porCnpj = new HashMap<>();
        for (var empresa : empresas) {
            if (porCnpj.putIfAbsent(empresa.cnpj().valor(), empresa) != null) {
                throw new IllegalArgumentException("CNPJ repetido entre as empresas do tenant");
            }
        }
        return porCnpj;
    }
}
