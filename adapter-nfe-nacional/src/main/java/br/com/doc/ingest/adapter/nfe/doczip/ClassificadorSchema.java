package br.com.doc.ingest.adapter.nfe.doczip;

/** Mapeia o atributo {@code schema} do {@code docZip} para o {@link TipoSchemaDfe}. */
public final class ClassificadorSchema {

    private ClassificadorSchema() {
    }

    public static TipoSchemaDfe classificar(String schema) {
        if (schema == null) {
            return TipoSchemaDfe.DESCONHECIDO;
        }
        String base = schema.strip();
        int corte = base.indexOf("_v");
        if (corte >= 0) {
            base = base.substring(0, corte);
        } else if (base.endsWith(".xsd")) {
            base = base.substring(0, base.length() - ".xsd".length());
        }
        for (TipoSchemaDfe tipo : TipoSchemaDfe.values()) {
            if (base.equals(tipo.nomeBase())) {
                return tipo;
            }
        }
        return TipoSchemaDfe.DESCONHECIDO;
    }
}
