package polight.server.domain.rag.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.rag.entity.PolicyChunk;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, UUID> {

  List<PolicyChunk> findByAnalysisResultIdOrderByChunkIndexAsc(UUID analysisResultId);

  Optional<PolicyChunk> findByAnalysisResultIdAndChunkIndex(UUID analysisResultId, int chunkIndex);
}
