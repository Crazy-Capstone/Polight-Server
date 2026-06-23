package polight.server.domain.notification.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  java.util.Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndReadAtIsNull(UUID userId);
}
