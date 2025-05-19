package com.example.async.controller;

import com.example.async.model.SseRequest;
import com.example.async.model.TaskRequest;
import com.example.async.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin("*")
public class FundController {
    private final TaskService taskService;

    @PostMapping("/fund-api")
    public ResponseEntity<String> initiateTask(@RequestBody TaskRequest taskRequest) {
        log.info("收到任務請求，關聯 ID: {}", taskRequest.getCorrelationId());
        taskService.processTaskAsync(taskRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("任務已啟動，關聯 ID: " + taskRequest.getCorrelationId());
    }

    @PostMapping(value = "/fund-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(@RequestBody SseRequest sseRequest) {
        String correlationId = sseRequest.getCorrelationId();
        log.info("為關聯 ID {} 建立 SSE 連線 (POST, body)", correlationId);
        return taskService.createSseEmitter(correlationId);
    }
}