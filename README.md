# 非同步任務處理與 SSE 示範專案

這個專案展示了如何使用 Spring Boot 和 Angular 實現非同步任務處理和伺服器推送事件 (SSE)。

## 專案架構

此專案包含四個主要部分：

1. **前端 (Angular 18)**：處理用戶介面和 SSE 連接
2. **後端 (Spring Boot, Java 17)**：處理 HTTP 請求、異步任務和 SSE 連接管理
3. **Kafka**：作為消息代理，處理事件發布和訂閱
4. **反向代理**：用於模擬雙 Pod 負載均衡環境

## 功能流程

1. 前端生成 correlationId 並同時發送 HTTP 請求和建立 SSE 連接
2. 反向代理將請求分發到兩個後端 Pod 中的一個
3. 後端接收請求後立即返回 202 Accepted
4. 後端異步處理任務，並通過 Kafka 發布事件更新
5. Kafka 將事件傳播到所有後端實例
6. 擁有 SSE 連接的後端實例將事件推送到前端
7. 前端實時更新 UI 顯示事件進度

## 反向代理說明

本專案提供兩種反向代理方式，用於模擬雙 Pod 負載均衡環境：

1. **Java 反向代理**：使用 JDK 內置的 HttpServer 實現
2. **Node.js 反向代理**：使用 http-proxy 庫實現

反向代理會將請求以輪詢方式分發到兩個後端實例（Pod A 和 Pod B）。這樣可以確保即使請求被發送到沒有 SSE 連接的 Pod，Kafka 也能確保事件被持有 SSE 連接的 Pod 處理並推送到前端。

## 運行說明

### 方法一：使用啟動腳本（推薦）

```bash
chmod +x start-servers.sh
./start-servers.sh
```

啟動腳本會依次啟動 Kafka、兩個後端 Pod、反向代理和前端。

### 方法二：手動啟動各個組件

#### 啟動 Kafka

```bash
docker-compose up -d zookeeper kafka
```

#### 啟動後端 Pod A

```bash
cd back
mvn spring-boot:run -Dspring-boot.run.profiles=pod1
```

#### 啟動後端 Pod B（在新的終端窗口）

```bash
cd back
mvn spring-boot:run -Dspring-boot.run.profiles=pod2
```

#### 啟動反向代理（在新的終端窗口）

Java 代理：

```bash
cd proxy
mvn compile exec:java -Dexec.mainClass="com.example.proxy.SimpleLoadBalancer"
```

或者 Node.js 代理：

```bash
cd proxy
npm install
npm start
```

#### 啟動前端（在新的終端窗口）

```bash
cd frontend
npm install
npm start
```

然後訪問 http://localhost:4200

## 注意事項

- 確保 Kafka 運行在 localhost:9092
- 後端 Pod A 運行在 localhost:9090
- 後端 Pod B 運行在 localhost:9091
- 反向代理運行在 localhost:8080
- 前端默認運行在 localhost:4200
- 請求會被反向代理以輪詢方式分發到兩個後端 Pod

## 技術棧

- **前端**：Angular 18, RxJS, TypeScript
- **後端**：Spring Boot 3, Java 17, Spring Kafka
- **消息代理**：Kafka
- **反向代理**：Java HttpServer 或 Node.js http-proxy
