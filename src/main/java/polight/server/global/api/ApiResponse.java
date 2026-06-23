package polight.server.global.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 응답")
public record ApiResponse<T>(
    @Schema(description = "성공 여부", example = "true") boolean success,
    @Schema(description = "응답 데이터") T data,
    @Schema(description = "응답 메시지", nullable = true, example = "null") String message) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> ok(T data, String message) {
    return new ApiResponse<>(true, data, message);
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(false, null, message);
  }
}
