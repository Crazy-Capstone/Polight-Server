package polight.server.global.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import polight.server.global.api.ApiResponse;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
    return ResponseEntity.status(exception.getErrorCode().status())
        .body(ApiResponse.error(exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
        .orElse(ErrorCode.VALIDATION_ERROR.message());
    return ResponseEntity.badRequest().body(ApiResponse.error(message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
    return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status())
        .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.message()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(ApiResponse.error(exception.getReason() == null ? exception.getMessage() : exception.getReason()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
    return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.status())
        .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.message()));
  }
}
