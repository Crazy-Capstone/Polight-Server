package polight.server.domain.rag.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.rag.entity.PolicyChunk;
import polight.server.domain.rag.repository.PolicyChunkRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagSearchScopeService {

  private final PolicyChunkRepository policyChunkRepository;

  public List<PolicyChunk> findUserScopedChunks(UUID userId) {
    return policyChunkRepository.findActiveCompletedChunksByUserId(userId);
  }

  public List<PolicyChunk> findTripScopedChunks(UUID userId, UUID tripId) {
    return policyChunkRepository.findActiveCompletedChunksByUserIdAndTripId(userId, tripId);
  }

  public List<PolicyChunk> findPolicyScopedChunks(UUID userId, UUID policyId) {
    return policyChunkRepository.findActiveCompletedChunksByUserIdAndPolicyId(userId, policyId);
  }

  public List<PolicyChunk> findAnalysisScopedChunks(UUID userId, UUID analysisResultId) {
    return policyChunkRepository.findActiveCompletedChunksByUserIdAndAnalysisResultId(userId, analysisResultId);
  }
}
