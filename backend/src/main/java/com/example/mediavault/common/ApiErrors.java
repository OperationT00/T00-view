package com.example.mediavault.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    public record Error(String code, String message) {}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> api(ApiException e) { return ResponseEntity.status(e.status()).body(new Error(e.code(),e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ResponseEntity<Error> input(Exception e) { return ResponseEntity.badRequest().body(new Error("INVALID_INPUT","请求参数不符合要求")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> constraint(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new Error("CONFLICT","记录重复或仍被其他记录引用"));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Error> forbidden(AccessDeniedException e) { return ResponseEntity.status(403).body(new Error("FORBIDDEN","没有执行此操作的权限")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected(Exception e) {
        // Exception messages can contain SQL parameters, paths and credentials.
        log.error("Request failed: {}",e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new Error("INTERNAL_ERROR","服务暂时不可用，请稍后重试"));
    }
}
