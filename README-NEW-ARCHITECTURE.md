# 基金債券處理系統 - 部署與配置指南

## 架構概覽

```
前端應用(4200) -> 主反向代理(8000) -> 集群代理1(8081) -> 基金系統實例(動態端口)
                                   -> 集群代理2(8082) -> 基金系統實例(動態端口)
                                                   ↓
                                            Redis事件中心(6379)
```

## 技術架構特點

### 1. 三層代理架構
- **主反向代理** (端口 8000): 統一入口，基於Header的集群路由
- **集群代理1** (端口 8081): 管理cluster-1集群實例
- **集群代理2** (端口 8082): 管理cluster-2集群實例

### 2. 動態POD身份管理
- 自動基於主機名+端口生成唯一POD標識
- 支援多主機部署相同端口的場景
- 無需手動配置POD ID

### 3. Redis事件驅動機制
- 使用Redis Pub/Sub實現跨實例事件廣播
- 智能事件路由：只有對應實例處理其負責的SSE連接
- SSE連接映射存儲在Redis中，24小時自動過期

## 系統啟動

### 前置需求
1. **Redis 6.2+** 已啟動 (localhost:6379)
2. **Maven 3.9+** 已安裝
3. **Java 17+** 已安裝

### 快速啟動
```bash
# 檢查Redis版本是否兼容
./check-redis-version.sh

# 啟動所有服務
./start-all.sh
```

### 手動啟動服務

#### 1. 主反向代理
```bash
cd main-proxy
mvn spring-boot:run
# 啟動在端口 8000
```

#### 2. 集群代理
```bash
# 集群代理1
cd fund-proxy
mvn spring-boot:run -Dspring-boot.run.profiles=proxy1

# 集群代理2
cd fund-proxy  
mvn spring-boot:run -Dspring-boot.run.profiles=proxy2
```

#### 3. 基金系統實例

**集群1部署**：
```bash
cd fund-system

# 實例1
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster1.properties --server.port=9090"

# 實例2  
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster1.properties --server.port=9091"
```

**集群2部署**：
```bash
cd fund-system

# 實例3
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster2.properties --server.port=9092"

# 實例4
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster2.properties --server.port=9093"
```

### 前端應用
```bash
cd frontend
npm install
ng serve
# 啟動在端口 4200
```

## 系統驗證

### 1. 健康檢查
```bash
# 主反向代理
curl http://localhost:8000/api/health

# 集群代理
curl http://localhost:8081/api/health
curl http://localhost:8082/api/health

# 基金系統實例（檢查POD身份）
curl http://localhost:9090/api/health
curl http://localhost:9091/api/health
```

### 2. Redis連接測試
```bash
redis-cli ping
# 應返回 PONG

# 檢查Redis監聽
redis-cli monitor
```

### 3. SSE連接測試
```bash
# 建立SSE連接（cluster1）
curl -N -H "X-Cluster-Route: cluster1" \
  -H "Accept: text/event-stream" \
  "http://localhost:8000/api/fund-events?correlationId=test-123&taskIds=task1,task2"

# 另開終端提交任務
curl -X POST http://localhost:8000/api/fund-api \
  -H "Content-Type: application/json" \
  -H "X-Cluster-Route: cluster1" \
  -d '{"correlationId":"test-123","numberOfSubtasks":3}'
```

## 路由機制

### 集群路由規則
| Header | 目標集群 | 代理端口 | 實例端口 |
|--------|----------|----------|----------|
| `X-Cluster-Route: cluster1` | 集群1 | 8081 | 9090,9091 |
| `X-Cluster-Route: cluster2` | 集群2 | 8082 | 9092,9093 |
| 無Header | 輪詢負載均衡 | 8081,8082 | 所有實例 |

### 前端配置
```typescript
// fund-bond.service.ts
private baseUrl = 'http://localhost:8000/api'; // 主反向代理

// 自動添加集群路由Header
private httpOptions = {
  headers: new HttpHeaders({
    'Content-Type': 'application/json',
    'X-Cluster-Route': 'cluster1' // 可動態設定
  })
};
```

## POD身份管理

### 自動生成規則
系統會根據運行環境自動生成POD標識：

```
主機名: host-app-01.company.com, 端口: 9090
-> POD ID: pod-app01-9090

主機名: k8s-worker-123, 端口: 9092  
-> POD ID: pod-worker123-9092

主機名: localhost, 端口: 9090
-> POD ID: pod-localhost-9090
```

### 配置檔說明
- `application-cluster1.properties`: cluster-1配置
- `application-cluster2.properties`: cluster-2配置

主要差異：
```properties
# cluster1
app.cluster.id=cluster-1

# cluster2  
app.cluster.id=cluster-2
```

## Redis事件流程

### 事件發布與訂閱
```
1. 任務執行 -> 發布事件到 task-events 頻道
2. 所有實例收到事件廣播
3. 每個實例檢查 sse-connections:{id} 映射
4. 只有負責該連接的實例處理事件
5. 推送到對應的SSE連接
```

### 連接映射格式
```
Redis Key: sse-connections:{connectionId}
Redis Value: {podId}:{clusterId}
TTL: 24小時
```

## 生產部署

### Docker容器化
```dockerfile
FROM openjdk:17-jre-slim
COPY fund-system.jar app.jar
COPY application-cluster*.properties /config/
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes部署
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fund-system-cluster1
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: fund-system
        image: fund-system:latest
        args:
        - --spring.config.additional-location=classpath:application-cluster1.properties
        - --server.port=9090
        env:
        - name: HOSTNAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
```

## 監控與診斷

### 日誌關鍵信息
- POD身份生成: `🏷️ 自動生成POD身份`
- SSE連接註冊: `📡 SSE連接已註冊`
- 事件路由決策: `🎯 事件歸屬檢查`
- Redis連接狀態: `🔗 Redis連接狀態`

### 故障排除

**常見問題**：
1. **POD ID衝突**: 檢查主機名是否重複
2. **Redis連接失敗**: 確認Redis服務狀態
3. **SSE事件遺失**: 檢查Redis事件訂閱狀態
4. **集群路由錯誤**: 驗證Header設定

**診斷命令**：
```bash
# 檢查Redis連接數
redis-cli info clients

# 監控Redis事件
redis-cli monitor | grep task-events

# 檢查SSE連接映射
redis-cli keys "sse-connections:*"
```

## 系統優勢

### 技術優勢
1. **動態POD管理**: 無需手動配置，自動適應部署環境
2. **智能事件路由**: 精確的跨實例事件分發
3. **水平擴展能力**: 輕鬆添加新實例和集群
4. **故障隔離**: 集群間故障不會相互影響

### 維運優勢  
1. **統一配置管理**: 集群級配置檔，部署簡化
2. **實時監控**: 完整的健康檢查和指標監控
3. **彈性部署**: 支援裸機、容器、K8s等多種環境
4. **故障自恢復**: 自動重連和資源清理機制 