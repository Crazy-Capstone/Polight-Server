package polight.server.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import polight.server.domain.auth.config.AppleOAuthProperties;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
@RequiredArgsConstructor
public class AppleTokenCipher {

  private static final SecureRandom RANDOM = new SecureRandom();
  private final AppleOAuthProperties properties;

  public void validateConfiguration() {
    key();
  }

  public String encrypt(String token, String subject) {
    try {
      byte[] nonce = new byte[12];
      RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(aad(subject));
      byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
      byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
      System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
      return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR);
    }
  }

  public String decrypt(String encrypted, String subject) {
    try {
      if (encrypted == null || !encrypted.startsWith("v1.")) {
        throw new IllegalArgumentException();
      }
      byte[] payload = Base64.getUrlDecoder().decode(encrypted.substring(3));
      if (payload.length < 29) {
        throw new IllegalArgumentException();
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOf(payload, 12)));
      cipher.updateAAD(aad(subject));
      return new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR);
    }
  }

  private SecretKey key() {
    try {
      if (properties.tokenEncryptionKey() == null || properties.tokenEncryptionKey().isBlank()) {
        throw new IllegalArgumentException();
      }
      byte[] key = Base64.getDecoder().decode(properties.tokenEncryptionKey());
      if (key.length != 32) {
        throw new IllegalArgumentException();
      }
      return new SecretKeySpec(key, "AES");
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR);
    }
  }

  private byte[] aad(String subject) {
    return ("apple:" + properties.clientId() + ":" + subject).getBytes(StandardCharsets.UTF_8);
  }
}
