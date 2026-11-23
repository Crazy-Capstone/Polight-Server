package polight.server.domain.consent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "consents", uniqueConstraints =
    @UniqueConstraint(name = "uk_consents_user", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User user;

  @Column(name = "terms_of_service", nullable = false)
  private boolean termsOfService;

  @Column(name = "privacy_collection_use", nullable = false)
  private boolean privacyCollectionUse;

  @Column(name = "overseas_transfer", nullable = false)
  private boolean overseasTransfer;

  @Column(name = "age_14_plus", nullable = false)
  private boolean age14Plus;

  @Column(name = "policy_analysis", nullable = false)
  private boolean policyAnalysis;

  @Column(name = "third_party_policy", nullable = false)
  private boolean thirdPartyPolicy;

  @Column(name = "health_information", nullable = false)
  private boolean healthInformation;

  public Consent(User user, Map<ConsentType, Boolean> consents) {
    this.user = user;
    update(consents);
  }

  public void update(Map<ConsentType, Boolean> consents) {
    consents.forEach((type, agreed) -> {
      switch (type) {
        case TERMS_OF_SERVICE -> this.termsOfService = agreed;
        case PRIVACY_COLLECTION_USE -> this.privacyCollectionUse = agreed;
        case OVERSEAS_TRANSFER -> this.overseasTransfer = agreed;
        case AGE_14_PLUS -> this.age14Plus = agreed;
        case POLICY_ANALYSIS -> this.policyAnalysis = agreed;
        case THIRD_PARTY_POLICY -> this.thirdPartyPolicy = agreed;
        case HEALTH_INFORMATION -> this.healthInformation = agreed;
      }
    });
  }

  public Map<ConsentType, Boolean> currentValues() {
    var values = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    values.put(ConsentType.TERMS_OF_SERVICE, termsOfService);
    values.put(ConsentType.PRIVACY_COLLECTION_USE, privacyCollectionUse);
    values.put(ConsentType.OVERSEAS_TRANSFER, overseasTransfer);
    values.put(ConsentType.AGE_14_PLUS, age14Plus);
    values.put(ConsentType.POLICY_ANALYSIS, policyAnalysis);
    values.put(ConsentType.THIRD_PARTY_POLICY, thirdPartyPolicy);
    values.put(ConsentType.HEALTH_INFORMATION, healthInformation);
    return Collections.unmodifiableMap(values);
  }
}
