package polight.server.global.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "페이지 응답")
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages());
  }
}
