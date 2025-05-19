package com.example.async.service;

import com.example.async.model.TaskEvent;
import com.example.async.model.TaskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BondService {
    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatFutureMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sseConnectionTaskIdsMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sseConnectionCompletedTasksMap = new ConcurrentHashMap<>();
    private static final String EVENT_TOPIC = "bond-events";
    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER = Executors.newScheduledThreadPool(2);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^(.*?)-\\d+$");
    private static final Pattern SSE_CONNECTION_ID_EXTRACTOR_PATTERN = Pattern.compile("^(.*)-[^-]+$");

    public SseEmitter createSseEmitter(String sseConnectionId, List<String> taskIds) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        if (taskIds != null && !taskIds.isEmpty()) {
            log.info("債券系統 - SSE 連線 {} 將追蹤任務 IDs: {}", sseConnectionId, taskIds);
            sseConnectionTaskIdsMap.put(sseConnectionId, new ArrayList<>(taskIds));
            sseConnectionCompletedTasksMap.put(sseConnectionId, new HashSet<>());
        } else {
            log.info("債券系統 - SSE 連線 {} 不追蹤特定任務 IDs (或 taskIds 為空)", sseConnectionId);
        }

        emitter.onCompletion(() -> {
            log.info("債券系統 - 關聯 ID 為 {} 的 SSE 連線已完成", sseConnectionId);
            cleanupSseResources(sseConnectionId);
        });

        emitter.onTimeout(() -> {
            log.info("債券系統 - 關聯 ID 為 {} 的 SSE 連線超時", sseConnectionId);
            cleanupSseResources(sseConnectionId);
            emitter.complete();
        });

        emitter.onError(ex -> {
            log.error("債券系統 - 關聯 ID 為 {} 的 SSE 發生錯誤", sseConnectionId, ex);
            cleanupSseResources(sseConnectionId);
            emitter.complete();
        });

        try {
            TaskEvent connectEvent = TaskEvent.builder()
                    .correlationId(sseConnectionId)
                    .status("CONNECTED")
                    .message("債券系統 SSE連接已建立")
                    .finalEvent(false)
                    .build();

            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(connectEvent));

            sseEmitterMap.put(sseConnectionId, emitter);
            startHeartbeat(sseConnectionId);
            log.info("債券系統 - 已為關聯 ID {} 添加 SSE Emitter 到映射中", sseConnectionId);
        } catch (IOException e) {
            log.error("債券系統 - 向關聯 ID 為 {} 的 SSE 發送初始事件時出錯", sseConnectionId, e);
            cleanupSseResources(sseConnectionId);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void startHeartbeat(String correlationId) {
        log.info("債券系統 - 啟動心跳機制，關聯 ID: {}, 心跳間隔: {}秒", correlationId, HEARTBEAT_INTERVAL_SECONDS);
        ScheduledFuture<?> future = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(() -> {
            SseEmitter emitter = sseEmitterMap.get(correlationId);
            if (emitter != null) {
                try {
                    log.debug("債券系統 - 發送心跳到 SSE 連線，關聯 ID: {}", correlationId);
                    TaskEvent heartbeatEvent = TaskEvent.builder()
                            .correlationId(correlationId)
                            .status("HEARTBEAT")
                            .message("債券系統心跳檢測")
                            .finalEvent(false)
                            .build();
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(System.currentTimeMillis()))
                            .name("HEARTBEAT")
                            .data(heartbeatEvent)
                            .reconnectTime(0));
                } catch (IOException e) {
                    log.error("債券系統 - 發送心跳到關聯 ID 為 {} 的 SSE 時出錯: {}", correlationId, e.getMessage());
                    cleanupSseResources(correlationId);
                }
            } else {
                log.info("債券系統 - 找不到關聯 ID 為 {} 的 SSE emitter，停止心跳", correlationId);
                stopHeartbeat(correlationId);
            }
        }, 2, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        heartbeatFutureMap.put(correlationId, future);
    }

    private void stopHeartbeat(String correlationId) {
        ScheduledFuture<?> future = heartbeatFutureMap.remove(correlationId);
        if (future != null) {
            future.cancel(false);
            log.info("債券系統 - 已停止關聯 ID 為 {} 的心跳", correlationId);
        }
    }

    private void cleanupSseResources(String sseConnectionId) {
        sseEmitterMap.remove(sseConnectionId);
        stopHeartbeat(sseConnectionId);
        sseConnectionTaskIdsMap.remove(sseConnectionId);
        sseConnectionCompletedTasksMap.remove(sseConnectionId);
        log.info("債券系統 - 已清理 SSE 連線 {} 的所有相關資源", sseConnectionId);
    }

    @Async
    public void processTaskAsync(TaskRequest request) {
        String correlationId = request.getCorrelationId();
        log.info("債券系統 - 開始處理關聯 ID 為 {} 的異步任務", correlationId);
        try {
            publishEvent(TaskEvent.builder().correlationId(correlationId).status("PROCESSING").message("債券任務已開始處理")
                    .finalEvent(false).build());
            for (int i = 0; i < request.getNumberOfSubtasks(); i++) {
                int sleepSeconds = ThreadLocalRandom.current().nextInt(2, 10);
                TimeUnit.SECONDS.sleep(sleepSeconds);
                executeSubtask(correlationId, i);
            }
            publishEvent(TaskEvent.builder().correlationId(correlationId).status("COMPLETED").message("所有債券任務已完成")
                    .finalEvent(true).build());
        } catch (Exception e) {
            log.error("債券系統 - 處理關聯 ID 為 {} 的任務時出錯", correlationId, e);
            publishEvent(TaskEvent.builder().correlationId(correlationId).status("FAILED")
                    .message("債券任務處理失敗: " + e.getMessage()).finalEvent(true).build());
        }
    }

    private void executeSubtask(String correlationId, int subtaskId) {
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(500, 2000));
            publishEvent(TaskEvent.builder().correlationId(correlationId).status("SUBTASK_COMPLETED")
                    .message("債券子任務 " + subtaskId + " 已完成").result("子任務 " + subtaskId + " 的結果").finalEvent(false)
                    .build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("債券系統 - 關聯 ID 為 {} 的子任務被中斷", correlationId, e);
        }
    }

    private void publishEvent(TaskEvent event) {
        try {
            log.info("債券系統 - 向 Kafka 發布事件: {}", event);
            kafkaTemplate.send(EVENT_TOPIC, event.getCorrelationId(), event);
        } catch (Exception e) {
            log.error("債券系統 - 發布事件到 Kafka 失敗: {}", e.getMessage(), e);
        }
    }

    private String extractSseConnectionIdFromSingleTaskId(String singleTaskId) {
        if (singleTaskId == null)
            return null;
        Matcher matcher = SSE_CONNECTION_ID_EXTRACTOR_PATTERN.matcher(singleTaskId);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        log.warn("債券系統 - 無法從 {} 中提取 SSE 連線 ID，將使用原 ID。", singleTaskId);
        return singleTaskId;
    }

    public void handleEvent(TaskEvent event) {
        String singleTaskId = event.getCorrelationId();
        log.info("債券系統 - Kafka 監聽器收到事件，單任務 ID: {}, 狀態: {}", singleTaskId, event.getStatus());

        String sseConnectionId = extractSseConnectionIdFromSingleTaskId(singleTaskId);
        if (sseConnectionId == null) {
            log.error("債券系統 - 無法從單任務 ID {} 提取 SSE 連線 ID，忽略事件。", singleTaskId);
            return;
        }
        log.info("債券系統 - 從單任務 ID {} 提取到 SSE 連線 ID: {}", singleTaskId, sseConnectionId);

        SseEmitter emitter = sseEmitterMap.get(sseConnectionId);
        if (emitter != null) {
            try {
                log.debug("債券系統 - 向 SSE 連線 {} 發送事件: {}", sseConnectionId, event);
                emitter.send(SseEmitter.event()
                        .id(singleTaskId + "-" + System.currentTimeMillis())
                        .name(event.getStatus())
                        .data(event));
                log.info("債券系統 - 已向 SSE 連線 {} 發送事件，單任務 ID: {}, 狀態: {}", sseConnectionId, singleTaskId,
                        event.getStatus());

                List<String> trackedTaskIds = sseConnectionTaskIdsMap.get(sseConnectionId);
                if (trackedTaskIds != null && !trackedTaskIds.isEmpty()) {
                    if (event.isFinalEvent()) {
                        log.info("債券系統 - 單任務 {} (屬於 SSE 連線 {}) 已完成 (finalEvent=true)", singleTaskId, sseConnectionId);
                        Set<String> completedTasks = sseConnectionCompletedTasksMap.computeIfAbsent(sseConnectionId,
                                k -> new HashSet<>());
                        completedTasks.add(singleTaskId);
                        log.info("債券系統 - SSE 連線 {} 的已完成任務列表: {}", sseConnectionId, completedTasks);

                        if (completedTasks.containsAll(trackedTaskIds) && trackedTaskIds.containsAll(completedTasks)) {
                            log.info("債券系統 - SSE 連線 {} 的所有追蹤任務均已完成。準備關閉 SSE 連線。", sseConnectionId);
                            emitter.send(SseEmitter.event().name("ALL_TASKS_COMPLETED").data(
                                    TaskEvent.builder()
                                            .correlationId(sseConnectionId)
                                            .status("ALL_TASKS_COMPLETED")
                                            .message("所有為此SSE連線追蹤的債券任務已處理完畢")
                                            .finalEvent(true)
                                            .build()));
                            emitter.complete();
                        } else {
                            log.info("債券系統 - SSE 連線 {} 尚有未完成的任務。追蹤: {}, 已完成: {}", sseConnectionId, trackedTaskIds,
                                    completedTasks);
                        }
                    }
                } else if (event.isFinalEvent()) {
                    log.info("債券系統 - SSE 連線 {} 不追蹤特定任務列表或收到針對整個連線的 finalEvent。單任務 {} 完成，準備關閉 SSE 連線。", sseConnectionId,
                            singleTaskId);
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("債券系統 - 向 SSE 連線 {} 發送事件 {} 時出錯: {}", sseConnectionId, event, e.getMessage(), e);
            } catch (Exception e) {
                log.error("債券系統 - 處理 SSE 連線 {} 的事件 {} 時發生意外錯誤: {}", sseConnectionId, event, e.getMessage(), e);
            }
        } else {
            log.warn("債券系統 - 找不到 SSE 連線 ID {} 對應的 SseEmitter。事件 {} 可能無法發送。", sseConnectionId, event);
        }
    }
}