package polight.server.global.security.oauth2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import polight.server.domain.user.entity.User;

@Getter
public class CustomOAuth2User implements OAuth2User {

  private final User user;
  private final Map<String, Object> attributes;

  public CustomOAuth2User(User user, Map<String, Object> attributes) {
    this.user = user;
    this.attributes = attributes;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(user.getRole().name()));
  }

  @Override
  public String getName() {
    return user.getId().toString();
  }
}
