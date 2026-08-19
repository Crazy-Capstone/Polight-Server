package polight.server.domain.concern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.concern.dto.ConcernResponse;
import polight.server.domain.concern.entity.Concern;

@RestController
@RequestMapping("/api/v1/concerns")
@Tag(name = "Concern", description = "걱정되는 상황 목록 API")
public class ConcernController {

  @GetMapping
  @Operation(summary = "걱정되는 상황 목록", description = "배열 순서가 화면에 보여줄 순서입니다. 대분류는 프론트에서 묶습니다.")
  public List<ConcernResponse> getConcerns() {
    return Arrays.stream(Concern.values()).map(ConcernResponse::from).toList();
  }
}
