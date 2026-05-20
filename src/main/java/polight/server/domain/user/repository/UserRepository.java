package polight.server.domain.user.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.entity.User.Provider;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
