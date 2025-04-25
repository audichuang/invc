package com.example.async.listener;

import com.example.async.model.TaskEvent;
import com.example.async.service.BondService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BondEventListener {
    private final BondService bondService;

    @KafkaListener(topics = "bond-events", groupId = "${spring.kafka.consumer.group-id}")
    public void handleTaskEvent(TaskEvent event) {
        log.info("債券系統 - 收到事件: {}", event);
        bondService.handleEvent(event);
    }
}