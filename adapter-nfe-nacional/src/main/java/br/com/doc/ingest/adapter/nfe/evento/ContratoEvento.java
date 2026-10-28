package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.domain.adapter.TipoManifestacao;

/**
 * Constantes do leiaute do evento de manifestação do destinatário, isoladas aqui (mesmo padrão de
 * {@code ContratoDistDfe}). O mapeamento de {@link TipoManifestacao} para {@code tpEvento} fica no
 * adapter (invariante 4).
 *
 * <p>Fonte: MOC 7.0 p. 100 (leiaute do evento: {@code cOrgao} 91 = AN, {@code versao}/{@code verEvento})
 * e Tab. 5-41 p. 113 ({@code descEvento}) — decisão E2-S11 D2 (2026-10-27).
 * [VALIDAR docs/FONTES-OFICIAIS.md itens 6/7]: conferir no smoke de homologação (E2-S12).
 *
 * <p>Por ora só a Ciência da Operação é suportada; os demais tipos entram depois que o envio funcionar.
 */
public final class ContratoEvento {

    public static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    public static final String VERSAO = "1.00";
    public static final String VER_EVENTO = "1.00";
    public static final String C_ORGAO_AN = "91";

    private ContratoEvento() {
    }

    /** Código {@code tpEvento} do tipo; lança {@link IllegalArgumentException} se ainda não suportado. */
    public static String tpEvento(TipoManifestacao tipo) {
        return switch (tipo) {
            case CIENCIA -> "210210";
            default -> throw naoSuportado(tipo);
        };
    }

    /** Texto {@code descEvento} do tipo (sem acento); lança {@link IllegalArgumentException} se ainda não suportado. */
    public static String descEvento(TipoManifestacao tipo) {
        return switch (tipo) {
            case CIENCIA -> "Ciencia da Operacao";
            default -> throw naoSuportado(tipo);
        };
    }

    private static IllegalArgumentException naoSuportado(TipoManifestacao tipo) {
        return new IllegalArgumentException("Tipo de manifestação ainda não suportado: " + tipo.name());
    }
}
