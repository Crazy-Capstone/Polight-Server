package polight.server.domain.terms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.TermsVerificationStatus;

public interface PolicyTermsRepository extends JpaRepository<PolicyTerms, UUID> {

  /** 보험사·상품명이 글자까지 같은 약관. 매칭의 빠른 경로다. */
  List<PolicyTerms> findByInsurerNameAndProductNameAndVerificationStatus(
      String insurerName, String productName, TermsVerificationStatus verificationStatus);

  List<PolicyTerms> findByOwnerUserId(UUID ownerUserId);

  /** 같은 파일이 이미 등록되어 있는지 확인한다. 중복 업로드 시 기존 약관을 재사용하기 위한 것이다. */
  List<PolicyTerms> findByFileHash(String fileHash);

  Optional<PolicyTerms> findByIdAndVerificationStatus(
      UUID id, TermsVerificationStatus verificationStatus);

  /**
   * 이 사용자가 쓸 수 있는 약관 전부. 매칭 후보 집합이다.
   *
   * <p>조건은 {@link PolicyTerms#isUsableBy}와 같다 -- VERIFIED 는 누구나, UNVERIFIED 는 주인만. 후보를 DB에서
   * 이 범위로 먼저 좁혀야, 매칭이 남의 UNVERIFIED 약관을 후보로 들고 오는 일이 애초에 없다.
   *
   * <p>전부 읽어 메모리에서 비교하는 이유: 보험사·상품명은 증권 OCR 결과라 표기가 흔들린다("삼성화재해상보험(주)" /
   * "삼성화재해상보험 주식회사"). SQL 등호로는 거의 맞지 않아 정규화한 뒤 비교해야 하는데, 정규화 규칙을 DB 함수로 옮기면 규칙을 두 곳에서
   * 관리하게 된다.
   *
   * <p>공용 약관은 상품 수만큼만 늘어 이 범위가 크지 않다(당장은 8건). 사용자 업로드는 주인 것만 딸려 온다. 공용 약관이 수천 건이 되면
   * 정규화 컬럼을 두고 인덱스로 좁히는 편이 낫다.
   */
  @Query(
      """
      SELECT pt
      FROM PolicyTerms pt
      WHERE pt.verificationStatus = polight.server.domain.terms.entity.TermsVerificationStatus.VERIFIED
         OR pt.ownerUser.id = :userId
      """)
  List<PolicyTerms> findUsableBy(@Param("userId") UUID userId);
}
