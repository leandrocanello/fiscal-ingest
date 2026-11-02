package br.com.doc.ingest.domain.catalogo;

import java.util.Optional;

/**
 * Junta os registros de uma mesma nota sem depender da ordem de chegada (DA-27): o resultado é a
 * fusão dos registros confiáveis ou, se não há nenhum, a fusão dos declarados. Um push não validado
 * nunca altera um documento confiável (inv. 9).
 */
public final class FusaoDocumento {

    private FusaoDocumento() {
    }

    /** Estado de um documento a partir do seu primeiro registro. */
    public static EstadoDocumento inicial(
            MetadadosDocumento metadados, SituacaoDocumento situacao, Confianca confianca) {
        return new EstadoDocumento(metadados, situacao, confianca == Confianca.DECLARADO);
    }

    /** Devolve o próprio {@code atual} quando o registro não muda nada. */
    public static EstadoDocumento fundir(
            EstadoDocumento atual, MetadadosDocumento metadados, SituacaoDocumento situacao, Confianca confianca) {
        boolean declarado = confianca == Confianca.DECLARADO;
        if (declarado && !atual.soDeclarado()) {
            return atual;
        }
        if (!declarado && atual.soDeclarado()) {
            return inicial(metadados, situacao, confianca);
        }
        var fundido = new EstadoDocumento(
                preencherVazios(atual.metadados(), metadados),
                maior(atual.situacao(), situacao),
                declarado);
        return fundido.equals(atual) ? atual : fundido;
    }

    private static MetadadosDocumento preencherVazios(MetadadosDocumento atual, MetadadosDocumento novo) {
        return new MetadadosDocumento(
                primeiro(atual.serie(), novo.serie()),
                primeiro(atual.numero(), novo.numero()),
                primeiro(atual.emissao(), novo.emissao()),
                primeiro(atual.emitenteCnpj(), novo.emitenteCnpj()),
                primeiro(atual.destinatarioDoc(), novo.destinatarioDoc()),
                primeiro(atual.valorTotal(), novo.valorTotal()),
                primeiro(atual.versaoLayout(), novo.versaoLayout()));
    }

    private static <T> Optional<T> primeiro(Optional<T> atual, Optional<T> novo) {
        return atual.isPresent() ? atual : novo;
    }

    private static SituacaoDocumento maior(SituacaoDocumento a, SituacaoDocumento b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
