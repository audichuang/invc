package com.example.async.service;

import com.example.async.model.TaskEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
public class RedisEventService implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String podId;
    private final String clusterId;

    public RedisEventService(RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            ApplicationContext applicationContext,
            String podId,
            @Value("${app.cluster.id}") String clusterId) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.applicationContext = applicationContext;
        this.podId = podId;
        this.clusterId = clusterId;
    }

    private static final String EVENT_CHANNEL = "task-events";
    private static final String SSE_REGISTRY_KEY = "sse-connections";

    @PostConstruct
    public void init() {
        // 訂閱事件頻道
        listenerContainer.addMessageListener(this, new ChannelTopic(EVENT_CHANNEL));
        log.info("Redis事件監聽器已啟動，POD: {}, Cluster: {}", podId, clusterId);
    }

    /**
     * 發布事件到Redis
     */
    public void publishEvent(TaskEvent event) {
        try {
            // 直接發送對象，讓RedisTemplate處理序列化
            redisTemplate.convertAndSend(EVENT_CHANNEL, event);
            log.info("已發布事件到Redis: {}", event);
        } catch (Exception e) {
            log.error("發布事件失敗: {}", event, e);
        }
    }

    /**
     * 註冊SSE連接
     */
    public void registerSseConnection(String sseConnectionId, String podId, String clusterId) {
        String key = SSE_REGISTRY_KEY + ":" + sseConnectionId;
        String connectionInfo = podId + ":" + clusterId;
        redisTemplate.opsForValue().set(key, connectionInfo);
        redisTemplate.expire(key, java.time.Duration.ofHours(24)); // 24小時過期
        log.info("已註冊SSE連接: {} -> {}", sseConnectionId, connectionInfo);
    }

    /**
     * 移除SSE連接註冊
     */
    public void unregisterSseConnection(String sseConnectionId) {
        String key = SSE_REGISTRY_KEY + ":" + sseConnectionId;
        redisTemplate.delete(key);
        log.info("已移除SSE連接註冊: {}", sseConnectionId);
    }

    /**
     * 檢查SSE連接是否在當前POD
     */
    public boolean isSseConnectionLocal(String sseConnectionId) {
        String key = SSE_REGISTRY_KEY + ":" + sseConnectionId;
        String connectionInfo = (String) redisTemplate.opsForValue().get(key);

        if (connectionInfo == null) {
            return false;
        }

        String expectedInfo = podId + ":" + clusterId;
        return expectedInfo.equals(connectionInfo);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String eventJson = new String(message.getBody());
            log.debug("收到Redis原始消息: {}", eventJson);

            // 創建新的ObjectMapper，避免類型信息問題
            ObjectMapper mapper = new ObjectMapper();
            TaskEvent event = mapper.readValue(eventJson, TaskEvent.class);

            log.info("收到Redis事件: {}, 當前POD: {}", event, podId);

            // 提取SSE連接ID
            String sseConnectionId = extractSseConnectionIdFromSingleTaskId(event.getCorrelationId());

            // 檢查此SSE連接是否在當前POD
            if (isSseConnectionLocal(sseConnectionId)) {
                log.info("事件對應的SSE連接在當前POD，處理事件: {}", event);
                // 延遲獲取TaskService以避免循環依賴
                TaskService taskService = applicationContext.getBean(TaskService.class);
                taskService.handleEvent(event);
            } else {
                log.debug("事件對應的SSE連接不在當前POD，跳過處理: {}", event);
            }

        } catch (Exception e) {
            log.error("處理Redis事件失敗: {}", new String(message.getBody()), e);
        }
    }

    private String extractSseConnectionIdFromSingleTaskId(String singleTaskId) {
        if (singleTaskId == null)
            return null;

        // 使用與TaskService相同的邏輯
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(.*)-[^-]+$");
        java.util.regex.Matcher matcher = pattern.matcher(singleTaskId);

        if (matcher.matches()) {
            return matcher.group(1);
        }

        log.warn("無法從任務ID {} 提取SSE連接ID，使用原始ID", singleTaskId);
        return singleTaskId;
    }
}