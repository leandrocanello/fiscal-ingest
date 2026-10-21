package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.FonteId;

/**
 * Descritor do adapter - identidade e capacidades da fonte.
 *
 * <p>O core consulta este descritor para saber:
 * <ul>
 *   <li>Qual fonte este adapter atende</li>
 *   <li>O que ele sabe fazer (lote, pontual, manifestacao)</li>
 *   <li>Se a fonte permite retrocesso (SEFAZ AN = false)</li>
 * </ul>
 *
 * @param fonteId      identificador da fonte (ex: "NFE_AN")
 * @param capacidades  o que o adapter suporta
 */
public record AdapterDescriptor(
    FonteId fonteId,
    Capacidades capacidades
) {}
