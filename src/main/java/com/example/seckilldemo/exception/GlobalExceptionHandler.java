package com.example.seckilldemo.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String,Object>> handleBusinessException(BusinessException e){
        Map<String,Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message",e.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleUnexpectedException(Exception e){
        log.error("未处理的服务异常", e);
        Map<String,Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "服务暂时不可用");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
