package com.example.async.listener;

import com.example.async.model.TaskEvent;
import com.example.async.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventListener {
    private final TaskService taskService;

    @KafkaListener(topics = "task-events", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(TaskEvent event) {
        log.info("從 Kafka 收到事件: {}", event);
        taskService.handleEvent(event);
    }
}