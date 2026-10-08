package com.mediassist.platform.documentembedding.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JpaDocumentChunkEmbeddingRepositoryTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    private JpaDocumentChunkEmbeddingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaDocumentChunkEmbeddingRepository(entityManager, jdbcTemplate);
    }

    @Test
    void shouldParameterizeDocumentModelAndLimitWithoutUsingQuestionTextInSql() {
        UUID documentId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), ArgumentMatchers.<RowMapper<SemanticSearchCandidate>>any()))
            .thenReturn(List.of());

        assertThat(repository.searchSimilarChunkCandidates(documentId, "configured-model", List.of(0.1, -0.2), 20))
            .isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), ArgumentMatchers.<RowMapper<SemanticSearchCandidate>>any());
        assertThat(sql.getValue()).contains("extraction.document_id = :documentId", "embedding.model_name = :modelName",
            "limit :candidateCount", "embedding.embedding::text as embedding_vector");
        assertThat(parameters.getValue().getValue("documentId")).isEqualTo(documentId);
        assertThat(parameters.getValue().getValue("modelName")).isEqualTo("configured-model");
        assertThat(parameters.getValue().getValue("candidateCount")).isEqualTo(20);
        assertThat(parameters.getValue().getValue("queryEmbedding")).isEqualTo("[0.1,-0.2]");
    }

    @Test
    void shouldMapInternalVectorsAndPreserveOriginalMatchMetadata() throws Exception {
        UUID chunkId = UUID.randomUUID();
        when(resultSet.getObject("chunk_id", UUID.class)).thenReturn(chunkId);
        when(resultSet.getInt("chunk_index")).thenReturn(4);
        when(resultSet.getString("chunk_text")).thenReturn("Only a suspected condition was recorded.");
        when(resultSet.getDouble("similarity_score")).thenReturn(0.63);
        when(resultSet.getString("model_name")).thenReturn("configured-model");
        when(resultSet.getString("embedding_vector")).thenReturn("[0.1,-0.2,3.0E-4]");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), ArgumentMatchers.<RowMapper<SemanticSearchCandidate>>any()))
            .thenAnswer(invocation -> {
                RowMapper<SemanticSearchCandidate> mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(resultSet, 0));
            });

        SemanticSearchCandidate candidate = repository.searchSimilarChunkCandidates(
            UUID.randomUUID(), "configured-model", List.of(1.0, 0.0, 0.0), 20
        ).getFirst();

        assertThat(candidate.match().chunkId()).isEqualTo(chunkId);
        assertThat(candidate.match().chunkIndex()).isEqualTo(4);
        assertThat(candidate.match().chunkText()).isEqualTo("Only a suspected condition was recorded.");
        assertThat(candidate.match().similarityScore()).isEqualTo(0.63);
        assertThat(candidate.match().modelName()).isEqualTo("configured-model");
        assertThat(candidate.embedding()).containsExactly(0.1, -0.2, 0.0003);
    }
}
