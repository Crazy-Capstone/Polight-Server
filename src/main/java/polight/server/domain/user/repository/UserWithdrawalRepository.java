package polight.server.domain.user.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserWithdrawalRepository {

  private final EntityManager entityManager;

  public void deleteUserData(UUID userId) {
    entityManager.flush();
    deleteUploadedTerms(userId);
    execute("delete from ChatMessage m where m.session.id in "
        + "(select s.id from ChatSession s where s.user.id = :userId)", userId);
    execute("delete from ChatSession s where s.user.id = :userId", userId);
    execute("delete from PolicyChunk c where c.user.id = :userId", userId);
    execute("delete from CoverageItem c where c.analysisResult.id in "
        + "(select a.id from AnalysisResult a where a.document.user.id = :userId)", userId);
    execute("delete from AnalysisResult a where a.document.id in "
        + "(select d.id from PolicyDocument d where d.user.id = :userId)", userId);
    execute("delete from PolicyDocument d where d.user.id = :userId", userId);
    execute("delete from Trip t where t.user.id = :userId", userId);
    execute("delete from Notification n where n.user.id = :userId", userId);
    execute("delete from NotificationPreference n where n.user.id = :userId", userId);
    execute("delete from User u where u.id = :userId", userId);
    // Bulk DML bypasses managed entities, including the locked user.
    entityManager.clear();
  }

  private void deleteUploadedTerms(UUID userId) {
    List<UUID> termsIds = entityManager.createQuery("""
        select t.id from PolicyTerms t left join t.sourceDocument d
        where t.ownerUser.id = :userId or d.user.id = :userId
        """, UUID.class).setParameter("userId", userId).getResultList();
    if (termsIds.isEmpty()) {
      return;
    }

    // Other members keep their analysis and insured amounts, not dangling references.
    executeTerms("""
        update AnalysisResult a
        set a.matchedTerms = null, a.termsMatchStage = null, a.termsMatchNotice = null
        where a.matchedTerms.id in :termsIds
        """, termsIds);
    executeTerms("""
        update CoverageItem c set c.termsCoverage = null
        where c.termsCoverage.id in
          (select tc.id from PolicyTermsCoverage tc where tc.terms.id in :termsIds)
        """, termsIds);
    executeTerms("""
        delete from PolicyTermsCoverageSource s
        where s.termsCoverage.id in
          (select c.id from PolicyTermsCoverage c where c.terms.id in :termsIds)
           or s.termsChunk.id in
          (select c.id from PolicyTermsChunk c where c.terms.id in :termsIds)
        """, termsIds);
    for (String entity : List.of(
        "CoverageDetailItem", "SubCoverageLimit", "RequiredDocument", "ExclusionCondition")) {
      executeTerms("delete from " + entity + " c where c.termsCoverage.id in "
          + "(select tc.id from PolicyTermsCoverage tc where tc.terms.id in :termsIds)", termsIds);
    }
    executeTerms("delete from PolicyTermsCoverage c where c.terms.id in :termsIds", termsIds);
    executeTerms("delete from PolicyTermsChunk c where c.terms.id in :termsIds", termsIds);
    executeTerms("delete from PolicyTerms t where t.id in :termsIds", termsIds);
  }

  private void execute(String query, UUID userId) {
    entityManager.createQuery(query).setParameter("userId", userId).executeUpdate();
  }

  private void executeTerms(String query, List<UUID> termsIds) {
    entityManager.createQuery(query).setParameter("termsIds", termsIds).executeUpdate();
  }
}
