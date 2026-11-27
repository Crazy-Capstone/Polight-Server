package polight.server.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.PaymentType;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.terms.entity.CoverageDetailItem;
import polight.server.domain.terms.entity.ExclusionCondition;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsCoverage;
import polight.server.domain.terms.entity.RequiredDocument;
import polight.server.domain.terms.entity.SubCoverageLimit;
import polight.server.domain.terms.repository.CoverageDetailItemRepository;
import polight.server.domain.terms.repository.ExclusionConditionRepository;
import polight.server.domain.terms.repository.PolicyTermsCoverageRepository;
import polight.server.domain.terms.repository.PolicyTermsRepository;
import polight.server.domain.terms.repository.RequiredDocumentRepository;
import polight.server.domain.terms.repository.SubCoverageLimitRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_COVERAGE_TEST_DB_URL:jdbc:h2:mem:coverage-detail;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
    "spring.datasource.driver-class-name=${POLIGHT_COVERAGE_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${POLIGHT_COVERAGE_TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "spring.flyway.enabled=${POLIGHT_COVERAGE_TEST_FLYWAY:false}",
    "spring.jpa.hibernate.ddl-auto=${POLIGHT_COVERAGE_TEST_DDL:create-drop}",
    "spring.jpa.show-sql=false",
    "spring.docker.compose.enabled=false",
    "analysis.timeout.enabled=false",
    "storage.type=local",
    "terms.matching.provider=local",
    "oauth.kakao.client-id=test-client-id",
    "oauth.kakao.redirect-uri=http://localhost/test",
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "internal.api-key=coverage-test-internal-key"
})
@AutoConfigureMockMvc
@DirtiesContext
class CoverageDetailHttpTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private PolicyDocumentRepository documents;
  @Autowired private AnalysisResultRepository analyses;
  @Autowired private CoverageItemRepository items;
  @Autowired private PolicyTermsRepository terms;
  @Autowired private PolicyTermsCoverageRepository rules;
  @Autowired private ExclusionConditionRepository exclusions;
  @Autowired private RequiredDocumentRepository requiredDocuments;
  @Autowired private CoverageDetailItemRepository details;
  @Autowired private SubCoverageLimitRepository subLimits;
  @Autowired private JwtTokenProvider tokens;
  private User owner;
  private Trip trip;
  private PolicyDocument document;
  private AnalysisResult analysis;
  private String insurerName;

  @BeforeEach
  void fixture() {
    owner = users.saveAndFlush(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Tester").build());
    trip = trips.saveAndFlush(Trip.builder().user(owner).name("Trip")
        .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 10)).build());
    document = documents.saveAndFlush(PolicyDocument.builder().user(owner).trip(trip)
        .documentKind(DocumentKind.CERTIFICATE).originalFilename("certificate.pdf")
        .storedFilePath("test/" + trip.getId() + ".pdf").build());
    analysis = analyses.saveAndFlush(AnalysisResult.builder().document(document).build());
    insurerName = "Insurer " + UUID.randomUUID();
  }

  @ParameterizedTest
  @EnumSource(PaymentType.class)
  @DisplayName("지급 유형을 콜백으로 수신하면 금액·통화·약관 조건을 유지한 채 저장하고 조회한다")
  void receivesPersistsAndReturnsAllPaymentTypesWithoutChangingAmounts(PaymentType paymentType)
      throws Exception {
    PolicyTermsCoverage rule = rule("Treatment during insured trip");
    var body = callback();
    ((ObjectNode) body.path("coverageItems").get(0)).put("paymentType", paymentType.name());

    complete(body);
    CoverageItem saved = items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()).get(0);
    assertThat(saved.getPaymentType()).isEqualTo(paymentType);
    assertThat(saved.getLimitAmount()).isEqualTo(3000L);
    assertThat(saved.getLimitCurrency()).isEqualTo("USD");
    assertThat(saved.getLimitLabel()).isEqualTo("USD 3,000");
    JsonNode coverage = coverage();

    assertThat(coverage.path("paymentType").asText()).isEqualTo(paymentType.name());
    assertThat(coverage.path("keyCondition").asText()).isEqualTo("Treatment during insured trip");
    assertAmounts(coverage);
    assertThat(rules.findById(rule.getId()).orElseThrow().getConditions())
        .isEqualTo("Existing terms conditions");
  }

  @ParameterizedTest
  @ValueSource(strings = {"omitted", "null", "", "UNKNOWN", "FUTURE_PAYMENT_TYPE", "fixed"})
  @DisplayName("지급 유형이 없거나 미분류 값이어도 기존 콜백은 수신하고 지급 유형만 null로 둔다")
  void oldNullEmptyAndUnclassifiedCallbacksRemainCompatible(String value) throws Exception {
    rule(null);
    var body = callback();
    ObjectNode item = (ObjectNode) body.path("coverageItems").get(0);
    if ("null".equals(value)) {
      item.putNull("paymentType");
    } else if (!"omitted".equals(value)) {
      item.put("paymentType", value);
    }

    complete(body);

    assertThat(items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId())
        .get(0).getPaymentType()).isNull();
    JsonNode coverage = coverage();
    assertThat(coverage.path("paymentType").isNull()).isTrue();
    assertThat(coverage.path("keyCondition").isNull()).isTrue();
    assertAmounts(coverage);
    JsonNode raw = mapper.readTree(analyses.findById(analysis.getId()).orElseThrow().getRawResultJson());
    assertThat(raw.path("coverageItems")).isEqualTo(body.path("coverageItems"));
  }

  @Test
  @DisplayName("연결된 약관 규칙이 없으면 증권의 가입 금액을 유지하고 약관 조건은 비워 반환한다")
  void returnsNullKeyConditionWhenNoRuleIsLinked() throws Exception {
    complete(callback());

    JsonNode coverage = coverage();
    assertThat(coverage.path("keyCondition").isNull()).isTrue();
    assertThat(coverage.path("paymentType").isNull()).isTrue();
    assertThat(coverage.path("exclusions").size()).isZero();
    assertAmounts(coverage);
  }

  @Test
  @DisplayName("같은 콜백을 재수신해도 담보는 중복되지 않고 공용 약관 규칙과 근거는 유지한다")
  void repeatedCallbacksKeepTermsRulesChildrenAndExclusionSummaryUnchanged() throws Exception {
    PolicyTermsCoverage rule = rule("Original key condition");
    var exclusion = exclusions.saveAndFlush(ExclusionCondition.builder().termsCoverage(rule)
        .title("Excluded activity").description("Existing exclusion summary")
        .sourceText("Original exclusion source").build());
    var required = requiredDocuments.saveAndFlush(RequiredDocument.builder().termsCoverage(rule)
        .documentName("Medical receipt").mandatory(true).build());
    var detail = details.saveAndFlush(CoverageDetailItem.builder().termsCoverage(rule)
        .title("Original detail").covered(true).build());
    var subLimit = subLimits.saveAndFlush(SubCoverageLimit.builder().termsCoverage(rule)
        .label("Per incident").value("USD 500").limitAmount(500L).limitCurrency("USD").build());
    var body = callback();
    var item = (ObjectNode) body.path("coverageItems").get(0);
    item.put("paymentType", "DAILY");
    item.putArray("exclusions").addObject().put("title", "Do not replace")
        .put("description", "Do not replace");
    item.putArray("requiredDocuments").addObject().put("documentName", "Do not replace");
    item.putArray("detailItems").addObject().put("title", "Do not replace");
    item.putArray("subLimits").addObject().put("label", "Do not replace");

    complete(body);
    complete(body);

    JsonNode coverage = coverage();
    assertThat(coverage.path("exclusions").get(0).path("description").asText())
        .isEqualTo("Existing exclusion summary");
    assertThat(coverage.path("exclusions").get(0).path("sourceText").asText())
        .isEqualTo("Original exclusion source");
    assertThat(coverage.path("requiredDocuments").get(0).path("documentName").asText())
        .isEqualTo("Medical receipt");
    assertThat(coverage.path("detailItems").get(0).path("title").asText()).isEqualTo("Original detail");
    assertThat(coverage.path("subLimits").get(0).path("value").asText()).isEqualTo("USD 500");
    assertThat(coverage.path("keyCondition").asText()).isEqualTo("Original key condition");
    assertAmounts(coverage);
    var storedRule = rules.findById(rule.getId()).orElseThrow();
    assertThat(storedRule.getConditions()).isEqualTo("Existing terms conditions");
    assertThat(storedRule.getUpdatedAt()).isEqualTo(rule.getUpdatedAt());
    assertThat(exclusions.findByTermsCoverageIdInOrderBySortOrderAsc(List.of(rule.getId())))
        .extracting(ExclusionCondition::getId).containsExactly(exclusion.getId());
    assertThat(requiredDocuments.findByTermsCoverageIdInOrderBySortOrderAsc(List.of(rule.getId())))
        .extracting(RequiredDocument::getId).containsExactly(required.getId());
    assertThat(details.findByTermsCoverageIdInOrderBySortOrderAsc(List.of(rule.getId())))
        .extracting(CoverageDetailItem::getId).containsExactly(detail.getId());
    assertThat(subLimits.findByTermsCoverageIdInOrderBySortOrderAsc(List.of(rule.getId())))
        .extracting(SubCoverageLimit::getId).containsExactly(subLimit.getId());
    assertThat(items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId())).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({"COVERED,true", "PARTIALLY_COVERED,true", "NOT_COVERED,false", "EXCLUDED,false"})
  @DisplayName("부분 보장을 포함한 보장 상태를 수신하면 가입 여부를 올바르게 저장하고 반환한다")
  void derivesCoveredFlagFromCoverageStatus(String coverageStatus, boolean covered) throws Exception {
    var body = callback();
    ((ObjectNode) body.path("coverageItems").get(0)).put("coverageStatus", coverageStatus);

    complete(body);

    var saved = items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId());
    assertThat(saved).singleElement().satisfies(item -> {
      assertThat(item.getCoverageStatus().name()).isEqualTo(coverageStatus);
      assertThat(item.isCovered()).isEqualTo(covered);
    });
    var response = coverage();
    assertThat(response.path("coverageStatus").asText()).isEqualTo(coverageStatus);
    assertThat(response.path("isCovered").asBoolean()).isEqualTo(covered);
  }

  @ParameterizedTest(name = "잘못된 두 번째 담보: {0}")
  @ValueSource(strings = {"missing-title", "blank-title", "invalid-status"})
  @DisplayName("재수신한 콜백의 뒤쪽 담보가 잘못되면 기존 담보 삭제와 앞쪽 담보 저장을 모두 롤백한다")
  void invalidLaterCoverageRollsBackReplacementAndPreservesPreviousResult(String problem) throws Exception {
    complete(callback());
    var before = analyses.findById(analysis.getId()).orElseThrow();
    var previousItem = items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()).get(0);
    var body = callback().put("summary", "Must not replace the previous summary");
    var replacement = (ObjectNode) body.path("coverageItems").get(0);
    replacement.put("title", "Replacement coverage").put("limitAmount", 9999);
    var invalid = replacement.deepCopy();
    switch (problem) {
      case "missing-title" -> invalid.remove("title");
      case "blank-title" -> invalid.put("title", "   ");
      case "invalid-status" -> invalid.put("coverageStatus", "UNSUPPORTED_STATUS");
      default -> throw new IllegalArgumentException(problem);
    }
    ((ArrayNode) body.path("coverageItems")).add(invalid);

    mvc.perform(post(callbackEndpoint()).header("X-Internal-Api-Key", "coverage-test-internal-key")
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    var after = analyses.findById(analysis.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    assertThat(after.getSummary()).isEqualTo(before.getSummary());
    assertThat(after.getRawResultJson()).isEqualTo(before.getRawResultJson());
    assertThat(after.getCompletedAt()).isEqualTo(before.getCompletedAt());
    assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    assertThat(items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .singleElement().satisfies(item -> {
          assertThat(item.getId()).isEqualTo(previousItem.getId());
          assertThat(item.getTitle()).isEqualTo("Medical expense");
          assertThat(item.getLimitAmount()).isEqualTo(3000L);
        });
    assertThat(documents.findById(document.getId()).orElseThrow().getParseStatus())
        .isEqualTo(DocumentParseStatus.COMPLETED);
  }

  @Test
  @DisplayName("실패 콜백은 분석과 문서를 실패로 표시하고 이미 저장된 담보는 보존한다")
  void failureCallbackPreservesExistingCoveragesAndMarksDocumentFailed() throws Exception {
    complete(callback());
    var previousItem = items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()).get(0);
    var failure = mapper.createObjectNode().put("errorMessage", "AI indexing failed");

    mvc.perform(post(callbackEndpoint().replace("/complete", "/fail"))
            .header("X-Internal-Api-Key", "coverage-test-internal-key")
            .contentType(MediaType.APPLICATION_JSON).content(failure.toString()))
        .andExpect(status().isNoContent());

    var failed = analyses.findById(analysis.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    assertThat(failed.getFailureReason()).isEqualTo("AI indexing failed");
    assertThat(failed.getCompletedAt()).isNotNull();
    assertThat(documents.findById(document.getId()).orElseThrow().getParseStatus())
        .isEqualTo(DocumentParseStatus.FAILED);
    assertThat(items.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .extracting(CoverageItem::getId).containsExactly(previousItem.getId());
    mvc.perform(get(coverageEndpoint())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.createAccessToken(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.coverages").isEmpty())
        .andExpect(jsonPath("$.selectedConcerns").isEmpty());
  }

  @Test
  @DisplayName("콜백은 내부 인증 키를 요구하고 보장 내역은 문서 소유자에게만 반환한다")
  void existingCallbackAndCoverageSecurityPoliciesRemainInEffect() throws Exception {
    mvc.perform(post(callbackEndpoint()).contentType(MediaType.APPLICATION_JSON)
        .content(callback().toString())).andExpect(status().isUnauthorized());
    complete(callback());
    mvc.perform(get(coverageEndpoint())).andExpect(status().isUnauthorized());
    User other = users.saveAndFlush(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Other").build());
    mvc.perform(get(coverageEndpoint())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.createAccessToken(other)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("보장 조회 문서는 지급 유형을 공개하고 내부 콜백 경로는 숨긴다")
  void swaggerDescribesNewFieldsAndRetainsHiddenInternalCallback() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    JsonNode docs = mapper.readTree(result.getResponse().getContentAsString());
    var properties = docs.path("components").path("schemas").path("CoverageItemResponse")
        .path("properties");
    assertThat(properties.path("keyCondition").path("description").asText()).contains("핵심 조건");
    assertThat(properties.path("paymentType").path("enum"))
        .isEqualTo(mapper.valueToTree(List.of("FIXED", "PROPORTIONAL", "INDEMNITY", "DAILY")));
    assertThat(properties.has("limitAmount")).isTrue();
    assertThat(properties.has("limitCurrency")).isTrue();
    assertThat(properties.has("limitLabel")).isTrue();
    assertThat(docs.path("components").path("schemas").path("ExclusionResponse")
        .path("properties").has("description")).isTrue();
    assertThat(docs.path("paths").has("/internal/analysis-results/{analysisResultId}/complete")).isFalse();
  }

  private PolicyTermsCoverage rule(String keyCondition) {
    PolicyTerms policy = terms.saveAndFlush(
        PolicyTerms.official(insurerName, "Travel policy", null, null));
    return rules.saveAndFlush(PolicyTermsCoverage.builder().terms(policy)
        .title("Medical expense").category("MEDICAL").limitLabel("Original terms limit")
        .conditions("Existing terms conditions").keyCondition(keyCondition).build());
  }

  private ObjectNode callback() {
    ObjectNode body = mapper.createObjectNode().put("analysisResultId", analysis.getId().toString())
        .put("status", "COMPLETED").put("summary", "Test summary")
        .put("insurerName", insurerName).put("productName", "Travel policy");
    body.putArray("coverageItems").addObject().put("title", "Medical expense")
        .put("category", "MEDICAL").put("coverageStatus", "COVERED")
        .put("limitLabel", "USD 3,000").put("limitAmount", 3000)
        .put("limitCurrency", "USD").put("conditions", "Certificate conditions");
    return body;
  }

  private void complete(ObjectNode body) throws Exception {
    mvc.perform(post(callbackEndpoint()).header("X-Internal-Api-Key", "coverage-test-internal-key")
        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isNoContent());
  }

  private JsonNode coverage() throws Exception {
    var result = mvc.perform(get(coverageEndpoint())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.createAccessToken(owner)))
        .andExpect(status().isOk()).andReturn();
    var coverages = mapper.readTree(result.getResponse().getContentAsString()).path("coverages");
    assertThat(coverages).hasSize(1);
    return coverages.get(0);
  }

  private void assertAmounts(JsonNode coverage) {
    assertThat(coverage.path("limitAmount").asLong()).isEqualTo(3000L);
    assertThat(coverage.path("limitCurrency").asText()).isEqualTo("USD");
    assertThat(coverage.path("limitLabel").asText()).isEqualTo("USD 3,000");
    assertThat(coverage.path("conditions").asText()).isEqualTo("Certificate conditions");
  }

  private String callbackEndpoint() {
    return "/internal/analysis-results/" + analysis.getId() + "/complete";
  }

  private String coverageEndpoint() {
    return "/api/v1/trips/" + trip.getId() + "/documents/" + document.getId() + "/analysis/coverages";
  }
}
