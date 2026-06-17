package com.mediassist.platform.documentembedding.infrastructure.persistence;

import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbedding;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbeddingRepository;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDocumentChunkEmbeddingRepository implements DocumentChunkEmbeddingRepository {

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JpaDocumentChunkEmbeddingRepository(
        EntityManager entityManager,
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunkEmbedding> findAllByDocumentIdAndModelName(UUID documentId, String modelName) {
        return entityManager.createQuery("""
                select embedding
                from DocumentChunkEmbedding embedding
                join fetch embedding.chunk chunk
                join fetch chunk.documentExtraction extraction
                join fetch extraction.document document
                where document.id = :documentId
                  and embedding.modelName = :modelName
                order by chunk.chunkIndex asc
                """, DocumentChunkEmbedding.class)
            .setParameter("documentId", documentId)
            .setParameter("modelName", modelName)
            .getResultList();
    }

    @Override
    public List<DocumentChunkEmbedding> findAllByChunkIdInAndModelName(Collection<UUID> chunkIds, String modelName) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery("""
                select embedding
                from DocumentChunkEmbedding embedding
                join fetch embedding.chunk chunk
                where chunk.id in :chunkIds
                  and embedding.modelName = :modelName
                """, DocumentChunkEmbedding.class)
            .setParameter("chunkIds", chunkIds)
            .setParameter("modelName", modelName)
            .getResultList();
    }

    @Override
    public void saveEmbedding(
        DocumentChunk chunk,
        String modelName,
        int embeddingDimension,
        List<Double> embedding
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("chunkId", chunk.getId())
            .addValue("modelName", modelName)
            .addValue("embeddingDimension", embeddingDimension)
            .addValue("embedding", toVectorLiteral(embedding))
            .addValue("createdAt", LocalDateTime.now());

        jdbcTemplate.update("""
            insert into document_chunk_embeddings (
                id,
                chunk_id,
                model_name,
                embedding_dimension,
                embedding,
                created_at,
                updated_at
            )
            values (
                :id,
                :chunkId,
                :modelName,
                :embeddingDimension,
                cast(:embedding as vector),
                :createdAt,
                null
            )
            on conflict (chunk_id, model_name) do nothing
            """, parameters);
    }

    @Override
    public List<SemanticSearchMatch> searchSimilarChunks(
        UUID documentId,
        String modelName,
        List<Double> queryEmbedding,
        int topK
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("documentId", documentId)
            .addValue("modelName", modelName)
            .addValue("queryEmbedding", toVectorLiteral(queryEmbedding))
            .addValue("topK", topK);

        return jdbcTemplate.query("""
            select
                chunk.id as chunk_id,
                chunk.chunk_index as chunk_index,
                chunk.chunk_text as chunk_text,
                embedding.model_name as model_name,
                1 - (embedding.embedding <=> cast(:queryEmbedding as vector)) as similarity_score
            from document_chunk_embeddings embedding
            join document_chunks chunk on chunk.id = embedding.chunk_id
            join document_extractions extraction on extraction.id = chunk.document_extraction_id
            where extraction.document_id = :documentId
              and embedding.model_name = :modelName
            order by embedding.embedding <=> cast(:queryEmbedding as vector)
            limit :topK
            """, parameters, new SemanticSearchMatchRowMapper());
    }

    private String toVectorLiteral(List<Double> embedding) {
        return embedding.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static class SemanticSearchMatchRowMapper implements RowMapper<SemanticSearchMatch> {

        @Override
        public SemanticSearchMatch mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new SemanticSearchMatch(
                resultSet.getObject("chunk_id", UUID.class),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getDouble("similarity_score"),
                resultSet.getString("model_name")
            );
        }
    }
}
