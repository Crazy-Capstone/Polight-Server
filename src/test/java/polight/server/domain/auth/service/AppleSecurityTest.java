package polight.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import polight.server.domain.auth.apple.AppleTestServer;
import polight.server.domain.auth.config.AppleOAuthProperties;
import polight.server.domain.auth.dto.AppleLoginRequest;
import polight.server.domain.auth.dto.apple.AppleTokenResponse;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

class AppleSecurityTest {

  private static final AppleTestServer SERVER = new AppleTestServer();
  private AppleIdentityTokenVerifier verifier;

  @BeforeEach
  void setUp() {
    SERVER.reset();
    verifier = new AppleIdentityTokenVerifier(SERVER.properties());
  }

  @AfterAll
  static void stop() {
    SERVER.close();
  }

  @Test
  void verifiesSignatureSubjectAudienceIssuerAndHashedOriginalNonce() {
    assertThat(verifier.verify(SERVER.identity("subject", "original-nonce"), "original-nonce")).isEqualTo("subject");
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> verifier.verify(SERVER.identity("subject", "original-nonce"), AppleTestServer.hash("original-nonce")))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID));
  }

  @ParameterizedTest
  @ValueSource(strings = {"issuer", "audience", "expired", "future-iat", "missing-iat", "missing-exp", "missing-sub", "missing-nonce", "wrong-nonce", "empty-sub"})
  void rejectsInvalidClaims(String field) {
    Consumer<JWTClaimsSet.Builder> change = switch (field) {
      case "issuer" -> builder -> builder.issuer("https://untrusted.example");
      case "audience" -> builder -> builder.audience("wrong.client");
      case "expired" -> builder -> builder.issueTime(Date.from(Instant.now().minusSeconds(600)))
          .expirationTime(Date.from(Instant.now().minusSeconds(1)));
      case "future-iat" -> builder -> builder.issueTime(Date.from(Instant.now().plusSeconds(120)));
      case "missing-iat" -> builder -> builder.issueTime(null);
      case "missing-exp" -> builder -> builder.expirationTime(null);
      case "missing-sub" -> builder -> builder.subject(null);
      case "empty-sub" -> builder -> builder.subject(" ");
      case "missing-nonce" -> builder -> builder.claim("nonce", null);
      default -> builder -> builder.claim("nonce", "not-the-hash");
    };
    String token = SERVER.identity("subject", "nonce", change);
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> verifier.verify(token, "nonce"))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID));
  }

  @Test
  void rejectsWrongSignatureAndUnknownKid() throws Exception {
    var original = SignedJWT.parse(SERVER.identity("subject", "nonce"));
    var wrongKey = new RSAKeyGenerator(2048).generate();
    var tampered = new SignedJWT(original.getHeader(), original.getJWTClaimsSet());
    tampered.sign(new RSASSASigner(wrongKey));
    assertInvalid(tampered.serialize());
    var unknown = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("unknown").build(), original.getJWTClaimsSet());
    unknown.sign(new RSASSASigner(wrongKey));
    assertInvalid(unknown.serialize());
  }

  @Test
  void rejectsWrongAlgorithmAndMissingKidBeforeKeyLookup() throws Exception {
    var original = SignedJWT.parse(SERVER.identity("subject", "nonce"));
    var wrongKey = new RSAKeyGenerator(2048).generate();
    var wrongAlgorithm = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS512).keyID("key").build(), original.getJWTClaimsSet());
    wrongAlgorithm.sign(new RSASSASigner(wrongKey));
    assertInvalid(wrongAlgorithm.serialize());
    var missingKid = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), original.getJWTClaimsSet());
    missingKid.sign(new RSASSASigner(wrongKey));
    assertInvalid(missingKid.serialize());
    assertThat(SERVER.jwksCalls).hasValue(0);
  }

  @Test
  void cachesPublicKeysAndRefreshesForRotatedKid() {
    verifier.verify(SERVER.identity("one", "nonce"), "nonce");
    int first = SERVER.jwksCalls.get();
    verifier.verify(SERVER.identity("two", "nonce"), "nonce");
    assertThat(SERVER.jwksCalls).hasValue(first);
    SERVER.rotateKey();
    assertThat(verifier.verify(SERVER.identity("three", "nonce"), "nonce")).isEqualTo("three");
    assertThat(SERVER.jwksCalls.get()).isGreaterThan(first);
  }

  @Test
  void publicKeyServerFailureIsNotClassifiedAsInvalidCredentials() {
    SERVER.jwksStatus.set(503);
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> verifier.verify(SERVER.identity("subject", "nonce"), "nonce"))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_SERVICE_UNAVAILABLE));
  }

  @Test
  void generatesShortLivedEs256ClientSecretWithExpectedClaims() throws Exception {
    var jwt = SignedJWT.parse(new AppleClientSecretGenerator(SERVER.properties()).generate());
    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(AppleTestServer.KEY_ID);
    assertThat(jwt.verify(new ECDSAVerifier(SERVER.clientPublicKey()))).isTrue();
    var claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo(AppleTestServer.TEAM_ID);
    assertThat(claims.getSubject()).isEqualTo(AppleTestServer.CLIENT_ID);
    assertThat(claims.getAudience()).containsExactly(AppleOAuthProperties.ISSUER);
    assertThat(claims.getExpirationTime().toInstant().getEpochSecond() - claims.getIssueTime().toInstant().getEpochSecond()).isEqualTo(300);
  }

  @Test
  void missingKeyConfigurationFailsWithoutExposingPathOrSecrets() {
    var p = SERVER.properties();
    var broken = new AppleOAuthProperties(p.clientId(), p.teamId(), p.keyId(), "/does-not-exist/test-key.p8",
        p.tokenEncryptionKey(), p.jwksUri(), p.tokenUri(), p.revokeUri(), p.connectTimeout(), p.readTimeout());
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> new AppleClientSecretGenerator(broken).generate())
        .satisfies(error -> {
          assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_CONFIGURATION_ERROR);
          assertThat(error.getCause()).isNull();
          assertThat(error.getMessage()).doesNotContain("test-key.p8", p.teamId(), p.keyId());
        });
  }

  @Test
  void encryptedTokensAreRandomizedAuthenticatedAndBoundToAppleSubject() {
    var cipher = new AppleTokenCipher(SERVER.properties());
    String first = cipher.encrypt("refresh-token-value", "subject");
    String second = cipher.encrypt("refresh-token-value", "subject");
    assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token-value");
    assertThat(cipher.decrypt(first, "subject")).isEqualTo("refresh-token-value");
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> cipher.decrypt(first, "another-subject"));
    char replacement = first.charAt(6) == 'A' ? 'B' : 'A';
    String tampered = first.substring(0, 6) + replacement + first.substring(7);
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> cipher.decrypt(tampered, "subject"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-base64", "c2hvcnQ="})
  void rejectsMissingOrInvalidEncryptionKey(String key) {
    var p = SERVER.properties();
    var broken = new AppleOAuthProperties(p.clientId(), p.teamId(), p.keyId(), p.privateKeyPath(),
        key, p.jwksUri(), p.tokenUri(), p.revokeUri(), p.connectTimeout(), p.readTimeout());
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> new AppleTokenCipher(broken).validateConfiguration())
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_CONFIGURATION_ERROR));
  }

  @Test
  void sensitiveRecordsHaveRedactedStringRepresentations() {
    assertThat(new AppleLoginRequest("identity-value", "code-value", "nonce-value", "Name").toString())
        .doesNotContain("identity-value", "code-value", "nonce-value", "Name");
    assertThat(new AppleTokenResponse("access-value", "refresh-value", "identity-value").toString())
        .doesNotContain("access-value", "refresh-value", "identity-value");
    assertThat(SERVER.properties().toString()).doesNotContain(SERVER.properties().tokenEncryptionKey());
  }

  private void assertInvalid(String token) {
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> verifier.verify(token, "nonce"))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID));
  }
}
