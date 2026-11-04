package com.hongyun.asynctask.exception;

import com.hongyun.asynctask.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskNotFound(TaskNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>("TASK_NOT_FOUND", ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        fieldErrors));
    }

    @ExceptionHandler(InvalidTaskStateTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTaskStateTransition(
            InvalidTaskStateTransitionException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>("INVALID_TASK_STATE_TRANSITION", ex.getMessage(), null));
    }

    @ExceptionHandler(TaskRetryLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskRetryLimitExceeded(
            TaskRetryLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiResponse<>("Task_RETRY_LIMIT_EXCEED", ex.getMessage(), null)
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch (
            MethodArgumentTypeMismatchException ex) {
            String parameterName = ex.getName();
            Object parameterValue = ex.getValue();

            String message = "Invalid request parameter: " + parameterName + "=" + parameterValue;

            Class<?> requiredType = ex.getRequiredType();

            if(requiredType != null && requiredType.isEnum()) {
                String allowedValues = buildAllowedEnumValues(requiredType);
                message = message + ". Allowed values: " + allowedValues;
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ApiResponse<>(
                            "INVALID_REQUEST_PARAM",
                            message,
                            null)
            );
    }

    private String buildAllowedEnumValues(Class<?> enumType) {
        Object[] enumConstants = enumType.getEnumConstants();

        if (enumConstants == null || enumConstants.length == 0) {
            return "";
        }

        return Arrays.stream(enumConstants)
                .map(Object::toString)
                .collect((Collectors.joining(", ")));
    }

    @ExceptionHandler(ConcurrentTaskStateChangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentTaskStateChange(
            ConcurrentTaskStateChangeException ex
    ){
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                    new ApiResponse<>(
                            "CONCURRENT_TASK_STATE_CHANGE",
                             ex.getMessage(),
                            null
                    )
                );
    }

}