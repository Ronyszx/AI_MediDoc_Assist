package com.mediassist.platform.documentembedding.domain;

import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "document_chunk_embeddings",
    indexes = {
        @Index(name = "idx_document_chunk_embeddings_chunk_id", columnList = "chunk_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_document_chunk_embeddings_chunk_model",
            columnNames = {"chunk_id", "model_name"}
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "chunk")
public class DocumentChunkEmbedding {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "chunk_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_document_chunk_embeddings_chunk")
    )
    private DocumentChunk chunk;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    @Column(name = "embedding_dimension", nullable = false)
    private Integer embeddingDimension;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
