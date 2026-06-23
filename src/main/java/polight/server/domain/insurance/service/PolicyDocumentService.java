package polight.server.domain.insurance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;

@Service
@RequiredArgsConstructor
public class PolicyDocumentService {

  private final PolicyDocumentRepository policyDocumentRepository;
}
