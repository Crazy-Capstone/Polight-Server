package polight.server.domain.insurance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.insurance.service.InsuranceDocumentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insurance-documents")
public class InsuranceDocumentController {

  private final InsuranceDocumentService insuranceDocumentService;
}
