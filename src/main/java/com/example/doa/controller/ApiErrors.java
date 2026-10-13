package com.example.doa.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> response(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"요청을 처리할 수 없습니다.":e.getReason()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","필수 항목과 입력 형식·길이를 확인하세요."));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("message","중복 데이터이거나 현재 처리할 수 없는 요청입니다."));}
    @ExceptionHandler(MailException.class) ResponseEntity<?> mail(){return ResponseEntity.status(503).body(Map.of("message","메일 발송을 사용할 수 없습니다. 관리자에게 문의하세요."));}
}
