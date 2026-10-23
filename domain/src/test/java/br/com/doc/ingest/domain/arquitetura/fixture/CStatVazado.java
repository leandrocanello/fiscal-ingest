package br.com.doc.ingest.domain.arquitetura.fixture;

/** Fixture violadora: o domínio não pode conhecer cStat (CLAUDE.md §2.2, inv. 4). */
public class CStatVazado {
    private int cStat;

    public int cStat() {
        return cStat;
    }
}
