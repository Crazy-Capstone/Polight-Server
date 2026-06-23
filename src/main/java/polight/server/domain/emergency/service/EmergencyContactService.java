package polight.server.domain.emergency.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.emergency.dto.EmergencyContactResponse;
import polight.server.domain.emergency.entity.EmergencyContact;
import polight.server.domain.emergency.repository.EmergencyContactRepository;
import polight.server.domain.policy.service.PolicyService;

@Service
@RequiredArgsConstructor
public class EmergencyContactService {
  private final EmergencyContactRepository contactRepository;
  private final PolicyService policyService;

  @Transactional(readOnly = true)
  public List<EmergencyContactResponse> getContacts(UUID userId, String countryCode, UUID policyId) {
    LinkedHashMap<String, EmergencyContact> unique = new LinkedHashMap<>();
    contactRepository.findByCountryCodeOrderByTypeAscNameAsc(countryCode.toUpperCase())
        .forEach(item -> unique.put(key(item), item));
    if (policyId != null) {
      policyService.requireOwnedPolicy(userId, policyId);
      contactRepository.findByPolicyIdOrderByTypeAscNameAsc(policyId)
          .forEach(item -> unique.putIfAbsent(key(item), item));
    }
    return unique.values().stream().map(item -> new EmergencyContactResponse(item.getId(), item.getType(),
        item.getName(), item.getPhone(), item.getDescription(), item.getInsurerName())).toList();
  }

  private String key(EmergencyContact item) {
    return item.getType() + "|" + item.getName() + "|" + item.getPhone();
  }
}
