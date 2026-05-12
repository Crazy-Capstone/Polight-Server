package polight.server.domain.insurance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import polight.server.domain.insurance.repository.InsuranceDocumentRepository;

@Service
@RequiredArgsConstructor
public class InsuranceDocumentService {

  private final InsuranceDocumentRepository insuranceDocumentRepository;
}
