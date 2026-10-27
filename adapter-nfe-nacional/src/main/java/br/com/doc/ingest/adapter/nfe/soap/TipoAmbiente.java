package br.com.doc.ingest.adapter.nfe.soap;

/** Ambiente do webservice de distribuição; vira o campo {@code tpAmb} do distDFeInt. */
public enum TipoAmbiente {
    PRODUCAO("1"),
    HOMOLOGACAO("2");

    private final String codigoTpAmb;

    TipoAmbiente(String codigoTpAmb) {
        this.codigoTpAmb = codigoTpAmb;
    }

    public String codigoTpAmb() {
        return codigoTpAmb;
    }
}
