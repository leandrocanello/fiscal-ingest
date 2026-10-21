package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.Documento;

import java.util.Optional;

/**
 * Resultado de buscarUm (consulta pontual / gap-fill): o documento, se encontrado,
 * mais o desfecho normalizado. SOMENTE metadado + BlobRef; NUNCA bytes/XML.
 *
 * <p>Arquivo recriado na Fase 1.5: estava referenciado pelo domínio, pelo adapter fake
 * e pelos testes, mas não foi recuperado do histórico. A forma segue o contrato de
 * docs/ARCHITECTURE.md §5.6 e o uso nos testes existentes.
 *
 * @param documento o documento encontrado, ou vazio
 * @param desfecho  o desfecho normalizado da consulta
 */
public record BuscarUmResultado(
    Optional<Documento> documento,
    Desfecho desfecho
) {}
