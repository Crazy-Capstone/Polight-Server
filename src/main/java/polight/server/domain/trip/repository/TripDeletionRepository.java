package polight.server.domain.trip.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.chat.entity.ChatSession;

@Repository
@RequiredArgsConstructor
public class TripDeletionRepository {

  private final EntityManager entityManager;

  public void deleteTripData(UUID tripId, UUID userId) {
    entityManager.flush();
    entityManager.createQuery("""
        select a from AnalysisResult a
        where a.document.id in
          (select d.id from PolicyDocument d where d.trip.id = :tripId and d.user.id = :userId)
        order by a.id
        """, AnalysisResult.class)
        .setParameter("tripId", tripId).setParameter("userId", userId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    entityManager.createQuery("""
        select s from ChatSession s where s.trip.id = :tripId and s.user.id = :userId order by s.id
        """, ChatSession.class)
        .setParameter("tripId", tripId).setParameter("userId", userId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();

    execute("""
        update PolicyTerms t set t.sourceDocument = null
        where t.sourceDocument.id in
          (select d.id from PolicyDocument d where d.trip.id = :tripId and d.user.id = :userId)
        """, tripId, userId);
    execute("""
        delete from ChatMessage m where m.session.id in
          (select s.id from ChatSession s where s.trip.id = :tripId and s.user.id = :userId)
        """, tripId, userId);
    execute("delete from ChatSession s where s.trip.id = :tripId and s.user.id = :userId", tripId, userId);
    execute("""
        delete from PolicyChunk c where c.user.id = :userId and (
          c.trip.id = :tripId
          or c.document.id in (select d.id from PolicyDocument d where d.trip.id = :tripId and d.user.id = :userId)
          or c.analysisResult.id in (select a.id from AnalysisResult a where a.document.trip.id = :tripId and a.document.user.id = :userId))
        """, tripId, userId);
    execute("""
        delete from CoverageItem c where c.analysisResult.id in
          (select a.id from AnalysisResult a where a.document.trip.id = :tripId and a.document.user.id = :userId)
        """, tripId, userId);
    execute("""
        delete from AnalysisResult a where a.document.id in
          (select d.id from PolicyDocument d where d.trip.id = :tripId and d.user.id = :userId)
        """, tripId, userId);
    execute("delete from PolicyDocument d where d.trip.id = :tripId and d.user.id = :userId", tripId, userId);
    execute("delete from Trip t where t.id = :tripId and t.user.id = :userId", tripId, userId);
    entityManager.clear();
  }

  private void execute(String query, UUID tripId, UUID userId) {
    entityManager.createQuery(query).setParameter("tripId", tripId)
        .setParameter("userId", userId).executeUpdate();
  }
}
