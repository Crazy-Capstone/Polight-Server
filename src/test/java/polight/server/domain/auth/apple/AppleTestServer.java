package polight.server.domain.auth.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import polight.server.domain.auth.config.AppleOAuthProperties;

public final class AppleTestServer implements AutoCloseable {

  public static final String CLIENT_ID = "com.polight.app";
  public static final String TEAM_ID = "TESTTEAM01";
  public static final String KEY_ID = "TESTKEY001";
  public final AtomicInteger jwksCalls = new AtomicInteger();
  public final AtomicInteger tokenCalls = new AtomicInteger();
  public final AtomicInteger revokeCalls = new AtomicInteger();
  public final AtomicInteger tokenStatus = new AtomicInteger(200);
  public final AtomicInteger jwksStatus = new AtomicInteger(200);
  public final AtomicInteger revokeStatus = new AtomicInteger(200);
  public final AtomicInteger revokeFailures = new AtomicInteger();
  public final AtomicReference<Map<String, String>> lastTokenForm = new AtomicReference<>();
  public final AtomicReference<Map<String, String>> lastRevokeForm = new AtomicReference<>();
  public String tokenError = "invalid_grant";
  public String errorDescription = "test error";

  private final HttpServer server;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Grant> grants = new ConcurrentHashMap<>();
  private final KeyPair clientKey;
  private final Path keyFile;
  private final String encryptionKey;
  private RSAKey signingKey;

  public AppleTestServer() {
    try {
      rotateKey();
      var generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      clientKey = generator.generateKeyPair();
      keyFile = Files.createTempFile("polight-apple-test-", ".p8");
      Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n"
          + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(clientKey.getPrivate().getEncoded())
          + "\n-----END PRIVATE KEY-----\n");
      byte[] key = new byte[32];
      new SecureRandom().nextBytes(key);
      encryptionKey = Base64.getEncoder().encodeToString(key);
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/keys", exchange -> {
        jwksCalls.incrementAndGet();
        send(exchange, jwksStatus.get(), new JWKSet(signingKey.toPublicJWK()).toString());
      });
      server.createContext("/token", this::exchangeCode);
      server.createContext("/revoke", this::revoke);
      server.start();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not initialize Apple test server", exception);
    }
  }

  public AppleOAuthProperties properties() {
    return new AppleOAuthProperties(CLIENT_ID, TEAM_ID, KEY_ID, keyFile.toString(), encryptionKey,
        url("/keys"), url("/token"), url("/revoke"), Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  public String url(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  public void reset() {
    jwksCalls.set(0);
    tokenCalls.set(0);
    revokeCalls.set(0);
    tokenStatus.set(200);
    jwksStatus.set(200);
    revokeStatus.set(200);
    revokeFailures.set(0);
    lastTokenForm.set(null);
    lastRevokeForm.set(null);
    grants.clear();
    tokenError = "invalid_grant";
    errorDescription = "test error";
  }

  public void rotateKey() {
    try {
      signingKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  public String identity(String subject, String nonce) {
    return identity(subject, nonce, builder -> {});
  }

  public String identity(String subject, String nonce, Consumer<JWTClaimsSet.Builder> customize) {
    try {
      var builder = new JWTClaimsSet.Builder().issuer(AppleOAuthProperties.ISSUER).audience(CLIENT_ID)
          .subject(subject).issueTime(Date.from(Instant.now().minusSeconds(1)))
          .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("nonce", hash(nonce))
          .claim("email", "ignored-email@example.invalid");
      customize.accept(builder);
      var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), builder.build());
      jwt.sign(new RSASSASigner(signingKey));
      return jwt.serialize();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  public void registerCode(String code, String subject, String nonce) {
    registerResponse(code, identity(subject, nonce), "apple-refresh-" + code);
  }

  public void registerResponse(String code, String identityToken, String refreshToken) {
    grants.put(code, new Grant(identityToken, refreshToken));
  }

  public ECPublicKey clientPublicKey() {
    return (ECPublicKey) clientKey.getPublic();
  }

  public static String hash(String nonce) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void exchangeCode(HttpExchange exchange) throws IOException {
    tokenCalls.incrementAndGet();
    var form = form(exchange);
    lastTokenForm.set(form);
    if (tokenStatus.get() != 200) {
      send(exchange, tokenStatus.get(), mapper.writeValueAsString(Map.of("error", tokenError, "error_description", errorDescription)));
      return;
    }
    if (!validClient(form) || !"authorization_code".equals(form.get("grant_type"))) {
      send(exchange, 400, "{\"error\":\"invalid_client\"}");
      return;
    }
    var grant = grants.remove(form.get("code"));
    if (grant == null) {
      send(exchange, 400, "{\"error\":\"invalid_grant\"}");
      return;
    }
    var response = new HashMap<String, Object>();
    response.put("access_token", "apple-test-access");
    response.put("id_token", grant.identityToken());
    response.put("refresh_token", grant.refreshToken());
    send(exchange, 200, mapper.writeValueAsString(response));
  }

  private void revoke(HttpExchange exchange) throws IOException {
    revokeCalls.incrementAndGet();
    var form = form(exchange);
    lastRevokeForm.set(form);
    if (!validClient(form)) {
      send(exchange, 400, "{\"error\":\"invalid_client\"}");
      return;
    }
    int status = revokeFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0 ? 503 : revokeStatus.get();
    send(exchange, status, status == 200 ? "{}" : "{\"error\":\"temporarily_unavailable\"}");
  }

  private boolean validClient(Map<String, String> form) {
    try {
      var jwt = SignedJWT.parse(form.get("client_secret"));
      if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm()) || !KEY_ID.equals(jwt.getHeader().getKeyID())
          || !jwt.verify(new ECDSAVerifier(clientPublicKey()))) {
        return false;
      }
      var claims = jwt.getJWTClaimsSet();
      return CLIENT_ID.equals(form.get("client_id")) && TEAM_ID.equals(claims.getIssuer())
          && CLIENT_ID.equals(claims.getSubject()) && claims.getAudience().contains(AppleOAuthProperties.ISSUER)
          && claims.getIssueTime().toInstant().isBefore(Instant.now().plusSeconds(1))
          && claims.getExpirationTime().toInstant().isAfter(Instant.now())
          && Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()).getSeconds() == 300;
    } catch (Exception exception) {
      return false;
    }
  }

  private Map<String, String> form(HttpExchange exchange) throws IOException {
    var values = new HashMap<String, String>();
    for (String part : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
      String[] pair = part.split("=", 2);
      values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
          pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
    }
    return values;
  }

  private void send(HttpExchange exchange, int status, String body) throws IOException {
    try (exchange) {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(0);
    try {
      Files.deleteIfExists(keyFile);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not remove generated test key", exception);
    }
  }

  private record Grant(String identityToken, String refreshToken) {}
}
