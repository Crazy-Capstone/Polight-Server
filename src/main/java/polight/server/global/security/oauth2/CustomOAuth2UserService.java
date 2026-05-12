package polight.server.global.security.oauth2;

import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.user.entity.AuthProvider;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.entity.UserRole;
import polight.server.domain.user.repository.UserRepository;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;

  public CustomOAuth2UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oauth2User = super.loadUser(userRequest);
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    if (!"kakao".equals(registrationId)) {
      throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
    }

    Map<String, Object> attributes = oauth2User.getAttributes();
    String providerId = String.valueOf(attributes.get("id"));
    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
    String email = (String) kakaoAccount.get("email");
    String nickname = (String) profile.get("nickname");

    User user = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)
        .map(existing -> {
          existing.updateName(nickname);
          return existing;
        })
        .orElseGet(() -> userRepository.save(User.builder()
            .email(email)
            .name(nickname)
            .provider(AuthProvider.KAKAO)
            .providerId(providerId)
            .role(UserRole.ROLE_USER)
            .build()));

    return new CustomOAuth2User(user, attributes);
  }
}
