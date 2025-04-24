package com.example.async.controller;

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
public class TaskController {
    private final TaskService taskService;

    @PostMapping("/first-api")
    public ResponseEntity<String> initiateTask(@RequestBody TaskRequest taskRequest) {
        log.info("Received task request with correlationId: {}", taskRequest.getCorrelationId());
        taskService.processTaskAsync(taskRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Task initiated with correlationId: " + taskRequest.getCorrelationId());
    }

    @GetMapping(value = "/events/{correlationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(@PathVariable String correlationId) {
        log.info("SSE connection established for correlationId: {}", correlationId);
        return taskService.createSseEmitter(correlationId);
    }
}