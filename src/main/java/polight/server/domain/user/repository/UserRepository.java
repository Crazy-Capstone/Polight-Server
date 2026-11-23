package polight.server.domain.user.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.entity.User.Provider;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :userId")
  Optional<User> findByIdForWithdrawal(@Param("userId") UUID userId);
}
