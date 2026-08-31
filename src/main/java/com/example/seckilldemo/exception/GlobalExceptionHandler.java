package com.example.seckilldemo.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Map<String,Object> handleRuntimeException(RuntimeException e){
        Map<String,Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message",e.getMessage());
        return result;
    }
}
