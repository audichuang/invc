//package com.example.async.controller;
//
//import com.example.async.model.TaskRequest;
//import com.example.async.service.BondService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
//
//@RestController
//@RequestMapping("/api")
//@RequiredArgsConstructor
//@Slf4j
//@CrossOrigin("*")
//public class BondController {
//    private final BondService bondService;
//
//    @PostMapping("/bond-api")
//    public ResponseEntity<String> initiateTask(@RequestBody TaskRequest taskRequest) {
//        log.info("收到債券任務請求，關聯 ID: {}", taskRequest.getCorrelationId());
//        bondService.processTaskAsync(taskRequest);
//        return ResponseEntity.status(HttpStatus.ACCEPTED)
//                .body("債券任務已啟動，關聯 ID: " + taskRequest.getCorrelationId());
//    }
//
//    @GetMapping(value = "/bond-events/{correlationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public SseEmitter subscribeToEvents(@PathVariable String correlationId) {
//        log.info("為債券系統關聯 ID {} 建立 SSE 連線", correlationId);
//        return bondService.createSseEmitter(correlationId);
//    }
//}