package com.example.pikpak.web;

import com.example.pikpak.PikPakException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@Order(-100)
@RestControllerAdvice(assignableTypes=PikPakController.class)
public class PikPakErrors {
    @ExceptionHandler(PikPakException.class)
    ResponseEntity<?> error(PikPakException e) {
        int status=switch(e.code()) {case NOT_FOUND->404;case CONFLICT->409;case INVALID_INPUT,INVALID_CURSOR->400;case UPSTREAM_RATE_LIMITED->429;default->502;};
        return ResponseEntity.status(status).body(Map.of("code",e.code().name(),"message",e.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> input(Exception e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","请求参数不符合要求"));}
}
