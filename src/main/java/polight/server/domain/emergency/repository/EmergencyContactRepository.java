package polight.server.domain.emergency.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.emergency.entity.EmergencyContact;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {

  List<EmergencyContact> findByCountryCodeOrderByTypeAscNameAsc(String countryCode);

  List<EmergencyContact> findByPolicyIdOrderByTypeAscNameAsc(UUID policyId);
}
