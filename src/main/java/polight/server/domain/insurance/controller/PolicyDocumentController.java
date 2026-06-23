package polight.server.domain.insurance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.insurance.service.PolicyDocumentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/policy-documents")
public class PolicyDocumentController {

  private final PolicyDocumentService policyDocumentService;
}
